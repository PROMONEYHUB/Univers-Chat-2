package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class MessageTickState {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@IgnoreExtraProperties
data class PrivateUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val readReceiptsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationPreviewEnabled: Boolean = true,
    val onlineStatusVisible: Boolean = true,
    val fcmTokens: Map<String, String> = emptyMap(),
    val blockedUserIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val statusVibe: String = "Available",
    @get:PropertyName("isOnline") @field:PropertyName("isOnline")
    val isOnline: Boolean = false,
    @get:PropertyName("onlineStatusVisible") @field:PropertyName("onlineStatusVisible")
    val onlineStatusVisible: Boolean = true,
    @get:PropertyName("isDiscoverable") @field:PropertyName("isDiscoverable")
    val isDiscoverable: Boolean = true,
    @ServerTimestamp val lastHeartbeatAt: Timestamp? = null,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    /**
     * Presence is only shown when:
     * 1. Both viewer and target have onlineStatusVisible enabled (bidirectional privacy),
     * 2. Target's foreground online flag is true, and
     * 3. Latest heartbeat (or activity timestamp) is within [HEARTBEAT_STALE_MS] (60s).
     */
    fun isEffectivelyOnline(
        nowMillis: Long = System.currentTimeMillis(),
        viewerShowsOnline: Boolean = true
    ): Boolean {
        if (!viewerShowsOnline || !onlineStatusVisible || !isOnline) return false
        val heartbeatMs = (lastHeartbeatAt ?: lastSeenAt ?: updatedAt)?.toDate()?.time ?: return false
        return (nowMillis - heartbeatMs) <= HEARTBEAT_STALE_MS
    }

    fun visibleLastSeen(viewerShowsOnline: Boolean = true): Timestamp? {
        if (!viewerShowsOnline || !onlineStatusVisible) return null
        return lastSeenAt ?: lastHeartbeatAt
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 25_000L
        const val HEARTBEAT_STALE_MS = 60_000L
    }
}

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = TYPE_DIRECT,
    val title: String = "",
    val topic: String = "",
    val createdBy: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageType: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    val pinnedBy: Map<String, Boolean> = emptyMap(),
    val mutedBy: Map<String, Boolean> = emptyMap(),
    val typingBy: Map<String, Timestamp> = emptyMap(),
    val recordingBy: Map<String, Timestamp> = emptyMap(),
    val pinnedMessageId: String? = null,
    val pinnedMessagePreview: String? = null,
    val pinnedMessageSenderName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val lastOutgoingTickState: MessageTickState? = null
) {
    fun displayTitleFor(currentUserId: String): String {
        if (type == TYPE_GROUP) return title.ifBlank { "Group Chat" }
        val otherId = participantIds.firstOrNull { it != currentUserId }
        return if (otherId != null) {
            participantNames[otherId]?.takeIf { it.isNotBlank() } ?: "Direct Chat"
        } else {
            participantNames[currentUserId]?.takeIf { it.isNotBlank() } ?: "Saved Notes"
        }
    }

    fun otherParticipantId(currentUserId: String): String? {
        return if (type == TYPE_DIRECT) participantIds.firstOrNull { it != currentUserId } else null
    }

    fun unreadCountFor(userId: String): Long {
        return unreadCounts[userId] ?: 0L
    }

    fun isPinnedBy(userId: String): Boolean = pinnedBy[userId] == true

    fun isMutedBy(userId: String): Boolean = mutedBy[userId] == true

    fun activeTypingUserIds(
        excludeUserId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> = typingBy.entries
        .filter { (uid, ts) ->
            uid != excludeUserId && (nowMillis - ts.toDate().time) <= INDICATOR_EXPIRY_MS
        }
        .map { it.key }

    fun activeRecordingUserIds(
        excludeUserId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> = recordingBy.entries
        .filter { (uid, ts) ->
            uid != excludeUserId && (nowMillis - ts.toDate().time) <= INDICATOR_EXPIRY_MS
        }
        .map { it.key }

    fun typingOrRecordingLabel(
        currentUserId: String,
        peopleMap: Map<String, UserProfile> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val recordingIds = activeRecordingUserIds(currentUserId, nowMillis)
        if (recordingIds.isNotEmpty()) {
            return if (type == TYPE_GROUP) {
                val first = peopleMap[recordingIds.first()]?.displayName
                    ?: participantNames[recordingIds.first()]
                    ?: "Someone"
                if (recordingIds.size == 1) "${first.substringBefore(" ")} is recording voice…"
                else "${recordingIds.size} people recording voice…"
            } else {
                "recording voice…"
            }
        }
        val typingIds = activeTypingUserIds(currentUserId, nowMillis)
        if (typingIds.isNotEmpty()) {
            return if (type == TYPE_GROUP) {
                val first = peopleMap[typingIds.first()]?.displayName
                    ?: participantNames[typingIds.first()]
                    ?: "Someone"
                if (typingIds.size == 1) "${first.substringBefore(" ")} is typing…"
                else "${typingIds.size} people typing…"
            } else {
                "typing…"
            }
        }
        return null
    }

    companion object {
        const val TYPE_DIRECT = "direct"
        const val TYPE_GROUP = "group"
        const val INDICATOR_EXPIRY_MS = 4_000L
    }
}

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val participantIds: List<String> = emptyList(),
    val type: String = TYPE_TEXT,
    val text: String = "",
    val imageBase64: String? = null,
    val audioBase64: String? = null,
    val audioDurationSec: Int = 0,
    @get:PropertyName("isForwarded") @field:PropertyName("isForwarded")
    val isForwarded: Boolean = false,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val pollQuestion: String? = null,
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, String> = emptyMap(),
    @get:PropertyName("isEdited") @field:PropertyName("isEdited")
    val isEdited: Boolean = false,
    val clientMessageId: String = "",
    val deliveredTo: Map<String, Timestamp> = emptyMap(),
    val readBy: Map<String, Timestamp> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val deliveryState: String = STATUS_SENT
) {
    fun resolveOutgoingTickState(
        currentUserId: String,
        conversationParticipants: List<String> = participantIds,
        showReadReceipts: Boolean = true
    ): MessageTickState {
        return when (deliveryState) {
            STATUS_FAILED -> MessageTickState.FAILED
            STATUS_SENDING -> MessageTickState.SENDING
            else -> {
                val recipients = (if (conversationParticipants.isNotEmpty()) conversationParticipants else participantIds)
                    .filter { it != currentUserId }
                if (recipients.isEmpty()) {
                    return if (showReadReceipts) MessageTickState.READ else MessageTickState.DELIVERED
                }
                val isRead = showReadReceipts && if (recipients.size == 1) {
                    recipients[0] in readBy
                } else {
                    recipients.all { it in readBy }
                }
                if (isRead) {
                    MessageTickState.READ
                } else if (recipients.any { it in deliveredTo || it in readBy }) {
                    MessageTickState.DELIVERED
                } else {
                    MessageTickState.SENT
                }
            }
        }
    }

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_POLL = "poll"
        const val TYPE_AUDIO = "audio"

        const val STATUS_SENDING = "sending"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
        const val STATUS_FAILED = "failed"
    }
}

fun buildDirectConversationId(uidA: String, uidB: String): String {
    val sorted = listOf(uidA, uidB).sorted()
    return "dm_${sorted[0]}_${sorted[1]}"
}
