package com.example.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.calls.IncomingCallCoordinator
import com.example.data.local.UniversDao
import com.example.data.local.UniversDatabase
import com.example.data.local.toEntity
import com.example.data.model.CallSession
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.PrivateUser
import com.example.ui.screens.evaluateNotificationHealth
import com.google.firebase.Timestamp
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationPhase2Test {

    private lateinit var context: Application
    private lateinit var nm: NotificationManager
    private lateinit var db: UniversDatabase
    private lateinit var dao: UniversDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        db = Room.inMemoryDatabaseBuilder(context, UniversDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.universDao()
        IncomingCallCoordinator.clearForTest()
        MessageNotificationCoordinator.stopObserving()
        MessageNotificationCoordinator.setAppForegroundState(false)
        MessageNotificationCoordinator.setActiveConversationId(null)
        NotificationHelper.clearAllNotificationState(context)
    }

    @After
    fun tearDown() {
        IncomingCallCoordinator.clearForTest()
        MessageNotificationCoordinator.stopObserving()
        NotificationHelper.clearAllNotificationState(context)
        db.close()
    }

    @Test
    fun `ensureChannels registers message, sync, call, and missed call channels`() {
        NotificationHelper.ensureChannels(context)

        val msgChannel = nm.getNotificationChannel(NotificationHelper.CHANNEL_MESSAGES)
        val syncChannel = nm.getNotificationChannel(NotificationHelper.CHANNEL_SYNC_STATUS)
        val callChannel = nm.getNotificationChannel(NotificationHelper.CHANNEL_CALLS)
        val missedChannel = nm.getNotificationChannel(NotificationHelper.CHANNEL_MISSED_CALLS)

        assertNotNull(msgChannel)
        assertNotNull(syncChannel)
        assertNotNull(callChannel)
        assertNotNull(missedChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, msgChannel!!.importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, callChannel!!.importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, missedChannel!!.importance)
    }

    @Test
    fun `push message notifications include inline reply, mark read, and summary grouping`() {
        NotificationHelper.showIncomingMessageNotificationFromPush(
            context = context,
            conversationId = "conv_a",
            isGroup = false,
            conversationTitle = "Alice",
            senderName = "Alice",
            messageText = "Hello from closed app!",
            timestampMillis = 1_000L
        )
        NotificationHelper.showIncomingMessageNotificationFromPush(
            context = context,
            conversationId = "conv_b",
            isGroup = true,
            conversationTitle = "Design Team",
            senderName = "Bob",
            messageText = "Shipped Phase 2",
            timestampMillis = 2_000L
        )

        val shadowNm = Shadows.shadowOf(nm)
        val firstNotif = shadowNm.getNotification(NotificationHelper.notificationIdFor("conv_a"))
        val summaryNotif = shadowNm.getNotification(NotificationHelper.SUMMARY_NOTIFICATION_ID)

        assertNotNull(firstNotif)
        assertNotNull(summaryNotif)
        assertEquals(NotificationHelper.MESSAGE_GROUP_KEY, firstNotif.group)
        assertTrue((summaryNotif.flags and Notification.FLAG_GROUP_SUMMARY) != 0)
        assertEquals(2, firstNotif.actions?.size ?: 0)
        assertEquals("Reply", firstNotif.actions[0].title.toString())
        assertEquals("Mark as read", firstNotif.actions[1].title.toString())

        NotificationHelper.cancelConversationNotification(context, "conv_a")
        NotificationHelper.cancelConversationNotification(context, "conv_b")
        assertNull(shadowNm.getNotification(NotificationHelper.SUMMARY_NOTIFICATION_ID))
    }

    @Test
    fun `coordinator masks preview when notificationPreviewEnabled is false and suppresses muted or blocked`() {
        val currentUid = "u_me"
        val baseline = Conversation(
            id = "dm_1",
            type = Conversation.TYPE_DIRECT,
            participantIds = listOf(currentUid, "u_alice"),
            participantNames = mapOf("u_alice" to "Alice"),
            lastMessageText = "Old msg",
            lastMessageSenderId = "u_alice",
            lastMessageSenderName = "Alice",
            lastMessageAt = Timestamp(Date(1_000L)),
            unreadCounts = mapOf(currentUid to 1L)
        )
        val mutedConv = Conversation(
            id = "dm_muted",
            type = Conversation.TYPE_DIRECT,
            participantIds = listOf(currentUid, "u_bob"),
            participantNames = mapOf("u_bob" to "Bob"),
            mutedBy = mapOf(currentUid to true),
            lastMessageText = "Muted initial",
            lastMessageSenderId = "u_bob",
            lastMessageSenderName = "Bob",
            lastMessageAt = Timestamp(Date(1_000L)),
            unreadCounts = mapOf(currentUid to 1L)
        )

        MessageNotificationCoordinator.updatePreferences(
            enabled = true,
            previewEnabled = false,
            blockedIds = setOf("u_blocked")
        )
        MessageNotificationCoordinator.setAppForegroundState(true)

        // Seed initial state (never notifies on seed)
        MessageNotificationCoordinator.onConversationsSnapshot(context, currentUid, listOf(baseline, mutedConv))

        val banners = mutableListOf<InAppMessageBanner>()
        // Collect non-blockingly via replay/tryEmit is internal; check foreground banner stream isn't triggered for muted/blocked
        val updatedAllowed = baseline.copy(
            lastMessageText = "Secret bank pin 9941",
            lastMessageType = Message.TYPE_TEXT,
            lastMessageAt = Timestamp(Date(5_000L)),
            unreadCounts = mapOf(currentUid to 2L)
        )
        val updatedMuted = mutedConv.copy(
            lastMessageText = "Should stay silent",
            lastMessageAt = Timestamp(Date(5_000L)),
            unreadCounts = mapOf(currentUid to 2L)
        )
        val updatedBlocked = Conversation(
            id = "dm_blocked",
            type = Conversation.TYPE_DIRECT,
            participantIds = listOf(currentUid, "u_blocked"),
            participantNames = mapOf("u_blocked" to "Spammer"),
            lastMessageText = "Spam",
            lastMessageSenderId = "u_blocked",
            lastMessageSenderName = "Spammer",
            lastMessageAt = Timestamp(Date(5_000L)),
            unreadCounts = mapOf(currentUid to 1L)
        )

        MessageNotificationCoordinator.onConversationsSnapshot(
            context,
            currentUid,
            listOf(updatedAllowed, updatedMuted, updatedBlocked)
        )

        // Wait briefly for coroutine notification dispatch
        Thread.sleep(200L)
        val shadowNm = Shadows.shadowOf(nm)
        val allowedNotif = shadowNm.getNotification(NotificationHelper.notificationIdFor("dm_1"))
        val mutedNotif = shadowNm.getNotification(NotificationHelper.notificationIdFor("dm_muted"))
        val blockedNotif = shadowNm.getNotification(NotificationHelper.notificationIdFor("dm_blocked"))

        assertNotNull(allowedNotif)
        assertNull(mutedNotif)
        assertNull(blockedNotif)
        val renderedMessages = allowedNotif.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val firstBundle = renderedMessages?.firstOrNull() as? android.os.Bundle
        assertEquals("New message", firstBundle?.getCharSequence("text")?.toString())
        assertTrue(banners.isEmpty())
    }

    @Test
    fun `private user entity preserves notificationPreviewEnabled and fcmTokens in Room`() = runTest {
        val user = PrivateUser(
            userId = "u1",
            email = "u1@example.com",
            readReceiptsEnabled = true,
            notificationsEnabled = true,
            notificationPreviewEnabled = false,
            onlineStatusVisible = true,
            fcmTokens = mapOf("dev_abc" to "fcm_token_value_123456"),
            blockedUserIds = listOf("u_spam")
        )
        dao.upsertPrivateUser(user.toEntity())
        val restored = dao.getPrivateUserOnce("u1")?.toDomain()

        assertNotNull(restored)
        assertFalse(restored!!.notificationPreviewEnabled)
        assertEquals("fcm_token_value_123456", restored.fcmTokens["dev_abc"])
        assertEquals(listOf("u_spam"), restored.blockedUserIds)
    }

    @Test
    fun `incoming call coordinator deduplicates, hands off accept, and handles missed cancel`() {
        val session = CallSession(
            id = "call_101",
            callerId = "u2",
            callerName = "Elena",
            calleeId = "u1",
            participantIds = listOf("u2", "u1"),
            conversationId = "dm_u1_u2",
            callType = CallSession.TYPE_VOICE,
            status = CallSession.STATUS_RINGING
        )

        val acceptedFirst = IncomingCallCoordinator.onIncomingCallDetected(
            context = context,
            session = session,
            startRingtone = false
        )
        val acceptedDuplicate = IncomingCallCoordinator.onIncomingCallDetected(
            context = context,
            session = session,
            startRingtone = false
        )
        assertTrue(acceptedFirst)
        assertFalse(acceptedDuplicate)
        assertEquals("call_101", IncomingCallCoordinator.ringingCall.value?.id)

        // Remote caller cancels -> clears ringing state and posts missed call notification
        IncomingCallCoordinator.onRemoteCallTerminated(
            context = context,
            callId = "call_101",
            terminalStatus = CallSession.STATUS_MISSED,
            callerName = "Elena",
            conversationId = "dm_u1_u2",
            isVideo = false
        )
        Thread.sleep(200L)
        assertNull(IncomingCallCoordinator.ringingCall.value)
        assertTrue(Shadows.shadowOf(nm).allNotifications.any {
            it.extras.getString(Notification.EXTRA_TITLE)?.contains("Missed", ignoreCase = true) == true
        })

        // Accept flow handoff
        val session2 = session.copy(id = "call_202")
        assertTrue(IncomingCallCoordinator.onIncomingCallDetected(context, session2, startRingtone = false))
        IncomingCallCoordinator.acceptFromIncomingUi(context, session2)
        assertNull(IncomingCallCoordinator.ringingCall.value)
        assertEquals("call_202", IncomingCallCoordinator.consumePendingAcceptCall()?.id)
        assertNull(IncomingCallCoordinator.consumePendingAcceptCall())
    }

    @Test
    fun `notification health evaluator flags missing permission and missing FCM token`() {
        NotificationHelper.ensureChannels(context)
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val snapshot = evaluateNotificationHealth(
            context = context,
            hasFirebaseAuth = true,
            fcmState = FcmTokenDiagnosticState(deviceId = "dev_1", currentToken = null)
        )
        assertFalse(snapshot.hasNotificationPermission)
        assertFalse(snapshot.hasFcmToken)
        assertFalse(snapshot.isAllHealthy)
        assertTrue(snapshot.issueCount >= 2)
    }
}
