# Univers Chat Telegram OTP Server (`@universchatapp`)

Ready-to-deploy Node.js backend for Render (`https://univers-chat.onrender.com`).

## Environment Variables on Render
- `TELEGRAM_BOT_TOKEN`: Token from `@BotFather` for `@universchatapp`
- `TELEGRAM_BOT_USERNAME`: `universchatapp`

## Endpoints
- `GET /health` — Health & bot configuration check
- `POST /send-otp` — Sends 6-digit OTP via `@universchatapp` to the linked phone number
- `POST /verify-otp` — Verifies the 6-digit OTP and 5-minute TTL
- `POST /telegram-webhook` — Optional webhook endpoint if `USE_WEBHOOK=true`
