package com.example.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.model.Message
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatDayHeader
import com.example.ui.components.isSameCalendarDay
import com.example.ui.theme.IosDarkBackground
import com.example.ui.theme.IosLightCardSurface
import com.example.ui.theme.OnlinePresenceGreen
import com.example.ui.theme.PulseChatTheme
import com.google.firebase.Timestamp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<Message>,
    currentUserId: String,
    modifier: Modifier = Modifier,
    chatTitle: String = "Messages",
    chatSubtitle: String? = null,
    avatarPhotoUrl: String? = null,
    isGroup: Boolean = false,
    isOnline: Boolean = false,
    showBackButton: Boolean = true,
    onBackClick: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onMessageLongPress: (Message) -> Unit = {},
    onToggleReaction: (Message, String) -> Unit = { _, _ -> },
    onVotePoll: (Message, Int) -> Unit = { _, _ -> }
) {
    val isDark = isSystemInDarkTheme()
    val canvasColor = if (isDark) IosDarkBackground else IosLightCardSurface
    val listState = rememberLazyListState()
    var draftText by rememberSaveable { mutableStateOf("") }
    var lightboxState by remember { mutableStateOf<Triple<Bitmap, String, String>?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val canSend = draftText.trim().isNotEmpty()
    val sendScale by animateFloatAsState(targetValue = if (canSend) 1f else 0.90f, label = "chat_send_scale")
    val resolvedSubtitle = chatSubtitle ?: if (isOnline) "Active now" else "iMessage · Univers Chat"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = canvasColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("chat_screen_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            name = chatTitle,
                            photoUrl = avatarPhotoUrl,
                            size = 40.dp,
                            isGroup = isGroup,
                            showPresence = !isGroup,
                            isOnline = isOnline,
                            showStoryRing = isOnline
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = chatTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = resolvedSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOnline) OnlinePresenceGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(canvasColor)
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.WavingHand,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Say hello to kick off the conversation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_messages_list")
                    ) {
                        itemsIndexed(
                            items = messages,
                            key = { index, msg -> msg.id.ifBlank { "msg_$index" } }
                        ) { index, message ->
                            val prev = messages.getOrNull(index - 1)
                            val next = messages.getOrNull(index + 1)
                            val showDayHeader = prev == null || !isSameCalendarDay(prev.createdAt, message.createdAt)
                            val isFirstInCluster = showDayHeader || !areMessagesClustered(prev, message)
                            val isLastInCluster = next == null ||
                                !isSameCalendarDay(message.createdAt, next.createdAt) ||
                                !areMessagesClustered(message, next)

                            if (showDayHeader) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = formatDayHeader(message.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            MessageBubble(
                                message = message,
                                isOwnMessage = message.senderId == currentUserId,
                                isGroup = isGroup,
                                currentUserId = currentUserId,
                                isFirstInCluster = isFirstInCluster,
                                isLastInCluster = isLastInCluster,
                                onLongPressOrTapActions = { onMessageLongPress(message) },
                                onToggleReaction = { emoji -> onToggleReaction(message, emoji) },
                                onVotePoll = { optionIdx -> onVotePoll(message, optionIdx) },
                                onOpenImage = { bmp ->
                                    lightboxState = Triple(bmp, message.senderName, message.text)
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp)
                                .heightIn(min = 42.dp, max = 120.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .border(
                                    width = 0.8.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draftText.isEmpty()) {
                                Text(
                                    text = "iMessage",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = draftText,
                                onValueChange = { draftText = it.take(2000) },
                                maxLines = 4,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_screen_composer_input")
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledIconButton(
                            onClick = {
                                val trimmed = draftText.trim()
                                if (trimmed.isNotEmpty()) {
                                    onSendMessage(trimmed)
                                    draftText = ""
                                }
                            },
                            enabled = canSend,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = sendScale
                                    scaleY = sendScale
                                }
                                .testTag("chat_screen_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Send message"
                            )
                        }
                    }
                }
            }
        }
    }

    lightboxState?.let { (bitmap, senderName, caption) ->
        PhotoLightboxDialog(
            bitmap = bitmap,
            senderName = senderName,
            caption = caption,
            onDismiss = { lightboxState = null }
        )
    }
}

private fun areMessagesClustered(first: Message?, second: Message?): Boolean {
    if (first == null || second == null) return false
    if (first.senderId != second.senderId) return false
    val t1 = first.createdAt?.toDate()?.time ?: return false
    val t2 = second.createdAt?.toDate()?.time ?: return false
    return abs(t2 - t1) <= 180_000L
}

@Preview(showBackground = true, name = "iOS ChatScreen Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "iOS ChatScreen Dark")
@Composable
private fun ChatScreenPreview() {
    val now = Timestamp.now()
    val sampleMessages = listOf(
        Message(
            id = "m1",
            senderId = "alex",
            senderName = "Alex Rivera",
            text = "Hey! Did you see the new iOS colors across Univers Chat?",
            createdAt = now
        ),
        Message(
            id = "m2",
            senderId = "me",
            senderName = "You",
            text = "Yes! The blue gradient bubbles and grouped surfaces look super clean.",
            reactions = mapOf("alex" to "❤️"),
            createdAt = now
        ),
        Message(
            id = "m3",
            senderId = "me",
            senderName = "You",
            text = "Clustering and day dividers make long threads effortless to scan too.",
            createdAt = now
        )
    )
    PulseChatTheme {
        ChatScreen(
            messages = sampleMessages,
            currentUserId = "me",
            chatTitle = "Alex Rivera",
            isOnline = true
        )
    }
}
