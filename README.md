# FlexCore — Gym Management System

[![CI](https://github.com/sief-elmenshawi/flexCore-Gym/actions/workflows/ci.yml/badge.svg)](https://github.com/sief-elmenshawi/flexCore-Gym/actions/workflows/ci.yml)

A solo backend portfolio project (Java 17 / Spring Boot) demonstrating RBAC security,
concurrency-safe booking, a subscription state machine with freeze support, mock payments,
and Egyptian-market features (family plans, bilingual AR/EN API responses).

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Framework | Spring Boot 4.1 (Web, Security, Data JPA, Validation, Actuator) |
| Database | PostgreSQL 16 + Flyway migrations (single source of truth) |
| Security | JWT (jjwt), method-level `@PreAuthorize` with permission authorities |
| Boilerplate | Lombok, MapStruct |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Testing | JUnit 5, Mockito, one `@SpringBootTest` concurrency integration test |
| CI/CD | GitHub Actions (JDK 17 + PostgreSQL service container), Dependabot |

## Structure

Package-by-feature. Each feature package (`subscription/`, `gymclass/`, `payment/`,
`auth/`, `user/`, `role/`, `ptsession/`, `attendance/`, `report/`) owns its own
`entity/`, `dto/request|response/`, `repository/`, `mapper/`, `service/(impl/)`,
`controller/`. Cross-cutting code lives in `core/`: JWT filter, exception handling,
rate limiting, auditing base entity, paging envelope.

Entities use real JPA relations (`@ManyToOne` / `@ManyToMany`) instead of bare foreign-key
IDs — e.g. `Subscription.user`, `ClassBooking.gymClass`. Read paths that touch relations
use `@EntityGraph` fetch joins (see `SubscriptionRepository.findUsableByUserId`,
`findAll(Specification, Pageable)` overrides) to avoid N+1 queries under load.

## Features

- **Auth & RBAC** — register/login with JWT; roles & permissions seeded via Flyway;
  endpoint protection with `@PreAuthorize("hasAuthority('VIEW_REPORTS')")` etc.
- **Subscriptions** — plans, purchase/cancel, freeze/unfreeze with end-date extension,
  family groups sharing a plan with capacity checks.
- **Gym classes & bookings** — capacity enforced with optimistic locking (`@Version`);
  a dedicated concurrency test races N members for the last seat.
- **Payments (mock)** — FAWRY / INSTAPAY / VODAFONE_CASH gateway simulation (~90% success);
  a successful payment renews an EXPIRED subscription from now; members have a
  paginated payment history (`GET /payments/my`).
- **Reports** — revenue aggregated by payment method; subscriptions expiring soon
  (composable Specification filters).
- **Maintenance job** — every 30 minutes: overdue ACTIVE subscriptions flip to EXPIRED,
  fully elapsed freezes thaw back to ACTIVE (bulk JPQL, audit-safe).
- **Login rate limiting** — 5 failed attempts within 15 minutes block that email for
  5 minutes with HTTP 429.
- **Bilingual errors** — every business rule maps to an i18n code resolved per
  `Accept-Language` (AR/EN).

## Getting Started

### Docker (recommended)

```bash
docker compose up --build
```

- App: `http://localhost:8080` · Swagger UI: `http://localhost:8080/swagger-ui.html`
- Postgres exposed on host port **5434** (container-internal 5432)
- Flyway runs all migrations automatically on first startup
- Stop with `docker compose down` (add `-v` to wipe data); DB credentials come from `.env`
  (see `.env.example`)

### IDE debugging against Dockerized Postgres

```bash
docker compose up -d postgres
```

Then run `FlexcoreApplication` from your IDE — `application.yml` defaults to
`jdbc:postgresql://localhost:5434/flexcore`.

## Running the Tests

34 of the 35 tests are plain Mockito unit tests — no database needed:

```bash
mvn test
```

One test (`BookingConcurrencyTest`) is a `@SpringBootTest` that replays an
overbooking race against a real database. To run the full suite:

```bash
docker compose up -d postgres
docker exec flexcore-postgres psql -U postgres -c "CREATE DATABASE flexcore_test"
mvn test   # src/test/resources/application-test.yml points at localhost:5434/flexcore_test
```

Flyway migrates the test database automatically before the context starts.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR to `main`
(and manually via `workflow_dispatch`):

1. Ubuntu runner + JDK 17 (Temurin) with Maven dependency caching
2. PostgreSQL 16 service container with readiness health checks
3. Full `mvn -B test` suite; datasource overridden via `SPRING_DATASOURCE_*` env vars

Dependabot (`.github/dependabot.yml`) opens weekly update PRs for both Maven
dependencies and workflow action versions; minor/patch updates are grouped into
one PR while security fixes arrive individually.

## Key Things to Point to in Interviews

- **Optimistic locking under contention** — `GymClass.version`; concurrent booking of the
  last seat surfaces as `ObjectOptimisticLockingFailureException`, flushed inside the
  transaction and translated into a business-rule error
  (`gymclass/service/impl/ClassBookingServiceImpl.java`). `BookingConcurrencyTest`
  proves it with a real race.
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
- **Rate limiter design** — time is injected into package-private core methods so expiry,
  window resets, and sweeping are unit-tested deterministically without sleeping threads.
- **Schema safety** — Flyway migrations are the single source of truth and
  `ddl-auto: validate` makes any entity/migration mismatch fail fast in CI.
- **Composable querying** — JPA `Specification`s build dynamic filters
  (`subscription/specification/`, `gymclass/specification/`) combined with
  `@EntityGraph` fetch joins to keep list endpoints N+1-free.

## Status

Feature-complete for the planned scope: auth/RBAC, subscriptions with freeze & family
plans, class booking with concurrency guarantees, PT sessions, attendance, mock payments
with renewal logic, reports, bilingual errors, rate limiting, and scheduled maintenance.
See the project spec (`gym-project-doc.html`) for personas, user stories, and diagrams.
