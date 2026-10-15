# DealsAndPriceTracker

> **The Shahar and Keren Project**: tracks prices across Israeli retailers and alerts you to real deals, not fake discounts.

## Table of Contents

- [Executive Summary & Objective](#executive-summary--objective)
- [Key Functional Features](#key-functional-features)
- [MVP](#mvp)
- [Technical Stack](#technical-stack)
- [Repository Layout](#repository-layout)
- [Database Schema](#database-schema)
- [High-Level Architecture](#high-level-architecture)
- [Running Locally](#running-locally)
- [Deploying](#deploying)
- [API](#api)
- [Roadmap](#roadmap)

## Executive Summary & Objective

**Problem Statement:** Israeli retailers (e.g., supermarkets like Shufersal, Victory and Rami Levy, and pharmacies like Super-Pharm) frequently run marketing promotions, multi-buy offers, or temporary price cuts. Consumers struggle to tell significant savings apart from minor discount noise or artificial "sale" prices based on inflated reference prices.

**Objective:** Build a data-driven full-stack application that regularly ingests and tracks product prices across Israeli retail and supermarket sites, uses an AI agent to detect genuine, non-trivial price drops, and dispatches real-time alerts via Telegram/WhatsApp.

## Key Functional Features

### Feature 1: Product Tracking & Search

- **User Input:** Users paste a product URL or enter a search query via the web interface.
- **Product Normalization:** Extracts product name, brand, volume/quantity metric (e.g., 500g, 1L), baseline price, and store brand ID.

> **Status:** Search by brand/name and category filter are implemented. Products are normalized by barcode, with brand, size label (e.g. "Size 4", "Stage 1"), pack quantity and unit type. Adding a product by pasted URL is not built yet.

### Feature 2: Automated Price Ingestion Worker

- **Scheduled Ingestion:** Cron-driven worker (every 6–12 hours) fetching updated prices and active store promotions/multi-buys.
- **Time-Series Storage:** Saves historical price snapshots per product store location.

> **Status:** Implemented as a scheduled job (`DailyJob`, daily at 06:00 Israel time for the MVP; configurable via `INGESTION_CRON`). Sources plug in through a `PriceSource` interface. `TransparencyPriceSource` reads the real Price Transparency Law files (`PriceFull` / `PromoFull` XML, plain or gzipped, UTF-8 or UTF-16): it logs in to each chain's portal, finds the newest files for the branch and parses them. A branch without a store code gets one from the chain's `Stores` file by matching its `store_match` words (e.g. "יגאל אלון"). Turn it on with `TRANSPARENCY_SOURCE_ENABLED=true` and `SAMPLE_SOURCE_ENABLED=false`; the synthetic `SamplePriceSource` remains the default for development.

### Feature 3: Smart Deal & Fake Discount Detection Agent

- **Threshold Filter:** Ignores minor fluctuations or trivial discounts (e.g., < 15% drop).
- **AI Evaluation Rules:**
  - **Historical Moving Average:** Calculates a true 30/60-day average price baseline.
  - **Price Anchoring / Fake Discount Detection:** Flags items where the "original price" was raised right before being marked down.
  - **Multi-Buy Unit Price Calculation:** Evaluates 1+1 or "buy 3 for 50 ILS" offers to compute true per-unit savings.

> **Status:** All rules are implemented in `DealDetector` (deterministic, unit-tested). A sale is flagged as fake when the advertised original price is higher than any regular price in the last 30 days, or when the regular price was raised in the week before the markdown. Each evaluation includes human-readable reasons, shown in the UI.

### Feature 4: Alert & Notification Dispatcher

- **Supported Channels:**
  - **Telegram Bot API:** Instant direct message with a deal breakdown link.
  - **WhatsApp (Twilio / Green API / WhatsApp Business API):** Messaging integration.
  - **Web-App Push Notifications:** Web push alerts triggered via browser Service Workers (using the Web Push API / Firebase Cloud Messaging), even when the site isn't actively open.

> **Status:** Browser push is implemented: logged-in users turn on "Deal alerts" on the home page, and after each ingestion every browser they turned it on in gets a notification for each real deal on a starred product (standard Web Push with VAPID, no Firebase account needed). A deal is pushed once, and again only if the price drops further or a week has passed. Telegram is also implemented (`TelegramNotifier`, rules created through the API, at most once per rule per day). WhatsApp isn't built yet.

## MVP

**Scope:** Collects diaper and baby food data from these Tel Aviv branches (around Yigal Alon / La Guardia / Moshe Dayan):

| Branch | Portal | Status |
| --- | --- | --- |
| Tiv Taam Yigal Alon (store 080, Yigal Alon 94) | publishedprices.co.il (Cerberus) | Verified live |
| Osher Ad Yigal Alon (store 024, Kreminitzky 3) | publishedprices.co.il (Cerberus) | Verified live |
| Yochananof Yigal Alon (store 027, Yigal Alon 57) | publishedprices.co.il (Cerberus) | Verified live |
| Shufersal Deal Yigal Alon | prices.shufersal.co.il | Configured, not verified yet (the site doesn't answer outside Israel) |
| Super-Pharm Yigal Alon (2 branches) | prices.super-pharm.co.il | Blocked: a bot challenge (Reblaze) rejects scripted and headless-browser requests from cloud IPs |
| Carrefour La Guardia (store 205, La Guardia 78) | prices.carrefour.co.il | Verified live |
| Good Pharm Moshe Dayan (store 852, Moshe Dayan 66) | goodpharm.binaprojects.com | Verified live |

Store codes that aren't known yet are looked up automatically on the first ingestion and saved.

**Product list:** Diapers and baby food.

### MVP Features

- [x] See the product list
- [x] Choose a product and see its price table (plus a 60-day price history chart)
- [x] Insights: every real deal on top, then *"Cheapest diaper is `<brand>` in `<store>`"* (per diaper / per 100 g, within the same diaper size or formula stage) for the products you starred; at least 2 cheapest insights, up to 4 insights in total when there are few deals
- [x] Accounts (email + password). Per user: star products, mark products as not interesting (their deals and prices are left out of insights), and a "show similar products" toggle for the cheapest insights
- [x] In-app chat to ask questions (Gemini free tier or Claude, grounded in the current price table)

### Definition of Done

- [ ] Deployed on AWS (database on AWS as well): see [docs/aws-deployment.md](docs/aws-deployment.md)
- [ ] Available online
- [ ] Daily price collection: the scheduled job and real store feeds are in place (Tiv Taam, Osher Ad, Yochananof, Carrefour and Good Pharm verified); Shufersal and Super-Pharm are still open
- [ ] Daily notification (if relevant): browser push and Telegram alerts are in place; needs deployment (push needs HTTPS)

## Technical Stack

| Layer | Technology Choice | As built |
| --- | --- | --- |
| **Backend** | Java 21 + Spring Boot 3 (Spring Web, Spring Data JPA, Spring AI) | Java 21 + Spring Boot 3.5 (Web, Data JPA, Validation, Actuator), Flyway migrations |
| **Frontend** | Next.js 14+ (App Router, React, Tailwind CSS, shadcn/ui) | Next.js 14 (App Router) + Tailwind CSS; shadcn/ui not added yet |
| **Database** | PostgreSQL + pgvector (stores time-series price snapshots & product embeddings) | PostgreSQL 16; pgvector not used yet (no embeddings in the MVP) |
| **AI Framework** | Spring AI (integrated with OpenAI GPT-4o / Claude / Gemini APIs) | Gemini REST API (free tier, default) or the official Anthropic Java SDK (Claude) for the chat; deal detection is rule-based |
| **Web Scraping / Data Ingestion** | Playwright / JSoup / Official Supermarket Open APIs (Cerelac / Israeli Food Transparency Law Data) | Price Transparency Law files, found through each chain's portal (Cerberus, Bina Projects, download pages); synthetic source for development |
| **Notifications** | Telegram Bot API / Web Push API / WhatsApp Green API | Web Push API (VAPID, implemented with the JDK only) + Telegram Bot API |

## Repository Layout

```
backend/   Java 21 + Spring Boot 3 (REST API, ingestion, deal detection, alerts, chat)
frontend/  Next.js 14 (App Router) + Tailwind CSS
docs/      Deployment notes
```

| Backend package | What it does |
| --- | --- |
| `ingestion` | `PriceSource` interface, `SamplePriceSource`, `TransparencyPriceSource` + `TransparencyFileParser`, chain portal clients in `ingestion.portal`, `IngestionService`, `DailyJob` |
| `deals` | `DealDetector` and `PriceAnalysisService` (latest price + history → deal evaluation and unit price) |
| `insights` | Genuine deals first, then cheapest per unit for the sizes / stages the user watches, skipping products marked not interesting |
| `auth` | `AuthService` (accounts, BCrypt passwords, bearer-token sessions) and `PreferencesService` (product marks, settings) |
| `notification` | `Notifier` interface, `TelegramNotifier`, `DealAlertService`; `notification.push`: Web Push encryption and VAPID (`WebPushCrypto`), `WebPushSender`, `DealPushService` |
| `chat` | `ChatService`: Gemini via its REST API (default) or Claude via the Anthropic Java SDK |
| `api` | REST controllers |

## Database Schema

The draft schema from the PRD stored one `products` row per store. To compare the same product across stores, the implemented schema splits it in two:

| Table | Purpose |
| --- | --- |
| `stores` | Store branches (chain, branch name, city, Price Transparency Law chain/store codes) |
| `products` | Canonical product, unique by barcode: title, brand, category, size label, unit quantity and type |
| `product_listings` | A product sold at a specific store (optional product URL) |
| `price_snapshots` | Time series per listing: `price`, `original_reference_price`, `is_promotion`, `promotion_description`, plus `promo_min_quantity` / `promo_total_price` for multi-buy offers |
| `alert_rules` | As in the draft, plus `last_notified_at` to send at most one alert per day |
| `push_subscriptions` | Browsers that turned on deal alerts: push endpoint and encryption keys, per user |
| `push_deal_notifications` | Last deal pushed per user and product, so an ongoing deal isn't pushed daily |
| `web_push_keys` | The server's generated VAPID key pair (unless set through env vars) |

Full DDL: [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration).

## High-Level Architecture

<!-- TODO: export the architecture diagram from the PRD and save it as docs/architecture.png -->

```mermaid
flowchart LR
    feeds[Store price feeds<br/>Price Transparency Law files] --> ingest
    subgraph backend[Spring Boot backend]
        ingest[Daily ingestion job] --> db[(PostgreSQL)]
        db --> detect[Deal detector]
        detect --> insights[Insights]
        detect --> alerts[Alert service]
        db --> chat[Chat service]
    end
    alerts --> telegram[Telegram]
    alerts --> push[Browser push services]
    chat <--> llm[Gemini or Claude API]
    frontend[Next.js frontend] <--> backend
```

## Running Locally

Requirements: Java 21, Maven, Node 20+, PostgreSQL 16 (or Docker).

```bash
# Everything in Docker
docker compose up --build

# Or run the pieces yourself
cd backend && ADMIN_TOKEN=devtoken mvn spring-boot:run      # expects Postgres on localhost:5432
cd frontend && npm install && npm run dev                    # http://localhost:3000
```

Load ~45 days of synthetic history so deal detection has something to work with:

```bash
curl -X POST -H "X-Admin-Token: devtoken" "http://localhost:8080/api/admin/backfill?days=45"
curl -X POST -H "X-Admin-Token: devtoken" "http://localhost:8080/api/admin/ingest"
```

Set `GEMINI_API_KEY` (free from Google AI Studio) to enable chat and `TELEGRAM_BOT_TOKEN` to enable Telegram alerts. Browser push works out of the box on `http://localhost:3000`; anywhere else the site must be served over HTTPS. On iPhone, web push only works after adding the site to the Home Screen. All configuration options are listed in [docs/aws-deployment.md](docs/aws-deployment.md).

## Deploying

The app runs on AWS on a single EC2 host (about $25/month) with HTTPS and automatic deploys
from `main`. See [deploy/README.md](deploy/README.md).

## API

| Method | Path | |
| --- | --- | --- |
| GET | `/api/products?category=DIAPERS\|BABY_FOOD&q=` | Product list with lowest price |
| GET | `/api/products/{id}` | Price table across stores, with deal evaluation |
| GET | `/api/products/{id}/history?days=60` | Price history |
| GET | `/api/insights` | Insights (send the bearer token for personal insights) |
| POST | `/api/auth/register`, `/api/auth/login` | `{ email, password }` → `{ token, expiresAt, user }` |
| POST | `/api/auth/logout`, GET `/api/auth/me` | `Authorization: Bearer <token>` |
| PUT / DELETE | `/api/me/products/{productId}/interest` | `{ interest: "WATCHED" \| "NOT_INTERESTED" }`, or clear the mark (bearer token required) |
| PUT | `/api/me/settings` | `{ showSimilarProducts }` (bearer token required) |
| GET | `/api/push/public-key` | The VAPID key browsers subscribe with |
| PUT / DELETE | `/api/me/push-subscription` | Save this browser's `PushSubscription` JSON, or forget `{ endpoint }` (bearer token required) |
| POST | `/api/me/push-subscription/test` | Send a test notification to the user's browsers (bearer token required) |
| GET | `/api/stores` | Tracked stores |
| POST | `/api/chat` | `{ "messages": [{ "role": "user", "content": "..." }] }` |
| POST | `/api/alerts` | `{ productId, channel: "TELEGRAM", recipientId, targetDiscountPercentage }` |
| GET / DELETE | `/api/alerts[/{id}]` | Admin token required |
| POST | `/api/admin/ingest`, `/api/admin/backfill?days=N` | Admin token required (`X-Admin-Token` header) |

## Roadmap

- [x] Real data: per-chain discovery of the latest `PriceFull` / `PromoFull` files, and store codes from each chain's Stores file
- [x] Verify the Yochananof, Carrefour and Good Pharm portals against live data
- [ ] Shufersal: verify from an Israeli IP (e.g. AWS il-central-1)
- [ ] Super-Pharm: get past the Reblaze bot challenge, or find another source
- [ ] Deploy to AWS
- [x] Browser push alerts for real deals on starred products
- [ ] WhatsApp notifier
- [ ] Add a product by pasting its URL (Feature 1)
