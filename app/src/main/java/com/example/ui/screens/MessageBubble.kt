package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.VoicePlaybackState
import com.example.audio.formatDurationSec
import com.example.data.model.Message
import com.example.data.model.MessageTickState
import com.example.ui.components.ImageUtils
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatMessageClock
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.IosDarkBubbleIncoming
import com.example.ui.theme.IosLightBubbleIncoming
import com.example.ui.theme.PulseBrushes

val QuickReactionEmojis = listOf("❤️", "👍", "😂", "🔥", "😮", "🙌")

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    isGroup: Boolean,
    currentUserId: String,
    isFirstInCluster: Boolean = true,
    isLastInCluster: Boolean = true,
    conversationParticipants: List<String> = emptyList(),
    showReadReceipts: Boolean = true,
    onLongPressOrTapActions: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onVotePoll: (Int) -> Unit,
    onOpenImage: ((Bitmap) -> Unit)? = null,
    onRetryFailedMessage: ((Message) -> Unit)? = null,
    voicePlaybackState: VoicePlaybackState = VoicePlaybackState(),
    onToggleVoicePlayback: ((Message) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val haptics = rememberIosHaptics()
    val tickState = remember(
        message.deliveryState,
        message.deliveredTo,
        message.readBy,
        conversationParticipants,
        showReadReceipts
    ) {
        message.resolveOutgoingTickState(currentUserId, conversationParticipants, showReadReceipts)
    }
    val bubbleShape = if (isOwnMessage) {
        RoundedCornerShape(
            topStart = 20.dp,
            bottomStart = 20.dp,
            topEnd = if (isFirstInCluster) 20.dp else 6.dp,
            bottomEnd = if (isLastInCluster) 4.dp else 6.dp
        )
    } else {
        RoundedCornerShape(
            topEnd = 20.dp,
            bottomEnd = 20.dp,
            topStart = if (isFirstInCluster) 20.dp else 6.dp,
            bottomStart = if (isLastInCluster) 4.dp else 6.dp
        )
    }

    val incomingBubbleColor = if (isDark) IosDarkBubbleIncoming else IosLightBubbleIncoming
    val bubbleBrush: Brush = if (isOwnMessage) {
        if (isDark) PulseBrushes.OutgoingBubbleDark else PulseBrushes.OutgoingBubbleLight
    } else {
        SolidColor(incomingBubbleColor)
    }
    val textColor = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurface
    val subtleColor = if (isOwnMessage) {
        Color.White.copy(alpha = 0.80f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val groupedReactions = remember(message.reactions) {
        message.reactions.values.groupingBy { it }.eachCount().entries.toList()
    }
    val myReaction = message.reactions[currentUserId]

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = if (isFirstInCluster) 8.dp else 2.dp,
                bottom = if (isLastInCluster) 4.dp else 1.dp
            ),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwnMessage && isGroup) {
            if (isLastInCluster) {
                UserAvatar(
                    name = message.senderName,
                    photoUrl = message.senderPhotoUrl,
                    size = 30.dp
                )
            } else {
                Spacer(modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 314.dp)
        ) {
            if (!isOwnMessage && isGroup && isFirstInCluster) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
                )
            }

            val incomingBorderModifier = if (!isOwnMessage) {
                Modifier.border(
                    width = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                    shape = bubbleShape
                )
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleBrush)
                    .then(incomingBorderModifier)
                    .combinedClickable(
                        onClick = {
                            haptics.lightTap()
                            if (isOwnMessage && tickState == MessageTickState.FAILED && onRetryFailedMessage != null) {
                                onRetryFailedMessage(message)
                            } else {
                                onLongPressOrTapActions()
                            }
                        },
                        onLongClick = {
                            haptics.impactMedium()
                            onLongPressOrTapActions()
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.isForwarded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Forward,
                                contentDescription = null,
                                tint = subtleColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Forwarded",
                                style = MaterialTheme.typography.labelSmall,
                                color = subtleColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!message.replyToPreview.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isOwnMessage) Color.White.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(
                                        if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = message.replyToSenderName ?: "Reply",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.replyToPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtleColor,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    val imgB64 = message.imageBase64
                    if (message.type == Message.TYPE_IMAGE && !imgB64.isNullOrBlank()) {
                        val bitmap by produceState<Bitmap?>(
                            initialValue = ImageUtils.getCachedBitmap(imgB64),
                            key1 = imgB64
                        ) {
                            if (value == null) {
                                value = ImageUtils.decodeBase64ToBitmapAsync(imgB64)
                            }
                        }
                        val loadedBitmap = bitmap
                        if (loadedBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(196.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (onOpenImage != null) onOpenImage(loadedBitmap)
                                            else onLongPressOrTapActions()
                                        },
                                        onLongClick = onLongPressOrTapActions
                                    )
                            ) {
                                Image(
                                    bitmap = loadedBitmap.asImageBitmap(),
                                    contentDescription = message.text.ifBlank { "Shared photo" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.ZoomIn,
                                            contentDescription = "Expand photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(196.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Loading photo…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subtleColor
                                )
                            }
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    when {
                        message.type == Message.TYPE_AUDIO -> {
                            VoiceMessageBubbleContent(
                                message = message,
                                isOwnMessage = isOwnMessage,
                                textColor = textColor,
                                subtleColor = subtleColor,
                                playbackState = voicePlaybackState,
                                onTogglePlay = {
                                    haptics.lightTap()
                                    onToggleVoicePlayback?.invoke(message)
                                }
                            )
                        }
                        message.type == Message.TYPE_POLL -> {
                            PollBubbleContent(
                                message = message,
                                currentUserId = currentUserId,
                                isOwnMessage = isOwnMessage,
                                textColor = textColor,
                                subtleColor = subtleColor,
                                onVotePoll = onVotePoll
                            )
                        }
                        message.text.isNotBlank() -> {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "Edited · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = subtleColor
                            )
                        }
                        Text(
                            text = if (tickState == MessageTickState.SENDING && message.createdAt == null) {
                                "Sending…"
                            } else {
                                formatMessageClock(message.createdAt)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = subtleColor
                        )
                        if (isOwnMessage) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val (tickIcon, tickTint, tickDesc) = when (tickState) {
                                MessageTickState.SENDING -> Triple(
                                    Icons.Rounded.Schedule,
                                    subtleColor,
                                    "Sending message"
                                )
                                MessageTickState.SENT -> Triple(
                                    Icons.Rounded.Done,
                                    subtleColor,
                                    "Sent"
                                )
                                MessageTickState.DELIVERED -> Triple(
                                    Icons.Rounded.DoneAll,
                                    subtleColor,
                                    "Delivered"
                                )
                                MessageTickState.READ -> Triple(
                                    Icons.Rounded.DoneAll,
                                    Color(0xFF7DD3FC),
                                    "Read"
                                )
                                MessageTickState.FAILED -> Triple(
                                    Icons.Rounded.ErrorOutline,
                                    Color(0xFFFF8A80),
                                    "Failed to send"
                                )
                            }
                            Icon(
                                imageVector = tickIcon,
                                contentDescription = tickDesc,
                                tint = tickTint,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (isOwnMessage && tickState == MessageTickState.FAILED && onRetryFailedMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            haptics.lightTap()
                            onRetryFailedMessage(message)
                        }
                        .testTag("retry_message_${message.clientMessageId.ifBlank { message.id }}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Retry failed message",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Failed · Tap to retry",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // iOS Tapback-style Reaction Pills
            if (groupedReactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedReactions.forEach { (emoji, count) ->
                        val isMine = myReaction == emoji
                        Surface(
                            shape = CircleShape,
                            color = if (isMine) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(
                                    width = 0.8.dp,
                                    color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable {
                                    haptics.selectionTick()
                                    onToggleReaction(emoji)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PollBubbleContent(
    message: Message,
    currentUserId: String,
    isOwnMessage: Boolean,
    textColor: Color,
    subtleColor: Color,
    onVotePoll: (Int) -> Unit
) {
    val haptics = rememberIosHaptics()
    val totalVotes = message.pollVotes.size
    val myVote = message.pollVotes[currentUserId]?.toIntOrNull()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Poll,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = message.pollQuestion ?: message.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message.pollOptions.forEachIndexed { index, option ->
            val votesForOption = message.pollVotes.values.count { it == index.toString() }
            val targetFraction = if (totalVotes > 0) votesForOption.toFloat() / totalVotes.toFloat() else 0f
            val animatedFraction by animateFloatAsState(targetValue = targetFraction, label = "poll_progress")
            val isSelected = myVote == index

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isOwnMessage) {
                    Color.White.copy(alpha = if (isSelected) 0.28f else 0.14f)
                } else {
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        haptics.selectionTick()
                        onVotePoll(index)
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Your vote",
                                    tint = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                        Text(
                            text = if (totalVotes > 0) "${(targetFraction * 100).toInt()}%" else "0%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = subtleColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animatedFraction },
                        color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary,
                        trackColor = if (isOwnMessage) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "$totalVotes vote${if (totalVotes == 1) "" else "s"} · Tap an option to vote",
        style = MaterialTheme.typography.labelSmall,
        color = subtleColor
    )
}

@Composable
private fun VoiceMessageBubbleContent(
    message: Message,
    isOwnMessage: Boolean,
    textColor: Color,
    subtleColor: Color,
    playbackState: VoicePlaybackState,
    onTogglePlay: () -> Unit
) {
    val isThisMessage = playbackState.activeMessageId == message.id
    val isPlaying = isThisMessage && playbackState.isPlaying
    val targetProgress = if (isThisMessage) playbackState.progress else 0f
    val animatedProgress by animateFloatAsState(targetValue = targetProgress, label = "voice_progress")
    val displaySec = if (isThisMessage && playbackState.positionSec > 0) {
        playbackState.positionSec
    } else {
        message.audioDurationSec.coerceAtLeast(1)
    }
    val waveformBars = remember(message.id) {
        val seed = message.id.hashCode()
        List(18) { i ->
            val h = ((seed ushr (i % 16)) xor (i * 7)) and 0x0F
            (6 + (h % 13)).dp
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(min = 204.dp, max = 264.dp)
            .padding(vertical = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isOwnMessage) Color.White.copy(alpha = 0.24f) else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = !message.audioBase64.isNullOrBlank(),
                    onClick = onTogglePlay
                )
                .testTag("play_voice_${message.id}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                waveformBars.forEachIndexed { idx, barHeight ->
                    val threshold = (idx + 1f) / waveformBars.size.toFloat()
                    val active = animatedProgress >= threshold
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(CircleShape)
                            .background(
                                when {
                                    active && isOwnMessage -> Color.White
                                    active -> MaterialTheme.colorScheme.primary
                                    isOwnMessage -> Color.White.copy(alpha = 0.34f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice note",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Text(
                    text = formatDurationSec(displaySec),
                    style = MaterialTheme.typography.labelSmall,
                    color = subtleColor
                )
            }
        }
    }
}

@Composable
fun MessageActionsDialog(
    message: Message,
    isOwnMessage: Boolean,
    isPinnedInChat: Boolean = false,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    val haptics = rememberIosHaptics()
    val hasCopyableText = message.text.isNotBlank() && message.type != Message.TYPE_AUDIO
    val isPendingOrFailed = message.deliveryState == Message.STATUS_SENDING ||
        message.deliveryState == Message.STATUS_FAILED
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                if (!isPendingOrFailed) {
                    Text(
                        text = "Tapback",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickReactionEmojis.forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.selectionTick()
                                        onReact(emoji)
                                        onDismiss()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))

                    ActionRow(
                        icon = Icons.AutoMirrored.Rounded.Reply,
                        label = "Reply",
                        onClick = {
                            haptics.lightTap()
                            onReply()
                            onDismiss()
                        }
                    )
                }

                if (message.deliveryState == Message.STATUS_FAILED && onRetry != null) {
                    ActionRow(
                        icon = Icons.Rounded.Refresh,
                        label = "Retry sending",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            haptics.lightTap()
                            onRetry()
                            onDismiss()
                        }
                    )
                }

                if (hasCopyableText && onCopy != null) {
                    ActionRow(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copy text",
                        onClick = {
                            haptics.lightTap()
                            onCopy()
                            onDismiss()
                        }
                    )
                }

                if (!isPendingOrFailed && onForward != null) {
                    ActionRow(
                        icon = Icons.AutoMirrored.Rounded.Forward,
                        label = "Forward",
                        onClick = {
                            haptics.lightTap()
                            onForward()
                            onDismiss()
                        }
                    )
                }

                if (!isPendingOrFailed && onTogglePin != null) {
                    ActionRow(
                        icon = Icons.Rounded.PushPin,
                        label = if (isPinnedInChat) "Unpin from chat" else "Pin in chat",
                        onClick = {
                            haptics.selectionTick()
                            onTogglePin()
                            onDismiss()
                        }
                    )
                }

                if (!isPendingOrFailed && isOwnMessage && message.type == Message.TYPE_TEXT) {
                    ActionRow(
                        icon = Icons.Rounded.Edit,
                        label = "Edit message",
                        onClick = {
                            haptics.lightTap()
                            onEdit()
                            onDismiss()
                        }
                    )
                }

                if (isOwnMessage && message.deliveryState != Message.STATUS_SENDING) {
                    ActionRow(
                        icon = Icons.Rounded.Delete,
                        label = "Delete message",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            haptics.impactMedium()
                            onDelete()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = tint
        )
    }
}
