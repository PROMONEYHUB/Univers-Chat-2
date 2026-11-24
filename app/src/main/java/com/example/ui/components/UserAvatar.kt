package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosBlueBright
import com.example.ui.theme.IosGreen
import com.example.ui.theme.IosIndigo
import com.example.ui.theme.IosOrange
import com.example.ui.theme.IosPink
import com.example.ui.theme.IosRed
import com.example.ui.theme.IosTeal
import com.example.ui.theme.OnlinePresenceGreen
import com.example.ui.theme.PulseBrushes
import kotlin.math.absoluteValue

private val IosAvatarGradients = listOf(
    listOf(IosBlueBright, IosBlue),
    listOf(IosOrange, IosPink),
    listOf(IosGreen, IosTeal),
    listOf(IosIndigo, IosBlue),
    listOf(IosPink, IosRed),
    listOf(IosTeal, IosBlueBright)
)

private val WhitespaceRegex = Regex("\\s+")

@Composable
fun UserAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isGroup: Boolean = false,
    showPresence: Boolean = false,
    isOnline: Boolean = false,
    showStoryRing: Boolean = false
) {
    val avatarBrush = remember(name) {
        val paletteIndex = (name.hashCode().absoluteValue) % IosAvatarGradients.size
        Brush.verticalGradient(IosAvatarGradients[paletteIndex])
    }
    val initials = remember(name) {
        name.trim()
            .split(WhitespaceRegex)
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "U" }
    }

    val ringPadding = if (showStoryRing) 2.5.dp else 0.dp
    val ringModifier = if (showStoryRing) {
        Modifier
            .border(width = 2.dp, brush = PulseBrushes.StoryRingActive, shape = CircleShape)
            .padding(ringPadding)
            .border(width = 1.5.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape)
    } else {
        Modifier
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(ringModifier)
                .clip(CircleShape)
                .background(avatarBrush),
            contentAlignment = Alignment.Center
        ) {
            if (isGroup) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = "$name group avatar",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.50f)
                )
            } else if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "$name profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = (size.value * 0.34f).coerceIn(11f, 28f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (showPresence) {
            val dotSize = (size * 0.27f).coerceAtLeast(10.dp).coerceAtMost(17.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (isOnline) OnlinePresenceGreen else MaterialTheme.colorScheme.outline)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}
