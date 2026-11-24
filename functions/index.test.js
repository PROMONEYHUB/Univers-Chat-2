"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  formatPushPreview,
  shouldDeliverToUser,
  extractTokenEntries,
  buildMessageMulticastPayload,
  buildIncomingCallMulticastPayload,
  buildCallStateChangedMulticastPayload,
} = require("./pushHelpers");

test("shouldDeliverToUser enforces notificationsEnabled, blocked list, muted chat, and valid tokens", () => {
  const baseUser = {
    notificationsEnabled: true,
    blockedUserIds: ["charlie"],
    fcmTokens: { dev1: "valid_fcm_token_string_12345" },
  };
  const conv = { mutedBy: { mutedUser: true } };

  assert.equal(
    shouldDeliverToUser({
      userData: baseUser,
      senderId: "alice",
      conversationData: conv,
      recipientId: "bob",
    }),
    true
  );

  // Blocked sender skipped
  assert.equal(
    shouldDeliverToUser({
      userData: baseUser,
      senderId: "charlie",
      conversationData: conv,
      recipientId: "bob",
    }),
    false
  );

  // Muted conversation skipped
  assert.equal(
    shouldDeliverToUser({
      userData: baseUser,
      senderId: "alice",
      conversationData: conv,
      recipientId: "mutedUser",
    }),
    false
  );

  // Disabled notifications skipped
  assert.equal(
    shouldDeliverToUser({
      userData: { ...baseUser, notificationsEnabled: false },
      senderId: "alice",
      conversationData: conv,
      recipientId: "bob",
    }),
    false
  );

  // Self skipped
  assert.equal(
    shouldDeliverToUser({
      userData: baseUser,
      senderId: "bob",
      conversationData: conv,
      recipientId: "bob",
    }),
    false
  );
});

test("buildMessageMulticastPayload respects notificationPreviewEnabled privacy setting", () => {
  const hiddenPreview = buildMessageMulticastPayload({
    conversationId: "dm_1",
    conversationData: { type: "direct", title: "" },
    messageId: "m1",
    messageData: {
      senderId: "alice",
      senderName: "Alice",
      type: "text",
      text: "Top secret pass",
    },
    recipientId: "bob",
    recipientUserData: { notificationPreviewEnabled: false },
    tokens: ["tok1"],
  });

  assert.equal(hiddenPreview.android.priority, "high");
  assert.equal(hiddenPreview.data.kind, "message");
  assert.equal(hiddenPreview.data.previewBody, "New message");
  assert.equal(hiddenPreview.data.showPreview, "false");

  const visiblePreview = buildMessageMulticastPayload({
    conversationId: "grp_1",
    conversationData: { type: "group", title: "Core Team" },
    messageId: "m2",
    messageData: {
      senderId: "alice",
      senderName: "Alice",
      type: "text",
      text: "Dinner at 8?",
    },
    recipientId: "bob",
    recipientUserData: { notificationPreviewEnabled: true },
    tokens: ["tok1"],
  });

  assert.equal(visiblePreview.data.conversationTitle, "Core Team");
  assert.equal(visiblePreview.data.previewBody, "Dinner at 8?");
  assert.equal(formatPushPreview({ type: "audio" }, false), "New voice message");
});

test("call push helpers build high-priority 30s TTL incoming and cancel payloads", () => {
  const incoming = buildIncomingCallMulticastPayload({
    callId: "c1",
    callData: {
      callerId: "alice",
      callerName: "Alice",
      calleeId: "bob",
      conversationId: "dm_1",
      callType: "video",
      qualityPreset: "HD_720P",
      offerSdp: "v=0",
    },
    tokens: ["tok_bob"],
  });

  assert.equal(incoming.android.priority, "high");
  assert.equal(incoming.android.ttl, 30000);
  assert.equal(incoming.data.kind, "call_incoming");
  assert.equal(incoming.data.callType, "video");

  const cancelled = buildCallStateChangedMulticastPayload({
    callId: "c1",
    callData: {
      status: "missed",
      callerId: "alice",
      callerName: "Alice",
      calleeId: "bob",
      conversationId: "dm_1",
      callType: "video",
      endedBy: "alice",
    },
    tokens: ["tok_bob"],
  });

  assert.equal(cancelled.data.kind, "call_state_changed");
  assert.equal(cancelled.data.status, "missed");
  assert.deepEqual(extractTokenEntries({ fcmTokens: { d1: "token_valid_12345", bad: "1" } }), [
    { deviceId: "d1", token: "token_valid_12345" },
  ]);
});
