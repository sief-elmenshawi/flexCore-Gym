# FlexCore — Project Analysis, Coverage Map & Improvement Plan

> Portfolio backend: Gym Management System — Spring Boot 4.1 / Java 17.
> This document is the single source of truth for what exists, what is covered by
> tests, what is intentionally traded off, and what to do next.

---

## 1. Project Snapshot

| Attribute | Value |
|---|---|
| Name / artifact | `flexcore` `0.0.1-SNAPSHOT` |
| Stack | Java 17, Spring Boot 4.1.1 (Web / Security / Data JPA / Validation / Actuator / AMQP / Data Redis), Flyway, PostgreSQL, RabbitMQ, Redis, OpenTelemetry |
| Schema | `ddl-auto: validate` + 15 Flyway migrations (single source of truth) |
| API docs | springdoc Swagger UI at `/swagger-ui.html` |
| Code size | ~160 main `.java` files, 36 test files |
| Test result | **149 unit/web tests green** (excl. 5 Docker-dependent integration tests) |

---

## 2. Architecture Map (package-by-feature)

```
com.flexcore
├── FlexcoreApplication           entry point (@SpringBootApplication)
├── auth          JWT login/register/refresh/logout + opaque refresh-token rotation
├── user          user CRUD + profile updates
├── role          RBAC roles & permissions CRUD
├── subscription  plans, purchases, freeze/unfreeze, family groups, expiry scheduler
├── payment       mock gateways (Fawry/InstaPay/Vodafone Cash) + idempotency
├── gymclass      classes + optimistic-lock booking with outbox event emission
├── ptsession     personal-training session booking (coach/trainee)
├── attendance    member check-in/out by receptionist or trainer
├── report        revenue-by-payment-method report
├── outbox        transactional outbox: recorder → poller → processor → Rabbit
├── notification  RabbitMQ consumer stand-in (downstream notification service)
└── core          security, JWT, i18n, exceptions, caching, tracing config, filters
```

**Component counts:** 12 controllers · 12 services (+impl) · 1 concrete `RefreshTokenService` · 14 repositories · 14 entities · 35 DTOs (20 req / 15 resp) · 7 mappers · 7 exception types + `@RestControllerAdvice` · 9 configs · 7 enums · 2 filters · 2 schedulers.

---

## 3. Test Coverage Map (36 files — what each protects)

| Layer | Tests | Type | Guards |
|---|---|---|---|
| Auth service | `AuthServiceTest` (9) | Mockito | register/login/refresh rotate/invalid reuse/logout |
| Auth web | `AuthControllerWebTest` (5) | @WebMvcTest | endpoints + JSON shape incl. `refreshToken` |
| JWT filter | `JwtAuthFilterTest` (3) | unit + spy | **exactly one `parseClaims` per request**, valid/invalid/missing token |
| Rate limit | `LoginRateLimiterTest` (7), `AttemptStateTest` (6) | Mockito | window/block/sweep transitions (time-injected) |
| Rate limit (Redis) | `RedisLoginRateLimiterIntegrationTest` | @SpringBootTest | Lua atomicity + concurrency (needs Docker) |
| User | `UserServiceTest` (2), `UserControllerWebTest` (5) | — | CRUD + authz |
| Role | `RoleServiceTest` (8), `RoleControllerWebTest` (5) | — | RBAC + permissions merge |
| Subscriptions | `SubscriptionServiceTest` (6), `SubscriptionControllerWebTest` (6), `SubscriptionExpirySchedulerTest` (2), `FamilyGroupServiceTest` (2), `FamilyGroupControllerWebTest` (4) | — | state machine, freeze/unfreeze, specs, capacity rule |
| Plan caching | `SubscriptionPlanCachingIntegrationTest` | @SpringBootTest | Redis cache eviction (needs Docker) |
| Payments | `PaymentServiceTest` (11) | Mockito | renew-from-now, cancelled refusal, gateway reject, **idempotency replay / first-charge / waiter-resolve / budget-exhaustion** |
| Payments retry | `PaymentIdempotencyWaiterRetryTest` (4) | @SpringJUnitConfig | real `@Retryable` proxy: immediate, retry-until-linked, budget → 409, backoff≥100ms |
| Payments web | `PaymentControllerWebTest` (5) | @WebMvcTest | `Idempotency-Key` header pass-through + authz |
| Gym classes | `GymClassControllerWebTest` (4), `ClassBookingServiceTest` (8), `ClassBookingControllerWebTest` (4) | — | booking rules, race-lost → i18n error |
| Booking race | `BookingConcurrencyTest` | @SpringBootTest | 5 threads racing for last seat, one wins (needs Docker) |
| PT sessions | `PTSessionServiceTest` (9), `PTSessionControllerWebTest` (4) | — | coach/trainee rules |
| Attendance | `AttendanceServiceTest` (5), `AttendanceControllerWebTest` (4) | — | check-in rules |
| Reports | `ReportServiceTest` (3), `ReportControllerWebTest` (3) | — | revenue aggregation |
| Outbox | `OutboxEventRecorderTest` (2), `OutboxEventProcessorTest` (6), `OutboxPublisherTest` (3), `OutboxFlowIntegrationTest` | — | MANDATORY propagation, SKIP LOCKED claim, backoff, DLQ (integration needs Docker) |
| Notification | `RabbitNotificationIntegrationTest` | @SpringBootTest | end-to-end Queue → consumer (needs Docker) |

**Note:** all 12 `@WebMvcTest` slices extend `SecuredControllerSliceTest` (real `SecurityConfiguration` + real `JwtAuthFilter` with mocked token provider) — the security filter chain is exercised on every web test, not skipped.

---

## 4. Cross-cutting Systems — how each works (interview block)

### 4.1 Security / JWT / RBAC
Stateless, CSRF off, `@EnableMethodSecurity`. Public: `/api/v1/auth/**`, `/actuator/**`, swagger, public GETs on plans/classes. Everything else requires a Bearer JWT. Access token 15 min (HS256, jjwt); **single `parseClaims` per request** (`JwtAuthFilter`), claims carry `permissions` so no DB hit for authz. `@PreAuthorize` reads permission-code authorities.

### 4.2 Refresh tokens (server-side revocation)
Opaque 32-byte `SecureRandom` token; **only SHA-256 hash persisted** (DB leak is useless). Rotation: every refresh mints a new token pair and chains via `replaced_by_token_id`. **Reuse detection**: presenting an already-rotated token revokes the whole family (all active tokens for the user). 7-day TTL, `POST /auth/refresh` + `POST /auth/logout`.

### 4.3 Login rate limiting
Default backend Redis: a **single atomic Lua script** does read→increment→decide block→TTL in one round-trip (no lost failures under concurrency). Policy 5 fails / 15 min → blocked 5 min → 429. Switchable to in-memory (swept hourly) for single-node dev.

### 4.4 Transactional outbox (RabbitMQ)
Business change + outbox row commit atomically (`Propagation.MANDATORY` — recorder is called inside the service transaction). `OutboxPollScheduler` (5 s) → `findPendingEvents` (due + status PENDING + partial index) → `OutboxEventProcessor` in `REQUIRES_NEW` with `FOR UPDATE SKIP LOCKED` re-check → Rabbit `publisher-confirm` awaited before `PUBLISHED`. Failure → exponential backoff (`base*2^attempts`, cap 1 h) → DEAD after 10 attempts. DLX dead-letter queue for consumer retries. W3C `traceparent` captured so traces span the queue boundary. In-process idempotent fallback handler when Rabbit is off.

### 4.5 Caching
Redis `@Cacheable("plans", sync=true)` on plan catalog GET, `@CacheEvict(allEntries=true)` on create/update. 2 h TTL, `flexcore:` key prefix, JSON serializer, null-caching disabled.

### 4.6 Optimistic concurrency
`GymClass` has `@Version`; booking does `saveAndFlush` inside the tx and translates `ObjectOptimisticLockingFailureException` to the i18n `error.booking.race-lost`. Payment idempotency uses a **unique constraint claim** (`INSERT ... ON CONFLICT DO NOTHING`): winner charges, loser `@Retryable`-waits for the linked payment, 409 on budget exhaustion.

### 4.7 i18n
`AcceptHeaderLocaleResolver` (ar/en) over `Accept-Language`; `GlobalExceptionHandler` resolves `(code, args)` against `messages*.properties` for ~16 error types; validator uses the same MessageSource (validation messages also bilingual).

### 4.8 Observability
Micrometer `@Observed` per flow, OTel + Jaeger (OTLP :14318), `X-Trace-Id` response filter, logback pattern with traceId/spanId, prod sampling 10%, appender wired via `InstallOpenTelemetryAppender`, outbox carries `traceparent` across boundaries.

### 4.9 Data integrity
`ddl-auto: validate` (drift detection), Flyway migrations with FK indexes built **`CONCURRENTLY`** (V13) + transactional-lock disabled to allow it; `@EntityGraph` fetch-joins on hot list queries (subscriptions, gym classes with trainer, user+role).

---

## 5. Metrics & Performance Work Completed

| Item | Before | After |
|---|---|---|
| JWT verification per request | **4 ×** `parseClaims` (4 HMAC verifies) | **1 ×** (claims reused) — `JwtAuthFilter` + `JwtTokenProvider.extractPermissions(Claims)` |
| Idempotency loser wait | hand-rolled `Thread.sleep(100)` ×5 loop | Spring-core `@Retryable(includes=PaymentInProgressException, maxRetries=4, delay=100, multiplier=1.5)` |
| Dead code | 4 unused `JwtTokenProvider` accessors | removed |
| New proof tests | — | `JwtAuthFilterTest` proves `atMost(1)`; `PaymentIdempotencyWaiterRetryTest` proves retry + backoff through real AOP proxy |

---

## 6. Gaps, Trade-offs & Improvement Plan

Priority legend: **[P0]** polish for interview / **[P1]** recommended next / **[P2]** scale-later.

| # | Area | Gap found | Why it matters | Effort | Priority |
|---|---|---|---|---|---|
| 1 | i18n | `RoleServiceImpl` throws `DuplicateResourceException("Role already exists: "+name)` — raw English, breaks the i18n pipeline | Inconsistent: every other error is a code; shown verbatim to Arabic users | 5 min | P0 |
| 2 | RefreshTokenService | `.plusNanos(expirationMs * 1_000_000)` — odd way to add milliseconds | Readability / easy to misuse at other TTLs | 2 min | P0 |
| 3 | AuthServiceImpl.refresh | empty `try { rotate } catch { throw }` wrapper adds nothing | Code smell; looks like a leftover | 2 min | P0 |
| 4 | Report | README/i18n promise "attendance reports", only revenue-by-method exists | Documented feature unimplemented; easy interview Q&A gap | ~2–3 h | P1 |
| 5 | RBAC dispatch | Endpoints branch on `roleName` (e.g. PT/attendance) via a string claim | Fragile vs. drilling authority codes; OK for demo, worth refactor | ~3 h | P1 |
| 6 | Family group capacity | check-then-insert TOCTOU (documented) | Under real concurrency two groups could exceed capacity; needs pessimistic lock or unique partial index | ~3–4 h | P1 |
| 7 | Settings hygiene | `allowedOriginPatterns("*")`, default `change-this-secret-...` in `application.yml` | Fine for demo; must tighten before "real data". Mention CORS tightening in README already | 5 min + deploy care | P0 |
| 8 | List pagination | `GET /roles`, `GET /plans` return full lists; role list does in-JVM permission merge | Inconsistent with paginated endpoints elsewhere | ~2 h | P2 |
| 9 | Outbox dead events | events with no handler stay PENDING forever (log-only "UNHANDLED") | Operational blind-spot at scale | ~1–2 h | P2 |
| 10 | Producer breadth | only `booking.confirmed` producer exists | Feature-complete claim is scoped; adding `payment.confirmed` would strengthen the outbox story | ~3 h | P2 |
| 11 | Docker-dependent tests | 5 `@SpringBootTest` tests can't run without Docker | CI does run them (service containers); local workflow breaks without Docker scope | n/a | P0 |
| 12 | Frontend | code comment references a `flexcore-login.html` not in repo | Not a blocker (backend-only repo) — but note it in README to avoid confusion | 1 min | P0 |

---

## 7. Suggested Backlog (ordered execution plan)

**Sprint S1 — Interview polish (fast wins):**
1. i18n code for role-exists (Gap 1)
2. `plus(expiration, MILLIS)` cleanup (Gap 2)
3. strip empty catch in `refresh` (Gap 3)
4. README footnote re: frontend & CORS scope (Gaps 7, 12)

**Sprint S2 — Feature-completion (value):**
5. Attendance/occupancy report + endpoint + tests (Gap 4)
6. Harden family-group capacity with a row lock (Gap 6)
7. `payment.confirmed` outbox event + consumer (Gap 10)

**Sprint S3 — Scale-readiness:**
8. Paginate role/plan lists (Gap 8)
9. Authority-code dispatch instead of role-name branching (Gap 5)
10. Outbox alert / dead-letter observability (Gap 9)

---

## 8. Interview Talking Points (cheat sheet)

- **"How do you stop double charging?"** Unique-constraint claim on `(user_id, idempotency_key)`; loser waits on `@Retryable` with backoff then 409. Original `Thread.sleep` loop was replaced by framework retry with exponential backoff + a test proving the real AOP proxy retries.
- **"Design a refresh-token flow."** Opaque random, stored hashed (SHA-256), rotation chain, reuse → family revocation, server-side logout.
- **"How do you guarantee exactly-once-ish delivery?"** Outbox row committed with the business tx; worker claims with `FOR UPDATE SKIP LOCKED`; Rabbit publisher-confirm before PUBLISHED; exponential backoff → DEAD; traceparent across the boundary.
- **"How do you keep a hot endpoint fast?"** Single JWT parse per request, permission claims in the token (no DB hit), `@EntityGraph` fetch joins, FK indexes built CONCURRENTLY, plans cached in Redis with sync=true.
- **"What did you trade off, consciously?"** Family-group capacity TOCTOU (documented), permissive CORS for demo, mock payment gateway, rate limiter as an atomic Lua script with a pluggable store.

---

*Last generated: 2026-09-11.*