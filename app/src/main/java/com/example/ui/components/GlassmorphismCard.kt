package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.IosDarkCardSurface

/**
 * Default visual tokens and brushes for [GlassmorphismCard].
 * Note: [DefaultBlurRadius] defaults to 0.dp to avoid expensive offscreen RenderEffect
 * passes on every scrolling list item, while still preserving full frosted rim & bloom depth.
 */
object GlassmorphismDefaults {
    val DefaultShape: Shape = RoundedCornerShape(22.dp)
    val DefaultBlurRadius: Dp = 0.dp
    val DefaultBorderWidth: Dp = 0.85.dp
    val HighlightedBorderWidth: Dp = 1.15.dp

    @Composable
    fun rememberBorderBrush(
        highlighted: Boolean = false,
        accentColor: Color = MaterialTheme.colorScheme.primary
    ): Brush {
        val isDark = isSystemInDarkTheme()
        return remember(isDark, highlighted, accentColor) {
            val topLeft = when {
                highlighted && isDark -> Color.White.copy(alpha = 0.34f)
                highlighted -> Color.White.copy(alpha = 0.96f)
                isDark -> Color.White.copy(alpha = 0.16f)
                else -> Color.White.copy(alpha = 0.92f)
            }
            val mid = when {
                highlighted -> accentColor.copy(alpha = if (isDark) 0.48f else 0.34f)
                isDark -> Color.White.copy(alpha = 0.06f)
                else -> Color(0xFFD6E2F5).copy(alpha = 0.55f)
            }
            val bottomRight = when {
                highlighted -> Color.White.copy(alpha = if (isDark) 0.20f else 0.72f)
                isDark -> Color.White.copy(alpha = 0.11f)
                else -> Color.White.copy(alpha = 0.70f)
            }
            Brush.linearGradient(colors = listOf(topLeft, mid, bottomRight))
        }
    }

    @Composable
    fun rememberBackdropBrush(
        highlighted: Boolean = false,
        containerColor: Color? = null,
        tintAlpha: Float? = null
    ): Brush {
        val isDark = isSystemInDarkTheme()
        return remember(isDark, highlighted, containerColor, tintAlpha) {
            if (containerColor != null) {
                val topColor = if (tintAlpha != null) {
                    containerColor.copy(alpha = tintAlpha.coerceIn(0f, 1f))
                } else {
                    containerColor
                }
                val bottomColor = topColor.copy(
                    alpha = (topColor.alpha * 0.90f).coerceAtLeast(0.06f)
                )
                Brush.verticalGradient(colors = listOf(topColor, bottomColor))
            } else if (isDark) {
                val baseTopAlpha = (tintAlpha ?: if (highlighted) 0.88f else 0.76f).coerceIn(0f, 1f)
                val baseBottomAlpha = (baseTopAlpha * 0.92f).coerceAtLeast(0.10f)
                Brush.verticalGradient(
                    colors = listOf(
                        IosDarkCardSurface.copy(alpha = baseTopAlpha),
                        Color(0xFF0C1322).copy(alpha = baseBottomAlpha)
                    )
                )
            } else {
                val topAlpha = (tintAlpha ?: if (highlighted) 0.94f else 0.84f).coerceIn(0f, 1f)
                val bottomAlpha = (topAlpha * 0.90f).coerceAtLeast(0.30f)
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = topAlpha),
                        Color(0xFFF6F9FF).copy(alpha = bottomAlpha)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassmorphismDefaults.DefaultShape,
    blurRadius: Dp = GlassmorphismDefaults.DefaultBlurRadius,
    borderWidth: Dp? = null,
    borderBrush: Brush? = null,
    borderColor: Color? = null,
    containerColor: Color? = null,
    highlighted: Boolean = false,
    tintAlpha: Float? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    val defaultRimBrush = GlassmorphismDefaults.rememberBorderBrush(
        highlighted = highlighted,
        accentColor = accentColor
    )
    val resolvedBorderBrush = remember(borderColor, borderBrush, defaultRimBrush) {
        when {
            borderColor != null -> SolidColor(borderColor)
            borderBrush != null -> borderBrush
            else -> defaultRimBrush
        }
    }
    val resolvedBorderWidth = borderWidth ?: if (highlighted) {
        GlassmorphismDefaults.HighlightedBorderWidth
    } else {
        GlassmorphismDefaults.DefaultBorderWidth
    }
    val backdropBrush = GlassmorphismDefaults.rememberBackdropBrush(
        highlighted = highlighted,
        containerColor = containerColor,
        tintAlpha = tintAlpha
    )
    val topSheenBrush = remember(isDark, highlighted) {
        Brush.verticalGradient(
            0f to Color.White.copy(
                alpha = when {
                    isDark && highlighted -> 0.08f
                    isDark -> 0.04f
                    highlighted -> 0.36f
                    else -> 0.24f
                }
            ),
            0.35f to Color.Transparent,
            1f to Color.Transparent
        )
    }
    val highlightBloomColor = remember(isDark, highlighted) {
        if (isDark) {
            Color.White.copy(alpha = if (highlighted) 0.07f else 0.035f)
        } else {
            Color.White.copy(alpha = if (highlighted) 0.40f else 0.22f)
        }
    }
    val secondaryBloomColor = remember(accentColor, isDark, highlighted) {
        accentColor.copy(
            alpha = when {
                highlighted && isDark -> 0.14f
                highlighted -> 0.09f
                isDark -> 0.04f
                else -> 0.03f
            }
        )
    }

    val interactiveModifier = when {
        onLongClick != null || onClick != null -> {
            Modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .combinedClickable(
                    enabled = enabled,
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                )
        }
        else -> Modifier
    }

    if (blurRadius > 0.dp) {
        Box(
            modifier = modifier
                .clip(shape)
                .then(interactiveModifier)
                .background(Color.Transparent)
                .border(
                    width = resolvedBorderWidth,
                    brush = resolvedBorderBrush,
                    shape = shape
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(
                        radius = blurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Rectangle
                    )
                    .background(backdropBrush)
                    .drawBehind {
                        drawRect(brush = topSheenBrush)
                    }
            )
            Box(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .then(interactiveModifier)
                .background(backdropBrush)
                .drawBehind {
                    val primaryRadius = size.maxDimension * 0.72f
                    if (primaryRadius > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(highlightBloomColor, Color.Transparent),
                                center = Offset(size.width * 0.14f, size.height * 0.08f),
                                radius = primaryRadius
                            ),
                            radius = primaryRadius,
                            center = Offset(size.width * 0.14f, size.height * 0.08f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(secondaryBloomColor, Color.Transparent),
                                center = Offset(size.width * 0.90f, size.height * 0.92f),
                                radius = primaryRadius * 0.68f
                            ),
                            radius = primaryRadius * 0.68f,
                            center = Offset(size.width * 0.90f, size.height * 0.92f)
                        )
                    }
                    drawRect(brush = topSheenBrush)
                }
                .border(
                    width = resolvedBorderWidth,
                    brush = resolvedBorderBrush,
                    shape = shape
                )
                .padding(contentPadding),
            content = content
        )
    }
}
