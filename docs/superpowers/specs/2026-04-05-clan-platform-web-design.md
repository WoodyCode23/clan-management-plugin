# Clan Platform Website — Design Spec

## Overview

A Next.js website for the clan management platform. Displays all data collected by the RuneLite plugin and stored in the Fastify API: drops, collection log, personal bests, player profiles, achievements, events, and bingo. Dark theme with OSRS-inspired gold/black aesthetic.

**Repo:** `C:/dev/clan-platform-web/` (new, separate from plugin and API)

**API dependency:** `clan-platform-api` running on `http://localhost:3001` (configurable via env var)

---

## Tech Stack

| Tool | Purpose |
|------|---------|
| Next.js 15 (App Router) | Framework, server components, route handlers |
| TypeScript | Type safety |
| Tailwind CSS v4 | Utility-first styling |
| shadcn/ui | Component library (dark theme base) |
| TanStack Query | Client-side data fetching, caching, polling |
| Cinzel (Google Font) | OSRS-style serif heading font |
| lucide-react | Icons (shadcn default) |

---

## Data Fetching Strategy

**Hybrid approach — server components for initial loads, TanStack Query for interactive sections.**

### Server-Side (RSC)

All public pages fetch data in server components using `fetch()` against the API's internal URL. This provides:
- Fast first paint
- SEO (page content in initial HTML)
- No loading spinners on first visit

Pages using server-side fetch: clan home, drops, collection log, hiscores, players, player profiles, achievements, events, bingo.

### Client-Side (TanStack Query)

Interactive sections that refresh or paginate use TanStack Query:
- Drop feed on home page — polls every 30 seconds
- Drops page — `useInfiniteQuery` for infinite scroll
- Leaderboards — refetch on filter/sort changes
- Player profile sub-sections — parallel queries
- Event leaderboard — polls while event is active

### API Configuration

- `NEXT_PUBLIC_API_URL` — public API URL for client-side fetches (e.g., `http://localhost:3001`)
- `API_URL` — internal API URL for server-side fetches (same value locally, could differ in production behind a reverse proxy)

---

## Authentication

### Discord OAuth Flow

1. User clicks "Login with Discord" on the website
2. Redirects to Discord OAuth2 authorize URL
3. Discord redirects back to `/auth/discord/callback` (Next.js route handler)
4. Route handler sends the code to `POST /auth/discord/callback` on the Fastify API
5. API returns access token + refresh token
6. Route handler sets both tokens as HTTP-only secure cookies
7. Redirects user back to the page they came from

### Session Management

- **Tokens stored in HTTP-only cookies** — not accessible to JavaScript
- **Next.js middleware** checks for valid auth cookie on protected routes (`/:slug/settings`, `/:slug/admin`)
- If no cookie or expired: redirect to Discord login
- **`/api/auth/me` route handler** — reads cookie, verifies JWT, returns user info (discord username, avatar, linked RSN, roles)
- **React auth context** — calls `/api/auth/me` on mount, provides user state to components
- **Token refresh** — if access token expired but refresh token valid, `/api/auth/me` handler calls `POST /auth/refresh` on the API, sets new cookies, returns user info

### Protected Routes

- `/:slug/settings` — requires authenticated user
- `/:slug/admin` — requires authenticated user with `view_admin` permission (or higher)

---

## Theme & Styling

### Color Palette

| Token | Value | Usage |
|-------|-------|-------|
| `--background` | `#0a0a0a` | Page background |
| `--card` | `#111111` | Card backgrounds |
| `--card-elevated` | `#1a1a1a` | Elevated surfaces, modals |
| `--primary` | `#d4a017` | Gold accent, buttons, active states |
| `--primary-hover` | `#b8860b` | Gold hover state |
| `--primary-highlight` | `#f5c842` | Bright gold for emphasis |
| `--foreground` | `#e8e0d4` | Body text (warm off-white) |
| `--foreground-muted` | `#8a8070` | Secondary text |
| `--border` | `#2a2211` | Subtle gold-tinted borders |
| `--border-active` | `#d4a017` | Active/focused borders |
| `--success` | `#22c55e` | Gains, positive values |
| `--destructive` | `#ef4444` | Deaths, errors |
| `--info` | `#3b82f6` | Informational |
| `--rare` | `#a855f7` | Rare/purple drops |

### Typography

- **Headings:** Cinzel (Google Font) — serif, medieval feel, gold color
- **Body:** Inter or system sans-serif — clean readability
- **Monospace:** For numbers, timestamps, KC values

### Component Styling

- Cards with `#111` background, subtle `#2a2211` border, gold border on hover
- Buttons: gold background with dark text for primary actions, outlined gold for secondary
- Tables: alternating row shading (`#111` / `#0f0f0f`), gold header text
- Tooltips: dark background, gold border (OSRS-style for item hovers)
- Badges: colored by type (green for skills, red for bosses, purple for rare, orange for clues)

### Layout

- Max-width container: 1200px, centered
- Sticky top navigation bar: clan name (left), page links (center), Discord login button (right)
- No sidebar — all navigation in top bar
- Card-based content sections
- Responsive: single column on mobile, 2-3 column grid on desktop

---

## Pages

### Landing — `/`

Minimal landing page. Dark background, centered content:
- Platform name + tagline
- "Find your clan" with a link to `/solus`
- Simple, clean, one screen

### Clan Home — `/:slug`

The main page. Three sections:
1. **Quick stats bar** — member count, total drops logged, total clog entries (server-side)
2. **Live drop feed** — most recent 20 drops, auto-refreshes every 30s via TanStack Query. Each entry: item icon (RuneLite CDN), item name, player RSN, monster, value, relative timestamp
3. **Active event card** — if an event is active, shows type, name, time remaining, top 5 leaderboard. If none: hidden

### Drops — `/:slug/drops`

Full drop log with filters and infinite scroll:
- **Filters bar:** player name input, item name input, monster name input (all optional, `ilike` search via API)
- **Drop list:** infinite scroll via `useInfiniteQuery`. Each row: item icon, item name, player RSN (links to profile), monster name, value (formatted with commas), timestamp
- Gold highlight row for high-value drops (above configurable threshold, default 1M)

### Collection Log — `/:slug/collection-log`

Leaderboard view:
- Ranked list of players by unique item count
- Each row: rank number, player RSN (links to profile), unique count, bar visualization of progress
- Click player row to navigate to `/:slug/collection-log/:rsn`

### Collection Log Player — `/:slug/collection-log/:rsn`

Individual player's collection log:
- Player name header with total unique count
- Grid of item icons (RuneLite CDN by item ID) with item name below each
- Sorted by date obtained (newest first)
- Empty state: "No collection log entries for {rsn}"

### Hiscores — `/:slug/hiscores`

Personal best leaderboards:
- **Boss selector** dropdown — lists all bosses that have at least one PB entry
- **Team size filter** — toggle between solo/duo/trio/etc.
- **Rankings table:** rank, player RSN (links to profile), best time (formatted as mm:ss.ms)
- Default: show first boss alphabetically

### Players — `/:slug/players`

Clan roster:
- Searchable table with columns: RSN, join date
- Search box filters by RSN (client-side filter for instant response)
- Click RSN to go to player profile

### Player Profile — `/:slug/players/:rsn`

Full player page with card sections:
- **Header:** RSN, join date, verified badge if verified
- **Recent Drops** card — last 10 drops (item icon, name, value, date)
- **Personal Bests** card — best time per boss (boss name, time, date)
- **Collection Log** card — unique count + link to full clog page
- **Recent Achievements** card — last 10 achievements (type badge, detail, date)

All sections fetched in parallel server-side via the `/players/:rsn` profile endpoint.

### Achievements — `/:slug/achievements`

Achievement feed:
- **Type filter** — buttons/tabs: All, Pet, 99, Diary, Quest, Combat Achievement, Clue
- **Feed list:** each entry shows player RSN (links to profile), type badge (colored by type), detail text, timestamp
- Sorted newest first
- Type badge colors: pet = purple, 99 = gold, diary = green, quest = blue, combat achievement = red, clue = orange

### Events — `/:slug/events`

- **Active event card** (if any): event type, name, time remaining countdown, full leaderboard table (rank, RSN, score/KC/XP gained)
- **Past events list:** collapsed cards showing type, name, date range, winner
- Empty state: "No events yet"

### Bingo — `/:slug/bingo`

- **Active bingo board** (if any): grid layout matching rows x cols, each tile shows name and completion status per team (colored dots)
- **Team standings** table: team name, color swatch, points, completed tiles count
- **Bounties** section: list of bounties with status (available/claimed), points, description
- Empty state: "No active bingo event"

### Settings — `/:slug/settings` (auth required)

- Link RSN to Discord account
- View linked RSN and verification status
- Unlink RSN

### Admin — `/:slug/admin` (auth + permission required)

Shell with tab navigation:
- **Events** tab — placeholder "Coming in Phase 4"
- **Bingo** tab — placeholder "Coming in Phase 4"
- **Whitelist** tab — placeholder "Coming in Phase 5"
- **Roles** tab — placeholder "Coming in Phase 5"
- **Webhooks** tab — placeholder "Coming in Phase 5"
- **Roster** tab — placeholder "Coming in Phase 5"

The admin shell and tab structure are built now so the route and permission gating work. Content filled in later phases.

---

## Error Handling

- **API unreachable:** "Unable to load data" card with a retry button
- **Clan not found (invalid slug):** Next.js `notFound()` — renders 404 page
- **Player not found:** 404 page with "Player not found in {clan name}"
- **Empty data:** Friendly messages per page ("No drops logged yet", "No collection log entries for {rsn}", etc.)
- **Auth expired:** Middleware redirects to Discord login, return URL preserved

---

## Item Icons

All item icons sourced from RuneLite CDN:
```
https://static.runelite.net/cache/item/icon/{itemId}.png
```

- Used on: drop feed, collection log grid, player profile drops
- Falls back to a generic placeholder icon if item ID is null or image fails to load
- Next.js `<Image>` component with `remotePatterns` configured for `static.runelite.net`

---

## File Structure

```
clan-platform-web/
  .env.local                          # API_URL, NEXT_PUBLIC_API_URL, DISCORD_CLIENT_ID, etc.
  tailwind.config.ts                  # OSRS gold/black theme tokens
  src/
    app/
      layout.tsx                      # Root layout: fonts, theme, QueryProvider, AuthProvider
      page.tsx                        # Landing page (/)
      auth/
        discord/
          callback/route.ts           # OAuth callback route handler
      api/
        auth/
          me/route.ts                 # Returns current user from cookie
          logout/route.ts             # Clears auth cookies
      [slug]/
        layout.tsx                    # Clan layout: top nav, clan context provider
        page.tsx                      # Clan home
        drops/page.tsx                # Drops feed
        collection-log/
          page.tsx                    # Leaderboard
          [rsn]/page.tsx              # Player clog
        hiscores/page.tsx             # PB leaderboards
        players/
          page.tsx                    # Roster
          [rsn]/page.tsx              # Player profile
        achievements/page.tsx         # Achievement feed
        events/page.tsx               # Events
        bingo/page.tsx                # Bingo board
        settings/page.tsx             # User settings (protected)
        admin/page.tsx                # Admin dashboard (protected)
    components/
      ui/                             # shadcn/ui components
      nav.tsx                         # Top navigation bar
      drop-feed.tsx                   # Live drop feed (TanStack Query)
      drop-row.tsx                    # Single drop entry
      item-icon.tsx                   # RuneLite CDN image with fallback
      leaderboard-table.tsx           # Reusable ranked table
      player-link.tsx                 # RSN link to profile
      event-card.tsx                  # Active event display
      bingo-board.tsx                 # Bingo grid component
      stat-bar.tsx                    # Quick stats bar
      type-badge.tsx                  # Achievement type badge
      countdown.tsx                   # Event countdown timer
    lib/
      api.ts                          # Server-side API fetch helper
      api-client.ts                   # Client-side API fetch helper (for TanStack Query)
      auth.ts                         # Cookie read/write, JWT verify helpers
      format.ts                       # Number formatting, time formatting
      constants.ts                    # RuneLite CDN URL, theme colors, etc.
    hooks/
      use-auth.ts                     # Auth context hook
    providers/
      query-provider.tsx              # TanStack QueryClientProvider
      auth-provider.tsx               # Auth context provider
    middleware.ts                      # Auth check for protected routes
```

---

## Environment Variables

```env
# API URLs
API_URL=http://localhost:3001                    # Server-side (internal)
NEXT_PUBLIC_API_URL=http://localhost:3001         # Client-side (browser)

# Discord OAuth
DISCORD_CLIENT_ID=<from discord developer portal>
DISCORD_REDIRECT_URI=http://localhost:3000/auth/discord/callback

# Auth
JWT_SECRET=<same secret as API for cookie verification>
```
