# 🎬 CineBook — Concurrent Seat Booking System

A production-quality seat booking backend built with **Spring Boot 3**, **PostgreSQL**, and **Redis**, designed to demonstrate real-world concurrency patterns: distributed locking, pessimistic locking, Redis caching, rate limiting, and idempotency.

---

## ✨ Features

| Feature | Implementation |
|---|---|
| **Distributed Locking** | Redisson `RLock` via Redis — prevents double-booking under high concurrency |
| **Pessimistic DB Locking** | `SELECT ... FOR UPDATE` on seat rows |
| **Redis Caching** | Seat map cached for 10 s, evicted on booking |
| **Rate Limiting** | Per-user Redis rate limiter (5 req/s on `/api/bookings`) |
| **Idempotency** | Unique `Idempotency-Key` header — safe to retry bookings |
| **JWT Auth** | Stateless Bearer token authentication |
| **Flyway Migrations** | Version-controlled schema |
| **Seat Hold Expiry** | Background scheduler releases expired holds |
| **Premium Frontend** | Built-in SPA (no external framework) at `http://localhost:8080` |

---

## 🏗️ Tech Stack

- **Java 17** + **Spring Boot 3.3**
- **PostgreSQL 16** — primary data store
- **Redis 7** — distributed locking, caching, rate limiting *(required)*
- **Redisson** — Redis client with distributed data structures
- **Flyway** — database migrations
- **JWT (jjwt 0.12)** — authentication
- **Spring Security** — endpoint protection
- **Lombok** — boilerplate reduction
- **SpringDoc OpenAPI** — auto-generated API docs at `/swagger-ui.html`

---

## 📐 Architecture

```
Browser (SPA)
     │
     ▼
Spring Boot App (port 8080)
     ├── SecurityFilter (JWT)
     ├── RateLimiterFilter (Redisson → Redis)
     ├── /api/auth/**         — register / login
     ├── /api/shows/**        — list shows & seat maps  ← Redis cached
     └── /api/bookings/**     — create booking          ← distributed lock
              │
              ├──► PostgreSQL — seats, bookings, users
              └──► Redis      — cache, locks, rate limiters
```

---

## 🚀 Quick Start

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (for PostgreSQL + Redis)
- Java 17+ (if running app locally)

---

### Option A — Full Docker Compose (Recommended)

Starts everything: PostgreSQL + Redis + Spring Boot app.

```bash
docker compose up --build
```

Open **http://localhost:8080** in your browser.

---

### Option B — Local Development (IDE)

Start only the infrastructure with Docker, then run the app from your IDE or Maven.

```bash
# 1. Start PostgreSQL + Redis
docker compose up -d db redis

# 2. Run the Spring Boot app
./mvnw spring-boot:run
```

> ⚠️ **Redis is required.** The application will not start without a reachable Redis instance on `localhost:6379` (or the `REDIS_HOST`/`REDIS_PORT` environment variables).

---

## ⚙️ Configuration

All settings have sensible defaults. Override with environment variables:

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/seatbooking` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | DB username |
| `DB_PASSWORD` | `postgres` | DB password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password (if auth enabled) |
| `JWT_SECRET` | *(base64 key)* | HMAC-SHA secret for JWT signing |
| `JWT_EXPIRATION_MS` | `86400000` | Token TTL (24 hours) |

---

## 🌐 API Reference

### Auth

| Method | Endpoint | Body | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{username, email, password}` | No |
| `POST` | `/api/auth/login` | `{username, password}` | No |

### Shows

| Method | Endpoint | Auth |
|---|---|---|
| `GET` | `/api/shows` | No |
| `GET` | `/api/shows/{id}/seats` | No |

### Bookings

| Method | Endpoint | Headers | Auth |
|---|---|---|---|
| `POST` | `/api/bookings` | `Idempotency-Key: <uuid>` | Bearer JWT |
| `GET` | `/api/bookings/my` | — | Bearer JWT |

**Booking Request Body:**
```json
{
  "showId": 1,
  "seatIds": [3, 7, 12]
}
```

**Booking Response:**
```json
{
  "bookingId": 42,
  "status": "CONFIRMED",
  "seatIds": [3, 7, 12],
  "createdAt": "2026-08-21T15:30:00",
  "message": "Booking confirmed"
}
```

### Interactive API Docs

Swagger UI is available at: **http://localhost:8080/swagger-ui.html**

---

## 🎯 Concurrency Design

### Why Redis Is Required

Redis serves three critical roles:

1. **Distributed Cache** — `GET /api/shows/{id}/seats` is cached in Redis for 10 seconds. Evicted on every booking to ensure freshness.

2. **Rate Limiting** — Per-user sliding-window rate limiter (5 req/s) on the booking endpoint, backed by Redisson's `RRateLimiter`.

3. **Distributed Locking** — *(Extension point)* Redisson `RLock` is available for services that need cross-node mutual exclusion beyond the pessimistic DB lock.

### Booking Flow

```
POST /api/bookings
  │
  ├─ [Redis] Check idempotency key — if exists, return cached response
  ├─ [Postgres] SELECT seat FOR UPDATE (pessimistic lock per seat)
  ├─ Validate seat status == AVAILABLE or HELD
  ├─ Mark seat BOOKED
  ├─ Create Booking record
  ├─ [Redis] Evict seat cache
  └─ [Postgres] Save idempotency record
```

---

## 🗃️ Database Schema

```
app_user          show            seat
─────────         ──────          ──────────────────
id                id              id
username          title           seat_number
email             venue           status (AVAILABLE|HELD|BOOKED)
password_hash     start_time      show_id → show.id
role                              version (optimistic lock)
                                  held_by, held_at

booking                     booking_seat              idempotency_record
───────────────             ────────────              ──────────────────
id                          id                        id
user_id → app_user.id       booking_id → booking.id   idempotency_key
status                      seat_id → seat.id         booking_id
created_at                  price_at_booking          response_status
idempotency_key                                       response_body
                                                      created_at
```

---

## 🧪 Running Tests

```bash
./mvnw test
```

Concurrency tests are in `src/test/java/.../service/PessimisticConcurrencyTest.java`.

---

## 📦 Project Structure

```
src/
├── main/
│   ├── java/.../seat_booking_system/
│   │   ├── config/          — Redis, Cache, DataSeeder, Scheduling
│   │   ├── controller/      — Auth, Show, Booking, GlobalExceptionHandler
│   │   ├── dto/             — Request/Response DTOs
│   │   ├── entity/          — JPA Entities
│   │   ├── ratelimit/       — Redisson rate limiter filter
│   │   ├── repository/      — Spring Data JPA repositories
│   │   ├── scheduler/       — Seat hold expiry job
│   │   ├── security/        — JWT filter, SecurityConfig
│   │   └── service/         — Business logic
│   └── resources/
│       ├── db/migration/    — Flyway SQL migrations
│       ├── static/          — Frontend SPA (index.html, style.css, app.js)
│       └── application.yml  — Configuration
└── test/                    — Integration & concurrency tests
```

---

## 👨‍💻 Author

Built by **Anish Gupta** as a learning project exploring concurrency, distributed systems patterns, and modern Spring Boot architecture.

---

## 📄 License

MIT License — feel free to use, fork, and learn from this project.
