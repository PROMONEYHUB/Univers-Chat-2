package com.example.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ChatModelsPhase1Test {

    private fun ts(millis: Long): Timestamp = Timestamp(Date(millis))

    @Test
    fun `isEffectivelyOnline respects online flag, 60s heartbeat expiry, and bidirectional privacy`() {
        val now = 1_700_000_100_000L
        val freshProfile = UserProfile(
            userId = "u2",
            displayName = "Alice",
            isOnline = true,
            onlineStatusVisible = true,
            lastHeartbeatAt = ts(now - 25_000L),
            lastSeenAt = ts(now - 25_000L)
        )

        assertTrue(freshProfile.isEffectivelyOnline(nowMillis = now, viewerShowsOnline = true))
        assertNotNull(freshProfile.visibleLastSeen(viewerShowsOnline = true))

        // Viewer hides online status -> hidden bidirectionally
        assertFalse(freshProfile.isEffectivelyOnline(nowMillis = now, viewerShowsOnline = false))
        assertNull(freshProfile.visibleLastSeen(viewerShowsOnline = false))

        // Target hides online status -> hidden bidirectionally
        val hiddenTarget = freshProfile.copy(onlineStatusVisible = false)
        assertFalse(hiddenTarget.isEffectivelyOnline(nowMillis = now, viewerShowsOnline = true))
        assertNull(hiddenTarget.visibleLastSeen(viewerShowsOnline = true))

        // Backgrounded / signed out -> false
        assertFalse(freshProfile.copy(isOnline = false).isEffectivelyOnline(nowMillis = now, viewerShowsOnline = true))

        // Heartbeat older than 60 seconds -> stale offline even if isOnline flag stuck true
        val staleProfile = freshProfile.copy(
            lastHeartbeatAt = ts(now - UserProfile.HEARTBEAT_STALE_MS - 1L),
            lastSeenAt = ts(now - UserProfile.HEARTBEAT_STALE_MS - 1L)
        )
        assertFalse(staleProfile.isEffectivelyOnline(nowMillis = now, viewerShowsOnline = true))

        // Boundary at exactly 60 seconds -> still valid
        val boundaryProfile = freshProfile.copy(
            lastHeartbeatAt = ts(now - UserProfile.HEARTBEAT_STALE_MS)
        )
        assertTrue(boundaryProfile.isEffectivelyOnline(nowMillis = now, viewerShowsOnline = true))
    }

    @Test
    fun `typingOrRecordingLabel prioritizes recording, ignores self, and expires after 4 seconds`() {
        val now = 1_700_000_200_000L

        val dmConversation = Conversation(
            id = "dm_u1_u2",
            type = Conversation.TYPE_DIRECT,
            participantIds = listOf("u1", "u2"),
            participantNames = mapOf("u1" to "Me", "u2" to "Alice Doe"),
            typingBy = mapOf(
                "u1" to ts(now - 500L), // self ignored
                "u2" to ts(now - 1_500L)
            )
        )
        assertEquals("typing…", dmConversation.typingOrRecordingLabel("u1", nowMillis = now))

        // Recording overrides typing
        val dmRecording = dmConversation.copy(
            recordingBy = mapOf("u2" to ts(now - 1_000L))
        )
        assertEquals("recording voice…", dmRecording.typingOrRecordingLabel("u1", nowMillis = now))

        // Expired after 4000ms
        val expiredDm = dmRecording.copy(
            typingBy = mapOf("u2" to ts(now - Conversation.INDICATOR_EXPIRY_MS - 1L)),
            recordingBy = mapOf("u2" to ts(now - Conversation.INDICATOR_EXPIRY_MS - 1L))
        )
        assertNull(expiredDm.typingOrRecordingLabel("u1", nowMillis = now))

        // Group chat formatting (single & multiple)
        val groupSingle = Conversation(
            id = "grp_1",
            type = Conversation.TYPE_GROUP,
            participantIds = listOf("u1", "u2", "u3"),
            participantNames = mapOf("u2" to "Alice Doe", "u3" to "Bob Smith"),
            typingBy = mapOf("u2" to ts(now - 800L))
        )
        assertEquals("Alice is typing…", groupSingle.typingOrRecordingLabel("u1", nowMillis = now))

        val groupMultiple = groupSingle.copy(
            typingBy = mapOf(
                "u2" to ts(now - 800L),
                "u3" to ts(now - 500L)
            )
        )
        assertEquals("2 people typing…", groupMultiple.typingOrRecordingLabel("u1", nowMillis = now))
    }

    @Test
    fun `resolveOutgoingTickState covers all 5 states, group receipts, and read receipt privacy`() {
        val now = 1_700_000_300_000L
        val participants = listOf("u1", "u2")

        // Sending & Failed take precedence
        val sending = Message(
            id = "m1",
            senderId = "u1",
            participantIds = participants,
            deliveryState = Message.STATUS_SENDING
        )
        assertEquals(MessageTickState.SENDING, sending.resolveOutgoingTickState("u1", participants, true))

        val failed = sending.copy(deliveryState = Message.STATUS_FAILED)
        assertEquals(MessageTickState.FAILED, failed.resolveOutgoingTickState("u1", participants, true))

        // Sent (neither delivered nor read)
        val sent = sending.copy(deliveryState = Message.STATUS_SENT)
        assertEquals(MessageTickState.SENT, sent.resolveOutgoingTickState("u1", participants, true))

        // Delivered
        val delivered = sent.copy(deliveredTo = mapOf("u2" to ts(now)))
        assertEquals(MessageTickState.DELIVERED, delivered.resolveOutgoingTickState("u1", participants, true))

        // Read in DM
        val read = delivered.copy(readBy = mapOf("u2" to ts(now)))
        assertEquals(MessageTickState.READ, read.resolveOutgoingTickState("u1", participants, true))

        // When viewer disables read receipts, READ downgrades to DELIVERED
        assertEquals(MessageTickState.DELIVERED, read.resolveOutgoingTickState("u1", participants, showReadReceipts = false))

        // Group chat requires ALL recipients to read for READ, ANY for DELIVERED
        val groupParticipants = listOf("u1", "u2", "u3")
        val groupPartialRead = sent.copy(
            participantIds = groupParticipants,
            deliveredTo = mapOf("u2" to ts(now)),
            readBy = mapOf("u2" to ts(now))
        )
        assertEquals(
            MessageTickState.DELIVERED,
            groupPartialRead.resolveOutgoingTickState("u1", groupParticipants, showReadReceipts = true)
        )

        val groupAllRead = groupPartialRead.copy(
            deliveredTo = mapOf("u2" to ts(now), "u3" to ts(now)),
            readBy = mapOf("u2" to ts(now), "u3" to ts(now))
        )
        assertEquals(
            MessageTickState.READ,
            groupAllRead.resolveOutgoingTickState("u1", groupParticipants, showReadReceipts = true)
        )
    }
}
