package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageTickState
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UniversDaoRobolectricTest {

    private lateinit var db: UniversDatabase
    private lateinit var dao: UniversDao

    private val ownerId = "u1"
    private val convId = "dm_u1_u2"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UniversDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.universDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ts(millis: Long): Timestamp = Timestamp(Date(millis))

    @Test
    fun `observeConversations and observeMessages emit cached entries in order`() = runTest {
        val conv1 = Conversation(
            id = "dm_u1_u2",
            type = Conversation.TYPE_DIRECT,
            participantIds = listOf("u1", "u2"),
            participantNames = mapOf("u2" to "Alice"),
            lastMessageText = "Hey",
            lastMessageSenderId = "u1",
            lastMessageAt = ts(2_000L),
            lastOutgoingTickState = MessageTickState.DELIVERED
        ).toEntity(ownerId)

        val conv2 = Conversation(
            id = "grp_1",
            type = Conversation.TYPE_GROUP,
            title = "Crew",
            participantIds = listOf("u1", "u2", "u3"),
            lastMessageText = "Yo",
            lastMessageSenderId = "u3",
            lastMessageAt = ts(1_000L)
        ).toEntity(ownerId)

        dao.upsertConversations(listOf(conv1, conv2))
        val cachedConvs = dao.observeConversations(ownerId).first()
        assertEquals(2, cachedConvs.size)
        assertEquals(MessageTickState.DELIVERED, cachedConvs.first { it.id == "dm_u1_u2" }.toDomain().lastOutgoingTickState)

        // Messages inserted out of order should emit ordered ascending by createdAtMillis
        val m2 = Message(
            id = "m2",
            conversationId = convId,
            senderId = "u2",
            text = "Second",
            createdAt = ts(2_000L),
            updatedAt = ts(2_000L),
            deliveryState = Message.STATUS_SENT
        ).toEntity(ownerId)
        val m1 = Message(
            id = "m1",
            conversationId = convId,
            senderId = "u1",
            text = "First",
            createdAt = ts(1_000L),
            updatedAt = ts(1_000L),
            deliveryState = Message.STATUS_SENT
        ).toEntity(ownerId)

        dao.upsertMessages(listOf(m2, m1))
        val ordered = dao.observeMessages(ownerId, convId).first()
        assertEquals(listOf("m1", "m2"), ordered.map { it.id })
    }

    @Test
    fun `reconcileLatestMessagesWindow preserves older history and pending or failed local rows`() = runTest {
        val olderPaginated = Message(
            id = "m_old",
            conversationId = convId,
            senderId = "u2",
            text = "Older history",
            createdAt = ts(500L),
            updatedAt = ts(500L),
            deliveryState = Message.STATUS_SENT
        ).toEntity(ownerId)

        val windowKept = Message(
            id = "m_kept",
            conversationId = convId,
            senderId = "u1",
            text = "Kept",
            createdAt = ts(2_000L),
            updatedAt = ts(2_000L),
            deliveryState = Message.STATUS_SENT
        ).toEntity(ownerId)

        val windowDeletedOnServer = Message(
            id = "m_deleted",
            conversationId = convId,
            senderId = "u2",
            text = "Deleted remotely",
            createdAt = ts(2_500L),
            updatedAt = ts(2_500L),
            deliveryState = Message.STATUS_SENT
        ).toEntity(ownerId)

        val localSending = Message(
            id = "c_sending",
            clientMessageId = "c_sending",
            conversationId = convId,
            senderId = "u1",
            text = "Still sending",
            createdAt = ts(3_000L),
            updatedAt = ts(3_000L),
            deliveryState = Message.STATUS_SENDING
        ).toEntity(ownerId)

        val localFailed = Message(
            id = "c_failed",
            clientMessageId = "c_failed",
            conversationId = convId,
            senderId = "u1",
            text = "Failed locally",
            createdAt = ts(3_500L),
            updatedAt = ts(3_500L),
            deliveryState = Message.STATUS_FAILED
        ).toEntity(ownerId)

        dao.upsertMessages(listOf(olderPaginated, windowKept, windowDeletedOnServer, localSending, localFailed))

        val remoteNew = Message(
            id = "m_new",
            conversationId = convId,
            senderId = "u2",
            text = "Newest",
            createdAt = ts(4_000L),
            updatedAt = ts(4_000L),
            deliveryState = Message.STATUS_SENT
        ).toEntity(ownerId)

        // Reconcile window starting at m_kept (2000ms), NOT a full snapshot:
        // m_old (500ms) is outside the window and kept;
        // m_deleted (2500ms) is inside the window and absent -> removed;
        // c_sending and c_failed remain intact.
        dao.reconcileLatestMessagesWindow(
            ownerUserId = ownerId,
            conversationId = convId,
            remoteMessages = listOf(windowKept, remoteNew),
            isFullSnapshot = false
        )

        val afterIds = dao.getMessagesOnce(ownerId, convId).map { it.id }
        assertEquals(listOf("m_old", "m_kept", "c_sending", "c_failed", "m_new"), afterIds)
    }

    @Test
    fun `outbox lifecycle tracks pending count, failure state, and sent completion`() = runTest {
        val msgId = "local_1"
        val outboxItem = OutboxMessageEntity(
            clientMessageId = msgId,
            ownerUserId = ownerId,
            conversationId = convId,
            senderId = ownerId,
            senderName = "Me",
            senderPhotoUrl = "",
            participantIdsJson = "[\"u1\",\"u2\"]",
            type = Message.TYPE_TEXT,
            text = "Offline hello",
            previewText = "Offline hello",
            imageBase64 = null,
            audioBase64 = null,
            audioDurationSec = null,
            isForwarded = false,
            replyToMessageId = null,
            replyToSenderName = null,
            replyToPreview = null,
            pollQuestion = null,
            pollOptionsJson = null,
            retryCount = 0,
            nextRetryAtMillis = 0L,
            createdAtMillis = 1_000L,
            status = OutboxMessageEntity.STATUS_PENDING,
            lastError = null
        )
        val localMessage = Message(
            id = msgId,
            clientMessageId = msgId,
            conversationId = convId,
            senderId = ownerId,
            text = "Offline hello",
            createdAt = ts(1_000L),
            updatedAt = ts(1_000L),
            deliveryState = Message.STATUS_SENDING
        ).toEntity(ownerId)

        dao.upsertOutbox(outboxItem)
        dao.upsertMessage(localMessage)
        assertEquals(1, dao.observePendingOutboxCount(ownerId).first())

        // Mark failed -> outbox status becomes failed, pending count becomes 0, message becomes failed
        dao.markOutboxMessageFailed(msgId, "Max retries reached")
        assertEquals(0, dao.observePendingOutboxCount(ownerId).first())
        assertEquals(OutboxMessageEntity.STATUS_FAILED, dao.getOutboxById(msgId)?.status)
        assertEquals("Max retries reached", dao.getOutboxById(msgId)?.lastError)
        assertEquals(Message.STATUS_FAILED, dao.getMessageById(msgId)?.deliveryState)

        // Re-enqueue for retry -> pending count back to 1
        dao.upsertOutbox(outboxItem.copy(retryCount = 1))
        dao.updateMessageDeliveryState(msgId, Message.STATUS_SENDING)
        assertEquals(1, dao.observePendingOutboxCount(ownerId).first())

        // Mark sent locally -> outbox entry deleted, message state becomes sent
        dao.markMessageAsSentLocally(ownerId, msgId, sentAtMillis = 5_000L)
        assertEquals(0, dao.observePendingOutboxCount(ownerId).first())
        assertNull(dao.getOutboxById(msgId))
        val updatedMsg = dao.getMessageById(msgId)
        assertNotNull(updatedMsg)
        assertTrue(updatedMsg?.deliveryState == Message.STATUS_SENT)
        assertEquals(5_000L, updatedMsg?.updatedAtMillis)
    }
}
