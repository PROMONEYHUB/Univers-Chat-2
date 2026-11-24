package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.GlassIconBadge
import com.example.ui.components.GlassmorphismCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.DynamicIslandDarkSurface
import com.example.ui.theme.OnlinePresenceGreen
import com.example.ui.theme.PulseBrushes

@Composable
fun ChatsHeaderIsland(
    currentUserProfile: UserProfile?,
    onlinePeople: List<UserProfile>,
    unreadConvCount: Int,
    viewerShowsOnline: Boolean = true,
    isNetworkOnline: Boolean = true,
    pendingOutboxCount: Int = 0,
    onNewGroupClick: () -> Unit,
    onCallHistoryClick: () -> Unit = {},
    onPersonClick: (UserProfile) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    var islandExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        // Editorial Brand & Messages Row + Frosted Action Capsule
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconBadge(
                        icon = Icons.Rounded.Forum,
                        contentDescription = null,
                        size = 22.dp,
                        iconSize = 13.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (unreadConvCount > 0) {
                        Icon(
                            imageVector = Icons.Rounded.MarkChatUnread,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    val statusLine = buildString {
                        if (!isNetworkOnline) {
                            append("Offline")
                            if (pendingOutboxCount > 0) {
                                append(" · $pendingOutboxCount queued")
                            } else {
                                append(" · Showing cached chats")
                            }
                        } else {
                            if (unreadConvCount > 0) {
                                append("$unreadConvCount unread")
                            } else {
                                append("All caught up")
                            }
                            if (onlinePeople.isNotEmpty()) {
                                append(" · ${onlinePeople.size} active now")
                            } else if (!currentUserProfile?.statusVibe.isNullOrBlank()) {
                                append(" · ${currentUserProfile?.statusVibe}")
                            }
                        }
                    }
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (!isNetworkOnline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            GlassmorphismCard(
                shape = CircleShape,
                highlighted = false
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptics.lightTap()
                            onCallHistoryClick()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("header_call_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Recent calls",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.lightTap()
                            onNewGroupClick()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("header_new_group_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = "Create group channel",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    currentUserProfile?.let { me ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.lightTap()
                                    onProfileClick()
                                }
                                .semantics { contentDescription = "Open profile and settings" }
                                .testTag("header_profile_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            val selfVisibleOnline = viewerShowsOnline && me.onlineStatusVisible && me.isOnline && isNetworkOnline
                            UserAvatar(
                                name = me.displayName,
                                photoUrl = me.photoUrl,
                                size = 38.dp,
                                showPresence = true,
                                isOnline = selfVisibleOnline,
                                showStoryRing = selfVisibleOnline
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Dynamic Island Live Capsule with Crisp Smoked Surface & Specular Rim
        val islandRim = remember {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.20f)
                )
            )
        }
        GlassmorphismCard(
            shape = RoundedCornerShape(20.dp),
            blurRadius = 0.dp,
            borderWidth = 0.85.dp,
            borderBrush = islandRim,
            containerColor = DynamicIslandDarkSurface.copy(alpha = 0.90f),
            onClick = {
                haptics.selectionTick()
                islandExpanded = !islandExpanded
            },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .testTag("dynamic_island_live_pill")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (onlinePeople.isNotEmpty()) {
                                        OnlinePresenceGreen.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = null,
                                tint = if (onlinePeople.isNotEmpty()) OnlinePresenceGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (onlinePeople.isNotEmpty()) {
                            Box(
                                modifier = Modifier.width(
                                    (26 + (onlinePeople.take(3).size - 1) * 16).dp
                                )
                            ) {
                                onlinePeople.take(3).forEachIndexed { idx, person ->
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (idx * 16).dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(DynamicIslandDarkSurface)
                                            .padding(1.5.dp)
                                    ) {
                                        UserAvatar(
                                            name = person.displayName,
                                            photoUrl = person.photoUrl,
                                            size = 23.dp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    !isNetworkOnline -> if (pendingOutboxCount > 0) {
                                        "Offline · $pendingOutboxCount queued"
                                    } else {
                                        "Waiting for network…"
                                    }
                                    onlinePeople.isNotEmpty() -> "${onlinePeople.size} online in Univers"
                                    else -> "Live Network Ready"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !isNetworkOnline -> "Messages will send automatically when connected"
                                    onlinePeople.isNotEmpty() -> "Tap to jump into a live chat"
                                    else -> currentUserProfile?.statusVibe?.ifBlank { "Real-time delivery active" }
                                        ?: "Real-time delivery active"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = if (islandExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (islandExpanded) "Collapse live bar" else "Expand live bar",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = islandExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "island_new_group") {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(PulseBrushes.BrandGradient)
                                        .clickable {
                                            haptics.lightTap()
                                            onNewGroupClick()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "New Channel",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            items(onlinePeople, key = { it.userId }) { person ->
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(0.6.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                        .clickable {
                                            haptics.lightTap()
                                            islandExpanded = false
                                            onPersonClick(person)
                                        }
                                        .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        name = person.displayName,
                                        photoUrl = person.photoUrl,
                                        size = 26.dp,
                                        showPresence = true,
                                        isOnline = true
                                    )
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(
                                        text = person.displayName.substringBefore(" "),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1
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
