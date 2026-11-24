package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.OutboxMessageEntity
import com.example.data.local.RoomConverters
import com.example.data.local.UniversDao
import com.example.data.local.UniversDatabase
import com.example.data.local.toEntity
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageTickState
import com.example.data.model.PrivateUser
import com.example.data.model.UserProfile
import com.example.data.model.buildDirectConversationId
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.ui.auth.AuthProfilePrefill
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

data class PagedMessagesResult(
    val messages: List<Message>,
    val isFullSnapshotFallback: Boolean,
    val mayHaveOlderMessages: Boolean
)

sealed interface OutboxSendOutcome {
    data class Sent(val sentAtMillis: Long) : OutboxSendOutcome
    data class Failure(val errorMessage: String, val isPermanent: Boolean) : OutboxSendOutcome
}

class MessagingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val localDao: UniversDao? = null
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        localDao = UniversDatabase.getInstance(context.applicationContext).universDao()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    private fun Throwable.findFirestoreException(): FirebaseFirestoreException? {
        return generateSequence(this) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun <T> Flow<T>.logFirestoreErrors(
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Flow<T> = catch { error ->
        val firestoreError = error.findFirestoreException()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
        throw error
    }

    fun generateClientMessageId(): String {
        return "msg_${UUID.randomUUID().toString().replace("-", "")}"
    }

    suspend fun syncCurrentUserProfile(firebaseUser: FirebaseUser): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid == firebaseUser.uid) { "Authenticated UID mismatch" }

        val email = firebaseUser.email?.takeIf { it.isNotBlank() }
            ?: firebaseUser.phoneNumber?.takeIf { it.isNotBlank() }
            ?: "${uid}@universchat.local"
        val privateRef = db.collection(USERS_COLLECTION).document(uid)
        val profileRef = db.collection(USER_PROFILES_COLLECTION).document(uid)

        var onlineStatusVisible = true
        try {
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "email" to email.take(320),
                    "readReceiptsEnabled" to true,
                    "notificationsEnabled" to true,
                    "notificationPreviewEnabled" to true,
                    "onlineStatusVisible" to true,
                    "fcmTokens" to emptyMap<String, String>(),
                    "blockedUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                privateRef.set(payload).await()
            } else {
                onlineStatusVisible = privateSnap.getBoolean("onlineStatusVisible") ?: true
                privateRef.update(
                    mapOf(
                        "email" to email.take(320),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, privateRef.path, auth)
            throw e
        }

        try {
            val profileSnap = profileRef.get().await()
            val pendingName = AuthProfilePrefill.pendingDisplayName?.takeIf { it.isNotBlank() }
            AuthProfilePrefill.pendingDisplayName = null
            if (!profileSnap.exists()) {
                val rawName = pendingName
                    ?: firebaseUser.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: firebaseUser.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Member ${it.takeLast(4)}" }
                    ?: email.substringBefore("@").ifBlank { "Univers Member" }
                val displayName = rawName.take(60)
                val sanitizedHandle = rawName.lowercase()
                    .replace(Regex("[^a-z0-9_]"), "")
                    .ifBlank { "user_${uid.take(6)}" }
                    .take(28)
                    .let { if (it.length < 2) "u_${uid.take(6)}" else it }
                val photoUrl = firebaseUser.photoUrl?.toString()?.take(500) ?: ""

                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "handle" to sanitizedHandle,
                    "bio" to "Hey there! I'm on Univers Chat.",
                    "photoUrl" to photoUrl,
                    "statusVibe" to "Available",
                    "isOnline" to onlineStatusVisible,
                    "onlineStatusVisible" to onlineStatusVisible,
                    "isDiscoverable" to true,
                    "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                profileRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "onlineStatusVisible" to onlineStatusVisible,
                    "isOnline" to onlineStatusVisible,
                    "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val existingPhoto = profileSnap.getString("photoUrl").orEmpty()
                val googlePhoto = firebaseUser.photoUrl?.toString()?.take(500).orEmpty()
                if (existingPhoto.isBlank() && googlePhoto.isNotBlank()) {
                    updates["photoUrl"] = googlePhoto
                }
                profileRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, profileRef.path, auth)
            throw e
        }
    }

    fun observePrivateUser(userId: String): Flow<PrivateUser?> = flow {
        val uid = requireUserId()
        val path = "$USERS_COLLECTION/$userId"
        emitAll(
            db.collection(USERS_COLLECTION).document(userId)
                .snapshots()
                .map { it.toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "$USER_PROFILES_COLLECTION/$userId"
        emitAll(
            db.collection(USER_PROFILES_COLLECTION).document(userId)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeDiscoverableProfiles(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(USER_PROFILES_COLLECTION)
                .whereEqualTo("isDiscoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<UserProfile> { it.isEffectivelyOnline() }
                                .thenBy { it.displayName.lowercase() }
                        )
                }
                .logFirestoreErrors(OperationType.LIST, USER_PROFILES_COLLECTION, uid)
        )
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(CONVERSATIONS_COLLECTION)
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<Conversation> { it.isPinnedBy(userId) }
                                .thenByDescending {
                                    (it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)).toDate().time
                                }
                        )
                }
                .logFirestoreErrors(OperationType.LIST, CONVERSATIONS_COLLECTION, uid)
        )
    }

    fun observeConversation(conversationId: String): Flow<Conversation?> = flow {
        val uid = requireUserId()
        val path = "$CONVERSATIONS_COLLECTION/$conversationId"
        emitAll(
            db.collection(CONVERSATIONS_COLLECTION).document(conversationId)
                .snapshots()
                .map { it.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeMessages(conversationId: String, userId: String): Flow<List<Message>> = flow {
        val uid = requireUserId()
        val path = "$CONVERSATIONS_COLLECTION/$conversationId/$MESSAGES_SUBCOLLECTION"
        emitAll(
            db.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .collection(MESSAGES_SUBCOLLECTION)
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { (it.createdAt ?: Timestamp(Long.MAX_VALUE / 1000, 0)).toDate().time }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    /**
     * Streams the latest [pageSize] messages using the composite index (participantIds ARRAY + createdAt DESC).
     * If the composite index has not yet been provisioned in Firestore Console (`FAILED_PRECONDITION`),
     * automatically falls back to an unindexed participant query so chat loading never breaks.
     */
    fun observeLatestMessagesPaged(
        conversationId: String,
        userId: String,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Flow<PagedMessagesResult> = flow {
        val uid = requireUserId()
        val path = "$CONVERSATIONS_COLLECTION/$conversationId/$MESSAGES_SUBCOLLECTION"
        val messagesCollection = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)

        val indexedFlow = messagesCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<Message> { (it.createdAt ?: Timestamp(Long.MAX_VALUE / 1000, 0)).toDate().time }
                            .thenBy { it.id }
                    )
                PagedMessagesResult(
                    messages = list,
                    isFullSnapshotFallback = false,
                    mayHaveOlderMessages = snapshot.size() >= pageSize
                )
            }
            .catch { error ->
                val fsErr = error.findFirestoreException()
                if (fsErr?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    Log.w(
                        TAG,
                        "Composite index missing for messages(participantIds, createdAt DESC) on $conversationId; using client-sorted fallback.",
                        fsErr
                    )
                    emitAll(
                        messagesCollection
                            .whereArrayContains("participantIds", userId)
                            .snapshots()
                            .map { snapshot ->
                                val sorted = snapshot.toObjects(
                                    Message::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                ).sortedWith(
                                    compareBy<Message> {
                                        (it.createdAt ?: Timestamp(Long.MAX_VALUE / 1000, 0)).toDate().time
                                    }.thenBy { it.id }
                                )
                                PagedMessagesResult(
                                    messages = sorted,
                                    isFullSnapshotFallback = true,
                                    mayHaveOlderMessages = false
                                )
                            }
                            .logFirestoreErrors(OperationType.LIST, path, uid)
                    )
                } else {
                    throw error
                }
            }
            .logFirestoreErrors(OperationType.LIST, path, uid)

        emitAll(indexedFlow)
    }

    suspend fun fetchOlderMessagesPage(
        conversationId: String,
        userId: String,
        beforeMillis: Long,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<List<Message>> = runCatching {
        requireUserId()
        val messagesCollection = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)

        val cursorTs = Timestamp(Date(beforeMillis))
        val snapshot = try {
            messagesCollection
                .whereArrayContains("participantIds", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .startAfter(cursorTs)
                .limit(pageSize.toLong())
                .get()
                .await()
        } catch (e: Exception) {
            val fsErr = e.findFirestoreException()
            if (fsErr?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                val allSnap = messagesCollection
                    .whereArrayContains("participantIds", userId)
                    .get()
                    .await()
                return@runCatching allSnap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { (it.createdAt?.toDate()?.time ?: Long.MAX_VALUE) < beforeMillis }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    .take(pageSize)
                    .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
            }
            throw e
        }

        snapshot.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
                compareBy<Message> { it.createdAt?.toDate()?.time ?: 0L }
                    .thenBy { it.id }
            )
    }

    suspend fun updateProfile(
        displayName: String,
        handle: String,
        bio: String,
        statusVibe: String,
        isOnline: Boolean,
        isDiscoverable: Boolean,
        onlineStatusVisible: Boolean = isOnline
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val profileRef = db.collection(USER_PROFILES_COLLECTION).document(uid)
        val privateRef = db.collection(USERS_COLLECTION).document(uid)
        val cleanName = displayName.trim().take(60)
        require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
        val cleanHandle = handle.trim().lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .take(32)
        require(cleanHandle.length >= 2) { "Handle must be at least 2 characters." }

        val effectiveOnline = isOnline && onlineStatusVisible
        val updates = mutableMapOf<String, Any>(
            "displayName" to cleanName,
            "handle" to cleanHandle,
            "bio" to bio.trim().take(160),
            "statusVibe" to statusVibe.trim().take(60),
            "isOnline" to effectiveOnline,
            "onlineStatusVisible" to onlineStatusVisible,
            "isDiscoverable" to isDiscoverable,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (effectiveOnline) {
            updates["lastHeartbeatAt"] = FieldValue.serverTimestamp()
        }
        try {
            db.runBatch { batch ->
                batch.update(profileRef, updates)
                batch.update(
                    privateRef,
                    mapOf(
                        "onlineStatusVisible" to onlineStatusVisible,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fall back to updating profileRef alone if privateRef wasn't initialized in an edge test
            try {
                profileRef.update(updates).await()
            } catch (inner: Exception) {
                handleFirestoreError(inner, OperationType.UPDATE, profileRef.path, auth)
                throw inner
            }
        }
    }

    suspend fun setOnlinePresence(isOnline: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection(USER_PROFILES_COLLECTION).document(uid)
        val updates = mutableMapOf<String, Any>(
            "isOnline" to isOnline,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isOnline) {
            updates["lastHeartbeatAt"] = FieldValue.serverTimestamp()
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun sendPresenceHeartbeat(): Result<Unit> = setOnlinePresence(isOnline = true)

    suspend fun updatePrivacyPreferences(
        readReceiptsEnabled: Boolean,
        notificationsEnabled: Boolean,
        onlineStatusVisible: Boolean? = null,
        notificationPreviewEnabled: Boolean? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val privateRef = db.collection(USERS_COLLECTION).document(uid)
        val profileRef = db.collection(USER_PROFILES_COLLECTION).document(uid)
        val privateUpdates = mutableMapOf<String, Any>(
            "readReceiptsEnabled" to readReceiptsEnabled,
            "notificationsEnabled" to notificationsEnabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (onlineStatusVisible != null) {
            privateUpdates["onlineStatusVisible"] = onlineStatusVisible
        }
        if (notificationPreviewEnabled != null) {
            privateUpdates["notificationPreviewEnabled"] = notificationPreviewEnabled
        }
        try {
            if (onlineStatusVisible != null) {
                val profileUpdates = mutableMapOf<String, Any>(
                    "onlineStatusVisible" to onlineStatusVisible,
                    "isOnline" to onlineStatusVisible,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (onlineStatusVisible) {
                    profileUpdates["lastHeartbeatAt"] = FieldValue.serverTimestamp()
                }
                db.runBatch { batch ->
                    batch.update(privateRef, privateUpdates)
                    batch.update(profileRef, profileUpdates)
                }.await()
            } else {
                privateRef.update(privateUpdates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, privateRef.path, auth)
            throw e
        }
    }

    suspend fun upsertFcmToken(
        deviceId: String,
        token: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanDevice = deviceId.trim().take(64)
        val cleanToken = token.trim()
        require(cleanDevice.isNotEmpty() && cleanToken.length > 10) { "Invalid FCM token payload." }
        val privateRef = db.collection(USERS_COLLECTION).document(uid)
        try {
            privateRef.update(
                FieldPath.of("fcmTokens", cleanDevice), cleanToken,
                FieldPath.of("updatedAt"), FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, privateRef.path, auth)
            throw e
        }
    }

    suspend fun removeFcmToken(deviceId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanDevice = deviceId.trim().take(64)
        require(cleanDevice.isNotEmpty()) { "Device ID required." }
        val privateRef = db.collection(USERS_COLLECTION).document(uid)
        try {
            privateRef.update(
                FieldPath.of("fcmTokens", cleanDevice), FieldValue.delete(),
                FieldPath.of("updatedAt"), FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, privateRef.path, auth)
            throw e
        }
    }

    suspend fun setUserBlocked(
        targetUserId: String,
        blocked: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(targetUserId.isNotBlank() && targetUserId != uid) { "Invalid user to block." }
        val docRef = db.collection(USERS_COLLECTION).document(uid)
        try {
            val snap = docRef.get().await()
            val currentBlocked = (snap.get("blockedUserIds") as? List<*>)
                ?.filterIsInstance<String>()
                ?.toMutableList()
                ?: mutableListOf()
            if (blocked) {
                if (targetUserId !in currentBlocked && currentBlocked.size < 100) {
                    currentBlocked.add(targetUserId)
                }
            } else {
                currentBlocked.removeAll { it == targetUserId }
            }
            docRef.update(
                mapOf(
                    "blockedUserIds" to currentBlocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun getOrCreateDirectConversation(
        currentProfile: UserProfile,
        otherProfile: UserProfile
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid == currentProfile.userId) { "Authenticated user mismatch" }

        val conversationId = buildDirectConversationId(currentProfile.userId, otherProfile.userId)
        val docRef = db.collection(CONVERSATIONS_COLLECTION).document(conversationId)

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val participantIds = listOf(currentProfile.userId, otherProfile.userId).distinct()
                val participantNames = mapOf(
                    currentProfile.userId to currentProfile.displayName,
                    otherProfile.userId to otherProfile.displayName
                )
                val participantPhotos = mapOf(
                    currentProfile.userId to currentProfile.photoUrl,
                    otherProfile.userId to otherProfile.photoUrl
                )
                val unreadCounts = participantIds.associateWith { 0L }

                val payload = mapOf(
                    "type" to Conversation.TYPE_DIRECT,
                    "title" to "",
                    "topic" to "",
                    "createdBy" to uid,
                    "participantIds" to participantIds,
                    "participantNames" to participantNames,
                    "participantPhotos" to participantPhotos,
                    "lastMessageText" to "",
                    "lastMessageSenderId" to "",
                    "lastMessageSenderName" to "",
                    "lastMessageType" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCounts" to unreadCounts,
                    "pinnedBy" to emptyMap<String, Boolean>(),
                    "mutedBy" to emptyMap<String, Boolean>(),
                    "typingBy" to emptyMap<String, Any>(),
                    "recordingBy" to emptyMap<String, Any>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            conversationId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    suspend fun createGroupConversation(
        title: String,
        topic: String,
        currentProfile: UserProfile,
        selectedMembers: List<UserProfile>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid == currentProfile.userId) { "Authenticated user mismatch" }
        val cleanTitle = title.trim().take(80)
        require(cleanTitle.isNotEmpty()) { "Group name is required." }

        val allProfiles = (listOf(currentProfile) + selectedMembers).distinctBy { it.userId }.take(50)
        val participantIds = allProfiles.map { it.userId }
        val participantNames = allProfiles.associate { it.userId to it.displayName }
        val participantPhotos = allProfiles.associate { it.userId to it.photoUrl }
        val unreadCounts = participantIds.associateWith { 0L }

        val docRef = db.collection(CONVERSATIONS_COLLECTION).document()
        val payload = mapOf(
            "type" to Conversation.TYPE_GROUP,
            "title" to cleanTitle,
            "topic" to topic.trim().take(160),
            "createdBy" to uid,
            "participantIds" to participantIds,
            "participantNames" to participantNames,
            "participantPhotos" to participantPhotos,
            "lastMessageText" to "${currentProfile.displayName} created \"$cleanTitle\"",
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to currentProfile.displayName,
            "lastMessageType" to Message.TYPE_TEXT,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts" to unreadCounts,
            "pinnedBy" to emptyMap<String, Boolean>(),
            "mutedBy" to emptyMap<String, Boolean>(),
            "typingBy" to emptyMap<String, Any>(),
            "recordingBy" to emptyMap<String, Any>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    // ---- Optimistic Outbox Enqueue + Delivery ----

    suspend fun enqueueOptimisticMessage(
        ownerUserId: String,
        conversation: Conversation,
        senderProfile: UserProfile,
        type: String,
        text: String,
        previewText: String,
        imageBase64: String? = null,
        audioBase64: String? = null,
        audioDurationSec: Int? = null,
        isForwarded: Boolean = false,
        replyTo: Message? = null,
        pollQuestion: String? = null,
        pollOptions: List<String>? = null
    ): Result<OutboxMessageEntity> = runCatching {
        val dao = requireNotNull(localDao) { "Local cache DAO is required for optimistic outbox." }
        require(conversation.id.isNotBlank()) { "Conversation ID is required." }

        val clientId = generateClientMessageId()
        val now = System.currentTimeMillis()
        val nowTs = RoomConverters.millisToTimestamp(now)
        val senderName = senderProfile.displayName.take(60).ifBlank { "Member" }
        val replyPreview = replyTo?.let {
            when (it.type) {
                Message.TYPE_IMAGE -> if (it.text.isNotBlank()) "📷 ${it.text}" else "📷 Photo"
                Message.TYPE_POLL -> "📊 ${it.pollQuestion ?: it.text}"
                Message.TYPE_AUDIO -> "🎤 ${it.text}"
                else -> it.text
            }.take(160)
        }

        val optimisticMsg = Message(
            id = clientId,
            conversationId = conversation.id,
            senderId = ownerUserId,
            senderName = senderName,
            senderPhotoUrl = senderProfile.photoUrl.take(500),
            participantIds = conversation.participantIds,
            type = type,
            text = text,
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            audioDurationSec = (audioDurationSec ?: 0).coerceIn(0, 180),
            isForwarded = isForwarded,
            replyToMessageId = replyTo?.id?.take(128),
            replyToSenderName = replyTo?.senderName?.take(60),
            replyToPreview = replyPreview,
            reactions = emptyMap(),
            pollQuestion = pollQuestion,
            pollOptions = pollOptions.orEmpty(),
            pollVotes = emptyMap(),
            isEdited = false,
            clientMessageId = clientId,
            deliveredTo = emptyMap(),
            readBy = emptyMap(),
            createdAt = nowTs,
            updatedAt = nowTs,
            deliveryState = Message.STATUS_SENDING
        )

        val outboxItem = OutboxMessageEntity(
            clientMessageId = clientId,
            ownerUserId = ownerUserId,
            conversationId = conversation.id,
            senderId = ownerUserId,
            senderName = senderName,
            senderPhotoUrl = senderProfile.photoUrl.take(500),
            participantIdsJson = RoomConverters.encodeStringList(conversation.participantIds),
            type = type,
            text = text,
            previewText = previewText.take(300),
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            audioDurationSec = audioDurationSec,
            isForwarded = isForwarded,
            replyToMessageId = replyTo?.id?.take(128),
            replyToSenderName = replyTo?.senderName?.take(60),
            replyToPreview = replyPreview,
            pollQuestion = pollQuestion,
            pollOptionsJson = pollOptions?.let { RoomConverters.encodeStringList(it) },
            retryCount = 0,
            nextRetryAtMillis = now,
            createdAtMillis = now,
            status = OutboxMessageEntity.STATUS_PENDING,
            lastError = null
        )

        dao.upsertMessage(optimisticMsg.toEntity(ownerUserId, now))
        dao.upsertOutbox(outboxItem)
        dao.updateConversationOptimisticPreview(
            ownerUserId = ownerUserId,
            conversationId = conversation.id,
            previewText = previewText.take(300),
            senderId = ownerUserId,
            senderName = senderName,
            messageType = type,
            timestampMillis = now,
            tickState = MessageTickState.SENDING.name
        )
        outboxItem
    }

    suspend fun retryFailedOutboxMessage(clientMessageId: String): Boolean {
        val dao = localDao ?: return false
        val item = dao.getOutboxById(clientMessageId) ?: return false
        val now = System.currentTimeMillis()
        dao.upsertOutbox(
            item.copy(
                status = OutboxMessageEntity.STATUS_PENDING,
                nextRetryAtMillis = now,
                lastError = null
            )
        )
        dao.updateMessageDeliveryState(clientMessageId, Message.STATUS_SENDING)
        return true
    }

    suspend fun sendOutboxItem(item: OutboxMessageEntity): OutboxSendOutcome {
        val uid = auth.currentUser?.uid
            ?: return OutboxSendOutcome.Failure("Sign in required before syncing.", isPermanent = false)
        if (uid != item.ownerUserId) {
            return OutboxSendOutcome.Failure("Signed-in account changed.", isPermanent = true)
        }

        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(item.conversationId)
        val msgRef = convRef.collection(MESSAGES_SUBCOLLECTION).document(item.clientMessageId)

        // Check idempotency: if a previous attempt actually landed on Firestore before response returned, finish cleanly
        val existingParticipants = try {
            val cachedConv = localDao?.getConversationOnce(item.ownerUserId, item.conversationId)
            val cachedIds = cachedConv?.let { RoomConverters.decodeStringList(it.participantIdsJson) }
                ?.takeIf { it.isNotEmpty() }
            cachedIds ?: RoomConverters.decodeStringList(item.participantIdsJson)
        } catch (_: Exception) {
            RoomConverters.decodeStringList(item.participantIdsJson)
        }

        val participantIds = if (uid in existingParticipants) {
            existingParticipants
        } else {
            val freshConvSnap = try {
                convRef.get().await()
            } catch (e: Exception) {
                return classifySendFailure(e, msgRef.path)
            }
            @Suppress("UNCHECKED_CAST")
            (freshConvSnap.get("participantIds") as? List<String>).orEmpty()
        }

        val messagePayload = mutableMapOf<String, Any>(
            "conversationId" to item.conversationId,
            "senderId" to uid,
            "senderName" to item.senderName.take(60).ifBlank { "Member" },
            "senderPhotoUrl" to item.senderPhotoUrl.take(500),
            "participantIds" to participantIds,
            "type" to item.type,
            "text" to item.text,
            "reactions" to emptyMap<String, String>(),
            "isEdited" to false,
            "clientMessageId" to item.clientMessageId,
            "deliveredTo" to emptyMap<String, Any>(),
            "readBy" to emptyMap<String, Any>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (item.isForwarded) messagePayload["isForwarded"] = true
        if (!item.imageBase64.isNullOrBlank()) messagePayload["imageBase64"] = item.imageBase64
        if (!item.audioBase64.isNullOrBlank()) {
            messagePayload["audioBase64"] = item.audioBase64
            messagePayload["audioDurationSec"] = (item.audioDurationSec ?: 1).coerceIn(0, 180)
        }
        if (!item.replyToMessageId.isNullOrBlank()) {
            messagePayload["replyToMessageId"] = item.replyToMessageId.take(128)
            messagePayload["replyToSenderName"] = (item.replyToSenderName ?: "").take(60)
            messagePayload["replyToPreview"] = (item.replyToPreview ?: "").take(160)
        }
        val pollOptions = RoomConverters.decodeStringList(item.pollOptionsJson)
        if (!item.pollQuestion.isNullOrBlank() && pollOptions.size >= 2) {
            messagePayload["pollQuestion"] = item.pollQuestion
            messagePayload["pollOptions"] = pollOptions
            messagePayload["pollVotes"] = emptyMap<String, String>()
        }

        val convUpdatePayload = mutableMapOf<String, Any>(
            "lastMessageText" to item.previewText.take(300),
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to item.senderName.take(60),
            "lastMessageType" to item.type,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts.$uid" to 0L,
            "typingBy.$uid" to FieldValue.delete(),
            "recordingBy.$uid" to FieldValue.delete(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        participantIds.filter { it != uid }.forEach { peerId ->
            convUpdatePayload["unreadCounts.$peerId"] = FieldValue.increment(1L)
        }

        return try {
            db.runBatch { batch ->
                batch.set(msgRef, messagePayload)
                batch.update(convRef, convUpdatePayload)
            }.await()
            val sentAt = System.currentTimeMillis()
            localDao?.markMessageAsSentLocally(item.ownerUserId, item.clientMessageId, sentAt)
            localDao?.updateConversationLastTickState(item.ownerUserId, item.conversationId, MessageTickState.SENT.name)
            OutboxSendOutcome.Sent(sentAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Check if the message already persisted remotely before failing cleanly
            val alreadyPersisted = runCatching { msgRef.get().await().exists() }.getOrDefault(false)
            if (alreadyPersisted) {
                val sentAt = System.currentTimeMillis()
                localDao?.markMessageAsSentLocally(item.ownerUserId, item.clientMessageId, sentAt)
                return OutboxSendOutcome.Sent(sentAt)
            }
            classifySendFailure(e, msgRef.path)
        }
    }

    private fun classifySendFailure(e: Exception, path: String): OutboxSendOutcome.Failure {
        val fsErr = e.findFirestoreException()
        if (fsErr != null) {
            val isPermanent = when (fsErr.code) {
                FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.NOT_FOUND -> true
                else -> false
            }
            if (isPermanent) {
                runCatching { handleFirestoreError(fsErr, OperationType.WRITE, path, auth) }
            }
            return OutboxSendOutcome.Failure(
                errorMessage = fsErr.localizedMessage ?: "Failed to send message",
                isPermanent = isPermanent
            )
        }
        return OutboxSendOutcome.Failure(
            errorMessage = e.localizedMessage ?: "Network unavailable",
            isPermanent = false
        )
    }

    // ---- Typing / Recording / Delivery & Read Receipts ----

    suspend fun setTypingIndicator(conversationId: String, isTyping: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (conversationId.isBlank()) return@runCatching
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversationId)
        try {
            convRef.update(
                mapOf(
                    "typingBy.$uid" to if (isTyping) FieldValue.serverTimestamp() else FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Typing indicator update skipped: ${e.message}")
        }
    }

    suspend fun setRecordingIndicator(conversationId: String, isRecording: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (conversationId.isBlank()) return@runCatching
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversationId)
        try {
            convRef.update(
                mapOf(
                    "recordingBy.$uid" to if (isRecording) FieldValue.serverTimestamp() else FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Recording indicator update skipped: ${e.message}")
        }
    }

    suspend fun markMessagesDelivered(
        conversationId: String,
        messageIds: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (conversationId.isBlank() || messageIds.isEmpty()) return@runCatching
        val messagesCol = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)

        messageIds.distinct().chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
            try {
                db.runBatch { batch ->
                    chunk.forEach { msgId ->
                        batch.update(
                            messagesCol.document(msgId),
                            mapOf(
                                "deliveredTo.$uid" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Delivered receipt batch failed on $conversationId: ${e.message}")
            }
        }
    }

    suspend fun markConversationMessagesDeliveredUpTo(
        conversationId: String,
        limitCount: Int = 10
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messagesCol = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)

        val snap = try {
            messagesCol
                .whereArrayContains("participantIds", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limitCount.toLong())
                .get()
                .await()
        } catch (e: Exception) {
            val fsErr = e.findFirestoreException()
            if (fsErr?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                messagesCol.whereArrayContains("participantIds", uid).get().await()
            } else {
                throw e
            }
        }
        val toMark = snap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { it.senderId != uid && uid !in it.deliveredTo }
            .map { it.id }
        if (toMark.isNotEmpty()) {
            markMessagesDelivered(conversationId, toMark)
        }
    }

    suspend fun markMessagesRead(
        conversationId: String,
        messageIds: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (conversationId.isBlank() || messageIds.isEmpty()) return@runCatching
        val messagesCol = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)

        messageIds.distinct().chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
            try {
                db.runBatch { batch ->
                    chunk.forEach { msgId ->
                        batch.update(
                            messagesCol.document(msgId),
                            mapOf(
                                "deliveredTo.$uid" to FieldValue.serverTimestamp(),
                                "readBy.$uid" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Read receipt batch failed on $conversationId: ${e.message}")
            }
        }
    }

    // ---- Direct Send Methods (kept for rules/tests & Notification Inline Reply) ----

    suspend fun sendTextMessage(
        conversation: Conversation,
        senderProfile: UserProfile,
        text: String,
        replyTo: Message? = null
    ): Result<Unit> = runCatching {
        val trimmed = text.trim().take(2000)
        require(trimmed.isNotEmpty()) { "Message cannot be empty." }
        sendMessageInternal(
            conversation = conversation,
            senderProfile = senderProfile,
            type = Message.TYPE_TEXT,
            text = trimmed,
            previewText = trimmed.take(300),
            imageBase64 = null,
            replyTo = replyTo,
            pollQuestion = null,
            pollOptions = null
        )
    }

    suspend fun sendImageMessage(
        conversation: Conversation,
        senderProfile: UserProfile,
        caption: String,
        imageBase64: String,
        replyTo: Message? = null
    ): Result<Unit> = runCatching {
        require(imageBase64.isNotBlank() && imageBase64.length <= 80000) {
            "Image size exceeds allowed limit."
        }
        val cleanCaption = caption.trim().take(2000)
        val preview = if (cleanCaption.isNotBlank()) "📷 $cleanCaption".take(300) else "📷 Shared a photo"
        sendMessageInternal(
            conversation = conversation,
            senderProfile = senderProfile,
            type = Message.TYPE_IMAGE,
            text = cleanCaption,
            previewText = preview,
            imageBase64 = imageBase64,
            replyTo = replyTo,
            pollQuestion = null,
            pollOptions = null
        )
    }

    suspend fun sendPollMessage(
        conversation: Conversation,
        senderProfile: UserProfile,
        question: String,
        options: List<String>
    ): Result<Unit> = runCatching {
        val cleanQuestion = question.trim().take(200)
        val cleanOptions = options.map { it.trim().take(80) }.filter { it.isNotEmpty() }.take(6)
        require(cleanQuestion.isNotEmpty()) { "Poll question cannot be empty." }
        require(cleanOptions.size >= 2) { "Provide at least 2 poll options." }

        sendMessageInternal(
            conversation = conversation,
            senderProfile = senderProfile,
            type = Message.TYPE_POLL,
            text = cleanQuestion,
            previewText = "📊 Poll: $cleanQuestion".take(300),
            imageBase64 = null,
            replyTo = null,
            pollQuestion = cleanQuestion,
            pollOptions = cleanOptions
        )
    }

    suspend fun sendVoiceMessage(
        conversation: Conversation,
        senderProfile: UserProfile,
        audioBase64: String,
        durationSec: Int,
        replyTo: Message? = null
    ): Result<Unit> = runCatching {
        require(audioBase64.length in 16..95_000) { "Voice message exceeds allowed limit." }
        val safeSec = durationSec.coerceIn(1, 180)
        val formatted = "%d:%02d".format(safeSec / 60, safeSec % 60)
        val label = "Voice message ($formatted)"
        sendMessageInternal(
            conversation = conversation,
            senderProfile = senderProfile,
            type = Message.TYPE_AUDIO,
            text = label,
            previewText = "🎤 $label",
            imageBase64 = null,
            audioBase64 = audioBase64,
            audioDurationSec = safeSec,
            replyTo = replyTo,
            pollQuestion = null,
            pollOptions = null
        )
    }

    suspend fun forwardMessage(
        targetConversation: Conversation,
        senderProfile: UserProfile,
        original: Message
    ): Result<Unit> = runCatching {
        when (original.type) {
            Message.TYPE_IMAGE -> {
                val img = original.imageBase64?.takeIf { it.isNotBlank() }
                    ?: error("Photo unavailable to forward.")
                val preview = if (original.text.isNotBlank()) "↪ 📷 ${original.text}".take(300) else "↪ 📷 Forwarded photo"
                sendMessageInternal(
                    conversation = targetConversation,
                    senderProfile = senderProfile,
                    type = Message.TYPE_IMAGE,
                    text = original.text.take(2000),
                    previewText = preview,
                    imageBase64 = img,
                    isForwarded = true
                )
            }
            Message.TYPE_AUDIO -> {
                val audio = original.audioBase64?.takeIf { it.length >= 16 }
                    ?: error("Voice note unavailable to forward.")
                val safeSec = original.audioDurationSec.coerceIn(1, 180)
                val label = original.text.ifBlank { "Voice message" }
                sendMessageInternal(
                    conversation = targetConversation,
                    senderProfile = senderProfile,
                    type = Message.TYPE_AUDIO,
                    text = label,
                    previewText = "↪ 🎤 $label".take(300),
                    imageBase64 = null,
                    audioBase64 = audio,
                    audioDurationSec = safeSec,
                    isForwarded = true
                )
            }
            Message.TYPE_POLL -> {
                val q = original.pollQuestion ?: original.text
                val opts = original.pollOptions
                if (opts.size >= 2) {
                    sendMessageInternal(
                        conversation = targetConversation,
                        senderProfile = senderProfile,
                        type = Message.TYPE_POLL,
                        text = q.take(200),
                        previewText = "↪ 📊 $q".take(300),
                        imageBase64 = null,
                        pollQuestion = q.take(200),
                        pollOptions = opts,
                        isForwarded = true
                    )
                } else {
                    sendMessageInternal(
                        conversation = targetConversation,
                        senderProfile = senderProfile,
                        type = Message.TYPE_TEXT,
                        text = q.take(2000),
                        previewText = "↪ $q".take(300),
                        imageBase64 = null,
                        isForwarded = true
                    )
                }
            }
            else -> {
                val clean = original.text.trim().take(2000)
                require(clean.isNotEmpty()) { "Nothing to forward." }
                sendMessageInternal(
                    conversation = targetConversation,
                    senderProfile = senderProfile,
                    type = Message.TYPE_TEXT,
                    text = clean,
                    previewText = "↪ $clean".take(300),
                    imageBase64 = null,
                    isForwarded = true
                )
            }
        }
    }

    private suspend fun sendMessageInternal(
        conversation: Conversation,
        senderProfile: UserProfile,
        type: String,
        text: String,
        previewText: String,
        imageBase64: String?,
        audioBase64: String? = null,
        audioDurationSec: Int? = null,
        isForwarded: Boolean = false,
        replyTo: Message? = null,
        pollQuestion: String? = null,
        pollOptions: List<String>? = null
    ) {
        val uid = requireUserId()
        require(conversation.id.isNotBlank()) { "Conversation ID is required." }

        val clientId = generateClientMessageId()
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        val msgRef = convRef.collection(MESSAGES_SUBCOLLECTION).document(clientId)

        val messagePayload = mutableMapOf<String, Any>(
            "conversationId" to conversation.id,
            "senderId" to uid,
            "senderName" to senderProfile.displayName.take(60).ifBlank { "Member" },
            "senderPhotoUrl" to senderProfile.photoUrl.take(500),
            "participantIds" to conversation.participantIds,
            "type" to type,
            "text" to text,
            "reactions" to emptyMap<String, String>(),
            "isEdited" to false,
            "clientMessageId" to clientId,
            "deliveredTo" to emptyMap<String, Any>(),
            "readBy" to emptyMap<String, Any>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isForwarded) {
            messagePayload["isForwarded"] = true
        }
        if (!imageBase64.isNullOrBlank()) {
            messagePayload["imageBase64"] = imageBase64
        }
        if (!audioBase64.isNullOrBlank()) {
            messagePayload["audioBase64"] = audioBase64
            messagePayload["audioDurationSec"] = (audioDurationSec ?: 1).coerceIn(0, 180)
        }
        if (replyTo != null && replyTo.id.isNotBlank()) {
            messagePayload["replyToMessageId"] = replyTo.id.take(128)
            messagePayload["replyToSenderName"] = replyTo.senderName.take(60)
            val replyPreview = when (replyTo.type) {
                Message.TYPE_IMAGE -> if (replyTo.text.isNotBlank()) "📷 ${replyTo.text}" else "📷 Photo"
                Message.TYPE_POLL -> "📊 ${replyTo.pollQuestion ?: replyTo.text}"
                Message.TYPE_AUDIO -> "🎤 ${replyTo.text}"
                else -> replyTo.text
            }.take(160)
            messagePayload["replyToPreview"] = replyPreview
        }
        if (!pollQuestion.isNullOrBlank() && !pollOptions.isNullOrEmpty()) {
            messagePayload["pollQuestion"] = pollQuestion
            messagePayload["pollOptions"] = pollOptions
            messagePayload["pollVotes"] = emptyMap<String, String>()
        }

        val convUpdatePayload = mutableMapOf<String, Any>(
            "lastMessageText" to previewText.take(300),
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to senderProfile.displayName.take(60),
            "lastMessageType" to type,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts.$uid" to 0L,
            "typingBy.$uid" to FieldValue.delete(),
            "recordingBy.$uid" to FieldValue.delete(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        conversation.participantIds.filter { it != uid }.forEach { peerId ->
            convUpdatePayload["unreadCounts.$peerId"] = FieldValue.increment(1L)
        }

        try {
            db.runBatch { batch ->
                batch.set(msgRef, messagePayload)
                batch.update(convRef, convUpdatePayload)
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path, auth)
            throw e
        }
    }

    suspend fun editMessage(
        conversationId: String,
        messageId: String,
        newText: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val trimmed = newText.trim().take(2000)
        require(trimmed.isNotEmpty()) { "Edited message cannot be empty." }
        val msgRef = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)
            .document(messageId)
        try {
            msgRef.update(
                mapOf(
                    "text" to trimmed,
                    "isEdited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path, auth)
            throw e
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> = runCatching {
        requireUserId()
        // If local outbox message hasn't been sent yet, remove locally cleanly
        val outboxItem = localDao?.getOutboxById(messageId)
        if (outboxItem != null) {
            localDao.deleteOutbox(messageId)
            localDao.deleteMessageById(messageId)
            return@runCatching
        }
        val msgRef = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)
            .document(messageId)
        try {
            msgRef.delete().await()
            localDao?.deleteMessageById(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, msgRef.path, auth)
            throw e
        }
    }

    suspend fun toggleReaction(
        conversationId: String,
        message: Message,
        emoji: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(message.id.isNotBlank()) { "Message ID is required." }
        val isSame = message.reactions[uid] == emoji
        val msgRef = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)
            .document(message.id)
        try {
            msgRef.update(
                mapOf(
                    "reactions.$uid" to if (isSame) FieldValue.delete() else emoji,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path, auth)
            throw e
        }
    }

    suspend fun votePoll(
        conversationId: String,
        message: Message,
        optionIndex: Int
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(message.id.isNotBlank()) { "Message ID is required." }
        val msgRef = db.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_SUBCOLLECTION)
            .document(message.id)
        try {
            msgRef.update(
                mapOf(
                    "pollVotes.$uid" to optionIndex.toString(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path, auth)
            throw e
        }
    }

    suspend fun markConversationRead(conversation: Conversation): Result<Unit> = runCatching {
        val uid = requireUserId()
        if ((conversation.unreadCounts[uid] ?: 0L) == 0L) return@runCatching
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.update(
                mapOf(
                    "unreadCounts.$uid" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun toggleMarkUnread(conversation: Conversation): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val currentlyUnread = (conversation.unreadCounts[uid] ?: 0L) > 0L
        val targetCount = if (currentlyUnread) 0L else 1L
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.update(
                mapOf(
                    "unreadCounts.$uid" to targetCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            !currentlyUnread
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun togglePinConversation(conversation: Conversation): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val nowPinned = !conversation.isPinnedBy(uid)
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.update(
                mapOf(
                    "pinnedBy.$uid" to if (nowPinned) true else FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            nowPinned
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun toggleMuteConversation(conversation: Conversation): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val nowMuted = !conversation.isMutedBy(uid)
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.update(
                mapOf(
                    "mutedBy.$uid" to if (nowMuted) true else FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            nowMuted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun pinMessage(conversationId: String, message: Message?): Result<Unit> = runCatching {
        requireUserId()
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversationId)
        val preview = when (message?.type) {
            Message.TYPE_IMAGE -> if (message.text.isNotBlank()) "📷 ${message.text}" else "📷 Photo"
            Message.TYPE_POLL -> "📊 ${message.pollQuestion ?: message.text}"
            Message.TYPE_AUDIO -> "🎤 ${message.text}"
            else -> message?.text.orEmpty()
        }.take(200)
        val updates = if (message == null) {
            mapOf<String, Any>(
                "pinnedMessageId" to "",
                "pinnedMessagePreview" to "",
                "pinnedMessageSenderName" to "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf<String, Any>(
                "pinnedMessageId" to message.id.take(128),
                "pinnedMessagePreview" to preview,
                "pinnedMessageSenderName" to message.senderName.take(60),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
        try {
            convRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun updateGroupInfo(
        conversation: Conversation,
        newTitle: String,
        newTopic: String
    ): Result<Unit> = runCatching {
        requireUserId()
        require(conversation.type == Conversation.TYPE_GROUP) { "Only groups can be updated." }
        val cleanTitle = newTitle.trim().take(80)
        require(cleanTitle.isNotEmpty()) { "Group title cannot be empty." }
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "topic" to newTopic.trim().take(160),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun addGroupMembers(
        conversation: Conversation,
        newMembers: List<UserProfile>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(conversation.type == Conversation.TYPE_GROUP) { "Cannot add members to a direct chat." }
        val toAdd = newMembers.filter { it.userId !in conversation.participantIds }
        if (toAdd.isEmpty()) return@runCatching

        val updatedIds = (conversation.participantIds + toAdd.map { it.userId }).distinct().take(50)
        val updatedNames = conversation.participantNames.toMutableMap().apply {
            toAdd.forEach { put(it.userId, it.displayName) }
        }
        val updatedPhotos = conversation.participantPhotos.toMutableMap().apply {
            toAdd.forEach { put(it.userId, it.photoUrl) }
        }
        val updatedUnread = conversation.unreadCounts.toMutableMap().apply {
            toAdd.forEach { putIfAbsent(it.userId, 0L) }
        }

        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.update(
                mapOf(
                    "participantIds" to updatedIds,
                    "participantNames" to updatedNames,
                    "participantPhotos" to updatedPhotos,
                    "unreadCounts" to updatedUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val msgSnapshot = convRef.collection(MESSAGES_SUBCOLLECTION)
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
            if (!msgSnapshot.isEmpty) {
                msgSnapshot.documents.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                    db.runBatch { batch ->
                        chunk.forEach { msgDoc ->
                            batch.update(
                                msgDoc.reference,
                                mapOf(
                                    "participantIds" to updatedIds,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }.await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun leaveGroup(conversation: Conversation): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(conversation.type == Conversation.TYPE_GROUP) { "Cannot leave a direct chat." }
        val updatedIds = conversation.participantIds.filter { it != uid }
        val updatedNames = conversation.participantNames.toMutableMap().apply { remove(uid) }
        val updatedPhotos = conversation.participantPhotos.toMutableMap().apply { remove(uid) }
        val updatedUnread = conversation.unreadCounts.toMutableMap().apply { remove(uid) }

        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.update(
                mapOf(
                    "participantIds" to updatedIds,
                    "participantNames" to updatedNames,
                    "participantPhotos" to updatedPhotos,
                    "unreadCounts" to updatedUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            throw e
        }
    }

    suspend fun deleteConversation(conversation: Conversation): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(conversation.id.isNotBlank()) { "Conversation ID is required." }
        val isCreator = conversation.createdBy == uid
        val isDirectParticipant = conversation.type == Conversation.TYPE_DIRECT && uid in conversation.participantIds
        require(isCreator || isDirectParticipant) {
            "Only the conversation creator or direct chat participant can delete this thread."
        }
        val convRef = db.collection(CONVERSATIONS_COLLECTION).document(conversation.id)
        try {
            convRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path, auth)
            throw e
        }
    }

    suspend fun removeOrDeleteConversation(conversation: Conversation): Result<Boolean> = runCatching {
        val uid = requireUserId()
        require(conversation.id.isNotBlank()) { "Conversation ID is required." }
        if (conversation.type == Conversation.TYPE_GROUP && conversation.createdBy != uid) {
            leaveGroup(conversation).getOrThrow()
            false
        } else {
            deleteConversation(conversation).getOrThrow()
            true
        }
    }

    companion object {
        private const val TAG = "MessagingRepository"
        const val DEFAULT_PAGE_SIZE = 30
        const val BATCH_CHUNK_SIZE = 450
        const val USERS_COLLECTION = "users"
        const val USER_PROFILES_COLLECTION = "user_profiles"
        const val CONVERSATIONS_COLLECTION = "conversations"
        const val MESSAGES_SUBCOLLECTION = "messages"
    }
}
