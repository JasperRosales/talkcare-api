# TalkCare API

REST API for the **TalkCare** desktop app (Mental Health & Justice Platform).
Spring Boot + PostgreSQL on **Neon**. The desktop app (separate repository) is only a UI and
calls this API for everything; it has no database credentials.

## Architecture

Spring Boot 4 (Java 21) + PostgreSQL on **Neon**, layered the standard Spring way:

```
src/main/java/com/talkcare/api/
├── TalkCareApiApplication.java   # entry point (server, or `migrate` / `seed` command)
├── config/        # @ConfigurationProperties, Neon URL → Hikari pool, Flyway baseline, /uploads, body limit
├── domain/        # Account → Client | Administrator | Professional → Attorney | Psychiatrist,
│                  # Report, Conversation, Message, enums (Role, ServiceType, ReportStatus, ...)
├── repository/    # @Repository classes; all SQL lives here (Spring JdbcClient)
├── service/       # @Service classes: business rules, transactions, authorization
├── security/      # Spring Security filter chain, JWT session tokens, JSON 401/403
├── storage/       # FileStorage interface + LocalFileStorage
├── mail/          # SMTP for password reset
├── error/         # ApiException hierarchy → stable error codes, @RestControllerAdvice
├── task/          # CLI tasks: migrate, seed
└── web/           # @RestController classes and request/response DTOs (records + Bean Validation)
src/main/resources/
├── application.yml          # maps the environment variables below
└── db/migration/V*.sql      # Flyway migrations (from docs/schema.md)
docs/schema.md               # normalized schema design (class-table inheritance)
```

Dependency rule: `web → service → repository → database`. Authorization is permission-based:
permission codes from `role_permissions` become Spring Security authorities and are checked with
`@PreAuthorize("hasAuthority('reports.create')")`; resource access is polymorphic
(`account.canAccessConversation(...)`, `account.canAccessReport(...)`). No endpoint compares role strings.

## Setup with Neon

Requires Java 21. The Maven wrapper (`./mvnw`) downloads Maven itself.

1. Create a Neon project and a database (e.g. `talkcare`).
2. Copy the connection strings from the Neon console:
   ```bash
   cp .env.example .env
   ```
   - `DATABASE_URL`: the **pooled** string (host contains `-pooler`), used by the API. The
     `postgresql://user:pass@host/db?sslmode=require` form from the console is used as-is.
   - `DATABASE_URL_UNPOOLED`: the **direct** string, used by migrations.
   - `JWT_SECRET`: at least 32 random characters, e.g. `openssl rand -hex 48`.
3. Build, migrate, seed, run:
   ```bash
   ./mvnw package                            # builds target/talkcare-api.jar (runs the tests)
   java -jar target/talkcare-api.jar migrate # applies pending migrations and exits
   java -jar target/talkcare-api.jar seed    # optional: the four test accounts below
   java -jar target/talkcare-api.jar         # http://localhost:4000
   ```
   During development, `./mvnw spring-boot:run` starts the server directly.

Flyway also applies pending migrations every time the server starts, so the `migrate` step is
optional; it's useful as a separate release step.

### Moving from the Express version

Same database, same environment variables, same endpoints and JSON, same port:

- **Migrations:** on its first run against a database migrated by `npm run db:migrate`, the API reads
  the old `schema_migrations` table, baselines Flyway at that version and applies only newer files.
  Nothing is re-run. The old table is left in place.
- **Sessions and passwords:** JWTs (HS256, same claims) and bcrypt hashes are compatible both ways.
  Keep the same `JWT_SECRET` and signed-in users stay signed in. The secret must now be at least
  32 characters in every environment; a shorter one means issuing a new secret (users sign in again).
- `DB_DRIVER` is gone: Java always connects over TCP with TLS, which Neon supports.
- `NODE_ENV=production` is still recognised; `APP_ENV` is the new name.

### Test accounts (`java -jar target/talkcare-api.jar seed`)

| Role | Username | Email | Password |
| --- | --- | --- | --- |
| CLIENT | `test.user` | `test.user@talkcare.com` | `TalkCareUser#2026` |
| ATTORNEY (was CASE_HANDLER) | `test.attorney` | `test.attorney@talkcare.com` | `TalkCareCaseHandler#2026` |
| PSYCHIATRIST (was THERAPIST) | `test.psychiatrist` | `test.psychiatrist@talkcare.com` | `TalkCareTherapist#2026` |
| ADMIN | `test.admin` | `test.admin@talkcare.com` | `TalkCareAdmin#2026` |

Override the passwords with `SEED_CLIENT_PASSWORD`, `SEED_ATTORNEY_PASSWORD`,
`SEED_PSYCHIATRIST_PASSWORD`, `SEED_ADMIN_PASSWORD`. Seeding refuses to run with
`NODE_ENV=production` unless `ALLOW_SEED=true`.

## Endpoints

Authenticate with `Authorization: Bearer <sessionToken>` (returned by login/register).
`/api/auth/login` takes an optional `role` (`CLIENT`, `ATTORNEY`, `PSYCHIATRIST`, `ADMIN`); when sent,
only an account with that role can sign in. Professional registration adds `licenseNo` (roll of
attorneys / PRC license, required) and `affiliation` (law firm / clinic, optional). New professionals
start `PENDING_VERIFICATION`, submit an ID like clients, and are only assigned clients after an admin
approves it. Admin accounts cannot self-register.

Errors are `{ "error": "<message>", "code": "VALIDATION_ERROR|UNAUTHENTICATED|FORBIDDEN|NOT_FOUND|CONFLICT|..." }`.

| Method | Path | Permission |
| --- | --- | --- |
| GET | `/api/health` | public |
| GET | `/api/lookups` (services, concern types, ID types) | public |
| POST | `/api/auth/login` · `/api/auth/register` · `/api/auth/logout` | public |
| POST | `/api/auth/register/attorney` · `/api/auth/register/psychiatrist` | public |
| GET · POST | `/api/appointments` (admins: `?date=YYYY-MM-DD`) | signed in; booking: clients |
| PATCH | `/api/appointments/{id}/status` (`CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`) | assigned professional, the client (cancel only), admins |
| POST | `/api/contact` (name, email, message; notifies admins) | public |
| PATCH | `/api/admin/users/{id}/status` (`SUSPEND`, `DEACTIVATE`, `REACTIVATE`) | `users.manage` |
| POST | `/api/auth/forgot-password` · `/api/auth/reset-password` | public |
| GET | `/api/auth/me` | signed in |
| PATCH | `/api/profile` · POST `/api/profile/photo` | signed in |
| GET | `/api/notifications` · POST `/api/notifications/read` | signed in |
| POST | `/api/verifications` | `verifications.submit` |
| POST | `/api/reports` | `reports.create` (clients) |
| GET | `/api/reports?status=` · `/api/reports/:id` | own / assigned / all |
| PATCH | `/api/reports/:id/status` | `reports.update.status` |
| POST | `/api/conversations` `{serviceType}` (get or open) | `conversations.create` |
| GET | `/api/conversations/assigned` | `conversations.read.assigned` |
| GET | `/api/conversations/:id` · `/api/conversations/:id/messages` | participant |
| POST | `/api/conversations/:id/messages` (client + auto-reply) | `messages.send` |
| POST | `/api/conversations/:id/replies` | `messages.reply` |
| GET | `/api/admin/metrics` · `/api/admin/schedule` | `admin.dashboard` |
| GET | `/api/admin/users` | `users.read` |
| GET/POST | `/api/admin/pending-requests` · `/api/admin/pending-requests/action` | `verifications.review` / `reports.update.status` |
| GET | `/api/admin/activity` | `admin.activity.read` |

## Tests

```bash
./mvnw test
```

Integration tests start an embedded PostgreSQL, apply the Flyway migrations and exercise the HTTP API,
including a session token and password hash produced by the Express version. No Docker or Neon
database is needed.

## Deployment notes

- Deploy wherever the Express version ran, as long as Java 21 is available: a Java buildpack
  (Railway, Heroku-style), or the included `Dockerfile` (Render, Fly.io, a VM).
  The start command is `java -jar target/talkcare-api.jar`; the release step is
  `java -jar target/talkcare-api.jar migrate`.
- Set `APP_ENV=production`, a strong `JWT_SECRET`, `CORS_ORIGIN`, and `DATABASE_URL` (plus
  `DATABASE_URL_UNPOOLED` for migrations).
- `CORS_ORIGIN` must include the origin the desktop UI is served from (`http://localhost:54321` by default).
- **Uploads** (profile photos, ID documents, chat photos) are written to `UPLOAD_DIR` and served from
  `/uploads`. That needs a persistent disk (Render, Railway, Fly volumes, a VM; the Docker image uses
  `/data/uploads`). On hosts without one, implement `FileStorage` with object storage (S3, R2).
  Filenames are random UUIDs, but the files are not access-controlled, so ID documents especially
  should move to private storage with signed URLs before production.
- Sessions are stateless JWTs (`JWT_EXPIRES_IN`, default 12h). Logout discards the token on the client;
  suspending a user (`users.status`) takes effect on their next request.
- Password-reset email needs the `TALKCARE_SMTP_*` variables; without them the endpoint returns 503.
- Request bodies are capped at 15 MB (images arrive as base64 data URLs).
