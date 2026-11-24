package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.OutboxMessageEntity
import com.example.data.local.UniversDao
import com.example.data.local.toEntity
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageTickState
import com.example.data.repository.MessagingRepository
import com.example.data.repository.OutboxSendOutcome
import com.example.notifications.FcmTokenManager
import com.example.notifications.MessageNotificationCoordinator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Single source of truth for active Firestore listeners and local Room cache synchronization.
 * Guarantees at most ONE session observer set and ONE active chat observer at any time,
 * drives automatic delivery/read receipts, and processes the offline outbox queue.
 */
class RealtimeSyncCoordinator(
    private val appContext: Context,
    private val repository: MessagingRepository,
    private val dao: UniversDao,
    private val connectivityMonitor: ConnectivityMonitor,
    private val presenceManager: PresenceManager,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outboxMutex = Mutex()

    private var sessionJob: Job? = null
    private var outboxTickerJob: Job? = null
    private var activeChatJob: Job? = null

    @Volatile
    private var activeUserId: String? = null

    @Volatile
    private var activeHasFirebaseAuth: Boolean = false

    @Volatile
    private var activeChatId: String? = null

    @Volatile
    private var readReceiptsEnabled: Boolean = true

    private val deliveredConvTimestamps = ConcurrentHashMap<String, Long>()
    private val markedDeliveredMsgIds = ConcurrentHashMap.newKeySet<String>()
    private val markedReadMsgIds = ConcurrentHashMap.newKeySet<String>()

    private val _isSyncingSession = MutableStateFlow(false)
    val isSyncingSession: StateFlow<Boolean> = _isSyncingSession.asStateFlow()

    fun startSession(userId: String, hasFirebaseAuth: Boolean) {
        if (activeUserId == userId &&
            activeHasFirebaseAuth == hasFirebaseAuth &&
            (!hasFirebaseAuth || sessionJob?.isActive == true)
        ) {
            return
        }

        stopSession()
        activeUserId = userId
        activeHasFirebaseAuth = hasFirebaseAuth
        presenceManager.updateSession(userId, hasFirebaseAuth)

        if (!hasFirebaseAuth) {
            _isSyncingSession.value = false
            return
        }

        _isSyncingSession.value = true

        sessionJob = scope.launch {
            launch {
                val fbUser = auth.currentUser
                if (fbUser != null && fbUser.uid == userId) {
                    repository.syncCurrentUserProfile(fbUser).onFailure { err ->
                        Log.w(TAG, "Initial profile sync failed: ${err.message}")
                    }
                    FcmTokenManager.syncCurrentToken(appContext, repository, userId)
                }
            }

            launch {
                repository.observePrivateUser(userId)
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w(TAG, "Private user sync error", err)
                    }
                    .collect { privateUser ->
                        if (privateUser != null) {
                            readReceiptsEnabled = privateUser.readReceiptsEnabled
                            dao.upsertPrivateUser(privateUser.toEntity())
                            presenceManager.setOnlineStatusVisible(privateUser.onlineStatusVisible)
                            MessageNotificationCoordinator.updatePreferences(
                                enabled = privateUser.notificationsEnabled,
                                previewEnabled = privateUser.notificationPreviewEnabled,
                                blockedIds = privateUser.blockedUserIds.toSet()
                            )
                            refreshConversationTicks(userId, privateUser.readReceiptsEnabled)
                        }
                    }
            }

            launch {
                repository.observeDiscoverableProfiles()
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w(TAG, "Profiles sync error", err)
                    }
                    .collect { profiles ->
                        if (profiles.isNotEmpty()) {
                            dao.reconcileDiscoverableProfiles(profiles.map { it.toEntity() })
                        }
                    }
            }

            launch {
                repository.observeConversations(userId)
                    .catch { err ->
                        if (err is CancellationException) throw err
                        _isSyncingSession.value = false
                        Log.w(TAG, "Conversations sync error", err)
                    }
                    .collect { conversations ->
                        _isSyncingSession.value = false
                        val entities = conversations.map { conv ->
                            val existingMessages = dao.getMessagesOnce(userId, conv.id)
                            val lastOutgoing = existingMessages.lastOrNull { it.senderId == userId }?.toDomain()
                            val computedTick = if (lastOutgoing != null && conv.lastMessageSenderId == userId) {
                                lastOutgoing.resolveOutgoingTickState(
                                    currentUserId = userId,
                                    conversationParticipants = conv.participantIds,
                                    showReadReceipts = readReceiptsEnabled
                                )
                            } else {
                                conv.lastOutgoingTickState
                            }
                            conv.toEntity(userId, computedTick)
                        }
                        dao.reconcileConversations(userId, entities)

                        MessageNotificationCoordinator.onConversationsSnapshot(
                            context = appContext,
                            currentUserId = userId,
                            conversations = conversations
                        )

                        // Mark newly arrived incoming messages as delivered while online
                        for (conv in conversations) {
                            val ts = conv.lastMessageAt?.toDate()?.time ?: 0L
                            val lastMarkedTs = deliveredConvTimestamps[conv.id] ?: 0L
                            if (ts > lastMarkedTs &&
                                conv.lastMessageSenderId.isNotBlank() &&
                                conv.lastMessageSenderId != userId &&
                                conv.unreadCountFor(userId) > 0L
                            ) {
                                deliveredConvTimestamps[conv.id] = ts
                                launch {
                                    repository.markConversationMessagesDeliveredUpTo(conv.id, limitCount = 10)
                                }
                            }
                        }
                    }
            }

            launch {
                connectivityMonitor.isConnected.collect { connected ->
                    if (connected) {
                        flushOutbox(userId)
                    }
                }
            }
        }

        outboxTickerJob = scope.launch {
            while (isActive) {
                if (connectivityMonitor.isConnected.value) {
                    flushOutbox(userId)
                }
                delay(OUTBOX_POLL_INTERVAL_MS)
            }
        }
    }

    fun startChatSync(conversationId: String, userId: String) {
        if (!activeHasFirebaseAuth || activeUserId != userId) return
        if (activeChatId == conversationId && activeChatJob?.isActive == true) return

        activeChatJob?.cancel()
        activeChatId = conversationId

        activeChatJob = scope.launch {
            launch {
                repository.observeConversation(conversationId)
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w(TAG, "Chat conversation sync error for $conversationId", err)
                    }
                    .collect { conv ->
                        if (conv != null) {
                            val existing = dao.getConversationOnce(userId, conv.id)
                            dao.upsertConversation(
                                conv.toEntity(
                                    ownerUserId = userId,
                                    overrideLastOutgoingTickState = existing?.toDomain()?.lastOutgoingTickState
                                )
                            )
                        }
                    }
            }

            launch {
                repository.observeLatestMessagesPaged(
                    conversationId = conversationId,
                    userId = userId,
                    pageSize = MessagingRepository.DEFAULT_PAGE_SIZE
                )
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w(TAG, "Paged message sync error for $conversationId", err)
                    }
                    .collect { pagedResult ->
                        val remoteMessages = pagedResult.messages
                        val entities = remoteMessages.map { it.toEntity(userId) }
                        dao.reconcileLatestMessagesWindow(
                            ownerUserId = userId,
                            conversationId = conversationId,
                            remoteMessages = entities,
                            isFullSnapshot = pagedResult.isFullSnapshotFallback ||
                                remoteMessages.size < MessagingRepository.DEFAULT_PAGE_SIZE
                        )

                        // Mark incoming messages delivered + read (if enabled)
                        val incomingToDeliver = remoteMessages
                            .filter {
                                it.senderId != userId &&
                                    userId !in it.deliveredTo &&
                                    markedDeliveredMsgIds.add("${it.id}_d")
                            }
                            .map { it.id }
                        if (incomingToDeliver.isNotEmpty()) {
                            launch { repository.markMessagesDelivered(conversationId, incomingToDeliver) }
                        }

                        if (readReceiptsEnabled) {
                            val incomingToRead = remoteMessages
                                .filter {
                                    it.senderId != userId &&
                                        userId !in it.readBy &&
                                        markedReadMsgIds.add("${it.id}_r")
                                }
                                .map { it.id }
                            if (incomingToRead.isNotEmpty()) {
                                launch { repository.markMessagesRead(conversationId, incomingToRead) }
                            }
                        }

                        updateLastOutgoingTickForConversation(userId, conversationId, remoteMessages)
                    }
            }
        }
    }

    fun stopChatSync(conversationId: String) {
        if (activeChatId == conversationId) {
            activeChatJob?.cancel()
            activeChatJob = null
            activeChatId = null
        }
    }

    suspend fun loadOlderMessages(conversationId: String, userId: String): Result<Boolean> {
        if (!activeHasFirebaseAuth || activeUserId != userId) {
            return Result.success(false)
        }
        val oldestTs = dao.getOldestConfirmedMessageTimestamp(userId, conversationId)
            ?: return Result.success(false)
        return repository.fetchOlderMessagesPage(
            conversationId = conversationId,
            userId = userId,
            beforeMillis = oldestTs,
            pageSize = MessagingRepository.DEFAULT_PAGE_SIZE
        ).map { olderList ->
            if (olderList.isNotEmpty()) {
                dao.upsertMessages(olderList.map { it.toEntity(userId) })
            }
            olderList.size >= MessagingRepository.DEFAULT_PAGE_SIZE
        }
    }

    fun triggerOutboxFlush() {
        val uid = activeUserId ?: return
        if (!activeHasFirebaseAuth) return
        scope.launch {
            flushOutbox(uid)
        }
    }

    private suspend fun flushOutbox(userId: String) {
        if (!connectivityMonitor.isConnected.value || auth.currentUser?.uid != userId) return
        outboxMutex.withLock {
            val due = dao.getDueOutboxMessages(userId, System.currentTimeMillis())
            for (item in due) {
                if (!connectivityMonitor.isConnected.value || auth.currentUser?.uid != userId) break
                when (val outcome = repository.sendOutboxItem(item)) {
                    is OutboxSendOutcome.Sent -> Unit
                    is OutboxSendOutcome.Failure -> {
                        val nextAttempt = item.retryCount + 1
                        if (outcome.isPermanent || nextAttempt >= MAX_AUTO_RETRIES) {
                            dao.markOutboxMessageFailed(item.clientMessageId, outcome.errorMessage)
                            dao.updateConversationLastTickState(
                                ownerUserId = item.ownerUserId,
                                conversationId = item.conversationId,
                                tickState = MessageTickState.FAILED.name
                            )
                        } else {
                            val backoffMs = min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L shl nextAttempt.coerceAtMost(6)))
                            dao.upsertOutbox(
                                item.copy(
                                    retryCount = nextAttempt,
                                    nextRetryAtMillis = System.currentTimeMillis() + backoffMs,
                                    lastError = outcome.errorMessage
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateLastOutgoingTickForConversation(
        userId: String,
        conversationId: String,
        latestMessages: List<Message>
    ) {
        val conv = dao.getConversationOnce(userId, conversationId)?.toDomain() ?: return
        val lastMsg = latestMessages.lastOrNull() ?: return
        if (lastMsg.senderId == userId) {
            val tick = lastMsg.resolveOutgoingTickState(
                currentUserId = userId,
                conversationParticipants = conv.participantIds,
                showReadReceipts = readReceiptsEnabled
            )
            dao.updateConversationLastTickState(userId, conversationId, tick.name)
        } else {
            dao.updateConversationLastTickState(userId, conversationId, null)
        }
    }

    private suspend fun refreshConversationTicks(userId: String, showReadReceipts: Boolean) {
        val conversations = dao.getConversationsOnce(userId)
        for (entity in conversations) {
            val conv = entity.toDomain()
            if (conv.lastMessageSenderId == userId) {
                val lastOutgoing = dao.getMessagesOnce(userId, conv.id)
                    .lastOrNull { it.senderId == userId }
                    ?.toDomain()
                if (lastOutgoing != null) {
                    val tick = lastOutgoing.resolveOutgoingTickState(
                        currentUserId = userId,
                        conversationParticipants = conv.participantIds,
                        showReadReceipts = showReadReceipts
                    )
                    dao.updateConversationLastTickState(userId, conv.id, tick.name)
                }
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        outboxTickerJob?.cancel()
        outboxTickerJob = null
        activeChatJob?.cancel()
        activeChatJob = null
        activeChatId = null
        activeUserId = null
        activeHasFirebaseAuth = false
        _isSyncingSession.value = false
        deliveredConvTimestamps.clear()
        markedDeliveredMsgIds.clear()
        markedReadMsgIds.clear()
        presenceManager.updateSession(null, false)
    }

    private companion object {
        const val TAG = "RealtimeSyncCoord"
        const val OUTBOX_POLL_INTERVAL_MS = 2_500L
        const val BASE_BACKOFF_MS = 1_500L
        const val MAX_BACKOFF_MS = 60_000L
        const val MAX_AUTO_RETRIES = 6
    }
}
