# Clan Platform — Full Design Spec

## Overview

A generic, self-hostable clan management platform for Old School RuneScape. Replaces the Google Sheets backend with a proper database-backed API, adds a website and Discord bot. The existing RuneLite plugin gains dual-mode support — Google Sheets for standard clans, platform API for clans that opt in.

**Goal:** Build a complete clan ecosystem — plugin collects data, API stores it, website displays it, Discord bot queries it — all in real-time.

**Tech Stack:**
- **API:** Fastify + TypeScript + Drizzle ORM + PostgreSQL + Socket.io
- **Website:** Next.js + TypeScript + Tailwind CSS + shadcn/ui
- **Discord Bot:** Discord.js (lives in the API repo)
- **Plugin:** Existing Java RuneLite plugin, extended with platform API support
- **Hosting:** Self-hosted (Proxmox VPS), portable to any cloud VPS

---

## System Architecture

### Repositories

| Repo | Tech | Purpose |
|------|------|---------|
| `clan-platform-api` | Fastify + Drizzle + PostgreSQL + Socket.io + Discord.js | Central API, WebSocket server, Discord bot |
| `clan-platform-web` | Next.js + Tailwind + shadcn/ui | Website frontend |
| `clan-management-plugin` | Java (existing) | RuneLite plugin — dual backend support |
| Google Apps Script (existing) | Apps Script | Standard mode backend (unchanged) |

### Data Flow

```
RuneLite Plugin ──POST──> Fastify API ──> PostgreSQL
                               |
                               |──> WebSocket push ──> Next.js website (live updates)
                               |──> Discord webhook (drop/event notifications)
                               └──> Discord bot (slash command responses)

Next.js website ──GET/POST──> Fastify API
Discord bot ──query──> PostgreSQL (direct, same process)
OSRS Hiscores API <──cron poll──> Fastify API (stat snapshots)
```

### Plugin Dual-Mode

- Config has `platformApiUrl` and `platformApiKey` fields (empty by default)
- If set: plugin sends data to the platform API
- If empty: uses Google Sheets backend as today
- Both can be active simultaneously
- No breaking changes for standard mode

---

## Database Schema

### Identity & Auth

**`clans`**
- id (uuid, PK)
- name (varchar)
- slug (varchar, unique) — URL-friendly identifier
- discord_guild_id (varchar, unique)
- settings (jsonb) — flexible clan-level config
- created_at (timestamp)

**`users`**
- id (uuid, PK)
- discord_id (varchar, unique)
- discord_username (varchar)
- avatar_url (varchar, nullable)
- created_at (timestamp)

**`players`**
- id (uuid, PK)
- user_id (uuid, FK users, nullable) — linked after verification
- clan_id (uuid, FK clans)
- rsn (varchar)
- verified (boolean, default false)
- verified_at (timestamp, nullable)
- joined_at (timestamp)
- unique(clan_id, rsn)

**`roles`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- name (varchar)
- discord_role_id (varchar, nullable) — auto-sync from Discord
- permissions (jsonb) — e.g. ["manage_events", "manage_bingo", "manage_whitelist", "manage_roles", "view_admin"]
- priority (integer) — for role hierarchy
- unique(clan_id, name)

**`user_roles`**
- user_id (uuid, FK users)
- role_id (uuid, FK roles)
- PK(user_id, role_id)

### Tracking

**`drops`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- player_id (uuid, FK players)
- item_name (varchar)
- item_id (integer, nullable)
- value (bigint)
- monster_name (varchar)
- kill_count (integer, nullable)
- screenshot_url (varchar, nullable)
- created_at (timestamp)

**`personal_bests`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- player_id (uuid, FK players)
- boss_key (varchar) — e.g. "cox_solo", "tob_4man"
- team_size (integer)
- time_ms (integer)
- created_at (timestamp)

**`collection_log_entries`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- player_id (uuid, FK players)
- item_name (varchar)
- item_id (integer, nullable)
- obtained_at (timestamp)
- unique(clan_id, player_id, item_name)

**`stat_snapshots`**
- id (uuid, PK)
- player_id (uuid, FK players)
- skills (jsonb) — all 23 skills + total level, combat level
- timestamp (timestamp)

**`achievements`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- player_id (uuid, FK players)
- type (varchar) — enum: pet, diary, quest, combat_achievement, 99, clue
- detail (varchar) — e.g. "Vorki", "Lumbridge Elite", "99 Mining"
- created_at (timestamp)

### Events

**`events`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- type (varchar) — boss, skill, gamer, clue
- metric (varchar) — WOM metric name
- display_name (varchar)
- start_time (timestamp)
- end_time (timestamp)
- status (varchar) — active, ended
- created_at (timestamp)

**`event_leaderboard`**
- id (uuid, PK)
- event_id (uuid, FK events)
- player_id (uuid, FK players)
- score (bigint)
- last_updated (timestamp)

### Bingo

**`bingo_events`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- name (varchar)
- grid_rows (integer)
- grid_cols (integer)
- start_time (timestamp)
- end_time (timestamp)
- status (varchar) — active, ended
- settings (jsonb)

**`bingo_tiles`**
- id (uuid, PK)
- bingo_event_id (uuid, FK bingo_events)
- row (integer)
- col (integer)
- name (varchar)
- code (varchar)
- points (numeric)

**`bingo_teams`**
- id (uuid, PK)
- bingo_event_id (uuid, FK bingo_events)
- name (varchar)
- code (varchar)
- color (varchar)

**`bingo_team_members`**
- id (uuid, PK)
- team_id (uuid, FK bingo_teams)
- player_id (uuid, FK players)

**`bingo_progress`**
- id (uuid, PK)
- tile_id (uuid, FK bingo_tiles)
- team_id (uuid, FK bingo_teams)
- player_id (uuid, FK players)
- completed_at (timestamp)

**`bingo_bounties`**
- id (uuid, PK)
- bingo_event_id (uuid, FK bingo_events)
- number (integer)
- description (varchar)
- points (numeric)
- release_time (timestamp)
- claimed_by (uuid, FK players, nullable)
- claimed_at (timestamp, nullable)

### Config

**`whitelist`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- item_name (varchar)
- item_id (integer, nullable)
- min_value (bigint, nullable)

**`discord_webhooks`**
- id (uuid, PK)
- clan_id (uuid, FK clans)
- type (varchar) — drops, events, bingo, achievements
- webhook_url (varchar)

---

## API Design

### Authentication

**Website users:** Discord OAuth2 -> JWT (short-lived access token + long-lived refresh token)

**Plugin:** API key per clan, sent as `Authorization: Bearer <key>` header. Admin/host actions require user JWT.

**RSN Verification:** Two methods:
1. Discord bot sends user a 6-char code via DM, user types it in clan chat, plugin detects and sends to API
2. Plugin auto-verifies when user is logged in-game with the plugin active (sends verification token)

### REST Routes

**Auth:**
```
POST   /auth/discord/callback        — OAuth2 exchange, returns JWT
POST   /auth/refresh                 — refresh token
POST   /auth/verify-rsn              — plugin sends verification token
```

**Players:**
```
GET    /clans/:slug/players          — roster with stats
GET    /clans/:slug/players/:rsn     — full profile
PATCH  /clans/:slug/players/:rsn     — admin update
```

**Drops:**
```
GET    /clans/:slug/drops            — paginated feed, filterable
POST   /clans/:slug/drops            — plugin submits drop
```

**Collection Log:**
```
GET    /clans/:slug/collection-log             — leaderboard
GET    /clans/:slug/collection-log/:rsn        — player's entries
POST   /clans/:slug/collection-log/bulk        — plugin bulk sync
POST   /clans/:slug/collection-log             — plugin submits entry
```

**Personal Bests:**
```
GET    /clans/:slug/pbs              — leaderboard by boss
GET    /clans/:slug/pbs/:boss        — rankings for specific boss
POST   /clans/:slug/pbs             — plugin submits PB
```

**Achievements:**
```
GET    /clans/:slug/achievements     — feed, filterable by type
POST   /clans/:slug/achievements     — plugin submits achievement
```

**Events:**
```
GET    /clans/:slug/events                     — current + past events
GET    /clans/:slug/events/:id/leaderboard     — event leaderboard
POST   /clans/:slug/events                     — admin creates event
PATCH  /clans/:slug/events/:id                 — admin ends/modifies event
```

**Bingo:**
```
GET    /clans/:slug/bingo                      — active bingo event + board
GET    /clans/:slug/bingo/:id/standings        — standings
POST   /clans/:slug/bingo                      — host creates event
POST   /clans/:slug/bingo/:id/progress         — plugin submits completion
PATCH  /clans/:slug/bingo/:id                  — host modifies event
```

**Admin:**
```
GET    /clans/:slug/admin/roles                — list roles
POST   /clans/:slug/admin/roles                — create custom role
PATCH  /clans/:slug/admin/roles/:id            — edit role permissions
GET    /clans/:slug/admin/whitelist             — current whitelist
PUT    /clans/:slug/admin/whitelist             — replace whitelist
GET    /clans/:slug/admin/webhooks              — webhook config
PUT    /clans/:slug/admin/webhooks              — update webhooks
```

**Internal:**
```
POST   /internal/sync-stats          — cron-triggered OSRS hiscores pull
```

### WebSocket Events (Socket.io, namespace per clan)

- `drop:new` — new drop logged
- `clog:new` — new collection log entry
- `pb:new` — new personal best
- `achievement:new` — new achievement
- `event:update` — event leaderboard changed
- `bingo:progress` — tile completed
- `bingo:bounty` — bounty released/claimed

---

## Website Pages

### Public (no login required)

- **`/`** — landing page, generic branding
- **`/:slug`** — clan home: live drop feed, active event card, quick stats
- **`/:slug/drops`** — full drop log with filters (player, item, monster, date range), infinite scroll
- **`/:slug/collection-log`** — leaderboard by unique count, click to view player's log
- **`/:slug/collection-log/:rsn`** — player's collection log, organized by category tabs
- **`/:slug/hiscores`** — PB leaderboards by boss with group/boss/size cascade
- **`/:slug/players`** — clan roster, searchable, sortable by total level/combat/join date
- **`/:slug/players/:rsn`** — player profile: stats, recent drops, PBs, clog %, achievements
- **`/:slug/events`** — current event with live leaderboard + past event history
- **`/:slug/bingo`** — live bingo board, team standings, bounty status

### Authenticated (Discord login)

- **`/:slug/settings`** — user links RSN, manages profile
- **`/:slug/admin`** — role-gated admin dashboard:
  - Events — start/end weekly events
  - Bingo — create/manage bingo events, tiles, teams, bounties
  - Whitelist — manage drop whitelist
  - Roles — create/edit custom roles, assign permissions
  - Webhooks — configure Discord webhook URLs
  - Roster — manage members, manual RSN verification

### Design

- Dark theme (OSRS aesthetic)
- Tailwind CSS + shadcn/ui components
- Real-time updates via WebSocket on drop feed and event leaderboards
- Responsive for mobile
- OSRS item icons from the OSRS Wiki API

---

## Discord Bot

### Slash Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/drops` | Recent drops, optional `@player` filter | Everyone |
| `/drops @player` | Specific player's recent drops | Everyone |
| `/pb <boss>` | Top times for a boss | Everyone |
| `/pb <boss> @player` | Player's PB for a boss | Everyone |
| `/clog @player` | Collection log count + recent entries | Everyone |
| `/clog leaderboard` | Top 10 by unique items | Everyone |
| `/stats @player` | Player profile summary | Everyone |
| `/event` | Current weekly event leaderboard | Everyone |
| `/bingo` | Current bingo standings | Everyone |
| `/leaderboard <type>` | Drops, clog, total level, boss KC | Everyone |
| `/verify` | Starts RSN verification flow | Everyone |
| `/event start <type> <metric>` | Start a weekly event | Admin |
| `/event end` | End current event | Admin |
| `/whitelist add <item>` | Add item to whitelist | Admin |
| `/whitelist remove <item>` | Remove from whitelist | Admin |

### Automatic Notifications (via webhooks)

- Valuable drops with screenshot
- New collection log entries
- New personal bests
- Achievement milestones (pets, 99s, diaries, quests)
- Weekly event start/end with results
- Bingo tile completions, bounty releases/claims

### RSN Verification Flow

1. User runs `/verify` in Discord
2. Bot DMs a random 6-character code
3. User types code in clan chat in-game
4. Plugin detects code, sends to `/auth/verify-rsn`
5. API matches code, links player to user

---

## Plugin Changes

### Dual-Mode Backend

- New config fields: `platformApiUrl` (string), `platformApiKey` (string)
- New service: `PlatformApiService` — mirrors `GoogleSheetsService` for platform API
- If platform fields are set: sends data to platform API
- Google Sheets fields remain, both can be active simultaneously
- No changes to standard mode

### New Features (platform mode only)

**Collection Log Sync:**
- "Sync Collection Log" button in plugin panel
- Confirmation dialog
- Reads in-game collection log interface tab by tab
- Bulk POST to `/collection-log/bulk`
- Progress bar: "Syncing page 3/42..."
- Ongoing detection via existing chat message listener for new entries

**Achievement Detection — chat message listeners:**
- Clue completions: `"Congratulations, you've completed a .+ clue!"`
- Achievement diaries: `"You have completed the .+ diary"`
- Pet drops: `"You have a funny feeling like you're being followed"`
- 99s: `"Congratulations, you've just advanced your .+ level. You are now level 99"`
- Combat achievements: `"You earned a new combat task:"`
- Posts to `/achievements` endpoint

**Verification Flow:**
- Detects 6-char verification code in clan chat
- Sends to `/auth/verify-rsn`

**WebSocket Connection (optional):**
- Connects to platform API for real-time config pushes
- Whitelist changes, event starts without polling

---

## Implementation Phases

### Phase 1 — Foundation (API + Database + Auth)
- Fastify project setup with TypeScript + Drizzle + PostgreSQL
- Database schema + migrations for all tables
- Discord OAuth2 flow + JWT auth
- Plugin API key auth middleware
- Core CRUD routes: drops, PBs, collection log, players, achievements
- Plugin `PlatformApiService` — dual mode working
- **Deliverable:** Plugin sends data to new API, stored in PostgreSQL

### Phase 2 — Website MVP
- Next.js project setup with Tailwind + shadcn/ui
- Clan home page with drop feed
- Drop log page with filters
- Player profiles
- PB leaderboards
- Collection log leaderboard + per-player view
- Clan roster
- Discord login
- **Deliverable:** Fully browsable clan website

### Phase 3 — Real-time + Discord Bot
- Socket.io WebSocket server on the API
- Live updates on website (drops, events, PBs)
- Discord.js bot with slash commands
- Discord webhook notifications
- RSN verification flow (bot + plugin)
- **Deliverable:** Live ecosystem — drops appear everywhere instantly

### Phase 4 — Events + Bingo on Platform
- Weekly events: create/manage via website admin + bot
- Event leaderboards (WOM integration or direct tracking)
- Bingo system migrated to platform
- Bingo board viewable on website
- **Deliverable:** Full event management on platform

### Phase 5 — Polish + Advanced Features
- Achievement detection in plugin + feed on website/bot
- Collection log bulk sync button in plugin
- OSRS Hiscores cron for stat snapshots
- Admin dashboard (roles, whitelist, webhooks)
- Custom role management
- Mobile responsive polish
- **Deliverable:** Feature complete

---

## Permissions

**Discord role-based with custom roles:**

- Discord Owner role = Host-level (all permissions)
- Discord roles sync automatically on login
- Custom roles created on website with granular permissions
- Assignable to Discord roles or individual users

**Permission keys:**
- `manage_events` — start/end weekly events
- `manage_bingo` — create/manage bingo events
- `manage_whitelist` — edit drop whitelist
- `manage_roles` — create/edit roles and permissions
- `manage_roster` — manage members, manual verification
- `manage_webhooks` — configure Discord webhooks
- `view_admin` — access admin dashboard
- `*` — all permissions (host/owner level)

---

## Screenshots & File Storage

- Screenshots stored on disk (configurable path, default `/data/screenshots/`)
- Served via Fastify static file route
- Future: S3-compatible storage if needed
- Plugin captures screenshot via `DrawManager.requestNextFrameListener()`, sends as multipart with drop submission
