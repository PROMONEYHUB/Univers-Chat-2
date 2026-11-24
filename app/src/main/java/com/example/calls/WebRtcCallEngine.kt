package com.example.calls

import android.content.Context
import android.util.Log
import com.example.data.model.CallQualityPreset
import com.example.data.model.IceCandidateSignal
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WebRtcCallEngine(
    context: Context,
    private val isVideoCall: Boolean,
    initialQualityPreset: CallQualityPreset,
    private val onLocalIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.PeerConnectionState) -> Unit,
    private val onRemoteVideoTrackChanged: (hasRemoteVideo: Boolean) -> Unit,
    private val onFacingChanged: (isFrontCamera: Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    val eglBase: EglBase = EglBase.create()

    private val audioDeviceModule: JavaAudioDeviceModule
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSender: RtpSender? = null

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoSender: RtpSender? = null
    private var remoteVideoTrack: VideoTrack? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private val pendingRemoteCandidates = CopyOnWriteArrayList<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false
    @Volatile
    private var isDisposed = false
    @Volatile
    private var isFrontCamera = true
    private var currentQualityPreset = initialQualityPreset

    init {
        ensureFactoryInitialized(appContext)

        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
            .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8Encoder = */ true,
            /* enableH264HighProfile = */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        setupPeerConnection()
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate == null || isDisposed) return
                    onLocalIceCandidate(
                        candidate.sdpMid.orEmpty(),
                        candidate.sdpMLineIndex,
                        candidate.sdp
                    )
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

                override fun onAddStream(stream: MediaStream?) = Unit

                override fun onRemoveStream(stream: MediaStream?) = Unit

                override fun onDataChannel(channel: DataChannel?) = Unit

                override fun onRenegotiationNeeded() = Unit

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    if (newState != null && !isDisposed) {
                        onConnectionStateChanged(newState)
                    }
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack && !isDisposed) {
                        attachRemoteVideoTrack(track)
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack && !isDisposed) {
                        attachRemoteVideoTrack(track)
                    }
                }
            }
        )

        setupLocalAudioTrack()
        if (isVideoCall) {
            setupLocalVideoTrack()
        }
        applyBitrateParameters(currentQualityPreset)
    }

    private fun setupLocalAudioTrack() {
        val pc = peerConnection ?: return
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
        }
        val source = peerConnectionFactory.createAudioSource(audioConstraints)
        val track = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, source).apply {
            setEnabled(true)
        }
        audioSource = source
        localAudioTrack = track
        audioSender = pc.addTrack(track, listOf(STREAM_ID))
    }

    private fun setupLocalVideoTrack() {
        val pc = peerConnection ?: return
        val enumerator = Camera2Enumerator(appContext)
        val deviceNames = enumerator.deviceNames
        val frontName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val selectedName = frontName ?: deviceNames.firstOrNull() ?: return
        isFrontCamera = enumerator.isFrontFacing(selectedName)
        onFacingChanged(isFrontCamera)

        val capturer = enumerator.createCapturer(selectedName, null) ?: return
        val helper = SurfaceTextureHelper.create("UniversCaptureThread", eglBase.eglBaseContext)
        val source = peerConnectionFactory.createVideoSource(capturer.isScreencast)
        capturer.initialize(helper, appContext, source.capturerObserver)
        runCatching {
            capturer.startCapture(
                currentQualityPreset.captureWidth.coerceAtLeast(1280),
                currentQualityPreset.captureHeight.coerceAtLeast(720),
                currentQualityPreset.fps.coerceAtLeast(30)
            )
        }.onFailure { err ->
            Log.w(TAG, "HD capture start fallback", err)
            runCatching { capturer.startCapture(1280, 720, 30) }
        }

        val track = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, source).apply {
            setEnabled(true)
        }
        videoCapturer = capturer
        surfaceTextureHelper = helper
        videoSource = source
        localVideoTrack = track
        videoSender = pc.addTrack(track, listOf(STREAM_ID))

        localRenderer?.let { renderer ->
            track.addSink(renderer)
        }
    }

    private fun attachRemoteVideoTrack(track: VideoTrack) {
        val previous = remoteVideoTrack
        if (previous != null && previous !== track) {
            remoteRenderer?.let { previous.removeSink(it) }
        }
        remoteVideoTrack = track
        track.setEnabled(true)
        remoteRenderer?.let { renderer ->
            track.addSink(renderer)
        }
        onRemoteVideoTrackChanged(true)
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (isDisposed || localRenderer === renderer) return
        localRenderer?.let { old -> localVideoTrack?.removeSink(old) }
        initRenderer(renderer, mirror = isFrontCamera, overlay = true)
        localRenderer = renderer
        localVideoTrack?.addSink(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (localRenderer === renderer) {
            localVideoTrack?.removeSink(renderer)
            runCatching { renderer.release() }
            localRenderer = null
        }
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (isDisposed || remoteRenderer === renderer) return
        remoteRenderer?.let { old -> remoteVideoTrack?.removeSink(old) }
        initRenderer(renderer, mirror = false, overlay = false)
        remoteRenderer = renderer
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (remoteRenderer === renderer) {
            remoteVideoTrack?.removeSink(renderer)
            runCatching { renderer.release() }
            remoteRenderer = null
        }
    }

    private fun initRenderer(
        renderer: SurfaceViewRenderer,
        mirror: Boolean,
        overlay: Boolean
    ) {
        runCatching {
            renderer.init(eglBase.eglBaseContext, null)
        }
        renderer.setMirror(mirror)
        renderer.setEnableHardwareScaler(true)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        renderer.setZOrderMediaOverlay(overlay)
    }

    suspend fun createOfferSdp(): String {
        val pc = peerConnection ?: error("PeerConnection unavailable")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }
        val rawOffer = suspendCoroutine<SessionDescription> { cont ->
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) cont.resume(sdp)
                    else cont.resumeWithException(IllegalStateException("Empty SDP offer"))
                }

                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Failed to create SDP offer"))
                }
            }, constraints)
        }
        val hdSdp = optimizeSdpForHd(rawOffer.description, currentQualityPreset)
        val tunedOffer = SessionDescription(rawOffer.type, hdSdp)
        setLocalDescription(tunedOffer)
        applyBitrateParameters(currentQualityPreset)
        return hdSdp
    }

    suspend fun acceptRemoteOfferAndCreateAnswer(offerSdp: String): String {
        val pc = peerConnection ?: error("PeerConnection unavailable")
        setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offerSdp))

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }
        val rawAnswer = suspendCoroutine<SessionDescription> { cont ->
            pc.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) cont.resume(sdp)
                    else cont.resumeWithException(IllegalStateException("Empty SDP answer"))
                }

                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Failed to create SDP answer"))
                }
            }, constraints)
        }
        val hdAnswerSdp = optimizeSdpForHd(rawAnswer.description, currentQualityPreset)
        val tunedAnswer = SessionDescription(rawAnswer.type, hdAnswerSdp)
        setLocalDescription(tunedAnswer)
        applyBitrateParameters(currentQualityPreset)
        return hdAnswerSdp
    }

    suspend fun applyRemoteAnswer(answerSdp: String) {
        if (isDisposed || isRemoteDescriptionSet) return
        setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
        applyBitrateParameters(currentQualityPreset)
    }

    fun addRemoteIceCandidate(signal: IceCandidateSignal) {
        if (isDisposed || signal.candidate.isBlank()) return
        val ice = IceCandidate(signal.sdpMid, signal.sdpMLineIndex, signal.candidate)
        if (!isRemoteDescriptionSet) {
            pendingRemoteCandidates.add(ice)
        } else {
            peerConnection?.addIceCandidate(ice)
        }
    }

    fun setMicrophoneMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
                localRenderer?.setMirror(isFront)
                onFacingChanged(isFront)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.w(TAG, "Camera switch error: $errorDescription")
            }
        })
    }

    fun updateQualityPreset(preset: CallQualityPreset) {
        currentQualityPreset = preset
        if (isVideoCall && preset != CallQualityPreset.HD_VOICE) {
            runCatching {
                videoCapturer?.changeCaptureFormat(preset.captureWidth, preset.captureHeight, preset.fps)
            }.onFailure { err ->
                Log.w(TAG, "Capture format update failed", err)
            }
        }
        applyBitrateParameters(preset)
    }

    private fun applyBitrateParameters(preset: CallQualityPreset) {
        audioSender?.let { sender ->
            runCatching {
                val params = sender.parameters
                params.encodings.forEach { encoding ->
                    encoding.maxBitrateBps = preset.maxAudioBitrateBps
                }
                sender.parameters = params
            }
        }
        if (isVideoCall && preset != CallQualityPreset.HD_VOICE) {
            videoSender?.let { sender ->
                runCatching {
                    val params = sender.parameters
                    params.encodings.forEach { encoding ->
                        encoding.maxBitrateBps = preset.maxVideoBitrateBps
                        encoding.minBitrateBps = 400_000
                        encoding.maxFramerate = preset.fps
                    }
                    sender.parameters = params
                }
            }
        }
    }

    private suspend fun setLocalDescription(description: SessionDescription) {
        val pc = peerConnection ?: error("PeerConnection unavailable")
        suspendCoroutine { cont ->
            pc.setLocalDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Failed to set local SDP"))
                }
            }, description)
        }
    }

    private suspend fun setRemoteDescription(description: SessionDescription) {
        val pc = peerConnection ?: error("PeerConnection unavailable")
        suspendCoroutine { cont ->
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    isRemoteDescriptionSet = true
                    val buffered = pendingRemoteCandidates.toList()
                    pendingRemoteCandidates.clear()
                    buffered.forEach { pc.addIceCandidate(it) }
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Failed to set remote SDP"))
                }
            }, description)
        }
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true

        localRenderer?.let { renderer ->
            localVideoTrack?.removeSink(renderer)
            runCatching { renderer.release() }
            localRenderer = null
        }
        remoteRenderer?.let { renderer ->
            remoteVideoTrack?.removeSink(renderer)
            runCatching { renderer.release() }
            remoteRenderer = null
        }

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null

        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        runCatching { peerConnectionFactory.dispose() }
        runCatching { audioDeviceModule.release() }
        runCatching { eglBase.release() }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private const val TAG = "WebRtcCallEngine"
        private const val STREAM_ID = "ARDAMS"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val VIDEO_TRACK_ID = "ARDAMSv0"

        @Volatile
        private var isFactoryInitialized = false

        private fun ensureFactoryInitialized(context: Context) {
            if (isFactoryInitialized) return
            synchronized(this) {
                if (!isFactoryInitialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions()
                    )
                    isFactoryInitialized = true
                }
            }
        }

        /**
         * Tunes SDP fmtp lines to prioritize 48kHz wideband stereo Opus (up to 128 kbps)
         * and high-bitrate HD video ramp-up.
         */
        fun optimizeSdpForHd(sdp: String, preset: CallQualityPreset): String {
            val lines = sdp.split("\r\n").toMutableList()
            val opusPayloads = mutableSetOf<String>()
            val videoPayloads = mutableSetOf<String>()

            val rtpmapRegex = Regex("^a=rtpmap:(\\d+)\\s+([A-Za-z0-9\\-_]+)/.*")
            for (line in lines) {
                val match = rtpmapRegex.matchEntire(line) ?: continue
                val pt = match.groupValues[1]
                val codec = match.groupValues[2].lowercase()
                when (codec) {
                    "opus" -> opusPayloads.add(pt)
                    "h264", "vp8", "vp9" -> videoPayloads.add(pt)
                }
            }

            val videoMaxKbps = (preset.maxVideoBitrateBps / 1000).coerceAtLeast(1000)
            val result = mutableListOf<String>()
            val existingFmtpPayloads = mutableSetOf<String>()

            for (line in lines) {
                val matchedOpus = opusPayloads.firstOrNull { line.startsWith("a=fmtp:$it ") }
                val matchedVideo = videoPayloads.firstOrNull { line.startsWith("a=fmtp:$it ") }
                when {
                    matchedOpus != null -> {
                        existingFmtpPayloads.add(matchedOpus)
                        val base = line.trimEnd(';')
                        val additions = buildString {
                            if (!base.contains("maxaveragebitrate=")) append(";maxaveragebitrate=${preset.maxAudioBitrateBps}")
                            if (!base.contains("stereo=")) append(";stereo=1")
                            if (!base.contains("sprop-stereo=")) append(";sprop-stereo=1")
                            if (!base.contains("useinbandfec=")) append(";useinbandfec=1")
                        }
                        result.add(base + additions)
                    }
                    matchedVideo != null && preset != CallQualityPreset.HD_VOICE -> {
                        existingFmtpPayloads.add(matchedVideo)
                        val base = line.trimEnd(';')
                        val additions = buildString {
                            if (!base.contains("x-google-min-bitrate=")) append(";x-google-min-bitrate=400")
                            if (!base.contains("x-google-start-bitrate=")) append(";x-google-start-bitrate=1200")
                            if (!base.contains("x-google-max-bitrate=")) append(";x-google-max-bitrate=$videoMaxKbps")
                        }
                        result.add(base + additions)
                    }
                    else -> result.add(line)
                }
            }

            return result.joinToString("\r\n")
        }
    }
}
