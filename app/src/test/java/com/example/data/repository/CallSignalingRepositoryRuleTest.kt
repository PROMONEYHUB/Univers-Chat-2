package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CallQualityPreset
import com.example.data.model.CallSession
import com.example.data.model.CallTarget
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CallSignalingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun callSignaling_createAcceptIceExchangeQualityUpgradeAndEnd_enforcesRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceMsgRepo = MessagingRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceMsgRepo.syncCurrentUserProfile(checkNotNull(auth.currentUser)).getOrThrow()
        }
        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceMsgRepo.observeUserProfile(aliceUid).first { it != null }!!
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobMsgRepo = MessagingRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobMsgRepo.syncCurrentUserProfile(checkNotNull(auth.currentUser)).getOrThrow()
        }
        val bobProfile = withTimeout(FLOW_TIMEOUT_MS) {
            bobMsgRepo.observeUserProfile(bobUid).first { it != null }!!
        }

        // Alice creates a direct conversation and places an HD video call to Bob
        signInTestUser(ALICE_EMAIL)
        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceMsgRepo.getOrCreateDirectConversation(aliceProfile, bobProfile).getOrThrow()
        }
        val aliceCallRepo = CallSignalingRepository(firestore, auth)
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val fakeOffer = "v=0\r\no=- 11111 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.createCallSession(
                callId = callId,
                callerProfile = aliceProfile,
                target = CallTarget(
                    userId = bobUid,
                    displayName = bobProfile.displayName,
                    photoUrl = bobProfile.photoUrl,
                    conversationId = convId
                ),
                callType = CallSession.TYPE_VIDEO,
                qualityPreset = CallQualityPreset.HD_720P,
                offerSdp = fakeOffer
            ).getOrThrow()
        }
        assertEquals(CallSession.STATUS_RINGING, created.status)

        // Alice sends an ICE candidate
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.sendIceCandidate(
                callId = callId,
                participantIds = created.participantIds,
                sdpMid = "0",
                sdpMLineIndex = 0,
                candidate = "candidate:1 1 UDP 2122252543 192.168.1.2 50000 typ host"
            ).getOrThrow()
        }

        // Bob sees incoming ringing call, observes Alice's candidate, accepts with answer SDP, upgrades to 1080p FHD
        signInTestUser(BOB_EMAIL)
        val bobCallRepo = CallSignalingRepository(firestore, auth)
        val incomingCalls = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeIncomingRingingCalls(bobUid).first { list -> list.any { it.id == callId } }
        }
        assertEquals(1, incomingCalls.count { it.id == callId })

        val aliceCandidatesForBob = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeRemoteCandidates(callId, bobUid).first { it.isNotEmpty() }
        }
        assertEquals(aliceUid, aliceCandidatesForBob.first().senderId)

        val fakeAnswer = "v=0\r\no=- 22222 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.acceptCall(callId, fakeAnswer).getOrThrow()
            bobCallRepo.updateQualityPreset(callId, CallQualityPreset.FHD_1080P).getOrThrow()
        }

        val acceptedSession = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeCallSession(callId).first {
                it?.status == CallSession.STATUS_ACCEPTED &&
                    it.qualityPreset == CallQualityPreset.FHD_1080P.wireValue
            }
        }
        assertNotNull(acceptedSession)

        // Charlie (outsider) cannot read or observe Alice & Bob's call
        val charlieUid = signInTestUser(CHARLIE_EMAIL)
        val charlieCallRepo = CallSignalingRepository(firestore, auth)
        val charlieIncoming = withTimeout(FLOW_TIMEOUT_MS) {
            charlieCallRepo.observeIncomingRingingCalls(charlieUid).first()
        }
        assertFalse(charlieIncoming.any { it.id == callId })

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                charlieCallRepo.observeCallSession(callId).first()
            }
            fail("Expected PERMISSION_DENIED when Charlie observes Alice & Bob's call")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }

        // Alice ends the call and terminal lock blocks any further status change
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.endCall(callId).getOrThrow()
        }
        val endedSession = withTimeout(FLOW_TIMEOUT_MS) {
            aliceCallRepo.observeCallSession(callId).first { it?.status == CallSession.STATUS_ENDED }
        }
        assertTrue(endedSession?.isTerminal == true)

        val postEndUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.updateQualityPreset(callId, CallQualityPreset.HD_720P)
        }
        assertTrue(postEndUpdate.isFailure)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_call@test.com"
        const val BOB_EMAIL = "bob_call@test.com"
        const val CHARLIE_EMAIL = "charlie_call@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
