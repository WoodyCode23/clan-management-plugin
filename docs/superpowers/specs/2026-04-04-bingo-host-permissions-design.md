# Bingo Host Permissions Design

## Overview

Three-tier permission system for the bingo API: Player, Admin, and Host. Bounty descriptions are hidden from players and admins until released. A protected, hidden "Host" tab stores sensitive data (host key, bounty descriptions, hint timing). The host key is also stored in Apps Script PropertiesService for belt-and-suspenders security.

## Permission Tiers

### Player (apiKey)

Read-only access to board, standings, and drops. Can auto-submit drops, push WOM KC/XP data, and persist bounty fired state.

**Allowed actions:**
- All GET endpoints (getBingoConfig, getTeamProgress, getAllStandings, getDroplog, getWhitelist, getBounties)
- `submitDrop` (POST)
- `updateTileProgress` (POST)
- `markBountyFired` (POST)

**Bounty redaction:** `getBingoConfig` and `getBounties` return `"???"` as the description for any bounty where `releaseFired` is `false`.

### Admin (adminKey)

Everything players can do, plus managing roster and adjusting progress. These are the clan admins who help run the event day-to-day.

**Additional actions:**
- `adminManualProgress` — adjust a team's tile points
- `adminUpdateRoster` — assign player to team
- `adminRemoveRoster` — remove player from roster

### Host (hostKey)

Everything admins can do, plus full control over the board structure, bounties, config, and whitelist. This is the single person who sets up and runs the bingo event.

**Additional actions (renamed from admin* to host* prefix):**
- `hostUpdateTile` — edit tile properties
- `hostAddTile` — add a new tile
- `hostRemoveTile` — remove a tile
- `hostUpdateTeam` — edit or add a team
- `hostRemoveTeam` — remove a team
- `hostUpdateBounty` — edit bounty or set winner
- `hostAddBounty` — add a new bounty
- `hostRemoveBounty` — remove a bounty
- `hostUpdateConfig` — update a config value
- `hostUpdateWhitelist` — add/remove whitelist items

## Host Tab (Google Sheet)

### Structure

A hidden, protected sheet named **"Host"** created by `setupBingoSheet()`.

**Settings section (rows 1-4):**

| Row | A (Key) | B (Value) |
|-----|---------|-----------|
| 1 | Host Email | `host@gmail.com` |
| 2 | Host Key | `generated-random-key` |
| 3 | Hint Minutes Before | `60` |

**Bounty descriptions section (row 6+):**

| Row | A (Header/Bounty #) | B (Description) |
|-----|---------------------|-----------------|
| 6 | Bounty # | Description |
| 7 | 1 | First team to get a Twisted Bow drop |
| 8 | 2 | Highest Zulrah KC in 24 hours |

### Protection

- Tab is hidden: `sheet.hideSheet()`
- Tab is protected: `sheet.protect().setDescription("Host only — do not unhide").addEditor(hostEmail)` with all other editors removed via `protection.removeEditors(protection.getEditors())`
- Only the host's Google account can edit or unhide the tab

### Host Key Storage

The host key is stored in two places:
1. **Host tab** cell B2 — visible to the host when they unhide the tab
2. **PropertiesService**: `PropertiesService.getScriptProperties().setProperty("hostKey", key)` — used by the API for authentication, not visible in any sheet tab

The API validates against PropertiesService, not the sheet cell. The sheet cell is just for the host's reference.

## Public Bounties Tab Changes

The public Bounties tab loses the Description column. New structure:

| Column | Description |
|--------|-------------|
| Number | Bounty ID (1, 2, 3...) |
| Release Time | When bounty goes live (EST, ISO format) |
| Points | Bonus points awarded to winner |
| Winner | Winning team code or player RSN (blank until awarded) |
| Hint Fired | `TRUE`/`FALSE` |
| Release Fired | `TRUE`/`FALSE` |
| Description | Blank until release — API copies from Host tab when `releaseFired` becomes true |

The Description column exists but starts empty. When the API fires a bounty release (or when `releaseFired` is set to true), it copies the description from the Host tab to this column so sheet viewers can see it after the fact.

## API Changes (BingoAPI.gs)

### Authentication Flow

```
doPost(e):
  1. Parse key from data
  2. Check key === apiKey → if not, reject
  3. If action starts with "host":
       Check data.hostKey against PropertiesService.getScriptProperties().getProperty("hostKey")
       If mismatch → reject
  4. If action starts with "admin":
       Check data.adminKey against Config tab adminKey
       If mismatch → reject
  5. Route to handler
```

### Bounty Description Redaction

`readBounties_()` accepts an optional `includeDescriptions` parameter (default `false`):

- When `false`: returns `"???"` for description on any bounty where `releaseFired` is `false`. Returns the real description (from Host tab) for released bounties.
- When `true`: returns full descriptions from Host tab for all bounties. Only used when the caller has the host key.

`getBingoConfig` and `getBounties` GET endpoints always call `readBounties_(false)` since GET requests only have the apiKey.

### Host Tab Reading

New helper `readHostBountyDescriptions_()`:
- Reads the Host tab's bounty description section (row 7+)
- Returns a map of bounty number → description

New helper `getHostConfigValue_(key)`:
- Reads settings from Host tab (rows 1-4)
- Used for `hintMinutesBefore` (moved from Config tab to Host tab)

### Bounty Release Description Copy

When `markBountyFired` is called with `field: "releaseFired"`:
1. Mark `releaseFired` as TRUE in the public Bounties tab
2. Read the description from Host tab for that bounty number
3. Write the description to the public Bounties tab's Description column

This makes the description visible in the sheet after release.

### Action Reclassification

**Renamed from `admin*` to `host*`:**
- `adminUpdateTile` → `hostUpdateTile`
- `adminAddTile` → `hostAddTile`
- `adminRemoveTile` → `hostRemoveTile`
- `adminUpdateTeam` → `hostUpdateTeam`
- `adminRemoveTeam` → `hostRemoveTeam`
- `adminUpdateBounty` → `hostUpdateBounty`
- `adminAddBounty` → `hostAddBounty`
- `adminRemoveBounty` → `hostRemoveBounty`
- `adminUpdateConfig` → `hostUpdateConfig`
- `adminUpdateWhitelist` → `hostUpdateWhitelist`

**Unchanged (stay admin-tier):**
- `adminManualProgress`
- `adminUpdateRoster`
- `adminRemoveRoster`

**Unchanged (stay player-tier):**
- `submitDrop`
- `updateTileProgress`
- `markBountyFired`

## Setup Script Changes (BingoSetup.gs)

### `setupBingoSheet()` New Prompts

After the team count prompt, add:
1. "Enter host email (Google account for Host tab protection):" — required, used for tab protection

The host key is auto-generated (random 32-char alphanumeric string) — no prompt needed.

### New: `createHostTab_(ss, hostEmail, hostKey)`

- Creates "Host" sheet
- Writes settings: host email, host key, hintMinutesBefore=60
- Writes bounty description header row
- Hides the sheet
- Protects with only the host email as editor
- Stores hostKey in PropertiesService

### Config Tab Changes

- Remove `hintMinutesBefore` row (moved to Host tab)
- `apiKey` and `adminKey` remain

### Success Message

Updated to show the generated host key:
```
Bingo sheet setup complete!

Host Key (save this — you'll need it in the plugin):
YOUR-GENERATED-KEY-HERE

Created N teams (rename them in the Teams tab).
Board tab shows your tile grid.
Whitelist has autocomplete from the Item Database.

Fill in tiles, teams, roster, whitelist, and bounties.
Then deploy as Web App and paste the URL into the plugin.
```

### `migrateSWB26()` Changes

- Creates Host tab with protection
- Moves existing bounty descriptions from public Bounties tab to Host tab
- Clears descriptions from public Bounties tab
- Generates and stores a hostKey
- Prompts for host email for tab protection

## Plugin Changes

### `ClanManagementConfig.java`

Add to Bingo section:
- `bingoAdminKey` (secret) — for roster/progress management
- `bingoHostKey` (secret) — for board/bounty/config management

### `BingoService.java`

- Add `adminKey` and `hostKey` fields with setters
- Add `doPostHost(payload)` that includes `hostKey` in the payload
- Rename methods: `adminUpdateTile()` → `hostUpdateTile()`, etc.
- Keep admin methods (`adminManualProgress`, `adminUpdateRoster`, `adminRemoveRoster`) using `adminKey`

### `AdminPanel.java`

Split bingo management into two visual sections:

**"Bingo Admin" section** (visible when `bingoAdminKey` is configured):
- Roster management (assign/remove players)
- Manual progress adjustment

**"Bingo Host" section** (visible when `bingoHostKey` is configured):
- Tile editor
- Team editor
- Bounty manager (add/edit/set winner)
- Config editor
- Whitelist manager

### `BingoPanel.java`

No changes needed. Already displays whatever description the API returns — if `"???"`, that's what players see.

### `BountyScheduler.java`

No changes needed. Reads `hintMinutesBefore` from `BingoConfig` — the API sources it from Host tab instead of Config tab, but the plugin doesn't know or care.

### `ClanManagementPlugin.java`

- Pass `bingoAdminKey` and `bingoHostKey` from config to `BingoService`
- Wire host callbacks in AdminPanel for tile/team/bounty/config/whitelist management
- Wire admin callbacks for roster/progress (already partially done)

### Hint Timing

`hintMinutesBefore` default changes from 15 to 60. Stored in Host tab, returned via `getBingoConfig`. Host can adjust it.

## Key Design Decisions

1. **PropertiesService for host key auth** — the API validates against PropertiesService, not the sheet cell. Even if someone unhides the Host tab, the API auth is in a separate store.
2. **Description copied to public tab on release** — sheet viewers see bounty descriptions after release, keeping the sheet experience fun and complete.
3. **Host tab protection via Google Sheets native protection** — only the host's Google account can edit/unhide. Not bulletproof (owner can always override) but sufficient for a clan event.
4. **Three separate keys** — allows independent revocation. Rotate the admin key without affecting players or host.
5. **Host actions renamed with `host*` prefix** — clear distinction in the API, easy to grep, matches the `admin*` pattern.

## Files Modified

- `google-apps-script/BingoSetup.gs`: new `createHostTab_()`, modified `setupBingoSheet()` and `migrateSWB26()`, public Bounties tab description column starts empty, hintMinutesBefore moved to Host tab
- `google-apps-script/BingoAPI.gs`: three-tier auth in `doPost()`, bounty description redaction in `readBounties_()`, `host*` action handlers, `readHostBountyDescriptions_()`, `getHostConfigValue_()`, description copy on release
- `src/main/java/com/droplogger/ClanManagementConfig.java`: add `bingoAdminKey`, `bingoHostKey`
- `src/main/java/com/droplogger/BingoService.java`: add host key support, rename host-tier methods
- `src/main/java/com/droplogger/AdminPanel.java`: split into Bingo Admin / Bingo Host sections
- `src/main/java/com/droplogger/ClanManagementPlugin.java`: wire new keys and host callbacks
