package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.CallTarget
import com.example.data.model.Conversation
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.GlassmorphismCard
import com.example.ui.components.IosSearchField
import com.example.ui.components.IosSegmentedControl
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.SegmentedTabItem
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.PulseBrushes
import com.example.ui.viewmodel.ChatFilter
import kotlinx.coroutines.delay

@Composable
fun ChatsScreen(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    conversationsState: UiState<List<Conversation>>,
    peopleState: UiState<List<UserProfile>>,
    searchQuery: String,
    selectedFilter: ChatFilter,
    selectedConversationId: String?,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (ChatFilter) -> Unit,
    onOpenConversation: (String) -> Unit,
    onStartDirectChat: (UserProfile) -> Unit,
    onCreateGroupChat: (title: String, topic: String, members: List<UserProfile>) -> Unit,
    onDeleteConversation: (Conversation, onResult: (Boolean) -> Unit) -> Unit = { _, onResult -> onResult(false) },
    onProfileClick: () -> Unit = {},
    blockedUserIds: Set<String> = emptySet(),
    callHistory: List<CallSession> = emptyList(),
    onTogglePinConversation: (Conversation) -> Unit = {},
    onToggleMuteConversation: (Conversation) -> Unit = {},
    onToggleMarkUnread: (Conversation) -> Unit = {},
    onStartCall: (target: CallTarget, isVideoCall: Boolean) -> Unit = { _, _ -> },
    cloudSyncPhoneNumber: String? = null,
    onLinkGoogleAccount: (() -> Unit)? = null,
    viewerShowsOnline: Boolean = true,
    readReceiptsEnabled: Boolean = true,
    isNetworkOnline: Boolean = true,
    pendingOutboxCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    var showNewChatDialog by remember { mutableStateOf(false) }
    var openDialogInGroupMode by remember { mutableStateOf(false) }
    var showCallHistoryDialog by remember { mutableStateOf(false) }
    var optionsTargetConversation by remember { mutableStateOf<Conversation?>(null) }
    val pendingDeleteIds = remember { mutableStateListOf<String>() }

    val allConversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val hasActiveTypingOrRecording = remember(allConversations) {
        allConversations.any { it.typingBy.isNotEmpty() || it.recordingBy.isNotEmpty() }
    }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasActiveTypingOrRecording) {
        while (true) {
            delay(if (hasActiveTypingOrRecording) 2_500L else 20_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val peopleList = (peopleState as? UiState.Success)?.data.orEmpty()
    val otherPeople = remember(peopleList, currentUserId, blockedUserIds, nowMillis, viewerShowsOnline) {
        peopleList.filter { it.userId != currentUserId && it.userId !in blockedUserIds }
            .sortedByDescending { it.isEffectivelyOnline(nowMillis, viewerShowsOnline) }
    }
    val onlinePeople = remember(otherPeople, nowMillis, viewerShowsOnline) {
        otherPeople.filter { it.isEffectivelyOnline(nowMillis, viewerShowsOnline) }
    }
    val peopleMap = remember(peopleList) { peopleList.associateBy { it.userId } }
    val unreadConvCount = remember(allConversations, currentUserId) {
        allConversations.count { it.unreadCountFor(currentUserId) > 0L }
    }

    val segmentedItems = remember(unreadConvCount) {
        ChatFilter.entries.map { filter ->
            SegmentedTabItem(
                value = filter,
                label = filter.label,
                badgeCount = if (filter == ChatFilter.UNREAD) unreadConvCount else 0,
                testTag = "filter_chip_${filter.name.lowercase()}"
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LiquidGlassBackdrop()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 0.8.dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            haptics.lightTap()
                            openDialogInGroupMode = false
                            showNewChatDialog = true
                        }
                        .testTag("new_chat_fab")
                ) {
                    Row(
                        modifier = Modifier
                            .background(PulseBrushes.BrandGradient)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = stringResource(R.string.new_chat),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Chat",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                ChatsHeaderIsland(
                    currentUserProfile = currentUserProfile,
                    onlinePeople = onlinePeople,
                    unreadConvCount = unreadConvCount,
                    viewerShowsOnline = viewerShowsOnline,
                    isNetworkOnline = isNetworkOnline,
                    pendingOutboxCount = pendingOutboxCount,
                    onNewGroupClick = {
                        openDialogInGroupMode = true
                        showNewChatDialog = true
                    },
                    onCallHistoryClick = { showCallHistoryDialog = true },
                    onPersonClick = onStartDirectChat,
                    onProfileClick = onProfileClick
                )

                IosSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = "Search chats, people & topics",
                    inputTestTag = "chats_search_input",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )

                when (conversationsState) {
                    is UiState.Loading -> {
                        InboxSkeletonList()
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassmorphismCard(
                                contentPadding = PaddingValues(20.dp),
                                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = conversationsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        val filtered = remember(
                            conversationsState.data,
                            searchQuery,
                            selectedFilter,
                            currentUserId,
                            pendingDeleteIds.size
                        ) {
                            conversationsState.data.filter { conv ->
                                if (conv.id in pendingDeleteIds) return@filter false
                                val matchesFilter = when (selectedFilter) {
                                    ChatFilter.ALL -> true
                                    ChatFilter.DIRECT -> conv.type == Conversation.TYPE_DIRECT
                                    ChatFilter.GROUPS -> conv.type == Conversation.TYPE_GROUP
                                    ChatFilter.UNREAD -> conv.unreadCountFor(currentUserId) > 0L
                                }
                                val title = conv.displayTitleFor(currentUserId)
                                val matchesSearch = searchQuery.isBlank() ||
                                    title.contains(searchQuery, ignoreCase = true) ||
                                    conv.lastMessageText.contains(searchQuery, ignoreCase = true) ||
                                    conv.topic.contains(searchQuery, ignoreCase = true)
                                matchesFilter && matchesSearch
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 112.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            if (!cloudSyncPhoneNumber.isNullOrBlank() && onLinkGoogleAccount != null) {
                                item(key = "phone_session_cloud_banner", contentType = "banner") {
                                    GlassmorphismCard(
                                        shape = RoundedCornerShape(18.dp),
                                        highlighted = true,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp)
                                            .clickable(onClick = onLinkGoogleAccount)
                                            .testTag("link_google_cloud_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Verified: $cloudSyncPhoneNumber",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Tap to link Google Account for real-time cloud chats & calls",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "Link",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "active_orbit_strip", contentType = "orbit") {
                                AnimatedVisibility(
                                    visible = otherPeople.isNotEmpty() && searchQuery.isBlank(),
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    ActivePeopleStoryStrip(
                                        people = otherPeople,
                                        nowMillis = nowMillis,
                                        viewerShowsOnline = viewerShowsOnline,
                                        onNewGroupClick = {
                                            openDialogInGroupMode = true
                                            showNewChatDialog = true
                                        },
                                        onPersonClick = onStartDirectChat
                                    )
                                }
                            }

                            item(key = "filter_segmented_bar", contentType = "filter") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    IosSegmentedControl(
                                        items = segmentedItems,
                                        selectedValue = selectedFilter,
                                        onSelect = onSelectFilter,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when {
                                                searchQuery.isNotBlank() -> "SEARCH RESULTS"
                                                selectedFilter == ChatFilter.ALL -> "CONVERSATIONS"
                                                else -> selectedFilter.label.uppercase()
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${filtered.size} ${if (filtered.size == 1) "thread" else "threads"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (filtered.isEmpty()) {
                                item(key = "empty_chats_card", contentType = "empty") {
                                    EmptyChatsView(
                                        isFiltered = searchQuery.isNotBlank() || selectedFilter != ChatFilter.ALL,
                                        onStartDirect = {
                                            openDialogInGroupMode = false
                                            showNewChatDialog = true
                                        },
                                        onCreateGroup = {
                                            openDialogInGroupMode = true
                                            showNewChatDialog = true
                                        }
                                    )
                                }
                            } else {
                                items(
                                    items = filtered,
                                    key = { it.id },
                                    contentType = { "conversation" }
                                ) { conversation ->
                                    val otherId = conversation.otherParticipantId(currentUserId)
                                    val otherProfile = otherId?.let { peopleMap[it] }
                                    val isPendingDelete = conversation.id in pendingDeleteIds
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 18.dp)
                                            .animateItem()
                                    ) {
                                        SwipeToDeleteConversationItem(
                                            conversation = conversation,
                                            currentUserId = currentUserId,
                                            otherProfile = otherProfile,
                                            peopleMap = peopleMap,
                                            nowMillis = nowMillis,
                                            viewerShowsOnline = viewerShowsOnline,
                                            readReceiptsEnabled = readReceiptsEnabled,
                                            isSelected = conversation.id == selectedConversationId,
                                            isPendingDelete = isPendingDelete,
                                            onOpen = { onOpenConversation(conversation.id) },
                                            onLongPress = { optionsTargetConversation = conversation },
                                            onDeleteOrLeave = {
                                                if (conversation.id !in pendingDeleteIds) {
                                                    pendingDeleteIds.add(conversation.id)
                                                }
                                                onDeleteConversation(conversation) { success ->
                                                    if (!success) {
                                                        pendingDeleteIds.remove(conversation.id)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewConversationDialog(
            people = peopleList.filter { it.userId !in blockedUserIds },
            currentUserId = currentUserId,
            initialGroupMode = openDialogInGroupMode,
            onDismiss = { showNewChatDialog = false },
            onStartDirect = { person ->
                showNewChatDialog = false
                onStartDirectChat(person)
            },
            onCreateGroup = { title, topic, members ->
                showNewChatDialog = false
                onCreateGroupChat(title, topic, members)
            }
        )
    }

    if (showCallHistoryDialog) {
        CallHistoryDialog(
            calls = callHistory,
            currentUserId = currentUserId,
            blockedUserIds = blockedUserIds,
            onDismiss = { showCallHistoryDialog = false },
            onCallBack = onStartCall
        )
    }

    optionsTargetConversation?.let { targetConv ->
        ConversationOptionsDialog(
            conversation = targetConv,
            currentUserId = currentUserId,
            onDismiss = { optionsTargetConversation = null },
            onTogglePin = { onTogglePinConversation(targetConv) },
            onToggleMute = { onToggleMuteConversation(targetConv) },
            onToggleMarkUnread = { onToggleMarkUnread(targetConv) },
            onDeleteOrLeave = {
                if (targetConv.id !in pendingDeleteIds) {
                    pendingDeleteIds.add(targetConv.id)
                }
                onDeleteConversation(targetConv) { success ->
                    if (!success) {
                        pendingDeleteIds.remove(targetConv.id)
                    }
                }
            }
        )
    }
}
