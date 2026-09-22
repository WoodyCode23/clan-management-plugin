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
- **Bingo**: a live board for a running bingo event, showing each team's tile progress, the
  standings, team rosters you can click for a player's contributions, and recent drops.
- **Ranks**: check your rank-up requirements (evaluated locally on your client) and request a rank.
- **Discord sharing** *(opt-in)*: post your own drops, personal bests, and deaths, with a
  screenshot, to your clan's Discord.

## Data & Privacy

This is the important part, so it is spelled out in full.

- **Nothing is shared until you connect.** The plugin does nothing at all without the API key your
  clan admin gives you, and every data-sharing toggle is **off by default.**
- **Some things do sync as soon as you are connected, with no separate toggle.** Those are listed in
  the second table below, so that connecting is an informed choice rather than a surprise. If you do
  not want them sent, remove the API key.
- **Data only goes to the Solus clan's own server** (`https://api.solusosrs.com`), a fixed,
  hardcoded URL. There are no third parties, and the plugin never fetches a URL to call from
  anywhere; the endpoint is compiled into the plugin.

| Setting | What it sends | What it's used for |
|---|---|---|
| **Track Drops** | each valuable drop (item, GP value, source monster, kill count) plus your RSN and account hash | the clan drop feed and value/points leaderboards |
| **Track Speed Times** | your personal-best boss times (boss, time, team members) plus your RSN and account hash | the clan speed-time boards; your raid party is read **locally** at the start only, to credit the right team |
| **Sync Collection Log** | your collection log items and obtained/total counts (only when you open the log) plus your RSN and account hash. This toggle **also** covers the names of your purchased Slayer Reward unlocks, read when you open the Slayer Rewards shop | clan collection-log tracking and the clog leaderboard; Slayer unlocks are used for rank requirements that need them |
| **Track Stats** | your RSN only; the server then reads your **public** XP/KC from the official OSRS hiscores | clan XP and boss-KC leaderboards. No private game data is sent for this |
| **Send screenshots to Discord** *(opt-in)* | a **screenshot** of your drop, personal best, or death, plus an optional caption, plus your RSN | posts to your clan's Discord via the server. Off by default; nothing is captured or sent unless you enable it. You can also black out chat, or just private messages, before a screenshot is sent |
| **Rank Requests** | the rank you request and which requirements you meet, plus your RSN | lets an admin review your rank-up. Requirements (skills, diaries, CAs, KC, item possession) are checked **locally on your client**; only the yes/no result is sent, never your bank or item list |

### Sent once you are connected, with no separate toggle

These need no setting turned on. They start when you add your API key and stop when you remove it.

| What | When | What it sends | What it's used for |
|---|---|---|---|
| **Combat achievements** | in real time from the in-game "task completed" message. The bulk read of the CA interface additionally needs **Sync Collection Log** on | the names of your completed CA tasks, plus your RSN and account hash | clan combat-achievement tracking and leaderboard |
| **Quests and Achievement Diaries** | once per login | your quest points, how many quests you have completed out of the total, **the names of the quests you have not finished**, your diary tier completion per region, and your account type, plus your RSN and account hash | rank requirements that depend on diaries or quests, so an admin can see you qualify without asking you for screenshots |
| **Group Ironman group** | once per login, and only on a GIM account | the text visible in the in-game GIM Group side panel, which is how your group's member names are read, plus your RSN, account type and account hash | grouping GIM accounts together on the clan's team pages |

If you would rather not send the quest, diary or GIM group data, the plugin has no toggle for it
today: remove the API key, or ask your clan admin to raise it.

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
2. **Install the plugin** from the RuneLite Plugin Hub (search "Solus").
3. In the plugin settings:
   - **Connection > API Key**: paste your key.
   - **Data Sharing**: turn on what you want to share (all off by default).
   - **Screenshots > Send screenshots to Discord** *(optional)*: enable it to post your own
     drops/PBs/deaths with a screenshot, and choose whether chat is blacked out first.

Admin tools need no extra key: the Admin tab appears on its own if the Discord account your key
belongs to holds an admin role in the clan.

## Plugin Tabs

| Tab | What it shows |
|---|---|
| **Home** | Connection status, announcements, active event, your clog/CA/XP summary |
| **Leaderboards** | Drops, Speed Times, XP and Boss KC, picked from one selector |
| **Activity** | Recent clan achievements, PBs, and notable drops |
| **Members** | Clan roster with each member's collection log, combat achievements, and stats; members who recently joined the clan are marked with a leaf |
| **Ranks** | Your rank-up progress and the request button |
| **Events** | Event schedule, sign-ups, the live clog-race board for your team, the draft, and a live bingo board when a bingo event is running: tile progress, standings, a team switcher, rosters you can click for a player's contributions, and recent drops |
| **Admin** | Roster sync, key rotation, speed-times moderation, announcements, and weekly-event scheduling. Only shown if your account has an admin role in the clan |

## License

BSD 2-Clause License. See [LICENSE](LICENSE).
