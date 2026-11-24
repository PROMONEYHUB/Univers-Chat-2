package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UniversDao
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.MessagingRepository
import com.example.data.sync.ConnectivityMonitor
import com.example.data.sync.RealtimeSyncCoordinator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatRoomViewModel(
    private val repository: MessagingRepository,
    val conversationId: String,
    val currentUserId: String,
    private val dao: UniversDao? = null,
    private val syncCoordinator: RealtimeSyncCoordinator? = null,
    private val connectivityMonitor: ConnectivityMonitor? = null,
    private val hasFirebaseAuth: Boolean = (Firebase.auth.currentUser != null)
) : ViewModel() {

    val isNetworkOnline: StateFlow<Boolean> =
        connectivityMonitor?.isConnected ?: MutableStateFlow(true).asStateFlow()

    val conversationState: StateFlow<UiState<Conversation?>> =
        if (dao != null) {
            dao.observeConversation(currentUserId, conversationId)
                .map<_, UiState<Conversation?>> { UiState.Success(it?.toDomain()) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(UiState.Error(error.message ?: "Failed to load conversation"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        } else {
            repository.observeConversation(conversationId)
                .map<Conversation?, UiState<Conversation?>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing conversation $conversationId", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load conversation"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        }

    val messagesState: StateFlow<UiState<List<Message>>> =
        if (dao != null) {
            dao.observeMessages(ownerUserId = currentUserId, conversationId = conversationId)
                .map<_, UiState<List<Message>>> { entities ->
                    UiState.Success(entities.map { it.toDomain() })
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(UiState.Error(error.message ?: "Failed to load messages"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        } else {
            repository.observeMessages(conversationId, currentUserId)
                .map<List<Message>, UiState<List<Message>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing messages for $conversationId", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load messages"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        }

    private val _replyingTo = MutableStateFlow<Message?>(null)
    val replyingTo: StateFlow<Message?> = _replyingTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _hasMoreOlder = MutableStateFlow(true)
    val hasMoreOlder: StateFlow<Boolean> = _hasMoreOlder.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private var typingClearJob: Job? = null
    private var recordingRefreshJob: Job? = null
    private var lastTypingSentMs: Long = 0L
    private var isCurrentlyTyping: Boolean = false
    private var isCurrentlyRecording: Boolean = false

    init {
        if (hasFirebaseAuth) {
            syncCoordinator?.startChatSync(conversationId, currentUserId)
        }
    }

    fun setReplyTarget(message: Message?) {
        _editingMessage.value = null
        _replyingTo.value = message
    }

    fun setEditingTarget(message: Message?) {
        _replyingTo.value = null
        _editingMessage.value = message
    }

    fun clearErrorBanner() {
        _errorBanner.value = null
    }

    fun markRead(conversation: Conversation) {
        if (!hasFirebaseAuth) return
        if (conversation.unreadCountFor(currentUserId) <= 0L) return
        viewModelScope.launch {
            repository.markConversationRead(conversation)
        }
    }

    fun loadOlderMessages() {
        if (!hasFirebaseAuth || syncCoordinator == null) {
            _hasMoreOlder.value = false
            return
        }
        if (_isLoadingOlder.value || !_hasMoreOlder.value) return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            syncCoordinator.loadOlderMessages(conversationId, currentUserId)
                .onSuccess { more ->
                    _hasMoreOlder.value = more
                }
                .onFailure { err ->
                    Log.w(TAG, "Failed to load older messages", err)
                }
            _isLoadingOlder.value = false
        }
    }

    fun onDraftChanged(text: String) {
        if (!hasFirebaseAuth) return
        val hasText = text.isNotBlank()
        if (!hasText) {
            clearTypingIndicator()
            return
        }
        val now = System.currentTimeMillis()
        if (!isCurrentlyTyping || (now - lastTypingSentMs) >= TYPING_THROTTLE_MS) {
            isCurrentlyTyping = true
            lastTypingSentMs = now
            viewModelScope.launch {
                repository.setTypingIndicator(conversationId, isTyping = true)
            }
        }
        typingClearJob?.cancel()
        typingClearJob = viewModelScope.launch {
            delay(TYPING_IDLE_TIMEOUT_MS)
            clearTypingIndicator()
        }
    }

    private fun clearTypingIndicator() {
        typingClearJob?.cancel()
        typingClearJob = null
        if (!isCurrentlyTyping) return
        isCurrentlyTyping = false
        lastTypingSentMs = 0L
        if (hasFirebaseAuth) {
            viewModelScope.launch {
                repository.setTypingIndicator(conversationId, isTyping = false)
            }
        }
    }

    fun onRecordingStateChanged(isRecording: Boolean) {
        if (!hasFirebaseAuth) return
        if (isCurrentlyRecording == isRecording && (isRecording && recordingRefreshJob?.isActive == true)) return
        isCurrentlyRecording = isRecording
        recordingRefreshJob?.cancel()
        if (isRecording) {
            recordingRefreshJob = viewModelScope.launch {
                while (isCurrentlyRecording) {
                    repository.setRecordingIndicator(conversationId, isRecording = true)
                    delay(RECORDING_REFRESH_MS)
                }
            }
        } else {
            recordingRefreshJob = null
            viewModelScope.launch {
                repository.setRecordingIndicator(conversationId, isRecording = false)
            }
        }
    }

    fun sendOrEditText(
        conversation: Conversation,
        senderProfile: UserProfile,
        text: String,
        onSuccess: () -> Unit
    ) {
        val editing = _editingMessage.value
        val replying = _replyingTo.value
        val trimmed = text.trim().take(2000)
        if (trimmed.isEmpty()) return

        clearTypingIndicator()

        if (editing == null && dao != null && hasFirebaseAuth) {
            _editingMessage.value = null
            _replyingTo.value = null
            onSuccess()
            viewModelScope.launch {
                repository.enqueueOptimisticMessage(
                    ownerUserId = currentUserId,
                    conversation = conversation,
                    senderProfile = senderProfile,
                    type = Message.TYPE_TEXT,
                    text = trimmed,
                    previewText = trimmed.take(300),
                    replyTo = replying
                ).onSuccess {
                    syncCoordinator?.triggerOutboxFlush()
                }.onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to queue message"
                }
            }
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            val result = if (editing != null) {
                repository.editMessage(conversationId, editing.id, trimmed)
            } else {
                repository.sendTextMessage(conversation, senderProfile, trimmed, replying)
            }
            result.onSuccess {
                _isSending.value = false
                _editingMessage.value = null
                _replyingTo.value = null
                onSuccess()
            }.onFailure { err ->
                _isSending.value = false
                _errorBanner.value = err.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun sendImage(
        conversation: Conversation,
        senderProfile: UserProfile,
        caption: String,
        imageBase64: String,
        onSuccess: () -> Unit
    ) {
        val replying = _replyingTo.value
        if (imageBase64.isBlank() || imageBase64.length > 80_000) {
            _errorBanner.value = "Image size exceeds allowed limit."
            return
        }
        val cleanCaption = caption.trim().take(2000)
        val preview = if (cleanCaption.isNotBlank()) "📷 $cleanCaption".take(300) else "📷 Shared a photo"

        if (dao != null && hasFirebaseAuth) {
            _replyingTo.value = null
            onSuccess()
            viewModelScope.launch {
                repository.enqueueOptimisticMessage(
                    ownerUserId = currentUserId,
                    conversation = conversation,
                    senderProfile = senderProfile,
                    type = Message.TYPE_IMAGE,
                    text = cleanCaption,
                    previewText = preview,
                    imageBase64 = imageBase64,
                    replyTo = replying
                ).onSuccess {
                    syncCoordinator?.triggerOutboxFlush()
                }.onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to queue image"
                }
            }
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            repository.sendImageMessage(conversation, senderProfile, cleanCaption, imageBase64, replying)
                .onSuccess {
                    _isSending.value = false
                    _replyingTo.value = null
                    onSuccess()
                }
                .onFailure { err ->
                    _isSending.value = false
                    _errorBanner.value = err.localizedMessage ?: "Failed to send image"
                }
        }
    }

    fun sendPoll(
        conversation: Conversation,
        senderProfile: UserProfile,
        question: String,
        options: List<String>,
        onSuccess: () -> Unit
    ) {
        val cleanQuestion = question.trim().take(200)
        val cleanOptions = options.map { it.trim().take(80) }.filter { it.isNotEmpty() }.take(6)
        if (cleanQuestion.isEmpty() || cleanOptions.size < 2) {
            _errorBanner.value = "Provide a question and at least 2 options."
            return
        }

        if (dao != null && hasFirebaseAuth) {
            onSuccess()
            viewModelScope.launch {
                repository.enqueueOptimisticMessage(
                    ownerUserId = currentUserId,
                    conversation = conversation,
                    senderProfile = senderProfile,
                    type = Message.TYPE_POLL,
                    text = cleanQuestion,
                    previewText = "📊 Poll: $cleanQuestion".take(300),
                    pollQuestion = cleanQuestion,
                    pollOptions = cleanOptions
                ).onSuccess {
                    syncCoordinator?.triggerOutboxFlush()
                }.onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to queue poll"
                }
            }
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            repository.sendPollMessage(conversation, senderProfile, cleanQuestion, cleanOptions)
                .onSuccess {
                    _isSending.value = false
                    onSuccess()
                }
                .onFailure { err ->
                    _isSending.value = false
                    _errorBanner.value = err.localizedMessage ?: "Failed to create poll"
                }
        }
    }

    fun sendVoiceNote(
        conversation: Conversation,
        senderProfile: UserProfile,
        audioBase64: String,
        durationSec: Int,
        onSuccess: () -> Unit
    ) {
        val replying = _replyingTo.value
        if (audioBase64.length !in 16..95_000) {
            _errorBanner.value = "Voice message exceeds allowed limit."
            return
        }
        val safeSec = durationSec.coerceIn(1, 180)
        val formatted = "%d:%02d".format(safeSec / 60, safeSec % 60)
        val label = "Voice message ($formatted)"

        if (dao != null && hasFirebaseAuth) {
            _replyingTo.value = null
            onSuccess()
            viewModelScope.launch {
                repository.enqueueOptimisticMessage(
                    ownerUserId = currentUserId,
                    conversation = conversation,
                    senderProfile = senderProfile,
                    type = Message.TYPE_AUDIO,
                    text = label,
                    previewText = "🎤 $label",
                    audioBase64 = audioBase64,
                    audioDurationSec = safeSec,
                    replyTo = replying
                ).onSuccess {
                    syncCoordinator?.triggerOutboxFlush()
                }.onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to queue voice message"
                }
            }
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            repository.sendVoiceMessage(conversation, senderProfile, audioBase64, durationSec, replying)
                .onSuccess {
                    _isSending.value = false
                    _replyingTo.value = null
                    onSuccess()
                }
                .onFailure { err ->
                    _isSending.value = false
                    _errorBanner.value = err.localizedMessage ?: "Failed to send voice message"
                }
        }
    }

    fun retryFailedMessage(clientMessageId: String) {
        if (!hasFirebaseAuth) return
        viewModelScope.launch {
            if (repository.retryFailedOutboxMessage(clientMessageId)) {
                syncCoordinator?.triggerOutboxFlush()
            }
        }
    }

    fun pinOrUnpinMessage(
        conversation: Conversation,
        message: Message?,
        onComplete: (pinned: Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.pinMessage(conversation.id, message)
                .onSuccess { onComplete(message != null) }
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to update pinned message"
                }
        }
    }

    fun forwardMessage(
        targetConversation: Conversation,
        senderProfile: UserProfile,
        original: Message,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSending.value = true
            repository.forwardMessage(targetConversation, senderProfile, original)
                .onSuccess {
                    _isSending.value = false
                    onSuccess()
                }
                .onFailure { err ->
                    _isSending.value = false
                    _errorBanner.value = err.localizedMessage ?: "Failed to forward message"
                }
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            repository.deleteMessage(conversationId, message.id)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to delete message"
                }
        }
    }

    fun toggleReaction(message: Message, emoji: String) {
        viewModelScope.launch {
            repository.toggleReaction(conversationId, message, emoji)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to update reaction"
                }
        }
    }

    fun votePoll(message: Message, optionIndex: Int) {
        viewModelScope.launch {
            repository.votePoll(conversationId, message, optionIndex)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to submit vote"
                }
        }
    }

    fun updateGroupInfo(conversation: Conversation, title: String, topic: String) {
        viewModelScope.launch {
            repository.updateGroupInfo(conversation, title, topic)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to update group info"
                }
        }
    }

    fun addMembers(conversation: Conversation, newMembers: List<UserProfile>) {
        viewModelScope.launch {
            repository.addGroupMembers(conversation, newMembers)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to add members"
                }
        }
    }

    fun leaveGroup(conversation: Conversation, onLeft: () -> Unit) {
        viewModelScope.launch {
            repository.leaveGroup(conversation)
                .onSuccess { onLeft() }
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to leave group"
                }
        }
    }

    fun deleteConversation(conversation: Conversation, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteConversation(conversation)
                .onSuccess { onDeleted() }
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to delete conversation"
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        typingClearJob?.cancel()
        recordingRefreshJob?.cancel()
        val clearTyping = isCurrentlyTyping
        val clearRecording = isCurrentlyRecording
        isCurrentlyTyping = false
        isCurrentlyRecording = false
        if (hasFirebaseAuth && (clearTyping || clearRecording)) {
            CoroutineScope(NonCancellable + Dispatchers.IO).launch {
                if (clearTyping) repository.setTypingIndicator(conversationId, isTyping = false)
                if (clearRecording) repository.setRecordingIndicator(conversationId, isRecording = false)
            }
        }
        syncCoordinator?.stopChatSync(conversationId)
    }

    private companion object {
        const val TAG = "ChatRoomVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val TYPING_THROTTLE_MS = 2_000L
        const val TYPING_IDLE_TIMEOUT_MS = 3_000L
        const val RECORDING_REFRESH_MS = 2_500L
    }
}
