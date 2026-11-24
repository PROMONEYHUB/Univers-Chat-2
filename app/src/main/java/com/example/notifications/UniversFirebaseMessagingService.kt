package com.example.notifications

import android.util.Log
import com.example.calls.IncomingCallCoordinator
import com.example.calls.IncomingCallService
import com.example.data.model.CallQualityPreset
import com.example.data.model.CallSession
import com.example.data.model.Message
import com.example.data.sync.UniversAppContainer
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/**
 * Handles high-priority FCM data messages when the app is in background, Doze, or killed:
 * - Rotates and syncs FCM registration tokens
 * - Displays grouped MessagingStyle notifications with inline Reply, Mark as read, mute & preview privacy checks
 * - Launches the full-screen incoming call service & ringtone, and handles caller cancel / missed calls
 */
class UniversFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenManager.onTokenRefreshed(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) return

        when (data["kind"]) {
            "message" -> handleIncomingMessagePush(data)
            "call_incoming" -> handleIncomingCallPush(data)
            "call_state_changed" -> handleCallStateChangedPush(data)
        }
    }

    private fun handleIncomingMessagePush(data: Map<String, String>) {
        val conversationId = data["conversationId"].orEmpty()
        if (conversationId.isBlank()) return

        val senderId = data["senderId"].orEmpty()
        val recipientId = data["recipientId"].orEmpty()
        val currentUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (currentUid != null && recipientId.isNotBlank() && currentUid != recipientId) {
            return
        }
        if (senderId.isNotBlank() && senderId == currentUid) return
        if (MessageNotificationCoordinator.shouldSuppressConversationNotification(conversationId)) return

        runBlocking {
            val container = UniversAppContainer.get(applicationContext)
            val uid = currentUid ?: recipientId
            val privateUser = if (uid.isNotBlank()) container.dao.getPrivateUserOnce(uid)?.toDomain() else null

            if (privateUser != null) {
                if (!privateUser.notificationsEnabled) return@runBlocking
                if (senderId.isNotBlank() && senderId in privateUser.blockedUserIds) return@runBlocking
            }

            if (uid.isNotBlank()) {
                val cachedConv = container.dao.getConversationOnce(uid, conversationId)?.toDomain()
                if (cachedConv?.isMutedBy(uid) == true) return@runBlocking
            }

            val showPreview = (privateUser?.notificationPreviewEnabled ?: true) &&
                (data["showPreview"] != "false")
            val messageType = data["messageType"] ?: Message.TYPE_TEXT
            val rawBody = data["previewBody"].orEmpty()
            val resolvedBody = if (showPreview && rawBody.isNotBlank()) {
                rawBody
            } else {
                when (messageType) {
                    Message.TYPE_IMAGE -> "New photo"
                    Message.TYPE_POLL -> "New poll"
                    Message.TYPE_AUDIO -> "New voice message"
                    else -> "New message"
                }
            }

            val senderName = data["senderName"].orEmpty().ifBlank { "Univers Member" }
            val conversationTitle = data["conversationTitle"].orEmpty().ifBlank { senderName }
            val isGroup = data["conversationType"] == "group"
            val sentAtMillis = data["sentAtMillis"]?.toLongOrNull() ?: System.currentTimeMillis()
            val avatarBitmap = NotificationHelper.loadCircularAvatarBitmap(data["senderPhotoUrl"])

            NotificationHelper.showIncomingMessageNotificationFromPush(
                context = applicationContext,
                conversationId = conversationId,
                isGroup = isGroup,
                conversationTitle = conversationTitle,
                senderName = senderName,
                messageText = resolvedBody,
                timestampMillis = sentAtMillis,
                senderAvatarBitmap = avatarBitmap
            )
        }
    }

    private fun handleIncomingCallPush(data: Map<String, String>) {
        val callId = data["callId"].orEmpty()
        val callerId = data["callerId"].orEmpty()
        val calleeId = data["calleeId"].orEmpty()
        if (callId.isBlank() || callerId.isBlank()) return

        val currentUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (currentUid != null && calleeId.isNotBlank() && currentUid != calleeId) return

        val sentAt = data["sentAtMillis"]?.toLongOrNull() ?: 0L
        if (sentAt > 0L && (System.currentTimeMillis() - sentAt) > IncomingCallCoordinator.STALE_PUSH_MS) {
            Log.d(TAG, "Ignoring stale call push $callId")
            return
        }

        val session = CallSession(
            id = callId,
            callerId = callerId,
            callerName = data["callerName"].orEmpty().ifBlank { "Member" },
            callerPhotoUrl = data["callerPhotoUrl"]?.takeIf { it.isNotBlank() },
            calleeId = calleeId.ifBlank { currentUid.orEmpty() },
            calleeName = "",
            participantIds = listOf(callerId, calleeId.ifBlank { currentUid.orEmpty() }),
            conversationId = data["conversationId"].orEmpty(),
            callType = data["callType"] ?: CallSession.TYPE_VOICE,
            qualityPreset = data["qualityPreset"] ?: CallQualityPreset.HD_VOICE.wireValue,
            status = CallSession.STATUS_RINGING,
            offerSdp = data["offerSdp"]?.takeIf { it.isNotBlank() }
        )

        runBlocking {
            val container = UniversAppContainer.get(applicationContext)
            val uid = currentUid ?: calleeId
            val privateUser = if (uid.isNotBlank()) container.dao.getPrivateUserOnce(uid)?.toDomain() else null
            if (privateUser != null) {
                if (!privateUser.notificationsEnabled) return@runBlocking
                if (callerId in privateUser.blockedUserIds) return@runBlocking
            }
            IncomingCallService.startIncomingCall(applicationContext, session)
        }
    }

    private fun handleCallStateChangedPush(data: Map<String, String>) {
        val callId = data["callId"].orEmpty()
        if (callId.isBlank()) return
        val status = data["status"].orEmpty()
        val callerName = data["callerName"].orEmpty().ifBlank { "Member" }
        val conversationId = data["conversationId"]
        val isVideo = data["callType"] == CallSession.TYPE_VIDEO

        IncomingCallCoordinator.onRemoteCallTerminated(
            context = applicationContext,
            callId = callId,
            terminalStatus = status,
            callerName = callerName,
            conversationId = conversationId,
            isVideo = isVideo,
            callerPhotoUrl = data["callerPhotoUrl"]
        )
    }

    private companion object {
        const val TAG = "UniversFcmService"
    }
}
