package com.example.ui.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Fast2SmsOtpServiceTest {

    @Test
    fun generateSixDigitOtp_producesSixNumericDigitsInRange() {
        val deterministic = Fast2SmsOtpService.generateSixDigitOtp(Random(42L))
        assertEquals(6, deterministic.length)
        assertTrue(deterministic.all { it.isDigit() })
        assertTrue(deterministic.toInt() in 100_000..999_999)
    }

    @Test
    fun normalizeAndBuildRequestUrl_matchesFast2SmsFormat() {
        val number = Fast2SmsOtpService.normalizePhoneForFast2Sms("+919876543210")
        assertEquals("9876543210", number)

        val url = Fast2SmsOtpService.buildRequestUrl("482913", number)
        assertEquals(
            "https://www.fast2sms.com/dev/bulkV2?route=q&message=Your%20Univers%20Chat%20verification%20code%20is%20482913&numbers=9876543210",
            url
        )
    }

    @Test
    fun sendOtp_parsesReturnTrueAndSetsFiveMinuteExpiration() = runTest {
        var capturedUrl = ""
        var capturedAuth = ""
        val now = 1_700_000_000_000L

        val result = Fast2SmsOtpService.sendOtp(
            phoneE164 = "+919876543210",
            apiKey = "test-api-key",
            nowMillis = now,
            httpGet = { url, auth ->
                capturedUrl = url
                capturedAuth = auth
                200 to """{"return":true,"request_id":"abc123","message":["SMS sent successfully."]}"""
            }
        )

        assertTrue(result is Fast2SmsSendResult.Sent)
        val sent = result as Fast2SmsSendResult.Sent
        assertEquals("test-api-key", capturedAuth)
        assertTrue(capturedUrl.startsWith("https://www.fast2sms.com/dev/bulkV2?route=q&message=Your%20Univers%20Chat%20verification%20code%20is%20"))
        assertTrue(capturedUrl.endsWith("&numbers=9876543210"))
        assertEquals("+919876543210", sent.challenge.phoneE164)
        assertEquals(now + 5 * 60 * 1000L, sent.challenge.expiresAtMillis)
    }

    @Test
    fun sendOtp_parsesReturnFalseAndExtractsMessageArray() = runTest {
        val result = Fast2SmsOtpService.sendOtp(
            phoneE164 = "+919876543210",
            apiKey = "test-api-key",
            httpGet = { _, _ ->
                400 to """{"return":false,"status_code":999,"message":["Minimum 100 INR balance required"]}"""
            }
        )

        assertTrue(result is Fast2SmsSendResult.Failed)
        assertEquals(
            "Minimum 100 INR balance required",
            (result as Fast2SmsSendResult.Failed).errorMessage
        )
    }

    @Test
    fun verifyOtp_enforcesMatchAndFiveMinuteExpiration() {
        val now = 1_700_000_000_000L
        val challenge = PendingOtpChallenge(
            phoneE164 = "+919876543210",
            otpCode = "654321",
            expiresAtMillis = now + 300_000L
        )

        assertTrue(Fast2SmsOtpService.verifyOtp(challenge, "654321", now + 299_000L) is OtpVerifyResult.Verified)
        assertTrue(Fast2SmsOtpService.verifyOtp(challenge, "111111", now + 10_000L) is OtpVerifyResult.Rejected)
        assertTrue(Fast2SmsOtpService.verifyOtp(challenge, "654321", now + 300_001L) is OtpVerifyResult.Rejected)
    }
}
