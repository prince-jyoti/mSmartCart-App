# mSmartCart — Interview Prep

## 1. The 30-second pitch

"I built **mSmartCart**, a full-stack e-commerce platform using a microservices architecture — 7 independent
Spring Boot services (product, user, cart, order, payment, gateway, plus a Eureka service registry) each with
their own MySQL database (except the registry), fronted by a React 19 / Redux Toolkit / Tailwind SPA.
Authentication is self-issued JWT (HS256), delivered as an `httpOnly`/`Secure`/`SameSite=Strict` cookie rather
than a third-party identity provider, with role-based access control (`USER`/`ADMIN`) enforced at each service.
Services discover each other via Netflix Eureka and talk via OpenFeign, with Resilience4j circuit breakers
providing graceful degradation when a downstream service is unavailable, and Spring AOP handling cross-cutting
logging/timing concerns. I integrated real Razorpay payments with server-side HMAC signature verification. The
whole stack runs with one `docker compose up`."

---

## 2. Walkthrough by topic

### Why microservices?
- Each domain (product, user, cart, order, payment) is independently deployable with its own schema —
  database-per-service, no shared DB.
- Demonstrates understanding of bounded contexts and service isolation, even at small scale.

### API Gateway (Spring Cloud Gateway, :8083)
- Single entry point for the frontend — routes `/auth/**`, `/products/**`, `/user/**`, `/carts/**`,
  `/orders/**`, `/payments/**` to the right service.
- JWT validation happens here too (`SecurityConfig` uses `oauth2ResourceServer().jwt()` with
  `@EnableWebFluxSecurity`) — unauthenticated requests to protected route prefixes are rejected before
  reaching downstream services. `/auth/**` is explicitly `permitAll()` since login/register can't require a
  token yet.
- `LoggingFilter` — cross-cutting request/response logging at the edge.

### Service discovery — Netflix Eureka
- A 7th service, `eureka-server` (port 8761), acts as the registry — standalone mode
  (`register-with-eureka=false`, `fetch-registry=false` on itself), self-preservation disabled (appropriate for
  a small dev cluster; would reconsider for a real multi-instance production deployment).
- All 6 other services register on startup via `spring.application.name` + `eureka.client.service-url.defaultZone`.
- Feign clients no longer hardcode target URLs — `@FeignClient(name = "product-service", ...)` (no `url=`
  attribute) resolves the actual address through Spring Cloud LoadBalancer + Eureka at call time.
- The gateway's routes changed from static `uri: http://product-service:8081` to `uri: lb://product-service` —
  `lb://` tells Spring Cloud Gateway not to connect directly, but to route the request through
  `ReactiveLoadBalancerClientFilter` first: that filter asks Spring Cloud LoadBalancer for an instance of
  `product-service`, LoadBalancer asks the `DiscoveryClient` (Eureka) for currently registered/healthy
  instances, picks one, rewrites the URI to the real address, and only then does the actual HTTP call go out.
  Same underlying mechanism is what makes `@FeignClient(name = "product-service")` work with no `url=` at all.
- **Honest caveat**: Eureka is in Spring Cloud's maintenance mode — still fully supported and extremely common
  in existing systems, but Consul or a cloud-native platform's built-in discovery (e.g. Kubernetes Services)
  would be the more forward-looking choice for a brand-new production system today. I picked Eureka here
  because it needs no new *type* of infrastructure (it's just another Spring Boot app) and integrates natively
  with the Feign setup already in place.

### Authentication — self-issued JWT, not a third-party IdP
This app originally used Keycloak (OAuth2/SSO). I migrated it off Keycloak to a self-issued JWT scheme —
worth being able to explain *both* the original design and why/how I changed it, since "I replaced an
identity provider with hand-rolled auth" is exactly the kind of decision an interviewer will probe.

- `user-service` owns identity: `POST /auth/register` (BCrypt-hashed password), `POST /auth/login` (verifies
  password, issues a JWT), `POST /auth/logout` (clears the cookie).
- The JWT is signed with **HMAC-SHA256** using a secret shared across all 6 services (`JWT_SECRET` env var) —
  symmetric key, not RSA/JWKS, since there's no separate authorization server anymore. Claims: `sub` (user id),
  `email`, `name`, `role`.
- Delivered as a `Set-Cookie: auth_token=...; HttpOnly; Secure; SameSite=Strict` header on login — **never** in
  the response body, and never touched by frontend JS. This is a deliberate hardening over the more common
  "JWT in `localStorage`" pattern: `HttpOnly` makes the token invisible to any injected/XSS script, `Secure`
  requires HTTPS (or the browser's localhost exception for dev), `SameSite=Strict` stops the cookie being sent
  on cross-site requests (CSRF mitigation).
- **Gateway AND every individual service validate the token independently** (defense in depth — each service
  has its own `SecurityConfig` with a `JwtDecoder` bean built from the shared secret via
  `NimbusJwtDecoder.withSecretKey(...)`).
- A custom `BearerTokenResolver` (`CookieBearerTokenResolver`, one per service) reads the JWT from the
  `auth_token` cookie for browser-originated requests, falling back to the standard
  `DefaultBearerTokenResolver` (reads the `Authorization: Bearer` header) for service-to-service Feign calls —
  one resolver, two transport mechanisms.
- Frontend has no `keycloak-js` anymore — `App.jsx` calls `GET /user/me` on boot (cookie sent automatically via
  `axios`'s `withCredentials: true`) to determine auth state, and `ProtectedRoute.jsx` gates routes off that
  Redux state.

### Role-based access control
- `User.role` is `"USER"` or `"ADMIN"`, set at registration (normalized server-side — any non-`"ADMIN"` value
  becomes `"USER"`, so a malformed/garbage client value can't produce an unexpected role string).
- A `JwtAuthenticationConverter` bean in each service maps the `role` claim to a Spring Security
  `ROLE_<role>` `GrantedAuthority`, which is what makes `@PreAuthorize("hasRole('ADMIN')")` actually work.
- Write endpoints are ADMIN-gated: `product-service` (create/update/delete), `order-service` (update/delete),
  `payment-service` (list-all/update/delete). Read endpoints and cart operations are open to any authenticated
  user. `order-service`'s `GET /orders` additionally branches internally — ADMIN sees every order, USER sees
  only their own (`OrderService.getAllOrders(role)`).

### Inter-service communication — OpenFeign
- Cart/order/payment services call product-service (price/details) and user-service (resolve the user) via
  Feign clients.
- `CustomErrorDecoder` — Feign error responses are handled explicitly rather than letting raw exceptions bubble up.
- `FeignClientConfig` — a `RequestInterceptor` propagates the caller's JWT (`Authorization: Bearer <token>`)
  onto outgoing Feign calls, reading it out of `SecurityContextHolder` (populated by the cookie resolver on the
  inbound request) — so the downstream service's own `SecurityConfig` validates it the same way it would a
  header-based request.

### Order flow — service-layer composition
- `OrderService.createOrder()` resolves the *current caller* via Feign (`getCurrentUser()` → `GET /user/me`),
  builds `OrderItem`s from the request payload, persists the order.
- `toOrderRes()` enriches the order by calling back out to product-service (titles/images), payment-service
  (payment status), and user-service — but resolves the **order's actual owner** by id
  (`getUserDTOById(order.getUserId())` → `GET /user/{id}`), not the current caller. This distinction matters:
  early in the JWT migration I had this calling `getCurrentUser()` here too, which meant an ADMIN listing
  *everyone's* orders saw their own email on every single order instead of the real owner's — a good example
  of "resolve the resource's owner by id, not the caller" as a general API-composition principle.
- Known inefficiency: each order item triggers its own Feign call to product-service in `toOrderItemDTO`
  (N+1 pattern) — good "what would you optimize" answer (batch endpoint, caching).

### Payments — Razorpay integration
- `payment-service` creates a Razorpay order (`createRazorpayOrder` via Unirest) and, on the frontend callback,
  verifies the payment server-side using **HMAC-SHA256** over `orderId|paymentId` compared against Razorpay's
  signature — before trusting the payment status.
- Security principle: never trust the client's claim that payment succeeded.
- Razorpay's `key_id` (public, client-visible by design — like Stripe's publishable key) lives in
  `frontend/.env` for the checkout widget; the `key_secret` lives only in the backend's `.env` → injected into
  `payment-service` as an environment variable, never hardcoded in a tracked file and never shipped to the
  browser.

### Frontend
- React 19 + Redux Toolkit slices per domain (auth, cart, product, order, payment) — clean separation of state by feature.
- `useCheckout` custom hook encapsulates the checkout flow: create order → load Razorpay script → open payment
  modal → verify → redirect to success page.
- `ProtectedRoute.jsx` gates routes off Redux `auth.isAuthenticated`, populated by a `GET /user/me` call on
  app boot (`authSlice.fetchCurrentUser`) rather than a Keycloak SDK session check.
- `api.jsx`'s axios instance uses `withCredentials: true` so the `httpOnly` cookie is sent automatically — no
  manual `Authorization` header attachment, no token in `localStorage`.

### DevOps
- `docker-compose.yml`: 5 backend services + 5 MySQL containers (each with healthchecks so dependents wait for
  DB readiness) + gateway + `eureka-server` + frontend, all on one bridge network. No identity-provider
  container anymore. Every service `depends_on` the registry so it's up before they try to register.
- Secrets (`JWT_SECRET`, `RAZORPAY_KEY`, `RAZORPAY_SECRET`) are injected via a gitignored root `.env` file, not
  hardcoded in `application.yml` — `.env.example` documents what's required without real values.
- One-command deployment — `docker compose up -d --build`.

---

## 3. Resilience / Feign fallbacks — now actually wired up (was previously the #1 "honest gap")

This section used to document that the `@FeignClient(fallback = ...)` classes were declared but inert — no
circuit breaker was actually enabled. That gap is now closed:

- `spring-cloud-starter-circuitbreaker-resilience4j` added to `cart-service`, `order-service`, `payment-service`.
- `spring.cloud.openfeign.circuitbreaker.enabled: true` set in each of their `application.yml`.
- No custom Resilience4j tuning (failure-rate thresholds, sliding window size, etc.) — running on library
  defaults, since the goal was specifically "make the already-declared fallbacks work," not build a fully
  production-tuned resilience policy.

**Verified live, not just configured**: stopped `product-service` mid-session, called `POST /carts/add` through
the gateway, and got back `200 OK` with the fallback's `"default-title"` placeholder product instead of a 500 —
`ProductServiceFallback` actually activated. Restarted `product-service`; the circuit breaker self-healed
through its half-open state and normal responses resumed within about a minute (Resilience4j's default
`waitDurationInOpenState`).

**Two real bugs surfaced getting this working — good "tell me about a bug" material**, see section 4.

**How to frame the remaining gap honestly**: no custom per-client tuning exists yet (I'm relying on Resilience4j
library defaults — e.g. `slidingWindowSize=100`, `waitDurationInOpenState=60s`), and there's no
`@CircuitBreaker`-annotated fine-grained control anywhere. For a production system I'd tune thresholds per
client based on actual traffic/latency characteristics rather than defaults.

---

## 4. Bugs found and fixed during the Keycloak → JWT migration (great "tell me about a bug you found" material)

These weren't hypothetical — I found and fixed each of these while migrating and verifying the new auth flow
end-to-end, several of which were **pre-existing and silently broken even under Keycloak**, just never
surfaced because nothing exercised that code path correctly.

- **`@PreAuthorize` never worked, anywhere, ever.** No service had `@EnableMethodSecurity`, and no
  `JwtAuthenticationConverter` mapped any claim to a Spring Security authority. Every `@PreAuthorize("hasRole('ADMIN')")`
  in the codebase was silently ignored — meaning every "admin-only" endpoint was actually open to any
  authenticated user the entire time, under Keycloak too. Fixed by adding `@EnableMethodSecurity` +
  a converter mapping the JWT's `role` claim to a `ROLE_*` authority.
- **Once that was fixed, it exposed a real bug**: `product-service`'s `GET /products/{id}` was
  `@PreAuthorize("hasRole('ADMIN')")`-gated — nonsensical for a single product detail view that regular users
  (and cart/order enrichment calls) need constantly. It had just never been *enforced* before. Removed the
  restriction from that one read endpoint.
- **Gateway port mismatch**: `docker-compose.yml` mapped `8083:8080`, but the gateway's own `application.yml`
  has `server.port: 8083` — nothing was ever listening on 8080 inside the container. This made the gateway
  completely unreachable from the host, a pre-existing bug that had nothing to do with the auth migration.
- **JWT algorithm mismatch**: `jjwt`'s `Jwts.builder().signWith(key)` auto-selects the strongest HMAC variant
  the key length supports — a 53-byte secret got signed with **HS384**, while
  `NimbusJwtDecoder.withSecretKey(key)` on the resource-server side defaults to expecting **HS256**. Silent
  401s with no useful error until I decoded the JWT header and compared `alg` on both sides. Fixed by pinning
  both the signing and verification side to HS256 explicitly.
- **Cookie-only resolver broke internal Feign calls**: I initially wrote `CookieBearerTokenResolver` to *only*
  check the cookie, which broke every service-to-service Feign call (they propagate the JWT via the
  `Authorization` header, since Feign has no cookie jar). Fixed by falling back to
  `DefaultBearerTokenResolver` when no cookie is present.
- **`PaymentController.getAllPayments()` had an unused `@RequestHeader("Authorization") String authHeader`
  parameter** — a Keycloak/header-era leftover. Since cookie-based requests never send that header, Spring
  rejected the request with a `400` *before* `@PreAuthorize` even ran — so this endpoint 400'd for everyone,
  admin included, regardless of role. Removed the dead parameter.
- **Order-owner attribution bug** (see the Order flow section above): `toOrderRes()` resolved "the order's
  user" via `getCurrentUser()` (whoever's making the request) instead of by the order's actual `userId`. Only
  visible once an ADMIN could actually list other users' orders — invisible in single-user testing.
- **Stale DB volume**: after changing the `User` entity's identity model (dropping `keycloakUserId`, adding
  `email`/`password`/`role`), the existing Docker volume's `users` table still had the old
  `keycloak_user_id NOT NULL UNIQUE` column, since `ddl-auto: update` only ever adds columns, never drops them.
  Every registration failed on that stale constraint until the volume was dropped and recreated.
- **Wrong circuit-breaker property name** — I initially set `feign.circuitbreaker.enabled=true`, which is the
  property documented in a lot of older tutorials/blog posts. For the Spring Cloud OpenFeign version actually
  in use (4.1.1, from the 2023.0.1 BOM), that property doesn't exist at all — Spring silently ignores unknown
  properties, so there was no error, it just never activated the circuit breaker. Found it by extracting
  `spring-cloud-openfeign-core`'s `META-INF/spring-configuration-metadata.json` from the jar and grepping for
  `circuitbreaker`, which showed the real property is `spring.cloud.openfeign.circuitbreaker.enabled`. Lesson:
  when a boolean config flag seems to have zero effect, verify the exact property name against the version
  actually on the classpath rather than trusting a remembered/documented name — Spring Boot config properties
  do get renamed across major versions.
- **Fallback bean never registered as a Spring bean**: once the circuit breaker property was actually correct,
  `cart-service` crashed on startup with `IllegalStateException: No fallback instance of type
  ProductServiceFallback found for feign client product-service`. Turned out `cart-service`'s
  `ProductServiceFallback` (unlike every other fallback class in the app) was missing `@Component` — a plain
  POJO implementing the Feign interface, never picked up by component scanning. This had zero effect while the
  circuit breaker was inert (nothing ever tried to look the bean up), so it sat there silently broken until the
  *other* bug was fixed and this one became load-bearing. A good example of two independent bugs masking each
  other — fixing the first one is what made the second one visible at all.

**Why this section matters for an interview**: it's a concrete, specific answer to "tell me about a hard bug"
or "how do you debug"— each one has a clear symptom, root cause, and fix, and several show the value of reading
stack traces down to the actual `Caused by:` line (or in one case, the library's own config metadata file)
rather than trusting the first error surfaced or a remembered property name.

---

## 5. Other honest weak points (own these proactively)

- `PaymentService` has commented-out code linking payments back to orders — the order↔payment relation isn't
  fully wired bidirectionally. Next step: complete that link.
- `CartService` swallows Feign errors and returns `null` on failure, which risks NPEs downstream — would
  replace with proper fallback DTOs or `Optional`.
- N+1 Feign calls during order enrichment — would batch or cache product lookups.
- Self-service role selection at signup (`AuthReq.role`) is intentionally insecure — anyone can register as
  ADMIN. Fine for local dev/demo where you need a way to actually exercise admin-gated endpoints without a
  separate provisioning flow, **not** something to ship to production as-is. The honest answer here: "in a
  real deployment I'd remove client-controlled role selection entirely and provision the first admin via a
  seeded account or a protected promotion endpoint."
- `GET /user/{id}` (added so `order-service` can resolve an order's actual owner by id) is reachable by *any*
  authenticated user through the gateway, not just internal service calls — a minor PII exposure (id/email/
  name/role enumeration), not a privilege escalation. Would tighten this to service-to-service only or
  ADMIN-only if hardening further.

Framing these as "next steps on my roadmap" rather than hiding them signals seniority and self-awareness.

---

## 6. AOP — implemented

### What is AOP?
Aspect-Oriented Programming separates **cross-cutting concerns** (logging, error handling, timing, security,
transactions) from business logic. Instead of repeating the same boilerplate in every service method, you
write it once as an "aspect" and Spring weaves it into target methods automatically via runtime proxies.

### Key components
- **Aspect** — a class (`@Aspect` + `@Component`) encapsulating a cross-cutting concern.
- **Advice** — the action taken: `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around`
  (most powerful — wraps the whole method call).
- **Pointcut** — an expression defining *where* the advice applies, e.g.
  `execution(* com.example.cart_service.service.*.*(..))`.
- **Join Point** — the point of execution being intercepted (in Spring AOP, always a method execution).
- **Weaving** — linking aspects to target objects; Spring AOP does this at runtime via JDK/CGLIB proxies.
- **Target object / Proxy** — the real bean vs. the proxy Spring actually injects, which runs advice then
  delegates to the real object.

### Why it was worth doing here
`CartService`, `OrderService`, and `PaymentService` all repeated the same manual pattern around Feign calls
(`log.info("Before Feign call")` / `log.error("Feign client error", e)`) — a textbook AOP candidate, so it got
pulled into a shared `@Around` aspect instead of staying duplicated per service.

### What's actually in the codebase now

**1. Dependency** — `spring-boot-starter-aop`, added to all 5 backend services (the three with Feign clients
need it for `FeignCallLoggingAspect`; all 5 use `ServiceTimingAspect`).

**2. `FeignCallLoggingAspect`** — one per service with Feign clients (`cart-service`, `order-service`,
`payment-service`), each at `configuration/FeignCallLoggingAspect.java`, pointcut scoped to that service's own
`client` package (e.g. `execution(* com.example.cart_service.client.*Client.*(..))`):
```java
@Slf4j
@Aspect
@Component
public class FeignCallLoggingAspect {

    @Pointcut("execution(* com.example.cart_service.client.*Client.*(..))")
    public void feignClientMethods() {}

    @Around("feignClientMethods()")
    public Object logAroundFeignCall(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        log.info("Before Feign call: {} args={}", method, Arrays.toString(pjp.getArgs()));

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("After Feign call: {} -> {} ({} ms)", method, result, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.error("Feign client error in {}: {}", method, ex.getMessage(), ex);
            throw ex; // rethrow so fallback/error handling still works
        }
    }
}
```
Removed the now-redundant manual `log.info`/`log.error` lines from `CartService.getUserDTO()` /
`getProductDTO()` once this was in place — the aspect covers that logging generically.

**3. `ServiceTimingAspect`** — one per backend service (`product-service`, `user-service`, `cart-service`,
`order-service`, `payment-service`), pointcut scoped to that service's own `service`/`services` package:
```java
@Slf4j
@Aspect
@Component
public class ServiceTimingAspect {

    @Around("execution(* com.example.cart_service.service..*(..))")
    public Object timeServiceMethod(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            log.info("{} executed in {} ms", pjp.getSignature().toShortString(),
                    (System.nanoTime() - start) / 1_000_000);
        }
    }
}
```

**Verified live**: hitting `/carts` produces log lines like `Before Feign call: UserServiceClient.getCurrentUser() args=[]`,
`After Feign call: ... (202 ms)`, and `CartService.getCartByUser() executed in 580 ms` — both aspects are
actually firing, not just declared.

### Caveats to mention
- **No shared library module** — since each service is a fully independent Maven project (not modules of a
  parent POM), these aspect classes are duplicated once per service rather than centralized in a shared
  dependency. That's consistent with other cross-cutting code in this app (e.g. `CookieBearerTokenResolver` is
  also duplicated per service) — a real production system with this many services would likely extract a
  shared internal library.
- **Pointcut precision** — expressions must target the right package/layer per service; `product-service` uses
  a `services` (plural) package while the others use `service` (singular), so the pointcuts aren't literally
  copy-pasteable across all five.
- **Proxy limitation** — Spring AOP proxies only intercept calls made *through the bean* (injected interface);
  internal `this.method()` calls bypass the proxy. Not an issue for Feign clients since they're always called
  via injected interfaces.
- **Must rethrow in `@Around`** — if you catch an exception for logging, rethrow it, otherwise the
  fallback/error-handling chain breaks silently (this matters even more now that Resilience4j fallbacks are
  actually live — swallowing the exception here would prevent the circuit breaker from ever recording the
  failure).

### How to frame this in the interview
"I noticed the manual logging/try-catch around Feign calls in `CartService`, `OrderService`, and
`PaymentService` was duplicated across all three — a textbook AOP use case — so I extracted it into a shared
`@Around` aspect per service targeting all `*Client` interfaces, plus a second aspect for service-layer timing.
It's a clean place to add metrics or tracing later without touching business logic."

---

## 7. Likely interviewer Q&A

**Q: Why did you choose database-per-service instead of a shared database?**
A: Service autonomy — each service owns its schema and can evolve independently without coordinating migrations
across teams. Tradeoff: no cross-service joins, so I do API composition (Feign calls) at the service layer to
stitch data together (e.g. `OrderService.toOrderRes()`).

**Q: Why did you move away from Keycloak to a self-issued JWT?**
A: Mainly to demonstrate I understand what an identity provider actually does under the hood rather than
treating it as a black box — issuing/signing tokens, password hashing, and cookie-based delivery. The honest
tradeoff: Keycloak gives you SSO, token refresh, an admin console, and battle-tested security (password
policies, brute-force protection, MFA-readiness) for free; a hand-rolled scheme means I own all of that. For a
real production system I'd lean back towards a managed IdP (Keycloak, Auth0, Cognito) rather than maintaining
custom auth long-term — this was a deliberate learning exercise, not a claim that hand-rolled auth is the
better production choice.

**Q: Why `httpOnly` + `Secure` + `SameSite=Strict` cookie instead of `localStorage`?**
A: `localStorage` is readable by any JavaScript running on the page, including injected/XSS script — full
token theft in one line. An `httpOnly` cookie can't be read by JS at all; the browser attaches it
automatically. `Secure` requires HTTPS in transit (or the browser's localhost trustworthy-origin exception in
dev). `SameSite=Strict` means the cookie isn't sent on cross-site requests at all, which closes most CSRF
vectors for free. The tradeoff is added complexity: the frontend needs `withCredentials: true` and correct
CORS `allowCredentials` config, and cross-origin cookie behavior needs care in production (matching
registrable domains).

**Q: Why validate the JWT at both the gateway AND each service?**
A: Defense in depth. The gateway is the first line of defense (rejects unauthenticated traffic early), but if a
service were ever called directly (bypassing the gateway, e.g. in a future internal-network scenario), it still
enforces its own auth. Also each service needs the JWT claims (user id, role) for its own authorization logic —
e.g. `@PreAuthorize("hasRole('ADMIN')")` has to evaluate locally.

**Q: How do you handle distributed transactions / data consistency across services?**
A: Currently no saga/orchestration pattern — each service operation is a local `@Transactional`. For true
cross-service consistency (e.g. order created but payment fails), I'd introduce a saga pattern or
event-driven compensation (e.g. via Kafka) — that's a clear extension point I'd call out.

**Q: How does the cart/order service know which user it's operating on?**
A: It calls `UserServiceClient.getCurrentUser()` via Feign (`GET /user/me`), which resolves the user from the
JWT identity propagated through the `RequestInterceptor` in `FeignClientConfig` — the same token that was
validated on the inbound request gets re-attached as an `Authorization` header on the outbound Feign call.

**Q: Walk me through what happens when a user pays for an order.**
A: Frontend `useCheckout` hook → order-service creates an `Order` → payment-service creates a Razorpay order via
`createRazorpayOrder` → Razorpay checkout modal opens client-side → on success, frontend sends
`orderId/paymentId/signature` to payment-service → `PaymentService.createPayment()` recomputes the HMAC-SHA256
signature server-side and compares it, then calls Razorpay's API to confirm payment status before persisting.

**Q: What would you change if this had to scale to real production traffic?**
A:
1. Tune Resilience4j properly per client (failure-rate thresholds, sliding window sizes) instead of library
   defaults — it's wired up and working, but running on defaults (see section 3).
2. Replace synchronous Feign chains in order enrichment with caching or async aggregation.
3. Add a message broker (Kafka/RabbitMQ) for order/payment events instead of synchronous calls.
4. Move off Eureka to Consul or platform-native discovery (Kubernetes Services) — Eureka works and is wired up,
   but it's in Spring Cloud's maintenance mode, not where I'd start a brand-new system today.
5. Centralized config server instead of per-service `application.yml` files with duplicated shared config
   (`JWT_SECRET`, Eureka URLs) — a Spring Cloud Config Server or similar would remove that duplication.
6. Centralized logging/tracing (ELK + Sleuth/Zipkin or OpenTelemetry) — `LoggingFilter` and the new AOP timing
   aspects are a start but not distributed tracing across service boundaries.
7. Move back to a managed identity provider and add refresh-token rotation instead of a single fixed-expiry
   access token.

**Q: Why Eureka over Consul or Kubernetes-native discovery?**
A: Eureka needs no new *type* of infrastructure — it's just another Spring Boot app, and it integrates natively
with the Feign/Spring Cloud LoadBalancer setup already in the codebase, so adding it was a config change plus
one new service rather than a new category of moving part. Consul would add a genuinely different
infrastructure component (a Consul agent, not a JVM app) for capabilities (health checks, KV config store)
this app doesn't currently need. Kubernetes-native service discovery would make the whole question moot — if
this were deployed on k8s, Services already provide DNS-based discovery for free and I wouldn't run Eureka at
all. I'm honest that Eureka is the "fits what's already here" choice, not the forward-looking one.

**Q: How does the circuit breaker actually behave when a dependency goes down?**
A: I tested this directly rather than just trusting the config: stopped `product-service`'s container while
`cart-service` was mid-traffic. The first several calls to `ProductServiceClient.getById()` failed and Feign
propagated the exception; once Resilience4j's failure threshold tripped, the breaker opened and started
short-circuiting instantly (0ms, no network attempt at all) straight to `ProductServiceFallback`'s placeholder
DTO — so `cart-service`'s own endpoints kept returning `200 OK` instead of 500s. After restarting
`product-service`, calls kept hitting the fallback for a bit (the breaker's `waitDurationInOpenState`, ~60s by
default), then transitioned to half-open, let a handful of trial calls through, and closed back to normal once
those succeeded — no manual intervention needed.

**Q: How is security enforced for the Razorpay flow specifically?**
A: Client-side payment confirmation is never trusted blindly. `payment-service` recomputes
`HMAC-SHA256(orderId|paymentId, secret)` and compares it to the signature sent by the client, then double-checks
status by calling Razorpay's `/v1/payments/{id}` API directly with basic auth before saving the payment record.
The Razorpay secret itself is injected via environment variable, never hardcoded or shipped to the frontend —
only the public `key_id` is client-visible.

**Q: Why OpenFeign over RestTemplate/WebClient?**
A: Declarative, interface-based HTTP clients — less boilerplate, and it integrates cleanly with Spring Cloud's
load-balancing/circuit-breaker/service-discovery ecosystem, which is exactly what let me add Eureka-based
discovery and Resilience4j circuit breaking with mostly config changes rather than rewriting the client calls.
The `FeignClientConfig` interceptor also lets me centralize JWT propagation across all clients in a service.

**Q: What's the role of `BaseResponse` and `CustomErrorDecoder`?**
A: `BaseResponse<T>` is a consistent response envelope (status code, message, data) used across all services —
makes Feign deserialization predictable. `CustomErrorDecoder` translates non-2xx Feign responses into
meaningful exceptions instead of generic `FeignException`s.

**Q: What does `lb://` actually mean, and why not just use `http://`?**
A: `http://product-service:8081` is a static address — the gateway connects to exactly that host:port every
time. `lb://product-service` means "resolve this dynamically instead": Spring Cloud Gateway routes the request
through `ReactiveLoadBalancerClientFilter`, which asks Spring Cloud LoadBalancer for an instance of
`product-service`; LoadBalancer asks the `DiscoveryClient` (Eureka in this app) for currently registered,
healthy instances; it picks one, rewrites the URI to the real address, and only then does the HTTP call
actually go out. The gateway's config never needs to know or change if `product-service` moves, scales, or
gets replaced — Eureka's registry is the single source of truth. The exact same mechanism is what lets
`@FeignClient(name = "product-service")` work with no `url=` attribute at all.

**Q: What's a Maven BOM, and why does this project use one?**
A: A BOM ("Bill of Materials") is a `pom.xml` with no code — just a big list of `<dependencyManagement>`
entries pinning exact, mutually-compatible versions for a family of related libraries. This project imports
`spring-cloud-dependencies` as a BOM in every service that uses Spring Cloud artifacts (OpenFeign, Eureka
client, LoadBalancer, Resilience4j integration): once imported, adding `spring-cloud-starter-netflix-eureka-client`
or similar needs no version number — the BOM supplies whichever version its release train (e.g. `2023.0.1`)
says is compatible with everything else in that train. Without it you'd have to hand-pick a version for every
one of Spring Cloud's dozens of modules and hope they interoperate. It's also exactly why this codebase has two
different BOM versions in play: `gateway-service`/`cart-service`/`order-service`/`payment-service` run Spring
Boot 3.3.1 with the `2023.0.1` Spring Cloud train, while `product-service`/`user-service` run Boot 3.5.4 and
needed the `2025.0.0` train instead — each Spring Boot version has exactly one correct matching Spring Cloud
release, and the BOM is how each service declares which one it's locked to.

**Q: What's a bug you found that taught you something?**
A: The `@PreAuthorize` one (section 4) — every "admin-only" endpoint in the app had been silently unenforced
the whole time because `@EnableMethodSecurity` was never declared anywhere. It's a good reminder that an
annotation existing in the code doesn't mean it's active; I only caught it because I deliberately tested with
both an ADMIN and a USER account and got a 200 where I expected a 403.
