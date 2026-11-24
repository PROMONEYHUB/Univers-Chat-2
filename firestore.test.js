const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const ts = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. Unauthenticated access must fail everywhere ---

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("user_profiles").where("isDiscoverable", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("conversations").where("participantIds", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    unauthDb
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .where("participantIds", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    unauthDb.collection("calls").where("participantIds", "array-contains", ALICE_UID).get()
  );
});

// --- 2. Private User PII isolation (/users/{uid}) ---

test("Owner can create and read own /users/{uid}; other users cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      readReceiptsEnabled: true,
      notificationsEnabled: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "spoof@example.com",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

// --- 3. Public User Profiles & Query Alignment (/user_profiles/{uid}) ---

test("Signed-in user can create own profile and query discoverable profiles, but cannot spoof or shadow-update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("user_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      bio: "Building things",
      photoUrl: "",
      statusVibe: "Online",
      isOnline: true,
      isDiscoverable: true,
      lastSeenAt: ts(),
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Discoverable list query succeeds
  await assertSucceeds(
    bobDb.collection("user_profiles").where("isDiscoverable", "==", true).get()
  );

  // Blanket unfiltered list query fails
  await assertFails(bobDb.collection("user_profiles").get());

  // Cross-user update fails
  await assertFails(
    bobDb.collection("user_profiles").doc(ALICE_UID).update({
      displayName: "Hacked Alice",
      updatedAt: ts(),
    })
  );

  // Shadow update with ghost field fails
  await assertFails(
    aliceDb.collection("user_profiles").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: ts(),
    })
  );
});

// --- 4. Conversations & Participant Isolation (/conversations/{cid}) ---

test("Participants can create, query, and update conversation; non-participants are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Check existence of deterministic DM ID before creation
  await assertSucceeds(aliceDb.collection("conversations").doc("dm_alice_bob").get());

  await assertSucceeds(
    aliceDb.collection("conversations").doc("dm_alice_bob").set({
      type: "direct",
      title: "",
      createdBy: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPhotos: {},
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      lastMessageType: "",
      lastMessageAt: ts(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Participant query aligned with rule succeeds
  await assertSucceeds(
    bobDb.collection("conversations").where("participantIds", "array-contains", BOB_UID).get()
  );

  // Unfiltered query fails
  await assertFails(bobDb.collection("conversations").get());

  // Charlie (non-participant) cannot read or update
  await assertFails(charlieDb.collection("conversations").doc("dm_alice_bob").get());
  await assertFails(
    charlieDb.collection("conversations").doc("dm_alice_bob").update({
      lastMessageText: "Intruder",
      updatedAt: ts(),
    })
  );

  // Bob can mark read (Action 2)
  await assertSucceeds(
    bobDb.collection("conversations").doc("dm_alice_bob").update({
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      updatedAt: ts(),
    })
  );

  // Bob cannot mutate immutable createdBy
  await assertFails(
    bobDb.collection("conversations").doc("dm_alice_bob").update({
      createdBy: BOB_UID,
      updatedAt: ts(),
    })
  );

  // Charlie (non-participant) cannot delete DM; Bob (participant) can delete DM
  await assertFails(charlieDb.collection("conversations").doc("dm_alice_bob").delete());
  await assertSucceeds(bobDb.collection("conversations").doc("dm_alice_bob").delete());
});

// --- 5. Messages Subcollection, Reactions, Polls & Edits ---

test("Participants can send, react, vote on polls, edit own text, while non-participants and spoofers fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("group_1").set({
      type: "group",
      title: "Design Club",
      topic: "Weekly sync",
      createdBy: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPhotos: {},
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      lastMessageType: "",
      lastMessageAt: ts(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Alice sends text message
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        conversationId: "group_1",
        senderId: ALICE_UID,
        senderName: "Alice",
        senderPhotoUrl: "",
        participantIds: [ALICE_UID, BOB_UID],
        type: "text",
        text: "Hello Bob!",
        reactions: {},
        isEdited: false,
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Bob queries messages with participantIds filter
  await assertSucceeds(
    bobDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );

  // Charlie cannot read or send messages into group_1
  await assertFails(
    charlieDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_1")
      .get()
  );
  await assertFails(
    charlieDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_bad")
      .set({
        conversationId: "group_1",
        senderId: CHARLIE_UID,
        senderName: "Charlie",
        participantIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
        type: "text",
        text: "Bypass",
        isEdited: false,
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Bob can react to Alice's message (Action 2)
  await assertSucceeds(
    bobDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        reactions: { [BOB_UID]: "🔥" },
        updatedAt: ts(),
      })
  );

  // Bob CANNOT edit Alice's message text
  await assertFails(
    bobDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        text: "Edited by Bob",
        isEdited: true,
        updatedAt: ts(),
      })
  );

  // Alice CAN edit her own text message (Action 1)
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        text: "Hello Bob! (updated)",
        isEdited: true,
        updatedAt: ts(),
      })
  );

  // Alice creates a poll message and Bob votes (Action 3)
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("poll_1")
      .set({
        conversationId: "group_1",
        senderId: ALICE_UID,
        senderName: "Alice",
        participantIds: [ALICE_UID, BOB_UID],
        type: "poll",
        text: "Coffee spot?",
        pollQuestion: "Coffee spot?",
        pollOptions: ["Blue Bottle", "Sightglass"],
        pollVotes: {},
        isEdited: false,
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  await assertSucceeds(
    bobDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("poll_1")
      .update({
        pollVotes: { [BOB_UID]: "0" },
        updatedAt: ts(),
      })
  );

  // Bob cannot delete Alice's message; Alice can delete her own
  await assertFails(
    bobDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_1")
      .delete()
  );
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("group_1")
      .collection("messages")
      .doc("msg_1")
      .delete()
  );
});

// --- 6. WebRTC HD Calls & ICE Candidates (/calls/{callId}/candidates/{candidateId}) ---

test("Participants can initiate, accept, exchange ICE candidates, end HD calls; outsiders and post-terminal updates fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Seed Bob's public profile because call creation verifies callee exists
  await assertSucceeds(
    bobDb.collection("user_profiles").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      handle: "bob",
      isOnline: true,
      isDiscoverable: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Alice starts an HD video call with Bob
  const callRef = aliceDb.collection("calls").doc("call_hd_1");
  await assertSucceeds(
    callRef.set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      conversationId: "dm_alice_bob",
      callType: "video",
      qualityPreset: "HD_720P",
      status: "ringing",
      offerSdp: "v=0\no=- 12345 2 IN IP4 127.0.0.1\ns=-\nt=0 0\n",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Blanket unfiltered query fails
  await assertFails(bobDb.collection("calls").get());

  // Bob's aligned query succeeds
  await assertSucceeds(
    bobDb.collection("calls").where("participantIds", "array-contains", BOB_UID).get()
  );

  // Charlie cannot read, spoof, or accept Bob's call
  await assertFails(charlieDb.collection("calls").doc("call_hd_1").get());
  await assertFails(
    charlieDb.collection("calls").doc("call_hd_1").update({
      status: "accepted",
      answerSdp: "v=0\no=- 999 2 IN IP4 127.0.0.1\n",
      updatedAt: ts(),
    })
  );

  // Alice cannot self-accept her own call
  await assertFails(
    aliceDb.collection("calls").doc("call_hd_1").update({
      status: "accepted",
      answerSdp: "v=0\no=- 999 2 IN IP4 127.0.0.1\n",
      updatedAt: ts(),
    })
  );

  // Bob accepts the call with answerSdp
  await assertSucceeds(
    bobDb.collection("calls").doc("call_hd_1").update({
      status: "accepted",
      answerSdp: "v=0\no=- 67890 2 IN IP4 127.0.0.1\ns=-\nt=0 0\n",
      updatedAt: ts(),
    })
  );

  // Alice upgrades live call quality preset to Full HD 1080p
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_hd_1").update({
      qualityPreset: "FHD_1080P",
      updatedAt: ts(),
    })
  );

  // Both participants exchange ICE candidates; Charlie cannot
  await assertSucceeds(
    aliceDb
      .collection("calls")
      .doc("call_hd_1")
      .collection("candidates")
      .doc("ice_a1")
      .set({
        callId: "call_hd_1",
        senderId: ALICE_UID,
        participantIds: [ALICE_UID, BOB_UID],
        sdpMid: "0",
        sdpMLineIndex: 0,
        candidate: "candidate:1 1 UDP 2122252543 192.168.1.10 54321 typ host",
        createdAt: ts(),
      })
  );
  await assertSucceeds(
    bobDb
      .collection("calls")
      .doc("call_hd_1")
      .collection("candidates")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    charlieDb
      .collection("calls")
      .doc("call_hd_1")
      .collection("candidates")
      .doc("ice_c1")
      .set({
        callId: "call_hd_1",
        senderId: CHARLIE_UID,
        participantIds: [ALICE_UID, BOB_UID],
        sdpMid: "0",
        sdpMLineIndex: 0,
        candidate: "candidate:1 1 UDP 2122252543 10.0.0.1 9999 typ host",
        createdAt: ts(),
      })
  );

  // Bob ends the call cleanly
  await assertSucceeds(
    bobDb.collection("calls").doc("call_hd_1").update({
      status: "ended",
      endedBy: BOB_UID,
      updatedAt: ts(),
    })
  );

  // Terminal state lock: cannot reopen an ended call
  await assertFails(
    aliceDb.collection("calls").doc("call_hd_1").update({
      qualityPreset: "HD_720P",
      updatedAt: ts(),
    })
  );
});

// --- 7. Social Messaging Options: Voice Notes, Pin/Mute, Pin Message, Forwarding & Block Users ---

test("Participants can send voice notes, pin/mute threads, pin messages, forward, and block users safely", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      readReceiptsEnabled: true,
      notificationsEnabled: true,
      blockedUserIds: [],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Alice blocks Charlie
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      blockedUserIds: [CHARLIE_UID],
      updatedAt: ts(),
    })
  );
  // Bob cannot modify Alice's blockedUserIds
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      blockedUserIds: [],
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc("dm_social").set({
      type: "direct",
      title: "",
      createdBy: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPhotos: {},
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      lastMessageType: "",
      lastMessageAt: ts(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      pinnedBy: {},
      mutedBy: {},
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Voice note creation succeeds with valid audioBase64, fails when audioBase64 is empty
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("dm_social")
      .collection("messages")
      .doc("voice_1")
      .set({
        conversationId: "dm_social",
        senderId: ALICE_UID,
        senderName: "Alice",
        participantIds: [ALICE_UID, BOB_UID],
        type: "audio",
        text: "Voice message (0:04)",
        audioBase64: "AAAAIGZ0eXBtcDQyAAAAAG1wNDJpc29tYXZjMQ==",
        audioDurationSec: 4,
        isForwarded: true,
        isEdited: false,
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  await assertFails(
    aliceDb
      .collection("conversations")
      .doc("dm_social")
      .collection("messages")
      .doc("voice_invalid")
      .set({
        conversationId: "dm_social",
        senderId: ALICE_UID,
        senderName: "Alice",
        participantIds: [ALICE_UID, BOB_UID],
        type: "audio",
        text: "Missing audio payload",
        audioBase64: "",
        audioDurationSec: 4,
        isEdited: false,
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Bob can pin/mute thread (Action 5) and pin a message (Action 6)
  await assertSucceeds(
    bobDb.collection("conversations").doc("dm_social").update({
      pinnedBy: { [BOB_UID]: true },
      mutedBy: { [BOB_UID]: true },
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc("dm_social").update({
      pinnedMessageId: "voice_1",
      pinnedMessagePreview: "🎤 Voice message (0:04)",
      pinnedMessageSenderName: "Alice",
      updatedAt: ts(),
    })
  );

  // Charlie cannot pin or mute Alice & Bob's chat
  await assertFails(
    charlieDb.collection("conversations").doc("dm_social").update({
      pinnedBy: { [CHARLIE_UID]: true },
      updatedAt: ts(),
    })
  );
});

// --- 8. Phase 1: Heartbeat Presence, Typing/Recording Indicators & Delivery/Read Receipts ---

test("Presence heartbeat, typing/recording indicators, and message delivery/read receipts obey rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Owner can store onlineStatusVisible & lastHeartbeatAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      readReceiptsEnabled: true,
      notificationsEnabled: true,
      onlineStatusVisible: false,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("user_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      isOnline: false,
      onlineStatusVisible: false,
      isDiscoverable: true,
      lastHeartbeatAt: ts(),
      lastSeenAt: ts(),
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("user_profiles").doc(ALICE_UID).update({
      isOnline: true,
      lastHeartbeatAt: ts(),
      lastSeenAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertFails(
    bobDb.collection("user_profiles").doc(ALICE_UID).update({
      lastHeartbeatAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc("dm_phase1").set({
      type: "direct",
      title: "",
      createdBy: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPhotos: {},
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      lastMessageType: "",
      lastMessageAt: ts(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      typingBy: {},
      recordingBy: {},
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Participants can update typingBy / recordingBy; outsiders cannot
  await assertSucceeds(
    bobDb.collection("conversations").doc("dm_phase1").update({
      typingBy: { [BOB_UID]: ts() },
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc("dm_phase1").update({
      recordingBy: { [ALICE_UID]: ts() },
      updatedAt: ts(),
    })
  );
  await assertFails(
    charlieDb.collection("conversations").doc("dm_phase1").update({
      typingBy: { [CHARLIE_UID]: ts() },
      updatedAt: ts(),
    })
  );

  // Create message with clientMessageId and confirm deliveredTo / readBy
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("dm_phase1")
      .collection("messages")
      .doc("client_msg_001")
      .set({
        conversationId: "dm_phase1",
        senderId: ALICE_UID,
        senderName: "Alice",
        participantIds: [ALICE_UID, BOB_UID],
        type: "text",
        text: "Optimistic ping",
        clientMessageId: "client_msg_001",
        deliveredTo: {},
        readBy: {},
        isEdited: false,
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  await assertSucceeds(
    bobDb
      .collection("conversations")
      .doc("dm_phase1")
      .collection("messages")
      .doc("client_msg_001")
      .update({
        deliveredTo: { [BOB_UID]: ts() },
        readBy: { [BOB_UID]: ts() },
        updatedAt: ts(),
      })
  );

  await assertFails(
    charlieDb
      .collection("conversations")
      .doc("dm_phase1")
      .collection("messages")
      .doc("client_msg_001")
      .update({
        deliveredTo: { [CHARLIE_UID]: ts() },
        updatedAt: ts(),
      })
  );
});

// --- 9. Phase 2: FCM Device Tokens & Notification Preview Privacy ---

test("Owner can register, rotate, and delete device FCM tokens and toggle notification previews; outsiders cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      readReceiptsEnabled: true,
      notificationsEnabled: true,
      notificationPreviewEnabled: false,
      fcmTokens: { dev_1: "fcm_token_abc123" },
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fcmTokens: { dev_1: "fcm_token_rotated" },
      notificationPreviewEnabled: true,
      updatedAt: ts(),
    })
  );

  // Bounded fcmTokens map (>15 entries rejected)
  const oversizedTokens = {};
  for (let i = 0; i < 16; i += 1) {
    oversizedTokens[`dev_${i}`] = `token_${i}`;
  }
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fcmTokens: oversizedTokens,
      updatedAt: ts(),
    })
  );

  // Cross-user token tampering denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      fcmTokens: { dev_rogue: "rogue_token" },
      updatedAt: ts(),
    })
  );
});


