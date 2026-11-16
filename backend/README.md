# Nexus CRM backend

Spring Boot 4 application on Java 25, built with Maven. The design is in
[../docs/backend-technical-design.md](../docs/backend-technical-design.md).

Built so far: the foundations (PostgreSQL, Flyway, one error format, request
IDs) and the **identity** module: Google sign-in, sessions, roles and users.
The business modules (leads, deals and so on) are not started.

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

## Layout

Code lives under `com.nexus.crm`, one package per module.

| Package | Contents |
| --- | --- |
| `identity` | Sign-in, sessions and users. `SecurityConfig` holds the security rules. |
| `shared` | Error format, request ID, the current-user type and roles. No business logic. |
