package com.example.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.repository.CallSignalingRepository
import com.example.notifications.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles Decline actions directly from the CallStyle incoming call notification.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE_CALL) return
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: return
        val appContext = context.applicationContext

        IncomingCallCoordinator.declineFromIncomingUi(appContext, callId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (runCatching { Firebase.auth.currentUser }.getOrNull() != null) {
                    CallSignalingRepository(appContext).declineCall(callId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decline call $callId from notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_DECLINE_CALL = "com.example.calls.ACTION_DECLINE_CALL"
    }
}
