# Solus Clan Plugin for RuneLite

A companion plugin for the **Solus** OSRS clan. It connects to the clan's own server
(`https://api.solusosrs.com`) to share your in-game progress (drops, personal-best boss times,
collection log, combat achievements, and XP/KC) so the clan can run leaderboards, events, and a
Discord bot.

It is a clan-specific plugin: it only talks to one fixed, hardcoded endpoint, and the only thing you
enter is the API key your clan admin gives you.

## Features

- **Drops**: log valuable drops to the clan drop feed and value/points leaderboards. A duplicate pet is credited to the boss you killed in the last minute, or else the one pet skill you were training; if neither is clear, nothing is posted.
- **Speed Times**: submit personal-best boss times, including automatic detection of live raid
  completions (Chambers, Theatre, and Tombs team times) for the clan speed-time boards.
- **Collection Log**: sync your collection log progress for clan clog tracking.
- **Combat Achievements**: sync your CA completions and track new ones the moment you earn them.
- **XP / KC**: clan XP and boss-KC leaderboards (read from the official OSRS hiscores).
- **Events**: browse the clan's event schedule (Skill/Boss of the Week, collection-log races,
  bingo), sign up for events, and follow a live clog-race board and draft for your team.
- **Bingo**: once a bingo's team draft is complete (or the bingo has started), a live board showing each team's tile progress, the
  standings, team rosters you can click for a player's contributions, and recent drops.
- **Masses**: each clan mass gets a card in the Events tab. Before it starts, press Sign up and
  the clan's Discord bot sends you a direct message with a link to the Discord event; clicking
  Interested there is your signup. While the mass runs, the card shows the boss's collection log
  with how many of each item have been obtained and who got what. After it ends, it stays under
  Past with the final numbers: kills, the top 10 killers, and drops.
- **Boss of the Week**: a boss event's card shows the KC ranking, the boss's collection log with
  how many of each item the clan got that week, and who got what and when, a page at a time. Click
  a name to see everything that player got that week.
- **Ranks**: check your rank-up requirements (evaluated locally on your client) and request a rank.
  For raid KC requirements, each Chambers of Xeric Challenge Mode, Theatre of Blood Hard Mode and
  Tombs of Amascut Expert clear counts as 1.5 normal clears. A requirement for the hard mode itself
  still counts only hard-mode clears.
- **Discord sharing** *(opt-in)*: post your own drops, personal bests, and deaths, with a
  screenshot, to your clan's Discord. Drop, pet and collection log screenshots wait a moment for
  the game's collection log popup, so it shows in the picture.

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
| **Track Speed Times** | your personal-best boss times (boss, time, team members) plus your RSN and account hash | the clan speed-time boards; your raid party is read **locally** at the start only, to credit the right team. A team time is marked verified only if every party member is on the clan's member list (guests in clan chat don't count) |
| **Sync Collection Log** | your collection log items and obtained/total counts (only when you open the log) plus your RSN and account hash. This toggle **also** covers the names of your purchased Slayer Reward unlocks, read when you open the Slayer Rewards shop | clan collection-log tracking and the clog leaderboard; Slayer unlocks are used for rank requirements that need them |
| **Track Stats** | your RSN only; the server then reads your **public** XP/KC from the official OSRS hiscores | clan XP and boss-KC leaderboards. No private game data is sent for this |
| **Send screenshots to Discord** *(opt-in)* | a **screenshot** of your drop, personal best, or death, plus an optional caption, plus your RSN | posts to your clan's Discord via the server. Off by default; nothing is captured or sent unless you enable it. You can also black out chat, or just private messages, before a screenshot is sent |
| **Send death pictures** | nothing extra: it only NARROWS the setting above | on by default, and only has any effect while screenshots are on. Turn it off to keep posting your drops and personal bests while your deaths stay private. With it off, no death screenshot is captured at all |
| **Rank Requests** | the rank you request and which requirements you meet, plus your RSN | lets an admin review your rank-up. Requirements (skills, diaries, CAs, KC, item possession) are checked **locally on your client**; only the yes/no result is sent, never your bank or item list |

### Sent once you are connected, with no separate toggle

These need no setting turned on. They start when you add your API key and stop when you remove it.

| What | When | What it sends | What it's used for |
|---|---|---|---|
| **Combat achievements** | in real time from the in-game "task completed" message. The bulk read of the CA interface additionally needs **Sync Collection Log** on | the names of your completed CA tasks, plus your RSN and account hash | clan combat-achievement tracking and leaderboard |
| **Quests and Achievement Diaries** | once per login | your quest points, how many quests you have completed out of the total, **the names of the quests you have not finished**, your diary tier completion per region, and your account type, plus your RSN and account hash | rank requirements that depend on diaries or quests, so an admin can see you qualify without asking you for screenshots |
| **Mass sign up** | only when you press Sign up on a mass card | nothing but the request (your API key identifies you) | the clan server sends you a Discord direct message with the event link; clicking Interested there is your signup |
| **Boss of the Week KC** | only during a Boss of the Week, when the game prints that boss's completion or kill count | the count from that chat message, plus your RSN | the week's KC ranking, for a boss the clan's usual KC source doesn't track yet |
| **Collection log page** | only when the clan server asks for a Boss of the Week page it doesn't have yet (for example a brand new raid), once per session | that page's name and its item IDs and names, read from the game's own data | showing that page's items on the Boss of the Week card |
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
     Deaths have their own toggle under it (**Send death pictures**, on by default) if you want
     your drops and PBs posted but not your deaths.

Admin tools need no extra key: the Admin tab appears on its own if the Discord account your key
belongs to holds an admin role in the clan.

## Plugin Tabs

| Tab | What it shows |
|---|---|
| **Home** | Connection status, announcements, active event, your clog/CA/XP summary |
| **Leaderboards** | Drops, Speed Times, XP and Boss KC, picked from one selector |
| **Activity** | Recent clan achievements, PBs, and notable drops |
| **Members** | Clan roster with each member's collection log (including raid KC per mode: Chambers/Challenge Mode, Theatre/Hard Mode, Tombs/Expert), combat achievements, and stats; members who recently joined the clan are marked with a leaf |
| **Ranks** | Your rank-up progress and the request button |
| **Events** | Event schedule, sign-ups, the live clog-race board for your team, the draft, mass cards (sign up, the live collection log, and final results), Boss of the Week cards (KC ranking, the boss's collection log, recent drops, and a per-player view), and a live bingo board once that bingo's draft is complete or it has started: tile progress, standings, a team switcher, rosters you can click for a player's contributions, and recent drops |
| **Admin** | Roster sync, key rotation, speed-times moderation, announcements, and weekly-event scheduling. Only shown if your account has an admin role in the clan |

## License

BSD 2-Clause License. See [LICENSE](LICENSE).
