const express = require('express');
const crypto = require('crypto');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;
const BOT_TOKEN = (process.env.TELEGRAM_BOT_TOKEN || '').trim();
const BOT_USERNAME = (process.env.TELEGRAM_BOT_USERNAME || 'universchatapp').replace(/^@/, '');
const OTP_TTL_MS = 5 * 60 * 1000;

// In-memory maps (persists while Render instance is warm; use Redis/Postgres for multi-instance).
const phoneToChatId = new Map();
const pendingOtps = new Map();
let lastUpdateId = 0;

function normalizeDigits(raw) {
  return String(raw || '').replace(/\D/g, '');
}

function phoneKeys(raw) {
  const digits = normalizeDigits(raw);
  const keys = new Set();
  if (!digits) return keys;
  keys.add(digits);
  if (digits.length > 10) {
    keys.add(digits.slice(-10));
  }
  return keys;
}

function findChatIdForPhone(rawPhone) {
  for (const key of phoneKeys(rawPhone)) {
    if (phoneToChatId.has(key)) {
      return phoneToChatId.get(key);
    }
  }
  return null;
}

function storePhoneChatMapping(rawPhone, chatId) {
  for (const key of phoneKeys(rawPhone)) {
    phoneToChatId.set(key, chatId);
  }
}

async function telegramApi(method, payload) {
  if (!BOT_TOKEN) {
    throw new Error('TELEGRAM_BOT_TOKEN is not configured on Render.');
  }
  const res = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload || {})
  });
  const data = await res.json();
  if (!data.ok) {
    throw new Error(data.description || `Telegram API ${method} failed`);
  }
  return data.result;
}

async function handleTelegramUpdate(update) {
  const msg = update && update.message;
  if (!msg || !msg.chat) return;
  const chatId = msg.chat.id;
  const text = (msg.text || '').trim();

  if (msg.contact && msg.contact.phone_number) {
    storePhoneChatMapping(msg.contact.phone_number, chatId);
    await telegramApi('sendMessage', {
      chat_id: chatId,
      text: 'Number linked to Univers Chat! Return to the app and tap Send Telegram OTP.',
      reply_markup: { remove_keyboard: true }
    });
    return;
  }

  if (text.startsWith('/start')) {
    const startArg = text.slice('/start'.length).trim();
    const argDigits = normalizeDigits(startArg);
    if (argDigits.length >= 7) {
      storePhoneChatMapping(argDigits, chatId);
      await telegramApi('sendMessage', {
        chat_id: chatId,
        text: `Linked +${argDigits} to your Telegram. Return to Univers Chat and tap Send Telegram OTP — your code will arrive right here!`
      });
      return;
    }

    await telegramApi('sendMessage', {
      chat_id: chatId,
      text: 'Welcome to Univers Chat OTP Bot! Tap the button below to share your phone number so we can send free login codes here.',
      reply_markup: {
        keyboard: [[{ text: 'Share My Phone Number', request_contact: true }]],
        resize_keyboard: true,
        one_time_keyboard: true
      }
    });
  }
}

async function pollTelegramUpdates() {
  if (!BOT_TOKEN) return;
  try {
    const updates = await telegramApi('getUpdates', {
      offset: lastUpdateId + 1,
      timeout: 20,
      allowed_updates: ['message']
    });
    for (const u of updates) {
      lastUpdateId = Math.max(lastUpdateId, u.update_id);
      await handleTelegramUpdate(u);
    }
  } catch (err) {
    console.error('Telegram poll error:', err.message);
  } finally {
    setTimeout(pollTelegramUpdates, 1000);
  }
}

app.get(['/', '/health'], (_req, res) => {
  res.json({
    ok: true,
    bot: `@${BOT_USERNAME}`,
    configured: Boolean(BOT_TOKEN)
  });
});

app.post('/telegram-webhook', async (req, res) => {
  try {
    await handleTelegramUpdate(req.body);
  } catch (err) {
    console.error('Webhook error:', err.message);
  }
  res.sendStatus(200);
});

async function handleSendOtp(req, res) {
  try {
    const rawPhone = req.body.phone || req.body.phoneNumber || req.body.number || '';
    const digits = normalizeDigits(rawPhone);
    if (digits.length < 7) {
      return res.status(400).json({ success: false, message: 'Enter a valid phone number.' });
    }

    const chatId = findChatIdForPhone(digits);
    if (!chatId) {
      return res.status(404).json({
        success: false,
        requiresBotStart: true,
        botUrl: `https://t.me/${BOT_USERNAME}?start=${digits}`,
        message: `Open @${BOT_USERNAME} on Telegram, tap Start & Share My Phone Number, then tap Send Telegram OTP again.`
      });
    }

    const otp = String(req.body.otp || req.body.code || crypto.randomInt(100000, 1000000));
    const expiresAt = Date.now() + OTP_TTL_MS;
    for (const key of phoneKeys(digits)) {
      pendingOtps.set(key, { otp, expiresAt });
    }

    await telegramApi('sendMessage', {
      chat_id: chatId,
      text: `Your Univers Chat verification code is: ${otp}\n\nValid for 5 minutes. Do not share this code with anyone.`
    });

    return res.json({
      success: true,
      serverVerify: true,
      botUrl: `https://t.me/${BOT_USERNAME}`,
      message: `OTP sent via @${BOT_USERNAME}. Check Telegram!`
    });
  } catch (err) {
    return res.status(500).json({
      success: false,
      message: err.message || 'Failed to send Telegram OTP.'
    });
  }
}

async function handleVerifyOtp(req, res) {
  const rawPhone = req.body.phone || req.body.phoneNumber || '';
  const code = normalizeDigits(req.body.otp || req.body.code || '');
  if (code.length !== 6) {
    return res.status(400).json({ verified: false, message: 'Enter the full 6-digit OTP.' });
  }

  let record = null;
  for (const key of phoneKeys(rawPhone)) {
    if (pendingOtps.has(key)) {
      record = pendingOtps.get(key);
      break;
    }
  }
  if (!record) {
    return res.status(404).json({ verified: false, message: 'OTP session expired. Request a new code.' });
  }
  if (Date.now() > record.expiresAt) {
    return res.status(400).json({ verified: false, message: 'This 6-digit OTP expired after 5 minutes.' });
  }
  if (record.otp !== code) {
    return res.status(401).json({ verified: false, message: "That 6-digit code doesn't match." });
  }

  for (const key of phoneKeys(rawPhone)) {
    pendingOtps.delete(key);
  }
  return res.json({ verified: true, success: true });
}

app.post(['/send-otp', '/api/send-otp'], handleSendOtp);
app.post(['/verify-otp', '/api/verify-otp'], handleVerifyOtp);

app.listen(PORT, () => {
  console.log(`Univers Chat Telegram OTP server listening on port ${PORT} for @${BOT_USERNAME}`);
  if (BOT_TOKEN && !process.env.USE_WEBHOOK) {
    pollTelegramUpdates();
  }
});
