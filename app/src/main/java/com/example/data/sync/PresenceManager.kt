package com.example.data.sync

import android.util.Log
import com.example.data.model.UserProfile
import com.example.data.repository.MessagingRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages accurate real-time foreground presence, 25-second heartbeats,
 * immediate offline transitions on background / connectivity loss / privacy disable,
 * and awaited offline writes before sign-out.
 */
class PresenceManager(
    private val repository: MessagingRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    @Volatile
    private var activeUserId: String? = null

    @Volatile
    private var hasFirebaseAuth: Boolean = false

    @Volatile
    private var isForeground: Boolean = false

    @Volatile
    private var onlineStatusVisible: Boolean = true

    @Volatile
    private var isCurrentlyPublishedOnline: Boolean = false

    private var heartbeatJob: Job? = null

    init {
        scope.launch {
            connectivityMonitor.isConnected.collect {
                reconcilePresenceState()
            }
        }
    }

    fun updateSession(userId: String?, hasFirebaseAuth: Boolean) {
        scope.launch {
            stateMutex.withLock {
                this@PresenceManager.activeUserId = userId
                this@PresenceManager.hasFirebaseAuth = hasFirebaseAuth
                if (!hasFirebaseAuth || userId == null) {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    isCurrentlyPublishedOnline = false
                }
            }
            reconcilePresenceState()
        }
    }

    fun setForeground(inForeground: Boolean) {
        scope.launch {
            stateMutex.withLock {
                isForeground = inForeground
            }
            reconcilePresenceState()
        }
    }

    fun setOnlineStatusVisible(visible: Boolean) {
        scope.launch {
            stateMutex.withLock {
                onlineStatusVisible = visible
            }
            reconcilePresenceState()
        }
    }

    /**
     * Must be awaited BEFORE Firebase.auth.signOut() so the user is marked offline
     * while credentials are still valid.
     */
    suspend fun markOfflineBeforeSignOut() {
        val shouldWriteOffline: Boolean
        stateMutex.withLock {
            shouldWriteOffline = hasFirebaseAuth && activeUserId != null && auth.currentUser != null
            heartbeatJob?.cancel()
            heartbeatJob = null
            isForeground = false
            isCurrentlyPublishedOnline = false
            activeUserId = null
            hasFirebaseAuth = false
        }
        if (shouldWriteOffline) {
            withContext(NonCancellable + Dispatchers.IO) {
                withTimeoutOrNull(SIGN_OUT_PRESENCE_TIMEOUT_MS) {
                    repository.setOnlinePresence(false).onFailure { err ->
                        Log.w(TAG, "Offline write before sign-out failed: ${err.message}")
                    }
                }
            }
        }
    }

    private suspend fun reconcilePresenceState() {
        val uid: String?
        val shouldBeOnline: Boolean
        val wasPublishedOnline: Boolean

        stateMutex.withLock {
            uid = activeUserId
            val networkAvailable = connectivityMonitor.isConnected.value
            shouldBeOnline = uid != null &&
                hasFirebaseAuth &&
                auth.currentUser?.uid == uid &&
                isForeground &&
                onlineStatusVisible &&
                networkAvailable
            wasPublishedOnline = isCurrentlyPublishedOnline

            if (shouldBeOnline) {
                if (heartbeatJob?.isActive != true) {
                    isCurrentlyPublishedOnline = true
                    heartbeatJob = scope.launch {
                        runHeartbeatLoop(uid!!)
                    }
                }
                return
            } else {
                heartbeatJob?.cancel()
                heartbeatJob = null
                isCurrentlyPublishedOnline = false
            }
        }

        if (wasPublishedOnline && uid != null && hasFirebaseAuth && auth.currentUser?.uid == uid) {
            try {
                withTimeoutOrNull(2_500L) {
                    repository.setOnlinePresence(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Offline transition write skipped: ${e.message}")
            }
        }
    }

    private suspend fun runHeartbeatLoop(expectedUid: String) {
        while (scope.isActive) {
            val stillValid = activeUserId == expectedUid &&
                hasFirebaseAuth &&
                auth.currentUser?.uid == expectedUid &&
                isForeground &&
                onlineStatusVisible &&
                connectivityMonitor.isConnected.value
            if (!stillValid) break

            repository.sendPresenceHeartbeat().onFailure { err ->
                Log.d(TAG, "Heartbeat skipped: ${err.message}")
            }
            delay(UserProfile.HEARTBEAT_INTERVAL_MS)
        }
    }

    private companion object {
        const val TAG = "PresenceManager"
        const val SIGN_OUT_PRESENCE_TIMEOUT_MS = 2_000L
    }
}
