package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.calls.CallActionReceiver
import com.example.calls.IncomingCallActivity
import com.example.data.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class NotificationMessageEntry(
    val senderName: String,
    val text: String,
    val timestampMillis: Long,
    val avatarBitmap: Bitmap? = null
)

object NotificationHelper {

    const val CHANNEL_MESSAGES = "univers_chat_messages"
    const val CHANNEL_SYNC_STATUS = "univers_chat_live_sync"
    const val CHANNEL_CALLS = "univers_chat_calls"
    const val CHANNEL_MISSED_CALLS = "univers_chat_missed_calls"

    const val SERVICE_NOTIFICATION_ID = 9001
    const val SUMMARY_NOTIFICATION_ID = 9002
    const val INCOMING_CALL_NOTIFICATION_ID = 9101
    private const val MISSED_CALL_NOTIFICATION_BASE = 9200

    const val MESSAGE_GROUP_KEY = "univers_chat_messages_group"

    const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_ACCEPT_CALL_ID = "extra_accept_call_id"
    const val KEY_INLINE_REPLY = "key_inline_reply"
    const val ACTION_INLINE_REPLY = "com.example.notifications.ACTION_INLINE_REPLY"
    const val ACTION_MARK_READ = "com.example.notifications.ACTION_MARK_READ"

    private const val MAX_HISTORY_PER_CHAT = 6
    private val chatMessageBuffers = ConcurrentHashMap<String, MutableList<NotificationMessageEntry>>()
    private val activeChatTitles = ConcurrentHashMap<String, String>()
    private val avatarCache = ConcurrentHashMap<String, Bitmap>()

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun canUseFullScreenIntentCompat(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return manager.canUseFullScreenIntent()
    }

    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time direct and group message alerts"
            enableVibration(true)
            enableLights(true)
            lightColor = Color.rgb(0, 122, 255)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setSound(soundUri, audioAttributes)
        }

        val syncChannel = NotificationChannel(
            CHANNEL_SYNC_STATUS,
            "Real-time delivery status",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps real-time Univers Chat message alerts active"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val callRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val callAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            "HD Voice & Video Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming full-screen HD voice and video call alerts"
            enableVibration(true)
            enableLights(true)
            lightColor = Color.rgb(52, 199, 89)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setSound(callRingtoneUri, callAudioAttributes)
        }

        val missedCallsChannel = NotificationChannel(
            CHANNEL_MISSED_CALLS,
            "Missed Calls",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts for unanswered voice and video calls"
            enableVibration(true)
            setShowBadge(true)
            setSound(soundUri, audioAttributes)
        }

        manager.createNotificationChannels(
            listOf(messageChannel, syncChannel, callsChannel, missedCallsChannel)
        )
    }

    fun buildLiveSyncNotification(context: Context): Notification {
        ensureChannels(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_SYNC_STATUS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Connected for real-time messages")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .build()
    }

    fun showIncomingMessageNotification(
        context: Context,
        conversation: Conversation,
        conversationTitle: String,
        senderName: String,
        messageText: String,
        timestampMillis: Long,
        senderAvatarBitmap: Bitmap? = null
    ) {
        showIncomingMessageNotificationInternal(
            context = context,
            conversationId = conversation.id,
            isGroup = conversation.type == Conversation.TYPE_GROUP,
            conversationTitle = conversationTitle,
            senderName = senderName,
            messageText = messageText,
            timestampMillis = timestampMillis,
            senderAvatarBitmap = senderAvatarBitmap
        )
    }

    fun showIncomingMessageNotificationFromPush(
        context: Context,
        conversationId: String,
        isGroup: Boolean,
        conversationTitle: String,
        senderName: String,
        messageText: String,
        timestampMillis: Long,
        senderAvatarBitmap: Bitmap? = null
    ) {
        showIncomingMessageNotificationInternal(
            context = context,
            conversationId = conversationId,
            isGroup = isGroup,
            conversationTitle = conversationTitle,
            senderName = senderName,
            messageText = messageText,
            timestampMillis = timestampMillis,
            senderAvatarBitmap = senderAvatarBitmap
        )
    }

    private fun showIncomingMessageNotificationInternal(
        context: Context,
        conversationId: String,
        isGroup: Boolean,
        conversationTitle: String,
        senderName: String,
        messageText: String,
        timestampMillis: Long,
        senderAvatarBitmap: Bitmap?
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val notifId = notificationIdFor(conversationId)
        val cleanTitle = conversationTitle.ifBlank { senderName.ifBlank { "Univers Chat" } }
        val cleanSender = senderName.ifBlank { cleanTitle }

        val history = chatMessageBuffers.getOrPut(conversationId) { mutableListOf() }
        synchronized(history) {
            val duplicate = history.lastOrNull()?.let {
                it.timestampMillis == timestampMillis && it.text == messageText && it.senderName == cleanSender
            } == true
            if (!duplicate) {
                history.add(
                    NotificationMessageEntry(
                        senderName = cleanSender,
                        text = messageText,
                        timestampMillis = timestampMillis,
                        avatarBitmap = senderAvatarBitmap
                    )
                )
                while (history.size > MAX_HISTORY_PER_CHAT) {
                    history.removeAt(0)
                }
            }
        }
        activeChatTitles[conversationId] = cleanTitle

        val openChatIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openChatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyIntent = Intent(context, MessageNotificationActionReceiver::class.java).apply {
            action = ACTION_INLINE_REPLY
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 10_000,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_INLINE_REPLY)
            .setLabel("Reply to $cleanTitle")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val markReadIntent = Intent(context, MessageNotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 20_000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as read",
            markReadPendingIntent
        ).build()

        val selfPerson = Person.Builder().setName("You").build()
        val messagingStyle = NotificationCompat.MessagingStyle(selfPerson)
            .setGroupConversation(isGroup)
            .also { style ->
                if (isGroup) {
                    style.setConversationTitle(cleanTitle)
                }
            }

        val entriesSnapshot = synchronized(history) { history.toList() }
        entriesSnapshot.forEach { entry ->
            val personBuilder = Person.Builder().setName(entry.senderName)
            if (entry.avatarBitmap != null) {
                personBuilder.setIcon(IconCompat.createWithBitmap(entry.avatarBitmap))
            }
            messagingStyle.addMessage(entry.text, entry.timestampMillis, personBuilder.build())
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setColor(Color.rgb(0, 122, 255))
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(timestampMillis)
            .setShowWhen(true)
            .setGroup(MESSAGE_GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)

        if (senderAvatarBitmap != null) {
            builder.setLargeIcon(senderAvatarBitmap)
        }

        val nm = NotificationManagerCompat.from(context)
        try {
            nm.notify(notifId, builder.build())

            if (activeChatTitles.size > 1) {
                val inboxStyle = NotificationCompat.InboxStyle()
                    .setBigContentTitle("${activeChatTitles.size} active chats")
                activeChatTitles.values.take(6).forEach { title ->
                    inboxStyle.addLine(title)
                }
                val summaryNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText("${activeChatTitles.size} unread conversations")
                    .setStyle(inboxStyle)
                    .setGroup(MESSAGE_GROUP_KEY)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .setAutoCancel(true)
                    .build()
                nm.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
            }
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun buildIncomingCallNotification(
        context: Context,
        callId: String,
        callerName: String,
        isVideo: Boolean,
        callerAvatarBitmap: Bitmap? = null
    ): Notification {
        ensureChannels(context)

        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            INCOMING_CALL_NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACCEPT_CALL_ID, callId)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            INCOMING_CALL_NOTIFICATION_ID + 1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            INCOMING_CALL_NOTIFICATION_ID + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val personBuilder = Person.Builder()
            .setName(callerName.ifBlank { "Member" })
            .setImportant(true)
        if (callerAvatarBitmap != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(callerAvatarBitmap))
        }
        val callerPerson = personBuilder.build()

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            answerPendingIntent
        ).setIsVideo(isVideo)

        return NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setColor(Color.rgb(52, 199, 89))
            .setContentTitle(callerName.ifBlank { "Member" })
            .setContentText(
                if (isVideo) "Incoming HD Video Call" else "Incoming HD Voice Call"
            )
            .setStyle(callStyle)
            .addPerson(callerPerson)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(30_000L)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }

    fun showMissedCallNotification(
        context: Context,
        callId: String,
        callerName: String,
        conversationId: String?,
        isVideo: Boolean,
        callerAvatarBitmap: Bitmap? = null
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!conversationId.isNullOrBlank()) {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
        }
        val notifId = MISSED_CALL_NOTIFICATION_BASE + ((callId.hashCode() and 0x0FFF))
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MISSED_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setColor(Color.rgb(255, 59, 48))
            .setContentTitle("Missed ${if (isVideo) "video" else "voice"} call")
            .setContentText("${callerName.ifBlank { "Member" }} tried to call you")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (callerAvatarBitmap != null) {
            builder.setLargeIcon(callerAvatarBitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelIncomingCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    fun cancelConversationNotification(context: Context, conversationId: String) {
        chatMessageBuffers.remove(conversationId)
        activeChatTitles.remove(conversationId)
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(notificationIdFor(conversationId))
        if (activeChatTitles.isEmpty()) {
            nm.cancel(SUMMARY_NOTIFICATION_ID)
        }
    }

    fun clearAllNotificationState(context: Context) {
        chatMessageBuffers.clear()
        activeChatTitles.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun notificationIdFor(conversationId: String): Int {
        return (conversationId.hashCode() and 0x0FFFFFFF).coerceAtLeast(1000)
    }

    suspend fun loadCircularAvatarBitmap(urlOrBase64: String?): Bitmap? {
        if (urlOrBase64.isNullOrBlank()) return null
        avatarCache[urlOrBase64]?.let { return it }

        val rawBitmap = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    urlOrBase64.startsWith("data:image", ignoreCase = true) -> {
                        val b64 = urlOrBase64.substringAfter("base64,", "")
                        if (b64.isBlank()) null
                        else {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                    urlOrBase64.startsWith("http://", ignoreCase = true) ||
                        urlOrBase64.startsWith("https://", ignoreCase = true) -> {
                        val conn = (URL(urlOrBase64).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 2500
                            readTimeout = 2500
                            doInput = true
                        }
                        try {
                            if (conn.responseCode in 200..299) {
                                conn.inputStream.use { BitmapFactory.decodeStream(it) }
                            } else {
                                null
                            }
                        } finally {
                            conn.disconnect()
                        }
                    }
                    else -> null
                }
            }.getOrNull()
        } ?: return null

        val circular = toCircularBitmap(rawBitmap, 128)
        if (avatarCache.size > 48) {
            avatarCache.clear()
        }
        avatarCache[urlOrBase64] = circular
        return circular
    }

    private fun toCircularBitmap(source: Bitmap, targetPx: Int): Bitmap {
        val scaled = if (source.width != targetPx || source.height != targetPx) {
            Bitmap.createScaledBitmap(source, targetPx, targetPx, true)
        } else {
            source
        }
        val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = targetPx / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        return output
    }
}
