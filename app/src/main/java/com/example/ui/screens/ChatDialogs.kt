package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.components.rememberIosHaptics

@Composable
fun PhotoLightboxDialog(
    bitmap: Bitmap,
    senderName: String,
    caption: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = caption.ifBlank { "Shared photo preview" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 72.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close photo preview",
                            tint = Color.White
                        )
                    }
                }
            }

            if (caption.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (question: String, options: List<String>) -> Unit
) {
    val haptics = rememberIosHaptics()
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
    val canSubmit = question.trim().isNotEmpty() && validOptions.size >= 2

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Poll,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Create Live Poll",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close poll dialog")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(200) },
                    label = { Text("Ask a question") },
                    placeholder = { Text("Where should we meet?") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("poll_question_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                options.forEachIndexed { idx, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { options[idx] = it.take(80) },
                        label = { Text("Option ${idx + 1}") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                if (options.size < 6) {
                    TextButton(onClick = {
                        haptics.lightTap()
                        options.add("")
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Option")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        haptics.lightTap()
                        onDismiss()
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            haptics.success()
                            onCreatePoll(question.trim(), validOptions)
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("send_poll_button")
                    ) {
                        Text("Post Poll")
                    }
                }
            }
        }
    }
}

@Composable
fun GroupInfoDialog(
    conversation: Conversation,
    allPeople: List<UserProfile>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onUpdateGroupInfo: (title: String, topic: String) -> Unit,
    onAddMembers: (List<UserProfile>) -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteChannel: (() -> Unit)? = null,
    photoMessages: List<Message> = emptyList(),
    onOpenPhoto: (Bitmap, String, String) -> Unit = { _, _, _ -> },
    onTogglePin: () -> Unit = {},
    onToggleMute: () -> Unit = {}
) {
    val haptics = rememberIosHaptics()
    var title by remember(conversation.id) { mutableStateOf(conversation.title) }
    var topic by remember(conversation.id) { mutableStateOf(conversation.topic) }
    var showAddMembers by remember { mutableStateOf(false) }
    var confirmDeleteChannel by remember { mutableStateOf(false) }
    val selectedToAdd = remember { mutableStateListOf<String>() }

    val nonMembers = remember(allPeople, conversation.participantIds) {
        allPeople.filter { it.userId !in conversation.participantIds }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
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
                                    imageVector = Icons.Rounded.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (showAddMembers) "Add Members" else "Channel Info",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close info")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!showAddMembers) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(80) },
                        label = { Text("Channel Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it.take(160) },
                        label = { Text("Topic") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            haptics.success()
                            onUpdateGroupInfo(title, topic)
                        },
                        enabled = title.trim().isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Channel Details")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val isPinned = conversation.isPinnedBy(currentUserId)
                    val isMuted = conversation.isMutedBy(currentUserId)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.selectionTick()
                                onTogglePin()
                            },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPinned) "Unpin" else "Pin", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.selectionTick()
                                onToggleMute()
                            },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMuted) "Unmute" else "Mute", maxLines = 1)
                        }
                    }

                    if (photoMessages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SharedPhotosStrip(
                            photoMessages = photoMessages,
                            onOpenPhoto = onOpenPhoto
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Members (${conversation.participantIds.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (nonMembers.isNotEmpty()) {
                            TextButton(onClick = { showAddMembers = true }) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Invite")
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 170.dp)) {
                        items(conversation.participantIds, key = { it }) { uid ->
                            val name = conversation.participantNames[uid] ?: "Member"
                            val photo = conversation.participantPhotos[uid]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = name, photoUrl = photo, size = 34.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (uid == currentUserId) "$name (You)" else name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    if (conversation.participantIds.size > 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                haptics.impactMedium()
                                onLeaveGroup()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Leave Channel")
                        }
                    }

                    if (conversation.createdBy == currentUserId && onDeleteChannel != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (!confirmDeleteChannel) {
                            OutlinedButton(
                                onClick = {
                                    haptics.lightTap()
                                    confirmDeleteChannel = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("delete_channel_button")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Channel")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        haptics.lightTap()
                                        confirmDeleteChannel = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Keep")
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptics.impactMedium()
                                        onDeleteChannel()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("confirm_delete_channel_button")
                                ) {
                                    Text("Delete", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(nonMembers, key = { it.userId }) { person ->
                            val selected = person.userId in selectedToAdd
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptics.selectionTick()
                                        if (selected) selectedToAdd.remove(person.userId)
                                        else selectedToAdd.add(person.userId)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = person.displayName, photoUrl = person.photoUrl, size = 38.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text("@${person.handle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            haptics.lightTap()
                            showAddMembers = false
                        }) {
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                haptics.success()
                                val chosen = nonMembers.filter { it.userId in selectedToAdd }
                                onAddMembers(chosen)
                                selectedToAdd.clear()
                                showAddMembers = false
                            },
                            enabled = selectedToAdd.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Add Selected")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupCallMemberPickerDialog(
    conversation: Conversation,
    currentUserId: String,
    isVideoCall: Boolean,
    onDismiss: () -> Unit,
    onSelectMember: (memberId: String, memberName: String, memberPhoto: String?) -> Unit
) {
    val haptics = rememberIosHaptics()
    val callableMemberIds = remember(conversation.participantIds, currentUserId) {
        conversation.participantIds.filter { it != currentUserId }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
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
                                    imageVector = if (isVideoCall) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isVideoCall) "Start HD Video Call" else "Start HD Voice Call",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close call picker")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a channel member to ring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (callableMemberIds.isEmpty()) {
                    Text(
                        text = "Invite another member to this channel first to place a call.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(callableMemberIds, key = { it }) { memberId ->
                            val memberName = conversation.participantNames[memberId] ?: "Member"
                            val memberPhoto = conversation.participantPhotos[memberId]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        haptics.lightTap()
                                        onSelectMember(memberId, memberName, memberPhoto)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = memberName, photoUrl = memberPhoto, size = 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = memberName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isVideoCall) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                    contentDescription = "Call $memberName",
                                    tint = MaterialTheme.colorScheme.primary
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
