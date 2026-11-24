package com.example.ui.auth

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseChatTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-420dpi")
class TelegramOtpServiceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `buildTelegramBotUrl includes start phone digits when valid`() {
        assertEquals(
            "https://t.me/universchatapp",
            TelegramOtpService.buildTelegramBotUrl()
        )
        assertEquals(
            "https://t.me/universchatapp?start=919760150227",
            TelegramOtpService.buildTelegramBotUrl("+919760150227")
        )
    }

    @Test
    fun `sendOtp posts to Render backend and sets 5-minute challenge`() = runTest {
        var requestedUrl = ""
        var requestedBody = ""
        val now = 1_700_000_000_000L

        val result = TelegramOtpService.sendOtp(
            phoneE164 = "+919760150227",
            baseUrl = "https://univers-chat.onrender.com/",
            nowMillis = now,
            httpPost = { url, jsonBody ->
                requestedUrl = url
                requestedBody = jsonBody
                200 to """{"success":true,"serverVerify":true,"challengeId":"ch_123","message":"Sent via @universchatapp"}"""
            }
        )

        assertEquals("https://univers-chat.onrender.com/send-otp", requestedUrl)
        assertTrue(requestedBody.contains("\"+919760150227\""))
        assertTrue(requestedBody.contains("\"universchatapp\""))
        assertTrue(result is TelegramOtpSendResult.Sent)
        val sent = result as TelegramOtpSendResult.Sent
        assertEquals("ch_123", sent.challenge.challengeId)
        assertTrue(sent.challenge.serverVerifyRequired)
        assertEquals(now + 5 * 60 * 1000L, sent.challenge.expiresAtMillis)
    }

    @Test
    fun `sendOtp falls back to api prefix on 404 html and flags needsBotStart when unlinked`() = runTest {
        val hitUrls = mutableListOf<String>()

        val result = TelegramOtpService.sendOtp(
            phoneE164 = "+919760150227",
            httpPost = { url, _ ->
                hitUrls.add(url)
                if (url.endsWith("/send-otp") && !url.contains("/api/")) {
                    404 to "<!DOCTYPE html><html><body>Cannot POST /send-otp</body></html>"
                } else {
                    404 to """{"success":false,"requiresBotStart":true,"botUrl":"https://t.me/universchatapp?start=919760150227","message":"Start @universchatapp on Telegram first."}"""
                }
            }
        )

        assertEquals(
            listOf(
                "https://univers-chat.onrender.com/send-otp",
                "https://univers-chat.onrender.com/api/send-otp"
            ),
            hitUrls
        )
        assertTrue(result is TelegramOtpSendResult.Failed)
        val failed = result as TelegramOtpSendResult.Failed
        assertTrue(failed.needsBotStart)
        assertEquals("https://t.me/universchatapp?start=919760150227", failed.botUrl)
        assertTrue(failed.errorMessage.contains("@universchatapp"))
    }

    @Test
    fun `verifyOtpWithBackend validates server response and falls back to local challenge on 404 stateless relay`() = runTest {
        val now = 1_700_000_000_000L
        val serverChallenge = PendingOtpChallenge(
            phoneE164 = "+919760150227",
            otpCode = "111111",
            expiresAtMillis = now + 300_000L,
            challengeId = "ch_srv",
            serverVerifyRequired = true
        )

        val serverOk = TelegramOtpService.verifyOtpWithBackend(
            challenge = serverChallenge,
            enteredCode = "654321",
            nowMillis = now + 10_000L,
            httpPost = { _, _ -> 200 to """{"verified":true}""" }
        )
        assertTrue(serverOk is OtpVerifyResult.Verified)

        val serverBad = TelegramOtpService.verifyOtpWithBackend(
            challenge = serverChallenge,
            enteredCode = "000000",
            nowMillis = now + 10_000L,
            httpPost = { _, _ -> 401 to """{"verified":false,"message":"Invalid Telegram code"}""" }
        )
        assertTrue(serverBad is OtpVerifyResult.Rejected)
        assertEquals("Invalid Telegram code", (serverBad as OtpVerifyResult.Rejected).errorMessage)

        val relayChallenge = PendingOtpChallenge(
            phoneE164 = "+919760150227",
            otpCode = "482913",
            expiresAtMillis = now + 300_000L,
            serverVerifyRequired = false
        )
        val relayOk = TelegramOtpService.verifyOtpWithBackend(
            challenge = relayChallenge,
            enteredCode = "482913",
            nowMillis = now + 20_000L,
            httpPost = { _, _ -> 404 to "Cannot POST /verify-otp" }
        )
        assertTrue(relayOk is OtpVerifyResult.Verified)

        val relayExpired = TelegramOtpService.verifyOtpWithBackend(
            challenge = relayChallenge,
            enteredCode = "482913",
            nowMillis = now + 300_001L,
            httpPost = { _, _ -> 404 to "Cannot POST /verify-otp" }
        )
        assertFalse(relayExpired is OtpVerifyResult.Verified)
    }

    @Test
    fun `auth screen renders telegram bot @universchatapp link card and accessible cta targets`() {
        composeRule.setContent {
            PulseChatTheme(darkTheme = true) {
                AuthScreen(onAuthSuccess = {})
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Telegram OTP & Google").assertIsDisplayed()
        composeRule.onNodeWithTag("open_telegram_bot_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("send_otp_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
