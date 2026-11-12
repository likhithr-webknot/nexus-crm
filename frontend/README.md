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
lib/                 navigation, formatting helpers, mock data
```

All data currently comes from `lib/mock-data.ts`. Its shapes mirror the planned API (see `../docs/backend-technical-design.md`), so components should not need to change when the backend is connected.
