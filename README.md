# Nexus CRM

A CRM for one sales team: leads, contacts, companies, a deal pipeline, follow-ups and a sales dashboard.

| Path | What it is | Status |
| --- | --- | --- |
| [`frontend/`](frontend) | Next.js 16 web app (React 19, Tailwind CSS 4) | Mock CRM screens with multi-currency deal entry and reporting; login page |
| [`backend/`](backend) | Spring Boot API (Java 25, PostgreSQL) | Foundations and sign-in (Google, sessions, roles, users); business modules not started |
| [`docs/`](docs) | Design documents | [Backend technical design](docs/backend-technical-design.md) (v0.2, draft) |

## Getting started

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:3000.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on every push to `main` and every pull request: install, lint, type-check and production build of the frontend.

## Conventions

- Secrets never go in the repo. Use `.env.local` (ignored) and commit a `.env.example` when variables are introduced.
- Mock data uses reserved `.example` domains, never real companies or people.

## Currency support

Deal and lead amounts retain their selected ISO 4217 currency. Workspace reporting defaults to INR and can be changed in Settings; totals use daily indicative rates from ExchangeRate-API and are hidden if a required rate is unavailable. The mock frontend stores this preference in the browser. Auditable daily rates and close-date snapshots depend on the planned CRM backend.
