package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.IosBlue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.VoiceNotePlayer
import com.example.audio.VoiceNoteRecorder
import com.example.data.model.CallTarget
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.ImageUtils
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatDayHeader
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.isSameCalendarDay
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.OnlinePresenceGreen
import com.example.ui.viewmodel.ChatRoomViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    viewModel: ChatRoomViewModel,
    currentUserProfile: UserProfile?,
    allPeople: List<UserProfile>,
    readReceiptsEnabled: Boolean,
    viewerShowsOnline: Boolean = true,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onStartCall: (target: CallTarget, isVideoCall: Boolean) -> Unit = { _, _ -> },
    allConversations: List<Conversation> = emptyList(),
    blockedUserIds: Set<String> = emptySet(),
    onTogglePinConversation: (Conversation) -> Unit = {},
    onToggleMuteConversation: (Conversation) -> Unit = {},
    onToggleBlockUser: (targetUserId: String, blocked: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = rememberIosHaptics()
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val conversationState by viewModel.conversationState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsStateWithLifecycle()
    val hasMoreOlder by viewModel.hasMoreOlder.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()

    val conversation = (conversationState as? UiState.Success)?.data
    val hasActivePeerActivity = remember(conversation?.typingBy, conversation?.recordingBy) {
        conversation?.typingBy?.isNotEmpty() == true || conversation?.recordingBy?.isNotEmpty() == true
    }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasActivePeerActivity) {
        while (true) {
            delay(if (hasActivePeerActivity) 2_000L else 20_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    var draftText by rememberSaveable(viewModel.conversationId) { mutableStateOf("") }
    var selectedMessageForActions by remember { mutableStateOf<Message?>(null) }
    var messageToForward by remember { mutableStateOf<Message?>(null) }
    var lightboxState by remember { mutableStateOf<Triple<Bitmap, String, String>?>(null) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showGroupInfoDialog by remember { mutableStateOf(false) }
    var showDirectInfoDialog by remember { mutableStateOf(false) }
    var showSearchBar by rememberSaveable(viewModel.conversationId) { mutableStateOf(false) }
    var inChatSearchQuery by rememberSaveable(viewModel.conversationId) { mutableStateOf("") }
    var isTrayOpen by remember { mutableStateOf(false) }
    var pendingGroupCallVideo by remember { mutableStateOf<Boolean?>(null) }

    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val voicePlayer = remember(context) { VoiceNotePlayer(context) }
    val voicePlaybackState by voicePlayer.state.collectAsStateWithLifecycle()
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancel()
            voicePlayer.release()
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val photoMessages = remember(messages) {
        messages.filter { it.type == Message.TYPE_IMAGE && !it.imageBase64.isNullOrBlank() }
    }
    val peopleMap = remember(allPeople) { allPeople.associateBy { it.userId } }
    val filteredMessages = remember(messages, showSearchBar, inChatSearchQuery) {
        if (!showSearchBar || inChatSearchQuery.isBlank()) messages
        else messages.filter { msg ->
            msg.text.contains(inChatSearchQuery, ignoreCase = true) ||
                msg.senderName.contains(inChatSearchQuery, ignoreCase = true) ||
                (msg.pollQuestion?.contains(inChatSearchQuery, ignoreCase = true) == true) ||
                msg.pollOptions.any { it.contains(inChatSearchQuery, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()
    val showJumpToBottom by remember {
        derivedStateOf { listState.canScrollForward && filteredMessages.isNotEmpty() }
    }

    val beginVoiceRecording: () -> Unit = {
        voicePlayer.stop()
        voiceRecorder.start()
            .onSuccess {
                isTrayOpen = false
                recordingSeconds = 0
                isRecordingAudio = true
            }
            .onFailure { err ->
                isRecordingAudio = false
                Toast.makeText(
                    context,
                    err.localizedMessage ?: "Microphone unavailable",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    val finishAndSendVoiceRecording: () -> Unit = {
        val conv = conversation
        val profile = currentUserProfile
        isRecordingAudio = false
        scope.launch {
            val encoded = withContext(Dispatchers.IO) {
                voiceRecorder.stopAndEncode()
            }
            if (conv != null && profile != null) {
                encoded.onSuccess { note ->
                    viewModel.sendVoiceNote(conv, profile, note.base64, note.durationSec) {}
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        err.localizedMessage ?: "Voice recording too short",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(isRecordingAudio) {
        viewModel.onRecordingStateChanged(isRecordingAudio)
        if (isRecordingAudio) {
            while (isRecordingAudio && recordingSeconds < 29) {
                delay(1000L)
                recordingSeconds += 1
            }
            if (isRecordingAudio && recordingSeconds >= 29) {
                finishAndSendVoiceRecording()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed for voice messages.", Toast.LENGTH_SHORT).show()
        }
    }

    val requestStartVoiceRecording: () -> Unit = {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            beginVoiceRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && conversation != null && currentUserProfile != null) {
            scope.launch {
                val base64 = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToCompressedBase64(context, uri)
                }
                if (base64 == null) {
                    Toast.makeText(context, "Could not process selected photo.", Toast.LENGTH_SHORT).show()
                } else {
                    val caption = draftText
                    haptics.success()
                    viewModel.sendImage(conversation, currentUserProfile, caption, base64) {
                        draftText = ""
                        isTrayOpen = false
                    }
                }
            }
        }
    }

    LaunchedEffect(editingMessage?.id) {
        editingMessage?.let { draftText = it.text }
    }

    LaunchedEffect(conversation?.id, conversation?.unreadCounts) {
        if (conversation != null) {
            viewModel.markRead(conversation)
        }
    }

    var initialScrollDone by remember(viewModel.conversationId) { mutableStateOf(false) }
    var lastBottomMessageKey by remember(viewModel.conversationId) { mutableStateOf<String?>(null) }
    val isNearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || lastVisible >= total - 3
        }
    }

    LaunchedEffect(messages, showSearchBar) {
        if (messages.isEmpty() || showSearchBar) return@LaunchedEffect
        val bottomMsg = messages.last()
        val bottomKey = bottomMsg.clientMessageId.ifBlank { bottomMsg.id }
        if (!initialScrollDone) {
            initialScrollDone = true
            lastBottomMessageKey = bottomKey
            listState.scrollToItem(messages.lastIndex)
        } else if (bottomKey != lastBottomMessageKey) {
            val shouldScroll = isNearBottom || bottomMsg.senderId == viewModel.currentUserId
            lastBottomMessageKey = bottomKey
            if (shouldScroll) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, messages.size, hasMoreOlder, isLoadingOlder, showSearchBar) {
        if (!showSearchBar && hasMoreOlder && !isLoadingOlder && messages.isNotEmpty() && listState.firstVisibleItemIndex <= 2) {
            viewModel.loadOlderMessages()
        }
    }

    val otherId = conversation?.otherParticipantId(viewModel.currentUserId)
    val otherProfile = remember(otherId, allPeople) {
        otherId?.let { id -> allPeople.firstOrNull { it.userId == id } }
    }
    val isGroup = conversation?.type == Conversation.TYPE_GROUP
    val isSelfChat = !isGroup && (conversation?.participantIds?.distinct()?.size ?: 0) <= 1
    val isOtherBlocked = !isGroup && !isSelfChat && otherId != null && otherId in blockedUserIds
    val canCallInThread = conversation != null && !isSelfChat && !isOtherBlocked &&
        conversation.participantIds.any { it != viewModel.currentUserId }
    val title = conversation?.displayTitleFor(viewModel.currentUserId) ?: "Chat"
    val isOtherOnline = !isGroup && !isOtherBlocked &&
        (otherProfile?.isEffectivelyOnline(nowMillis, viewerShowsOnline) == true)
    val typingLabel = remember(
        conversation?.typingBy,
        conversation?.recordingBy,
        peopleMap,
        nowMillis,
        viewModel.currentUserId,
        isOtherBlocked
    ) {
        if (isOtherBlocked) null
        else conversation?.typingOrRecordingLabel(viewModel.currentUserId, peopleMap, nowMillis)
    }
    val visibleLastSeen = remember(otherProfile, viewerShowsOnline) {
        otherProfile?.visibleLastSeen(viewerShowsOnline)
    }
    val subtitle = when {
        conversation == null -> "Loading…"
        isOtherBlocked -> "Blocked contact"
        typingLabel != null -> typingLabel
        isGroup -> {
            val base = "${conversation.participantIds.size} members"
            if (conversation.topic.isNotBlank()) "$base · ${conversation.topic}" else base
        }
        otherProfile != null -> {
            when {
                isOtherOnline -> "Active now · ${otherProfile.statusVibe}"
                visibleLastSeen != null -> "Seen ${formatRelativeTime(visibleLastSeen)}"
                else -> "@${otherProfile.handle}"
            }
        }
        else -> "Direct conversation"
    }

    val chatCanvasColor = if (isDark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = chatCanvasColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = {
                                haptics.lightTap()
                                onBack()
                            },
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to chats",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = conversation != null) {
                                haptics.lightTap()
                                if (isGroup) showGroupInfoDialog = true
                                else showDirectInfoDialog = true
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        UserAvatar(
                            name = title,
                            photoUrl = if (isGroup) null else (otherProfile?.photoUrl ?: conversation?.participantPhotos?.get(otherId)),
                            size = 40.dp,
                            isGroup = isGroup,
                            showPresence = !isGroup && otherProfile != null,
                            isOnline = isOtherOnline,
                            showStoryRing = isOtherOnline
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (typingLabel != null || isOtherOnline) OnlinePresenceGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (typingLabel != null || isOtherOnline) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .border(
                                width = 0.6.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (canCallInThread) {
                                val triggerCall: (Boolean) -> Unit = { isVideo ->
                                    if (isGroup) {
                                        pendingGroupCallVideo = isVideo
                                    } else if (otherId != null) {
                                        val peerName = otherProfile?.displayName
                                            ?: conversation.participantNames[otherId]
                                            ?: title
                                        val peerPhoto = otherProfile?.photoUrl
                                            ?: conversation.participantPhotos[otherId]
                                        onStartCall(
                                            CallTarget(
                                                userId = otherId,
                                                displayName = peerName,
                                                photoUrl = peerPhoto,
                                                conversationId = conversation.id
                                            ),
                                            isVideo
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        haptics.lightTap()
                                        triggerCall(false)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("voice_call_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = "Start HD voice call",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptics.lightTap()
                                        triggerCall(true)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("video_call_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = "Start HD video call",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    haptics.lightTap()
                                    if (showSearchBar) {
                                        showSearchBar = false
                                        inChatSearchQuery = ""
                                    } else {
                                        showSearchBar = true
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("chat_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search conversation",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.lightTap()
                                    if (isGroup) showGroupInfoDialog = true
                                    else showDirectInfoDialog = true
                                },
                                enabled = conversation != null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag(if (isGroup) "group_info_button" else "direct_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = if (isGroup) "Channel info" else "Contact info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)

            AnimatedVisibility(visible = !isNetworkOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Offline · Cached messages shown, new messages queued automatically",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showSearchBar) {
                ChatRoomSearchBar(
                    query = inChatSearchQuery,
                    matchCount = filteredMessages.size,
                    onQueryChange = { inChatSearchQuery = it },
                    onClose = {
                        showSearchBar = false
                        inChatSearchQuery = ""
                    }
                )
            }

            if (conversation != null && !conversation.pinnedMessageId.isNullOrBlank()) {
                PinnedMessageBanner(
                    senderName = conversation.pinnedMessageSenderName.orEmpty(),
                    preview = conversation.pinnedMessagePreview.orEmpty(),
                    onJumpToPinned = {
                        val idx = filteredMessages.indexOfFirst { it.id == conversation.pinnedMessageId }
                        if (idx >= 0) {
                            scope.launch { listState.animateScrollToItem(idx) }
                        } else {
                            Toast.makeText(context, "Pinned message is not in current view.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUnpin = {
                        viewModel.pinOrUnpinMessage(conversation, null)
                    }
                )
            }

            AnimatedVisibility(visible = errorBanner != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearErrorBanner) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss error")
                        }
                    }
                }
            }

            // Atmospheric Messenger Conversation Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(chatCanvasColor)
                    .drawBehind {
                        val topAlpha = if (isDark) 0.14f else 0.08f
                        val bottomAlpha = if (isDark) 0.10f else 0.06f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(IosBlue.copy(alpha = topAlpha), Color.Transparent),
                                center = Offset(size.width * 0.85f, size.height * 0.12f),
                                radius = size.width * 0.8f
                            ),
                            radius = size.width * 0.8f,
                            center = Offset(size.width * 0.85f, size.height * 0.12f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(AuroraCyan.copy(alpha = bottomAlpha), Color.Transparent),
                                center = Offset(size.width * 0.12f, size.height * 0.88f),
                                radius = size.width * 0.72f
                            ),
                            radius = size.width * 0.72f,
                            center = Offset(size.width * 0.12f, size.height * 0.88f)
                        )
                    }
            ) {
                when (val state = messagesState) {
                    is UiState.Loading -> {
                        ChatRoomSkeletonList()
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (filteredMessages.isEmpty()) {
                            ChatRoomEmptyState(
                                isSearchFiltered = showSearchBar && inChatSearchQuery.isNotBlank(),
                                searchQuery = inChatSearchQuery
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(
                                    items = filteredMessages,
                                    key = { _, msg -> msg.clientMessageId.ifBlank { msg.id } },
                                    contentType = { _, msg -> msg.type }
                                ) { index, msg ->
                                    val prev = filteredMessages.getOrNull(index - 1)
                                    val next = filteredMessages.getOrNull(index + 1)
                                    val showDayHeader = prev == null || !isSameCalendarDay(prev.createdAt, msg.createdAt)
                                    val isFirstInCluster = showDayHeader || !areClusteredMessages(prev, msg)
                                    val isLastInCluster = next == null ||
                                        !isSameCalendarDay(msg.createdAt, next.createdAt) ||
                                        !areClusteredMessages(msg, next)

                                    if (showDayHeader) {
                                        DayDividerPill(label = formatDayHeader(msg.createdAt))
                                    }

                                    MessageBubble(
                                        message = msg,
                                        isOwnMessage = msg.senderId == viewModel.currentUserId,
                                        isGroup = isGroup,
                                        currentUserId = viewModel.currentUserId,
                                        isFirstInCluster = isFirstInCluster,
                                        isLastInCluster = isLastInCluster,
                                        conversationParticipants = conversation?.participantIds.orEmpty(),
                                        showReadReceipts = readReceiptsEnabled,
                                        onLongPressOrTapActions = { selectedMessageForActions = msg },
                                        onToggleReaction = { emoji -> viewModel.toggleReaction(msg, emoji) },
                                        onVotePoll = { optionIdx -> viewModel.votePoll(msg, optionIdx) },
                                        onOpenImage = { bmp ->
                                            lightboxState = Triple(bmp, msg.senderName, msg.text)
                                        },
                                        onRetryFailedMessage = { failed ->
                                            val cid = failed.clientMessageId.ifBlank { failed.id }
                                            viewModel.retryFailedMessage(cid)
                                        },
                                        voicePlaybackState = voicePlaybackState,
                                        onToggleVoicePlayback = { voiceMsg ->
                                            val encoded = voiceMsg.audioBase64
                                            if (!encoded.isNullOrBlank()) {
                                                voicePlayer.togglePlayback(voiceMsg.id, encoded)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoadingOlder && !showSearchBar,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loading earlier messages…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                JumpToLatestButton(
                    visible = showJumpToBottom,
                    onClick = {
                        scope.launch {
                            if (filteredMessages.isNotEmpty()) {
                                listState.animateScrollToItem(filteredMessages.lastIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 12.dp)
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = typingLabel != null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 10.dp)
                ) {
                    TypingIndicatorPill(label = typingLabel.orEmpty())
                }
            }

            // Reply / Edit Banner
            val bannerTarget = editingMessage ?: replyingTo
            androidx.compose.animation.AnimatedVisibility(visible = bannerTarget != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (editingMessage != null) {
                                        "Editing message"
                                    } else {
                                        "Replying to ${replyingTo?.senderName ?: ""}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = bannerTarget?.text?.ifBlank { "Photo / Poll attachment" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.lightTap()
                                    if (editingMessage != null) draftText = ""
                                    viewModel.setEditingTarget(null)
                                    viewModel.setReplyTarget(null)
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel reply or edit")
                            }
                        }
                    }
                }
            }

            if (isOtherBlocked) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You blocked this contact",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(
                            onClick = { onToggleBlockUser(otherId, false) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Unblock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Expandable iPhone Quick Options Tray
                IosQuickInsertTray(
                    visible = isTrayOpen && !isRecordingAudio,
                    onPickPhoto = {
                        isTrayOpen = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCreatePoll = {
                        isTrayOpen = false
                        showPollDialog = true
                    },
                    onRecordVoice = {
                        isTrayOpen = false
                        requestStartVoiceRecording()
                    },
                    onInsertQuickReply = { snippet ->
                        draftText = if (draftText.isBlank()) snippet else "$draftText $snippet"
                        isTrayOpen = false
                    }
                )

                // Capsule iOS Composer Bar
                IosMessageComposerBar(
                    draftText = draftText,
                    onDraftChange = { newText ->
                        draftText = newText
                        viewModel.onDraftChanged(newText)
                    },
                    canInteract = conversation != null && currentUserProfile != null,
                    isSending = isSending,
                    isTrayOpen = isTrayOpen,
                    onToggleTray = { isTrayOpen = !isTrayOpen },
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenPoll = { showPollDialog = true },
                    onSend = {
                        val conv = conversation ?: return@IosMessageComposerBar
                        val profile = currentUserProfile ?: return@IosMessageComposerBar
                        if (draftText.trim().isEmpty()) return@IosMessageComposerBar
                        haptics.success()
                        viewModel.sendOrEditText(conv, profile, draftText) {
                            draftText = ""
                            isTrayOpen = false
                        }
                    },
                    isRecordingAudio = isRecordingAudio,
                    recordingSeconds = recordingSeconds,
                    onStartVoiceRecord = requestStartVoiceRecording,
                    onCancelVoiceRecord = {
                        isRecordingAudio = false
                        voiceRecorder.cancel()
                    },
                    onSendVoiceRecord = finishAndSendVoiceRecording
                )
            }
        }
    }

    selectedMessageForActions?.let { msg ->
        val isPinned = conversation?.pinnedMessageId == msg.id
        MessageActionsDialog(
            message = msg,
            isOwnMessage = msg.senderId == viewModel.currentUserId,
            isPinnedInChat = isPinned,
            onDismiss = { selectedMessageForActions = null },
            onReact = { emoji -> viewModel.toggleReaction(msg, emoji) },
            onReply = { viewModel.setReplyTarget(msg) },
            onEdit = { viewModel.setEditingTarget(msg) },
            onDelete = { viewModel.deleteMessage(msg) },
            onCopy = {
                clipboardManager.setText(AnnotatedString(msg.text))
                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
            },
            onForward = if (allConversations.isNotEmpty()) {
                { messageToForward = msg }
            } else null,
            onTogglePin = conversation?.let { conv ->
                {
                    viewModel.pinOrUnpinMessage(conv, if (isPinned) null else msg) { pinned ->
                        Toast.makeText(
                            context,
                            if (pinned) "Message pinned to top" else "Message unpinned",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onRetry = if (msg.deliveryState == Message.STATUS_FAILED) {
                {
                    viewModel.retryFailedMessage(msg.clientMessageId.ifBlank { msg.id })
                }
            } else null
        )
    }

    messageToForward?.let { original ->
        val forwardTargets = remember(allConversations, conversation?.id) {
            allConversations.filter { it.id != conversation?.id }
        }
        ForwardMessageDialog(
            conversations = forwardTargets,
            currentUserId = viewModel.currentUserId,
            peopleMap = peopleMap,
            onDismiss = { messageToForward = null },
            onSelectConversation = { targetConv ->
                val profile = currentUserProfile
                messageToForward = null
                if (profile != null) {
                    viewModel.forwardMessage(targetConv, profile, original) {
                        Toast.makeText(
                            context,
                            "Forwarded to ${targetConv.displayTitleFor(viewModel.currentUserId)}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    lightboxState?.let { (bitmap, senderName, caption) ->
        PhotoLightboxDialog(
            bitmap = bitmap,
            senderName = senderName,
            caption = caption,
            onDismiss = { lightboxState = null }
        )
    }

    if (showPollDialog && conversation != null && currentUserProfile != null) {
        CreatePollDialog(
            onDismiss = { showPollDialog = false },
            onCreatePoll = { question, options ->
                showPollDialog = false
                haptics.success()
                viewModel.sendPoll(conversation, currentUserProfile, question, options) {}
            }
        )
    }

    if (showGroupInfoDialog && conversation != null) {
        GroupInfoDialog(
            conversation = conversation,
            allPeople = allPeople,
            currentUserId = viewModel.currentUserId,
            onDismiss = { showGroupInfoDialog = false },
            onUpdateGroupInfo = { newTitle, newTopic ->
                viewModel.updateGroupInfo(conversation, newTitle, newTopic)
            },
            onAddMembers = { members ->
                viewModel.addMembers(conversation, members)
            },
            onLeaveGroup = {
                showGroupInfoDialog = false
                viewModel.leaveGroup(conversation, onLeft = onBack)
            },
            onDeleteChannel = {
                showGroupInfoDialog = false
                viewModel.deleteConversation(conversation, onDeleted = onBack)
            },
            photoMessages = photoMessages,
            onOpenPhoto = { bmp, sender, caption ->
                showGroupInfoDialog = false
                lightboxState = Triple(bmp, sender, caption)
            },
            onTogglePin = { onTogglePinConversation(conversation) },
            onToggleMute = { onToggleMuteConversation(conversation) }
        )
    }

    if (showDirectInfoDialog && conversation != null) {
        val conv = conversation
        val directTarget = if (!isSelfChat && otherId != null) {
            CallTarget(
                userId = otherId,
                displayName = otherProfile?.displayName ?: conv.participantNames[otherId] ?: title,
                photoUrl = otherProfile?.photoUrl ?: conv.participantPhotos[otherId],
                conversationId = conv.id
            )
        } else null

        DirectChatInfoDialog(
            conversation = conv,
            otherProfile = otherProfile ?: currentUserProfile.takeIf { otherId == null || otherId == viewModel.currentUserId },
            currentUserId = viewModel.currentUserId,
            messageCount = messages.size,
            onDismiss = { showDirectInfoDialog = false },
            onDeleteConversation = {
                showDirectInfoDialog = false
                viewModel.deleteConversation(conv, onDeleted = onBack)
            },
            onStartVoiceCall = directTarget?.let { target -> { onStartCall(target, false) } },
            onStartVideoCall = directTarget?.let { target -> { onStartCall(target, true) } },
            photoMessages = photoMessages,
            onOpenPhoto = { bmp, sender, caption ->
                showDirectInfoDialog = false
                lightboxState = Triple(bmp, sender, caption)
            },
            onTogglePin = { onTogglePinConversation(conv) },
            onToggleMute = { onToggleMuteConversation(conv) },
            isOtherBlocked = isOtherBlocked,
            viewerShowsOnline = viewerShowsOnline,
            onToggleBlock = otherId?.takeIf { !isSelfChat }?.let { targetUid ->
                {
                    onToggleBlockUser(targetUid, !isOtherBlocked)
                }
            }
        )
    }

    val groupCallVideo = pendingGroupCallVideo
    if (groupCallVideo != null && conversation != null) {
        GroupCallMemberPickerDialog(
            conversation = conversation,
            currentUserId = viewModel.currentUserId,
            isVideoCall = groupCallVideo,
            onDismiss = { pendingGroupCallVideo = null },
            onSelectMember = { memberId, memberName, memberPhoto ->
                pendingGroupCallVideo = null
                onStartCall(
                    CallTarget(
                        userId = memberId,
                        displayName = memberName,
                        photoUrl = memberPhoto,
                        conversationId = conversation.id
                    ),
                    groupCallVideo
                )
            }
        )
    }
}

private fun areClusteredMessages(first: Message?, second: Message?): Boolean {
    if (first == null || second == null) return false
    if (first.senderId != second.senderId) return false
    val t1 = first.createdAt?.toDate()?.time ?: return false
    val t2 = second.createdAt?.toDate()?.time ?: return false
    return abs(t2 - t1) <= 180_000L
}
