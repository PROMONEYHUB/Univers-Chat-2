package com.example.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.data.model.Conversation
import com.example.data.model.PrivateUser
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.theme.PulseChatTheme
import com.example.ui.viewmodel.ChatFilter
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-420dpi")
class Phase3UiPolishTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val currentUser = UserProfile(
        userId = "u_self",
        displayName = "Alex Mercer",
        handle = "alexm",
        bio = "Shipping real-time apps",
        statusVibe = "Available",
        isOnline = true,
        lastHeartbeatAt = Timestamp(Date())
    )

    private val alice = UserProfile(
        userId = "u_alice",
        displayName = "Alice Vance",
        handle = "alicev",
        bio = "Product Design",
        statusVibe = "Focusing",
        isOnline = true,
        lastHeartbeatAt = Timestamp(Date())
    )

    private val bob = UserProfile(
        userId = "u_bob",
        displayName = "Bob Chen",
        handle = "bchen",
        bio = "Systems",
        statusVibe = "Coffee run",
        isOnline = false,
        lastSeenAt = Timestamp(Date(System.currentTimeMillis() - 600_000L))
    )

    private val sampleConversations = listOf(
        Conversation(
            id = "conv_1",
            type = Conversation.TYPE_DIRECT,
            participantIds = listOf("u_self", "u_alice"),
            participantNames = mapOf("u_self" to "Alex Mercer", "u_alice" to "Alice Vance"),
            lastMessageText = "Design specs look crisp!",
            lastMessageSenderId = "u_alice",
            lastMessageAt = Timestamp(Date()),
            unreadCounts = mapOf("u_self" to 2L)
        ),
        Conversation(
            id = "conv_2",
            type = Conversation.TYPE_GROUP,
            title = "Core Engineering",
            topic = "Latency & Polish",
            participantIds = listOf("u_self", "u_alice", "u_bob"),
            participantNames = mapOf("u_self" to "Alex Mercer", "u_alice" to "Alice Vance", "u_bob" to "Bob Chen"),
            lastMessageText = "Build green on main",
            lastMessageSenderId = "u_bob",
            lastMessageAt = Timestamp(Date(System.currentTimeMillis() - 120_000L)),
            unreadCounts = mapOf("u_self" to 0L)
        )
    )

    @Test
    fun `chats screen renders loading skeleton cleanly and meets fab touch target`() {
        composeRule.setContent {
            PulseChatTheme(darkTheme = true) {
                ChatsScreen(
                    currentUserId = currentUser.userId,
                    currentUserProfile = currentUser,
                    conversationsState = UiState.Loading,
                    peopleState = UiState.Loading,
                    searchQuery = "",
                    selectedFilter = ChatFilter.ALL,
                    selectedConversationId = null,
                    onSearchQueryChange = {},
                    onSelectFilter = {},
                    onOpenConversation = {},
                    onStartDirectChat = {},
                    onCreateGroupChat = { _, _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Messages").assertIsDisplayed()
        composeRule.onNodeWithTag("new_chat_fab")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `chats screen filters conversations by unread chip and search query`() {
        var filter by mutableStateOf(ChatFilter.ALL)
        var query by mutableStateOf("")

        composeRule.setContent {
            PulseChatTheme(darkTheme = false) {
                ChatsScreen(
                    currentUserId = currentUser.userId,
                    currentUserProfile = currentUser,
                    conversationsState = UiState.Success(sampleConversations),
                    peopleState = UiState.Success(listOf(currentUser, alice, bob)),
                    searchQuery = query,
                    selectedFilter = filter,
                    selectedConversationId = null,
                    onSearchQueryChange = { query = it },
                    onSelectFilter = { filter = it },
                    onOpenConversation = {},
                    onStartDirectChat = {},
                    onCreateGroupChat = { _, _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("conversation_card_conv_1").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_card_conv_2").assertIsDisplayed()

        composeRule.onNodeWithTag("filter_chip_unread").performClick()
        composeRule.waitForIdle()
        assertEquals(ChatFilter.UNREAD, filter)
        composeRule.onNodeWithTag("conversation_card_conv_1").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_card_conv_2").assertDoesNotExist()

        filter = ChatFilter.ALL
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chats_search_input").performTextInput("Engineering")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("conversation_card_conv_2").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_card_conv_1").assertDoesNotExist()
    }

    @Test
    fun `people screen renders virtualized contacts with accessible touch targets and search`() {
        var query by mutableStateOf("")

        composeRule.setContent {
            PulseChatTheme(darkTheme = true) {
                PeopleScreen(
                    currentUserId = currentUser.userId,
                    currentUserProfile = currentUser,
                    peopleState = UiState.Success(listOf(currentUser, alice, bob)),
                    searchQuery = query,
                    onSearchQueryChange = { query = it },
                    onMessagePerson = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Contacts").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_call_person_u_alice")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("video_call_person_u_alice")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("message_person_u_alice")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("people_search_input").performTextInput("bchen")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("message_person_u_bob").assertIsDisplayed()
        composeRule.onNodeWithTag("message_person_u_alice").assertDoesNotExist()
    }

    @Test
    fun `profile screen renders health entry and primary buttons meet touch size`() {
        var openedHealth = false

        composeRule.setContent {
            PulseChatTheme(darkTheme = false) {
                ProfileScreen(
                    userProfile = currentUser,
                    privateUser = PrivateUser(
                        id = currentUser.userId,
                        userId = currentUser.userId,
                        email = "alex@example.com"
                    ),
                    isBusy = false,
                    onSaveProfile = { _, _, _, _, _, _ -> },
                    onUpdatePrivacy = { _, _, _ -> },
                    onSignOut = {},
                    onOpenNotificationHealth = { openedHealth = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_display_name_input")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("save_profile_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("open_notification_health_row")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(openedHealth)

        composeRule.onNodeWithTag("sign_out_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `notification health screen renders summary, toggles, and 48dp action buttons`() {
        var previewEnabled by mutableStateOf(true)

        composeRule.setContent {
            PulseChatTheme(darkTheme = true) {
                NotificationHealthScreen(
                    hasFirebaseAuth = true,
                    notificationsEnabled = true,
                    notificationPreviewEnabled = previewEnabled,
                    onToggleNotificationPreview = { previewEnabled = it },
                    onResyncFcmToken = {},
                    onBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("notification_health_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("notification_health_back_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("health_test_notification_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("health_refresh_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
