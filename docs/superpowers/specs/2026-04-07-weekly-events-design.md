# Weekly Events System Design

## Overview

Four event types — Boss of the Week, Skill of the Week, Gamer of the Week, Clue Hunter of the Week — share one "active event" slot per clan. Admins start/end events from the plugin or web admin panel. Leaderboards are sourced from the Wise Old Man (WOM) API and stored in the platform DB for historical record. WOM group membership syncs automatically from the platform's clan roster.

## Event Types

| Type | Metric Source | Unit | Accent Color |
|------|--------------|------|-------------|
| `boss` | WOM boss KC gained | KC | Red `#E74C3C` |
| `skill` | WOM skill XP gained | XP | Green `#2ECC71` |
| `gamer` | WOM activity score gained | score | Purple `#9B59B6` |
| `clue` | WOM clue completions gained | completed | Orange `#F39C12` |

Metric lists already exist in `EventMetrics.java` in the plugin.

## Data Model

### Events Table (exists: `events`)

| Column | Type | Purpose |
|--------|------|---------|
| id | UUID PK | |
| clan_id | UUID FK | |
| type | varchar(20) | `boss`, `skill`, `gamer`, `clue` |
| metric | varchar(100) | WOM metric name (e.g., `general_graardor`, `mining`) |
| display_name | varchar(200) | Human name (e.g., "General Graardor", "Mining") |
| start_time | timestamptz | When the event started |
| end_time | timestamptz | When the event is scheduled to end |
| status | varchar(20) | `active` or `ended` |
| created_at | timestamptz | |

Constraint: only one `status = 'active'` event per clan at a time. Enforced in application logic (starting a new event ends the current one).

### Event Leaderboard Table (exists: `event_leaderboard`)

| Column | Type | Purpose |
|--------|------|---------|
| id | UUID PK | |
| event_id | UUID FK | References events.id |
| player_id | UUID FK | References players.id |
| score | bigint | Gained value (KC, XP, score, completions) |
| last_updated | timestamptz | Last WOM sync time |

Upserted periodically from WOM. On event end, the final scores are frozen.

### WOM Configuration (in `clans.settings` JSONB)

- `womGroupId` (number) — WOM group ID
- `womVerificationCode` (string) — WOM group verification code for membership mutations

No schema migration needed — these are JSONB fields on the existing `clans` table.

## API

### New Route File: `src/routes/events.ts`

All admin routes use `requireAdmin` middleware with `manage_events` permission.

**POST `/admin/:slug/events`** — Start an event
- Body: `{ type, metric, displayName, durationDays?: number }`
- `durationDays` defaults to 7
- If an active event exists, automatically ends it first (sets status to `"ended"`)
- Creates new event row with `status = 'active'`, `startTime = now`, `endTime = now + durationDays`
- Returns the created event

**POST `/admin/:slug/events/:id/end`** — End an event
- Sets `status = 'ended'`, `endTime = now`
- Returns the ended event

**GET `/clans/:slug/events/active`** — Get active event + leaderboard
- Auth: API key (for plugin) or public (for web)
- Returns active event object + top leaderboard entries with player RSNs
- Returns `null` if no active event

**GET `/clans/:slug/events`** — List events
- Public (no auth required)
- Returns recent events (active first, then ended, most recent first)
- Each event includes top 10 leaderboard entries

**GET `/clans/:slug/events/:id`** — Single event detail
- Public
- Returns event + full leaderboard

### Bootstrap Change

`GET /clans/:slug/bootstrap` — Change `activeEvent` to query the `events` table:
```
SELECT * FROM events WHERE clan_id = ? AND status = 'active' LIMIT 1
```
Instead of reading from `clans.settings` JSONB. Same response shape so the plugin parsing doesn't change.

### Admin Settings Change

- GET `/admin/:slug/settings` — Include `womGroupId` and `womVerificationCode` (masked) in response
- PUT `/admin/:slug/settings` — Accept `womGroupId` and `womVerificationCode` updates

## Cron Jobs

### Leaderboard Sync (every 5 minutes)

For each clan with an active event and a configured `womGroupId`:

1. Call WOM API: `GET /v2/groups/:groupId/gained?metric=:eventMetric&period=week`
2. Map WOM usernames to `players` table rows (case-insensitive RSN match)
3. Upsert into `event_leaderboard`: update score and last_updated for existing entries, insert new ones
4. Skip if WOM returns an error or rate limit

Only runs while an event is active. Does not run for ended events.

### WOM Group Membership Sync (every 30 minutes)

For each clan with `womGroupId` and `womVerificationCode` configured:

1. Read all RSNs from `clan_roster` table
2. Call WOM API: `PUT /v2/groups/:groupId` with body `{ verificationCode, members: [{ username }] }`
3. Log success/failure

This keeps WOM group membership in sync with whoever is in the clan roster on the platform. The roster itself is populated by the plugin when members open the in-game clan tab.

### Implementation

Both crons integrate into the existing `src/lib/snapshot-cron.ts` infrastructure. Add two new interval-based jobs alongside the existing stat snapshot cron.

## Web Frontend

### Events Page (`/[slug]/events`)

Replace the current placeholder with:

- **Active event section** — If an event is active: colored border card (reuse/extend `EventCard`), event name, type label, countdown timer, full leaderboard table (rank, RSN, score with unit)
- **No active event** — "No active event" message
- **Past events section** — List of ended events as cards. Each shows: type label, name, winner (rank 1), date range. Expandable to show full leaderboard.

Data fetched from `GET /clans/:slug/events` (public, no auth needed).

### Admin Settings Panel

Add a **WOM Integration** section (between Stat Tracking and Clan Codes):
- WOM Group ID — number input
- WOM Verification Code — password input (masked, clearable)
- Save button
- "Sync Roster to WOM" button — triggers immediate membership sync

Add a **Weekly Events** section (between WOM Integration and Clan Codes):
- Current event status: active event card or "No active event"
- Start Event form:
  - Event type dropdown: Boss of the Week, Skill of the Week, Gamer of the Week, Clue Hunter of the Week
  - Metric dropdown: populated based on type (same metric lists as `EventMetrics.java`)
  - Duration: number input, default 7 days
  - "Start Event" button (warns if replacing active event)
- "End Event" button with confirmation dialog
- Disabled state with message if WOM group ID is not configured

### Metric Lists for Web

The web needs the same metric name/display name mappings as `EventMetrics.java`. Create a shared `src/lib/event-metrics.ts` with the same data. This is duplicated from the plugin but necessary for the web admin to populate dropdowns.

## Plugin Changes

### `AdminService.java`

Replace `startEvent()` and `endEvent()` to call the platform API:

- `startEvent()` → `POST {platformUrl}/admin/{slug}/events` with JSON body `{ type, metric, displayName }`
  - Uses `platformApiService` for authenticated requests (API key in header)
- `endEvent()` → `POST {platformUrl}/admin/{slug}/events/{activeEventId}/end`
  - Needs the active event ID. The plugin can get this from the bootstrap response (add `id` field to `activeEvent`).

### `ClanManagementPlugin.java`

- Parse `activeEvent.id` from bootstrap response (new field, needed for end-event API call)
- Store `activeEventId` alongside existing `activeEventType`, `activeEventMetric`, etc.
- Pass event ID to the admin panel's end-event callback

### Bootstrap Response Change

Add `id` to the `activeEvent` object so the plugin can reference the event for the end endpoint:
```json
{
  "activeEvent": {
    "id": "uuid",
    "type": "boss",
    "metric": "general_graardor",
    "displayName": "General Graardor",
    "startTime": "...",
    "endTime": "..."
  }
}
```

### No Changes Needed

- `EventMetrics.java` — already complete
- `AdminPanel.java` — already has full event UI (dropdowns, buttons)
- `ClanPanel.java` — already displays event card + WOM leaderboard
- `DiscordWebhookService.java` — already posts event start/end embeds

## Dependencies

- WOM API v2 (`api.wiseoldman.net/v2`) — no auth needed for reads, verification code needed for group mutations
- WOM group must be created manually by the clan admin on wiseoldman.net first
- WOM group ID and verification code entered in admin settings

## Edge Cases

- **No WOM group configured** — Event start is disabled on web. Plugin shows "Configure WOM Group ID to use events" in admin panel.
- **WOM API down** — Leaderboard sync silently skips. Existing scores remain. Next sync picks up.
- **WOM rate limits** — The 5-minute cron interval stays well within limits. Group sync at 30 minutes is also safe.
- **Player not in `players` table** — If WOM returns a username that doesn't match any player, skip that entry. They'll appear once they're in the roster.
- **Event expires naturally** — The cron stops syncing. Web shows it as ended. Plugin hides the card (already handles this via endTime comparison).
- **Replace active event** — Starting a new event auto-ends the current one. Web confirms with a warning dialog.
