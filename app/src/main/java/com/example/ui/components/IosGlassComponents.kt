package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosPink
import com.example.ui.theme.IosTeal

/**
 * Multi-point atmospheric aurora backdrop rendered natively on Canvas with smooth multi-stop
 * radial gradients (zero expensive full-screen RenderEffect blur passes).
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val primaryAlpha = if (isDark) 0.32f else 0.16f
    val secondaryAlpha = if (isDark) 0.24f else 0.13f
    val accentAlpha = if (isDark) 0.18f else 0.10f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    0f to IosBlue.copy(alpha = primaryAlpha),
                    0.45f to IosBlue.copy(alpha = primaryAlpha * 0.42f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.88f, h * 0.08f),
                    radius = w * 0.88f
                ),
                radius = w * 0.88f,
                center = Offset(w * 0.88f, h * 0.08f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to AuroraViolet.copy(alpha = secondaryAlpha),
                    0.48f to AuroraViolet.copy(alpha = secondaryAlpha * 0.40f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.08f, h * 0.18f),
                    radius = w * 0.78f
                ),
                radius = w * 0.78f,
                center = Offset(w * 0.08f, h * 0.18f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to AuroraCyan.copy(alpha = accentAlpha),
                    0.5f to AuroraCyan.copy(alpha = accentAlpha * 0.35f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.48f, h * 0.02f),
                    radius = w * 0.72f
                ),
                radius = w * 0.72f,
                center = Offset(w * 0.48f, h * 0.02f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to IosTeal.copy(alpha = accentAlpha * 0.85f),
                    0.5f to IosTeal.copy(alpha = accentAlpha * 0.30f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.92f, h * 0.56f),
                    radius = w * 0.74f
                ),
                radius = w * 0.74f,
                center = Offset(w * 0.92f, h * 0.56f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to IosPink.copy(alpha = accentAlpha * 0.65f),
                    0.5f to IosPink.copy(alpha = accentAlpha * 0.25f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.12f, h * 0.82f),
                    radius = w * 0.68f
                ),
                radius = w * 0.68f,
                center = Offset(w * 0.12f, h * 0.82f)
            )
        }
    }
}

@Composable
fun rememberGlassRimBrush(
    highlighted: Boolean = false
): Brush = GlassmorphismDefaults.rememberBorderBrush(highlighted = highlighted)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    highlighted: Boolean = false,
    tintAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    GlassmorphismCard(
        modifier = modifier,
        shape = shape,
        highlighted = highlighted,
        tintAlpha = tintAlpha,
        content = content
    )
}

@Composable
fun GlassIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.72f),
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .border(
                width = 0.75.dp,
                color = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.88f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
