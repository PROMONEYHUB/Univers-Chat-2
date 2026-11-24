package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.GlassmorphismCard
import com.example.ui.components.IosSearchField
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.IosBlue
import com.example.ui.theme.OnlinePresenceGreen
import com.example.ui.theme.PulseBrushes
import kotlinx.coroutines.delay

@Composable
fun PeopleScreen(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    peopleState: UiState<List<UserProfile>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onMessagePerson: (UserProfile) -> Unit,
    onCallPerson: (person: UserProfile, isVideoCall: Boolean) -> Unit = { _, _ -> },
    blockedUserIds: Set<String> = emptySet(),
    viewerShowsOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val allPeople = (peopleState as? UiState.Success)?.data.orEmpty()
    val memberCount = remember(allPeople, currentUserId, blockedUserIds) {
        allPeople.count { it.userId != currentUserId && it.userId !in blockedUserIds }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LiquidGlassBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Contacts",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (memberCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = "$memberCount member${if (memberCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Discover Univers Chat members and start an instant conversation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                IosSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = "Search name, @handle, or vibe…",
                    inputTestTag = "people_search_input"
                )
            }

            when (peopleState) {
                is UiState.Loading -> {
                    PeopleSkeletonList()
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = peopleState.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is UiState.Success -> {
                    val others = remember(peopleState.data, searchQuery, currentUserId, blockedUserIds, nowMillis, viewerShowsOnline) {
                        peopleState.data
                            .filter { it.userId != currentUserId && it.userId !in blockedUserIds }
                            .filter {
                                searchQuery.isBlank() ||
                                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                                    it.handle.contains(searchQuery, ignoreCase = true) ||
                                    it.statusVibe.contains(searchQuery, ignoreCase = true)
                            }
                            .sortedByDescending { it.isEffectivelyOnline(nowMillis, viewerShowsOnline) }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (currentUserProfile != null && searchQuery.isBlank()) {
                            item(key = "self_notes_card", contentType = "self_notes") {
                                SavedNotesBannerCard(
                                    profile = currentUserProfile,
                                    onClick = { onMessagePerson(currentUserProfile) },
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                        }

                        if (others.isEmpty()) {
                            item(key = "empty_people", contentType = "empty_people") {
                                GlassmorphismCard(
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(26.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(60.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PersonSearch,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (searchQuery.isNotBlank()) "No people match \"$searchQuery\"" else "No other members online yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "When other people verify their phone number on Univers Chat, their discoverable profiles appear here automatically.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = others,
                                key = { _, person -> person.userId },
                                contentType = { _, _ -> "contact_row" }
                            ) { index, person ->
                                val isFirst = index == 0
                                val isLast = index == others.lastIndex
                                val rowShape = RoundedCornerShape(
                                    topStart = if (isFirst) 22.dp else 0.dp,
                                    topEnd = if (isFirst) 22.dp else 0.dp,
                                    bottomStart = if (isLast) 22.dp else 0.dp,
                                    bottomEnd = if (isLast) 22.dp else 0.dp
                                )
                                Surface(
                                    shape = rowShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(rowShape)
                                ) {
                                    GroupedPersonRow(
                                        profile = person,
                                        nowMillis = nowMillis,
                                        viewerShowsOnline = viewerShowsOnline,
                                        showBottomDivider = !isLast,
                                        onMessageClick = { onMessagePerson(person) },
                                        onVoiceCallClick = { onCallPerson(person, false) },
                                        onVideoCallClick = { onCallPerson(person, true) }
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

@Composable
private fun SavedNotesBannerCard(
    profile: UserProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    val isDark = isSystemInDarkTheme()
    val bgBrush = if (isDark) PulseBrushes.SavedNotesGradientDark else PulseBrushes.SavedNotesGradientLight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bgBrush)
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(IosBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = "Saved Notes",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Personal Space",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Saved Notes (${profile.displayName.substringBefore(" ")})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Message yourself for quick notes, photos & links",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            FilledTonalButton(
                onClick = {
                    haptics.lightTap()
                    onClick()
                },
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("message_person_${profile.userId}")
            ) {
                Text("Open", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun GroupedPersonRow(
    profile: UserProfile,
    nowMillis: Long,
    viewerShowsOnline: Boolean,
    showBottomDivider: Boolean,
    onMessageClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    onVideoCallClick: () -> Unit
) {
    val haptics = rememberIosHaptics()
    val isOnline = profile.isEffectivelyOnline(nowMillis, viewerShowsOnline)
    val visibleLastSeen = profile.visibleLastSeen(viewerShowsOnline)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = profile.displayName,
                photoUrl = profile.photoUrl,
                size = 52.dp,
                showPresence = true,
                isOnline = isOnline,
                showStoryRing = isOnline
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "@${profile.handle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = profile.bio.ifBlank { "On Univers Chat" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (profile.statusVibe.isNotBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = profile.statusVibe,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                    if (isOnline) {
                        Text(
                            text = "Online now",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = OnlinePresenceGreen
                        )
                    } else if (visibleLastSeen != null) {
                        Text(
                            text = "Seen ${formatRelativeTime(visibleLastSeen)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.lightTap()
                        onVoiceCallClick()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_call_person_${profile.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "HD voice call ${profile.displayName}",
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        haptics.lightTap()
                        onVideoCallClick()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("video_call_person_${profile.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "HD video call ${profile.displayName}",
                        modifier = Modifier.size(19.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        haptics.lightTap()
                        onMessageClick()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("message_person_${profile.userId}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Chat with ${profile.displayName}",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showBottomDivider) {
            HorizontalDivider(
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 80.dp, end = 14.dp)
            )
        }
    }
}

@Composable
private fun PeopleSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(6) { index ->
            GlassmorphismCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    )
                    Spacer(modifier = Modifier.width(13.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.45f else 0.58f)
                                .height(15.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}
