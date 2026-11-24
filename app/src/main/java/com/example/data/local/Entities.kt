package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageTickState
import com.example.data.model.PrivateUser
import com.example.data.model.UserProfile

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["ownerUserId", "lastMessageAtMillis"])
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val type: String,
    val title: String,
    val topic: String,
    val createdBy: String,
    val participantIdsJson: String,
    val participantNamesJson: String,
    val participantPhotosJson: String,
    val lastMessageText: String,
    val lastMessageSenderId: String,
    val lastMessageSenderName: String,
    val lastMessageType: String,
    val lastMessageAtMillis: Long?,
    val unreadCountsJson: String,
    val pinnedByJson: String,
    val mutedByJson: String,
    val typingByJson: String,
    val recordingByJson: String,
    val pinnedMessageId: String?,
    val pinnedMessagePreview: String?,
    val pinnedMessageSenderName: String?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val lastOutgoingTickState: String?
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        type = type,
        title = title,
        topic = topic,
        createdBy = createdBy,
        participantIds = RoomConverters.decodeStringList(participantIdsJson),
        participantNames = RoomConverters.decodeStringMap(participantNamesJson),
        participantPhotos = RoomConverters.decodeStringMap(participantPhotosJson),
        lastMessageText = lastMessageText,
        lastMessageSenderId = lastMessageSenderId,
        lastMessageSenderName = lastMessageSenderName,
        lastMessageType = lastMessageType,
        lastMessageAt = RoomConverters.millisToTimestamp(lastMessageAtMillis),
        unreadCounts = RoomConverters.decodeLongMap(unreadCountsJson),
        pinnedBy = RoomConverters.decodeBooleanMap(pinnedByJson),
        mutedBy = RoomConverters.decodeBooleanMap(mutedByJson),
        typingBy = RoomConverters.decodeTimestampMap(typingByJson),
        recordingBy = RoomConverters.decodeTimestampMap(recordingByJson),
        pinnedMessageId = pinnedMessageId,
        pinnedMessagePreview = pinnedMessagePreview,
        pinnedMessageSenderName = pinnedMessageSenderName,
        createdAt = RoomConverters.millisToTimestamp(createdAtMillis),
        updatedAt = RoomConverters.millisToTimestamp(updatedAtMillis),
        lastOutgoingTickState = lastOutgoingTickState?.let { raw ->
            runCatching { MessageTickState.valueOf(raw) }.getOrNull()
        }
    )
}

fun Conversation.toEntity(
    ownerUserId: String,
    overrideLastOutgoingTickState: MessageTickState? = lastOutgoingTickState
): ConversationEntity = ConversationEntity(
    id = id,
    ownerUserId = ownerUserId,
    type = type,
    title = title,
    topic = topic,
    createdBy = createdBy,
    participantIdsJson = RoomConverters.encodeStringList(participantIds),
    participantNamesJson = RoomConverters.encodeStringMap(participantNames),
    participantPhotosJson = RoomConverters.encodeStringMap(participantPhotos),
    lastMessageText = lastMessageText,
    lastMessageSenderId = lastMessageSenderId,
    lastMessageSenderName = lastMessageSenderName,
    lastMessageType = lastMessageType,
    lastMessageAtMillis = RoomConverters.timestampToMillis(lastMessageAt),
    unreadCountsJson = RoomConverters.encodeLongMap(unreadCounts),
    pinnedByJson = RoomConverters.encodeBooleanMap(pinnedBy),
    mutedByJson = RoomConverters.encodeBooleanMap(mutedBy),
    typingByJson = RoomConverters.encodeTimestampMap(typingBy),
    recordingByJson = RoomConverters.encodeTimestampMap(recordingBy),
    pinnedMessageId = pinnedMessageId,
    pinnedMessagePreview = pinnedMessagePreview,
    pinnedMessageSenderName = pinnedMessageSenderName,
    createdAtMillis = RoomConverters.timestampToMillis(createdAt),
    updatedAtMillis = RoomConverters.timestampToMillis(updatedAt),
    lastOutgoingTickState = overrideLastOutgoingTickState?.name
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["ownerUserId", "conversationId", "createdAtMillis"]),
        Index(value = ["conversationId", "deliveryState"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String,
    val participantIdsJson: String,
    val type: String,
    val text: String,
    val imageBase64: String?,
    val audioBase64: String?,
    val audioDurationSec: Int,
    val isForwarded: Boolean,
    val replyToMessageId: String?,
    val replyToSenderName: String?,
    val replyToPreview: String?,
    val reactionsJson: String,
    val pollQuestion: String?,
    val pollOptionsJson: String,
    val pollVotesJson: String,
    val isEdited: Boolean,
    val clientMessageId: String,
    val deliveredToJson: String,
    val readByJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deliveryState: String
) {
    fun toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        senderPhotoUrl = senderPhotoUrl,
        participantIds = RoomConverters.decodeStringList(participantIdsJson),
        type = type,
        text = text,
        imageBase64 = imageBase64,
        audioBase64 = audioBase64,
        audioDurationSec = audioDurationSec,
        isForwarded = isForwarded,
        replyToMessageId = replyToMessageId,
        replyToSenderName = replyToSenderName,
        replyToPreview = replyToPreview,
        reactions = RoomConverters.decodeStringMap(reactionsJson),
        pollQuestion = pollQuestion,
        pollOptions = RoomConverters.decodeStringList(pollOptionsJson),
        pollVotes = RoomConverters.decodeStringMap(pollVotesJson),
        isEdited = isEdited,
        clientMessageId = clientMessageId,
        deliveredTo = RoomConverters.decodeTimestampMap(deliveredToJson),
        readBy = RoomConverters.decodeTimestampMap(readByJson),
        createdAt = RoomConverters.millisToTimestamp(createdAtMillis),
        updatedAt = RoomConverters.millisToTimestamp(updatedAtMillis),
        deliveryState = deliveryState
    )
}

fun Message.toEntity(
    ownerUserId: String,
    fallbackCreatedAtMillis: Long = System.currentTimeMillis()
): MessageEntity {
    val createdMs = RoomConverters.timestampToMillis(createdAt) ?: fallbackCreatedAtMillis
    val updatedMs = RoomConverters.timestampToMillis(updatedAt) ?: createdMs
    return MessageEntity(
        id = id,
        ownerUserId = ownerUserId,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        senderPhotoUrl = senderPhotoUrl,
        participantIdsJson = RoomConverters.encodeStringList(participantIds),
        type = type,
        text = text,
        imageBase64 = imageBase64,
        audioBase64 = audioBase64,
        audioDurationSec = audioDurationSec,
        isForwarded = isForwarded,
        replyToMessageId = replyToMessageId,
        replyToSenderName = replyToSenderName,
        replyToPreview = replyToPreview,
        reactionsJson = RoomConverters.encodeStringMap(reactions),
        pollQuestion = pollQuestion,
        pollOptionsJson = RoomConverters.encodeStringList(pollOptions),
        pollVotesJson = RoomConverters.encodeStringMap(pollVotes),
        isEdited = isEdited,
        clientMessageId = clientMessageId.ifBlank { id },
        deliveredToJson = RoomConverters.encodeTimestampMap(deliveredTo),
        readByJson = RoomConverters.encodeTimestampMap(readBy),
        createdAtMillis = createdMs,
        updatedAtMillis = updatedMs,
        deliveryState = deliveryState
    )
}

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val handle: String,
    val bio: String,
    val photoUrl: String,
    val statusVibe: String,
    val isOnline: Boolean,
    val onlineStatusVisible: Boolean,
    val isDiscoverable: Boolean,
    val lastHeartbeatAtMillis: Long?,
    val lastSeenAtMillis: Long?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?
) {
    fun toDomain(): UserProfile = UserProfile(
        id = userId,
        userId = userId,
        displayName = displayName,
        handle = handle,
        bio = bio,
        photoUrl = photoUrl,
        statusVibe = statusVibe,
        isOnline = isOnline,
        onlineStatusVisible = onlineStatusVisible,
        isDiscoverable = isDiscoverable,
        lastHeartbeatAt = RoomConverters.millisToTimestamp(lastHeartbeatAtMillis),
        lastSeenAt = RoomConverters.millisToTimestamp(lastSeenAtMillis),
        createdAt = RoomConverters.millisToTimestamp(createdAtMillis),
        updatedAt = RoomConverters.millisToTimestamp(updatedAtMillis)
    )
}

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    userId = userId.ifBlank { id },
    displayName = displayName,
    handle = handle,
    bio = bio,
    photoUrl = photoUrl,
    statusVibe = statusVibe,
    isOnline = isOnline,
    onlineStatusVisible = onlineStatusVisible,
    isDiscoverable = isDiscoverable,
    lastHeartbeatAtMillis = RoomConverters.timestampToMillis(lastHeartbeatAt),
    lastSeenAtMillis = RoomConverters.timestampToMillis(lastSeenAt),
    createdAtMillis = RoomConverters.timestampToMillis(createdAt),
    updatedAtMillis = RoomConverters.timestampToMillis(updatedAt)
)

@Entity(tableName = "private_users")
data class PrivateUserEntity(
    @PrimaryKey val userId: String,
    val email: String,
    val readReceiptsEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val notificationPreviewEnabled: Boolean,
    val onlineStatusVisible: Boolean,
    val fcmTokensJson: String,
    val blockedUserIdsJson: String,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?
) {
    fun toDomain(): PrivateUser = PrivateUser(
        id = userId,
        userId = userId,
        email = email,
        readReceiptsEnabled = readReceiptsEnabled,
        notificationsEnabled = notificationsEnabled,
        notificationPreviewEnabled = notificationPreviewEnabled,
        onlineStatusVisible = onlineStatusVisible,
        fcmTokens = RoomConverters.decodeStringMap(fcmTokensJson),
        blockedUserIds = RoomConverters.decodeStringList(blockedUserIdsJson),
        createdAt = RoomConverters.millisToTimestamp(createdAtMillis),
        updatedAt = RoomConverters.millisToTimestamp(updatedAtMillis)
    )
}

fun PrivateUser.toEntity(): PrivateUserEntity = PrivateUserEntity(
    userId = userId.ifBlank { id },
    email = email,
    readReceiptsEnabled = readReceiptsEnabled,
    notificationsEnabled = notificationsEnabled,
    notificationPreviewEnabled = notificationPreviewEnabled,
    onlineStatusVisible = onlineStatusVisible,
    fcmTokensJson = RoomConverters.encodeStringMap(fcmTokens),
    blockedUserIdsJson = RoomConverters.encodeStringList(blockedUserIds),
    createdAtMillis = RoomConverters.timestampToMillis(createdAt),
    updatedAtMillis = RoomConverters.timestampToMillis(updatedAt)
)

@Entity(
    tableName = "outbox_messages",
    indices = [
        Index(value = ["ownerUserId", "status", "nextRetryAtMillis"]),
        Index(value = ["conversationId"])
    ]
)
data class OutboxMessageEntity(
    @PrimaryKey val clientMessageId: String,
    val ownerUserId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String,
    val participantIdsJson: String,
    val type: String,
    val text: String,
    val previewText: String,
    val imageBase64: String?,
    val audioBase64: String?,
    val audioDurationSec: Int?,
    val isForwarded: Boolean,
    val replyToMessageId: String?,
    val replyToSenderName: String?,
    val replyToPreview: String?,
    val pollQuestion: String?,
    val pollOptionsJson: String?,
    val retryCount: Int,
    val nextRetryAtMillis: Long,
    val createdAtMillis: Long,
    val status: String,
    val lastError: String?
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_FAILED = "failed"
    }
}
