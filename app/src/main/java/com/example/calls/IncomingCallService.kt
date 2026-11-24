package com.example.calls

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.data.model.CallQualityPreset
import com.example.data.model.CallSession
import com.example.data.repository.CallSignalingRepository
import com.example.notifications.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Foreground service (phoneCall type) triggered by high-priority FCM call pushes when the app is
 * backgrounded or killed. Displays the CallStyle full-screen incoming call notification, starts
 * ringing, watches Firestore for remote caller cancellation, and enforces a 30-second ring timeout.
 */
class IncomingCallService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeCallId: String? = null
    private var callSessionObserverJob: Job? = null
    private var timeoutJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_INCOMING_CALL) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val session = extractCallSession(intent)
        if (session == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        activeCallId = session.id
        IncomingCallCoordinator.onIncomingCallDetected(
            context = applicationContext,
            session = session,
            startRingtone = true
        )

        val initialNotification = NotificationHelper.buildIncomingCallNotification(
            context = applicationContext,
            callId = session.id,
            callerName = session.callerName,
            isVideo = session.isVideoCall,
            callerAvatarBitmap = null
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        } else {
            0
        }

        val startedForeground = runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.INCOMING_CALL_NOTIFICATION_ID,
                initialNotification,
                foregroundType
            )
            true
        }.getOrElse { err ->
            Log.w(TAG, "Unable to elevate IncomingCallService to foreground; posting standard notification", err)
            if (NotificationHelper.hasNotificationPermission(applicationContext)) {
                runCatching {
                    NotificationManagerCompat.from(applicationContext)
                        .notify(NotificationHelper.INCOMING_CALL_NOTIFICATION_ID, initialNotification)
                }
            }
            false
        }

        // Load circular caller avatar asynchronously and refresh the CallStyle notification
        serviceScope.launch {
            val avatar = NotificationHelper.loadCircularAvatarBitmap(session.callerPhotoUrl)
            if (avatar != null && activeCallId == session.id) {
                val updatedNotification = NotificationHelper.buildIncomingCallNotification(
                    context = applicationContext,
                    callId = session.id,
                    callerName = session.callerName,
                    isVideo = session.isVideoCall,
                    callerAvatarBitmap = avatar
                )
                runCatching {
                    NotificationManagerCompat.from(applicationContext)
                        .notify(NotificationHelper.INCOMING_CALL_NOTIFICATION_ID, updatedNotification)
                }
            }
        }

        observeRemoteCallCancellation(session)
        scheduleRingTimeout(session)

        if (!startedForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Keep service alive only if foreground elevation succeeded; timeout + notification remain active
            Log.d(TAG, "Running without FGS promotion on API ${Build.VERSION.SDK_INT}")
        }

        return START_NOT_STICKY
    }

    private fun observeRemoteCallCancellation(session: CallSession) {
        callSessionObserverJob?.cancel()
        if (runCatching { Firebase.auth.currentUser }.getOrNull() == null) return

        callSessionObserverJob = serviceScope.launch {
            val repo = CallSignalingRepository(applicationContext)
            repo.observeCallSession(session.id)
                .catch { err ->
                    if (err is CancellationException) throw err
                    Log.w(TAG, "Failed observing incoming call ${session.id}", err)
                }
                .collect { remote ->
                    if (remote == null || remote.isTerminal || remote.status == CallSession.STATUS_ACCEPTED) {
                        val terminalStatus = remote?.status ?: CallSession.STATUS_ENDED
                        if (remote?.status == CallSession.STATUS_ACCEPTED) {
                            IncomingCallCoordinator.stopRingingForCall(session.id)
                            stopSelfCleanly()
                        } else {
                            IncomingCallCoordinator.onRemoteCallTerminated(
                                context = applicationContext,
                                callId = session.id,
                                terminalStatus = terminalStatus,
                                callerName = session.callerName,
                                conversationId = session.conversationId,
                                isVideo = session.isVideoCall,
                                callerPhotoUrl = session.callerPhotoUrl
                            )
                        }
                    }
                }
        }
    }

    private fun scheduleRingTimeout(session: CallSession) {
        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(IncomingCallCoordinator.RING_TIMEOUT_MS)
            if (activeCallId == session.id) {
                if (runCatching { Firebase.auth.currentUser }.getOrNull() != null) {
                    runCatching {
                        CallSignalingRepository(applicationContext)
                            .endCall(session.id, asMissed = true)
                    }
                }
                IncomingCallCoordinator.onRemoteCallTerminated(
                    context = applicationContext,
                    callId = session.id,
                    terminalStatus = CallSession.STATUS_MISSED,
                    callerName = session.callerName,
                    conversationId = session.conversationId,
                    isVideo = session.isVideoCall,
                    callerPhotoUrl = session.callerPhotoUrl
                )
            }
        }
    }

    private fun stopSelfCleanly() {
        callSessionObserverJob?.cancel()
        callSessionObserverJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        activeCallId?.let { IncomingCallCoordinator.stopRingingForCall(it) }
        activeCallId = null
        NotificationHelper.cancelIncomingCallNotification(applicationContext)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        callSessionObserverJob?.cancel()
        timeoutJob?.cancel()
        activeCallId?.let { IncomingCallCoordinator.stopRingingForCall(it) }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IncomingCallService"
        private const val ACTION_START_INCOMING_CALL = "com.example.calls.ACTION_START_INCOMING_CALL"
        private const val ACTION_STOP_INCOMING_CALL = "com.example.calls.ACTION_STOP_INCOMING_CALL"

        private const val EXTRA_CALL_ID = "extra_call_id"
        private const val EXTRA_CALLER_ID = "extra_caller_id"
        private const val EXTRA_CALLER_NAME = "extra_caller_name"
        private const val EXTRA_CALLER_PHOTO = "extra_caller_photo"
        private const val EXTRA_CALLEE_ID = "extra_callee_id"
        private const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        private const val EXTRA_CALL_TYPE = "extra_call_type"
        private const val EXTRA_QUALITY_PRESET = "extra_quality_preset"
        private const val EXTRA_OFFER_SDP = "extra_offer_sdp"

        fun startIncomingCall(context: Context, session: CallSession) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, IncomingCallService::class.java).apply {
                action = ACTION_START_INCOMING_CALL
                putExtra(EXTRA_CALL_ID, session.id)
                putExtra(EXTRA_CALLER_ID, session.callerId)
                putExtra(EXTRA_CALLER_NAME, session.callerName)
                putExtra(EXTRA_CALLER_PHOTO, session.callerPhotoUrl)
                putExtra(EXTRA_CALLEE_ID, session.calleeId)
                putExtra(EXTRA_CONVERSATION_ID, session.conversationId)
                putExtra(EXTRA_CALL_TYPE, session.callType)
                putExtra(EXTRA_QUALITY_PRESET, session.qualityPreset)
                putExtra(EXTRA_OFFER_SDP, session.offerSdp)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }.onFailure { err ->
                Log.w(TAG, "startForegroundService failed; posting notification fallback", err)
                IncomingCallCoordinator.onIncomingCallDetected(appContext, session, startRingtone = true)
                val notif = NotificationHelper.buildIncomingCallNotification(
                    context = appContext,
                    callId = session.id,
                    callerName = session.callerName,
                    isVideo = session.isVideoCall,
                    callerAvatarBitmap = null
                )
                if (NotificationHelper.hasNotificationPermission(appContext)) {
                    runCatching {
                        NotificationManagerCompat.from(appContext)
                            .notify(NotificationHelper.INCOMING_CALL_NOTIFICATION_ID, notif)
                    }
                }
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, IncomingCallService::class.java).apply {
                action = ACTION_STOP_INCOMING_CALL
            }
            runCatching { appContext.stopService(intent) }
            NotificationHelper.cancelIncomingCallNotification(appContext)
        }

        private fun extractCallSession(intent: Intent?): CallSession? {
            if (intent == null) return null
            val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
            val callerId = intent.getStringExtra(EXTRA_CALLER_ID).orEmpty()
            if (callId.isBlank() || callerId.isBlank()) return null

            val calleeId = intent.getStringExtra(EXTRA_CALLEE_ID).orEmpty()
            return CallSession(
                id = callId,
                callerId = callerId,
                callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty().ifBlank { "Member" },
                callerPhotoUrl = intent.getStringExtra(EXTRA_CALLER_PHOTO)?.takeIf { it.isNotBlank() },
                calleeId = calleeId,
                participantIds = listOfNotNull(
                    callerId,
                    calleeId.takeIf { it.isNotBlank() }
                ),
                conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty(),
                callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: CallSession.TYPE_VOICE,
                qualityPreset = intent.getStringExtra(EXTRA_QUALITY_PRESET) ?: CallQualityPreset.HD_VOICE.wireValue,
                status = CallSession.STATUS_RINGING,
                offerSdp = intent.getStringExtra(EXTRA_OFFER_SDP)?.takeIf { it.isNotBlank() }
            )
        }
    }
}
