# Deploying to AWS

MVP definition of done: deployed on AWS (including the database), available online, daily price
collection and daily notifications. Suggested setup:

| Piece | AWS service |
|---|---|
| PostgreSQL | **RDS for PostgreSQL 16** (db.t4g.micro is enough for the MVP). pgvector is available on RDS when we need embeddings. |
| Backend (Spring Boot) | **App Runner** or **ECS Fargate** from the `backend/Dockerfile` image in ECR. Run a single instance: the daily job uses Spring's `@Scheduled`, so multiple instances would each run it. |
| Frontend (Next.js) | **App Runner** / ECS from `frontend/Dockerfile`, or **Amplify Hosting**. |
| Secrets | **Secrets Manager** / SSM Parameter Store, injected as env vars. |

## Backend environment variables

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | RDS connection (`jdbc:postgresql://<endpoint>:5432/dealstracker`) |
| `ADMIN_TOKEN` | Shared secret for `/api/admin/*` and listing/deleting alerts |
| `GEMINI_API_KEY` | Enables the in-app chat (Gemini free tier, the default) |
| `GEMINI_MODEL` | Gemini model for the chat (default `gemini-3.8-flash`) |
| `CHAT_PROVIDER` | `gemini` (default) or `claude` |
| `ANTHROPIC_API_KEY` | Enables the chat when `CHAT_PROVIDER=claude` |
| `TELEGRAM_BOT_TOKEN` | Enables Telegram alerts |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | Optional. Browser push key pair (base64url); if unset, one is generated on first start and kept in the `web_push_keys` table. Don't change it once browsers have subscribed. |
| `VAPID_SUBJECT` | Contact push services may use (`mailto:` or `https:` URL) |
| `CORS_ALLOWED_ORIGINS` | Frontend origin(s), comma-separated |
| `INGESTION_CRON` | Defaults to `0 0 6 * * *` (06:00 Israel time) |
| `SAMPLE_SOURCE_ENABLED` | Set to `false` once real feeds are configured |
| `TRANSPARENCY_SOURCE_ENABLED` | Set to `true` to read Price Transparency Law files |
| `YOCHANANOF_PORTAL_URL`, `YOCHANANOF_USERNAME`, `YOCHANANOF_PASSWORD` | Yochananof's portal and login (defaults: url.publishedprices.co.il, `yohananof`, no password) |
| `TIV_TAAM_USERNAME` / `_PASSWORD`, `OSHER_AD_USERNAME` / `_PASSWORD` | Override the publishedprices.co.il logins (defaults: `TivTaam`, `osherad`, no password) |

## Frontend environment variables

| Variable | Purpose |
|---|---|
| `BACKEND_URL` | Internal URL of the backend service |

## Steps (outline)

1. Create the RDS instance and a `dealstracker` database; allow access from the backend's security group only.
2. Push both images to ECR (`docker build` + `docker push`).
3. Create the backend service with the env vars above; health check `/actuator/health`.
4. Create the frontend service with `BACKEND_URL` pointing at the backend.
5. For a demo environment, seed history once: `curl -X POST -H "X-Admin-Token: $ADMIN_TOKEN" https://<backend>/api/admin/backfill?days=45`.
