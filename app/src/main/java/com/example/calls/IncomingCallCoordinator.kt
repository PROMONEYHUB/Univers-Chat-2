package com.example.calls

import android.content.Context
import com.example.data.model.CallSession
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates incoming call ringing, lock-screen UI state, 30s countdown/timeout,
 * deduplication across FCM + Firestore listeners, and notification Answer handoff.
 */
object IncomingCallCoordinator {

    const val STALE_PUSH_MS = 35_000L
    const val RING_TIMEOUT_MS = 30_000L
    const val RING_TIMEOUT_SECONDS = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ringingCall = MutableStateFlow<CallSession?>(null)
    val ringingCall: StateFlow<CallSession?> = _ringingCall.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(RING_TIMEOUT_SECONDS)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _pendingAcceptCall = MutableStateFlow<CallSession?>(null)
    val pendingAcceptCall: StateFlow<CallSession?> = _pendingAcceptCall.asStateFlow()

    private val _pendingAcceptCallId = MutableStateFlow<String?>(null)
    val pendingAcceptCallId: StateFlow<String?> = _pendingAcceptCallId.asStateFlow()

    private val handledCallIds = ConcurrentHashMap.newKeySet<String>()
    private val missedNotifiedCallIds = ConcurrentHashMap.newKeySet<String>()

    private var audioController: CallAudioController? = null
    private var countdownJob: Job? = null

    fun isRingingCall(callId: String): Boolean {
        return _ringingCall.value?.id == callId
    }

    fun onIncomingCallDetected(
        context: Context,
        session: CallSession,
        startRingtone: Boolean = true,
        onTimeout: ((CallSession) -> Unit)? = null
    ): Boolean {
        if (session.id.isBlank() || session.isTerminal) return false
        if (_ringingCall.value?.id == session.id) return false
        if (handledCallIds.contains(session.id)) return false

        if (handledCallIds.size > 64) {
            handledCallIds.clear()
        }
        handledCallIds.add(session.id)

        _ringingCall.value = session
        _remainingSeconds.value = RING_TIMEOUT_SECONDS

        if (startRingtone) {
            val controller = audioController ?: CallAudioController(context.applicationContext).also {
                audioController = it
            }
            controller.startRinging()
        }

        countdownJob?.cancel()
        countdownJob = scope.launch {
            var remaining = RING_TIMEOUT_SECONDS
            while (remaining > 0 && _ringingCall.value?.id == session.id) {
                delay(1_000L)
                remaining -= 1
                if (_ringingCall.value?.id == session.id) {
                    _remainingSeconds.value = remaining
                }
            }
            if (_ringingCall.value?.id == session.id) {
                _ringingCall.value = null
                stopRingtoneInternal()
                onTimeout?.invoke(session)
            }
        }
        return true
    }

    fun acceptFromIncomingUi(context: Context, session: CallSession) {
        if (handledCallIds.size > 64) handledCallIds.clear()
        handledCallIds.add(session.id)
        _pendingAcceptCall.value = session
        clearRingingStateOnly(session.id)
        NotificationHelper.cancelIncomingCallNotification(context.applicationContext)
        IncomingCallService.stop(context.applicationContext)
    }

    fun declineFromIncomingUi(context: Context, callId: String) {
        if (handledCallIds.size > 64) handledCallIds.clear()
        handledCallIds.add(callId)
        clearRingingStateOnly(callId)
        NotificationHelper.cancelIncomingCallNotification(context.applicationContext)
        IncomingCallService.stop(context.applicationContext)
    }

    fun onRemoteCallTerminated(
        context: Context,
        callId: String,
        terminalStatus: String,
        callerName: String,
        conversationId: String?,
        isVideo: Boolean,
        callerPhotoUrl: String? = null
    ) {
        val wasRinging = _ringingCall.value?.id == callId
        val wasAcceptedLocally = _pendingAcceptCall.value?.id == callId
        if (wasRinging) {
            clearRingingStateOnly(callId)
        }
        if (handledCallIds.size > 64) handledCallIds.clear()
        handledCallIds.add(callId)

        val appContext = context.applicationContext
        NotificationHelper.cancelIncomingCallNotification(appContext)
        IncomingCallService.stop(appContext)

        val shouldShowMissed = !wasAcceptedLocally && (
            terminalStatus == CallSession.STATUS_MISSED ||
                (terminalStatus == CallSession.STATUS_ENDED && wasRinging)
            )
        if (shouldShowMissed && missedNotifiedCallIds.add(callId)) {
            if (missedNotifiedCallIds.size > 64) {
                missedNotifiedCallIds.clear()
                missedNotifiedCallIds.add(callId)
            }
            scope.launch(Dispatchers.IO) {
                val avatar = NotificationHelper.loadCircularAvatarBitmap(callerPhotoUrl)
                NotificationHelper.showMissedCallNotification(
                    context = appContext,
                    callId = callId,
                    callerName = callerName,
                    conversationId = conversationId,
                    isVideo = isVideo,
                    callerAvatarBitmap = avatar
                )
            }
        }
    }

    fun markMissedNotificationShown(callId: String) {
        if (missedNotifiedCallIds.size > 64) missedNotifiedCallIds.clear()
        missedNotifiedCallIds.add(callId)
    }

    fun emitNotificationAcceptCallId(callId: String?) {
        if (!callId.isNullOrBlank()) {
            _pendingAcceptCallId.value = callId
        }
    }

    fun consumePendingAcceptCallId(): String? {
        val id = _pendingAcceptCallId.value
        _pendingAcceptCallId.value = null
        return id
    }

    fun consumePendingAcceptCall(): CallSession? {
        val current = _pendingAcceptCall.value
        _pendingAcceptCall.value = null
        return current
    }

    fun stopRingingForCall(callId: String) {
        if (_ringingCall.value?.id == callId) {
            clearRingingStateOnly(callId)
        }
    }

    private fun clearRingingStateOnly(callId: String) {
        countdownJob?.cancel()
        countdownJob = null
        if (_ringingCall.value?.id == callId) {
            _ringingCall.value = null
        }
        _remainingSeconds.value = RING_TIMEOUT_SECONDS
        stopRingtoneInternal()
    }

    private fun stopRingtoneInternal() {
        runCatching { audioController?.stopRinging() }
    }

    fun clearForTest() {
        countdownJob?.cancel()
        countdownJob = null
        _ringingCall.value = null
        _pendingAcceptCall.value = null
        _pendingAcceptCallId.value = null
        _remainingSeconds.value = RING_TIMEOUT_SECONDS
        handledCallIds.clear()
        missedNotifiedCallIds.clear()
        stopRingtoneInternal()
    }
}
