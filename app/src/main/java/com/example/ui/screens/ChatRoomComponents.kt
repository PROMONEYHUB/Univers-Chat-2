package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.formatDurationSec
import com.example.ui.components.IosSearchField
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosIndigo
import com.example.ui.theme.IosRed

private val QuickRepliesList = listOf(
    "👋 Hey there!",
    "Sounds good 👍",
    "On my way!",
    "🔥 Love this",
    "Let's vote!"
)

@Composable
fun ChatRoomSearchBar(
    query: String,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            IosSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search in conversation…",
                inputTestTag = "chat_room_search_input",
                matchCount = matchCount,
                showCancelWhenActive = true,
                onCancelClick = onClose,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun PinnedMessageBanner(
    senderName: String,
    preview: String,
    onJumpToPinned: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptics.lightTap()
                onJumpToPinned()
            }
            .testTag("pinned_message_banner")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (senderName.isNotBlank()) "Pinned · $senderName" else "Pinned message",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = preview.ifBlank { "Attachment" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        haptics.lightTap()
                        onUnpin()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("unpin_message_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Unpin message",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun IosQuickInsertTray(
    visible: Boolean,
    onPickPhoto: () -> Unit,
    onCreatePoll: () -> Unit,
    onRecordVoice: () -> Unit = {},
    onInsertQuickReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "tray_photo") {
                        Surface(
                            shape = CircleShape,
                            color = IosBlue.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.lightTap()
                                    onPickPhoto()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = IosBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Photos",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IosBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    item(key = "tray_poll") {
                        Surface(
                            shape = CircleShape,
                            color = IosIndigo.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.lightTap()
                                    onCreatePoll()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Poll,
                                    contentDescription = null,
                                    tint = IosIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Poll",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IosIndigo,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    item(key = "tray_voice") {
                        Surface(
                            shape = CircleShape,
                            color = IosRed.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.lightTap()
                                    onRecordVoice()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = IosRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Voice",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IosRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    items(QuickRepliesList) { snippet ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.selectionTick()
                                    onInsertQuickReply(snippet)
                                }
                        ) {
                            Text(
                                text = snippet,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IosMessageComposerBar(
    draftText: String,
    onDraftChange: (String) -> Unit,
    canInteract: Boolean,
    isSending: Boolean,
    isTrayOpen: Boolean,
    onToggleTray: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenPoll: () -> Unit,
    onSend: () -> Unit,
    isRecordingAudio: Boolean = false,
    recordingSeconds: Int = 0,
    onStartVoiceRecord: () -> Unit = {},
    onCancelVoiceRecord: () -> Unit = {},
    onSendVoiceRecord: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    val hasDraftText = draftText.trim().isNotEmpty()
    val canSendText = canInteract && hasDraftText && !isSending
    val sendScale by animateFloatAsState(
        targetValue = if (canSendText || isRecordingAudio) 1f else 0.92f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ios_send_scale"
    )
    val plusRotation by animateFloatAsState(
        targetValue = if (isTrayOpen) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ios_plus_rotate"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
            if (isRecordingAudio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptics.lightTap()
                            onCancelVoiceRecord()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cancel_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel voice recording",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(IosRed.copy(alpha = 0.12f))
                            .border(0.8.dp, IosRed.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FiberManualRecord,
                            contentDescription = null,
                            tint = IosRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording voice… ${formatDurationSec(recordingSeconds)} / 0:30",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = IosRed
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledIconButton(
                        onClick = {
                            haptics.success()
                            onSendVoiceRecord()
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Send voice message"
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // iOS '+' Expandable Options Button
                    IconButton(
                        onClick = {
                            haptics.lightTap()
                            onToggleTray()
                        },
                        enabled = canInteract && !isSending,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_quick_tray_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTrayOpen) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = if (isTrayOpen) "Close quick options" else "Open quick options",
                                tint = if (isTrayOpen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = plusRotation }
                            )
                        }
                    }

                    // Direct 1-tap Photo & Poll pills (compact & accessible)
                    IconButton(
                        onClick = {
                            haptics.lightTap()
                            onPickPhoto()
                        },
                        enabled = canInteract && !isSending,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("attach_photo_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Share photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptics.lightTap()
                            onOpenPoll()
                        },
                        enabled = canInteract && !isSending,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("create_poll_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Poll,
                                contentDescription = "Create poll",
                                tint = IosIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Sleek Frosted Capsule Input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp)
                            .heightIn(min = 42.dp, max = 120.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
                            .border(
                                width = if (hasDraftText) 1.1.dp else 0.8.dp,
                                color = if (hasDraftText) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (draftText.isEmpty()) {
                            Text(
                                text = "Message",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = draftText,
                            onValueChange = { onDraftChange(it.take(2000)) },
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("message_composer_input")
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (hasDraftText || isSending) {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = canSendText,
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
                                .testTag("send_message_button")
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Send message"
                                )
                            }
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                haptics.impactMedium()
                                onStartVoiceRecord()
                            },
                            enabled = canInteract,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("record_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Record voice message"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JumpToLatestButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Surface(
            onClick = {
                haptics.lightTap()
                onClick()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(44.dp)
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .testTag("jump_to_latest_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Jump to latest messages",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ChatRoomEmptyState(
    isSearchFiltered: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
                    imageVector = if (isSearchFiltered) Icons.Rounded.Search else Icons.Rounded.WavingHand,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isSearchFiltered) "No matching messages" else "Start the conversation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSearchFiltered) {
                "Nothing matched \"$searchQuery\" in this chat."
            } else {
                "Send a crisp iMessage note, tap + for quick replies, share a photo, or drop a live poll."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DayDividerPill(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            modifier = Modifier.border(
                width = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                shape = CircleShape
            )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun TypingIndicatorPill(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
        modifier = modifier
            .border(
                width = 0.7.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = CircleShape
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(com.example.ui.theme.OnlinePresenceGreen)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChatRoomSkeletonList(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) { idx ->
            val isOwn = idx % 2 == 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (idx % 3 == 0) 0.62f else 0.48f)
                        .height(if (idx % 3 == 2) 56.dp else 40.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isOwn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                )
            }
        }
    }
}

