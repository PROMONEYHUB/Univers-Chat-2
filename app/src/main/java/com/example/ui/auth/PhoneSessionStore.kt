package com.example.ui.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.phoneSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "univers_phone_auth_session"
)

data class PhoneLoginSession(
    val phoneNumberE164: String,
    val displayName: String,
    val handle: String,
    val bio: String,
    val statusVibe: String,
    val isOnline: Boolean,
    val isDiscoverable: Boolean,
    val loggedInAtMillis: Long
) {
    val userId: String
        get() = "phone_${phoneNumberE164.filter { it.isDigit() }}"
}

sealed interface PhoneSessionLoadState {
    data object Loading : PhoneSessionLoadState
    data class Ready(val session: PhoneLoginSession?) : PhoneSessionLoadState
}

object PhoneSessionStore {
    private val KEY_PHONE_E164 = stringPreferencesKey("logged_in_phone_e164")
    private val KEY_DISPLAY_NAME = stringPreferencesKey("logged_in_display_name")
    private val KEY_HANDLE = stringPreferencesKey("logged_in_handle")
    private val KEY_BIO = stringPreferencesKey("logged_in_bio")
    private val KEY_STATUS_VIBE = stringPreferencesKey("logged_in_status_vibe")
    private val KEY_IS_ONLINE = booleanPreferencesKey("logged_in_is_online")
    private val KEY_IS_DISCOVERABLE = booleanPreferencesKey("logged_in_is_discoverable")
    private val KEY_LOGGED_IN_AT = longPreferencesKey("logged_in_at_millis")

    fun sessionStateFlow(context: Context): Flow<PhoneSessionLoadState> {
        val appContext = context.applicationContext
        return appContext.phoneSessionDataStore.data.map { prefs ->
            val phone = prefs[KEY_PHONE_E164].orEmpty().trim()
            if (phone.isEmpty()) {
                PhoneSessionLoadState.Ready(null)
            } else {
                val digits = phone.filter { it.isDigit() }
                val fallbackName = "Member ${digits.takeLast(4).ifBlank { "User" }}"
                val storedName = prefs[KEY_DISPLAY_NAME].orEmpty().trim().ifBlank { fallbackName }
                val defaultHandle = storedName.lowercase()
                    .replace(Regex("[^a-z0-9_]"), "")
                    .ifBlank { "user_${digits.takeLast(6)}" }
                    .take(28)
                val storedHandle = prefs[KEY_HANDLE].orEmpty().trim().ifBlank { defaultHandle }
                PhoneSessionLoadState.Ready(
                    PhoneLoginSession(
                        phoneNumberE164 = phone,
                        displayName = storedName,
                        handle = storedHandle,
                        bio = prefs[KEY_BIO] ?: "Hey there! I'm on Univers Chat.",
                        statusVibe = prefs[KEY_STATUS_VIBE] ?: "Available",
                        isOnline = prefs[KEY_IS_ONLINE] ?: true,
                        isDiscoverable = prefs[KEY_IS_DISCOVERABLE] ?: true,
                        loggedInAtMillis = prefs[KEY_LOGGED_IN_AT] ?: 0L
                    )
                )
            }
        }
    }

    suspend fun saveLoggedInPhone(
        context: Context,
        phoneNumberE164: String,
        displayName: String?
    ) {
        val appContext = context.applicationContext
        val digits = phoneNumberE164.filter { it.isDigit() }
        val cleanName = displayName?.trim()?.take(60)?.takeIf { it.isNotBlank() }
            ?: "Member ${digits.takeLast(4).ifBlank { "User" }}"
        val cleanHandle = cleanName.lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .ifBlank { "user_${digits.takeLast(6)}" }
            .take(28)
            .let { if (it.length < 2) "u_${digits.takeLast(6)}" else it }

        appContext.phoneSessionDataStore.edit { prefs ->
            prefs[KEY_PHONE_E164] = phoneNumberE164.trim()
            prefs[KEY_DISPLAY_NAME] = cleanName
            prefs[KEY_HANDLE] = cleanHandle
            prefs[KEY_BIO] = "Hey there! I'm on Univers Chat."
            prefs[KEY_STATUS_VIBE] = "Available"
            prefs[KEY_IS_ONLINE] = true
            prefs[KEY_IS_DISCOVERABLE] = true
            prefs[KEY_LOGGED_IN_AT] = System.currentTimeMillis()
        }
    }

    suspend fun updateLocalProfile(
        context: Context,
        displayName: String,
        handle: String,
        bio: String,
        statusVibe: String,
        isOnline: Boolean,
        isDiscoverable: Boolean
    ) {
        val appContext = context.applicationContext
        appContext.phoneSessionDataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = displayName.trim().take(60)
            prefs[KEY_HANDLE] = handle.trim().lowercase().replace(Regex("[^a-z0-9_]"), "").take(32)
            prefs[KEY_BIO] = bio.trim().take(160)
            prefs[KEY_STATUS_VIBE] = statusVibe.trim().take(60)
            prefs[KEY_IS_ONLINE] = isOnline
            prefs[KEY_IS_DISCOVERABLE] = isDiscoverable
        }
    }

    suspend fun clearSession(context: Context) {
        val appContext = context.applicationContext
        appContext.phoneSessionDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
