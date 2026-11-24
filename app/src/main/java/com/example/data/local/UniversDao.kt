package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface UniversDao {

    // ---- Conversations ----

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId")
    fun observeConversations(ownerUserId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId")
    suspend fun getConversationsOnce(ownerUserId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId AND id = :conversationId LIMIT 1")
    fun observeConversation(ownerUserId: String, conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId AND id = :conversationId LIMIT 1")
    suspend fun getConversationOnce(ownerUserId: String, conversationId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(entity: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(entities: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE ownerUserId = :ownerUserId AND id NOT IN (:activeIds)")
    suspend fun deleteConversationsNotIn(ownerUserId: String, activeIds: List<String>)

    @Query("DELETE FROM conversations WHERE ownerUserId = :ownerUserId")
    suspend fun deleteAllConversations(ownerUserId: String)

    @Transaction
    suspend fun reconcileConversations(ownerUserId: String, remoteConversations: List<ConversationEntity>) {
        val existingById = getConversationsOnce(ownerUserId).associateBy { it.id }
        val merged = remoteConversations.map { remote ->
            val existing = existingById[remote.id]
            val resolvedTick = remote.lastOutgoingTickState ?: existing?.lastOutgoingTickState
            if (resolvedTick != remote.lastOutgoingTickState) {
                remote.copy(lastOutgoingTickState = resolvedTick)
            } else {
                remote
            }
        }
        if (merged.isEmpty()) {
            deleteAllConversations(ownerUserId)
        } else {
            upsertConversations(merged)
            deleteConversationsNotIn(ownerUserId, merged.map { it.id })
        }
    }

    @Query(
        "UPDATE conversations SET lastMessageText = :previewText, lastMessageSenderId = :senderId, " +
            "lastMessageSenderName = :senderName, lastMessageType = :messageType, " +
            "lastMessageAtMillis = :timestampMillis, updatedAtMillis = :timestampMillis, " +
            "lastOutgoingTickState = :tickState WHERE ownerUserId = :ownerUserId AND id = :conversationId"
    )
    suspend fun updateConversationOptimisticPreview(
        ownerUserId: String,
        conversationId: String,
        previewText: String,
        senderId: String,
        senderName: String,
        messageType: String,
        timestampMillis: Long,
        tickState: String
    )

    @Query(
        "UPDATE conversations SET lastOutgoingTickState = :tickState " +
            "WHERE ownerUserId = :ownerUserId AND id = :conversationId"
    )
    suspend fun updateConversationLastTickState(
        ownerUserId: String,
        conversationId: String,
        tickState: String?
    )

    // ---- Messages ----

    @Query(
        "SELECT * FROM messages WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId " +
            "ORDER BY createdAtMillis ASC, id ASC"
    )
    fun observeMessages(ownerUserId: String, conversationId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId " +
            "ORDER BY createdAtMillis ASC, id ASC"
    )
    suspend fun getMessagesOnce(ownerUserId: String, conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query(
        "SELECT MIN(createdAtMillis) FROM messages " +
            "WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId " +
            "AND deliveryState NOT IN ('sending', 'failed')"
    )
    suspend fun getOldestConfirmedMessageTimestamp(ownerUserId: String, conversationId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(entities: List<MessageEntity>)

    @Query(
        "DELETE FROM messages WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId " +
            "AND deliveryState NOT IN ('sending', 'failed') AND createdAtMillis >= :windowStartMillis"
    )
    suspend fun deleteAllConfirmedInWindow(
        ownerUserId: String,
        conversationId: String,
        windowStartMillis: Long
    )

    @Query(
        "DELETE FROM messages WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId " +
            "AND deliveryState NOT IN ('sending', 'failed') AND createdAtMillis >= :windowStartMillis " +
            "AND id NOT IN (:activeIds)"
    )
    suspend fun deleteMissingConfirmedInWindow(
        ownerUserId: String,
        conversationId: String,
        windowStartMillis: Long,
        activeIds: List<String>
    )

    @Query(
        "UPDATE messages SET deliveryState = :deliveryState WHERE id = :messageId"
    )
    suspend fun updateMessageDeliveryState(messageId: String, deliveryState: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Transaction
    suspend fun reconcileLatestMessagesWindow(
        ownerUserId: String,
        conversationId: String,
        remoteMessages: List<MessageEntity>,
        isFullSnapshot: Boolean
    ) {
        if (remoteMessages.isEmpty()) {
            if (isFullSnapshot) {
                deleteAllConfirmedInWindow(ownerUserId, conversationId, Long.MIN_VALUE)
            }
            return
        }
        val windowStart = if (isFullSnapshot) {
            Long.MIN_VALUE
        } else {
            remoteMessages.minOf { it.createdAtMillis }
        }
        val remoteIds = remoteMessages.map { it.id }
        deleteMissingConfirmedInWindow(ownerUserId, conversationId, windowStart, remoteIds)
        upsertMessages(remoteMessages)
    }

    // ---- Profiles & Private User ----

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeUserProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isDiscoverable = 1")
    fun observeDiscoverableProfiles(): Flow<List<UserProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfiles(profiles: List<UserProfileEntity>)

    @Query("DELETE FROM user_profiles WHERE isDiscoverable = 1 AND userId NOT IN (:activeIds)")
    suspend fun deleteMissingDiscoverableProfiles(activeIds: List<String>)

    @Transaction
    suspend fun reconcileDiscoverableProfiles(profiles: List<UserProfileEntity>) {
        if (profiles.isNotEmpty()) {
            upsertUserProfiles(profiles)
            deleteMissingDiscoverableProfiles(profiles.map { it.userId })
        }
    }

    @Query("SELECT * FROM private_users WHERE userId = :userId LIMIT 1")
    fun observePrivateUser(userId: String): Flow<PrivateUserEntity?>

    @Query("SELECT * FROM private_users WHERE userId = :userId LIMIT 1")
    suspend fun getPrivateUserOnce(userId: String): PrivateUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivateUser(privateUser: PrivateUserEntity)

    // ---- Offline Message Outbox ----

    @Query(
        "SELECT COUNT(*) FROM outbox_messages WHERE ownerUserId = :ownerUserId " +
            "AND status = '${OutboxMessageEntity.STATUS_PENDING}'"
    )
    fun observePendingOutboxCount(ownerUserId: String): Flow<Int>

    @Query(
        "SELECT * FROM outbox_messages WHERE ownerUserId = :ownerUserId " +
            "AND status = '${OutboxMessageEntity.STATUS_PENDING}' AND nextRetryAtMillis <= :nowMillis " +
            "ORDER BY createdAtMillis ASC"
    )
    suspend fun getDueOutboxMessages(ownerUserId: String, nowMillis: Long): List<OutboxMessageEntity>

    @Query(
        "SELECT * FROM outbox_messages WHERE ownerUserId = :ownerUserId " +
            "AND status = '${OutboxMessageEntity.STATUS_PENDING}' ORDER BY createdAtMillis ASC"
    )
    suspend fun getAllPendingOutbox(ownerUserId: String): List<OutboxMessageEntity>

    @Query("SELECT * FROM outbox_messages WHERE clientMessageId = :clientMessageId LIMIT 1")
    suspend fun getOutboxById(clientMessageId: String): OutboxMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(item: OutboxMessageEntity)

    @Query("DELETE FROM outbox_messages WHERE clientMessageId = :clientMessageId")
    suspend fun deleteOutbox(clientMessageId: String)

    @Transaction
    suspend fun markMessageAsSentLocally(
        ownerUserId: String,
        clientMessageId: String,
        sentAtMillis: Long
    ) {
        deleteOutbox(clientMessageId)
        val existing = getMessageById(clientMessageId) ?: return
        upsertMessage(
            existing.copy(
                deliveryState = Message.STATUS_SENT,
                updatedAtMillis = sentAtMillis
            )
        )
    }

    @Transaction
    suspend fun markOutboxMessageFailed(
        clientMessageId: String,
        errorMessage: String?
    ) {
        val outbox = getOutboxById(clientMessageId)
        if (outbox != null) {
            upsertOutbox(
                outbox.copy(
                    status = OutboxMessageEntity.STATUS_FAILED,
                    lastError = errorMessage
                )
            )
        }
        updateMessageDeliveryState(clientMessageId, Message.STATUS_FAILED)
    }
}
