# Solus Clan Plugin for RuneLite

A companion plugin for the **Solus** OSRS clan. It connects to the clan's own server
(`https://api.solusosrs.com`) to share your in-game progress (drops, personal-best boss times,
collection log, combat achievements, and XP/KC) so the clan can run leaderboards, events, and a
Discord bot.

It is a clan-specific plugin: it only talks to one fixed, hardcoded endpoint, and the only thing you
enter is the API key your clan admin gives you.

## Features

- **Drops**: log valuable drops to the clan drop feed and value/points leaderboards.
- **Speed Times**: submit personal-best boss times, including automatic detection of live raid
  completions (Chambers, Theatre, and Tombs team times) for the clan speed-time boards.
- **Collection Log**: sync your collection log progress for clan clog tracking.
- **Combat Achievements**: sync your CA completions and track new ones the moment you earn them.
- **XP / KC**: clan XP and boss-KC leaderboards (read from the official OSRS hiscores).
- **Events**: browse the clan's event schedule (Skill/Boss of the Week, collection-log races,
  bingo), sign up for events, and follow a live clog-race board and draft for your team.
- **Ranks**: check your rank-up requirements (evaluated locally on your client) and request a rank.
- **Discord sharing** *(opt-in)*: post your own drops, personal bests, and deaths, with a
  screenshot, to your clan's Discord.
- **Discord linking**: link your in-game account to your Discord profile on the clan site.

## Data & Privacy

This is the important part, so it is spelled out in full.

- **Nothing is shared unless you turn it on.** Every data-sharing toggle is **off by default.**
- **Data only goes to the Solus clan's own server** (`https://api.solusosrs.com`), a fixed,
  hardcoded URL. There are no third parties, and the plugin never fetches a URL to call from
  anywhere; the endpoint is compiled into the plugin.

| Setting | What it sends | What it's used for |
|---|---|---|
| **Track Drops** | each valuable drop (item, GP value, source monster, kill count) plus your RSN and account hash | the clan drop feed and value/points leaderboards |
| **Track Speed Times** | your personal-best boss times (boss, time, team members) plus your RSN and account hash | the clan speed-time boards; your raid party is read **locally** at the start only, to credit the right team |
| **Sync Collection Log** | your collection log items and obtained/total counts (only when you open the log) plus your RSN and account hash | clan collection-log tracking and the clog leaderboard |
| **Sync Combat Achievements** | the names of your completed CA tasks plus your RSN and account hash | clan combat-achievement tracking and leaderboard. Read from the CA interface, and, in real time, from the in-game "task completed" message |
| **Track Stats** | your RSN only; the server then reads your **public** XP/KC from the official OSRS hiscores | clan XP and boss-KC leaderboards. No private game data is sent for this |
| **Discord Sharing** *(opt-in)* | a **screenshot** of your drop, personal best, or death, plus an optional caption, plus your RSN | posts to your clan's Discord via the server. Off by default; nothing is captured or sent unless you enable it |
| **Rank Requests** | the rank you request and which requirements you meet, plus your RSN | lets an admin review your rank-up. Requirements (skills, diaries, CAs, KC, item possession) are checked **locally on your client**; only the yes/no result is sent, never your bank or item list |
| **Link Code** (one-time) | the short code you paste plus your account hash and RSN | links your in-game account to your Discord on the clan website; the code is cleared from settings right after use |

Notes:

- **Account hash** is RuneLite's stable, per-account identifier (`client.getAccountHash()`). We key
  your data on it so your history follows you if you change your RSN. It is **not** a password or
  personal information.
- **In-game position / party** is read **locally only**, to figure out who is in your raid team for
  PB attribution. Your location is **never sent** to the server.
- **Bank and item data never leave your client.** Rank requirements that depend on owning an item
  are checked locally; only the pass/fail result is sent.
- The plugin writes small cache files (whitelist / hiscores / drops, for offline panel display)
  under `~/.runelite/clan-management/`.

## Setup

1. **Get your API key** from your Solus clan admin or dashboard.
2. **Install the plugin** from the RuneLite Plugin Hub (search "Solus"), or as an external plugin:
   `./gradlew build`, then copy `build/libs/drop-logger-plugin-1.0.0.jar` into
   `~/.runelite/externalPlugins/`.
3. In the plugin settings:
   - **Connection > API Key**: paste your key.
   - **Data Sharing**: turn on what you want to share (all off by default).
   - **Discord Sharing** *(optional)*: enable it to post your own drops/PBs/deaths with a screenshot.
   - **Link Code** *(optional)*: paste a code from the clan website to link your Discord.
   - **Admin > Admin API Key** *(admins only)*: paste your admin key for the admin tools.

## Plugin Tabs

| Tab | What it shows |
|---|---|
| **Home** | Connection status, announcements, active event, your clog/CA/XP summary |
| **Speed Times** | Boss PB leaderboards (pick boss and team size), Clan Only or All |
| **Drops** | Recent clan drops and drop leaderboards |
| **XP** | Clan XP leaderboard (per skill, by period) |
| **Events** | Event schedule, sign-ups, the live clog-race board for your team, and the draft |
| **Members** | Clan roster with each member's collection log, combat achievements, and stats |
| **Ranks** | Your rank-up progress and the request button |
| **Activity** | Recent clan achievements, PBs, and notable drops |
| **Admin** | Roster sync, key rotation, events, and moderation (admin key required) |

## License

BSD 2-Clause License. See [LICENSE](LICENSE).
