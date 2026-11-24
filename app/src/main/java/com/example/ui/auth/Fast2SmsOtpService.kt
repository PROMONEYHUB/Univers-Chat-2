package com.example.ui.auth

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Random

data class PendingOtpChallenge(
    val phoneE164: String,
    val otpCode: String,
    val expiresAtMillis: Long,
    val challengeId: String? = null,
    val serverVerifyRequired: Boolean = false
)

data class Fast2SmsApiResponse(
    val success: Boolean,
    val message: String
)

sealed interface Fast2SmsSendResult {
    data class Sent(
        val challenge: PendingOtpChallenge,
        val serverMessage: String
    ) : Fast2SmsSendResult

    data class Failed(
        val errorMessage: String
    ) : Fast2SmsSendResult
}

sealed interface OtpVerifyResult {
    data object Verified : OtpVerifyResult
    data class Rejected(val errorMessage: String) : OtpVerifyResult
}

object Fast2SmsOtpService {
    const val OTP_EXPIRATION_MILLIS: Long = 5 * 60 * 1000L
    private const val FAST2SMS_BASE_URL = "https://www.fast2sms.com/dev/bulkV2"
    private const val PLACEHOLDER_API_KEY = "your_fast2sms_api_key_here"
    private const val TIMEOUT_MS = 15_000

    private val secureRandom = SecureRandom()

    fun generateSixDigitOtp(random: Random = secureRandom): String {
        val code = 100_000 + random.nextInt(900_000)
        return code.toString()
    }

    fun normalizePhoneForFast2Sms(phoneE164: String): String {
        val digits = phoneE164.filter { it.isDigit() }
        return if (digits.length == 12 && digits.startsWith("91")) {
            digits.drop(2)
        } else {
            digits
        }
    }

    fun buildRequestUrl(otpCode: String, phoneForFast2Sms: String): String {
        val rawMessage = "Your Univers Chat verification code is $otpCode"
        val encodedMessage = URLEncoder.encode(rawMessage, Charsets.UTF_8.name())
            .replace("+", "%20")
        val encodedNumbers = URLEncoder.encode(phoneForFast2Sms, Charsets.UTF_8.name())
        return "$FAST2SMS_BASE_URL?route=q&message=$encodedMessage&numbers=$encodedNumbers"
    }

    fun parseApiResponse(responseBody: String, httpStatusCode: Int = 200): Fast2SmsApiResponse {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty()) {
            return Fast2SmsApiResponse(
                success = false,
                message = "Empty response from Fast2SMS (HTTP $httpStatusCode)."
            )
        }
        return try {
            val json = JSONObject(trimmed)
            val isSuccess = json.optBoolean("return", false)
            val messageArray = json.optJSONArray("message")
            val arrayMessages = buildList {
                if (messageArray != null) {
                    for (i in 0 until messageArray.length()) {
                        val item = messageArray.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }
            val resolvedMessage = when {
                arrayMessages.isNotEmpty() -> arrayMessages.joinToString(" ")
                json.optString("message").isNotBlank() -> json.optString("message").trim()
                isSuccess -> "OTP sent successfully."
                else -> "Fast2SMS request failed (HTTP $httpStatusCode)."
            }
            Fast2SmsApiResponse(success = isSuccess, message = resolvedMessage)
        } catch (_: Exception) {
            Fast2SmsApiResponse(
                success = false,
                message = "Unexpected response from Fast2SMS (HTTP $httpStatusCode)."
            )
        }
    }

    suspend fun sendOtp(
        phoneE164: String,
        apiKey: String = BuildConfig.FAST2SMS_API_KEY,
        nowMillis: Long = System.currentTimeMillis(),
        httpGet: suspend (url: String, authHeader: String) -> Pair<Int, String> = ::executeHttpGet
    ): Fast2SmsSendResult {
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isEmpty() || cleanedKey == PLACEHOLDER_API_KEY) {
            return Fast2SmsSendResult.Failed(
                "Fast2SMS API key is not configured. Add FAST2SMS_API_KEY in the AI Studio Secrets panel."
            )
        }

        val fast2SmsNumber = normalizePhoneForFast2Sms(phoneE164)
        if (fast2SmsNumber.length < 7) {
            return Fast2SmsSendResult.Failed("Enter a valid phone number.")
        }

        val otpCode = generateSixDigitOtp()
        val requestUrl = buildRequestUrl(otpCode = otpCode, phoneForFast2Sms = fast2SmsNumber)

        return try {
            val (statusCode, body) = httpGet(requestUrl, cleanedKey)
            val parsed = parseApiResponse(body, statusCode)
            if (parsed.success) {
                Fast2SmsSendResult.Sent(
                    challenge = PendingOtpChallenge(
                        phoneE164 = phoneE164,
                        otpCode = otpCode,
                        expiresAtMillis = nowMillis + OTP_EXPIRATION_MILLIS
                    ),
                    serverMessage = parsed.message
                )
            } else {
                Fast2SmsSendResult.Failed(parsed.message)
            }
        } catch (e: IOException) {
            Fast2SmsSendResult.Failed(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Network error while sending SMS OTP. Check your internet connection."
            )
        } catch (e: Exception) {
            Fast2SmsSendResult.Failed(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Failed to send SMS OTP. Please try again."
            )
        }
    }

    fun verifyOtp(
        challenge: PendingOtpChallenge?,
        enteredCode: String,
        nowMillis: Long = System.currentTimeMillis()
    ): OtpVerifyResult {
        if (challenge == null) {
            return OtpVerifyResult.Rejected("Verification session expired. Please request a new OTP.")
        }
        if (nowMillis > challenge.expiresAtMillis) {
            return OtpVerifyResult.Rejected("This 6-digit OTP expired after 5 minutes. Tap Resend OTP for a new code.")
        }
        val cleanedInput = enteredCode.filter { it.isDigit() }
        if (cleanedInput.length != 6) {
            return OtpVerifyResult.Rejected("Enter the full 6-digit verification code.")
        }
        return if (cleanedInput == challenge.otpCode) {
            OtpVerifyResult.Verified
        } else {
            OtpVerifyResult.Rejected("That 6-digit code doesn't match. Check your SMS and try again.")
        }
    }

    private suspend fun executeHttpGet(
        requestUrl: String,
        authHeader: String
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("authorization", authHeader)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            statusCode to body
        } finally {
            connection.disconnect()
        }
    }
}
