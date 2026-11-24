package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calls.CallAudioController
import com.example.calls.IncomingCallCoordinator
import com.example.calls.IncomingCallService
import com.example.calls.WebRtcCallEngine
import com.example.data.model.CallQualityPreset
import com.example.data.model.CallSession
import com.example.data.model.CallTarget
import com.example.data.model.UserProfile
import com.example.data.repository.CallSignalingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.ConcurrentHashMap

enum class CallConnectionPhase(val label: String) {
    IDLE("Idle"),
    DIALING("Dialing HD…"),
    RINGING_OUTGOING("Ringing…"),
    CONNECTING("Connecting encrypted stream…"),
    CONNECTED("HD Connected"),
    RECONNECTING("Reconnecting…"),
    ENDED("Call ended")
}

data class ActiveCallUiState(
    val session: CallSession? = null,
    val phase: CallConnectionPhase = CallConnectionPhase.IDLE,
    val isOutgoing: Boolean = true,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val hasRemoteVideo: Boolean = false,
    val isMinimized: Boolean = false,
    val qualityPreset: CallQualityPreset = CallQualityPreset.HD_720P,
    val durationSeconds: Long = 0L,
    val endReasonBanner: String? = null
)

class CallViewModel(
    appContext: Context,
    private val repository: CallSignalingRepository,
    val currentUserId: String,
    private val hasFirebaseAuth: Boolean = true
) : ViewModel() {

    private val context = appContext.applicationContext
    private val audioController = CallAudioController(context)

    private val _incomingCall = MutableStateFlow<CallSession?>(null)
    val incomingCall: StateFlow<CallSession?> = _incomingCall.asStateFlow()

    private val _callState = MutableStateFlow(ActiveCallUiState())
    val callState: StateFlow<ActiveCallUiState> = _callState.asStateFlow()

    private val _callNotice = MutableStateFlow<String?>(null)
    val callNotice: StateFlow<String?> = _callNotice.asStateFlow()

    private var rtcEngine: WebRtcCallEngine? = null
    private var sessionObserverJob: Job? = null
    private var candidatesObserverJob: Job? = null
    private var durationTimerJob: Job? = null
    private var outgoingTimeoutJob: Job? = null

    private val appliedCandidateIds = ConcurrentHashMap.newKeySet<String>()
    private var appliedRemoteAnswer = false
    private val blockedCallerIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        if (hasFirebaseAuth) {
            observeIncomingCalls()
        }
    }

    fun updateBlockedUserIds(ids: Set<String>) {
        blockedCallerIds.value = ids
        val currentIncoming = _incomingCall.value
        if (currentIncoming != null && currentIncoming.callerId in ids) {
            _incomingCall.value = null
            audioController.stopRinging()
        }
    }

    private fun observeIncomingCalls() {
        viewModelScope.launch {
            repository.observeIncomingRingingCalls(currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Incoming call listener error", error)
                    emit(emptyList())
                }
                .collect { ringingList ->
                    val currentActiveId = _callState.value.session?.id
                    val blocked = blockedCallerIds.value
                    val candidateCall = ringingList.firstOrNull { call ->
                        call.id != currentActiveId && !call.isTerminal && call.callerId !in blocked
                    }
                    val prevIncoming = _incomingCall.value
                    _incomingCall.value = candidateCall
                    if (candidateCall != null && currentActiveId == null) {
                        if (prevIncoming?.id != candidateCall.id &&
                            !IncomingCallCoordinator.isRingingCall(candidateCall.id)
                        ) {
                            audioController.startRinging()
                        }
                    } else {
                        audioController.stopRinging()
                    }
                }
        }
    }

    fun startOutgoingCall(
        callerProfile: UserProfile,
        target: CallTarget,
        isVideoCall: Boolean,
        preferredVideoPreset: CallQualityPreset = CallQualityPreset.HD_720P
    ) {
        if (_callState.value.session != null) {
            _callNotice.value = "Finish your current call before starting another."
            return
        }
        if (target.userId == currentUserId) {
            _callNotice.value = "You cannot start a call with yourself."
            return
        }

        val preset = if (isVideoCall) preferredVideoPreset else CallQualityPreset.HD_VOICE
        val callType = if (isVideoCall) CallSession.TYPE_VIDEO else CallSession.TYPE_VOICE
        val callId = repository.allocateCallId()

        audioController.stopRinging()
        audioController.startCallAudio(speakerOnByDefault = isVideoCall)

        appliedCandidateIds.clear()
        appliedRemoteAnswer = false

        val engine = createEngine(
            callId = callId,
            participantIds = listOf(currentUserId, target.userId),
            isVideoCall = isVideoCall,
            preset = preset
        )
        rtcEngine = engine

        val provisionalSession = CallSession(
            id = callId,
            callerId = currentUserId,
            callerName = callerProfile.displayName,
            callerPhotoUrl = callerProfile.photoUrl,
            calleeId = target.userId,
            calleeName = target.displayName,
            calleePhotoUrl = target.photoUrl,
            participantIds = listOf(currentUserId, target.userId),
            conversationId = target.conversationId,
            callType = callType,
            qualityPreset = preset.wireValue,
            status = CallSession.STATUS_RINGING
        )

        _callState.value = ActiveCallUiState(
            session = provisionalSession,
            phase = CallConnectionPhase.DIALING,
            isOutgoing = true,
            isMuted = false,
            isSpeakerOn = isVideoCall,
            isCameraEnabled = isVideoCall,
            isFrontCamera = true,
            hasRemoteVideo = false,
            isMinimized = false,
            qualityPreset = preset,
            durationSeconds = 0L
        )

        viewModelScope.launch {
            try {
                val offerSdp = withContext(Dispatchers.Default) {
                    engine.createOfferSdp()
                }
                val created = repository.createCallSession(
                    callId = callId,
                    callerProfile = callerProfile,
                    target = target,
                    callType = callType,
                    qualityPreset = preset,
                    offerSdp = offerSdp
                ).getOrThrow()

                _callState.update {
                    it.copy(
                        session = created,
                        phase = CallConnectionPhase.RINGING_OUTGOING
                    )
                }
                startSignalingObservers(callId)
                startOutgoingNoAnswerTimer(callId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initiate outgoing call", e)
                _callNotice.value = e.localizedMessage ?: "Could not start HD call."
                cleanupCallResources()
            }
        }
    }

    fun acceptIncomingCall(callSession: CallSession) {
        if (_callState.value.session != null) {
            _callNotice.value = "You are already on another call."
            return
        }
        val offerSdp = callSession.offerSdp
        if (offerSdp.isNullOrBlank()) {
            _callNotice.value = "Call offer expired."
            viewModelScope.launch { repository.declineCall(callSession.id) }
            return
        }

        _incomingCall.value = null
        audioController.stopRinging()
        IncomingCallCoordinator.stopRingingForCall(callSession.id)
        IncomingCallService.stop(context)
        audioController.startCallAudio(speakerOnByDefault = callSession.isVideoCall)

        appliedCandidateIds.clear()
        appliedRemoteAnswer = true // Caller already sent offer; we are answering

        val preset = callSession.qualityEnum()
        val engine = createEngine(
            callId = callSession.id,
            participantIds = callSession.participantIds,
            isVideoCall = callSession.isVideoCall,
            preset = preset
        )
        rtcEngine = engine

        _callState.value = ActiveCallUiState(
            session = callSession,
            phase = CallConnectionPhase.CONNECTING,
            isOutgoing = false,
            isMuted = false,
            isSpeakerOn = callSession.isVideoCall,
            isCameraEnabled = callSession.isVideoCall,
            isFrontCamera = true,
            hasRemoteVideo = false,
            isMinimized = false,
            qualityPreset = preset,
            durationSeconds = 0L
        )

        viewModelScope.launch {
            try {
                val answerSdp = withContext(Dispatchers.Default) {
                    engine.acceptRemoteOfferAndCreateAnswer(offerSdp)
                }
                repository.acceptCall(callSession.id, answerSdp).getOrThrow()
                startSignalingObservers(callSession.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to accept incoming call", e)
                _callNotice.value = e.localizedMessage ?: "Could not connect HD call."
                cleanupCallResources()
            }
        }
    }

    fun declineIncomingCall(callSession: CallSession) {
        _incomingCall.value = null
        audioController.stopRinging()
        IncomingCallCoordinator.declineFromIncomingUi(context, callSession.id)
        viewModelScope.launch {
            repository.declineCall(callSession.id)
        }
    }

    suspend fun getRingingCallSessionById(callId: String): CallSession? {
        val currentIncoming = _incomingCall.value
        if (currentIncoming != null && currentIncoming.id == callId && !currentIncoming.isTerminal) {
            return currentIncoming
        }
        val fetched = repository.getCallSession(callId).getOrNull() ?: return null
        return if (!fetched.isTerminal && fetched.status == CallSession.STATUS_RINGING) fetched else null
    }

    fun hangUp() {
        val current = _callState.value
        val callId = current.session?.id
        val wasNeverAnswered = current.isOutgoing &&
            (current.phase == CallConnectionPhase.DIALING || current.phase == CallConnectionPhase.RINGING_OUTGOING)

        cleanupCallResources()

        if (!callId.isNullOrBlank()) {
            viewModelScope.launch {
                repository.endCall(callId, asMissed = wasNeverAnswered)
            }
        }
    }

    fun toggleMute() {
        val next = !_callState.value.isMuted
        rtcEngine?.setMicrophoneMuted(next)
        _callState.update { it.copy(isMuted = next) }
    }

    fun toggleSpeaker() {
        val next = !_callState.value.isSpeakerOn
        audioController.setSpeakerphone(next)
        _callState.update { it.copy(isSpeakerOn = next) }
    }

    fun toggleCamera() {
        val session = _callState.value.session ?: return
        if (!session.isVideoCall) return
        val next = !_callState.value.isCameraEnabled
        rtcEngine?.setCameraEnabled(next)
        _callState.update { it.copy(isCameraEnabled = next) }
    }

    fun switchCamera() {
        val session = _callState.value.session ?: return
        if (!session.isVideoCall) return
        rtcEngine?.switchCamera()
    }

    fun cycleVideoQualityPreset() {
        val current = _callState.value
        val session = current.session ?: return
        if (!session.isVideoCall) return
        val next = if (current.qualityPreset == CallQualityPreset.FHD_1080P) {
            CallQualityPreset.HD_720P
        } else {
            CallQualityPreset.FHD_1080P
        }
        rtcEngine?.updateQualityPreset(next)
        _callState.update { it.copy(qualityPreset = next) }
        viewModelScope.launch {
            repository.updateQualityPreset(session.id, next)
        }
    }

    fun setMinimized(minimized: Boolean) {
        _callState.update { it.copy(isMinimized = minimized) }
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        rtcEngine?.attachLocalRenderer(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        rtcEngine?.detachLocalRenderer(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        rtcEngine?.attachRemoteRenderer(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        rtcEngine?.detachRemoteRenderer(renderer)
    }

    fun clearCallNotice() {
        _callNotice.value = null
    }

    private fun createEngine(
        callId: String,
        participantIds: List<String>,
        isVideoCall: Boolean,
        preset: CallQualityPreset
    ): WebRtcCallEngine {
        return WebRtcCallEngine(
            context = context,
            isVideoCall = isVideoCall,
            initialQualityPreset = preset,
            onLocalIceCandidate = { sdpMid, sdpMLineIndex, candidate ->
                viewModelScope.launch {
                    repository.sendIceCandidate(
                        callId = callId,
                        participantIds = participantIds,
                        sdpMid = sdpMid,
                        sdpMLineIndex = sdpMLineIndex,
                        candidate = candidate
                    )
                }
            },
            onConnectionStateChanged = { peerState ->
                viewModelScope.launch {
                    handlePeerConnectionState(peerState)
                }
            },
            onRemoteVideoTrackChanged = { hasVideo ->
                _callState.update { it.copy(hasRemoteVideo = hasVideo) }
            },
            onFacingChanged = { isFront ->
                _callState.update { it.copy(isFrontCamera = isFront) }
            }
        )
    }

    private suspend fun handlePeerConnectionState(peerState: PeerConnection.PeerConnectionState) {
        when (peerState) {
            PeerConnection.PeerConnectionState.CONNECTING -> {
                _callState.update {
                    if (it.phase != CallConnectionPhase.CONNECTED) {
                        it.copy(phase = CallConnectionPhase.CONNECTING)
                    } else it
                }
            }
            PeerConnection.PeerConnectionState.CONNECTED -> {
                outgoingTimeoutJob?.cancel()
                _callState.update { it.copy(phase = CallConnectionPhase.CONNECTED) }
                startDurationTimerIfNeeded()
            }
            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                _callState.update { it.copy(phase = CallConnectionPhase.RECONNECTING) }
            }
            PeerConnection.PeerConnectionState.FAILED -> {
                _callNotice.value = "Connection lost. Ending call."
                hangUp()
            }
            PeerConnection.PeerConnectionState.CLOSED -> {
                cleanupCallResources()
            }
            else -> Unit
        }
    }

    private fun startSignalingObservers(callId: String) {
        sessionObserverJob?.cancel()
        sessionObserverJob = viewModelScope.launch {
            repository.observeCallSession(callId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Call session listener error", error)
                    _callNotice.value = error.localizedMessage ?: "Call connection interrupted."
                    cleanupCallResources()
                }
                .collect { remoteSession ->
                if (remoteSession == null) {
                    cleanupCallResources()
                    return@collect
                }
                _callState.update {
                    it.copy(
                        session = remoteSession,
                        qualityPreset = remoteSession.qualityEnum()
                    )
                }

                if (remoteSession.status == CallSession.STATUS_ACCEPTED &&
                    !appliedRemoteAnswer &&
                    !remoteSession.answerSdp.isNullOrBlank()
                ) {
                    appliedRemoteAnswer = true
                    outgoingTimeoutJob?.cancel()
                    _callState.update { it.copy(phase = CallConnectionPhase.CONNECTING) }
                    val engine = rtcEngine
                    if (engine != null) {
                        runCatching {
                            withContext(Dispatchers.Default) {
                                engine.applyRemoteAnswer(remoteSession.answerSdp)
                            }
                        }.onFailure { err ->
                            Log.w(TAG, "Failed to apply remote answer", err)
                        }
                    }
                }

                if (remoteSession.isTerminal) {
                    val reason = when (remoteSession.status) {
                        CallSession.STATUS_DECLINED -> "${remoteSession.peerNameFor(currentUserId)} declined the call"
                        CallSession.STATUS_MISSED -> "No answer"
                        else -> "Call ended"
                    }
                    if (remoteSession.endedBy != currentUserId) {
                        _callNotice.value = reason
                    }
                    cleanupCallResources()
                }
            }
        }

        candidatesObserverJob?.cancel()
        candidatesObserverJob = viewModelScope.launch {
            repository.observeRemoteCandidates(callId, currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Remote ICE candidate listener error", error)
                }
                .collect { candidates ->
                    val engine = rtcEngine ?: return@collect
                    candidates.forEach { candidate ->
                        if (candidate.id.isNotBlank() && appliedCandidateIds.add(candidate.id)) {
                            engine.addRemoteIceCandidate(candidate)
                        }
                    }
                }
        }
    }

    private fun startDurationTimerIfNeeded() {
        if (durationTimerJob?.isActive == true) return
        durationTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _callState.update { it.copy(durationSeconds = it.durationSeconds + 1L) }
            }
        }
    }

    private fun startOutgoingNoAnswerTimer(callId: String) {
        outgoingTimeoutJob?.cancel()
        outgoingTimeoutJob = viewModelScope.launch {
            delay(IncomingCallCoordinator.RING_TIMEOUT_MS)
            val current = _callState.value
            if (current.session?.id == callId &&
                (current.phase == CallConnectionPhase.DIALING ||
                    current.phase == CallConnectionPhase.RINGING_OUTGOING)
            ) {
                _callNotice.value = "No answer"
                cleanupCallResources()
                repository.endCall(callId, asMissed = true)
            }
        }
    }

    private fun cleanupCallResources() {
        outgoingTimeoutJob?.cancel()
        outgoingTimeoutJob = null
        durationTimerJob?.cancel()
        durationTimerJob = null
        sessionObserverJob?.cancel()
        sessionObserverJob = null
        candidatesObserverJob?.cancel()
        candidatesObserverJob = null

        rtcEngine?.dispose()
        rtcEngine = null
        audioController.stopCallAudio()

        appliedCandidateIds.clear()
        appliedRemoteAnswer = false
        _callState.value = ActiveCallUiState()
    }

    override fun onCleared() {
        super.onCleared()
        cleanupCallResources()
    }

    private companion object {
        const val TAG = "CallViewModel"
    }
}
