# mSmartCart — Runbook

Operational reference for running, rebuilding, testing, and troubleshooting this app day-to-day. For an architecture overview see `README.md`.

---

## 1. First-time setup

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
```

Edit both files with real values:
- **`.env`** (root, read by `docker-compose.yml`): `JWT_SECRET` (any long random string), `RAZORPAY_KEY` / `RAZORPAY_SECRET` (from your Razorpay dashboard).
- **`frontend/.env`**: `VITE_REACT_APP_RAZORPAY_KEY_ID` (Razorpay's public key_id).

Both are gitignored — never commit real values. Verify before committing anything:

```bash
git status --short .env frontend/.env   # should print nothing
```

---

## 2. Running the full stack (Docker Compose)

### Start everything

```bash
docker compose up -d --build
```

First run takes a few minutes (image builds + 5 MySQL healthchecks gating service startup). Subsequent runs are faster — Docker only rebuilds layers that changed.

### Check it's up

```bash
docker compose ps
```

Wait until backend services show `Up` and DBs show `Up (healthy)`. Then:

| URL | What |
|---|---|
| http://localhost:5173 | Frontend |
| http://localhost:8083 | API Gateway |
| http://localhost:8761 | Eureka dashboard (service registry) |
| http://localhost:8081 | product-service (direct) |
| http://localhost:8082 | user-service (direct) |
| http://localhost:8084 | cart-service (direct) |
| http://localhost:8085 | order-service (direct) |
| http://localhost:8086 | payment-service (direct) |

Services take longer to become fully ready than to just start — each one has to register with Eureka before
Feign/gateway routing to it works. Confirm all 6 (gateway + 5 backend services) show `UP` before testing:

```bash
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" | python3 -c "
import sys, json
d = json.load(sys.stdin)
for a in d['applications']['application']:
    insts = a['instance']
    if isinstance(insts, dict): insts = [insts]
    for i in insts: print(a['name'], '->', i['status'])
"
```
Or just open http://localhost:8761 in a browser.

### Stop / restart

```bash
docker compose stop              # pause containers, keep them (fast resume)
docker compose start             # resume paused containers

docker compose down              # stop + remove containers & network (keeps DB volumes)
docker compose down -v           # same, but ALSO deletes DB volumes — wipes all data

docker compose restart <service> # stop+start one service without recreating it
```

### Rebuild after code changes

Docker images are a snapshot — code changes aren't picked up until rebuilt.

```bash
docker compose up -d --build <service-name>     # rebuild + restart just one service
docker compose up -d --build                    # rebuild + restart everything
```

Examples:
```bash
docker compose up -d --build order-service
docker compose up -d --build frontend
```

Changing only `docker-compose.yml` (env vars, ports) doesn't need `--build`:
```bash
docker compose up -d
```

### Logs

```bash
docker compose logs -f <service>          # tail one service, e.g. gateway-service
docker logs <container-name> --tail 100   # e.g. microservicesmartcartapp-order-service-1
```

---

## 3. Local development (without Docker)

Each service's `application.yml` defaults to Docker-network hostnames (`product-db`, `user-service`, etc.) for datasource URLs and Feign target URLs — commented-out `localhost` lines are right above each active one if you want to switch.

### Java version

Services require **Java 21**. If multiple JDKs are installed, switch before any Maven command (in the same shell invocation — `JAVA_HOME` doesn't persist across separate commands):

```bash
use_java21   # or: export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 && export PATH=$JAVA_HOME/bin:$PATH
java -version
```

### Hybrid approach (recommended): infra in Docker, one service local

```bash
docker compose up -d eureka-server order-db user-db cart-db payment-db product-db   # start just the DB(s) you need

cd order-service
use_java21 && ./mvnw spring-boot:run
```

Then edit that service's `application.yml` to point at `localhost:<db-port>` instead of the Docker hostname (uncomment the `localhost` line, comment the Docker one).

Also point `eureka.client.service-url.defaultZone` at `http://localhost:8761/eureka/` instead of the Docker
hostname — `eureka-server`'s port 8761 is published to the host, so a locally-run service can reach it as long
as `eureka-server` itself is running (in Docker, per the command above, or also run locally). Without this, the
locally-run service won't register, and Feign calls to/from it (which now resolve targets via Eureka, not
hardcoded URLs) will fail to find it.

### Frontend

```bash
cd frontend
npm install
npm run dev       # http://localhost:5173, hot reload
npm run build     # production build → dist/
```

---

## 4. Database access

```bash
docker exec -it microservicesmartcartapp-user-db-1 mysql -uroot -proot userdb
docker exec -it microservicesmartcartapp-order-db-1 mysql -uroot -proot orderdb
docker exec -it microservicesmartcartapp-product-db-1 mysql -uroot -proot productdb
docker exec -it microservicesmartcartapp-cart-db-1 mysql -uroot -proot cartdb
docker exec -it microservicesmartcartapp-payment-db-1 mysql -uroot -proot paymentdb
```

Or from the host (each DB is also published):

```bash
mysql -h127.0.0.1 -P3308 -uroot -proot userdb     # userdb
mysql -h127.0.0.1 -P3307 -uroot -proot productdb  # productdb
mysql -h127.0.0.1 -P3309 -uroot -proot cartdb      # cartdb
mysql -h127.0.0.1 -P3310 -uroot -proot orderdb     # orderdb
mysql -h127.0.0.1 -P3311 -uroot -proot paymentdb   # paymentdb
```

### Reset a single service's DB (e.g. after a schema change JPA can't auto-migrate)

```bash
docker compose stop user-service user-db
docker compose rm -f user-service user-db
docker volume rm microservicesmartcartapp_userdb-data
docker compose up -d user-db user-service
```

`ddl-auto: update` only **adds** columns, never drops/renames them — an entity field rename or removal leaves stale columns behind (can violate `NOT NULL` constraints on inserts). Volume reset is the fastest fix in dev; only do this if you don't need the existing data.

---

## 5. Testing the auth flow directly (curl)

Useful for debugging without the frontend.

```bash
# Register
curl -s -X POST http://localhost:8083/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com","password":"password123","role":"USER"}'

# Login — cookie comes back in Set-Cookie header
curl -s -D - -o /dev/null -X POST http://localhost:8083/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' | grep -i set-cookie

# Extract the token and reuse it manually (curl doesn't honor Secure cookies over plain http like browsers do)
TOKEN=$(curl -s -D - -o /dev/null -X POST http://localhost:8083/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' \
  | grep -i "^set-cookie" | sed -n 's/.*auth_token=\([^;]*\).*/\1/p')

# Use it
curl -s -H "Cookie: auth_token=$TOKEN" http://localhost:8083/user/me
curl -s -H "Cookie: auth_token=$TOKEN" http://localhost:8083/carts
curl -s -H "Cookie: auth_token=$TOKEN" http://localhost:8083/orders

# Logout (clears the cookie)
curl -s -H "Cookie: auth_token=$TOKEN" -X POST http://localhost:8083/auth/logout
```

> Note: real browsers treat `localhost` as a secure context and will send `Secure` cookies over plain `http://localhost`. `curl` does not — it won't store or resend a `Secure` cookie received over HTTP, so `-c cookiejar.txt` / `-b cookiejar.txt` won't work here. Extract and pass the value manually as shown above, or test through the actual browser frontend.

### Role-gated endpoints (require ADMIN)

```bash
curl -s -H "Cookie: auth_token=$ADMIN_TOKEN" -X DELETE http://localhost:8083/products/1
curl -s -H "Cookie: auth_token=$ADMIN_TOKEN" http://localhost:8083/payments        # list all payments
curl -s -H "Cookie: auth_token=$ADMIN_TOKEN" -X PUT http://localhost:8083/orders/1 -d '...'
```
A non-ADMIN token gets `403` on all of these.

---

## 6. Testing the circuit breaker / fallback behavior

`cart-service`, `order-service`, `payment-service` have Resilience4j circuit breakers wrapping their Feign
clients. To see a fallback actually trigger:

```bash
# Stop a downstream dependency
docker compose stop product-service

# Add an item to cart (cart-service calls product-service via Feign to enrich it)
curl -s -H "Cookie: auth_token=$TOKEN" -X POST http://localhost:8083/carts/add \
  -H "Content-Type: application/json" -d '{"productId":2,"quantity":1}'
# Expect: 200 OK with a placeholder item ("title":"default-title") instead of a 500

# Bring it back
docker compose start product-service
# Wait for it to re-register with Eureka:
until curl -s http://localhost:8761/eureka/apps/PRODUCT-SERVICE | grep -q UP; do sleep 3; done
```

After restarting the dependency, the circuit breaker won't immediately resume calling it — Resilience4j keeps
it open for `waitDurationInOpenState` (default ~60s) before trying again (half-open), then needs a handful of
successful trial calls before fully closing. If you still see the fallback response right after restarting the
dependency, that's expected — retry the same request a few times over the next 30-60 seconds and it'll recover
on its own; no restart or manual intervention needed.

---

## 7. Common issues

| Symptom | Cause | Fix |
|---|---|---|
| `net::ERR_CONNECTION_REFUSED` from frontend | Gateway port mismatch or stack not fully started | `docker compose ps` — confirm gateway-service is `Up`; wait for DB healthchecks |
| 401 on every request despite logging in | Cookie not being sent | Confirm `withCredentials: true` in `frontend/src/utils/api.jsx`, and that the frontend origin is in gateway's CORS `allowedOrigins` |
| 500 on `/carts`, `/orders`, etc. right after `docker compose up` | Service still starting, request raced ahead of it | Wait ~30-60s after `docker compose up`, or poll a health endpoint before hitting the app |
| Registration fails with a DB constraint error | Stale schema from a previous entity shape (`ddl-auto: update` doesn't drop columns) | See §4 "Reset a single service's DB" |
| `Cannot access ... class file has wrong version` when running `./mvnw` locally | Wrong JDK on `PATH` | Run `use_java21` (or set `JAVA_HOME` to a JDK 21 install) in the **same** shell command as the `mvnw` call |
| Backend Feign call to another service 403s unexpectedly | An endpoint has an incorrect `@PreAuthorize` role requirement | Check the target controller — read endpoints (`GET`) generally shouldn't require ADMIN unless it's a genuinely admin-only listing |
| A Feign call fails immediately with no network delay, even though the downstream service is confirmed up | Resilience4j circuit breaker is still `OPEN` from a recent failure streak | Wait ~60s (`waitDurationInOpenState` default) and retry — it self-heals through half-open, no restart needed |
| Service fails to start with `IllegalStateException: No fallback instance ... found for feign client ...` | The fallback class referenced in `@FeignClient(fallback = X.class)` isn't a registered Spring bean | Add `@Component` to the fallback class — it must implement the client interface *and* be `@Component`-annotated |
| Changing `feign.circuitbreaker.enabled` (or similar) has no effect at all, no error | Wrong property name for the Spring Cloud OpenFeign version in use | Check the actual property via that version's `spring-cloud-openfeign-core-*.jar!/META-INF/spring-configuration-metadata.json`; this app uses `spring.cloud.openfeign.circuitbreaker.enabled` (4.1.1), not the older `feign.circuitbreaker.enabled` |
| A service never shows up in the Eureka dashboard | Service hasn't finished starting, or can't reach `eureka-server` | Check `docker compose logs <service>` for connection errors to `eureka-server:8761`; confirm `eureka-server` container is `Up` first |

---

## 8. Key concepts you'll run into editing this repo

### `lb://` in gateway routes
`gateway-service/application.yml` routes use `uri: lb://product-service` instead of a literal
`http://product-service:8081`. `lb://` means "don't connect directly — resolve this through the LoadBalancer
first." At request time, Spring Cloud Gateway asks Spring Cloud LoadBalancer for a live instance of that
service name, LoadBalancer asks Eureka for currently registered/healthy instances, picks one, and only then
does the actual HTTP call go out. Practical effect: if you rename a service or change its port, you don't touch
the gateway route — Eureka is the only place that needs to know the real address. The same mechanism is what
lets Feign clients work with no `url=` attribute (`@FeignClient(name = "product-service")`).

### The Spring Cloud BOM (`spring-cloud-dependencies`) in each `pom.xml`
Every service's `pom.xml` imports a `spring-cloud-dependencies` BOM ("Bill of Materials") — a version-pinning
manifest with no code of its own. It's what lets you add `spring-cloud-starter-netflix-eureka-client`,
`spring-cloud-starter-loadbalancer`, `spring-cloud-starter-circuitbreaker-resilience4j`, etc. **without a
version number** — the BOM supplies whichever version its release train says is mutually compatible.

This matters operationally because **not all services in this repo use the same BOM version**:

| Services | Spring Boot | Spring Cloud BOM |
|---|---|---|
| `gateway-service`, `cart-service`, `order-service`, `payment-service` | 3.3.1 | `2023.0.1` |
| `product-service`, `user-service` | 3.5.4 | `2025.0.0` |

If you add a new Spring Cloud dependency to a service, check its Spring Boot version first and use the matching
BOM — mixing BOM versions or pinning an explicit version that doesn't match the Boot version is a common source
of hard-to-diagnose `NoSuchMethodError`/`ClassNotFoundException` at runtime.

---

## 9. Quick reference — port map

| Port | Service |
|---|---|
| 5173 | frontend |
| 8083 | gateway-service |
| 8761 | eureka-server (dashboard + registry) |
| 8081 | product-service |
| 8082 | user-service |
| 8084 | cart-service |
| 8085 | order-service |
| 8086 | payment-service |
| 3307 | product-db (host-mapped) |
| 3308 | user-db (host-mapped) |
| 3309 | cart-db (host-mapped) |
| 3310 | order-db (host-mapped) |
| 3311 | payment-db (host-mapped) |
