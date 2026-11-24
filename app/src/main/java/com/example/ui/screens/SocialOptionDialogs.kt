package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CallSession
import com.example.data.model.CallTarget
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.UserProfile
import com.example.ui.components.ImageUtils
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosGreen
import com.example.ui.theme.IosOrange
import com.example.ui.theme.IosRed

@Composable
fun ConversationOptionsDialog(
    conversation: Conversation,
    currentUserId: String,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleMarkUnread: () -> Unit,
    onDeleteOrLeave: () -> Unit
) {
    val haptics = rememberIosHaptics()
    val isPinned = conversation.isPinnedBy(currentUserId)
    val isMuted = conversation.isMutedBy(currentUserId)
    val hasUnread = conversation.unreadCountFor(currentUserId) > 0L
    val isLeaveGroup = conversation.type == Conversation.TYPE_GROUP && conversation.createdBy != currentUserId

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conversation.displayTitleFor(currentUserId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Conversation options",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close options")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                OptionSheetRow(
                    icon = Icons.Rounded.PushPin,
                    badgeColor = IosBlue,
                    label = if (isPinned) "Unpin conversation" else "Pin to top",
                    testTag = "conv_option_pin",
                    onClick = {
                        haptics.selectionTick()
                        onTogglePin()
                        onDismiss()
                    }
                )
                OptionSheetRow(
                    icon = if (isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    badgeColor = IosOrange,
                    label = if (isMuted) "Unmute notifications" else "Mute notifications",
                    testTag = "conv_option_mute",
                    onClick = {
                        haptics.selectionTick()
                        onToggleMute()
                        onDismiss()
                    }
                )
                OptionSheetRow(
                    icon = if (hasUnread) Icons.Rounded.MarkChatRead else Icons.Rounded.MarkChatUnread,
                    badgeColor = IosGreen,
                    label = if (hasUnread) "Mark as read" else "Mark as unread",
                    testTag = "conv_option_unread",
                    onClick = {
                        haptics.selectionTick()
                        onToggleMarkUnread()
                        onDismiss()
                    }
                )
                OptionSheetRow(
                    icon = if (isLeaveGroup) Icons.AutoMirrored.Rounded.ExitToApp else Icons.Rounded.DeleteOutline,
                    badgeColor = IosRed,
                    label = if (isLeaveGroup) "Leave channel" else "Delete conversation",
                    labelColor = MaterialTheme.colorScheme.error,
                    testTag = "conv_option_delete",
                    onClick = {
                        haptics.impactMedium()
                        onDeleteOrLeave()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun ForwardMessageDialog(
    conversations: List<Conversation>,
    currentUserId: String,
    peopleMap: Map<String, UserProfile>,
    onDismiss: () -> Unit,
    onSelectConversation: (Conversation) -> Unit
) {
    val haptics = rememberIosHaptics()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Forward to…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close forward picker")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (conversations.isEmpty()) {
                    Text(
                        text = "Start another conversation first to forward messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(conversations, key = { it.id }) { conv ->
                            val title = conv.displayTitleFor(currentUserId)
                            val isGroup = conv.type == Conversation.TYPE_GROUP
                            val otherId = conv.otherParticipantId(currentUserId)
                            val photo = if (isGroup) null else (otherId?.let { peopleMap[it]?.photoUrl ?: conv.participantPhotos[it] })
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        haptics.lightTap()
                                        onSelectConversation(conv)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .testTag("forward_target_${conv.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    name = title,
                                    photoUrl = photo,
                                    size = 40.dp,
                                    isGroup = isGroup
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isGroup) {
                                        Text(
                                            text = "${conv.participantIds.size} members",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Forward,
                                    contentDescription = "Forward to $title",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryDialog(
    calls: List<CallSession>,
    currentUserId: String,
    blockedUserIds: Set<String>,
    onDismiss: () -> Unit,
    onCallBack: (target: CallTarget, isVideo: Boolean) -> Unit
) {
    val haptics = rememberIosHaptics()
    val visibleCalls = remember(calls, currentUserId, blockedUserIds) {
        calls.filter { session ->
            val peerId = if (session.callerId == currentUserId) session.calleeId else session.callerId
            peerId !in blockedUserIds
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Recent Calls",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close call history")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (visibleCalls.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recent calls yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Start an HD Voice or HD Video call from any chat or contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(visibleCalls, key = { it.id }) { session ->
                            val isOutgoing = session.callerId == currentUserId
                            val peerId = if (isOutgoing) session.calleeId else session.callerId
                            val peerName = (if (isOutgoing) session.calleeName else session.callerName).ifBlank { "Member" }
                            val peerPhoto = if (isOutgoing) session.calleePhotoUrl else session.callerPhotoUrl
                            val isMissed = session.status == CallSession.STATUS_MISSED ||
                                session.status == CallSession.STATUS_DECLINED

                            val dirIcon = when {
                                isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                                isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                                else -> Icons.AutoMirrored.Rounded.CallReceived
                            }
                            val dirColor = when {
                                isMissed -> IosRed
                                isOutgoing -> IosBlue
                                else -> IosGreen
                            }
                            val statusText = when {
                                isMissed && !isOutgoing -> "Missed"
                                isMissed && isOutgoing -> "Unanswered"
                                isOutgoing -> "Outgoing"
                                else -> "Incoming"
                            }
                            val timeLabel = formatRelativeTime(session.createdAt)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = peerName, photoUrl = peerPhoto, size = 42.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = peerName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = dirIcon,
                                            contentDescription = null,
                                            tint = dirColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$statusText ${if (session.isVideoCall) "Video" else "Voice"} · $timeLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMissed) IosRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                val target = CallTarget(
                                    userId = peerId,
                                    displayName = peerName,
                                    photoUrl = peerPhoto,
                                    conversationId = session.conversationId
                                )
                                IconButton(
                                    onClick = {
                                        haptics.lightTap()
                                        onDismiss()
                                        onCallBack(target, false)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = "Voice call $peerName",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptics.lightTap()
                                        onDismiss()
                                        onCallBack(target, true)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = "Video call $peerName",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedUsersDialog(
    blockedUserIds: Set<String>,
    allPeople: List<UserProfile>,
    onDismiss: () -> Unit,
    onUnblockUser: (String) -> Unit
) {
    val haptics = rememberIosHaptics()
    val peopleMap = remember(allPeople) { allPeople.associateBy { it.userId } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Blocked Contacts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close blocked contacts")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (blockedUserIds.isEmpty()) {
                    Text(
                        text = "You haven't blocked anyone. You can block a contact anytime from their chat info.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(blockedUserIds.toList(), key = { it }) { uid ->
                            val person = peopleMap[uid]
                            val name = person?.displayName ?: "Member (${uid.take(6)})"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    name = name,
                                    photoUrl = person?.photoUrl,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (person != null) {
                                        Text(
                                            text = "@${person.handle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        haptics.selectionTick()
                                        onUnblockUser(uid)
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("unblock_user_$uid")
                                ) {
                                    Text("Unblock", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedPhotosStrip(
    photoMessages: List<Message>,
    onOpenPhoto: (Bitmap, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photoMessages.isEmpty()) return
    val haptics = rememberIosHaptics()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Shared Photos (${photoMessages.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(photoMessages.takeLast(12).reversed(), key = { it.id }) { msg ->
                val bmp = remember(msg.imageBase64) {
                    msg.imageBase64?.let { ImageUtils.decodeBase64ToBitmap(it) }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = msg.text.ifBlank { "Shared photo from ${msg.senderName}" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptics.lightTap()
                                onOpenPhoto(bmp, msg.senderName, msg.text)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionSheetRow(
    icon: ImageVector,
    badgeColor: Color,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(badgeColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = labelColor
        )
    }
}
