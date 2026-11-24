package com.example.ui.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class TelegramOtpSendResponse(
    val success: Boolean,
    val message: String,
    val challengeId: String? = null,
    val serverVerifyRequired: Boolean = false,
    val needsBotStart: Boolean = false,
    val botUrl: String? = null
)

sealed interface TelegramOtpSendResult {
    data class Sent(
        val challenge: PendingOtpChallenge,
        val serverMessage: String,
        val botUrl: String
    ) : TelegramOtpSendResult

    data class Failed(
        val errorMessage: String,
        val needsBotStart: Boolean = false,
        val botUrl: String = TelegramOtpService.buildTelegramBotUrl()
    ) : TelegramOtpSendResult
}

object TelegramOtpService {
    const val TELEGRAM_BOT_USERNAME = "universchatapp"
    const val TELEGRAM_BOT_AT = "@universchatapp"
    const val RENDER_BASE_URL = "https://univers-chat.onrender.com"
    const val OTP_EXPIRATION_MILLIS: Long = 5 * 60 * 1000L
    private const val TIMEOUT_MS = 45_000

    fun buildTelegramBotUrl(phoneE164: String? = null): String {
        val digits = phoneE164.orEmpty().filter { it.isDigit() }
        return if (digits.length >= 7) {
            "https://t.me/$TELEGRAM_BOT_USERNAME?start=$digits"
        } else {
            "https://t.me/$TELEGRAM_BOT_USERNAME"
        }
    }

    fun normalizeBaseUrl(baseUrl: String = RENDER_BASE_URL): String {
        return baseUrl.trim().trimEnd('/')
    }

    fun buildSendPayloadJson(phoneE164: String, otpCode: String): String {
        val digits = phoneE164.filter { it.isDigit() }
        val localTenDigits = if (digits.length == 12 && digits.startsWith("91")) {
            digits.drop(2)
        } else {
            digits
        }
        return JSONObject().apply {
            put("phone", phoneE164)
            put("phoneNumber", phoneE164)
            put("number", localTenDigits)
            put("otp", otpCode)
            put("code", otpCode)
            put("botUsername", TELEGRAM_BOT_USERNAME)
            put("channel", "telegram")
        }.toString()
    }

    fun buildVerifyPayloadJson(
        phoneE164: String,
        otpCode: String,
        challengeId: String? = null
    ): String {
        return JSONObject().apply {
            put("phone", phoneE164)
            put("phoneNumber", phoneE164)
            put("otp", otpCode)
            put("code", otpCode)
            if (!challengeId.isNullOrBlank()) {
                put("challengeId", challengeId)
                put("sessionId", challengeId)
            }
        }.toString()
    }

    fun parseSendResponse(
        responseBody: String,
        httpStatusCode: Int,
        phoneE164: String
    ): TelegramOtpSendResponse {
        val trimmed = responseBody.trim()
        val defaultBotUrl = buildTelegramBotUrl(phoneE164)
        if (trimmed.isEmpty()) {
            val okStatus = httpStatusCode in 200..299
            return TelegramOtpSendResponse(
                success = okStatus,
                message = if (okStatus) {
                    "OTP sent via $TELEGRAM_BOT_AT."
                } else {
                    "Render server returned HTTP $httpStatusCode with an empty response."
                },
                botUrl = defaultBotUrl
            )
        }

        return try {
            val json = JSONObject(trimmed)
            val statusText = json.optString("status").trim().lowercase()
            val errorText = firstNonBlank(
                json.optString("error"),
                json.optString("err"),
                json.optString("detail")
            )
            val messageText = firstNonBlank(
                json.optString("message"),
                json.optString("msg"),
                json.optString("description"),
                errorText
            )

            val explicitSuccess: Boolean? = when {
                json.has("success") -> json.optBoolean("success")
                json.has("ok") -> json.optBoolean("ok")
                json.has("sent") -> json.optBoolean("sent")
                json.has("return") -> json.optBoolean("return")
                statusText in setOf("ok", "sent", "success") -> true
                statusText in setOf("error", "failed", "fail") -> false
                else -> null
            }

            val isSuccess = (httpStatusCode in 200..299) &&
                (explicitSuccess ?: errorText.isBlank())

            val challengeId = firstNonBlank(
                json.optString("challengeId"),
                json.optString("sessionId"),
                json.optString("requestId"),
                json.optString("request_id")
            ).takeIf { it.isNotBlank() }

            val serverVerifyRequired = json.optBoolean("serverVerify", false) ||
                json.optBoolean("verifyRequired", false) ||
                json.optBoolean("verifyOnServer", false) ||
                json.optString("otpSource").equals("server", ignoreCase = true) ||
                challengeId != null

            val botUrl = firstNonBlank(
                json.optString("botUrl"),
                json.optString("bot_url"),
                json.optString("telegramUrl"),
                json.optString("startUrl"),
                defaultBotUrl
            )

            val lowerError = "$messageText $errorText".lowercase()
            val needsBotStart = !isSuccess && (
                json.optBoolean("requiresBotStart", false) ||
                    json.optBoolean("needsStart", false) ||
                    json.optBoolean("needsRegistration", false) ||
                    lowerError.contains("start") ||
                    lowerError.contains("chat_id") ||
                    lowerError.contains("chat id") ||
                    lowerError.contains("telegram") ||
                    lowerError.contains("not linked") ||
                    lowerError.contains("not registered") ||
                    lowerError.contains("universchatapp")
                )

            val resolvedMessage = when {
                isSuccess -> messageText.ifBlank { "OTP sent to your Telegram via $TELEGRAM_BOT_AT." }
                messageText.isNotBlank() -> messageText
                else -> "Could not send Telegram OTP (HTTP $httpStatusCode)."
            }

            TelegramOtpSendResponse(
                success = isSuccess,
                message = resolvedMessage,
                challengeId = challengeId,
                serverVerifyRequired = serverVerifyRequired,
                needsBotStart = needsBotStart,
                botUrl = botUrl
            )
        } catch (_: Exception) {
            val okStatus = httpStatusCode in 200..299 && !looksLikeHtml(trimmed)
            val fallbackMessage = if (okStatus) {
                "OTP sent to your Telegram via $TELEGRAM_BOT_AT."
            } else if (httpStatusCode == 404) {
                "OTP endpoint not found on Render server (HTTP 404). Make sure $RENDER_BASE_URL exposes POST /send-otp for $TELEGRAM_BOT_AT."
            } else {
                "Unexpected response from Render server (HTTP $httpStatusCode)."
            }
            TelegramOtpSendResponse(
                success = okStatus,
                message = fallbackMessage,
                needsBotStart = httpStatusCode == 404 || trimmed.contains("start", ignoreCase = true),
                botUrl = defaultBotUrl
            )
        }
    }

    suspend fun sendOtp(
        phoneE164: String,
        baseUrl: String = RENDER_BASE_URL,
        nowMillis: Long = System.currentTimeMillis(),
        httpPost: suspend (url: String, jsonBody: String) -> Pair<Int, String> = ::executeHttpPost
    ): TelegramOtpSendResult {
        if (!isValidE164PhoneNumber(phoneE164)) {
            return TelegramOtpSendResult.Failed(
                errorMessage = "Enter a valid phone number with country code."
            )
        }

        val normalizedBase = normalizeBaseUrl(baseUrl)
        val otpCode = Fast2SmsOtpService.generateSixDigitOtp()
        val payload = buildSendPayloadJson(phoneE164 = phoneE164, otpCode = otpCode)
        val defaultBotUrl = buildTelegramBotUrl(phoneE164)

        return try {
            var (statusCode, body) = httpPost("$normalizedBase/send-otp", payload)
            if ((statusCode == 404 || statusCode == 405) && !hasStructuredJsonMessage(body)) {
                val (altStatus, altBody) = httpPost("$normalizedBase/api/send-otp", payload)
                if (altStatus != 404 && altStatus != 405) {
                    statusCode = altStatus
                    body = altBody
                }
            }

            val parsed = parseSendResponse(body, statusCode, phoneE164)
            if (parsed.success) {
                TelegramOtpSendResult.Sent(
                    challenge = PendingOtpChallenge(
                        phoneE164 = phoneE164,
                        otpCode = otpCode,
                        expiresAtMillis = nowMillis + OTP_EXPIRATION_MILLIS,
                        challengeId = parsed.challengeId,
                        serverVerifyRequired = parsed.serverVerifyRequired
                    ),
                    serverMessage = parsed.message,
                    botUrl = parsed.botUrl ?: defaultBotUrl
                )
            } else {
                TelegramOtpSendResult.Failed(
                    errorMessage = parsed.message,
                    needsBotStart = parsed.needsBotStart,
                    botUrl = parsed.botUrl ?: defaultBotUrl
                )
            }
        } catch (e: IOException) {
            TelegramOtpSendResult.Failed(
                errorMessage = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Could not reach Render server. If it was asleep, wait a few seconds and retry.",
                botUrl = defaultBotUrl
            )
        } catch (e: Exception) {
            TelegramOtpSendResult.Failed(
                errorMessage = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Failed to send Telegram OTP. Please try again.",
                botUrl = defaultBotUrl
            )
        }
    }

    suspend fun verifyOtpWithBackend(
        challenge: PendingOtpChallenge?,
        enteredCode: String,
        baseUrl: String = RENDER_BASE_URL,
        nowMillis: Long = System.currentTimeMillis(),
        httpPost: suspend (url: String, jsonBody: String) -> Pair<Int, String> = ::executeHttpPost
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

        val normalizedBase = normalizeBaseUrl(baseUrl)
        val payload = buildVerifyPayloadJson(
            phoneE164 = challenge.phoneE164,
            otpCode = cleanedInput,
            challengeId = challenge.challengeId
        )

        return try {
            var (statusCode, body) = httpPost("$normalizedBase/verify-otp", payload)
            if ((statusCode == 404 || statusCode == 405 || statusCode == 501) && !hasStructuredJsonMessage(body)) {
                val (altStatus, altBody) = httpPost("$normalizedBase/api/verify-otp", payload)
                statusCode = altStatus
                body = altBody
            }

            // If backend does not expose a /verify-otp route (stateless relay), verify against challenge code.
            if (statusCode == 404 || statusCode == 405 || statusCode == 501) {
                return if (!challenge.serverVerifyRequired) {
                    verifyLocalOtp(challenge, cleanedInput)
                } else {
                    OtpVerifyResult.Rejected("Render OTP verification endpoint not found (HTTP $statusCode).")
                }
            }

            parseVerifyResponse(body, statusCode)
        } catch (_: IOException) {
            if (!challenge.serverVerifyRequired) {
                verifyLocalOtp(challenge, cleanedInput)
            } else {
                OtpVerifyResult.Rejected("Network error while verifying OTP with Render server.")
            }
        } catch (e: Exception) {
            OtpVerifyResult.Rejected(
                e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Verification failed. Please try again."
            )
        }
    }

    private fun verifyLocalOtp(challenge: PendingOtpChallenge, cleanedInput: String): OtpVerifyResult {
        return if (cleanedInput == challenge.otpCode) {
            OtpVerifyResult.Verified
        } else {
            OtpVerifyResult.Rejected("That 6-digit code doesn't match. Check Telegram ($TELEGRAM_BOT_AT) and try again.")
        }
    }

    internal fun parseVerifyResponse(responseBody: String, httpStatusCode: Int): OtpVerifyResult {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty()) {
            return if (httpStatusCode in 200..299) {
                OtpVerifyResult.Verified
            } else {
                OtpVerifyResult.Rejected("OTP verification failed (HTTP $httpStatusCode).")
            }
        }

        return try {
            val json = JSONObject(trimmed)
            val statusText = json.optString("status").trim().lowercase()
            val errorText = firstNonBlank(
                json.optString("error"),
                json.optString("err"),
                json.optString("message"),
                json.optString("msg")
            )
            val explicitVerified: Boolean? = when {
                json.has("verified") -> json.optBoolean("verified")
                json.has("valid") -> json.optBoolean("valid")
                json.has("success") -> json.optBoolean("success")
                json.has("ok") -> json.optBoolean("ok")
                statusText in setOf("verified", "valid", "ok", "success") -> true
                statusText in setOf("invalid", "expired", "error", "failed") -> false
                else -> null
            }

            val isVerified = (httpStatusCode in 200..299) && (explicitVerified ?: true)
            if (isVerified) {
                OtpVerifyResult.Verified
            } else {
                OtpVerifyResult.Rejected(
                    errorText.ifBlank { "That 6-digit code doesn't match. Check Telegram ($TELEGRAM_BOT_AT) and try again." }
                )
            }
        } catch (_: Exception) {
            if (httpStatusCode in 200..299 && !looksLikeHtml(trimmed)) {
                OtpVerifyResult.Verified
            } else {
                OtpVerifyResult.Rejected("OTP verification failed (HTTP $httpStatusCode).")
            }
        }
    }

    private fun hasStructuredJsonMessage(body: String): Boolean {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            val json = JSONObject(trimmed)
            json.has("message") || json.has("error") || json.has("requiresBotStart")
        }.getOrDefault(false)
    }

    private fun looksLikeHtml(body: String): Boolean {
        return body.startsWith("<", ignoreCase = true)
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() && it != "null" }?.trim().orEmpty()
    }

    private suspend fun executeHttpPost(
        requestUrl: String,
        jsonBody: String
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }
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
