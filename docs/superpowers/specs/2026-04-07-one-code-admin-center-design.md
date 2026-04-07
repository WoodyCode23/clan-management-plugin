# One-Code Setup & Admin Center Design Spec

**Date:** 2026-04-07
**Goal:** Consolidate all plugin configuration into a single code, build a full admin center on the website with database editing capabilities, and package everything for self-hosted Docker deployment.

---

## 1. One-Code Bootstrap

### Encoding

The clan code is a base64-encoded string containing three pipe-delimited values:

```
base64(apiUrl|slug|apiKey)
```

Example: `https://api.solus.gg|solus|abc123def` encodes to `aHR0cHM6Ly9hcGkuc29sdXMuZ2d8c29sdXN8YWJjMTIzZGVm`

Members paste this single string into the plugin's `clanCode` config field. The plugin decodes it to extract the API URL, clan slug, and API key.

### Plugin Changes

- **New field:** `clanCode` (replaces `platformApiUrl`, `platformApiKey`, `platformClanSlug`)
- **Remove fields:** `platformApiUrl`, `platformApiKey`, `platformClanSlug`, `adminApiKey`
- **Keep (legacy):** `boardCode` — hidden in UI when `clanCode` is set, provides backwards compatibility with Google Sheets
- **Keep:** All per-user toggles (`enableClanDropLog`, `postDrops`, `postPbs`, `chatConfirmation`, `pbChatConfirmation`, `refreshInterval`, `enableClogSync`, `enablePbSync`, `enableStatTracking`)
- On startup with a valid `clanCode`, the plugin calls `GET /clans/:slug/bootstrap` to fetch shared config (Discord webhook URL, min drop value, active event state, announcements, etc.)

### API Endpoints

- `POST /clans/:slug/generate-code` — requires admin JWT auth. Returns the encoded clan code string.
- `GET /clans/:slug/bootstrap` — requires API key auth. Returns:
  ```json
  {
    "clanName": "Solus",
    "settings": { "minDropValue": 100000, ... },
    "discordWebhooks": { "drops": "https://...", "pbs": "https://..." },
    "activeEvent": { "type": "boss", "metric": "zulrah", "displayName": "Zulrah", "endTime": "..." } | null,
    "announcements": []
  }
  ```

---

## 2. Admin Authentication & Authorization

### Auth Flow

1. User logs into website via Discord OAuth (existing)
2. Admin routes use a new `requireAdmin` middleware that checks:
   - User is JWT-authenticated
   - User is either the clan owner OR has a role in `user_roles` with the required permission

### Clan Ownership

- Add `ownerUserId` column to `clans` table (UUID, references `users.id`)
- The owner always has full admin access regardless of roles
- **First-user setup:** When the first user logs in after a fresh Docker deployment, they become the owner of the clan automatically

### Permissions Model

The `roles` table already exists with:
- `name` (e.g. "Admin", "Moderator")
- `permissions` JSONB array (e.g. `["admin"]`, `["manage_drops", "manage_roster"]`)
- `discordRoleId` — optional, for auto-assignment when a Discord user with that role logs in

Available permissions:
- `admin` — full access to everything
- `manage_drops` — view/edit/delete drops
- `manage_roster` — view/edit/delete roster and player entries
- `manage_pbs` — view/edit/delete personal bests
- `manage_clog` — view/edit/delete collection log entries
- `manage_events` — start/end weekly events
- `manage_settings` — edit clan settings, webhooks, whitelist
- `manage_roles` — create/edit/delete roles and assign to users
- `generate_codes` — generate and revoke clan codes

### Role Management UI

- Admin center "Roles" section
- Create roles with name + permission checkboxes
- Assign roles to users by Discord username (user must have logged in at least once)
- Optionally link role to a Discord role ID for auto-assignment on login

---

## 3. Admin Center — Data Management UI

### Route

`/{slug}/admin` — only accessible to authenticated users with admin permissions.

### Layout

Left sidebar listing all manageable tables, grouped:

**Members:**
- Players
- Roster

**Tracking:**
- Drops
- Personal Bests
- Collection Log Entries
- Collection Log Catalog
- Stat Snapshots

**Events:**
- Weekly Events
- Achievements

**Config:**
- Clan Settings (purpose-built form, see Section 4)
- Discord Webhooks
- Whitelist
- Roles & Permissions

**Tools:**
- Generate Clan Code
- Snapshot Admin (queue stats, rate control)

### Table View

Selecting a table from the sidebar shows:
- Row count
- Search bar filtering across all text columns
- Column-specific filter dropdowns/inputs where useful (e.g. filter drops by player, PBs by boss)
- Sortable column headers (click to sort asc/desc)
- Paginated table (50 rows per page)
- "Add Row" button at top
- Checkbox column on the left for bulk selection

### Row Editing

Click a row to open a slide-out detail panel on the right:
- Each field editable with appropriate input type:
  - Text for strings
  - Number input for integers
  - Dropdown for enums (e.g. `source`: live/adventure_log/unverified)
  - Date picker for timestamps
- Foreign key fields show the human-readable value (e.g. `playerId` displays the RSN, not the UUID) with a lookup/autocomplete
- Save / Cancel / Delete buttons
- Delete requires a confirmation dialog

### Bulk Actions

- Select multiple rows via checkboxes
- Action bar appears at top: "Delete Selected (N)"
- "Select All" checkbox in table header
- Confirmation dialog before bulk delete

---

## 4. Clan Settings Panel

A purpose-built settings form within the admin center (not the raw table editor):

### General
- Clan name (editable)
- Slug (read-only)
- API URL (read-only)

### Drop Tracking
- Minimum drop value (number input)
- Whitelist items (add/remove with item name input)

### Discord Webhooks
- Add/remove webhook URLs by type: drops, PBs, events, achievements
- "Test" button per webhook that sends a sample embed

### Stat Tracking
- Snapshot cron interval (seconds)
- Rate limit per second
- Enable/disable cron toggle

### Weekly Events
- Start/end events from the website
- Same controls as plugin admin panel: type dropdown, metric picker, start/end buttons
- Shows active event card with countdown

### Clan Codes
- Generate new codes (calls `POST /clans/:slug/generate-code`)
- View active codes
- Revoke codes (invalidates the API key, requires regeneration)

### Danger Zone
- Rotate API key — warning that this invalidates all existing clan codes
- Purge data by table — select a table and clear all rows for this clan

---

## 5. Docker Compose Deployment

### Services

`docker-compose.yml` with three services:

1. **postgres** — PostgreSQL 16
   - Named volume `pgdata` for persistence
   - Health check for readiness

2. **api** — Fastify API
   - Built from `clan-platform-api/`
   - Runs DB migrations on startup
   - Depends on `postgres` health check
   - Exposes port 3001

3. **web** — Next.js frontend
   - Built from `clan-platform-web/`
   - Depends on `api`
   - Exposes port 3000

### Configuration

`.env.example` file:
```env
# Required
CLAN_NAME=Solus
CLAN_SLUG=solus
DOMAIN=solus.gg
POSTGRES_PASSWORD=changeme
DATABASE_URL=postgresql://postgres:changeme@postgres:5432/clan_platform
JWT_SECRET=<generate-random-64-char-string>
DISCORD_CLIENT_ID=<from Discord Developer Portal>
DISCORD_CLIENT_SECRET=<from Discord Developer Portal>
DISCORD_REDIRECT_URI=https://solus.gg/auth/discord/callback

# Optional
SNAPSHOT_CRON_INTERVAL=600000
SNAPSHOT_RATE=5
```

### SSL / Reverse Proxy

Not included in compose. The host provides SSL termination via their existing reverse proxy (Nginx Proxy Manager, Traefik, Caddy, etc.). Documentation covers:
- Point domain DNS at the Proxmox machine
- Reverse proxy: `solus.gg` → `localhost:3000`, `api.solus.gg` → `localhost:3001`
- Enable SSL (Let's Encrypt via the proxy manager)

### Setup Flow

1. Clone the repository
2. Copy `.env.example` to `.env`, fill in Discord app credentials and domain
3. `docker compose up -d`
4. Visit the site, log in with Discord — first user becomes clan owner
5. Set clan name in admin settings
6. Generate a clan code from admin center
7. Share the code with clan members

### Deployment Documentation

A `DEPLOY.md` at the repo root covering:
- Prerequisites (Docker, Docker Compose, domain with DNS, Discord application)
- Step-by-step setup instructions
- Discord application setup (create app, add OAuth2 redirect, get client ID/secret)
- Reverse proxy configuration examples (Nginx Proxy Manager, Caddy)
- Backup and restore (pg_dump from the postgres container)
- Updating (git pull, docker compose build, docker compose up -d)
- Troubleshooting common issues

---

## 6. Migration from Google Sheets

### Platform Takes Over

All features currently on the Google Sheet move to the platform API:
- Drop logging (already dual-writing)
- Hiscores/PBs (already on platform)
- Discord webhook URL (moves to `discord_webhooks` table)
- Admin settings (moves to `clans.settings` JSONB)
- Announcements (moves to `clans.settings`)

### Plugin Behavior

- If `clanCode` is set: use platform API exclusively for all features
- If only `boardCode` is set: use Google Sheets (legacy mode, unchanged behavior)
- If both are set: `clanCode` takes precedence, `boardCode` is ignored

### No Data Migration Tool

No automated migration from Google Sheets to the platform DB. Data rebuilds naturally:
- Adventure log sync repopulates PBs
- Drop logging starts fresh on the platform
- Collection log sync repopulates on next in-game open
- Stat snapshots build up from the cron

---

## API Endpoints Summary (New/Modified)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/clans/:slug/bootstrap` | API key | Plugin config bootstrap |
| POST | `/clans/:slug/generate-code` | Admin JWT | Generate encoded clan code |
| GET | `/admin/:slug/tables` | Admin JWT | List available tables |
| GET | `/admin/:slug/tables/:table` | Admin JWT | Paginated table data with search/filter/sort |
| GET | `/admin/:slug/tables/:table/:id` | Admin JWT | Single row detail |
| PUT | `/admin/:slug/tables/:table/:id` | Admin JWT | Update a row |
| POST | `/admin/:slug/tables/:table` | Admin JWT | Create a row |
| DELETE | `/admin/:slug/tables/:table/:id` | Admin JWT | Delete a row |
| DELETE | `/admin/:slug/tables/:table` | Admin JWT | Bulk delete (IDs in body) |
| GET | `/admin/:slug/settings` | Admin JWT | Get clan settings |
| PUT | `/admin/:slug/settings` | Admin JWT | Update clan settings |
| GET | `/admin/:slug/roles` | Admin JWT | List roles |
| POST | `/admin/:slug/roles` | Admin JWT | Create role |
| PUT | `/admin/:slug/roles/:id` | Admin JWT | Update role |
| DELETE | `/admin/:slug/roles/:id` | Admin JWT | Delete role |
| POST | `/admin/:slug/roles/:id/assign` | Admin JWT | Assign role to user |
| DELETE | `/admin/:slug/roles/:id/unassign/:userId` | Admin JWT | Remove role from user |

---

## Database Changes

- `clans` table: Add `ownerUserId` UUID column (references `users.id`, nullable initially)
- No other schema changes — all existing tables are sufficient
