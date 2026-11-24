package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class RecordedVoiceNote(
    val base64: String,
    val durationSec: Int
)

data class VoicePlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val positionSec: Int = 0
)

class VoiceNoteRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun start(): Result<Unit> = runCatching {
        stopInternal(deleteFile = true)
        val file = File(appContext.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(18_000)
            setMaxDuration(30_000)
            setMaxFileSize(66_000L)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        outputFile = file
        startedAtMs = System.currentTimeMillis()
    }

    fun stopAndEncode(): Result<RecordedVoiceNote> = runCatching {
        val file = outputFile ?: error("No active voice recording.")
        val elapsedSec = (((System.currentTimeMillis() - startedAtMs) / 1000L).toInt()).coerceIn(1, 180)
        try {
            recorder?.stop()
        } finally {
            recorder?.release()
            recorder = null
        }
        val bytes = file.readBytes()
        file.delete()
        outputFile = null
        require(bytes.size >= 128) { "Recording was too short. Hold or speak a little longer." }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        require(encoded.length <= 94_500) { "Voice note exceeded size limit." }
        RecordedVoiceNote(base64 = encoded, durationSec = elapsedSec)
    }

    fun cancel() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
        }
        outputFile = null
    }
}

class VoiceNotePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    fun togglePlayback(messageId: String, audioBase64: String) {
        val current = _state.value
        val player = mediaPlayer
        if (current.activeMessageId == messageId && player != null) {
            if (player.isPlaying) {
                player.pause()
                progressJob?.cancel()
                _state.value = current.copy(isPlaying = false)
            } else {
                player.start()
                _state.value = current.copy(isPlaying = true)
                startProgressLoop(messageId, player)
            }
            return
        }

        stop()

        runCatching {
            val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val file = File(appContext.cacheDir, "play_${messageId.take(12)}.m4a")
            FileOutputStream(file).use { it.write(bytes) }
            tempFile = file

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                start()
            }
            mediaPlayer = mp
            _state.value = VoicePlaybackState(
                activeMessageId = messageId,
                isPlaying = true,
                progress = 0f,
                positionSec = 0
            )
            startProgressLoop(messageId, mp)
        }.onFailure {
            stop()
        }
    }

    private fun startProgressLoop(messageId: String, player: MediaPlayer) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer === player) {
                val duration = player.duration.coerceAtLeast(1)
                val currentPos = player.currentPosition.coerceIn(0, duration)
                _state.value = VoicePlaybackState(
                    activeMessageId = messageId,
                    isPlaying = player.isPlaying,
                    progress = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    positionSec = currentPos / 1000
                )
                if (!player.isPlaying) break
                delay(120L)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        tempFile?.delete()
        tempFile = null
        _state.value = VoicePlaybackState()
    }

    fun release() {
        stop()
        scope.cancel()
    }
}

fun formatDurationSec(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%d:%02d".format(mins, secs)
}
