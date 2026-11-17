# Nexus CRM roadmap

What we are building next, in order, and why. Each slice is finished (backend, frontend, tests) before the next one starts, so the app is shippable after every slice.

**Rules for every slice** (production readiness is part of the work, not a last pass):

1. Every endpoint checks the caller's role and record scope on the server. The frontend only hides buttons.
2. Every change writes an audit entry in the same transaction.
3. Every create that a client may retry accepts an `Idempotency-Key`.
4. Every public endpoint has a rate limit, input size limits and spam protection.
5. Lists are paged and read with a fixed number of queries (`QueryShapeTests` style).
6. Changes to a table are a new Flyway migration, safe to run on a database that already has data.
7. A failure shows a clear message in the UI, never a blank screen.

## Phase 1: Sales productivity

| Slice | What | Status |
| --- | --- | --- |
| 1.1 | **Recycle bin and restore** for leads, contacts, companies and deals. Managers and admins only. A restore that would break a rule (a duplicate email, a deleted company) is refused with the reason. | **Done** (backend, frontend, 6 tests) |
| 1.2 | **Bulk actions** on leads: assign, change status, delete, tag. One request, all or nothing, one audit entry per record. Contacts and deals get tag and delete. | **Done** (backend, frontend, tests) |
| 1.3 | **Saved views**: a named set of filters, private or shared with the team. Sort order is not saved yet: the lists sort in the browser. | **Done** (backend, frontend, tests) |
| 1.4 | **Notes and attachments** on leads, contacts and deals. Files go to object storage, never into the database; type and size are checked; downloads are scoped. | Planned |
| 1.5 | **Tags** on leads, contacts and deals, with filtering. The API also accepts companies; there is no company tag screen yet. | **Done** (backend, frontend, tests) |
| 1.6 | **Duplicate detection and merge** for contacts and companies. A merge moves every deal, activity and follow-up to the surviving record, in one transaction. | Planned |

## Phase 2: Customization

| Slice | What |
| --- | --- |
| 2.1 | **Multiple pipelines** with editable stages (name, order, win probability). Closing a deal keeps its history. |
| 2.2 | **Custom fields** (text, number, date, pick-list) on leads, contacts and deals. Stored as typed columns in a field-values table, not free-form JSON, so reports and filters stay fast. |
| 2.3 | **Field visibility by role.** |

## Phase 3: Automation

| Slice | What |
| --- | --- |
| 3.1 | **Assignment rules**: round-robin, or by source, applied to new leads. |
| 3.2 | **Workflow rules**: when something happens, do something (assign, create a follow-up, notify). Rules run in the same transaction, have a loop guard, and every run is logged. |
| 3.3 | **Stale-deal and overdue alerts.** An hourly job sends one digest per person. Thresholds are `nexus.alerts.*` settings (14 days, 24 hours); there is no settings screen for them yet. | **Done** (backend, frontend, tests) |
| 3.4 | **Email templates and Gmail delivery** through each sender's consented Google Workspace mailbox. |

### Added scope from the current product request

| Area | Current implementation | Remaining verification or expansion |
| --- | --- | --- |
| Communications | Database drafts, 30-day draft purge, Gmail OAuth and send path, admin banner with sender name embedded in the image. | Google Cloud redirect registration, sender consent and a real provider send test. |
| Campaigns | Database-backed records with role scope and audit. | Audience enrollment, delivery, scheduling and engagement reporting. |
| Automations | Published graph runs on deal creation, won deals and qualified leads; owner notifications, follow-ups, conditions, delays and run history. | More triggers and actions, plus broader operational controls. |
| Products and quotes | Shared product catalog, persisted quote lines and server-calculated totals. | Document generation and approval workflow. |
| AI | Admin-managed encrypted key, streaming CRM assistant with scoped recent records and reviewable create forms, public company and market research with provider search-source links, email drafting, workflow creation and review before applying findings. | Broader CRM actions and a signed-in browser check of streaming. |
| Recycle bin | Restore now includes archived workspace records. | Live-browser check after backend restart. |

## Phase 4: Insights

| Slice | What |
| --- | --- |
| 4.1 | **Per-rep quotas** and attainment. |
| 4.2 | **Sales forecast** by stage and month. |
| 4.3 | **Report builder** with saved reports and dashboards. Only fields from an allow-list can be chosen, and every query is scoped, so a report can never show a record the user cannot open. |

## Phase 5: Integrations

| Slice | What |
| --- | --- |
| 5.1 | **Web-to-lead**: a public endpoint with a rate limit, a honeypot field, a size limit, and an idempotency key. |
| 5.2 | **API keys** for scripts, shown once, stored hashed, revocable, with their own role and rate limit. |
| 5.3 | **Outgoing webhooks**: signed (HMAC) payloads, retries with backoff, a delivery log, and automatic pause after repeated failures. |

## Calendar and inbox sync

| Slice | What | Status |
| --- | --- | --- |
| 6.1 | **Calendar link** (Apple Calendar, Outlook, any app that reads `.ics`): a private link per user that shows their follow-ups. Stored only as a hash, replaceable, removable. One way. | **Done** (backend, frontend, tests) |
| 6.2 | **Google Calendar push**: follow-ups become 30-minute events in the user's own calendar, within a minute or two of a change. One way; an event deleted in Google stays deleted. Needs the Calendar API and the `calendar.events` scope. | **Done** against a stubbed Google; not yet run against real Google |
| 6.3 | **Inbox sync**: mail to or from a known contact or lead is copied onto that record (subject, people, Gmail's snippet). Read-only `gmail.readonly` scope, which Google treats as restricted. | **Done** against a stubbed Gmail; not yet run against real Google |

## Operations, in parallel with the phases

- Rate limiting on sign-in and refresh (the design asks for 10 per minute per address).
- Hard delete of records that have been in the recycle bin for 90 days (a nightly job).
- Metrics and a readiness check that covers the database.
- Security headers and a Content-Security-Policy for the frontend.
- Database backup and restore steps written down, and a restore test.

## Outside the current integration

Two-way calendar sync (reading changes made in Google or Apple Calendar back
into the CRM) is not built. Outgoing Gmail mail and CRM-stored drafts are in
scope. Drafts expire 30 days after creation;
other recycled records retain the separate 90-day policy above.
