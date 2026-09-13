# FlexCore Web — Design Specification

> Working design spec for the frontend (React + Vite + TS + Ant Design + TanStack Query).
> Language: English default, Arabic RTL toggle. Theme is token-driven — every value
> below is a variable, swappable in one file.

---

## 1. Visual Identity

**Direction (default): Dark + Orange energy.**
Gym-appropriate (energy, intensity), and it makes the demo screenshots pop in an interview.
Since Ant Design theming is 100% token-based, this is a reversible decision — changing
palette later = editing `theme.ts` + nothing else.

```
Background (page)      #0f1216   (near-black, slight cool tint)
Surface (cards)        #171b22   (raised panels)
Surface elevated       #1e242e   (modals, dropdowns, hover)
Border                 #2a3140
Text                   #e8ecf1   (primary), #9aa4b2 (secondary/muted)
Accent (primary)       #ff6b35   (energetic orange)         hover: #ff8a5c
Success                #22c55e
Danger                 #ef4444
Warning                #f59e0b
```

Rationale: orange on near-black = strong foreground contrast (WCAG AA-ish), and the accent
is used sparingly (buttons, active menu, highlights) so it stays loud, not noisy.

---

## 2. Typography & Rhythm

| Token | Value |
|---|---|
| Font family | Inter (system stack fallback) |
| Base size | 14px (antd default, kept for density) |
| Headings | 16–24px, weight 600 |
| Numbers/currency | tabular-nums (revenue, pricing align) |
| Page max width | 1440px, content gutter 24px |
| Corner radius | 8px (cards/tables), 20px (avatars/stat tiles) |
| Spacing scale | 4/8/12/16/24/32 |

---

## 3. Layout — Fixed Sider (locked decision)

```
┌─────────────────────────────────────────────────────────────┐
│  Sider (fixed, 240px)  │  Header: breadcrumb · lang EN/ع  · user menu  │
│  ───────────────────── │───────────────────────────────────────────────── │
│  LOGO / FlexCore       │                                               │
│  ─ menu per role ─     │          Content (main, scrollable)          │
│  Dashboard             │                                               │
│  Plans                 │    Cards · Tables · Forms · Modals            │
│  Billing               │            (per feature page)                 │
│  Classes · PT · Atten  │                                               │
│  ─ admin only ─        │                                               │
│  Users · Roles · Reps  │                                               │
│                        │                                               │
│  (collapsible, icons)  │                                               │
└─────────────────────────────────────────────────────────────────────────┘
```

- Sider fixed 240px, collapsible to 80px (icon-only), dark bg `#12151a`.
- Menu items **filtered by JWT `permissions`** — the guest sees only what their role allows.
- Header: page breadcrumb, `X-Trace-Id` chip (demo of the tracing header), RTL/lang toggle, avatar + logout.
- Content region max 1440px, centered, 24px padding, cards at 100% width.

---

## 4. Page archetypes (role-driven)

| Archetype | Components | Used by |
|---|---|---|
| **Dashboard** | 4 StatCards (revenue, active subs, members today, classes/PT count) + recent activity | everyone (content varies) |
| **Public catalog** | Card grid of plans with price + action buttons | `/plans` (public, hits cached endpoint) |
| **Subscriptions/billing** | Descriptions + PlanCard + "Renew/Pay" CTA → PayModal; freeze flow; family members table | MEMBER |
| **Classes** | Filterable table (date/slot/available seats) + Book button; booking → success/race-lost modal | MEMBER/TRAINER |
| **PT schedule** | Calendar-ish week view for TRAINER; my-sessions table for MEMBER | both |
| **Attendance** | QR-ish check-in widget: pick member → record | RECEPTIONIST/TRAINER |
| **Admin tables** | DataTable (paginated) + Drawer forms for user/role/permission matrix | ADMIN |
| **Reports** | Revenue-by-method bar chart + table | ADMIN |

---

## 5. Signature interactions (proof-of-work moments)

These are the details that make the portfolio read "production", not "CRUD tutorial":

1. **Auto-refresh (rotation)**
   First 401 on any API call → app calls `POST /auth/refresh` once (queuing in-flight requests)
   → retries the original call. Failed refresh → hard logout with message.
   UX: totally silent unless it fails — the interview demo: open DevTools Network, watch a
   stale call recover with no user action.
   (Implemented in the axios interceptor; TanStack Query re-drives the retried call cleanly.)

2. **Idempotent payment**
   `POST /payments/initiate` always carries `Idempotency-Key` = `crypto.randomUUID()` captured
   at modal open. Button disabled while in-flight; any network flake = same key replayed, never
   a second charge. Toast on success shows trace id.

3. **Race-lost booking**
   Backend replies `error.booking.race-lost` → visible localized toast:
   "This spot was just taken — refresh to see updated availability." (demonstrates i18n + the
   backend optimistic-lock work in one click.)

4. **Language switch**
   Toggle swaps `Accept-Language` header AND `ConfigProvider` locale + `dir="rtl"` + mirrored
   sider. Everything (incl. backend errors) flips live.

---

## 6. Data layer — TanStack Query (locked)

Single library for fetching + caching + mutations. Replaces hand-rolled `useEffect` loaders.

| Concern | TanStack Query pattern |
|---|---|
| Fetch "hydrate or refresh" | `useQuery({ queryKey: ['plans'], queryFn, staleTime: 60s })` — public plans cached, no refetch spam |
| Mutations + invalidation | `useMutation(bookClass)` → `onSuccess: invalidateQueries(['classes'], ['bookings'])`; same for pay/renew/freeze |
| Loading/error states | `isPending`, `isError` render skeletons/`ApiErrorFallback` — no loading flags by hand |
| Backend pagination | `useQuery({ queryKey: ['payments', page, size] })` powered by `PagedResponse` shape |
| Auto-refresh coupling | Query retries ride the axios interceptor — a 401-then-refresh comes back as a succeeded query |

**Rule:** no `useEffect` for server data, ever. Data access lives in per-feature hooks
(`usePlans`, `useBookClass`, `useMyPayments`, …) under `features/<x>/hooks/`, each wrapping
a typed `api.ts` call.

---

## 7. Ant Design consumption plan

| Token area | Value |
|---|---|
| `algorithm` | `theme.darkAlgorithm` |
| `colorPrimary` | `#ff6b35` |
| `colorBgBase` | `#0f1216` |
| `borderRadius` | 8 |
| `fontFamily` | Inter |
| Components used | Layout, Menu, Card, Table, Form, Modal, Drawer, Statistic, Descriptions, Tag, Toast(message), DatePicker, Segmented, Select, InputNumber, Space, Skeleton, Empty, Result |

Everything else = antd defaults. We build 3-4 custom primitives only:
`DataTable` (pagination ≡ backend `PagedResponse`), `PermissionGate`, `StatCard`,
`TraceChip`, and `ApiErrorFallback` (maps backend `ErrorResponse` code → localized message).

---

## 8. RTL/LTR switch — the pitfalls (explicit, no surprises)

Flipping `dir="rtl"` with a Fixed Sider is a classic source of sliders-and-scrollbars bugs.
Adopt all six, tested as a group:

1. **Ant Design flips direction via `ConfigProvider direction="rtl"`** — set it from the same
   source of truth as the `Accept-Language` header (a single `langCtx`), so UI and API never disagree.
2. **Sider side follows the reading side**: a CSS variable `--app-side: right` (or left); the
   `Layout.Sider` changes its `style={{ borderInlineStart/Eend }}`, and the menu's
   `theme="dark"` icons flip with `ConfigProvider` automatically. Do **not** hardcode
   `position: fixed; left: 0`.
3. **Collapse chevrons** — let antd's `Menu`, `Dropdown` and `Table.sort` arrows render via
   `ConfigProvider`; never rotate icons manually in RTL (reuse `trigger` from antd, no custom
   arrow).
4. **Overflow guard**: content container gets `min-width: 0` and `overflow-x: hidden` on the
   scrollable region; tables use `scroll={{ x: 'max-content' }}` so wide tables scroll *inside*
   the card instead of widening the page (this is the #1 horizontal-scrollbar source in RTL).
5. **Numbers & currency** must stay LTR even inside Arabic copy: wrap amounts in
   `dir="ltr"` spans with `tabular-nums`. The backend sends ISO amounts; display with `Intl.NumberFormat(locale)`.
6. **`document.documentElement.dir`** synced with the context for third-party bits (toasts/anchor),
   and `ConfigProvider` receives the matching antd locale (`arEG` / `enUS`).

Test checklist for the toggle: sider collapse icon points correctly, dropdowns open on the
correct inline edge, no horizontal scrollbar at 1280/1440 px in both directions, currency
reads LTR inside Arabic text.

---

## 9. Page inventory (build list, role → route)

| Route | Title | Roles | Backend endpoint(s) |
|---|---|---|---|
| `/` | Dashboard | all | reports + counts |
| `/login`, `/register` | Auth | public | auth/login, auth/register |
| `/plans` | Plans | public | GET /plans (cached) |
| `/billing` | My Billing | MEMBER | payments/my, subscriptions, freeze, family |
| `/classes` | Classes | MEMBER/TRAINER | classes/*, bookings |
| `/pt` | PT Sessions | MEMBER/TRAINER | pt-sessions |
| `/attendance` | Attendance | RECEPTIONIST/TRAINER | attendance |
| `/admin/users` | Users | ADMIN | users/* |
| `/admin/roles` | Roles & Permissions | ADMIN | roles/* |
| `/admin/reports` | Reports | ADMIN | reports |

---

## 10. Out of scope (v1)

- No auth-flows beyond login/register (no password reset — backend has none).
- No real-time push (polling only; backend has no SSE/WS).
- Charts minimal (reuse a simple bar from antd's Plotly-free path — `@ant-design/plots` if desired).
- Payment is mocked on the backend by design — UI presents it as the real flow.

---

## 11. File map (mirrors backend packages)

```
src/core/    api(client/refresh) · auth(context/guards) · i18n(en/ar) · theme.ts
             query(client.ts,QueryClient defaults) · components(DataTable/PermissionGate/
             StatCard/TraceChip/ApiError) · types
src/features/ auth/ dashboard/ plans/ subscriptions/ payments/ classes/ pt/ attendance/
              admin/users/ admin/roles/ admin/reports/
              └─ each: api.ts · hooks/(usePlans, useBookClass, useMyPayments, …) · pages/ · ui/
src/layouts/ AppLayout, NotFound
```

---

*Version 1.1 — 2026-09-11. Locked: dark+orange (default), Fixed Sider, EN-default+AR toggle,
TanStack Query for data access.*