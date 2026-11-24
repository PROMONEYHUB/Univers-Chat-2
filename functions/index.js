"use strict";

const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
  onDocumentCreated,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");
const {
  shouldDeliverToUser,
  extractTokenEntries,
  buildMessageMulticastPayload,
  buildIncomingCallMulticastPayload,
  buildCallStateChangedMulticastPayload,
} = require("./pushHelpers");

initializeApp();

const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-universc-d72af19c-dc1d-4a9e-a6e9-d52b223768f8";

const db = getFirestore(DATABASE_ID);
const messaging = getMessaging();

const STALE_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

async function sendAndPruneStaleTokens(userId, tokenEntries, multicastMessage) {
  if (!tokenEntries.length) return;
  const response = await messaging.sendEachForMulticast(multicastMessage);
  if (response.failureCount === 0) return;

  const updates = {};
  response.responses.forEach((res, idx) => {
    if (!res.success && res.error && STALE_TOKEN_CODES.has(res.error.code)) {
      const deviceId = tokenEntries[idx]?.deviceId;
      if (deviceId) {
        updates[`fcmTokens.${deviceId}`] = FieldValue.delete();
      }
    }
  });

  if (Object.keys(updates).length > 0) {
    updates.updatedAt = FieldValue.serverTimestamp();
    await db.collection("users").doc(userId).update(updates).catch(() => {});
  }
}

exports.onMessageCreated = onDocumentCreated(
  {
    document: "conversations/{conversationId}/messages/{messageId}",
    database: DATABASE_ID,
    region: "asia-southeast1",
  },
  async (event) => {
    const messageData = event.data?.data();
    if (!messageData) return;

    const { conversationId, messageId } = event.params;
    const senderId = String(messageData.senderId || "");
    const convSnap = await db.collection("conversations").doc(conversationId).get();
    const conversationData = convSnap.exists ? convSnap.data() : {};

    const participantIds = Array.isArray(messageData.participantIds)
      ? messageData.participantIds
      : Array.isArray(conversationData?.participantIds)
        ? conversationData.participantIds
        : [];

    const recipientIds = participantIds.filter((uid) => uid && uid !== senderId);
    if (!recipientIds.length) return;

    const userSnaps = await Promise.all(
      recipientIds.map((uid) => db.collection("users").doc(uid).get())
    );

    await Promise.all(
      userSnaps.map(async (snap, idx) => {
        const recipientId = recipientIds[idx];
        const userData = snap.exists ? snap.data() : null;
        if (
          !shouldDeliverToUser({
            userData,
            senderId,
            conversationData,
            recipientId,
          })
        ) {
          return;
        }
        const entries = extractTokenEntries(userData);
        const tokens = entries.map((e) => e.token);
        const payload = buildMessageMulticastPayload({
          conversationId,
          conversationData,
          messageId,
          messageData,
          recipientId,
          recipientUserData: userData,
          tokens,
        });
        await sendAndPruneStaleTokens(recipientId, entries, payload);
      })
    );
  }
);

exports.onCallCreated = onDocumentCreated(
  {
    document: "calls/{callId}",
    database: DATABASE_ID,
    region: "asia-southeast1",
  },
  async (event) => {
    const callData = event.data?.data();
    if (!callData || callData.status !== "ringing") return;

    const { callId } = event.params;
    const calleeId = String(callData.calleeId || "");
    const callerId = String(callData.callerId || "");
    if (!calleeId || calleeId === callerId) return;

    const userSnap = await db.collection("users").doc(calleeId).get();
    const userData = userSnap.exists ? userSnap.data() : null;
    if (!userData || userData.notificationsEnabled === false) return;

    const blocked = Array.isArray(userData.blockedUserIds)
      ? userData.blockedUserIds
      : [];
    if (blocked.includes(callerId)) return;

    const entries = extractTokenEntries(userData);
    if (!entries.length) return;

    const payload = buildIncomingCallMulticastPayload({
      callId,
      callData,
      tokens: entries.map((e) => e.token),
    });
    await sendAndPruneStaleTokens(calleeId, entries, payload);
  }
);

exports.onCallUpdated = onDocumentUpdated(
  {
    document: "calls/{callId}",
    database: DATABASE_ID,
    region: "asia-southeast1",
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    if (before.status !== "ringing" || after.status === "ringing") {
      return;
    }

    const { callId } = event.params;
    const calleeId = String(after.calleeId || "");
    if (!calleeId) return;

    const userSnap = await db.collection("users").doc(calleeId).get();
    const userData = userSnap.exists ? userSnap.data() : null;
    if (!userData) return;

    const entries = extractTokenEntries(userData);
    if (!entries.length) return;

    const payload = buildCallStateChangedMulticastPayload({
      callId,
      callData: after,
      tokens: entries.map((e) => e.token),
    });
    await sendAndPruneStaleTokens(calleeId, entries, payload);
  }
);
