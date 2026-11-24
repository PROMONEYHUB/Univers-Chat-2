package com.example.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlassmorphismCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders foreground content inside glass card`() {
        composeRule.setContent {
            PulseChatTheme(darkTheme = false) {
                GlassmorphismCard(
                    contentPadding = PaddingValues(16.dp),
                    highlighted = true,
                    modifier = Modifier.testTag("glass_card_static")
                ) {
                    Text("Pinned Conversation")
                }
            }
        }

        composeRule.onNodeWithTag("glass_card_static").assertIsDisplayed()
        composeRule.onNodeWithText("Pinned Conversation").assertIsDisplayed()
    }

    @Test
    fun `clickable card enforces minimum touch target and respects enabled state`() {
        var clicks by mutableIntStateOf(0)
        var isEnabled by androidx.compose.runtime.mutableStateOf(true)

        composeRule.setContent {
            PulseChatTheme(darkTheme = true) {
                GlassmorphismCard(
                    shape = RoundedCornerShape(24.dp),
                    blurRadius = 24.dp,
                    borderWidth = 1.5.dp,
                    borderColor = Color.White.copy(alpha = 0.6f),
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    onClick = { clicks++ },
                    enabled = isEnabled,
                    modifier = Modifier.testTag("glass_card_clickable")
                ) {
                    Text("Tap me")
                }
            }
        }

        val card = composeRule.onNodeWithTag("glass_card_clickable")
        card.assertWidthIsAtLeast(48.dp)
        card.assertHeightIsAtLeast(48.dp)
        card.performClick()
        assertEquals(1, clicks)

        isEnabled = false
        composeRule.waitForIdle()
        card.performClick()
        assertEquals(1, clicks)
    }
}
