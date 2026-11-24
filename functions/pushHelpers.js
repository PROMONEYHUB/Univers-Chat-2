"use strict";

function formatPushPreview(messageData, showPreview = true) {
  const type = String(messageData?.type || "text");
  if (!showPreview) {
    return type === "audio"
      ? "New voice message"
      : type === "image"
        ? "New photo"
        : type === "poll"
          ? "New poll"
          : "New message";
  }

  const rawText = String(messageData?.text || "").trim();
  if (rawText.length > 0) {
    return rawText.slice(0, 180);
  }
  switch (type) {
    case "image":
      return "📷 Shared a photo";
    case "poll":
      return `📊 ${String(messageData?.pollQuestion || "Started a live poll").slice(0, 140)}`;
    case "audio":
      return "🎤 Sent a voice message";
    default:
      return "Sent a message";
  }
}

function shouldDeliverToUser({
  userData,
  senderId,
  conversationData,
  recipientId,
}) {
  if (!recipientId || recipientId === senderId) return false;
  if (!userData) return false;
  if (userData.notificationsEnabled === false) return false;

  const blocked = Array.isArray(userData.blockedUserIds)
    ? userData.blockedUserIds
    : [];
  if (senderId && blocked.includes(senderId)) return false;

  const mutedBy = conversationData?.mutedBy;
  if (mutedBy && typeof mutedBy === "object" && mutedBy[recipientId] === true) {
    return false;
  }

  return extractTokenEntries(userData).length > 0;
}

function extractTokenEntries(userData) {
  const map = userData?.fcmTokens;
  if (!map || typeof map !== "object") return [];
  return Object.entries(map)
    .filter(
      ([deviceId, token]) =>
        typeof deviceId === "string" &&
        deviceId.length > 0 &&
        typeof token === "string" &&
        token.length > 10
    )
    .map(([deviceId, token]) => ({ deviceId, token }));
}

function buildMessageMulticastPayload({
  conversationId,
  conversationData,
  messageId,
  messageData,
  recipientId,
  recipientUserData,
  tokens,
}) {
  const senderId = String(messageData?.senderId || "");
  const senderName = String(messageData?.senderName || "Univers Member").slice(0, 60);
  const senderPhotoUrl = String(messageData?.senderPhotoUrl || "").slice(0, 500);
  const convType = String(conversationData?.type || "direct");
  const isGroup = convType === "group";
  const conversationTitle = isGroup
    ? String(conversationData?.title || "Group Chat").slice(0, 80)
    : senderName;
  const showPreview = recipientUserData?.notificationPreviewEnabled !== false;
  const body = formatPushPreview(messageData, showPreview);
  const sentAtMillis = String(
    messageData?.createdAt?.toMillis?.() ?? Date.now()
  );

  // High-priority Android data payload ensures onMessageReceived() wakes even in
  // killed/background/Doze states so UniversFirebaseMessagingService renders
  // NotificationCompat.MessagingStyle grouped per chat with inline Reply, Mark Read,
  // muted checks, and circular sender avatars.
  return {
    tokens,
    data: {
      kind: "message",
      recipientId: String(recipientId),
      conversationId: String(conversationId),
      conversationType: convType,
      conversationTitle,
      messageId: String(messageId),
      senderId,
      senderName,
      senderPhotoUrl,
      messageType: String(messageData?.type || "text"),
      previewBody: body,
      showPreview: String(showPreview),
      sentAtMillis,
    },
    android: {
      priority: "high",
      ttl: 86400 * 1000,
      collapseKey: `chat_${conversationId}`,
    },
  };
}

function buildIncomingCallMulticastPayload({
  callId,
  callData,
  tokens,
}) {
  const sentAtMillis = String(callData?.createdAt?.toMillis?.() ?? Date.now());
  return {
    tokens,
    data: {
      kind: "call_incoming",
      callId: String(callId),
      callerId: String(callData?.callerId || ""),
      callerName: String(callData?.callerName || "Member").slice(0, 60),
      callerPhotoUrl: String(callData?.callerPhotoUrl || "").slice(0, 500),
      calleeId: String(callData?.calleeId || ""),
      conversationId: String(callData?.conversationId || ""),
      callType: String(callData?.callType || "voice"),
      qualityPreset: String(callData?.qualityPreset || "HD_VOICE"),
      offerSdp: String(callData?.offerSdp || ""),
      sentAtMillis,
    },
    android: {
      priority: "high",
      ttl: 30 * 1000,
    },
  };
}

function buildCallStateChangedMulticastPayload({
  callId,
  callData,
  tokens,
}) {
  return {
    tokens,
    data: {
      kind: "call_state_changed",
      callId: String(callId),
      status: String(callData?.status || "ended"),
      callerId: String(callData?.callerId || ""),
      callerName: String(callData?.callerName || "Member").slice(0, 60),
      callerPhotoUrl: String(callData?.callerPhotoUrl || "").slice(0, 500),
      calleeId: String(callData?.calleeId || ""),
      conversationId: String(callData?.conversationId || ""),
      callType: String(callData?.callType || "voice"),
      endedBy: String(callData?.endedBy || ""),
      sentAtMillis: String(Date.now()),
    },
    android: {
      priority: "high",
      ttl: 30 * 1000,
    },
  };
}

module.exports = {
  formatPushPreview,
  shouldDeliverToUser,
  extractTokenEntries,
  buildMessageMulticastPayload,
  buildIncomingCallMulticastPayload,
  buildCallStateChangedMulticastPayload,
};
