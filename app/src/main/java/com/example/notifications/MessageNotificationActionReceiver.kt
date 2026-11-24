package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.data.repository.MessagingRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Handles Direct Inline Reply (RemoteInput) and Mark-as-Read directly from phone notifications.
 */
class MessageNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_ID) ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val auth = Firebase.auth
                val currentUser = auth.currentUser ?: return@launch
                val db = FirebaseFirestore.getInstance(
                    context.applicationContext.getString(R.string.firestore_database_id)
                )
                val repository = MessagingRepository(db, auth)

                val convSnap = db.collection(MessagingRepository.CONVERSATIONS_COLLECTION)
                    .document(conversationId)
                    .get()
                    .await()
                val conversation = convSnap.toObject(Conversation::class.java) ?: return@launch

                when (action) {
                    NotificationHelper.ACTION_INLINE_REPLY -> {
                        val replyText = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(NotificationHelper.KEY_INLINE_REPLY)
                            ?.toString()
                            ?.trim()
                            .orEmpty()

                        if (replyText.isNotEmpty()) {
                            val profileSnap = db.collection(MessagingRepository.USER_PROFILES_COLLECTION)
                                .document(currentUser.uid)
                                .get()
                                .await()
                            val profile = profileSnap.toObject(UserProfile::class.java)
                                ?: UserProfile(
                                    userId = currentUser.uid,
                                    displayName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Univers Member"
                                )
                            repository.sendTextMessage(conversation, profile, replyText)
                            repository.markConversationRead(conversation)
                        }
                        NotificationHelper.cancelConversationNotification(context, conversationId)
                    }

                    NotificationHelper.ACTION_MARK_READ -> {
                        repository.markConversationRead(conversation)
                        NotificationHelper.cancelConversationNotification(context, conversationId)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MsgNotifActionRx", "Failed to handle notification action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
