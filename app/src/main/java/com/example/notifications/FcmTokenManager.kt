package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.repository.MessagingRepository
import com.example.data.sync.UniversAppContainer
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class FcmTokenDiagnosticState(
    val deviceId: String = "",
    val currentToken: String? = null,
    val lastSyncedUserId: String? = null,
    val lastError: String? = null,
    val lastSyncedAtMillis: Long? = null
)

object FcmTokenManager {

    private const val TAG = "FcmTokenManager"
    private const val PREFS_NAME = "univers_fcm_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_TOKEN = "cached_fcm_token"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _diagnosticState = MutableStateFlow(FcmTokenDiagnosticState())
    val diagnosticState: StateFlow<FcmTokenDiagnosticState> = _diagnosticState.asStateFlow()

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun getCachedToken(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    private fun saveCachedToken(context: Context, token: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
        }.apply()
    }

    suspend fun syncCurrentToken(
        context: Context,
        repository: MessagingRepository,
        userId: String
    ): Result<String> {
        val appContext = context.applicationContext
        val deviceId = getOrCreateDeviceId(appContext)
        return try {
            val token = withContext(Dispatchers.IO) {
                FirebaseMessaging.getInstance().token.await()
            }
            saveCachedToken(appContext, token)
            repository.upsertFcmToken(deviceId, token).getOrThrow()
            _diagnosticState.value = FcmTokenDiagnosticState(
                deviceId = deviceId,
                currentToken = token,
                lastSyncedUserId = userId,
                lastError = null,
                lastSyncedAtMillis = System.currentTimeMillis()
            )
            Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync FCM token", e)
            _diagnosticState.value = FcmTokenDiagnosticState(
                deviceId = deviceId,
                currentToken = getCachedToken(appContext),
                lastSyncedUserId = null,
                lastError = e.localizedMessage ?: "FCM registration unavailable"
            )
            Result.failure(e)
        }
    }

    fun onTokenRefreshed(context: Context, newToken: String) {
        val appContext = context.applicationContext
        val deviceId = getOrCreateDeviceId(appContext)
        saveCachedToken(appContext, newToken)
        val currentUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        _diagnosticState.value = _diagnosticState.value.copy(
            deviceId = deviceId,
            currentToken = newToken,
            lastError = null
        )
        if (currentUid != null) {
            scope.launch {
                val repo = UniversAppContainer.get(appContext).messagingRepository
                repo.upsertFcmToken(deviceId, newToken)
                    .onSuccess {
                        _diagnosticState.value = FcmTokenDiagnosticState(
                            deviceId = deviceId,
                            currentToken = newToken,
                            lastSyncedUserId = currentUid,
                            lastError = null,
                            lastSyncedAtMillis = System.currentTimeMillis()
                        )
                    }
                    .onFailure { err ->
                        _diagnosticState.value = _diagnosticState.value.copy(
                            lastError = err.localizedMessage ?: "Failed to save rotated FCM token"
                        )
                    }
            }
        }
    }

    suspend fun removeTokenBeforeSignOut(
        context: Context,
        repository: MessagingRepository
    ) {
        val appContext = context.applicationContext
        val deviceId = getOrCreateDeviceId(appContext)
        withContext(NonCancellable) {
            runCatching { repository.removeFcmToken(deviceId).getOrThrow() }
                .onFailure { Log.w(TAG, "Could not remove FCM token from Firestore before sign-out", it) }
            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
            saveCachedToken(appContext, null)
            _diagnosticState.value = FcmTokenDiagnosticState(deviceId = deviceId)
        }
    }
}
