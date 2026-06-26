# Solus Clan Plugin for RuneLite

A companion plugin for the **Solus** OSRS clan. It connects to the clan's own server
(`https://api.solusosrs.com`) to share your in-game progress — drops, personal-best boss
times, collection log, and XP/KC — so the clan can run leaderboards, tracking, and a
Discord bot.

It is a clan-specific plugin: it only talks to one fixed, hardcoded endpoint, and the only
thing you enter is the API key your clan admin gives you.

## Features

- **Drops** — log valuable drops to the clan drop feed + value/points leaderboards
- **Speed Times** — submit personal-best boss kill times for the clan speed-time boards
- **Collection Log** — sync your collection log progress for clan clog tracking
- **XP / KC** — clan XP and boss-KC leaderboards (read from the official OSRS hiscores)
- **Activity & Events** — recent clan activity feed and weekly events
- **Discord linking** — link your in-game account to your Discord profile on the clan site

## Data & Privacy

This is the important part, so it's spelled out in full.

- **Nothing is shared unless you turn it on.** Every data-sharing toggle is **off by
  default**.
- **Data only goes to the Solus clan's own server** (`https://api.solusosrs.com`) — a fixed,
  hardcoded URL. There are no third parties, and the plugin never fetches a URL to call from
  anywhere; the endpoint is compiled into the plugin.

| Setting | What it sends | What it's used for |
|---|---|---|
| **Track Drops** | each valuable drop (item, GP value, source monster, kill count) + your RSN + account hash | the clan drop feed and value/points leaderboards |
| **Track Speed Times** | your personal-best boss times (boss, time, team members) + your RSN + account hash | the clan speed-time boards; your raid party is read **locally** at the start only, to credit the right team |
| **Sync Collection Log** | your collection log items + obtained/total counts (only when you open the log) + your RSN + account hash | clan collection-log tracking and the clog leaderboard |
| **Track Stats** | your RSN only — the server then reads your **public** XP/KC from the official OSRS hiscores | clan XP and boss-KC leaderboards. No private game data is sent for this |
| **Link Code** (one-time) | the short code you paste + your account hash + RSN | links your in-game account to your Discord on the clan website; the code is cleared from settings right after use |

Notes:

- **Account hash** is RuneLite's stable, per-account identifier (`client.getAccountHash()`).
  We key your data on it so your history follows you if you change your RSN. It is **not** a
  password or personal information.
- **In-game position / party** is read **locally only**, to figure out who is in your raid
  team for PB attribution. Your location is **never sent** to the server.
- The plugin writes small cache files (whitelist / hiscores / drops, for offline panel
  display) under `~/.runelite/clan-management/`.

## Setup

1. **Get your API key** from your Solus clan admin / dashboard.
2. **Install the plugin** — from the RuneLite Plugin Hub (search "Solus"), or as an external
   plugin: `./gradlew build` then copy `build/libs/drop-logger-plugin-1.0.0.jar` into
   `~/.runelite/externalPlugins/`.
3. In the plugin settings:
   - **Connection → API Key** — paste your key.
   - **Data Sharing** — turn on what you want to share (all off by default).
   - **Link Code** *(optional)* — paste a code from the clan website to link your Discord.
   - **Admin → Admin API Key** *(admins only)* — paste your admin key for the admin tools.

## Plugin Tabs

| Tab | What it shows |
|---|---|
| **Home** | Connection status, announcements, active event, your clog/XP summary |
| **Speed Times** | Boss PB leaderboards (pick boss / team size) |
| **Drops** | Recent clan drops + drop leaderboards |
| **XP** | Clan XP leaderboard (per skill, by period) |
| **Activity** | Recent clan achievements, PBs, and notable drops |
| **Admin** | Roster sync, key rotation, and moderation (admin key required) |

## License

BSD 2-Clause License. See [LICENSE](LICENSE).
