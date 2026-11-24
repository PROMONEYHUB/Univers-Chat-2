package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =====================================================================
// Univers Chat Palette — Light Mode (Airy Porcelain + Electric Sapphire)
// =====================================================================
val IosBlue = Color(0xFF0066F5)
val IosBlueDeep = Color(0xFF004CC4)
val IosBlueBright = Color(0xFF2B8DFF)
val IosIndigo = Color(0xFF5B56E8)
val IosTeal = Color(0xFF0EA5C6)
val IosGreen = Color(0xFF18C964)
val IosRed = Color(0xFFFF3B30)
val IosOrange = Color(0xFFFF9500)
val IosPink = Color(0xFFFF2D6F)

val IosBlueContainerLight = Color(0xFFE1EEFF)
val IosOnBlueContainerLight = Color(0xFF002B66)
val IosRedContainerLight = Color(0xFFFFE5E4)
val IosOnRedContainerLight = Color(0xFF690005)
val IosGreenContainerLight = Color(0xFFD9FBE6)
val IosOnGreenContainerLight = Color(0xFF003B18)

val IosLightGroupedBackground = Color(0xFFF3F6FC)   // Fresh airy ice-porcelain canvas
val IosLightCardSurface = Color(0xFFFFFFFF)         // Crisp elevated card surface
val IosLightBubbleIncoming = Color(0xFFEFF3FA)      // Soft ice-slate incoming bubble
val IosLightFill = Color(0xFFE6ECF6)                // Soft slate fill for inputs/pills
val IosLightLabel = Color(0xFF0F172A)               // Deep slate ink label
val IosLightSecondaryLabel = Color(0xFF5A657A)      // Accessible cool slate secondary
val IosLightSeparator = Color(0xFFC9D4E5)
val IosLightHairline = Color(0xFFE2E8F4)

// =====================================================================
// Univers Chat Palette — Dark Mode (Midnight Obsidian + Luminous Cyan/Sapphire)
// =====================================================================
val IosDarkBlue = Color(0xFF2997FF)
val IosDarkBlueDeep = Color(0xFF005ED9)
val IosDarkIndigo = Color(0xFF706EFF)
val IosDarkTeal = Color(0xFF45D9F5)
val IosDarkGreen = Color(0xFF2CE075)
val IosDarkRed = Color(0xFFFF4D44)
val IosDarkOrange = Color(0xFFFFA31A)
val IosDarkPink = Color(0xFFFF407A)

val IosBlueContainerDark = Color(0xFF102A52)
val IosOnBlueContainerDark = Color(0xFFD6E7FF)
val IosRedContainerDark = Color(0xFF561518)
val IosOnRedContainerDark = Color(0xFFFFDAD8)
val IosGreenContainerDark = Color(0xFF0A3E20)
val IosOnGreenContainerDark = Color(0xFFC4FBE0)

val IosDarkBackground = Color(0xFF060A13)           // Deep midnight OLED obsidian
val IosDarkCardSurface = Color(0xFF101828)          // Elevated deep slate glass surface
val IosDarkBubbleIncoming = Color(0xFF1B2436)       // Slate incoming chat bubble
val IosDarkFill = Color(0xFF1E293D)                 // Slate fill for inputs/pills
val IosDarkLabel = Color(0xFFF8FAFC)                // Crisp luminous white label
val IosDarkSecondaryLabel = Color(0xFF9FB0C8)       // Cool silver-slate secondary
val IosDarkSeparator = Color(0xFF33415C)
val IosDarkHairline = Color(0xFF222E44)

// Semantic Aliases
val OnlinePresenceGreen = IosGreen
val AwayPresenceAmber = IosOrange
val ErrorRed = IosRed
val ErrorContainerLight = IosRedContainerLight
val ErrorContainerDark = IosRedContainerDark

// Dynamic Island & Liquid Glass Surface Tokens
val DynamicIslandDarkSurface = Color(0xFF0C1220)
val DynamicIslandDarkBorder = Color(0xFF27344E)
val AuroraCyan = Color(0xFF22D3EE)
val AuroraViolet = Color(0xFF7C3AED)

// Google Sign-In Mark Tokens
val GoogleBlue = Color(0xFF4285F4)
val GoogleRed = Color(0xFFEA4335)
val GoogleYellow = Color(0xFFFBBC05)
val GoogleGreen = Color(0xFF34A853)

// Messenger Gradients & Brushes
object PulseBrushes {
    val BrandGradient: Brush = Brush.linearGradient(
        colors = listOf(IosBlueBright, IosBlue, IosIndigo)
    )

    val OutgoingBubbleLight: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF238BFF), IosBlue)
    )

    val OutgoingBubbleDark: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF2693FF), IosDarkBlueDeep)
    )

    val StoryRingActive: Brush = Brush.sweepGradient(
        colors = listOf(IosBlueBright, AuroraCyan, IosIndigo, IosPink, IosBlueBright)
    )

    val UnreadBadgeGradient: Brush = Brush.linearGradient(
        colors = listOf(IosBlueBright, IosIndigo)
    )

    val SavedNotesGradientLight: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFE7F1FF), Color(0xFFF2ECFF))
    )

    val SavedNotesGradientDark: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF132444), Color(0xFF1F1B3B))
    )

    val SpotlightCardLight: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFF5F9FF), Color(0xFFFFFFFF))
    )

    val SpotlightCardDark: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF141E32), Color(0xFF101828))
    )
}
