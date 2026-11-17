# Nexus CRM backend

Spring Boot 4 application on Java 25, built with Maven. The design is in
[../docs/backend-technical-design.md](../docs/backend-technical-design.md).

Built so far: the foundations (PostgreSQL, Flyway, one error format, request
IDs) and the **identity** module: Google sign-in, sessions, roles and users.

Every module has its tables, entities, services and REST endpoints under `/api/v1`:
identity (users, teams), leads (with CSV import and conversion), contacts and
companies, deals and the pipeline, activities, follow-ups, notifications, search,
reporting (dashboard, reports, revenue targets), audit, and currencies with daily
exchange rates. Each module is one flat package, for example `com.nexus.crm.leads`.
A module refers to another module's records by ID only, never by entity, and tells
other modules what happened through events (`LeadEvents`, `DealEvents`).

What a user can see is decided in one place, `UserService.visibleOwnerIds`: a rep sees
their own records, a manager their team's, an admin everyone's. A record outside that
is reported as "not found".

Differences from the design doc: lists use page numbers and totals instead of cursors
(the screens show "1-10 of 120"), the lead CSV import runs inside the request instead of
as a background job, and ShedLock and Spring Modulith are not used (one instance).
The audit and history tables are append-only only if the app's database role is given
INSERT and SELECT but not UPDATE or DELETE on them. Do that when deploying.

## Conventions

- **Records:** every request, response, input, filter and event is its own top-level record file with one
  component per line, like `shared/Label.java`. They live in `dto/` (and `event/` for events) next to the
  module's classes, for example `leads/dto/LeadResponse.java`. Records that Spring binds as configuration,
  JPA composite keys and private helpers stay nested.
- **Threads:** request threads, `@Scheduled` jobs and the application task executor run on virtual threads
  (`spring.threads.virtual.enabled`). Blocking JDBC and HTTP calls are fine there.
- **Database pool:** HikariCP keeps 3 to 20 connections. It is the real limit on concurrent database work.
- **Logs:** Flyway's startup line contains the database URL, so its logger is set to WARN.

## Requirements

- JDK 25
- PostgreSQL 17 or later to run the application
- Docker to run the tests (they start their own PostgreSQL with Testcontainers)

The tests never contact Google. The part of sign-in that talks to Google is
checked by signing in by hand, as described under "Running locally".

Maven is not required; the wrapper (`./mvnw`) downloads the right version.

## Commands

| Task | Command |
| --- | --- |
| Run locally (port 8080) | `./mvnw spring-boot:run` |
| Run the tests | `./mvnw verify` |
| Build the jar | `./mvnw package` |

## Running locally

1. Create an empty database and a user that owns it:
   `createuser nexus_crm && createdb -O nexus_crm nexus_crm`.
   Flyway creates the tables at startup. The `citext` extension is created by
   the first migration, which the database owner is allowed to do.
2. In Google Cloud Console create an OAuth 2.0 client of type "Web
   application" with the authorised redirect URI
   `http://localhost:8080/api/v1/auth/login/oauth2/code/google`.
3. Copy [`.env.example`](.env.example) to `.env`, fill it in, and set
   `NEXUS_BOOTSTRAP_ADMIN_EMAIL` to your own Google address. On an empty
   database that address is invited as the first admin.
4. `set -a; source .env; set +a; ./mvnw spring-boot:run`
5. Start the frontend with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1`
   (see `frontend/.env.example`) and sign in at http://localhost:3000/login.

## Configuration

Everything is set through environment variables; `.env.example` lists them.
The application refuses to start without Google credentials or a signing key.

### Signing key

Access tokens are signed with ES256. Generate a key pair once per environment
and keep it in the platform's secret store:

```bash
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
```

Set `NEXUS_JWT_PRIVATE_KEY` and `NEXUS_JWT_PUBLIC_KEY` to the two files'
contents. The header lines and line breaks are optional, so a single-line
value works. `NEXUS_JWT_ALLOW_EPHEMERAL_KEY=true` is for local development
only: it generates a new key at every start.

### In production

- Serve the API and the frontend from the **same origin**, with the reverse
  proxy routing `/api` to this application. The frontend reads the
  `XSRF-TOKEN` cookie from script, which a browser only allows for cookies of
  its own host.
- Set `NEXUS_FORWARD_HEADERS=framework`, so redirect URIs and client IPs come
  from the proxy's `X-Forwarded-*` headers, and keep port 8080 unreachable
  from outside.

## How sign-in works

| Step | Endpoint | Notes |
| --- | --- | --- |
| Start | `GET /api/v1/auth/oauth2/authorization/google` | The login page links here. Redirects to Google with state, nonce and PKCE. |
| Callback | `GET /api/v1/auth/login/oauth2/code/google` | Checks the person against the invitations, sets the cookies, redirects to the frontend. A refusal redirects to `/login?error=<code>`. |
| Current user | `GET /api/v1/me` | 401 `not_authenticated` when signed out. |
| Refresh | `POST /api/v1/auth/refresh` | Call once on 401 `token_expired`, then retry the request. |
| Sign out | `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all` | This browser, or every device. |
| Users | `GET /api/v1/users`, `POST /api/v1/users`, `PATCH /api/v1/users/{id}` | Reading: every role. Inviting and changing: admins. |

Cookies: `nx_at` (access token, a JWT valid for 15 minutes), `nx_rt` (refresh
token, 14 days, rotated on every use, sent to the refresh endpoint only) and
`XSRF-TOKEN`. Every `POST`, `PUT`, `PATCH` and `DELETE` except refresh must
echo `XSRF-TOKEN` in an `X-XSRF-TOKEN` header.

Sign-in error codes: `not_invited`, `account_disabled`, `email_unverified`,
`access_denied` (cancelled at Google) and `sign_in_failed` (anything else).

## Health checks

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

No other actuator endpoint is exposed over HTTP.

## Mail, drafts and AI

Google sign-in and Gmail sending have separate consent. Enable the Gmail API
in Google Cloud, add `NEXUS_MAIL_REDIRECT_URI` as a second authorised OAuth
redirect URI, and set a stable `NEXUS_MAIL_TOKEN_KEY` (base64 of 32 random
bytes). Each sender then uses **Settings → Mail & banner → Connect Google
Workspace**. The callback accepts only the same verified Google account used
for CRM sign-in. Refresh tokens are encrypted in PostgreSQL. Admins can upload
a PNG or JPEG banner; the sender's CRM display name is drawn onto a copy of
the image and embedded in outgoing email. Communication drafts are deleted 30
days after creation by a daily job. A sent email records Gmail's message ID.
If Gmail returns `403 PERMISSION_DENIED`, reconnect the mailbox to refresh its
`gmail.send` grant. If it persists, confirm the Gmail API is enabled for the
Google Cloud project and that Workspace policy allows the OAuth app and Gmail
send scope.

Each Google feature asks for its own permission, so people only grant what
they use: **mail** (`gmail.send`), **calendar** (`calendar.events`) and
**inbox** (`gmail.readonly`). Connecting a second feature uses Google's
incremental consent, so earlier permissions are kept. The permissions Google
granted are stored on the connection (`mail_connection.scopes`); if Google
later refuses the saved token, the connection is marked `needs_reconnect` and
the Settings screens ask the user to connect again.

- **Calendar link (Apple Calendar and others).** *Settings → Calendars* makes a
  private `.ics` link per user. Only a SHA-256 hash is stored. The link is the
  credential, so the public feed (`/api/v1/calendar/ics/{token}.ics`) has no
  sign-in, is rate limited, and answers every wrong link with the same 404.
  Behind a proxy set `NEXUS_FORWARD_HEADERS=framework` so the link carries the
  public address.
- **Google Calendar push.** Enable the Google Calendar API in the Cloud
  project and add the `calendar.events` scope to the consent screen (a
  sensitive scope). A job runs every minute, one way (CRM to Google), and gives
  each event an id made from the follow-up id so a retry never duplicates it.
  Turn it off with `nexus.calendar.google-sync=false`.
- **Inbox sync.** Uses `gmail.readonly`, which Google classes as a *restricted*
  scope: it works for test users now, but serving more than 100 users needs
  Google's verification and a security assessment. Every five minutes each
  connected mailbox is read; only messages to or from an address that matches a
  contact or lead are kept, as subject, people and Gmail's snippet (no full
  body, no attachments). Spam, trash and drafts are skipped. Turn it off for
  everyone with `nexus.inbox.sync=false`.

  **Privacy rule: a person's mail is only ever theirs.** Mail is attached only to
  contacts and leads that person can open themselves (their own, their team's if
  they manage it, everyone's for an admin; unassigned leads for anyone), so it
  never lands on a colleague's contact. `GET /api/v1/emails` returns only the
  caller's own rows, so a colleague, a manager or an admin who opens the same
  contact sees none of it. Each user can switch it off and delete what was copied.
- **Disconnecting.** Google keeps one grant per user, shared by mail, calendar
  and inbox, and cannot cancel a single feature. Turning a feature off removes
  its permission from the saved connection. When the *last* feature is turned
  off, the grant is cancelled at Google (`oauth2.googleapis.com/revoke`) and the
  saved token is deleted. If Google cannot be reached, the token is still
  deleted and the user is told to remove the app at
  myaccount.google.com/permissions. Permissions are compared as whole words of
  Google's scope list, never as substrings.
- **Alerts.** An hourly job tells deal owners (and their team lead and admins)
  about open deals with no activity for `nexus.alerts.stale-deal-days` days,
  and assignees about follow-ups overdue by `nexus.alerts.overdue-follow-up-hours`
  hours. Each person gets one notification per run, not one per record.

Set `OPENAI_API_KEY` only in the backend environment to enable the assistant,
email drafts, workflow generation and public web research. An admin can also
set or replace the key in **Settings → Nexus assistant**. That key overrides
the environment key, is encrypted in PostgreSQL, and is never returned to the
browser. `NEXUS_AI_CONFIG_KEY` may be set to a stable base64 32-byte key for
encryption; otherwise the stable `NEXUS_MAIL_TOKEN_KEY` is used. Removing the
admin key restores the environment key when one exists. `NEXUS_AI_KEY_NAME`
labels environment-key quota alerts. The assistant streams answers and can
research arbitrary public companies and markets with search-source links. It limits each user to 30
requests per hour. A billing `insufficient_quota` response alerts admins in
the app and, when their own Gmail connection is available, by email. An
optional `NEXUS_AI_MONTHLY_REQUEST_LIMIT` alerts at 80% of a configured
request allowance; it is not a measure of the OpenAI billing balance. Without
the key, the UI displays “AI API key has not been set.”

While the Google OAuth app is in Testing, add each Gmail sender to its OAuth
test-user list in Google Cloud. Public use of the Gmail scope needs Google's
verification. When accessing this laptop from another computer, tunnel both
ports 3000 and 8080 and use `http://localhost:3000`; the registered OAuth
callbacks and frontend redirect currently use `localhost`.

## Repeated requests and duplicate notifications

- **Idempotency keys.** `POST /api/v1/deals` (and the workspace creates) accept an
  `Idempotency-Key` header. The key is saved in the same transaction as the
  create, with the answer, so a repeat of the same request returns the first deal
  instead of making a second. A key belongs to one person and one kind of
  request, and remembers a fingerprint of the request: reusing it for different
  details is refused (422) instead of answering with the wrong deal. A failed
  create saves nothing, so a retry really runs. Keys are kept for 24 hours. The
  frontend sends one key per request (`lib/idempotency.ts`): the same request
  keeps its key until it succeeds.
- **Notifications.** The same message (same person, type, title and link) is not
  added again while an identical one is still unread (a unique index, so two
  requests at once cannot both add it). After it is read, it can be sent again.
  A batch assignment of leads sends the new owner one notification, not one per
  lead.

## Layout

Code lives under `com.nexus.crm`, one package per module.

| Package | Contents |
| --- | --- |
| `identity` | Sign-in, sessions and users. `SecurityConfig` holds the security rules. |
| `shared` | Error format, request ID, the current-user type and roles. No business logic. |
