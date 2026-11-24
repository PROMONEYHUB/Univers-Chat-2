package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UniversDao
import com.example.data.model.CallSession
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.PrivateUser
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.CallSignalingRepository
import com.example.data.repository.MessagingRepository
import com.example.data.sync.ConnectivityMonitor
import com.example.data.sync.PresenceManager
import com.example.data.sync.RealtimeSyncCoordinator
import com.example.notifications.FcmTokenManager
import com.example.notifications.MessageNotificationCoordinator
import com.example.ui.auth.PhoneLoginSession
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatFilter(val label: String) {
    ALL("All"),
    DIRECT("Direct"),
    GROUPS("Groups"),
    UNREAD("Unread")
}

class MainMessagingViewModel(
    private val repository: MessagingRepository,
    val currentUserId: String,
    private val dao: UniversDao? = null,
    private val syncCoordinator: RealtimeSyncCoordinator? = null,
    private val connectivityMonitor: ConnectivityMonitor? = null,
    private val presenceManager: PresenceManager? = null,
    private val callSignalingRepository: CallSignalingRepository? = null,
    private val phoneSession: PhoneLoginSession? = null,
    private val hasFirebaseAuth: Boolean = (Firebase.auth.currentUser != null)
) : ViewModel() {

    private val _localPhoneProfile = MutableStateFlow(
        phoneSession?.let {
            UserProfile(
                userId = currentUserId,
                displayName = it.displayName,
                handle = it.handle,
                bio = it.bio,
                statusVibe = it.statusVibe,
                isOnline = it.isOnline,
                onlineStatusVisible = it.isOnline,
                isDiscoverable = it.isDiscoverable
            )
        }
    )

    private val _localPrivateUser = MutableStateFlow(
        phoneSession?.let {
            PrivateUser(
                userId = currentUserId,
                email = it.phoneNumberE164,
                onlineStatusVisible = it.isOnline
            )
        }
    )

    val isNetworkOnline: StateFlow<Boolean> =
        connectivityMonitor?.isConnected ?: MutableStateFlow(true).asStateFlow()

    val pendingOutboxCount: StateFlow<Int> =
        if (hasFirebaseAuth && dao != null) {
            dao.observePendingOutboxCount(currentUserId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = 0
                )
        } else {
            MutableStateFlow(0).asStateFlow()
        }

    val currentUserProfile: StateFlow<UserProfile?> = if (hasFirebaseAuth) {
        val source = if (dao != null) {
            dao.observeUserProfile(currentUserId).map { it?.toDomain() }
        } else {
            repository.observeUserProfile(currentUserId)
        }
        source
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing current user profile", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )
    } else {
        _localPhoneProfile.asStateFlow()
    }

    val privateUser: StateFlow<PrivateUser?> = if (hasFirebaseAuth) {
        val source = if (dao != null) {
            dao.observePrivateUser(currentUserId).map { it?.toDomain() }
        } else {
            repository.observePrivateUser(currentUserId)
        }
        source
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing private user", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )
    } else {
        _localPrivateUser.asStateFlow()
    }

    val blockedUserIds: StateFlow<Set<String>> = privateUser
        .map { it?.blockedUserIds?.toSet().orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val callHistory: StateFlow<List<CallSession>> =
        if (hasFirebaseAuth) {
            (callSignalingRepository?.observeCallHistory(currentUserId) ?: flowOf(emptyList()))
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing call history", firestoreError ?: error)
                    emit(emptyList())
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = emptyList()
                )
        } else {
            MutableStateFlow(emptyList<CallSession>()).asStateFlow()
        }

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        if (hasFirebaseAuth && dao != null) {
            combine(
                dao.observeConversations(currentUserId),
                syncCoordinator?.isSyncingSession ?: flowOf(false)
            ) { entities, isSyncing ->
                if (entities.isEmpty() && isSyncing) {
                    UiState.Loading
                } else {
                    val sorted = entities.map { it.toDomain() }
                        .sortedWith(
                            compareByDescending<Conversation> { it.isPinnedBy(currentUserId) }
                                .thenByDescending {
                                    (it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)).toDate().time
                                }
                        )
                    UiState.Success(sorted)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )
        } else if (hasFirebaseAuth) {
            repository.observeConversations(currentUserId)
                .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing conversations", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load conversations"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        } else {
            MutableStateFlow<UiState<List<Conversation>>>(UiState.Success(emptyList())).asStateFlow()
        }

    val peopleState: StateFlow<UiState<List<UserProfile>>> =
        if (hasFirebaseAuth && dao != null) {
            combine(
                dao.observeDiscoverableProfiles(),
                syncCoordinator?.isSyncingSession ?: flowOf(false)
            ) { entities, isSyncing ->
                if (entities.isEmpty() && isSyncing) {
                    UiState.Loading
                } else {
                    val profiles = entities.map { it.toDomain() }
                        .sortedWith(
                            compareByDescending<UserProfile> { it.isEffectivelyOnline() }
                                .thenBy { it.displayName.lowercase() }
                        )
                    UiState.Success(profiles)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )
        } else if (hasFirebaseAuth) {
            repository.observeDiscoverableProfiles()
                .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing user profiles", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load people directory"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        } else {
            MutableStateFlow<UiState<List<UserProfile>>>(UiState.Success(emptyList())).asStateFlow()
        }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _chatFilter = MutableStateFlow(ChatFilter.ALL)
    val chatFilter: StateFlow<ChatFilter> = _chatFilter.asStateFlow()

    private val _peopleSearchQuery = MutableStateFlow("")
    val peopleSearchQuery: StateFlow<String> = _peopleSearchQuery.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        if (syncCoordinator != null) {
            syncCoordinator.startSession(currentUserId, hasFirebaseAuth)
        } else if (hasFirebaseAuth) {
            viewModelScope.launch {
                val firebaseUser = Firebase.auth.currentUser
                if (firebaseUser != null && firebaseUser.uid == currentUserId) {
                    repository.syncCurrentUserProfile(firebaseUser).onFailure { err ->
                        Log.w(TAG, "Failed to sync user profile", err)
                        _actionMessage.value = err.localizedMessage ?: "Failed to sync profile"
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectChatFilter(filter: ChatFilter) {
        _chatFilter.value = filter
    }

    fun updatePeopleSearchQuery(query: String) {
        _peopleSearchQuery.value = query
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun startDirectChat(
        otherProfile: UserProfile,
        onConversationReady: (String) -> Unit
    ) {
        val me = currentUserProfile.value
        if (me == null) {
            _actionMessage.value = "Syncing your profile, please try again in a moment."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.getOrCreateDirectConversation(me, otherProfile)
                .onSuccess { convId ->
                    _isBusy.value = false
                    onConversationReady(convId)
                }
                .onFailure { err ->
                    _isBusy.value = false
                    _actionMessage.value = err.localizedMessage ?: "Could not open conversation"
                }
        }
    }

    fun createGroupChat(
        title: String,
        topic: String,
        members: List<UserProfile>,
        onConversationReady: (String) -> Unit
    ) {
        val me = currentUserProfile.value
        if (me == null) {
            _actionMessage.value = "Syncing your profile, please try again in a moment."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.createGroupConversation(title, topic, me, members)
                .onSuccess { convId ->
                    _isBusy.value = false
                    onConversationReady(convId)
                }
                .onFailure { err ->
                    _isBusy.value = false
                    _actionMessage.value = err.localizedMessage ?: "Could not create group"
                }
        }
    }

    fun saveProfile(
        displayName: String,
        handle: String,
        bio: String,
        statusVibe: String,
        onlineStatusVisible: Boolean,
        isDiscoverable: Boolean
    ) {
        if (!hasFirebaseAuth) {
            val cleanName = displayName.trim().take(60)
            val cleanHandle = handle.trim().lowercase().replace(Regex("[^a-z0-9_]"), "").take(32)
            _localPhoneProfile.value = (_localPhoneProfile.value ?: UserProfile(userId = currentUserId)).copy(
                displayName = cleanName.ifEmpty { "Univers Member" },
                handle = cleanHandle.ifEmpty { "member" },
                bio = bio.trim().take(160),
                statusVibe = statusVibe.trim().take(60),
                isOnline = onlineStatusVisible,
                onlineStatusVisible = onlineStatusVisible,
                isDiscoverable = isDiscoverable
            )
            _localPrivateUser.value = (_localPrivateUser.value ?: PrivateUser(userId = currentUserId)).copy(
                onlineStatusVisible = onlineStatusVisible
            )
            _actionMessage.value = "Profile saved locally"
            return
        }
        presenceManager?.setOnlineStatusVisible(onlineStatusVisible)
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateProfile(
                displayName = displayName,
                handle = handle,
                bio = bio,
                statusVibe = statusVibe,
                isOnline = onlineStatusVisible,
                isDiscoverable = isDiscoverable,
                onlineStatusVisible = onlineStatusVisible
            ).onSuccess {
                _isBusy.value = false
                _actionMessage.value = "Profile updated"
            }.onFailure { err ->
                _isBusy.value = false
                _actionMessage.value = err.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun updatePrivacyPreferences(
        readReceipts: Boolean,
        notifications: Boolean,
        onlineStatusVisible: Boolean = privateUser.value?.onlineStatusVisible ?: true,
        notificationPreviewEnabled: Boolean = privateUser.value?.notificationPreviewEnabled ?: true
    ) {
        if (!hasFirebaseAuth) {
            _localPrivateUser.value = (_localPrivateUser.value ?: PrivateUser(userId = currentUserId)).copy(
                readReceiptsEnabled = readReceipts,
                notificationsEnabled = notifications,
                onlineStatusVisible = onlineStatusVisible,
                notificationPreviewEnabled = notificationPreviewEnabled
            )
            _localPhoneProfile.value = _localPhoneProfile.value?.copy(
                isOnline = onlineStatusVisible,
                onlineStatusVisible = onlineStatusVisible
            )
            return
        }
        presenceManager?.setOnlineStatusVisible(onlineStatusVisible)
        viewModelScope.launch {
            repository.updatePrivacyPreferences(
                readReceiptsEnabled = readReceipts,
                notificationsEnabled = notifications,
                onlineStatusVisible = onlineStatusVisible,
                notificationPreviewEnabled = notificationPreviewEnabled
            ).onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to update preferences"
            }
        }
    }

    fun syncFcmTokenNow(context: Context, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!hasFirebaseAuth) {
            onComplete(false, "Sign in with Google cloud sync to register an FCM token.")
            return
        }
        viewModelScope.launch {
            FcmTokenManager.syncCurrentToken(context, repository, currentUserId)
                .onSuccess {
                    _actionMessage.value = "Device push token synced"
                    onComplete(true, "Push token synced")
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Could not register device token"
                    _actionMessage.value = msg
                    onComplete(false, msg)
                }
        }
    }

    fun deleteOrLeaveConversation(
        conversation: Conversation,
        onResult: (success: Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.removeOrDeleteConversation(conversation)
                .onSuccess { wasDeleted ->
                    _actionMessage.value = if (wasDeleted) "Conversation deleted" else "Left channel"
                    onResult(true)
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to remove conversation"
                    onResult(false)
                }
        }
    }

    fun togglePinConversation(conversation: Conversation) {
        viewModelScope.launch {
            repository.togglePinConversation(conversation)
                .onSuccess { pinned ->
                    _actionMessage.value = if (pinned) "Conversation pinned" else "Conversation unpinned"
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update pin"
                }
        }
    }

    fun toggleMuteConversation(conversation: Conversation) {
        viewModelScope.launch {
            repository.toggleMuteConversation(conversation)
                .onSuccess { muted ->
                    _actionMessage.value = if (muted) "Notifications muted" else "Notifications unmuted"
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update mute"
                }
        }
    }

    fun toggleMarkUnread(conversation: Conversation) {
        viewModelScope.launch {
            repository.toggleMarkUnread(conversation)
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update read status"
                }
        }
    }

    fun setUserBlocked(targetUserId: String, blocked: Boolean) {
        viewModelScope.launch {
            repository.setUserBlocked(targetUserId, blocked)
                .onSuccess {
                    _actionMessage.value = if (blocked) "Contact blocked" else "Contact unblocked"
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update block setting"
                }
        }
    }

    fun setForegroundPresence(inForeground: Boolean) {
        if (presenceManager != null) {
            presenceManager.setForeground(inForeground)
        } else if (hasFirebaseAuth && Firebase.auth.currentUser != null) {
            viewModelScope.launch {
                repository.setOnlinePresence(inForeground)
            }
        }
    }

    suspend fun prepareForSignOut(context: Context? = null) {
        if (context != null) {
            if (hasFirebaseAuth) {
                FcmTokenManager.removeTokenBeforeSignOut(context, repository)
            }
            com.example.notifications.NotificationHelper.clearAllNotificationState(context)
        }
        presenceManager?.markOfflineBeforeSignOut()
        syncCoordinator?.stopSession()
        MessageNotificationCoordinator.stopObserving()
    }

    private companion object {
        const val TAG = "MainMessagingVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
