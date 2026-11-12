# Nexus CRM — Backend Technical Design

Nov 12, 2026 · @Likhith Raju

### Document control

| Field | Value |
| --- | --- |
| Document | Nexus CRM backend technical design |
| Author | @[Likhith Raju](mailto:likhith.r@webknot.in)|
| Written on | 12 Nov 2026 |
| Last updated | 12 Nov 2026 |
| Version | 0.2 |
| Status | Draft |
| Approval rule | Approved when every area in Sign-off (under Review and approval) is Approved or Approved with conditions. A single Rejected sends the document back to Draft |

#### Revision history

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 0.2 | 12 Nov 2026 | @[Likhith Raju](mailto:likhith.r@webknot.in) | Removed Apple sign-in (Google only); added document control and sign-off; added multiple currencies with INR as the default and reporting currency |
| 0.1 | 12 Nov 2026 | @[Likhith Raju](mailto:likhith.r@webknot.in) | First draft |

## Executive summary

Nexus CRM (customer relationship management) needs a backend for the Next.js frontend that already exists. This document proposes one Spring Boot application on one PostgreSQL database, for one company with about 10 concurrent users. Each choice says why it fits that size and what it costs. Labels used throughout: **Confirmed** (agreed fact), **Assumption** (believed true, not verified), **Decision** (proposed choice, open to review), **Open question** (needs an answer).

The main decisions:

- **Confirmed:** single company, so no multi-tenancy (one shared database serving several customer companies). Sign-in with Google, with JSON Web Tokens (JWT) for API access. No email integration in v1. Amounts in multiple currencies, with INR as the default and reporting currency.
- **Assumption:** about 10 concurrent users and under 20 requests per second at peak (see Capacity).
- **Decision: modular monolith.** One deployable app made of internal modules, with boundaries checked by Spring Modulith. *Why:* one process has ample headroom at this load. Separate services would add network failures and cross-service transactions without adding capacity we need. *Trade-off:* everything deploys together, and one slow module can affect the others.
- **Decision: invite-only sign-in.** Google sign-in uses OpenID Connect (OIDC, the identity layer built on OAuth 2.0). It proves who someone is. The backend then checks that the person was invited, and issues its own 15-minute access token and a rotating refresh token, both in httpOnly cookies (cookies that page JavaScript cannot read). *Why:* anyone can create a Google account, so identity alone must never grant access. *Trade-off:* we own token signing, key rotation and refresh-token storage instead of renting them from a hosted identity provider.
- **Decision: a Lead converts into a Contact, a Company and a Deal.** Pipeline stages live on Deals. *Why:* one customer can have several deals, and pipeline value and win rate are properties of deals, not people. *Trade-off:* users get an explicit convert step, and the dashboard's Recent Leads card changes slightly.
- **Decision: minimal infrastructure.** PostgreSQL also handles search, the event outbox and job locking. The outbox saves events in the same transaction as the data, so none are lost. The cache is Caffeine, which lives inside the app's memory. *Why:* every extra server is one more thing to run, secure and back up. *Trade-off:* if we ever run several app instances, the in-memory cache and job scheduling need revisiting.

**Estimate:** about 10 weeks for one developer, in seven phases (see Implementation plan). The design is reviewed and approved before any code is written; the next section tracks that.

## Review and approval

This document is a **draft**. No implementation starts until every review area below is Approved. Each stage has an exit condition, so it is always clear what the next step is.

1. **Draft.** The author writes the problem, requirements, architecture and proposed decisions. *Done when:* every section is filled and all unknowns are listed under Open questions.
2. **Technical review.** Each area's reviewer comments inline on the sections in the table below. *Done when:* every area in Sign-off has a named reviewer.
3. **Feedback and revision.** Disagreements are settled in comment threads. Each outcome goes into the decision log with its trade-off, and Open questions are updated. *Done when:* no unresolved threads remain.
4. **Approval.** Each reviewer records Approved, Approved with conditions, Changes requested or Rejected in Sign-off. Remaining risks are recorded in the decision log. *Done when:* all five areas are Approved or Approved with conditions, and Document status is set to Approved.
5. **Implementation.** Approved phases become engineering tasks, and each task links the section it implements.
6. **Post-implementation review.** Anything built differently from this design goes into the decision log, and the affected sections are updated to match what shipped.

### Sign-off

Each reviewer records a decision for their area, the date, and a comment. *Approved with conditions* needs its conditions in the comment. *Changes requested* and *Rejected* need a reason, and the specific objections go as inline comments on the sections concerned.

| Area | Sections to review | Reviewer | Decision | Decided on | Conditions or reason |
| --- | --- | --- | --- | --- | --- |
| Backend | Technical design, Database schema, API contracts, Error handling, Testing |  | Pending |  |  |
| Frontend | API contracts, Sign-in flow (cookies, CSRF), Error handling | To assign | Pending |  |  |
| Infrastructure | Technology stack, Deployment and rollback, Observability, Failure scenarios | To assign | Pending |  |  |
| Security | Sign-in flow, Security considerations, data retention | To assign | Pending |  |  |
| Product / sales lead | Goals, Scope, dashboard KPI definitions, Open questions | To assign | Pending |  |  |

### Decision log

| # | Decision | Status | Source and trade-off |
| --- | --- | --- | --- |
| D1 | Single company; no multi-tenancy | Proposed | Stated by product owner, 12 Nov 2026. Selling to other companies later would need a migration that adds a company identifier to every table. |
| D2 | Sign-in with Google (OIDC); JWT for API access | Proposed | Stated by product owner, 12 Nov 2026; Apple removed the same day. We own token issuance and rotation. |
| D3 | No email integration in v1 | Proposed | Stated by product owner, 12 Nov 2026. Reps log emails as activities by hand. |
| D4 | Modular monolith on Spring Boot | Proposed | Proposed here. Everything deploys together. |
| D5 | Lead converts into Contact + Company + Deal | Proposed | Proposed here. Adds a convert step to the UI. |
| D6 | PostgreSQL for data, search, outbox and job locks; Caffeine as the only cache | Proposed | Proposed here. Running more than one app instance would need a rework. |
| D7 | Invite-only access: sign-in succeeds only for users an admin has added | Proposed | Proposed here. Admins must add each user before their first sign-in. |
| D8 | Multiple currencies; INR is the default and the reporting currency; won deals keep their INR value from the close date | Proposed | Stated by product owner, 12 Nov 2026 (currencies and INR default); the close-date snapshot is proposed here. Needs an exchange-rate table and a daily rate job. |

## Goals and non-goals

The goal is a correct, secure, low-maintenance system of record for one sales team. It is not a platform built for scale it will not see. Every goal below has a check, so we can tell when it has been met.

### Goals

| Goal | How we know it is met |
| --- | --- |
| One source of truth for leads, contacts, companies, deals, activities and follow-ups | The sales team stops using its spreadsheets within 4 weeks of launch |
| Dashboard numbers that are correct and explainable | Every KPI (key performance indicator) on the dashboard has a written definition (see Data flow), and a test checks it against hand-calculated data |
| Secure sign-in with Google, for invited users only | An account that has not been invited is refused, and a security test proves it |
| Each rep sees only what their role allows | An automated test checks every endpoint against every role and against records owned by someone else |
| Fast enough that nobody notices the server | 95% of API requests finish in under 300 ms (p95 < 300 ms) at the expected load |
| Small enough for 1 or 2 developers to run | One deployable app plus one database; a release takes under 10 minutes and a rollback under 5 |
| Nothing lost when something fails | Database backups can be restored to any moment in the last 7 days, and a restore is rehearsed before launch |

### Non-goals

Each of these is excluded on purpose. Each has a reason, and a condition that would make us reconsider.

| Non-goal | Why not now | Reconsider when |
| --- | --- | --- |
| Serving several customer companies (multi-tenancy) | Confirmed single company (D1) | Nexus is sold to other companies |
| Horizontal scaling (many app instances) | One instance has about 10x headroom at the expected load | Sustained load passes 50 requests per second |
| 99.99% uptime | An internal tool for business hours; 99.5% is enough, and higher costs redundant infrastructure | The CRM becomes customer-facing |
| Microservices or event streaming (e.g. Kafka) | No independent scaling or team-ownership need (see Alternatives) | Several teams own separate parts, or an outside system consumes CRM events |
| A configurable platform (custom fields, workflow builder) | Fixed fields and stages are faster to build and easier to report on | Sales asks for the same customisation repeatedly |

## Scope

Version 1 covers every screen in the current frontend, plus what those screens need behind them: invitations, roles, audit and import. Everything in the second table is excluded from v1 on purpose.

### In scope (v1)

| Area | Included | Frontend screen |
| --- | --- | --- |
| Sign-in and identity | Google sign-in; invite-only accounts; roles Admin, Manager, Rep; sign out; sign out of all devices | Login, profile menu |
| Team management | Admin invites, disables and changes the role of users; managers have teams | Team Management |
| Leads | Create, edit, assign, change status, disqualify with a reason, soft delete and restore, CSV import, duplicate warning by email | Leads |
| Contacts and companies | Create, edit, link contacts to companies, view everything related to a contact | Contacts |
| Deals and pipeline | Convert a lead to a deal, move deals between fixed stages, mark won or lost with a reason, keep stage history | Sales Pipeline |
| Activities | Log notes, calls and meetings by hand, plus system entries (for example "stage changed"), shown on a timeline | Activities |
| Follow-ups | Tasks with a due date and an assignee; an in-app reminder when one is due or overdue; complete or cancel | Follow-ups, dashboard card |
| Dashboard and reports | The 7 KPIs, revenue vs monthly target, lead sources, pipeline by stage, for a chosen date range | Dashboard, Reports |
| Search | One search box across leads, contacts, companies and deals, tolerant of typos | Top-bar search |
| Notifications | In-app only: assignments, due follow-ups, deals won | Bell menu |
| Audit log | Who changed what and when, for every business record | Settings (admin only) |
| Website lead capture | A public endpoint for the website's contact form, with rate limiting and spam protection | None; used by the website |
| Currencies | Leads and deals in any enabled currency: INR (default), USD, AUD and SAR at launch, more enabled by an admin. Daily exchange rates; every report and the dashboard in INR | Leads, Sales Pipeline, Dashboard, Settings |

### Out of scope (v1)

| Excluded | Reason | Workaround in v1 |
| --- | --- | --- |
| Email integration (Gmail or Outlook sync, sending email) | Confirmed out of scope (D3) | Reps log emails as activities by hand |
| Email notifications and invite emails | Needs an email provider and deliverability setup | Admin shares the app link; reminders are in-app only |
| Calendar sync | Same OAuth and sync complexity as email | Follow-ups carry a due date and time |
| Multi-tenancy | Single company (D1) | None needed |
| Native mobile apps | Not requested | The web app works on phones |
| Custom fields, custom pipelines, workflow automation | Platform work with no stated need | Fixed fields; one pipeline with fixed stages |
| Quotes, invoices, products catalogue | Belongs to billing or ERP (enterprise resource planning) software, not a CRM v1 | Deal amount only |
| Marketing campaigns | Separate product area | "Campaign" is kept only as a lead source |
| Telephony and AI features | Not requested | None |

## Capacity and service levels

Expected peak is about 8 requests per second. We design for 50, which is roughly 6x headroom on one small app instance. At this size, correctness and security matter far more than throughput, and that is the main reason for a monolith.

**How the peak is estimated (Assumption):** 10 concurrent users. Opening a page fires about 8 API calls (for example the dashboard summary, recent leads, follow-ups, unread notifications and the current user). A busy user opens a page about every 10 seconds. So 10 users × 8 calls ÷ 10 s ≈ 8 requests per second at peak, and about 1 to 2 per second on average across a working day.

| Dimension | Assumption or target | Why it matters |
| --- | --- | --- |
| Concurrent users | 10 (Assumption, from product owner) | Drives everything below |
| Peak load | About 8 requests/s expected; designed and load-tested for 50 | Proves headroom without paying for it |
| Latency | p95 under 300 ms for reads and writes; dashboard summary p95 under 500 ms | Dashboard queries aggregate the most data |
| Data after 5 years | About 25k leads, 10k contacts, 5k deals, 400k activities, 1M audit rows; under 5 GB including indexes (Assumption, from the dashboard's \~400 new leads a month) | Fits in one PostgreSQL instance's memory and cache; no partitioning needed |
| App instance | 1 virtual CPU, 1.5 GB memory | Enough for the JVM (Java Virtual Machine) at this load |
| Database | Managed PostgreSQL, 2 virtual CPUs, 4 GB memory, 20 GB storage | Managed backups and point-in-time recovery |
| Database connections | Pool of 10 (HikariCP) | Requests are short; more connections would only add contention |
| Availability | 99.5% per month, about 3.6 hours of allowed downtime | Internal tool, used in business hours |
| Recovery point objective (RPO, the most data we can lose) | 5 minutes | Point-in-time recovery from the managed database |
| Recovery time objective (RTO, the longest we can be down) | 1 hour | Redeploying the app or restoring the database fits in this |

**Decision: one app instance.** *Why:* the expected load uses a few percent of one virtual CPU, and a single instance keeps the cache, scheduled jobs and deploys simple. *Trade-off:* the app is briefly unavailable during a host failure or restart (seconds to minutes), which the 99.5% target allows. Deploys still have no downtime, because the new version starts before the old one stops (see Deployment).

## Technology stack

The stack is the standard Spring ecosystem on PostgreSQL. Every extra component must replace work we would otherwise write ourselves, or remove a real risk. Versions are the current long-term-support (LTS) or stable lines as of November 2026. Exact patch versions are pinned when the project is created.

### What we use

| Concern | Choice | Why | Trade-off |
| --- | --- | --- | --- |
| Language and runtime | Java 25 (LTS) with virtual threads enabled | Long support window. Virtual threads let simple blocking code handle many concurrent requests without a reactive framework | None significant at this load |
| Framework | Spring Boot 4.x with Spring Web MVC | The team asked for Spring Boot. MVC is simpler to write, debug and test than WebFlux (Spring's reactive stack) | Fewer request threads than WebFlux at extreme concurrency, which virtual threads cover anyway |
| Module boundaries and events | Spring Monolith | Fails a test when one module reaches into another's internals. Provides the event outbox (event publication registry), so events are never lost | Adds a dependency and its conventions |
| Sign-in and tokens | Spring Security: OAuth2 Client for Google, OAuth2 Resource Server to validate our own JWTs | Built-in, well-reviewed implementations of OIDC and JWT validation | None significant |
| Database | PostgreSQL 17 or later, managed | Relational data with strong constraints. Also gives full-text search, JSONB (binary JSON) columns for audit diffs, and row locks for scheduling | A managed instance costs more than a database on the app server, but buys backups and point-in-time recovery |
| Schema migrations | Flyway | Versioned SQL files that run identically in every environment | Rollback is a forward fix, not an automatic undo (see Deployment) |
| Data access | Spring Data JPA (Hibernate) for create, read, update and delete; JdbcClient with plain SQL for reports | JPA removes boilerplate for entity work. Reports are aggregates that are clearer as SQL | Two styles to learn; the rule is "JPA for entities, SQL for reports" |
| Cache | Caffeine (in-memory L1 Cache is sufficient) | Two small hot spots: the signed-in user's status and role (60 s), and dashboard summaries (60 s). Holding them in memory avoids repeated database lookups on every request | Each app instance has its own copy. Fine with one instance; revisit with Redis if we ever run several |
| Scheduled jobs | Spring @Scheduled with ShedLock (database-backed lock) | Follow-up reminders and nightly metrics. ShedLock stops a job running twice while old and new versions overlap during a deploy | A lock table in the database |
| Rate limiting | Bucket4j (in-memory) | Protects sign-in, token refresh and the public lead form from abuse | Limits are per instance |
| Validation | Jakarta Bean Validation | Declarative input checks on every request object | None |
| API documentation | springdoc-openapi | Generates the OpenAPI spec (a machine-readable API description). The frontend generates its TypeScript types from it, so the two cannot drift apart | Annotations needed for good docs |
| Observability | Not Required | Health Checks are sufficient | Storing the logs in the `log/` folder is adequate |
| Build and packaging | Maven; container image built with Cloud Native Buildpacks (`spring-boot:build-image`) | No Dockerfile to maintain; reproducible images | Less control than a hand-written Dockerfile |
| Testing | JUnit 5, Testcontainers (real PostgreSQL in Docker), MockMvcTester, WireMock | Tests run against the same database engine as production | Docker is required on developer machines and in CI |
| Exchange rates | A daily scheduled job calling one configured rate provider with Spring's RestClient (10 s timeout, 3 retries) | Rates are stored, so every conversion can be reproduced and audited; no rate lookup happens during a user request | One external dependency, covered by the 7-day fallback and admin overrides |

### What we deliberately do not use

| Not used | Why not | Would use when |
| --- | --- | --- |
| Redis | Caffeine covers the cache with one instance; nothing needs shared state | Several app instances need a shared cache or rate limits |
| Kafka or RabbitMQ | The Modulith outbox delivers events inside the app; no outside system consumes them | An external system must receive CRM events |
| Elasticsearch or OpenSearch | PostgreSQL full-text search plus pg\_trgm (fuzzy matching) handles tens of thousands of rows quickly | Search spans millions of documents or needs relevance tuning |
| Kubernetes | One container and one database do not need an orchestrator | Many services or teams share a cluster |
| GraphQL | One frontend with known screens; REST plus OpenAPI is simpler to cache, secure and test | Many different clients need different data shapes |
| A hosted identity provider (Auth0, Keycloak, Cognito) | Google is the only sign-in method, and Spring Security covers it | We need enterprise SSO (single sign-on), multi-factor policies or many sign-in methods |

## Detailed technical design

### Architecture and component responsibilities

The backend is one Spring Boot app split into 10 business modules plus a shared kernel, behind a reverse proxy, on one PostgreSQL database. The diagram shows what talks to what. The table says what each module owns, which is the rule reviewers should check code against.

&#91;embedded content: Runtime architecture · one app container with 10 modules, one database\]

All traffic, from the frontend and from the website form, enters through the reverse proxy and the same security filters. Google is only contacted during sign-in. The app's one dependency at runtime is PostgreSQL.

**Decision: package by feature, with public APIs between modules.** Each module is a top-level package (`com.nexus.crm.leads`, and so on). Only its `api` subpackage is visible to other modules. Inside, the layers are `web` (controllers and request/response records), `application` (services, transactions, permission checks), `domain` (entities and business rules) and `persistence` (repositories). *Why:* a change to deals stays inside the deals package, and Spring Modulith fails the build if another module reaches into it. *Trade-off:* calls between modules go through small interfaces instead of injecting any repository directly.

| Module | Responsibility | Owns tables | Publishes events |
| --- | --- | --- | --- |
| identity | Google sign-in callback; issuing and refreshing tokens; users, teams, roles, invitations; resolving the signed-in user | app\_user, user\_identity, refresh\_token, team | UserInvited, UserDisabled |
| leads | Lead create, edit and assign; status changes; duplicate check; CSV import; the public website form; orchestrating lead conversion | lead, lead\_status\_history | LeadCreated, LeadAssigned, LeadStatusChanged, LeadConverted |
| contacts | Contacts and companies, and the links between them | contact, company | ContactCreated |
| deals | Deals, the fixed pipeline stages, stage moves, won and lost, stage history | deal, pipeline\_stage, deal\_stage\_history | DealStageChanged, DealWon, DealLost |
| activities | Timeline entries: manual notes, calls, meetings, plus system entries written in response to other modules' events | activity | None |
| followups | Tasks with due dates; the reminder job | follow\_up | FollowUpDue |
| notifications | In-app notifications, written in response to events (lead assigned, follow-up due, deal won) | notification | None |
| reporting | Dashboard and report queries; KPI definitions; monthly revenue targets; nightly metrics rollup | revenue\_target, daily\_metric | None |
| search | The top-bar search across leads, contacts, companies and deals | None (reads search views) | None |
| audit | Records every change to a business record: who, what, when | audit\_log | None |
| shared | Error format, paging, current-user helper; the money type, currencies and exchange rates (tables currency and exchange\_rate, plus the daily rate job). No other business logic | None | None |

**Decision: lead conversion is one synchronous transaction.** The leads module calls the contacts and deals APIs directly, so the lead, contact, company and deal are all created or none are. *Why:* a half-converted lead, for example a deal with no contact, is corrupt data that someone has to fix by hand. *Trade-off:* leads depends on contacts and deals at compile time. Every other cross-module reaction is an event.

**Decision: reporting and search read through database views.** They read other modules' data through SQL views (`report_*`, `search_*`) defined in migrations, not by querying those tables directly. *Why:* aggregate reports need joins across modules, and going through each module's API one record at a time would be slow and complex. *Trade-off:* a change to a table must also update its views, and a migration test catches a view that breaks.

**Decision: audit is written in the same transaction as the change.** *Why:* an audit row that can be lost is not an audit log. *Trade-off:* every write does one extra insert, which is negligible at this load.

### Data flow

Six flows cover almost all traffic. Sign-in is drawn because it branches. The rest are straight sequences.

#### 1. Sign-in with Google

&#91;embedded content: Sign-in flow · two account checks before any token is issued\]

A disabled user fails both checks: they are not an active linked user, and disabled users are excluded from the email match.

1. The user clicks "Continue with Google". The browser goes to the backend, which creates a random `state`, a `nonce` and a PKCE (Proof Key for Code Exchange) verifier. It stores them in a short-lived encrypted cookie and redirects to the provider.
2. The user signs in at the provider, which redirects back to the backend's callback with a one-time code.
3. The backend checks the `state`, exchanges the code (plus the PKCE verifier) for an ID token, and validates the token's signature, issuer, audience, expiry and nonce.
4. **Account check 1:** is this (provider, subject) pair already linked to an active user? The provider's `sub` (subject) claim is a stable ID for that person at that provider. Email is never used to recognise a returning user, because emails can change hands.
5. **Account check 2, first sign-in only:** is there an invited or active user whose email matches the token's verified email? If so, the identity is linked to that user. If not, sign-in is refused and the attempt is logged. No account is ever created automatically.
6. The backend issues the tokens as cookies and redirects to the dashboard:
   - Access token: a JWT, 15 minutes, signed with our private key (ES256). Claims: user ID, role, token ID, expiry. Cookie `nx_at`, HttpOnly, Secure, SameSite=Lax.
   - Refresh token: 256 random bits, 14 days. Only its SHA-256 hash is stored. Cookie `nx_rt`, HttpOnly, Secure, SameSite=Strict, sent only to the refresh endpoint.
   - CSRF token: cookie `XSRF-TOKEN`, readable by the frontend, which echoes it in a header on every change request.

#### 2. An authenticated API request

1. The request reaches the proxy, which forwards it to the app with the client's IP address.
2. Filters run in order: assign a request ID (`X-Request-Id`), apply the rate limit, validate the `nx_at` JWT (signature, expiry, issuer), and load the user's current status and role from the cache (60 s). A disabled user is rejected even if their token has not yet expired.
3. For POST, PUT, PATCH and DELETE, the `X-XSRF-TOKEN` header must match the `XSRF-TOKEN` cookie.
4. The controller validates the request body. The service checks record-level access (see Security), opens a transaction, writes the change and its audit row, and commits.
5. After commit, domain events go to listeners (activities, notifications). Each event is stored in the outbox first, so it is retried if a listener fails.

#### 3. Token refresh

1. When an API call returns 401 (Unauthorized) with code `token_expired`, the frontend calls `POST /api/v1/auth/refresh` once, then retries the original call.
2. The backend finds the refresh token by its hash. If it is valid, the token is marked as used and a new access and refresh pair is issued (rotation).
3. Reuse of an already-used refresh token suggests it was stolen. Every token in that sign-in session is revoked, the user must sign in again, and a security event is logged. Exception: reuse within 10 seconds gets the same new pair, so two browser tabs refreshing at once do not log the user out.

#### 4. Website lead capture

1. The website form posts to `POST /api/v1/public/leads` with a CAPTCHA token and an `Idempotency-Key` header (a unique ID per submission, so a retried request creates one lead, not two).
2. The app verifies the CAPTCHA, applies a strict per-IP rate limit, checks a hidden honeypot field (a field humans never fill but bots do), and validates the input.
3. If an open lead already exists with that email, an activity is added to it instead of creating a duplicate. Otherwise a new lead with source `WEBSITE` is created, unassigned.
4. `LeadCreated` notifies managers in-app.

#### 5. Lead conversion

1. `POST /api/v1/leads/{id}/convert` carries the lead's `version` and the new or existing company, the contact details and the deal (name, amount, expected close date).
2. In one transaction: lock the lead with optimistic locking (the update fails if the version changed), create or attach the company, create the contact, create the deal in stage Qualified, mark the lead `CONVERTED` with links to all three, and write status history and audit rows.
3. After commit, `LeadConverted` adds a timeline entry and refreshes the dashboard cache.

#### 6. Deal stage change and dashboard

1. Dragging a deal on the pipeline board sends `PATCH /api/v1/deals/{id}/stage` with the target stage and the deal's `version`. A stale version returns 409 (Conflict), and the board reloads that deal.
2. The service checks the move is allowed. Moving to Lost requires a reason; Won and Lost set `closed_at`. Each move writes a `deal_stage_history` row.
3. `DealStageChanged`, `DealWon` and `DealLost` drive the timeline and notifications, and clear the cached dashboard summary.
4. `GET /api/v1/dashboard/summary?from=&to=` is served from the cache (60 s, keyed by date range and visibility scope) or computed with SQL over the report views.

#### Dashboard KPI definitions

**Decision:** every number on the dashboard has exactly one definition, and a test with hand-calculated data checks each one. *Why:* most disputes about CRM numbers come from two people meaning different things by "qualified" or "conversion". Each KPI is scoped to what the viewer may see (own, team, or everyone). "vs last month" compares with the previous period of the same length.

| KPI | Definition |
| --- | --- |
| Total leads | Leads created on or before the end of the range, excluding deleted ones |
| New leads | Leads created within the range |
| Qualified leads | Leads whose status changed to Qualified within the range (from status history) |
| Deals won | Deals moved to Won with `closed_at` within the range |
| Deals lost | Deals moved to Lost with `closed_at` within the range |
| Revenue | Sum of the INR values of deals won within the range, each converted at its close-date rate (base\_amount) |
| Conversion rate | Leads converted within the range ÷ new leads within the range (see Open questions) |
| Revenue vs target | Won revenue per calendar month in INR against that month's INR target |
| Lead sources | Share of new leads within the range, by source |
| Pipeline overview | Open deals now: count and total amount per stage, in INR at the latest rates |

### Currencies and exchange rates

Every amount is stored in the currency it was agreed in. Every report, target and dashboard figure is in INR, the base currency, converted with daily exchange rates. A won deal keeps the INR value it had on the day it closed, so reported revenue never changes afterwards.

- **Confirmed:** INR is the default currency for new leads and deals, and the reporting currency. Enabled at launch: INR, USD (US dollar), AUD (Australian dollar) and SAR (Saudi riyal). An admin can enable any other ISO 4217 currency without a release (see Open questions 7 and 8).
- **Decision: store the original amount and currency, never a converted one.** *Why:* the customer's contract is in their currency, and converting at entry would change the number the rep negotiated. *Trade-off:* every report needs a conversion step.
- **Decision: snapshot the INR value when a deal closes.** When a deal moves to Won or Lost, the backend stores `base_amount` (amount × the rate for the close date) and the `fx_rate` it used. *Why:* last quarter's revenue must give the same number next year, whatever exchange rates do. *Trade-off:* a closed deal's INR value differs slightly from converting it at today's rate. That is intended, and the API returns both values.
- **Decision: open pipeline is converted at the latest rate.** Pipeline value, and leads' estimated value, are converted to INR when requested, using the most recent rate. *Why:* nothing is final for open deals. *Trade-off:* the pipeline total moves with exchange rates even when no deal changes, so the dashboard labels it "at today's rates".
- **Decision: rates live in a table, filled by a daily job.** A scheduled job (ShedLock-protected) fetches one rate per enabled currency per day from a configured provider, with a 10-second timeout and up to 3 retries. An admin can override any day's rate, for example to match finance's monthly rate. *Trade-off:* one external dependency, which the next point covers.
- **Missing rates:** if a date has no rate, the most recent earlier rate is used, up to 7 days old. Beyond that, figures in that currency are still shown but flagged `fxStale: true`, the dashboard shows "exchange rates out of date", and a warning is logged.
- **Precision:** amounts are stored as `numeric(19,4)`, so currencies with 3 decimal places (for example KWD, the Kuwaiti dinar) fit. The API rejects more decimals than the currency allows (422 `invalid_precision`).
- **Revenue targets** are set in INR only.
- **Display:** the frontend formats each amount in its own currency with the browser's `Intl.NumberFormat`. INR uses Indian digit grouping (for example ₹12,34,567.00). Amounts in other currencies show their INR equivalent on hover.
- **Changing currency:** an open deal's currency can be edited; a won or lost deal's cannot (409 `invalid_state`).

### API contracts

The API is REST (resource-oriented HTTP with JSON) under `/api/v1`. The OpenAPI spec generated from the code is the contract. The frontend generates its TypeScript types from that spec, and CI (continuous integration) fails if a change would break an existing client.

#### Conventions

| Topic | Rule | Why |
| --- | --- | --- |
| Versioning | `/api/v1`. Within v1, changes are additive only: new fields and endpoints, never removed or renamed ones. A breaking change means `/api/v2` | The frontend and backend can deploy independently without breaking each other |
| Format | JSON, camelCase field names, enum values in UPPER\_SNAKE\_CASE | One style everywhere |
| Time | Timestamps in ISO-8601 UTC (`2026-11-12T09:30:00Z`); date-only fields as `2026-11-12` | No timezone ambiguity; the frontend converts to local time |
| IDs | UUIDv7 (universally unique IDs that sort by creation time) | Not guessable from neighbouring records; time-ordered, so database indexes stay compact |
| Money | `{"amount": "48000.00", "currency": "INR"}`, amount as a string. The currency must be an enabled ISO 4217 code and defaults to INR. Deals also return baseAmount (INR), fxRate and fxRateDate | JavaScript numbers are floating point and can round cents incorrectly |
| Partial updates | PATCH with JSON Merge Patch (RFC 7396): fields sent are changed, `null` clears a field, absent fields are untouched | Edit forms send only what changed |
| Concurrent edits | Every editable resource returns `version`. Writes must send it back, and a mismatch returns 409 `version_conflict` with the current state | Two people editing the same deal do not silently overwrite each other |
| Pagination | Cursor-based: `?limit=25&cursor=…` returns `{ items, nextCursor }`. Maximum limit 100 | Stable while new rows arrive, and fast on large tables, unlike page offsets |
| Sorting and filtering | `sort=createdAt,desc` on an allow-list of fields per endpoint; filters as named query parameters | No raw column names from clients, which prevents SQL injection through sorting |
| Safe retries | `Idempotency-Key` header on creates that may be retried (public lead form, convert, import). Keys are kept for 24 hours | A network retry never creates two records |
| Rate limits | 429 Too Many Requests with a `Retry-After` header | Tells clients exactly when to retry |

#### Endpoints

Roles: R = Rep, M = Manager, A = Admin. Scope rules (own records, team records, all records) are in Security considerations.

| Area | Endpoints | Roles |
| --- | --- | --- |
| Sign-in | `GET /auth/oauth2/authorization/google` (start), `GET /auth/login/oauth2/code/google` (callback), `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/logout-all`, `GET /me` | Public, then any signed-in user |
| Users and teams | `GET /users`, `POST /users` (invite), `PATCH /users/{id}` (role, team, status), `GET\|POST\|PATCH /teams` | Read: R, M, A. Change: A |
| Leads | `GET /leads`, `POST /leads`, `GET\|PATCH\|DELETE /leads/{id}`, `POST /leads/{id}/restore`, `POST /leads/{id}/assign`, `POST /leads/{id}/status`, `POST /leads/{id}/convert`, `POST /leads/imports` (CSV, returns 202 Accepted and a job ID), `GET /leads/imports/{jobId}` | R, M, A, within scope. Assign, restore and import: M, A |
| Website form | `POST /public/leads` | Public (CAPTCHA plus rate limit) |
| Contacts and companies | `GET\|POST /contacts`, `GET\|PATCH\|DELETE /contacts/{id}`, `GET\|POST /companies`, `GET\|PATCH /companies/{id}` | R, M, A, within scope |
| Deals and pipeline | `GET\|POST /deals`, `GET\|PATCH /deals/{id}`, `PATCH /deals/{id}/stage`, `GET /pipeline/stages`, `GET /pipeline/board` | R, M, A, within scope |
| Activities | `GET /activities?leadId=\|contactId=\|dealId=`, `POST /activities`, `PATCH\|DELETE /activities/{id}` (author only) | R, M, A |
| Follow-ups | `GET /follow-ups?assignee=me&due=today\|overdue`, `POST /follow-ups`, `PATCH /follow-ups/{id}`, `POST /follow-ups/{id}/complete` | R, M, A, within scope |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/read` | Own notifications only |
| Dashboard and reports | `GET /dashboard/summary?from=&to=`, `GET /reports/revenue`, `GET /reports/lead-sources`, `GET /reports/pipeline`, `PUT /revenue-targets/{yyyy-mm}` | Read: R, M, A, within scope. Targets: A |
| Search | `GET /search?q=&types=` | R, M, A, within scope |
| Audit | `GET /audit?entityType=&entityId=` | A |
| Operations | `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus` | Internal network only; the proxy blocks them from outside |
| Currencies and rates | GET /currencies, PATCH /currencies/{code} (enable or disable), GET /exchange-rates?currency=&from=&to=, PUT /exchange-rates/{currency}/{date} (manual override) | Read: R, M, A. Change: A |

#### Examples

Create a lead: `POST /api/v1/leads`

```json
{
  "firstName": "Olivia",
  "lastName": "Martin",
  "email": "olivia@apexdynamics.io",
  "companyName": "Apex Dynamics",
  "source": "REFERRAL",
  "ownerId": "01933b8e-5f2a-7c1e-9a44-2b6f0d3e8a10",
  "estimatedValue": { "amount": "48000.00", "currency": "USD" }
}
```

Response `201 Created`, with a `Location: /api/v1/leads/{id}` header:

```json
{
  "id": "01933b90-1c7d-7f02-8b11-6e2a9f4c5d21",
  "firstName": "Olivia",
  "lastName": "Martin",
  "email": "olivia@apexdynamics.io",
  "companyName": "Apex Dynamics",
  "source": "REFERRAL",
  "status": "NEW",
  "owner": { "id": "01933b8e-5f2a-7c1e-9a44-2b6f0d3e8a10", "name": "Sarah Chen" },
  "estimatedValue": { "amount": "48000.00", "currency": "USD" },
  "possibleDuplicates": [],
  "createdAt": "2026-11-12T09:30:00Z",
  "updatedAt": "2026-11-12T09:30:00Z",
  "version": 0
}
```

List leads: `GET /api/v1/leads?status=NEW&ownerId=me&sort=createdAt,desc&limit=25`

```json
{
  "items": [ { "id": "01933b90-1c7d-7f02-8b11-6e2a9f4c5d21", "firstName": "Olivia", "status": "NEW", "version": 0 } ],
  "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTExLTEyVDA5OjMwOjAwWiJ9"
}
```

Move a deal: `PATCH /api/v1/deals/{id}/stage`

```json
{ "stageId": "PROPOSAL", "version": 3 }
```

The response is the updated deal with `"version": 4`. If someone else moved the deal first, the response is 409 `version_conflict` (see Error handling).

### Database schema

The database is PostgreSQL, with one schema and 21 tables (plus two framework tables). Every rule that can live in the database does: foreign keys, CHECK constraints and partial unique indexes. The service layer repeats those checks to give friendly errors; the database stops bad data even if the code has a bug.

&#91;embedded content: Core tables and foreign keys · framework and plumbing tables omitted\]

Activities and follow-ups (dashed) link to a lead, a contact or a deal through three nullable foreign keys, with a CHECK that at least one is set. Ownership lines to app\_user are left out to keep the picture readable.

#### Conventions

- **Decision: soft delete on business records** (`deleted_at`). *Why:* CRM users delete things by mistake, and an admin restore is cheaper than a database restore. *Trade-off:* every query must exclude deleted rows. Entities enforce this with Hibernate's `@SQLRestriction`, and the report views include the same filter.
- **Decision: enums as `text` with a CHECK constraint**, not PostgreSQL enum types. *Why:* changing a PostgreSQL enum inside a migration is awkward. *Trade-off:* the allowed values appear in both the CHECK constraint and the Java enum, and a test checks they match.
- **Money** is an amount (`numeric(19,4)`) plus a `char(3)` ISO 4217 currency code that references the `currency` table, defaulting to `INR`. Four decimal places cover currencies with three minor units; the API enforces each currency's own limit. **Time** is `timestamptz`, stored in UTC. **IDs** are UUIDv7, generated by the app.
- **Every business table** has `created_at`, `updated_at`, `created_by`, `updated_by`, `version` (for optimistic locking) and `deleted_at`. Below, these are shortened to `/* audit columns */`.
- **History and audit tables are append-only.** The app's database role has INSERT and SELECT on them, but no UPDATE or DELETE. *Why:* the dashboard and the audit trail must not be quietly rewritten, even by a bug.

#### Tables

```sql
CREATE EXTENSION IF NOT EXISTS citext;   -- case-insensitive text, for emails
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- trigram fuzzy matching, for search

-- currencies (shared) ---------------------------------------------------
CREATE TABLE currency (
  code         char(3) PRIMARY KEY,      -- ISO 4217: 'INR','USD','AUD','SAR', ...
  name         text NOT NULL,
  minor_units  smallint NOT NULL CHECK (minor_units BETWEEN 0 AND 4),  -- decimals: INR 2, KWD 3
  enabled      boolean NOT NULL DEFAULT true,
  is_base      boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX ON currency (is_base) WHERE is_base;   -- exactly one base currency
-- seeded by migration: INR (base), USD, AUD, SAR

CREATE TABLE exchange_rate (
  currency      char(3) NOT NULL REFERENCES currency(code),
  rate_date     date NOT NULL,
  rate_to_base  numeric(18,8) NOT NULL CHECK (rate_to_base > 0),  -- 1 unit = this many INR
  source        text NOT NULL,           -- provider name, or 'MANUAL' for an admin override
  updated_by    uuid,                    -- set for manual overrides
  updated_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (currency, rate_date)
);

-- identity ---------------------------------------------------------------
CREATE TABLE team (
  id          uuid PRIMARY KEY,
  name        text NOT NULL UNIQUE,
  manager_id  uuid               -- FK added after app_user exists
);

CREATE TABLE app_user (
  id            uuid PRIMARY KEY,
  email         citext NOT NULL UNIQUE,
  display_name  text NOT NULL,
  avatar_url    text,
  role          text NOT NULL CHECK (role IN ('ADMIN','MANAGER','REP')),
  status        text NOT NULL CHECK (status IN ('INVITED','ACTIVE','DISABLED')),
  team_id       uuid REFERENCES team(id),
  invited_by    uuid REFERENCES app_user(id),
  last_login_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version int NOT NULL DEFAULT 0
);
ALTER TABLE team ADD FOREIGN KEY (manager_id) REFERENCES app_user(id);

CREATE TABLE user_identity (
  id             uuid PRIMARY KEY,
  user_id        uuid NOT NULL REFERENCES app_user(id),
  provider       text NOT NULL CHECK (provider IN ('GOOGLE')),
  subject        text NOT NULL,          -- the provider's stable 'sub' claim
  email_at_link  citext,
  linked_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, subject)
);

CREATE TABLE refresh_token (
  id           uuid PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES app_user(id),
  family_id    uuid NOT NULL,            -- one per sign-in; revoked together on reuse
  token_hash   bytea NOT NULL UNIQUE,    -- SHA-256 of the token, never the token itself
  issued_at    timestamptz NOT NULL,
  expires_at   timestamptz NOT NULL,
  used_at      timestamptz,
  revoked_at   timestamptz,
  user_agent   text,
  ip           inet
);
CREATE INDEX ON refresh_token (user_id);
CREATE INDEX ON refresh_token (family_id);

-- contacts ---------------------------------------------------------------
CREATE TABLE company (
  id        uuid PRIMARY KEY,
  name      text NOT NULL,
  domain    citext,
  industry  text,
  owner_id  uuid NOT NULL REFERENCES app_user(id),
  /* audit columns */
);
CREATE UNIQUE INDEX ON company (domain) WHERE deleted_at IS NULL AND domain IS NOT NULL;

CREATE TABLE contact (
  id          uuid PRIMARY KEY,
  first_name  text NOT NULL,
  last_name   text NOT NULL,
  email       citext,
  phone       text,
  job_title   text,
  company_id  uuid REFERENCES company(id),
  owner_id    uuid NOT NULL REFERENCES app_user(id),
  /* audit columns */
);
CREATE UNIQUE INDEX ON contact (email) WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE INDEX ON contact (company_id);
CREATE INDEX ON contact (owner_id) WHERE deleted_at IS NULL;

-- leads ------------------------------------------------------------------
CREATE TABLE lead (
  id                    uuid PRIMARY KEY,
  first_name            text NOT NULL,
  last_name             text NOT NULL,
  email                 citext,
  phone                 text,
  company_name          text,
  job_title             text,
  source                text NOT NULL CHECK (source IN ('WEBSITE','SOCIAL','REFERRAL','CAMPAIGN','MANUAL','IMPORT')),
  status                text NOT NULL CHECK (status IN ('NEW','CONTACTED','QUALIFIED','DISQUALIFIED','CONVERTED')),
  owner_id              uuid REFERENCES app_user(id),   -- NULL = unassigned queue
  estimated_value       numeric(19,4) CHECK (estimated_value >= 0),
  currency              char(3) NOT NULL DEFAULT 'INR' REFERENCES currency(code),
  disqualify_reason     text,
  converted_at          timestamptz,
  converted_company_id  uuid REFERENCES company(id),
  converted_contact_id  uuid REFERENCES contact(id),
  converted_deal_id     uuid,                            -- FK added after deal exists
  search_text           text GENERATED ALWAYS AS (
                          lower(first_name || ' ' || last_name || ' ' ||
                                coalesce(email::text, '') || ' ' || coalesce(company_name, ''))) STORED,
  /* audit columns */
  CHECK (status <> 'CONVERTED'    OR converted_deal_id IS NOT NULL),
  CHECK (status <> 'DISQUALIFIED' OR disqualify_reason IS NOT NULL)
);
-- one OPEN lead per email; converted or disqualified leads may repeat
CREATE UNIQUE INDEX ON lead (email)
  WHERE deleted_at IS NULL AND status IN ('NEW','CONTACTED','QUALIFIED');
CREATE INDEX ON lead (owner_id, status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ON lead (created_at);
CREATE INDEX ON lead USING gin (search_text gin_trgm_ops);

CREATE TABLE lead_status_history (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  lead_id      uuid NOT NULL REFERENCES lead(id),
  from_status  text,
  to_status    text NOT NULL,
  changed_at   timestamptz NOT NULL DEFAULT now(),
  changed_by   uuid REFERENCES app_user(id)
);
CREATE INDEX ON lead_status_history (to_status, changed_at);
CREATE INDEX ON lead_status_history (lead_id);

-- deals ------------------------------------------------------------------
CREATE TABLE pipeline_stage (
  code             text PRIMARY KEY,     -- 'QUALIFIED','DEMO','PROPOSAL','NEGOTIATION','WON','LOST'
  name             text NOT NULL,
  position         smallint NOT NULL UNIQUE,
  kind             text NOT NULL CHECK (kind IN ('OPEN','WON','LOST')),
  win_probability  smallint NOT NULL CHECK (win_probability BETWEEN 0 AND 100)
);

CREATE TABLE deal (
  id                   uuid PRIMARY KEY,
  name                 text NOT NULL,
  company_id           uuid NOT NULL REFERENCES company(id),
  primary_contact_id   uuid REFERENCES contact(id),
  lead_id              uuid REFERENCES lead(id),
  owner_id             uuid NOT NULL REFERENCES app_user(id),
  stage_code           text NOT NULL REFERENCES pipeline_stage(code),
  amount               numeric(19,4) NOT NULL CHECK (amount >= 0),
  currency             char(3) NOT NULL DEFAULT 'INR' REFERENCES currency(code),
  source               text,                         -- copied from the lead, for attribution
  expected_close_date  date,
  closed_at            timestamptz,
  lost_reason          text,
  base_amount          numeric(19,4),                -- INR value at the close-date rate; set on Won or Lost
  fx_rate              numeric(18,8),                -- rate used for base_amount
  fx_rate_date         date,
  /* audit columns */
  CHECK (stage_code <> 'LOST' OR lost_reason IS NOT NULL)
);
ALTER TABLE lead ADD FOREIGN KEY (converted_deal_id) REFERENCES deal(id);
CREATE INDEX ON deal (stage_code, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX ON deal (closed_at) WHERE closed_at IS NOT NULL;
CREATE INDEX ON deal (company_id);

CREATE TABLE deal_stage_history (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  deal_id           uuid NOT NULL REFERENCES deal(id),
  from_stage        text REFERENCES pipeline_stage(code),
  to_stage          text NOT NULL REFERENCES pipeline_stage(code),
  amount_at_change  numeric(19,4) NOT NULL,
  currency          char(3) NOT NULL REFERENCES currency(code),
  changed_at        timestamptz NOT NULL DEFAULT now(),
  changed_by        uuid REFERENCES app_user(id)
);
CREATE INDEX ON deal_stage_history (to_stage, changed_at);
CREATE INDEX ON deal_stage_history (deal_id);

-- activities and follow-ups ----------------------------------------------
CREATE TABLE activity (
  id           uuid PRIMARY KEY,
  type         text NOT NULL CHECK (type IN ('NOTE','CALL','MEETING','EMAIL_LOG','SYSTEM')),
  subject      text NOT NULL,
  body         text,                     -- plain text; rendered escaped
  occurred_at  timestamptz NOT NULL,
  actor_id     uuid REFERENCES app_user(id),   -- NULL for system entries
  lead_id      uuid REFERENCES lead(id),
  contact_id   uuid REFERENCES contact(id),
  deal_id      uuid REFERENCES deal(id),
  /* audit columns */
  CHECK (num_nonnulls(lead_id, contact_id, deal_id) >= 1)
);
CREATE INDEX ON activity (lead_id, occurred_at DESC)    WHERE lead_id IS NOT NULL;
CREATE INDEX ON activity (contact_id, occurred_at DESC) WHERE contact_id IS NOT NULL;
CREATE INDEX ON activity (deal_id, occurred_at DESC)    WHERE deal_id IS NOT NULL;

CREATE TABLE follow_up (
  id            uuid PRIMARY KEY,
  title         text NOT NULL,
  notes         text,
  due_at        timestamptz NOT NULL,
  assignee_id   uuid NOT NULL REFERENCES app_user(id),
  status        text NOT NULL CHECK (status IN ('OPEN','DONE','CANCELLED')),
  completed_at  timestamptz,
  reminded_at   timestamptz,
  lead_id       uuid REFERENCES lead(id),
  contact_id    uuid REFERENCES contact(id),
  deal_id       uuid REFERENCES deal(id),
  /* audit columns */
  CHECK (num_nonnulls(lead_id, contact_id, deal_id) >= 1)
);
CREATE INDEX ON follow_up (assignee_id, status, due_at);
CREATE INDEX ON follow_up (due_at) WHERE status = 'OPEN' AND reminded_at IS NULL;  -- reminder job

-- notifications, audit, reporting, plumbing --------------------------------
CREATE TABLE notification (
  id            uuid PRIMARY KEY,
  recipient_id  uuid NOT NULL REFERENCES app_user(id),
  type          text NOT NULL,
  title         text NOT NULL,
  link          text,                    -- in-app path only, e.g. /deals/{id}
  read_at       timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON notification (recipient_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE audit_log (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  occurred_at  timestamptz NOT NULL DEFAULT now(),
  actor_id     uuid REFERENCES app_user(id),
  entity_type  text NOT NULL,
  entity_id    uuid NOT NULL,
  action       text NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE','RESTORE','CONVERT')),
  changes      jsonb,                    -- {field: [old, new]}
  request_id   text
);
CREATE INDEX ON audit_log (entity_type, entity_id, occurred_at DESC);

CREATE TABLE revenue_target (
  month       date PRIMARY KEY CHECK (extract(day FROM month) = 1),
  amount      numeric(19,2) NOT NULL CHECK (amount >= 0),
  currency    char(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),  -- targets are in the base currency
  updated_by  uuid REFERENCES app_user(id),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE daily_metric (              -- nightly rollup, rebuilt idempotently
  day       date NOT NULL,
  owner_id  uuid NOT NULL REFERENCES app_user(id),
  metric    text NOT NULL,
  value     numeric(19,2) NOT NULL,
  PRIMARY KEY (day, owner_id, metric)
);

CREATE TABLE idempotency_key (
  scope            text NOT NULL,          -- endpoint name
  key              text NOT NULL,
  response_status  int NOT NULL,
  response_body    jsonb,
  created_at       timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (scope, key)
);

CREATE TABLE import_job (
  id            uuid PRIMARY KEY,
  uploaded_by   uuid NOT NULL REFERENCES app_user(id),
  status        text NOT NULL CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
  total_rows    int, imported_rows int, failed_rows int,
  error_report  jsonb,                     -- row number + reason, for download
  created_at    timestamptz NOT NULL DEFAULT now(),
  finished_at   timestamptz
);
-- plus framework tables: event_publication (Spring Modulith outbox) and shedlock
```

#### Data lifecycle

| Data | Kept for | How it is removed |
| --- | --- | --- |
| Leads, contacts, companies, deals, activities | Until deleted; soft-deleted rows purged after 90 days | Nightly job; an admin can restore within the 90 days |
| Audit log | All Time | Never purged; on an erasure request, the person's values inside audit entries are blanked |
| Notifications | 90 days | Nightly job |
| Refresh tokens | 30 days after expiry | Nightly job |
| Idempotency keys | 24 hours | Hourly job |
| Personal data on request (for example under GDPR, the EU data protection law) | Erased within 30 days | Admin action that blanks the person's fields and keeps an audit entry without them |

### Error handling

Every error response uses one format: Problem Details (RFC 9457, the internet standard for HTTP API errors, built into Spring). Each error carries a stable machine-readable `code` that the frontend switches on, and a `requestId` that support can find in the logs. Internal details never leave the server.

#### Error format

```json
{
  "type": "https://nexus-crm.example/errors/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "2 fields are invalid.",
  "instance": "/api/v1/leads",
  "code": "validation_failed",
  "requestId": "6f1c2a9e4b7d",
  "errors": [
    { "field": "email", "code": "invalid_format", "message": "Must be a valid email address." },
    { "field": "lastName", "code": "required", "message": "Last name is required." }
  ]
}
```

#### Status codes and when they are used

| Status | `code` values | When | What the frontend does |
| --- | --- | --- | --- |
| 400 Bad Request | `malformed_request` | Body is not valid JSON, or a parameter has the wrong type | Shows a generic error; this is a bug to fix |
| 401 Unauthorized | `token_expired`, `not_authenticated` | Access token missing, expired or invalid | `token_expired`: refreshes once and retries. Otherwise sends the user to sign-in |
| 403 Forbidden | `forbidden`, `csrf_invalid` | The role does not allow the action (for example a Rep inviting users) | Shows "You don't have access" |
| 404 Not Found | `not_found` | The record does not exist, **or exists but is outside the user's scope** | Shows the not-found state |
| 409 Conflict | `version_conflict`, `duplicate`, `invalid_state` | Stale `version`; an open lead with this email already exists; an invalid transition such as converting a converted lead | Version conflict: reloads and shows what changed. Duplicate: links to the existing record |
| 413 Payload Too Large | `file_too_large` | CSV import over 5 MB | Shows the limit |
| 422 Unprocessable Content | `validation_failed`, unsupported\_currency, invalid\_precision | Field rules broken; details in `errors[]` | Highlights each field |
| 429 Too Many Requests | `rate_limited` | Rate limit hit; `Retry-After` header set | Waits and retries, or tells the user |
| 500 Internal Server Error | `internal_error` | Anything unexpected | Shows "Something went wrong" with the `requestId` |
| 503 Service Unavailable | `unavailable` | Database unreachable, or the app is shutting down | Shows a banner and retries reads with backoff |

#### Rules

- **Decision: return 404, not 403, for records outside the user's scope.** *Why:* a 403 confirms the record exists, which leaks information, for example that a competitor is a customer. *Trade-off:* a little harder to debug, so the server logs the real reason with the request ID.
- **One global handler** (`@RestControllerAdvice`) maps exceptions to responses. Services throw typed exceptions such as `NotFoundException`, `ConflictException` and `InvalidStateException`, never HTTP types. *Why:* business code stays independent of HTTP, and every endpoint behaves the same way.
- **Never in a response:** stack traces, SQL, class names, other users' data. **Always in the logs for 5xx errors:** the full exception, the request ID and the user ID.
- **Retries:** the frontend retries only safe requests (GET) on 503 and 429, and creates only when an `Idempotency-Key` was sent. It never retries other writes automatically.
- **Messages** are written for the end user. The `code` is the contract. Message wording may change at any time without breaking clients.
- **Database constraint violations** (for example the unique open-lead email index) are caught and translated to 409 `duplicate`, so a race between two requests still produces a clean error rather than a 500.

## Alternatives considered

Each row is a real option we rejected, with the reason and with what the chosen option costs us. Reviewers who disagree should comment on the row; if the outcome changes, it moves to the decision log.

| Area | Chosen | Rejected alternative | Why rejected | What the chosen option costs |
| --- | --- | --- | --- | --- |
| Architecture | Modular monolith | Microservices (one service per module) | 10 users need no independent scaling. Lead conversion would become a distributed transaction across 3 services | No independent deploys per module; one bad release affects everything |
| API sessions | JWT access token plus rotating refresh token (owner's choice) | Server-side sessions (Spring Session in PostgreSQL) | Owner prefers JWT; it also suits a future mobile client | Access tokens cannot be revoked instantly. Mitigated by the 15-minute expiry and the per-request user-status check |
| Who signs the tokens | The backend (Spring Security) | A hosted identity provider such as Auth0, Keycloak or Cognito | Only one sign-in method, standard OIDC. A provider adds cost, or a server to run | We own key rotation and refresh-token storage |
| Where tokens live in the browser | HttpOnly cookies | `localStorage` with an Authorization header | Any XSS (cross-site scripting) bug could read tokens from `localStorage`; httpOnly cookies cannot be read by scripts | Cookies need CSRF protection, and the frontend and API must share a site |
| Lead model | Lead converts into Contact + Company + Deal | One "lead" record that moves through every stage | Repeat customers, several contacts per company and deal-level win rates are impossible to model cleanly | A convert step in the UI; more tables |
| Linking activities and follow-ups | Three nullable foreign keys plus a CHECK | One polymorphic pair (`entity_type`, `entity_id`) | A polymorphic pair cannot have foreign keys, so orphans and typos go unnoticed | Adding a new linkable type means adding a column |
| Web framework | Spring MVC with virtual threads | Spring WebFlux (reactive) | Harder to write, debug and test; no throughput need | None at this scale |
| Data access | JPA for entities, JdbcClient for reports | jOOQ everywhere, or JPA everywhere | jOOQ adds code generation to the build. JPA is poor at aggregate queries | Two styles in one codebase |
| Async work | Spring Modulith events with the database outbox | Kafka or RabbitMQ | No outside consumers. A broker is another stateful system to run and secure | Events stay inside the app; an outside consumer would need a broker later |
| Cache | Caffeine, in memory | Redis | One instance has nothing to share; Redis is another server | The cache is lost on restart (it warms up again in seconds) |
| Search | PostgreSQL full-text search plus trigram matching | Elasticsearch or OpenSearch | Tens of thousands of rows; a second datastore would need syncing | Weaker relevance ranking than a search engine |
| Pagination | Cursor (keyset) | Page number and offset | Offsets get slower with depth and skip or repeat rows while new data arrives | No "jump to page 37"; totals only on request |
| Database | PostgreSQL | MongoDB or another document store | CRM data is relational: constraints and joins are the point | Schema changes need migrations |
| API style | REST plus OpenAPI | GraphQL | One known client; REST is simpler to secure per endpoint, cache and rate-limit | Some screens need two or three calls instead of one query |
| Hosting | One container plus a managed database | Kubernetes | Nothing to orchestrate | Fewer self-healing features; restarts rely on the platform's policy |
| Monitoring | Health checks plus log files (owner's choice) | A metrics, tracing and alerting stack (OpenTelemetry with Grafana, or a hosted equivalent) | Owner judges the full stack unnecessary at this size | No dashboards or traces; slow requests and error spikes go unseen unless someone reads the logs (see Observability for the minimum we still recommend) |
| Currency handling | Original amount and currency stored; INR value snapshotted when a deal closes | Convert everything to INR on entry; or convert every amount at today's rate in every report | Converting on entry loses the amount the customer agreed. Converting at today's rate makes last quarter's revenue change every day | An exchange-rate table and a daily job to run; small differences between a closed deal's INR value and today's conversion |

## Security considerations

The CRM holds personal data about customers and the company's whole sales pipeline. The two most likely real-world failures are a user seeing records they should not (broken authorization) and a stolen session. Most of this section addresses those two.

### Authorization: who sees what

**Decision: roles plus record-level scope, enforced in one place.** Every repository query for leads, contacts, companies, deals, activities and follow-ups goes through a scope filter built from the current user. Controllers never filter by hand. *Why:* scattered `if` checks are exactly where IDOR (insecure direct object reference: changing an ID in the URL to read someone else's record) bugs come from. *Trade-off:* every query carries an extra predicate, and reporting must apply the same scope in SQL.

| Capability | Rep | Manager | Admin |
| --- | --- | --- | --- |
| Read and edit leads, contacts, deals, follow-ups | Own records, plus the unassigned lead queue | Own team's records | All |
| Reassign leads and deals | No | Within own team | All |
| Import CSV, restore deleted records | No | Yes | Yes |
| Dashboard and reports | Own numbers | Team numbers | Company numbers |
| Invite users, change roles, disable users | No | No | Yes |
| Revenue targets, audit log | No | No | Yes |

**Open question:** should reps see each other's companies and contacts as read-only, so they don't call the same account twice? The table above assumes not.

### Threats and controls

| Threat | Control |
| --- | --- |
| Anyone with a Google account signs in | Invite-only: sign-in succeeds only for an invited or active user (see Data flow 1). Optional extra: accept only the company's Google Workspace domain (`hd` claim) |
| Account takeover through a reused email | Returning users are matched on the provider's `sub`, never on email. An email is used once, at first link, and only if the provider marks it verified |
| Stolen access token | 15-minute lifetime; httpOnly cookie that scripts cannot read; a per-request status check means disabling a user takes effect within 60 seconds |
| Stolen refresh token | Rotation on every use; reuse revokes the whole sign-in session and logs a security event; tokens stored as SHA-256 hashes; "Sign out of all devices" revokes everything |
| CSRF (cross-site request forgery: another site making the browser send requests with our cookies) | SameSite cookies plus a double-submit CSRF token on every state-changing request |
| CORS misuse | CORS (cross-origin resource sharing) allows exactly the frontend's origin, with credentials; no wildcards |
| XSS | The API returns JSON only. Notes are stored as plain text and the frontend escapes them. A strict Content-Security-Policy header is set on the frontend |
| SQL injection | Only parameterised queries (JPA and JdbcClient); sort fields from an allow-list; no string-built SQL |
| CSV formula injection (a cell starting `=` runs as a formula when opened in Excel) | Import and export prefix risky leading characters (`= + - @`) with a single quote; uploads limited to 5 MB and 10,000 rows |
| Abuse of public endpoints | Rate limits: sign-in 10/min per IP, refresh 30/min per user, public lead form 5/min per IP plus CAPTCHA and honeypot. Request bodies capped at 1 MB |
| Leaked secrets | No secrets in the repo or the image. Google client secret, JWT signing key and database password come from the platform's secret store as environment variables. A secret-scanning check runs in CI |
| Signing key compromise | Keys have a `kid` (key ID) so a new key can be introduced while the old one is still accepted for 15 minutes. Rotated every 90 days, immediately on suspicion; all refresh tokens revoked after an emergency rotation |
| Personal data in logs | Emails and phone numbers are masked in logs; no request or response bodies logged; tokens never logged |
| Vulnerable dependencies | Dependabot, OWASP Dependency-Check and a container image scan (Trivy) in CI; high-severity findings block the release |
| Transport | TLS (HTTPS) only, terminated at the proxy; HSTS (HTTP Strict Transport Security) header; the app port is not exposed publicly |
| Insider misuse | Every change is in the append-only audit log; admins can review it per record |

### Bootstrapping and test-only paths

- The first admin is created from configuration (`NEXUS_BOOTSTRAP_ADMIN_EMAIL`) on an empty database only. The setting is ignored once any admin exists.
- End-to-end tests use a fake sign-in provider that runs only under the `e2e` profile. The app refuses to start if that profile is active together with `prod`.

## Observability and monitoring

**Decision (product owner, 12 Nov 2026):** no metrics, tracing or dashboard stack in v1. Health checks plus log files are enough. This section accepts that decision and adds four zero-cost measures. Without them, the decision leaves the team unable to see outages or read logs after a crash.

### Concerns with the decision, and the minimum fix

| Concern | Why it matters | Minimum fix | Trade-off |
| --- | --- | --- | --- |
| A `log/` folder inside the container is deleted when the container is replaced (every deploy) or rebuilt after a crash | The logs we most need, from just before a crash, are the ones that disappear | Write logs to standard output, which the hosting platform keeps; or mount `log/` on a persistent volume with rolling files (14 days, 1 GB cap) | A volume needs a size limit and a disk-space check |
| Health checks only help if something watches them | Without an alert, users find outages before the team does | An external uptime monitor (several have free tiers) polls `/actuator/health/readiness` every minute and emails or messages the team after 2 failures. It also warns about TLS certificate expiry | One more account to manage; about 10 minutes to set up |
| Plain-text logs are slow to search during an incident | Finding one user's failed request means reading by eye | Spring Boot's built-in structured logging: one JSON line per event, with `requestId`, `userId`, path, status and duration. No extra infrastructure | Raw log lines are harder to read without `jq` (a command-line JSON tool) |
| Errors users hit go unnoticed unless reported | Bugs live longer | A daily scan of the logs for 5xx errors and security events by whoever is on duty; an error tracker (for example Sentry) is optional later | Manual effort, about 5 minutes a day |

### What is logged

| Event | Level | Fields |
| --- | --- | --- |
| Every API request (one line when it completes) | INFO | requestId, userId, method, path template, status, durationMs |
| Sign-in success, refusal and sign-out | INFO / WARN | requestId, provider, userId or a masked email, reason |
| Security events: refresh-token reuse, CSRF failure, rate limit hit | WARN | requestId, userId, IP, event type |
| Unhandled exceptions (5xx) | ERROR | requestId, userId, full stack trace |
| Scheduled jobs (reminders, purges, metrics rollup) | INFO / ERROR | job name, duration, rows affected, failure reason |
| Slow database queries (over 500 ms) | WARN | query name, duration (no parameter values) |
| Outbox events not delivered after 10 minutes | WARN | event type, age |

Never logged: tokens, request or response bodies, unmasked emails or phone numbers.

### Health checks

| Endpoint | Meaning | Used by |
| --- | --- | --- |
| `/actuator/health/liveness` | The process is running and not deadlocked | The platform restarts the container if this fails 3 times |
| `/actuator/health/readiness` | The database is reachable and migrations have finished | The proxy and the deploy step send traffic only when this passes; the uptime monitor alerts on it |

Both are reachable only from the internal network and the uptime monitor, and return no details publicly.

### Revisit this decision when

- We run more than one app instance, because logs are then split across machines.
- Users report slowness that the request log cannot explain.
- The CRM gains users outside the sales team, or a formal uptime commitment.

## Failure scenarios and recovery

The system has two moving parts, the app and the database, so most failures are one of those two being down or wrong. Each scenario lists how we find out, what users see, and how it is fixed within the 1-hour RTO and 5-minute RPO (see Capacity).

| Scenario | How we find out | What users see | Automatic response | Recovery |
| --- | --- | --- | --- | --- |
| App process crashes or hangs | Liveness fails; uptime monitor alert | Errors for up to about 1 minute | Platform restarts the container | Read the logs, which survive because they go to stdout or a volume; fix forward |
| Database unreachable | Readiness fails; uptime alert; 503 responses | "Service unavailable" banner; nothing is saved | Readiness takes the app out of rotation; connection attempts time out after 5 s instead of hanging | Managed database failover or restart; the app reconnects without a redeploy |
| Database data corrupted or deleted by mistake | User report; audit log | Wrong or missing records | None | Single records: restore from soft delete. Wider damage: point-in-time restore to a new instance, then switch the app over (about 30 to 45 minutes) |
| Bad release (bug, wrong configuration) | Errors in logs, user reports, failed post-deploy smoke test | Feature broken | Deploy stops if the new version never becomes ready, so the old one keeps serving | Redeploy the previous image (see Deployment); under 5 minutes |
| Migration fails during deploy | Deploy step fails | Nothing; the old version keeps running | Flyway runs each migration in a transaction; PostgreSQL rolls back DDL on failure | Fix the migration; never edit an applied one, always add a new one |
| Google sign-in is down | Sign-in refusals in logs; user reports | New sign-ins fail | None | Signed-in users continue for up to 14 days on refresh tokens. |
| JWT signing key leaked | Security review | None visible | None | Rotate the key, revoke all refresh tokens; everyone signs in again (Security) |
| An event listener fails (timeline entry or notification not written) | Outbox warning after 10 minutes | A missing timeline entry or notification | The outbox keeps the event; it is retried on restart and by a scheduled resubmit | Fix the listener; events are delivered once it is deployed |
| Same job runs twice during a deploy overlap | Would show as duplicate reminders | Duplicate notification | ShedLock lets only one instance run each job | None needed |
| Two users edit the same record | 409 response | "This deal changed, here is the latest" | Optimistic locking rejects the second write | User re-applies the change |
| Client retries a create after a timeout | Same `Idempotency-Key` | Nothing unusual | The stored response is returned; no duplicate record | None needed |
| Disk fills with logs | Disk-space check on the volume (if a volume is used) | App may stop writing logs | Rolling files cap at 1 GB | Raise the cap or shorten retention |
| Connection pool exhausted (for example by a slow query) | Requests wait, then time out; slow-query warnings | Slow pages, then 503 | Pool wait times out after 5 s with a clean error, so threads are not stuck forever | Find the slow query in the logs and add an index or fix it |
| Exchange-rate provider down or returning bad data | Rate job errors in logs; rates older than 7 days flagged fxStale | "Exchange rates out of date" notice on the dashboard | The last known rate (up to 7 days old) is used; the job retries hourly; a rate that moves more than 10% in a day is rejected and logged | An admin enters the missing rates by hand; closed deals already carry their own rate and are unaffected |

### Backups

- **Continuous:** the managed database's point-in-time recovery, with 7 days of history.
- **Daily:** an encrypted logical dump (`pg_dump`) copied to separate storage in another region, kept for 30 days. This protects against losing the database account itself.
- **Restore drill:** before launch, then every quarter. A backup that has never been restored is an assumption, not a backup.

## Deployment and rollback strategy

Every merge to `main` builds one container image, tagged with the git commit. The same image is promoted from staging to production. Rollback means redeploying the previous image, and the database is never rolled back by script, because every migration is written to work with both the old and the new app version.

### Environments

| Environment | Purpose | Database | Sign-in |
| --- | --- | --- | --- |
| Local | Development | PostgreSQL in Docker Compose, seeded with fake data | Google test OAuth client, or the fake provider |
| CI | Automated tests | Testcontainers PostgreSQL, created per test run | Fake provider (`e2e` profile) |
| UAT | Final checks before production, on production-like settings | Separate managed instance with anonymised or fake data | Separate Google OAuth client; UAT callback URLs |
| Production | Real use | Managed PostgreSQL with point-in-time recovery | Production Google OAuth client |

### Release pipeline (GitHub Actions)

1. A pull request runs the build, unit, module and integration tests, the OpenAPI breaking-change check, the dependency and secret scans, and a migration test from an empty database. Merging requires all of them to pass and one review.
2. A merge to `main` builds the image with buildpacks, scans it with Trivy, and pushes it tagged with the commit SHA.
3. Auto-deploy to staging: Flyway migrations run as a separate one-off step before the new app starts, then smoke tests run (health, sign-in with the fake provider, create and read a lead).
4. Production deploy by manual approval of the same image. First a fresh database snapshot, then migrations, then a zero-downtime switch: the new container starts, traffic moves only after readiness passes, and the old container drains in-flight requests for 30 seconds before stopping.
5. Post-deploy smoke test against production (health and one authenticated read). On failure, the pipeline redeploys the previous image automatically.

**Decision: migrations run as a separate step, not at app startup.** *Why:* a failing migration stops the release before any new code serves traffic, and no two app instances race to migrate. *Trade-off:* one extra pipeline step to maintain.

**Decision: expand-and-contract schema changes.** A breaking change ships in two releases. Release 1 adds the new column or table (expand) and writes to both old and new. Release 2, after release 1 is stable, removes the old one (contract). *Why:* every schema in production works with both the current and the previous app version, so rolling back the app is always safe. *Trade-off:* a rename takes two releases instead of one.

### Rollback

| Situation | Action | Time |
| --- | --- | --- |
| New version never becomes ready | Nothing; the old version is still serving | 0 |
| New version is live but broken | `deploy <previous-sha>`, the same pipeline with an older image | Under 5 minutes |
| A migration was wrong but harmless | Fix forward with a new migration | Next release |
| A migration damaged data | Point-in-time restore to a new instance from the pre-deploy snapshot, switch the app over, then reapply valid writes from the audit log if needed | 30 to 60 minutes |

**Hosting (Open question):** any platform that runs one container with health-checked, zero-downtime deploys and offers managed PostgreSQL meets this design. Examples include a container platform such as Render, Fly.io or AWS App Runner with a managed database, or one virtual machine running Docker Compose behind Caddy (a reverse proxy with automatic HTTPS). The choice depends on budget and on where the company's data must live.

## Testing strategy

Tests protect the behaviour that would hurt most if it broke: who can see what, sign-in, lead conversion, and the dashboard numbers. There is no coverage percentage target. Each layer exists to catch a specific kind of bug, and runs against a real PostgreSQL rather than an in-memory substitute.

| Layer | What it proves | Tools | Examples | Runs |
| --- | --- | --- | --- | --- |
| Unit | Business rules in isolation | JUnit 5, AssertJ | Allowed lead status transitions; won and lost rules; KPI arithmetic; CSV row parsing and formula escaping; refresh-token reuse detection | Every build, in seconds |
| Module | Module boundaries hold, and events cause the right reactions | Spring Modulith `@ApplicationModuleTest`, Modulith verification | Leads does not touch deals' internals; `DealWon` creates a timeline entry and a notification | Every build |
| Persistence | Migrations, constraints, indexes and report views work on real PostgreSQL | Testcontainers, Flyway | One open lead per email is enforced; a CHECK blocks Lost without a reason; every report view compiles after all migrations | Every build |
| API and security | HTTP contract, error format and the authorization matrix | MockMvcTester, Spring Security test support | Every endpoint × every role × own, team-mate's and other team's record gives the expected 2xx or 404; CSRF required; 409 on a stale version | Every build |
| Sign-in | The OIDC flow, including both account checks | WireMock as a fake Google (issuer, keys, token endpoint) | Uninvited user refused; a disabled user refused; a token with the wrong audience or nonce rejected | Every build |
| KPI correctness | Dashboard numbers match their written definitions | Testcontainers plus a fixed hand-calculated dataset | 12 leads and 5 deals across INR, USD and SAR, with fixed exchange rates and known stage moves, produce exactly the expected 7 KPIs and "vs last month" values | Every build |
| Contract | No accidental breaking API change | OpenAPI diff against `main`; the frontend compiles against the generated types | Removing or renaming a response field fails the pull request | Every pull request |
| End to end | Critical user journeys through the real frontend and backend | Playwright against staging, with the fake sign-in provider | Sign in, create a lead, convert it, move the deal to Won, see the dashboard change | After each staging deploy |
| Load | The 50 requests/s target with p95 under 300 ms | k6 | A 10-minute mixed read and write scenario on staging | Before launch and before major releases |
| Restore | Backups actually restore | Manual runbook | Point-in-time restore to a new instance; the app starts against it | Before launch, then quarterly |

**Decision: the authorization matrix is a generated test, not hand-written cases.** A table of (endpoint, role, record relationship, expected status) drives one parameterised test, and a new endpoint fails the build until it is added to the table. *Why:* broken authorization is the most likely serious bug in a CRM, and hand-written cases miss new endpoints. *Trade-off:* the table must be kept up to date, which is the point.

## Implementation plan

The build takes about 10 weeks for one developer, in seven phases (Assumption: one full-time developer who knows Spring, with no other duties). Each phase ends with an exit check and a deploy to staging. The frontend replaces its mock data screen by screen as each API lands, not all at the end. Implementation starts only after this document is approved (see Review and approval).

&#91;embedded content: Build timeline · 7 phases, about 10 weeks, one developer\]

Sign-in comes first because every other endpoint and every authorization test depends on it. The dashboard comes late because it reads data that phases 2 to 4 create.

| Phase | Delivers | Exit check | Frontend wiring |
| --- | --- | --- | --- |
| 0 Foundations (1 wk) | Repository and Maven build, CI pipeline, Docker Compose, Flyway baseline, error format, request ID and JSON logging, health checks, OpenAPI generation, staging environment | A request to staging returns a Problem Details error with a request ID; CI is green | Generate TypeScript types from the OpenAPI spec |
| 1 Sign-in and users (1.5 wk) | Google sign-in, JWT and refresh rotation, CSRF, invite-only users, roles, teams, `/me`, sign-out | All sign-in tests pass, including refusals; first admin bootstrapped on staging | Login page, profile menu, Team Management |
| 2 Leads, contacts, companies (2 wk) | Create, read, update and soft delete; assignment; status history; duplicate check; scope filter; audit log; search; CSV import | Authorization matrix test green for these endpoints | Leads and Contacts screens, top-bar search |
| 3 Deals, pipeline and currencies (2 wk) | Currencies and exchange rates (tables, daily job, admin override), fixed stages, deals, stage moves with optimistic locking, won and lost, stage history, lead conversion | Conversion is all-or-nothing under a forced failure; 409 on a stale version; a won USD deal keeps its INR value after the rate changes | Sales Pipeline board, convert flow |
| 4 Activities, follow-ups, notifications (1.5 wk) | Timeline, follow-ups, reminder job with ShedLock, in-app notifications, Modulith outbox | Events survive a forced listener failure and are delivered after restart | Activities, Follow-ups, bell menu |
| 5 Dashboard and reports (1 wk) | Report views, KPI queries, revenue targets, dashboard summary with caching, nightly rollup | KPI test matches the hand-calculated dataset | Dashboard and Reports switch from mock to live data |
| 6 Hardening and launch (1 wk) | Rate limits, public lead form, security headers, backup and restore drill, k6 load test, uptime monitor, production environment, data import from spreadsheets | Load test meets the targets; restore drill done; security review signed off | End-to-end tests green on staging |

## Open questions and discussion points

Thirteen questions remain. Questions 1, 2, 11 and 13 affect phases 0 and 1, so they should be answered before implementation starts. Each has a default the build will use if no answer arrives, so nothing is blocked indefinitely.

| # | Question | Why it matters | Default if unanswered | Needed by | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Does the company use Google Workspace? | If so, sign-in can also be restricted to the company domain (`hd` claim), a second lock on top of invites | Invite-only, no domain restriction | Phase 1 | Open |
| 2 | Is a native mobile app planned? | Mobile clients usually send tokens in an Authorization header, not cookies | Cookies only; adding header support later is a small change | Phase 1 | Open |
| 3 | May reps see each other's contacts and companies (read-only)? | Prevents two reps calling the same account, but widens data access | No: own records only | Phase 2 | Open |
| 4 | How should website leads be assigned? | Decides who follows up first, and how fast | Unassigned queue; managers assign | Phase 2 | Open |
| 5 | What existing data must be imported, and from where? | Shapes the CSV import and the launch plan | One-off CSV import of leads and contacts in phase 6 | Phase 2 | Open |
| 6 | Confirm the pipeline stages: Qualified, Demo, Proposal, Negotiation, Won, Lost | Stage history and reports depend on them; changing them after launch means a data migration | Those six, fixed in v1 | Phase 3 | Open |
| 7 | Which currencies are enabled at launch, beyond INR (base), USD, AUD and SAR? For example AED, EUR, GBP | Each enabled currency needs a daily exchange rate | Those four; an admin can enable any other ISO 4217 currency later without a release | Phase 3 | Open |
| 8 | Exchange rates: an automatic daily feed, or monthly rates set by finance? And which source (for example RBI reference rates)? | Decides how reported revenue in INR is calculated; finance teams often require their own fixed rates | Daily rates fetched automatically from one configured provider; an admin can override any day's rate | Phase 3 | Open |
| 9 | Conversion rate: "converted in period ÷ created in period", or "share of leads created in the period that ever converted" (cohort)? | The two give different numbers; the cohort figure keeps changing after the period ends | Converted in period ÷ created in period | Phase 5 | Open |
| 10 | Revenue targets: one company-wide monthly figure, or per team or per rep? | Changes the schema and the dashboard | One company-wide figure per month, in INR | Phase 5 | Open |
| 11 | Hosting platform, monthly budget, and any rule on where data must be stored | Needed to create the UAT environment in phase 0 | None: required before phase 0 starts | Phase 0 | Open |
| 12 | Retention: is keeping the audit log forever, and purging deleted records after 90 days, acceptable legally and for the business? | Legal and compliance requirements may differ | As written in Data lifecycle | Phase 6 | Open |
| 13 | Accept the four low-cost monitoring measures in Observability (logs to stdout or a volume, uptime monitor, JSON logs, a daily error check)? | Without them, outages and crash logs go unseen | Adopt them | Phase 0 | Open |

## Glossary

Every abbreviation and specialist term used in this document.

| Term | Meaning |
| --- | --- |
| Caffeine | A Java library that caches data in the app's own memory |
| CAPTCHA | A challenge that tells humans from bots on public forms |
| CI | Continuous integration: automatic build and tests on every change |
| citext | A PostgreSQL extension for case-insensitive text, used for emails |
| CORS | Cross-origin resource sharing: browser rules for which websites may call an API |
| CRM | Customer relationship management |
| CSRF | Cross-site request forgery: another website making a user's browser send requests with their cookies |
| DDL | Data definition language: SQL statements that change the schema, such as CREATE TABLE |
| ERP | Enterprise resource planning: finance, billing and inventory software |
| ES256 | A digital signature algorithm (ECDSA with SHA-256) used to sign our JWTs |
| Expand and contract | Making a breaking schema change in two releases, so old and new app versions both work during a deploy |
| Flyway | A tool that applies versioned SQL migration files in order |
| GDPR | General Data Protection Regulation, the EU data protection law |
| HSTS | HTTP Strict Transport Security: a header telling browsers to use HTTPS only |
| httpOnly cookie | A cookie that page JavaScript cannot read |
| Idempotency key | A unique ID sent with a request so a retry does not perform the action twice |
| IDOR | Insecure direct object reference: reading another user's record by changing an ID in the URL |
| JPA | Jakarta Persistence API, the Java standard for mapping objects to database tables (implemented by Hibernate) |
| JSONB | PostgreSQL's binary JSON column type |
| JVM | Java Virtual Machine |
| JWT | JSON Web Token: a signed token carrying claims such as the user ID and expiry |
| KPI | Key performance indicator |
| LTS | Long-term support release |
| Modular monolith | One deployable application divided into modules with enforced boundaries |
| MVC | Model-view-controller; Spring Web MVC is Spring's standard (non-reactive) web framework |
| OAuth 2.0 | The standard for letting an app act with a user's permission at another service |
| OIDC | OpenID Connect: the identity layer on top of OAuth 2.0 that says who the user is |
| Optimistic locking | Rejecting a write if the record's `version` changed since it was read |
| Outbox | Saving events in the same database transaction as the data, then delivering them, so none are lost |
| p95 | 95th percentile: 95% of requests are faster than this |
| pg\_trgm | A PostgreSQL extension for fuzzy text matching using trigrams (three-letter chunks) |
| PKCE | Proof Key for Code Exchange: protects the OAuth sign-in code from being stolen and reused |
| Problem Details | RFC 9457, the standard JSON format for HTTP API errors |
| REST | Resource-oriented HTTP API style |
| RPO | Recovery point objective: the most data we can afford to lose |
| RTO | Recovery time objective: the longest we can afford to be down |
| SameSite | A cookie setting that limits when browsers send it with requests from other sites |
| SHA-256 | A one-way hash function; we store refresh tokens only as hashes |
| ShedLock | A library that ensures a scheduled job runs on only one instance at a time |
| SSO | Single sign-on |
| sub | Subject: the provider's stable, unique ID for a user |
| TLS | Transport Layer Security, the encryption behind HTTPS |
| UUIDv7 | A universally unique identifier that sorts by creation time |
| XSS | Cross-site scripting: injecting script into a page other users view |
| Base currency | The single currency all reporting uses: INR |
| Exchange rate (FX rate) | How many INR one unit of another currency is worth on a given day |
| ISO 4217 | The international standard of three-letter currency codes, such as INR, USD, AUD and SAR |
