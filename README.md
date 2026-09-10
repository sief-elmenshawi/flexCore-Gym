# FlexCore — Gym Management System

[![CI](https://github.com/sief-elmenshawi/flexCore-Gym/actions/workflows/ci.yml/badge.svg)](https://github.com/sief-elmenshawi/flexCore-Gym/actions/workflows/ci.yml)
[![Codecov](https://codecov.io/gh/sief-elmenshawi/flexCore-Gym/graph/badge.svg)](https://codecov.io/gh/sief-elmenshawi/flexCore-Gym)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A solo backend portfolio project (Java 17 / Spring Boot) demonstrating RBAC security,
concurrency-safe booking, event-driven delivery via a transactional outbox + RabbitMQ,
distributed tracing, a subscription state machine with freeze support, mock payments,
and Egyptian-market features (family plans, bilingual AR/EN API responses).

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Framework | Spring Boot 4.1 (Web, Security, Data JPA, Validation, Actuator, AMQP) |
| Database | PostgreSQL 16 + Flyway migrations (single source of truth) |
| Caching / rate limiting | Redis — `@Cacheable` plan catalog, Lua-scripted distributed login rate limiter (selectable Redis/in-memory backend) |
| Messaging | RabbitMQ — transactional outbox pattern bridges `booking.confirmed` out of the request transaction, with dead-letter routing and publisher-confirmed delivery |
| Observability | OpenTelemetry + Jaeger distributed tracing (propagated across the outbox's async boundary), `X-Trace-Id` response header, Micrometer + Prometheus registry |
| Security | JWT (jjwt), method-level `@PreAuthorize` with permission authorities, configurable CORS |
| Boilerplate | Lombok, MapStruct |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Testing | JUnit 5, Mockito, MockMvc, 5 `@SpringBootTest` integration tests (concurrency, caching, rate limiting, outbox, RabbitMQ) |
| CI/CD | GitHub Actions (JDK 17 + PostgreSQL + Redis + RabbitMQ service containers), Dependabot |

## Structure

Package-by-feature. Each feature package (`subscription/`, `gymclass/`, `payment/`,
`auth/`, `user/`, `role/`, `ptsession/`, `attendance/`, `report/`) owns its own
`entity/`, `dto/request|response/`, `repository/`, `mapper/`, `service/(impl/)`,
`controller/`. Cross-cutting code lives in `core/`: JWT filter, exception handling,
rate limiting, CORS, auditing base entity, paging envelope. `outbox/` and
`notification/` hold the messaging pipeline; they are cross-cutting infrastructure,
not a business feature.

Entities use real JPA relations (`@ManyToOne` / `@ManyToMany`) instead of bare foreign-key
IDs — e.g. `Subscription.user`, `ClassBooking.gymClass`. Read paths that touch relations
use `@EntityGraph` fetch joins (see `SubscriptionRepository.findUsableByUserId`,
`UserRepository.findAll`, `RoleRepository.findAll`, and the `findAll(Specification, Pageable)`
overrides) to avoid N+1 queries under load, and every foreign key column is explicitly
indexed (`V13__add_foreign_key_indexes.sql`) since Postgres does not do this automatically.

## API at a Glance

12 controllers, ~40 endpoints under `/api/v1`, secured with permission-based
`@PreAuthorize`. Full request/response schemas are in Swagger UI once the app is
running (`/swagger-ui.html`); the table below is just the map.

| Resource | Base path | Notes |
|---|---|---|
| Auth | `/auth` | register, login (rate-limited) |
| Users | `/users` | self-service profile + admin-managed accounts |
| Roles | `/roles` | role & permission management |
| Subscription plans | `/plans` | catalog, cached in Redis |
| Subscriptions | `/subscriptions` | purchase, freeze/unfreeze, cancel |
| Family groups | `/family-groups` | shared family-plan subscriptions |
| Gym classes | `/classes` | scheduling + public search |
| Class bookings | `/bookings` | seat reservations, optimistic-locked |
| PT sessions | `/pt-sessions` | one-on-one trainer bookings |
| Attendance | `/attendance` | front-desk check-in log |
| Payments | `/payments` | mock gateway + renewal semantics |
| Reports | `/reports` | revenue and operational reporting |

Roles: `ADMIN`, `TRAINER`, `RECEPTIONIST`, `MEMBER` — seeded via Flyway with their
permission sets (see `V1__create_users_roles_permissions.sql`).

## Features

- **Auth & RBAC** — register/login with JWT; roles & permissions seeded via Flyway;
  endpoint protection with `@PreAuthorize("hasAuthority('VIEW_REPORTS')")` etc.
- **Subscriptions** — plans, purchase/cancel, freeze/unfreeze with end-date extension,
  family groups sharing a plan with capacity checks.
- **Gym classes & bookings** — capacity enforced with optimistic locking (`@Version`);
  a dedicated concurrency test races N members for the last seat. A confirmed booking
  is recorded as an outbox event in the same transaction and delivered via RabbitMQ.
- **Payments (mock)** — FAWRY / INSTAPAY / VODAFONE_CASH gateway simulation (~90% success);
  a successful payment renews an EXPIRED subscription from now; members have a
  paginated payment history (`GET /payments/my`).
- **Reports** — revenue aggregated by payment method; subscriptions expiring soon
  (composable Specification filters).
- **Event-driven delivery (outbox + RabbitMQ)** — `booking.confirmed` is written to an
  `outbox_events` row inside the booking's own transaction (commits or rolls back
  together), then polled and published to a RabbitMQ exchange with publisher confirms,
  exponential backoff on failure, and dead-lettering after `app.jobs.outbox-max-attempts`.
  Falls back to an in-process log handler when `app.rabbit.enabled: false`.
- **Distributed tracing** — OpenTelemetry exports to Jaeger; the W3C `traceparent` is
  captured on the outbox row at record time and re-injected at publish time, so a trace
  survives the jump from the request thread to the background scheduler thread and into
  the RabbitMQ consumer. Every response carries an `X-Trace-Id` header for support
  correlation.
- **Maintenance job** — every 30 minutes: overdue ACTIVE subscriptions flip to EXPIRED,
  fully elapsed freezes thaw back to ACTIVE (bulk JPQL, audit-safe).
- **Login rate limiting** — 5 failed attempts within 15 minutes block that email for
  5 minutes with HTTP 429. Backed by Redis (Lua script, atomic, TTL-bounded) by default;
  switchable to a single-node in-memory store via `app.security.login-rate-limit.store`.
- **CORS** — configurable cross-origin policy so an external client (SPA, mobile app,
  another service) can call the API from the browser.
- **Bilingual errors** — every business rule maps to an i18n code resolved per
  `Accept-Language` (AR/EN).

## Getting Started

### Docker (recommended)

```bash
docker compose up --build
```

- App: `http://localhost:8080` · Swagger UI: `http://localhost:8080/swagger-ui.html`
- Postgres exposed on host port **5434** (container-internal 5432)
- RabbitMQ management UI: `http://localhost:15672` (guest/guest)
- Jaeger UI (traces): `http://localhost:16686`
- Flyway runs all migrations automatically on first startup
- Stop with `docker compose down` (add `-v` to wipe data); DB credentials come from `.env`
  (see `.env.example`)

### IDE debugging against Dockerized dependencies

```bash
docker compose up -d postgres redis rabbitmq jaeger
```

Then run `FlexcoreApplication` from your IDE — `application.yml` defaults to
`jdbc:postgresql://localhost:5434/flexcore`. Set `APP_RABBIT_ENABLED=false` if you'd
rather skip RabbitMQ locally; booking events then log through the in-process fallback
handler instead.

## Running the Tests

```bash
mvn test
```

Most of the suite is plain Mockito unit tests or MockMvc web slices — no infrastructure
needed. Five tests are `@SpringBootTest` integration tests that need real
dependencies running (Postgres, Redis, RabbitMQ):

- `BookingConcurrencyTest` — replays an overbooking race against a real database
- `RedisLoginRateLimiterIntegrationTest` — concurrent rate-limit correctness against Redis
- `SubscriptionPlanCachingIntegrationTest` — cache hit/evict behavior via the app's own `CacheManager`
- `OutboxFlowIntegrationTest` — record → poll → publish → mark-published, end to end
- `RabbitNotificationIntegrationTest` — outbox → broker → consumer, with a real queue

To run the full suite locally:

```bash
docker compose up -d postgres redis rabbitmq
docker exec flexcore-postgres psql -U postgres -c "CREATE DATABASE flexcore_test"
mvn test   # src/test/resources/application-test.yml points at localhost:5434/flexcore_test
```

Flyway migrates the test database automatically before the context starts.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR to `main`
(and manually via `workflow_dispatch`):

1. Ubuntu runner + JDK 17 (Temurin) with Maven dependency caching
2. PostgreSQL 16, Redis, and RabbitMQ service containers with readiness health checks
3. Full `mvn -B test` suite; datasource overridden via `SPRING_DATASOURCE_*` env vars
4. JaCoCo coverage report uploaded to [Codecov](https://codecov.io/gh/sief-elmenshawi/flexCore-Gym)
   (token stored as the `CODECOV_TOKEN` repository secret)

Dependabot (`.github/dependabot.yml`) opens weekly update PRs for both Maven
dependencies and workflow action versions; minor/patch updates are grouped into
one PR while security fixes arrive individually.

## Key Things to Point to in Interviews

- **Optimistic locking under contention** — `GymClass.version`; concurrent booking of the
  last seat surfaces as `ObjectOptimisticLockingFailureException`, flushed inside the
  transaction and translated into a business-rule error
  (`gymclass/service/impl/ClassBookingServiceImpl.java`). `BookingConcurrencyTest`
  proves it with a real race.
- **Transactional outbox** — `OutboxEventRecorder.recordEvent` runs with
  `Propagation.MANDATORY` so the event row commits or rolls back atomically with the
  business change it describes; `OutboxEventProcessor` claims one row at a time with
  `PESSIMISTIC_WRITE` + lock timeout `-2` (→ `FOR UPDATE SKIP LOCKED` on Postgres), so
  multiple worker instances never double-publish. Failures get exponential backoff and
  eventual dead-lettering instead of retrying forever.
- **Guaranteed broker delivery** — `OutboxRabbitPublisher` blocks on the
  `CorrelationData` future from `publisher-confirm-type: correlated` before the outbox
  row is marked `PUBLISHED`, so "bytes written to the socket" is never mistaken for
  "durably accepted by the broker."
- **Trace continuity across an async boundary** — the W3C `traceparent` is snapshotted
  on the outbox row when the event is recorded (request thread) and re-injected when
  it's published (scheduler thread), so a trace in Jaeger follows one booking end to
  end instead of splitting into two unrelated traces at the queue boundary.
- **Subscription state machine** — status transitions are guarded twice: lazily at read
  time (`endDate` checks) so correctness never depends on the cron, and eagerly by a
  scheduled reconciler using bulk JPQL (which sets `lastModifiedDate` explicitly because
  bulk updates bypass JPA auditing).
- **Payment renewal semantics** — cancelled subscriptions refuse payment outright; a
  successful charge restarts only EXPIRED windows from now
  (`payment/service/impl/PaymentServiceImpl.java`).
- **Documented TOCTOU trade-off** — family-group capacity uses check-then-insert without a
  lock; the race window is milliseconds wide with bounded impact, so it was accepted
  deliberately (a pessimistic lock on the group row would close it if requirements changed).
- **i18n error pipeline** — domain exceptions carry message codes + args;
  `core/exception/GlobalExceptionHandler.java` resolves them per locale, including a
  Jackson `InvalidFormatException` handler that lists allowed enum values.
- **Rate limiter design** — atomic via a single Lua script (one round trip, no
  WATCH/MULTI/EXEC), TTL-bounded so stale keys self-expire, and swappable to an
  in-memory backend behind one property for single-node deployments.
- **Schema safety** — Flyway migrations are the single source of truth and
  `ddl-auto: validate` makes any entity/migration mismatch fail fast in CI.
- **Composable querying, indexed for real** — JPA `Specification`s build dynamic filters
  (`subscription/specification/`, `gymclass/specification/`) combined with
  `@EntityGraph` fetch joins to keep list endpoints N+1-free, backed by explicit indexes
  on every foreign key (`V13__add_foreign_key_indexes.sql`) since Postgres never indexes
  those on its own.

## Status

Feature-complete for the planned scope: auth/RBAC, subscriptions with freeze & family
plans, class booking with concurrency guarantees and event-driven notification delivery
(outbox + RabbitMQ), distributed tracing, PT sessions, attendance, mock payments with
renewal logic, reports, bilingual errors, rate limiting, CORS, and scheduled maintenance.
See the project spec (`gym-project-doc.html`) for personas, user stories, and diagrams.

## License

Distributed under the [MIT License](LICENSE).
