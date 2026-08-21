# FlexCore — Gym Management System

Solo backend portfolio project (Java Spring Boot) demonstrating RBAC/permissions,
concurrency-safe booking, a subscription state machine, and Egyptian-market features
(subscription freeze, family packages, bilingual AR/EN API responses).

## Structure
Package-by-feature. Each feature package (`subscription/`, `gymclass/`, `payment/`, ...)
contains its own `entity/`, `dto/request|response/`, `repository/` (+`impl/`),
`mapper/`, `service/` (+`impl/`), `controller/`. Shared cross-cutting code
(Result pattern, Specification, Auditable base entity, security, exceptions)
lives in `core/`.

## Key patterns to point to in interviews
- `gymclass/service/impl/ClassBookingServiceImpl.java` — optimistic locking
  (`GymClass.version`) prevents overbooking; a version conflict is translated
  into a `Result.failure(...)`, not a raw exception.
- `core/result/Result.java` — Result Pattern for business-rule failures.
- `core/specification/Specification.java` — Specification Pattern contract
  used for composable filtering (see `subscription/specification/`,
  `gymclass/specification/`).
- `core/entity/Auditable.java` — JPA auditing base class (createdBy/date,
  lastModifiedBy/date) via `@MappedSuperclass` + `AuditingEntityListener`.
- `core/repository/IUnitOfWork.java` — multi-aggregate transactional boundary.

## Getting started

### Option A — Docker (recommended, no local Postgres/Maven needed)
1. `docker compose up --build`
2. The app starts on `http://localhost:8080`, Postgres on `localhost:5432`.
3. Flyway runs migrations automatically on startup.
4. To stop: `docker compose down` (add `-v` to also wipe the DB volume).

### Option B — Local Postgres only via Docker, app run from your IDE
Useful if you want to debug/step through the app locally but don't want to
install Postgres natively:
1. `docker compose up -d postgres`
2. Run `FlexcoreApplication` from your IDE (or `mvn spring-boot:run`) — it
   connects to `localhost:5432` per `application.yml`.

### Option C — Fully local (no Docker)
1. Create a Postgres database named `flexcore`.
2. Update `src/main/resources/application.yml` with your DB credentials.
3. `mvn spring-boot:run`
4. Flyway will run migrations in `src/main/resources/db/migration` on startup.

## Status
This is a scaffolded starting point — entities/services listed in the project
plan are stubbed as empty files where not yet implemented. See project docs
(`gym-project-doc.html`) for the full spec: personas, features, user stories,
diagrams, and open questions.
