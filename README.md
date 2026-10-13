# Deals & Price Tracker

Tracks prices of baby products across Israeli stores, detects **genuine** price drops (and
fake "sales" built on inflated reference prices), and sends alerts.

**MVP scope:** diapers and baby food at local branches in Yad Eliyahu, Tel Aviv (Yochananof,
Osher Ad, Carrefour, Super-Pharm, Good Pharm, Tiv Taam).

- Product list, with a price table per product across stores
- Insights such as "Cheapest diaper (Size 4) is <brand> at <store>"
- In-app chat that answers questions about current prices (Claude)
- Daily price collection and daily alerts (Telegram)

## Layout

```
backend/   Java 21 + Spring Boot 3 (REST API, ingestion, deal detection, alerts, chat)
frontend/  Next.js 14 (App Router) + Tailwind CSS
docs/      Deployment notes
```

### Backend modules

| Package | What it does |
|---|---|
| `ingestion` | `PriceSource` interface; `SamplePriceSource` (synthetic data for dev), `TransparencyPriceSource` + `TransparencyFileParser` (Price Transparency Law XML files, plain or gzipped); `DailyJob` runs ingestion then alerts on a cron |
| `deals` | `DealDetector`: 30/60-day moving average, threshold filter (default 15%), fake-discount detection (inflated reference price, price raised just before a markdown), multi-buy per-unit pricing |
| `insights` | Cheapest per unit (per diaper / per 100 g) by category and size, genuine deals, suspicious sales |
| `notification` | `Notifier` interface, `TelegramNotifier`, `DealAlertService` (at most one alert per rule per day) |
| `chat` | `ChatService`: Claude via the official Anthropic Java SDK, grounded in the current price table |
| `api` | REST controllers |

### Data model

Changed from the original draft so the same product can be compared across stores: `products`
are identified by barcode, `product_listings` link a product to a store, and `price_snapshots`
belong to a listing. Multi-buy offers are stored as `promo_min_quantity` / `promo_total_price`.
See `backend/src/main/resources/db/migration`.

## Running locally

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

Set `ANTHROPIC_API_KEY` to enable chat and `TELEGRAM_BOT_TOKEN` to enable Telegram alerts.

## API

| Method | Path | |
|---|---|---|
| GET | `/api/products?category=DIAPERS\|BABY_FOOD&q=` | Product list with lowest price |
| GET | `/api/products/{id}` | Price table across stores, with deal evaluation |
| GET | `/api/products/{id}/history?days=60` | Price history |
| GET | `/api/insights` | Insights |
| GET | `/api/stores` | Tracked stores |
| POST | `/api/chat` | `{ "messages": [{ "role": "user", "content": "..." }] }` |
| POST | `/api/alerts` | `{ productId, channel: "TELEGRAM", recipientId, targetDiscountPercentage }` |
| GET/DELETE | `/api/alerts[/{id}]` | Admin token required |
| POST | `/api/admin/ingest`, `/api/admin/backfill?days=N` | Admin token required (`X-Admin-Token`) |

## Next steps

- [ ] Real data: per-chain discovery of the latest PriceFull/PromoFull files, and filling in
      `chain_code` / `store_code` for the Yad Eliyahu branches from each chain's Stores file
- [ ] Deploy to AWS (see [docs/aws-deployment.md](docs/aws-deployment.md))
- [ ] UI for subscribing to alerts
- [ ] WhatsApp and Web Push notifiers
- [ ] Product search by pasted URL (Feature 1 in the design doc)
