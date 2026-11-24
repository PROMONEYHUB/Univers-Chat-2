package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Keeps the Firestore real-time stream connected so incoming messages trigger phone notifications
 * even when Univers Chat is moved to the background or the screen is off.
 */
class RealtimeMessagingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildLiveSyncNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        com.example.data.sync.UniversAppContainer.get(applicationContext)
            .syncCoordinator
            .startSession(userId, hasFirebaseAuth = true)
        return START_STICKY
    }

    companion object {
        fun syncServiceState(context: Context, shouldRun: Boolean) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, RealtimeMessagingService::class.java)
            if (shouldRun && NotificationHelper.hasNotificationPermission(appContext)) {
                try {
                    ContextCompat.startForegroundService(appContext, intent)
                } catch (_: Exception) {
                    // Fallback gracefully to in-process coordinator if OS restricts FGS start
                }
            } else {
                try {
                    appContext.stopService(intent)
                } catch (_: Exception) {
                    // Ignore if not running
                }
            }
        }
    }
}
