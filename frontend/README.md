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
app/                  routes: thin pages that render one view
components/<feature>/ one folder per screen (leads, pipeline, follow-ups, ...)
components/ui/        shared building blocks (Card, Dialog, FormDialog, Pagination, LoadFailed, ...)
components/shell/     sidebar, top bar and its menus
hooks/                data and behaviour used by several screens (useRefresh, useOwnerOptions, ...)
lib/                  API clients (one file per backend area), pure helpers, query keys
```

Inside a feature folder each file has one job:

| File | Job |
| --- | --- |
| `<feature>-view.tsx` | Composes the screen. Holds only what is on screen right now (filters, open dialog). |
| `use-<feature>.ts` | Reads and changes data with TanStack Query. No markup. |
| `<piece>.tsx` | One part of the UI (table, filter bar, card) that gets everything through props. |
| `<action>-dialog.tsx` | One dialog, built on `FormDialog`. |
| `lib/<topic>.ts` | Pure logic with no React (forecast maths, CSV parsing, calendar maths). Easy to test. |

Rules that keep it this way:

- A view never calls `fetch` or an API function directly; it uses a hook. The API clients in `lib/` are the only code that knows URLs.
- Query keys live in `lib/query-keys.ts`. After a change, call `useRefresh()(AFTER.lead)` (or another `AFTER` group) instead of listing keys by hand.
- To add a settings section, write its component and add one line to the list in `settings-view.tsx`.
- Before copying a block of JSX or a hook a second time, move it to `components/ui` or `hooks/`.

All data comes from the backend API through the clients in `lib/` (`leads.ts`, `deals.ts`, `contacts.ts` and so on), read with TanStack Query. `lib/api.ts` sends the session cookies and CSRF header. Start the backend first (`../backend/README.md`), then set `NEXT_PUBLIC_API_BASE_URL` as in `.env.example`. There is no mock data.
