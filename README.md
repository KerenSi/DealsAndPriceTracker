# DealsAndPriceTracker

> **The Shahar and Keren Project**: tracks prices across Israeli retailers and alerts you to real deals, not fake discounts.

## Table of Contents

- [Executive Summary & Objective](#executive-summary--objective)
- [Key Functional Features](#key-functional-features)
- [MVP](#mvp)
- [Technical Stack](#technical-stack)
- [Core Database Schema (Draft)](#core-database-schema-draft)
- [High-Level Architecture](#high-level-architecture)

## Executive Summary & Objective

**Problem Statement:** Israeli retailers (e.g., supermarkets like Shufersal, Victory and Rami Levy, and pharmacies like Super-Pharm) frequently run marketing promotions, multi-buy offers, or temporary price cuts. Consumers struggle to tell significant savings apart from minor discount noise or artificial "sale" prices based on inflated reference prices.

**Objective:** Build a data-driven full-stack application that regularly ingests and tracks product prices across Israeli retail and supermarket sites, uses an AI agent to detect genuine, non-trivial price drops, and dispatches real-time alerts via Telegram/WhatsApp.

## Key Functional Features

### Feature 1: Product Tracking & Search

- **User Input:** Users paste a product URL or enter a search query via the web interface.
- **Product Normalization:** Extracts product name, brand, volume/quantity metric (e.g., 500g, 1L), baseline price, and store brand ID.

### Feature 2: Automated Price Ingestion Worker

- **Scheduled Ingestion:** Cron-driven worker (every 6–12 hours) fetching updated prices and active store promotions/multi-buys.
- **Time-Series Storage:** Saves historical price snapshots per product store location.

### Feature 3: Smart Deal & Fake Discount Detection Agent

- **Threshold Filter:** Ignores minor fluctuations or trivial discounts (e.g., < 15% drop).
- **AI Evaluation Rules:**
  - **Historical Moving Average:** Calculates a true 30/60-day average price baseline.
  - **Price Anchoring / Fake Discount Detection:** Flags items where the "original price" was raised right before being marked down.
  - **Multi-Buy Unit Price Calculation:** Evaluates 1+1 or "buy 3 for 50 ILS" offers to compute true per-unit savings.

### Feature 4: Alert & Notification Dispatcher

- **Supported Channels:**
  - **Telegram Bot API:** Instant direct message with a deal breakdown link.
  - **WhatsApp (Twilio / Green API / WhatsApp Business API):** Messaging integration.
  - **Web-App Push Notifications:** Web push alerts triggered via browser Service Workers (using the Web Push API / Firebase Cloud Messaging), even when the site isn't actively open.

## MVP

**Scope:** Collects diaper and baby food data from local branches in Yad Eliyahu of:

- Yochananof
- Osher Ad
- Carrefour
- Super-Pharm
- Good Pharm
- Tiv Taam

**Product list:** Diapers and baby food.

### MVP Features

- See the product list
- Choose a product and see its price table
- Insights, e.g. *"Cheapest diaper is `<brand>` in `<store>`"*
- In-app chat to ask questions

### Definition of Done

- [ ] Deployed on AWS (database on AWS as well)
- [ ] Available online
- [ ] Daily price collection
- [ ] Daily notification (if relevant)

## Technical Stack

| Layer | Technology Choice |
| --- | --- |
| **Backend** | Java 21 + Spring Boot 3 (Spring Web, Spring Data JPA, Spring AI) |
| **Frontend** | Next.js 14+ (App Router, React, Tailwind CSS, shadcn/ui) |
| **Database** | PostgreSQL + pgvector (stores time-series price snapshots & product embeddings) |
| **AI Framework** | Spring AI (integrated with OpenAI GPT-4o / Claude / Gemini APIs) |
| **Web Scraping / Data Ingestion** | Playwright / JSoup / Official Supermarket Open APIs (Cerelac / Israeli Food Transparency Law Data) |
| **Notifications** | Telegram Bot API / Web Push API / WhatsApp Green API |

## Core Database Schema (Draft)

```sql
-- Products Table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    brand VARCHAR(100),
    category VARCHAR(100),
    store_name VARCHAR(100) NOT NULL,
    product_url TEXT NOT NULL,
    image_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Price History Snapshots
CREATE TABLE price_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    price NUMERIC(10, 2) NOT NULL,
    original_reference_price NUMERIC(10, 2),
    is_promotion BOOLEAN DEFAULT FALSE,
    promotion_description TEXT,
    scraped_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- User Alert Subscriptions
CREATE TABLE alert_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    target_discount_percentage NUMERIC(5, 2) DEFAULT 15.00,
    notification_channel VARCHAR(50) NOT NULL, -- 'TELEGRAM', 'WHATSAPP', 'WEB_PUSH'
    channel_recipient_id VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);
```

## High-Level Architecture

<!-- TODO: export the architecture diagram from the PRD and save it as docs/architecture.png -->
![High-Level Architecture](docs/architecture.png)
