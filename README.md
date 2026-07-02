# mSmartCart — E-Commerce Microservices Platform

A full-stack e-commerce application built with a microservices architecture. 6 independent Spring Boot services discover each other via Netflix Eureka and communicate over OpenFeign (with Resilience4j circuit breakers), secured by self-issued JWT auth (httpOnly cookies), and deployed together using Docker Compose.

---

## Architecture

```
React Frontend (Vite + Redux Toolkit + Tailwind CSS)
        |
   API Gateway (Spring Cloud Gateway :8083)
        |
 ┌──────┬───────┬────────┬────────┬──────────┐
 │      │       │        │        │
Product User   Cart    Order  Payment
:8081  :8082  :8084   :8085   :8086
  │      │       │        │        │
MySQL  MySQL  MySQL   MySQL   MySQL

All 6 services above register with:
Eureka Server (:8761) — service discovery
```

Each service has its own isolated MySQL database. Services locate each other through Eureka rather than
hardcoded URLs; Feign calls between services are wrapped in Resilience4j circuit breakers that fall back to
degraded default responses if a dependency is unavailable.

---

## Services

| Service | Port | Database | Responsibility |
|---|---|---|---|
| eureka-server | 8761 | — | Service registry / discovery |
| gateway-service | 8083 | — | Routes all requests, handles CORS, validates JWT |
| product-service | 8081 | productdb | Product CRUD (write ops ADMIN-only) |
| user-service | 8082 | userdb | Auth (register/login/logout), user profiles |
| cart-service | 8084 | cartdb | Cart management, item add/remove/update |
| order-service | 8085 | orderdb | Order creation and tracking (update/delete ADMIN-only) |
| payment-service | 8086 | paymentdb | Razorpay payment + signature verification |
| frontend | 5173 | — | React 19 SPA |

---

## Tech Stack

**Frontend:** React 19, Vite, Redux Toolkit, Tailwind CSS, React Router v7, Axios

**Backend:** Java 21, Spring Boot, Spring Security (OAuth2 Resource Server), Spring Cloud Gateway, OpenFeign, Netflix Eureka (service discovery), Resilience4j (circuit breakers), Spring AOP, JPA/Hibernate, MySQL

**Auth:** Self-issued JWT (HS256), BCrypt password hashing, `httpOnly` + `Secure` + `SameSite=Strict` cookie — no third-party identity provider

**DevOps:** Docker, Docker Compose

**Payments:** Razorpay (order creation + HMAC-SHA256 signature verification)

---

## Key Features

- **Cookie-based JWT auth** — `user-service` issues a signed JWT on login, delivered as an `httpOnly`/`Secure`/`SameSite=Strict` cookie (never touched by JS, immune to XSS token theft). Every service validates it independently with a shared signing secret.
- **Role-based access control** — `USER` vs `ADMIN` roles enforced via `@PreAuthorize` at each service (e.g. only ADMIN can create/update/delete products, or update/delete orders and payments).
- **Protected Routes** — React frontend blocks unauthenticated access per route.
- **Real Razorpay Integration** — creates Razorpay orders, processes payments, verifies signatures server-side to prevent tampering.
- **Service discovery** — all services register with Eureka; Feign clients and gateway routes resolve targets by service name (`lb://`) instead of hardcoded URLs.
- **Circuit breakers with real fallbacks** — cart/order/payment services wrap their Feign clients in Resilience4j circuit breakers; if a dependency goes down, calls fail over to a degraded default response instead of a 500, and self-heal automatically once the dependency recovers.
- **AOP for cross-cutting concerns** — Feign call logging and service-layer execution timing are handled by Spring AOP aspects instead of duplicated try/catch and log statements in every service method.
- **Per-service DB isolation** — each microservice owns its own MySQL schema.
- **Secrets via environment variables** — JWT secret and Razorpay credentials are never hardcoded; they're injected via a gitignored `.env` file (see `.env.example`).
- **One-command deployment** — full stack via `docker compose up`.

---

## Getting Started

### Prerequisites
- Docker & Docker Compose installed

### Configure secrets

Copy the example env files and fill in real values:

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
```

- Root `.env` — `JWT_SECRET` (any long random string, shared across all backend services) and `RAZORPAY_KEY`/`RAZORPAY_SECRET` (from your Razorpay dashboard). Consumed by `docker-compose.yml` and injected into each service container.
- `frontend/.env` — `VITE_REACT_APP_RAZORPAY_KEY_ID` (Razorpay's public key_id — safe to expose client-side, needed by the checkout widget). Read by Vite at build time.

Both `.env` files are gitignored — never commit real secrets.

### Run

```bash
docker compose up -d --build
```

This starts: Eureka service registry, API Gateway, all 5 backend services, 5 MySQL instances, and the React frontend.

| URL | Service |
|---|---|
| http://localhost:5173 | Frontend |
| http://localhost:8083 | API Gateway |
| http://localhost:8761 | Eureka dashboard |

Common commands:

```bash
docker compose ps                    # check container status
docker compose logs -f <service>     # tail logs for one service
docker compose up -d --build <svc>   # rebuild + restart just one service after code changes
docker compose down                  # stop and remove containers (keeps DB volumes)
docker compose down -v               # also wipes DB volumes
```

### Local Development (without Docker)

```bash
# Frontend
cd frontend
npm install
npm run dev

# Any backend service (e.g. product-service) — requires Java 21
cd product-service
./mvnw spring-boot:run
```

> Each service's `application.yml` defaults to Docker-network hostnames (e.g. `product-db`, `eureka-server`) for datasource URLs and the Eureka registry. For local (non-Docker) runs, either run the databases + `eureka-server` via `docker compose up <db-name> eureka-server` and point `SPRING_DATASOURCE_URL`/`eureka.client.service-url.defaultZone` at `localhost:<published-port>`, or run the full stack via Docker Compose instead. Feign clients no longer use hardcoded target URLs — they resolve addresses via Eureka, so a locally-run service must be able to reach the registry to call or be called by anything else.

---

## Auth flow

- `POST /auth/register` — creates a user (`user-service`), password hashed with BCrypt.
- `POST /auth/login` — verifies credentials, issues a JWT (`role`, `email`, `name` claims) as an `httpOnly` cookie.
- `POST /auth/logout` — clears the cookie.
- `GET /user/me` — returns the current session's profile; used by the frontend on app boot to check if the cookie is still valid.
- Every other service validates the same JWT independently (symmetric HS256 key, shared via `JWT_SECRET`), reading it from the cookie for browser requests or from the `Authorization: Bearer` header for internal service-to-service Feign calls.

---

## Project Structure

```
microServiceSmartCartApp/
├── frontend/               # React 19 + Vite SPA
├── eureka-server/          # Service registry (Netflix Eureka)
├── gateway-service/        # Spring Cloud Gateway
├── product-service/        # Product management
├── user-service/           # Auth + user profile management
├── cart-service/           # Cart + item management
├── order-service/          # Order management
├── payment-service/        # Razorpay integration
├── .env.example            # Template for backend/docker-compose secrets
└── docker-compose.yml      # Full stack orchestration
```
