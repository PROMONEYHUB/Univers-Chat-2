package com.example.calls

import com.example.data.model.CallQualityPreset
import com.example.data.model.CallSession
import com.example.ui.screens.formatCallDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallQualityAndSdpTest {

    @Test
    fun optimizeSdpForHd_enrichesOpusStereoHighBitrateAndVideoBitrateBounds() {
        val sampleSdp = listOf(
            "v=0",
            "o=- 123456 2 IN IP4 127.0.0.1",
            "s=-",
            "t=0 0",
            "m=audio 9 UDP/TLS/RTP/SAVPF 111",
            "a=rtpmap:111 opus/48000/2",
            "a=fmtp:111 minptime=10;useinbandfec=1",
            "m=video 9 UDP/TLS/RTP/SAVPF 96",
            "a=rtpmap:96 H264/90000",
            "a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f"
        ).joinToString("\r\n")

        val tunedFhd = WebRtcCallEngine.optimizeSdpForHd(sampleSdp, CallQualityPreset.FHD_1080P)

        assertTrue(tunedFhd.contains("maxaveragebitrate=128000"))
        assertTrue(tunedFhd.contains("stereo=1"))
        assertTrue(tunedFhd.contains("sprop-stereo=1"))
        assertTrue(tunedFhd.contains("x-google-max-bitrate=2500"))
        assertTrue(tunedFhd.contains("x-google-start-bitrate=1200"))

        val tunedVoice = WebRtcCallEngine.optimizeSdpForHd(sampleSdp, CallQualityPreset.HD_VOICE)
        assertTrue(tunedVoice.contains("maxaveragebitrate=128000"))
        assertFalse(tunedVoice.contains("x-google-max-bitrate="))
    }

    @Test
    fun callSessionAndDurationHelpers_returnExpectedMetadata() {
        val session = CallSession(
            id = "call_1",
            callerId = "alice",
            callerName = "Alice",
            calleeId = "bob",
            calleeName = "Bob",
            participantIds = listOf("alice", "bob"),
            callType = CallSession.TYPE_VIDEO,
            qualityPreset = CallQualityPreset.FHD_1080P.wireValue,
            status = CallSession.STATUS_ACCEPTED
        )

        assertTrue(session.isVideoCall)
        assertFalse(session.isTerminal)
        assertEquals("Bob", session.peerNameFor("alice"))
        assertEquals("Alice", session.peerNameFor("bob"))
        assertEquals(CallQualityPreset.FHD_1080P, session.qualityEnum())
        assertEquals("02:05", formatCallDuration(125L))
    }
}
