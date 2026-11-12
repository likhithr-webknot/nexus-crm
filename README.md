# Nexus CRM

A CRM for one sales team: leads, contacts, companies, a deal pipeline, follow-ups and a sales dashboard.

| Path | What it is | Status |
| --- | --- | --- |
| [`frontend/`](frontend) | Next.js 16 web app (React 19, Tailwind CSS 4) | Dashboard built on mock data; other screens are placeholders |
| `backend/` | Spring Boot API (Java 25, PostgreSQL) | Not started; design under review |
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
