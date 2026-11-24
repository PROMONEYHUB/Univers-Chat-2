package com.example.notifications

import android.content.Context
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.sync.UniversAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class InAppMessageBanner(
    val conversationId: String,
    val title: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val previewText: String,
    val isGroup: Boolean,
    val timestampMillis: Long
)

object MessageNotificationCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var observedUserId: String? = null

    @Volatile
    private var notificationsEnabled: Boolean = true

    @Volatile
    private var notificationPreviewEnabled: Boolean = true

    @Volatile
    private var blockedUserIds: Set<String> = emptySet()

    @Volatile
    private var appInForeground: Boolean = false

    @Volatile
    private var activeConversationId: String? = null

    private val lastSeenMessageTimestamps = ConcurrentHashMap<String, Long>()
    private val lastNotifiedEventKeys = ConcurrentHashMap<String, String>()
    private var hasSeededInitialConversations = false

    private val _inAppBanners = MutableSharedFlow<InAppMessageBanner>(extraBufferCapacity = 8)
    val inAppBanners: SharedFlow<InAppMessageBanner> = _inAppBanners.asSharedFlow()

    private val _pendingDeepLinkConversationId = MutableStateFlow<String?>(null)
    val pendingDeepLinkConversationId: StateFlow<String?> = _pendingDeepLinkConversationId.asStateFlow()

    fun setAppForegroundState(inForeground: Boolean) {
        appInForeground = inForeground
    }

    fun isAppInForeground(): Boolean = appInForeground

    fun getActiveConversationId(): String? = activeConversationId

    fun setActiveConversationId(conversationId: String?) {
        activeConversationId = conversationId
    }

    fun emitDeepLinkConversation(conversationId: String?) {
        if (!conversationId.isNullOrBlank()) {
            _pendingDeepLinkConversationId.value = conversationId
        }
    }

    fun consumeDeepLinkConversation() {
        _pendingDeepLinkConversationId.value = null
    }

    fun updatePreferences(
        enabled: Boolean,
        previewEnabled: Boolean = true,
        blockedIds: Set<String>
    ) {
        notificationsEnabled = enabled
        notificationPreviewEnabled = previewEnabled
        blockedUserIds = blockedIds
    }

    fun startObservingForUser(context: Context, userId: String) {
        val appContext = context.applicationContext
        if (observedUserId != userId) {
            resetTrackingForUser(userId)
        }
        UniversAppContainer.get(appContext).syncCoordinator.startSession(userId, hasFirebaseAuth = true)
    }

    fun resetTrackingForUser(userId: String) {
        observedUserId = userId
        hasSeededInitialConversations = false
        lastSeenMessageTimestamps.clear()
        lastNotifiedEventKeys.clear()
    }

    fun stopObserving() {
        observedUserId = null
        hasSeededInitialConversations = false
        lastSeenMessageTimestamps.clear()
        lastNotifiedEventKeys.clear()
    }

    fun shouldSuppressConversationNotification(conversationId: String): Boolean {
        return appInForeground && activeConversationId == conversationId
    }

    fun onConversationsSnapshot(
        context: Context,
        currentUserId: String,
        conversations: List<Conversation>
    ) {
        if (observedUserId != currentUserId) {
            resetTrackingForUser(currentUserId)
        }

        if (!hasSeededInitialConversations) {
            conversations.forEach { conv ->
                val ts = conv.lastMessageAt?.toDate()?.time ?: 0L
                lastSeenMessageTimestamps[conv.id] = ts
            }
            hasSeededInitialConversations = true
            return
        }

        val appContext = context.applicationContext
        for (conv in conversations) {
            val latestTs = conv.lastMessageAt?.toDate()?.time ?: continue
            val previousTs = lastSeenMessageTimestamps[conv.id] ?: 0L
            if (latestTs <= previousTs) continue

            lastSeenMessageTimestamps[conv.id] = latestTs

            val senderId = conv.lastMessageSenderId
            if (senderId.isBlank() || senderId == currentUserId) continue
            if (senderId in blockedUserIds) continue
            if (conv.isMutedBy(currentUserId)) continue
            if (conv.unreadCountFor(currentUserId) <= 0L) continue
            if (!notificationsEnabled) continue

            val eventKey = "${conv.id}|$latestTs|$senderId|${conv.lastMessageText}"
            if (lastNotifiedEventKeys[conv.id] == eventKey) continue
            lastNotifiedEventKeys[conv.id] = eventKey

            if (shouldSuppressConversationNotification(conv.id)) continue

            val convTitle = conv.displayTitleFor(currentUserId)
            val senderName = conv.lastMessageSenderName.ifBlank { convTitle }
            val preview = formatNotificationPreview(conv, notificationPreviewEnabled)
            val otherPhoto = conv.participantPhotos[senderId]

            if (appInForeground) {
                _inAppBanners.tryEmit(
                    InAppMessageBanner(
                        conversationId = conv.id,
                        title = convTitle,
                        senderName = senderName,
                        senderPhotoUrl = otherPhoto,
                        previewText = preview,
                        isGroup = conv.type == Conversation.TYPE_GROUP,
                        timestampMillis = latestTs
                    )
                )
            }

            scope.launch {
                val avatarBitmap = NotificationHelper.loadCircularAvatarBitmap(otherPhoto)
                NotificationHelper.showIncomingMessageNotification(
                    context = appContext,
                    conversation = conv,
                    conversationTitle = convTitle,
                    senderName = senderName,
                    messageText = preview,
                    timestampMillis = latestTs,
                    senderAvatarBitmap = avatarBitmap
                )
            }
        }
    }

    private fun formatNotificationPreview(
        conversation: Conversation,
        showPreview: Boolean
    ): String {
        if (!showPreview) {
            return when (conversation.lastMessageType) {
                Message.TYPE_IMAGE -> "New photo"
                Message.TYPE_POLL -> "New poll"
                Message.TYPE_AUDIO -> "New voice message"
                else -> "New message"
            }
        }
        val raw = conversation.lastMessageText.trim()
        if (raw.isNotEmpty()) return raw
        return when (conversation.lastMessageType) {
            Message.TYPE_IMAGE -> "📷 Shared a photo"
            Message.TYPE_POLL -> "📊 Started a live poll"
            Message.TYPE_AUDIO -> "🎤 Sent a voice message"
            else -> "Sent a message"
        }
    }
}
