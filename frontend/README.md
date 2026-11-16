# Nexus CRM frontend

Next.js 16 (App Router) with React 19, Tailwind CSS 4 and lucide-react icons. Charts are hand-written SVG; there is no chart library.

## Scripts

| Command | Purpose |
| --- | --- |
| `npm run dev` | Start the dev server on http://localhost:3000 |
| `npm run build` | Production build (also type-checks) |
| `npm run lint` | ESLint |
| `npm run typecheck` | Generate Next.js route types, then run TypeScript |

Node 22 or later (pinned in `.nvmrc`). CI runs lint, type-check and build on every push to `main` and every pull request (`.github/workflows/ci.yml`).

## Structure

```
app/                 routes; app/page.tsx is the dashboard
components/shell/    sidebar, top bar, app shell
components/dashboard dashboard widgets
components/ui/       shared primitives (Card, Avatar, StageBadge, Popover)
lib/                 API clients (one file per backend area), navigation, formatting helpers
```

All data comes from the backend API through the clients in `lib/` (`leads.ts`, `deals.ts`, `contacts.ts` and so on), read with TanStack Query. `lib/api.ts` sends the session cookies and CSRF header. Start the backend first (`../backend/README.md`), then set `NEXT_PUBLIC_API_BASE_URL` as in `.env.example`. There is no mock data.
