# TalkCare API

REST API for the **TalkCare** desktop app (Mental Health & Justice Platform).
Express 5 + PostgreSQL on **Neon**. The desktop app (separate repository) is only a UI and
calls this API for everything; it has no database credentials.

## Architecture

```
src/
├── index.js            # starts the HTTP server
├── app.js              # Express app factory (CORS, JSON, /uploads, /api, errors)
├── config.js           # environment configuration
├── db.js               # connection provider: Neon serverless driver or node-postgres
├── errors.js           # AppError hierarchy → stable error codes
├── domain/
│   ├── accounts.js     # Account → Client | Administrator | Professional → Attorney | Psychiatrist
│   └── constants.js    # roles, permissions, services, report statuses
├── repositories/       # all SQL lives here
├── services/           # business rules, transactions, authorization
├── middleware/         # authenticate (JWT) / requirePermission / error handler
└── routes/index.js     # route table
db/migrations/          # versioned SQL (from docs/schema.md)
scripts/                # migrate.js, seed.js
docs/schema.md          # normalized schema design (class-table inheritance)
```

Dependency rule: `routes → services → repositories → db`. Authorization is permission-based
(`account.can(permission)`, permissions come from `role_permissions`) plus polymorphic
resource checks (`account.canAccessConversation(...)`, `account.canAccessReport(...)`).
No endpoint compares role strings.

## Setup with Neon

1. Create a Neon project and a database (e.g. `talkcare`).
2. Copy the connection strings from the Neon console:
   ```bash
   cp .env.example .env
   ```
   - `DATABASE_URL`: the **pooled** string (host contains `-pooler`), used by the API.
   - `DATABASE_URL_UNPOOLED`: the **direct** string, used by migrations.
   - `JWT_SECRET`: a long random value:
     `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`
3. Install, migrate, seed, run:
   ```bash
   npm install
   npm run db:migrate   # applies db/migrations/*.sql once each (tracked in schema_migrations)
   npm run db:seed      # optional: the four test accounts below
   npm run dev          # http://localhost:4000
   ```

For `*.neon.tech` hosts the API uses `@neondatabase/serverless` (WebSockets, suits serverless
hosts). Any other host uses `pg` over TCP, so a local Postgres also works for development.
Override with `DB_DRIVER=neon|pg`.

### Test accounts (`npm run db:seed`)

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
Errors are `{ "error": "<message>", "code": "VALIDATION_ERROR|UNAUTHENTICATED|FORBIDDEN|NOT_FOUND|CONFLICT|..." }`.

| Method | Path | Permission |
| --- | --- | --- |
| GET | `/api/health` | public |
| GET | `/api/lookups` (services, concern types, ID types) | public |
| POST | `/api/auth/login` · `/api/auth/register` · `/api/auth/logout` | public |
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

## Deployment notes

- Set `NODE_ENV=production`, a strong `JWT_SECRET`, `CORS_ORIGIN`, and `DATABASE_URL`.
  Run `npm run db:migrate` as a release step.
- `CORS_ORIGIN` must include the origin the desktop UI is served from (`http://localhost:54321` by default).
- **Uploads** (profile photos, ID documents, chat photos) are written to `UPLOAD_DIR` and served from
  `/uploads`. That needs a persistent disk (Render, Railway, Fly volumes, a VM). On serverless hosts
  replace `LocalStorage` in `src/services/storage.js` with object storage (S3, R2, Vercel Blob).
  Filenames are random UUIDs, but the files are not access-controlled, so ID documents especially
  should move to private storage with signed URLs before production.
- Sessions are stateless JWTs (`JWT_EXPIRES_IN`, default 12h). Logout discards the token on the client;
  suspending a user (`users.status`) takes effect on their next request.
- Password-reset email needs the `TALKCARE_SMTP_*` variables; without them the endpoint returns 503.
