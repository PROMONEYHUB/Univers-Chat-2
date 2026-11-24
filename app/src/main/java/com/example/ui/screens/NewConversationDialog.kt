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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UserProfile
import com.example.ui.components.IosSearchField
import com.example.ui.components.IosSegmentedControl
import com.example.ui.components.SegmentedTabItem
import com.example.ui.components.UserAvatar
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.PulseBrushes

private val GroupTopicPresets = listOf("Friends", "Study", "Weekend", "Gaming", "Work", "Travel")

@Composable
fun NewConversationDialog(
    people: List<UserProfile>,
    currentUserId: String,
    initialGroupMode: Boolean = false,
    onDismiss: () -> Unit,
    onStartDirect: (UserProfile) -> Unit,
    onCreateGroup: (title: String, topic: String, members: List<UserProfile>) -> Unit
) {
    val haptics = rememberIosHaptics()
    var isGroupMode by remember { mutableStateOf(initialGroupMode) }
    var groupTitle by remember { mutableStateOf("") }
    var groupTopic by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val allOtherPeople = remember(people, currentUserId) {
        people.filter { it.userId != currentUserId }
            .sortedByDescending { it.isOnline }
    }
    val filteredPeople = remember(allOtherPeople, search) {
        if (search.isBlank()) allOtherPeople
        else allOtherPeople.filter {
            it.displayName.contains(search, ignoreCase = true) ||
                it.handle.contains(search, ignoreCase = true) ||
                it.statusVibe.contains(search, ignoreCase = true)
        }
    }
    val selectedPeople = remember(allOtherPeople, selectedIds.size, selectedIds.toList()) {
        allOtherPeople.filter { it.userId in selectedIds }
    }

    val modeTabs = remember {
        listOf(
            SegmentedTabItem(value = false, label = "Direct Chat", testTag = "mode_direct_chip"),
            SegmentedTabItem(value = true, label = "Group Channel", testTag = "mode_group_chip")
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp)
                .imePadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // iOS Sheet Grabber
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        )
                    }

                    // iOS Navigation Sheet Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                haptics.lightTap()
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = if (isGroupMode) "New Group" else "New Message",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (isGroupMode) {
                            Button(
                                onClick = {
                                    haptics.success()
                                    onCreateGroup(groupTitle, groupTopic, selectedPeople)
                                },
                                enabled = groupTitle.trim().isNotEmpty(),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("create_group_confirm_button")
                            ) {
                                Text("Create", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(68.dp))
                        }
                    }

                    // Sliding iOS Mode Switcher
                    IosSegmentedControl(
                        items = modeTabs,
                        selectedValue = isGroupMode,
                        onSelect = { isGroupMode = it },
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )

                    // Group Setup Inset Grouped Card
                    AnimatedVisibility(
                        visible = isGroupMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 0.6.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(PulseBrushes.BrandGradient),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Groups,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (groupTitle.isEmpty()) {
                                                    Text(
                                                        text = "Group Name (required)",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                BasicTextField(
                                                    value = groupTitle,
                                                    onValueChange = { groupTitle = it.take(80) },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("group_title_input")
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(
                                                thickness = 0.6.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (groupTopic.isEmpty()) {
                                                    Text(
                                                        text = "Add channel topic (optional)",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                BasicTextField(
                                                    value = groupTopic,
                                                    onValueChange = { groupTopic = it.take(160) },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(GroupTopicPresets) { preset ->
                                            val chosen = groupTopic == preset
                                            Surface(
                                                shape = CircleShape,
                                                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        haptics.selectionTick()
                                                        groupTopic = if (chosen) "" else preset
                                                    }
                                            ) {
                                                Text(
                                                    text = preset,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (chosen) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Selected Members Token Strip
                            AnimatedVisibility(visible = selectedPeople.isNotEmpty()) {
                                Column {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(selectedPeople, key = { it.userId }) { member ->
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable { selectedIds.remove(member.userId) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 5.dp, end = 9.dp, top = 4.dp, bottom = 4.dp)
                                                ) {
                                                    UserAvatar(
                                                        name = member.displayName,
                                                        photoUrl = member.photoUrl,
                                                        size = 24.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = member.displayName.substringBefore(" "),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Remove ${member.displayName}",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // Spotlight Search Inside Sheet
                    IosSearchField(
                        query = search,
                        onQueryChange = { search = it },
                        placeholder = "Search people by name or @handle",
                        showCancelWhenActive = false,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )

                    Text(
                        text = if (isGroupMode) {
                            if (selectedIds.isEmpty()) "SUGGESTED CONTACTS"
                            else "${selectedIds.size} SELECTED"
                        } else {
                            "CONTACTS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )

                    if (filteredPeople.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        ) {
                            Text(
                                text = if (isGroupMode) {
                                    "No matching contacts yet — you can still tap Create and invite members anytime!"
                                } else {
                                    "No other members discovered yet. Switch to Group Channel to spin up a room."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)
                        ) {
                            item(key = "people_picker_grouped") {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            width = 0.6.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        filteredPeople.forEachIndexed { index, person ->
                                            val isSelected = person.userId in selectedIds
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (isGroupMode) {
                                                            haptics.selectionTick()
                                                            if (isSelected) selectedIds.remove(person.userId)
                                                            else selectedIds.add(person.userId)
                                                        } else {
                                                            haptics.lightTap()
                                                            onStartDirect(person)
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                UserAvatar(
                                                    name = person.displayName,
                                                    photoUrl = person.photoUrl,
                                                    size = 44.dp,
                                                    showPresence = true,
                                                    isOnline = person.isOnline,
                                                    showStoryRing = person.isOnline
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = person.displayName,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "@${person.handle}${if (person.statusVibe.isNotBlank()) " · ${person.statusVibe}" else ""}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (isGroupMode) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                        contentDescription = if (isSelected) "Selected" else "Not selected",
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                            if (index < filteredPeople.lastIndex) {
                                                HorizontalDivider(
                                                    thickness = 0.6.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                    modifier = Modifier.padding(start = 70.dp, end = 14.dp)
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
        }
    }
}
