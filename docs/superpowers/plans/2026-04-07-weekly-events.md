# Weekly Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build weekly clan events (Boss/Skill/Gamer/Clue of the Week) with WOM-sourced leaderboards, automatic WOM group sync, and historical event data across API, web, and RuneLite plugin.

**Architecture:** Events are stored in the `events` table (already exists). A cron fetches leaderboard data from the WOM API and stores it in `event_leaderboard`. A separate cron syncs clan roster to WOM group membership. The web shows active/past events with leaderboards. The plugin's existing event UI is rewired from Google Sheets to the platform API.

**Tech Stack:** TypeScript/Fastify/Drizzle (API), TypeScript/Next.js/React Query (web), Java/RuneLite (plugin), WOM API v2

---

## File Structure

### API (`C:\dev\clan-platform-api`)

| File | Action | Responsibility |
|------|--------|---------------|
| `src/routes/events.ts` | Create | Event CRUD (start, end, list, detail) + leaderboard endpoints |
| `src/lib/wom-service.ts` | Create | WOM API client (group gained, group membership sync) |
| `src/lib/event-cron.ts` | Create | Leaderboard sync cron (every 5 min) + WOM group sync cron (every 30 min) |
| `src/routes/bootstrap.ts` | Modify | Read active event from `events` table instead of `clans.settings` |
| `src/routes/admin-settings.ts` | Modify | Add WOM group ID + verification code to settings GET/PUT |
| `src/app.ts` | Modify | Register events routes, start event crons |

### Web (`C:\dev\clan-platform-web`)

| File | Action | Responsibility |
|------|--------|---------------|
| `src/lib/event-metrics.ts` | Create | Metric name/display name mappings (mirrors EventMetrics.java) |
| `src/app/[slug]/events/page.tsx` | Modify | Active event + leaderboard + past events list |
| `src/components/event-card.tsx` | Modify | Add leaderboard table and past-event variant |
| `src/app/[slug]/admin/settings-tab.tsx` | Modify | Add WOM Integration + Weekly Events sections |

### Plugin (`C:\dev\OSRS_Bingo\drop-logger-plugin`)

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/java/com/droplogger/AdminService.java` | Modify | Rewire startEvent/endEvent to platform API |
| `src/main/java/com/droplogger/ClanManagementPlugin.java` | Modify | Parse event ID from bootstrap, pass to admin callbacks |

---

### Task 1: WOM Service

**Files:**
- Create: `C:\dev\clan-platform-api\src\lib\wom-service.ts`

A thin client for the WOM API v2 endpoints we need.

- [ ] **Step 1: Create wom-service.ts**

Create `C:\dev\clan-platform-api\src\lib\wom-service.ts`:

```typescript
const WOM_BASE = "https://api.wiseoldman.net/v2";

export interface WomGainedEntry {
  player: { displayName: string };
  data: { gained: number };
}

interface WomGainedResponse {
  data: WomGainedEntry[];
}

/**
 * Fetch gained scores for a group metric over a period.
 * GET /v2/groups/:id/gained?metric=:metric&period=:period
 */
export async function fetchGroupGained(
  groupId: number,
  metric: string,
  period: string = "week",
): Promise<WomGainedEntry[]> {
  const url = `${WOM_BASE}/groups/${groupId}/gained?metric=${encodeURIComponent(metric)}&period=${encodeURIComponent(period)}&limit=50`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`WOM API error: ${res.status} ${res.statusText}`);
  }
  const body = (await res.json()) as WomGainedEntry[];
  return body;
}

interface WomMember {
  username: string;
  role?: string;
}

/**
 * Sync group membership on WOM.
 * PUT /v2/groups/:id
 */
export async function syncGroupMembers(
  groupId: number,
  verificationCode: string,
  members: { username: string }[],
): Promise<void> {
  const url = `${WOM_BASE}/groups/${groupId}`;
  const res = await fetch(url, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ verificationCode, members }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`WOM group sync error: ${res.status} — ${text}`);
  }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd C:\dev\clan-platform-api && npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:\dev\clan-platform-api
git add src/lib/wom-service.ts
git commit -m "feat: add WOM API client for group gained and membership sync"
```

---

### Task 2: Event API Routes

**Files:**
- Create: `C:\dev\clan-platform-api\src\routes\events.ts`
- Modify: `C:\dev\clan-platform-api\src\app.ts`

- [ ] **Step 1: Create events.ts**

Create `C:\dev\clan-platform-api\src\routes\events.ts`:

```typescript
import { FastifyInstance } from "fastify";
import { z } from "zod";
import { eq, and, desc } from "drizzle-orm";
import { db } from "../db/index.js";
import { events, eventLeaderboard, players } from "../db/schema/index.js";
import { requireAdmin } from "../auth/middleware.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

export async function eventsRoutes(app: FastifyInstance) {
  // POST /admin/:slug/events — start a new event
  app.post<{ Params: { slug: string } }>(
    "/admin/:slug/events",
    { preHandler: [requireAdmin("manage_events")] },
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const schema = z.object({
        type: z.enum(["boss", "skill", "gamer", "clue"]),
        metric: z.string().min(1),
        displayName: z.string().min(1),
        durationDays: z.number().min(1).max(30).default(7),
      });
      const parsed = schema.safeParse(request.body);
      if (!parsed.success) return reply.status(400).send({ error: "Invalid payload", details: parsed.error.flatten() });

      // End any currently active event for this clan
      await db.update(events)
        .set({ status: "ended", endTime: new Date() })
        .where(and(eq(events.clanId, clan.id), eq(events.status, "active")));

      const now = new Date();
      const endTime = new Date(now.getTime() + parsed.data.durationDays * 24 * 60 * 60 * 1000);

      const [event] = await db.insert(events).values({
        clanId: clan.id,
        type: parsed.data.type,
        metric: parsed.data.metric,
        displayName: parsed.data.displayName,
        startTime: now,
        endTime,
        status: "active",
      }).returning();

      return reply.status(201).send(event);
    },
  );

  // POST /admin/:slug/events/:id/end — end an event
  app.post<{ Params: { slug: string; id: string } }>(
    "/admin/:slug/events/:id/end",
    { preHandler: [requireAdmin("manage_events")] },
    async (request, reply) => {
      const [event] = await db.update(events)
        .set({ status: "ended", endTime: new Date() })
        .where(and(eq(events.id, request.params.id), eq(events.status, "active")))
        .returning();

      if (!event) return reply.status(404).send({ error: "Active event not found" });
      return event;
    },
  );

  // GET /clans/:slug/events/active — get active event + leaderboard (public)
  app.get<{ Params: { slug: string } }>(
    "/clans/:slug/events/active",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const [event] = await db.select()
        .from(events)
        .where(and(eq(events.clanId, clan.id), eq(events.status, "active")))
        .limit(1);

      if (!event) return { event: null, leaderboard: [] };

      const leaderboard = await db.select({
        id: eventLeaderboard.id,
        score: eventLeaderboard.score,
        lastUpdated: eventLeaderboard.lastUpdated,
        rsn: players.rsn,
      })
        .from(eventLeaderboard)
        .innerJoin(players, eq(eventLeaderboard.playerId, players.id))
        .where(eq(eventLeaderboard.eventId, event.id))
        .orderBy(desc(eventLeaderboard.score))
        .limit(50);

      return { event, leaderboard };
    },
  );

  // GET /clans/:slug/events — list recent events (public)
  app.get<{ Params: { slug: string }; Querystring: { limit?: string } }>(
    "/clans/:slug/events",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const limit = Math.min(50, parseInt(request.query.limit ?? "20", 10) || 20);

      const eventRows = await db.select()
        .from(events)
        .where(eq(events.clanId, clan.id))
        .orderBy(desc(events.createdAt))
        .limit(limit);

      // For each event, fetch top 10 leaderboard entries
      const result = await Promise.all(
        eventRows.map(async (event) => {
          const leaderboard = await db.select({
            id: eventLeaderboard.id,
            score: eventLeaderboard.score,
            lastUpdated: eventLeaderboard.lastUpdated,
            rsn: players.rsn,
          })
            .from(eventLeaderboard)
            .innerJoin(players, eq(eventLeaderboard.playerId, players.id))
            .where(eq(eventLeaderboard.eventId, event.id))
            .orderBy(desc(eventLeaderboard.score))
            .limit(10);

          return { ...event, leaderboard };
        }),
      );

      return { events: result };
    },
  );

  // GET /clans/:slug/events/:id — single event detail (public)
  app.get<{ Params: { slug: string; id: string } }>(
    "/clans/:slug/events/:id",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const [event] = await db.select()
        .from(events)
        .where(and(eq(events.id, request.params.id), eq(events.clanId, clan.id)))
        .limit(1);

      if (!event) return reply.status(404).send({ error: "Event not found" });

      const leaderboard = await db.select({
        id: eventLeaderboard.id,
        score: eventLeaderboard.score,
        lastUpdated: eventLeaderboard.lastUpdated,
        rsn: players.rsn,
      })
        .from(eventLeaderboard)
        .innerJoin(players, eq(eventLeaderboard.playerId, players.id))
        .where(eq(eventLeaderboard.eventId, event.id))
        .orderBy(desc(eventLeaderboard.score));

      return { ...event, leaderboard };
    },
  );
}
```

- [ ] **Step 2: Register in app.ts**

In `C:\dev\clan-platform-api\src\app.ts`, add:

```typescript
import { eventsRoutes } from "./routes/events.js";
```

And in `buildApp()`, add after `await app.register(adminSettingsRoutes);`:

```typescript
await app.register(eventsRoutes);
```

- [ ] **Step 3: Build to verify**

```bash
cd C:\dev\clan-platform-api && npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:\dev\clan-platform-api
git add src/routes/events.ts src/app.ts
git commit -m "feat: add event CRUD and leaderboard API routes"
```

---

### Task 3: Event Cron + WOM Group Sync

**Files:**
- Create: `C:\dev\clan-platform-api\src\lib\event-cron.ts`
- Modify: `C:\dev\clan-platform-api\src\app.ts`

- [ ] **Step 1: Create event-cron.ts**

Create `C:\dev\clan-platform-api\src\lib\event-cron.ts`:

```typescript
import { eq, and } from "drizzle-orm";
import { db } from "../db/index.js";
import { clans, events, eventLeaderboard, players, clanRoster } from "../db/schema/index.js";
import { fetchGroupGained, syncGroupMembers } from "./wom-service.js";

let leaderboardTimer: ReturnType<typeof setInterval> | null = null;
let groupSyncTimer: ReturnType<typeof setInterval> | null = null;

const LEADERBOARD_INTERVAL = 5 * 60 * 1000;  // 5 minutes
const GROUP_SYNC_INTERVAL = 30 * 60 * 1000;   // 30 minutes

export function startEventCrons() {
  if (!leaderboardTimer) {
    console.log("Event leaderboard cron starting — interval 5m");
    leaderboardTimer = setInterval(runLeaderboardSync, LEADERBOARD_INTERVAL);
  }
  if (!groupSyncTimer) {
    console.log("WOM group sync cron starting — interval 30m");
    groupSyncTimer = setInterval(runGroupSync, GROUP_SYNC_INTERVAL);
  }
}

export function stopEventCrons() {
  if (leaderboardTimer) {
    clearInterval(leaderboardTimer);
    leaderboardTimer = null;
  }
  if (groupSyncTimer) {
    clearInterval(groupSyncTimer);
    groupSyncTimer = null;
  }
}

/**
 * For each clan with an active event and a WOM group configured,
 * fetch gained data from WOM and upsert into event_leaderboard.
 */
async function runLeaderboardSync() {
  try {
    const allClans = await db.select().from(clans);

    for (const clan of allClans) {
      const settings = (clan.settings ?? {}) as Record<string, unknown>;
      const womGroupId = settings.womGroupId as number | undefined;
      if (!womGroupId) continue;

      // Find active event
      const [activeEvent] = await db.select()
        .from(events)
        .where(and(eq(events.clanId, clan.id), eq(events.status, "active")))
        .limit(1);

      if (!activeEvent) continue;

      // Check if event has expired naturally
      if (activeEvent.endTime && new Date() > activeEvent.endTime) {
        await db.update(events)
          .set({ status: "ended" })
          .where(eq(events.id, activeEvent.id));
        console.log(`Event ${activeEvent.id} expired naturally — marked as ended`);
        continue;
      }

      try {
        const gained = await fetchGroupGained(womGroupId, activeEvent.metric, "week");

        // Load all clan players once for RSN matching
        const clanPlayers = await db.select()
          .from(players)
          .where(eq(players.clanId, clan.id));

        for (const entry of gained) {
          const rsn = entry.player.displayName;
          const score = entry.data.gained;
          if (score <= 0) continue;

          const matchedPlayer = clanPlayers.find(
            (p) => p.rsn.toLowerCase() === rsn.toLowerCase(),
          );

          if (!matchedPlayer) continue;

          // Upsert leaderboard entry
          const [existing] = await db.select()
            .from(eventLeaderboard)
            .where(and(
              eq(eventLeaderboard.eventId, activeEvent.id),
              eq(eventLeaderboard.playerId, matchedPlayer.id),
            ))
            .limit(1);

          if (existing) {
            await db.update(eventLeaderboard)
              .set({ score, lastUpdated: new Date() })
              .where(eq(eventLeaderboard.id, existing.id));
          } else {
            await db.insert(eventLeaderboard).values({
              eventId: activeEvent.id,
              playerId: matchedPlayer.id,
              score,
              lastUpdated: new Date(),
            });
          }
        }

        console.log(`Leaderboard synced for event ${activeEvent.id} (${activeEvent.displayName})`);
      } catch (err) {
        console.error(`Failed to sync leaderboard for clan ${clan.slug}:`, err);
      }
    }
  } catch (err) {
    console.error("Leaderboard cron error:", err);
  }
}

/**
 * For each clan with WOM credentials configured,
 * sync the clan_roster to the WOM group membership.
 */
async function runGroupSync() {
  try {
    const allClans = await db.select().from(clans);

    for (const clan of allClans) {
      const settings = (clan.settings ?? {}) as Record<string, unknown>;
      const womGroupId = settings.womGroupId as number | undefined;
      const womVerificationCode = settings.womVerificationCode as string | undefined;
      if (!womGroupId || !womVerificationCode) continue;

      try {
        const roster = await db.select({ rsn: clanRoster.rsn })
          .from(clanRoster)
          .where(eq(clanRoster.clanId, clan.id));

        if (roster.length === 0) continue;

        const members = roster.map((r) => ({ username: r.rsn }));
        await syncGroupMembers(womGroupId, womVerificationCode, members);
        console.log(`WOM group ${womGroupId} synced with ${members.length} members for clan ${clan.slug}`);
      } catch (err) {
        console.error(`Failed to sync WOM group for clan ${clan.slug}:`, err);
      }
    }
  } catch (err) {
    console.error("WOM group sync cron error:", err);
  }
}
```

- [ ] **Step 2: Start crons in app.ts**

In `C:\dev\clan-platform-api\src\app.ts`, add import:

```typescript
import { startEventCrons } from "./lib/event-cron.js";
```

And after `startCron();` in `buildApp()`, add:

```typescript
startEventCrons();
```

- [ ] **Step 3: Build to verify**

```bash
cd C:\dev\clan-platform-api && npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:\dev\clan-platform-api
git add src/lib/event-cron.ts src/app.ts
git commit -m "feat: add event leaderboard cron and WOM group sync cron"
```

---

### Task 4: Bootstrap + Admin Settings Changes

**Files:**
- Modify: `C:\dev\clan-platform-api\src\routes\bootstrap.ts`
- Modify: `C:\dev\clan-platform-api\src\routes\admin-settings.ts`

- [ ] **Step 1: Update bootstrap to read from events table**

In `C:\dev\clan-platform-api\src\routes\bootstrap.ts`:

Add import at top:
```typescript
import { events } from "../db/schema/index.js";
import { and, eq } from "drizzle-orm";
```

Replace the `activeEvent` section in the return (lines 35-41) from:
```typescript
activeEvent: settings.eventType ? {
  type: settings.eventType,
  metric: settings.eventMetric,
  displayName: settings.eventDisplayName,
  startTime: settings.eventStartTime,
  endTime: settings.eventEndTime,
} : null,
```

To:
```typescript
activeEvent: await (async () => {
  const [active] = await db.select()
    .from(events)
    .where(and(eq(events.clanId, clan.id), eq(events.status, "active")))
    .limit(1);
  if (!active) return null;
  return {
    id: active.id,
    type: active.type,
    metric: active.metric,
    displayName: active.displayName,
    startTime: active.startTime.toISOString(),
    endTime: active.endTime.toISOString(),
  };
})(),
```

- [ ] **Step 2: Update admin settings to include WOM config**

In `C:\dev\clan-platform-api\src\routes\admin-settings.ts`:

In the GET handler return object, add a `wom` section after `statTracking`:
```typescript
wom: {
  womGroupId: settings.womGroupId ?? null,
  hasVerificationCode: !!settings.womVerificationCode,
},
```

In the PUT handler, add after the `minDropValue` block:
```typescript
if (body.womGroupId !== undefined) {
  currentSettings.womGroupId = body.womGroupId;
}
if (body.womVerificationCode !== undefined) {
  currentSettings.womVerificationCode = body.womVerificationCode;
}
```

- [ ] **Step 3: Build to verify**

```bash
cd C:\dev\clan-platform-api && npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:\dev\clan-platform-api
git add src/routes/bootstrap.ts src/routes/admin-settings.ts
git commit -m "feat: bootstrap reads events table, settings includes WOM config"
```

---

### Task 5: Web Event Metrics + Types

**Files:**
- Create: `C:\dev\clan-platform-web\src\lib\event-metrics.ts`

- [ ] **Step 1: Create event-metrics.ts**

Create `C:\dev\clan-platform-web\src\lib\event-metrics.ts`:

```typescript
export const EVENT_TYPES = [
  { value: "boss", label: "Boss of the Week" },
  { value: "skill", label: "Skill of the Week" },
  { value: "gamer", label: "Gamer of the Week" },
  { value: "clue", label: "Clue Hunter of the Week" },
] as const;

export type EventType = (typeof EVENT_TYPES)[number]["value"];

export const EVENT_TYPE_COLORS: Record<EventType, { border: string; text: string; bg: string }> = {
  boss: { border: "border-red-500/40", text: "text-red-400", bg: "bg-red-500/10" },
  skill: { border: "border-green-500/40", text: "text-green-400", bg: "bg-green-500/10" },
  gamer: { border: "border-purple-500/40", text: "text-purple-400", bg: "bg-purple-500/10" },
  clue: { border: "border-orange-500/40", text: "text-orange-400", bg: "bg-orange-500/10" },
};

export const EVENT_UNITS: Record<EventType, string> = {
  boss: "KC",
  skill: "XP",
  gamer: "score",
  clue: "completed",
};

export const BOSS_METRICS: Record<string, string> = {
  abyssal_sire: "Abyssal Sire",
  alchemical_hydra: "Alchemical Hydra",
  amoxliatl: "Amoxliatl",
  araxxor: "Araxxor",
  artio: "Artio",
  barrows_chests: "Barrows Chests",
  bryophyta: "Bryophyta",
  callisto: "Callisto",
  calvarion: "Calvar'ion",
  cerberus: "Cerberus",
  chambers_of_xeric: "Chambers of Xeric",
  chambers_of_xeric_challenge_mode: "Chambers of Xeric (CM)",
  chaos_elemental: "Chaos Elemental",
  chaos_fanatic: "Chaos Fanatic",
  commander_zilyana: "Commander Zilyana",
  corporeal_beast: "Corporeal Beast",
  crazy_archaeologist: "Crazy Archaeologist",
  dagannoth_prime: "Dagannoth Prime",
  dagannoth_rex: "Dagannoth Rex",
  dagannoth_supreme: "Dagannoth Supreme",
  deranged_archaeologist: "Deranged Archaeologist",
  duke_sucellus: "Duke Sucellus",
  general_graardor: "General Graardor",
  giant_mole: "Giant Mole",
  grotesque_guardians: "Grotesque Guardians",
  hespori: "Hespori",
  hueycoatl: "Hueycoatl",
  kalphite_queen: "Kalphite Queen",
  king_black_dragon: "King Black Dragon",
  kraken: "Kraken",
  kreearra: "Kree'arra",
  kril_tsutsaroth: "K'ril Tsutsaroth",
  lunar_chests: "Lunar Chests",
  mimic: "Mimic",
  nex: "Nex",
  nightmare: "Nightmare",
  obor: "Obor",
  phantom_muspah: "Phantom Muspah",
  phosanis_nightmare: "Phosani's Nightmare",
  royal_titans: "Royal Titans",
  sarachnis: "Sarachnis",
  scorpia: "Scorpia",
  scurrius: "Scurrius",
  skotizo: "Skotizo",
  sol_heredit: "Sol Heredit",
  spindel: "Spindel",
  tempoross: "Tempoross",
  the_gauntlet: "The Gauntlet",
  the_corrupted_gauntlet: "The Corrupted Gauntlet",
  the_leviathan: "The Leviathan",
  the_whisperer: "The Whisperer",
  theatre_of_blood: "Theatre of Blood",
  theatre_of_blood_hard_mode: "Theatre of Blood (HM)",
  thermonuclear_smoke_devil: "Thermonuclear Smoke Devil",
  tombs_of_amascut: "Tombs of Amascut",
  tombs_of_amascut_expert: "Tombs of Amascut (Expert)",
  tzkal_zuk: "TzKal-Zuk",
  tztok_jad: "TzTok-Jad",
  vardorvis: "Vardorvis",
  venenatis: "Venenatis",
  vetion: "Vet'ion",
  vorkath: "Vorkath",
  wintertodt: "Wintertodt",
  yama: "Yama",
  zalcano: "Zalcano",
  zulrah: "Zulrah",
};

export const SKILL_METRICS: Record<string, string> = {
  overall: "Overall",
  attack: "Attack",
  defence: "Defence",
  strength: "Strength",
  hitpoints: "Hitpoints",
  ranged: "Ranged",
  prayer: "Prayer",
  magic: "Magic",
  cooking: "Cooking",
  woodcutting: "Woodcutting",
  fletching: "Fletching",
  fishing: "Fishing",
  firemaking: "Firemaking",
  crafting: "Crafting",
  smithing: "Smithing",
  mining: "Mining",
  herblore: "Herblore",
  agility: "Agility",
  thieving: "Thieving",
  slayer: "Slayer",
  farming: "Farming",
  runecrafting: "Runecrafting",
  hunter: "Hunter",
  construction: "Construction",
};

export const ACTIVITY_METRICS: Record<string, string> = {
  bounty_hunter_hunter: "Bounty Hunter — Hunter",
  bounty_hunter_rogue: "Bounty Hunter — Rogue",
  colosseum_glory: "Colosseum Glory",
  guardians_of_the_rift: "Guardians of the Rift",
  last_man_standing: "Last Man Standing",
  league_points: "League Points",
  pvp_arena: "PvP Arena",
  rifts_closed: "Rifts Closed",
  soul_wars_zeal: "Soul Wars",
  volcanic_mine: "Volcanic Mine",
};

export const CLUE_METRICS: Record<string, string> = {
  clue_scrolls_all: "All Clues",
  clue_scrolls_beginner: "Beginner",
  clue_scrolls_easy: "Easy",
  clue_scrolls_medium: "Medium",
  clue_scrolls_hard: "Hard",
  clue_scrolls_elite: "Elite",
  clue_scrolls_master: "Master",
};

/** Get the metrics map for a given event type. */
export function getMetricsForType(type: EventType): Record<string, string> {
  switch (type) {
    case "boss": return BOSS_METRICS;
    case "skill": return SKILL_METRICS;
    case "gamer": return ACTIVITY_METRICS;
    case "clue": return CLUE_METRICS;
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd C:\dev\clan-platform-web
git add src/lib/event-metrics.ts
git commit -m "feat: add event metric mappings for web"
```

---

### Task 6: Web Events Page

**Files:**
- Modify: `C:\dev\clan-platform-web\src\app\[slug]\events\page.tsx`
- Modify: `C:\dev\clan-platform-web\src\components\event-card.tsx`

- [ ] **Step 1: Update EventCard component**

Replace `C:\dev\clan-platform-web\src\components\event-card.tsx` with:

```tsx
"use client";

import { useState } from "react";
import { ChevronDown, ChevronUp, Trophy } from "lucide-react";
import { EVENT_TYPE_COLORS, EVENT_UNITS, type EventType } from "@/lib/event-metrics";

interface LeaderboardEntry {
  rsn: string;
  score: number;
}

interface EventCardProps {
  type: string;
  name: string;
  startTime: string;
  endTime: string;
  status: string;
  leaderboard: LeaderboardEntry[];
  expanded?: boolean;
}

export function EventCard({ type, name, startTime, endTime, status, leaderboard, expanded: initialExpanded }: EventCardProps) {
  const [expanded, setExpanded] = useState(initialExpanded ?? false);
  const eventType = type as EventType;
  const colors = EVENT_TYPE_COLORS[eventType] ?? { border: "border-border", text: "text-foreground-muted", bg: "bg-card" };
  const unit = EVENT_UNITS[eventType] ?? "";

  const end = new Date(endTime);
  const now = new Date();
  const remaining = end.getTime() - now.getTime();
  const isActive = status === "active" && remaining > 0;

  const days = Math.floor(remaining / (1000 * 60 * 60 * 24));
  const hours = Math.floor((remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));

  const start = new Date(startTime);
  const dateRange = `${start.toLocaleDateString()} — ${end.toLocaleDateString()}`;

  const topEntries = expanded ? leaderboard : leaderboard.slice(0, 5);

  return (
    <div className={`rounded-lg border-2 bg-card ${colors.border}`}>
      <div className="p-4">
        <div className="flex items-center justify-between mb-1">
          <span className={`text-xs uppercase tracking-wider ${colors.text}`}>
            {type} of the Week
          </span>
          {isActive ? (
            <span className="text-xs text-foreground-muted">
              {days > 0 ? `${days}d ${hours}h remaining` : `${hours}h remaining`}
            </span>
          ) : (
            <span className="text-xs text-foreground-muted">{dateRange}</span>
          )}
        </div>
        <h3 className="font-heading text-xl text-primary">{name}</h3>
      </div>

      {topEntries.length > 0 && (
        <div className="border-t border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-foreground-muted text-xs">
                <th className="text-left py-2 px-4 w-12">#</th>
                <th className="text-left py-2 px-4">Player</th>
                <th className="text-right py-2 px-4">{unit}</th>
              </tr>
            </thead>
            <tbody>
              {topEntries.map((entry, i) => (
                <tr key={entry.rsn} className="border-b border-border last:border-0">
                  <td className="py-2 px-4 text-foreground-muted">
                    {i === 0 ? <Trophy size={14} className={colors.text} /> : i + 1}
                  </td>
                  <td className="py-2 px-4 text-foreground">{entry.rsn}</td>
                  <td className="py-2 px-4 text-right text-foreground-muted font-mono">
                    {entry.score.toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {leaderboard.length > 5 && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="w-full flex items-center justify-center gap-1 py-2 text-xs text-foreground-muted hover:text-foreground border-t border-border transition-colors"
        >
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          {expanded ? "Show less" : `Show all ${leaderboard.length}`}
        </button>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Update events page**

Replace `C:\dev\clan-platform-web\src\app\[slug]\events\page.tsx` with:

```tsx
"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClientFetch } from "@/lib/api-client";
import { EventCard } from "@/components/event-card";

interface LeaderboardEntry {
  id: string;
  rsn: string;
  score: number;
  lastUpdated: string;
}

interface EventData {
  id: string;
  type: string;
  metric: string;
  displayName: string;
  startTime: string;
  endTime: string;
  status: string;
  leaderboard: LeaderboardEntry[];
}

export default function EventsPage() {
  const { slug } = useParams<{ slug: string }>();

  const { data, isLoading } = useQuery({
    queryKey: ["events", slug],
    queryFn: () => apiClientFetch<{ events: EventData[] }>(`/clans/${slug}/events`),
    enabled: !!slug,
    refetchInterval: 60_000, // refresh every minute
  });

  const events = data?.events ?? [];
  const activeEvent = events.find((e) => e.status === "active");
  const pastEvents = events.filter((e) => e.status !== "active");

  if (isLoading) {
    return <p className="text-foreground-muted text-center py-8">Loading events...</p>;
  }

  return (
    <div className="space-y-6">
      <h1 className="font-heading text-3xl text-primary">Events</h1>

      {activeEvent ? (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-foreground-muted">Active Event</h2>
          <EventCard
            type={activeEvent.type}
            name={activeEvent.displayName}
            startTime={activeEvent.startTime}
            endTime={activeEvent.endTime}
            status={activeEvent.status}
            leaderboard={activeEvent.leaderboard}
            expanded
          />
        </div>
      ) : (
        <div className="rounded-lg border border-border bg-card p-8 text-center">
          <p className="text-foreground-muted">No active event.</p>
          <p className="text-foreground-muted text-sm mt-1">
            Events can be started by a clan admin from the Admin panel or RuneLite plugin.
          </p>
        </div>
      )}

      {pastEvents.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-foreground-muted">Past Events</h2>
          {pastEvents.map((event) => (
            <EventCard
              key={event.id}
              type={event.type}
              name={event.displayName}
              startTime={event.startTime}
              endTime={event.endTime}
              status={event.status}
              leaderboard={event.leaderboard}
            />
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Build to verify**

```bash
cd C:\dev\clan-platform-web && npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:\dev\clan-platform-web
git add src/app/[slug]/events/page.tsx src/components/event-card.tsx
git commit -m "feat: events page with active event leaderboard and past events"
```

---

### Task 7: Web Admin — WOM Integration + Event Management

**Files:**
- Modify: `C:\dev\clan-platform-web\src\app\[slug]\admin\settings-tab.tsx`

- [ ] **Step 1: Add WOM Integration and Weekly Events sections to settings-tab.tsx**

In `C:\dev\clan-platform-web\src\app\[slug]\admin\settings-tab.tsx`:

Update the `SettingsResponse` interface to add:
```typescript
wom: { womGroupId: number | null; hasVerificationCode: boolean };
```

Add imports at the top:
```typescript
import { Play, Square, Globe } from "lucide-react";
import { EVENT_TYPES, getMetricsForType, type EventType } from "@/lib/event-metrics";
```

In the `SettingsTab` component return, add between `<StatTrackingSection>` and `<ClanCodesSection>`:
```tsx
<WomSection slug={slug} settings={settings} onSaved={invalidateSettings} />
<EventsSection slug={slug} settings={settings} onSaved={invalidateSettings} />
```

Then add the two new section components:

```tsx
// ─── WOM Integration ────────────────────────────────────────────────────────

function WomSection({
  slug,
  settings,
  onSaved,
}: {
  slug: string;
  settings: SettingsResponse | undefined;
  onSaved: () => void;
}) {
  const [groupId, setGroupId] = useState<string>(
    settings?.wom.womGroupId?.toString() ?? ""
  );
  const [verificationCode, setVerificationCode] = useState("");
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);

  async function save() {
    setSaving(true);
    try {
      const body: Record<string, unknown> = {};
      if (groupId) body.womGroupId = parseInt(groupId, 10);
      if (verificationCode) body.womVerificationCode = verificationCode;
      await apiAuthFetch(`/admin/${slug}/settings`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
      setVerificationCode("");
      onSaved();
    } finally {
      setSaving(false);
    }
  }

  async function syncNow() {
    setSyncing(true);
    try {
      await apiAuthFetch(`/admin/${slug}/settings/wom-sync`, { method: "POST" });
    } catch {
      // ignore — will add this endpoint
    } finally {
      setSyncing(false);
    }
  }

  return (
    <section className="space-y-4 pb-8 border-b border-border">
      <h3 className="text-lg font-heading text-primary">WOM Integration</h3>
      <p className="text-sm text-foreground-muted">
        Connect your Wise Old Man group for automatic roster sync and event leaderboards.
      </p>

      <div className="space-y-3 max-w-sm">
        <div className="space-y-1">
          <label className="text-xs text-foreground-muted">WOM Group ID</label>
          <Input
            type="number"
            value={groupId}
            onChange={(e) => setGroupId(e.target.value)}
            placeholder="e.g. 1234"
          />
        </div>

        <div className="space-y-1">
          <label className="text-xs text-foreground-muted">
            Verification Code {settings?.wom.hasVerificationCode && (
              <span className="text-green-400 ml-1">(saved)</span>
            )}
          </label>
          <Input
            type="password"
            value={verificationCode}
            onChange={(e) => setVerificationCode(e.target.value)}
            placeholder={settings?.wom.hasVerificationCode ? "••••••••" : "Enter code"}
          />
        </div>

        <div className="flex gap-2">
          <button
            onClick={save}
            disabled={saving}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md bg-primary/20 text-primary hover:bg-primary/30 disabled:opacity-50 transition-colors"
          >
            <Save size={13} />
            {saving ? "Saving..." : "Save"}
          </button>
          {settings?.wom.womGroupId && settings?.wom.hasVerificationCode && (
            <button
              onClick={syncNow}
              disabled={syncing}
              className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md border border-border text-foreground-muted hover:text-foreground hover:bg-card-elevated disabled:opacity-50 transition-colors"
            >
              <Globe size={13} className={syncing ? "animate-spin" : ""} />
              {syncing ? "Syncing..." : "Sync Roster to WOM"}
            </button>
          )}
        </div>
      </div>
    </section>
  );
}

// ─── Weekly Events ──────────────────────────────────────────────────────────

function EventsSection({
  slug,
  settings,
  onSaved,
}: {
  slug: string;
  settings: SettingsResponse | undefined;
  onSaved: () => void;
}) {
  const queryClient = useQueryClient();
  const [eventType, setEventType] = useState<EventType>("boss");
  const [metric, setMetric] = useState("");
  const [duration, setDuration] = useState(7);
  const [starting, setStarting] = useState(false);
  const [ending, setEnding] = useState(false);
  const [confirmEnd, setConfirmEnd] = useState(false);

  const { data: activeData } = useQuery({
    queryKey: ["active-event", slug],
    queryFn: () => apiAuthFetch<{ event: { id: string; type: string; displayName: string; endTime: string } | null }>(`/admin/${slug}/events/active`),
  });

  const activeEvent = activeData?.event;
  const hasWom = !!settings?.wom.womGroupId;
  const metrics = getMetricsForType(eventType);
  const metricEntries = Object.entries(metrics);

  async function startEvent() {
    if (!metric) return;
    const displayName = metrics[metric] ?? metric;
    setStarting(true);
    try {
      await apiAuthFetch(`/admin/${slug}/events`, {
        method: "POST",
        body: JSON.stringify({ type: eventType, metric, displayName, durationDays: duration }),
      });
      queryClient.invalidateQueries({ queryKey: ["active-event", slug] });
      onSaved();
    } finally {
      setStarting(false);
    }
  }

  async function endEvent() {
    if (!activeEvent) return;
    setEnding(true);
    try {
      await apiAuthFetch(`/admin/${slug}/events/${activeEvent.id}/end`, { method: "POST" });
      queryClient.invalidateQueries({ queryKey: ["active-event", slug] });
      setConfirmEnd(false);
      onSaved();
    } finally {
      setEnding(false);
    }
  }

  return (
    <section className="space-y-4 pb-8 border-b border-border">
      <h3 className="text-lg font-heading text-primary">Weekly Events</h3>

      {!hasWom && (
        <p className="text-sm text-yellow-400">
          Configure a WOM Group ID above to enable events.
        </p>
      )}

      {activeEvent && (
        <div className="rounded-md border border-primary/30 bg-primary/5 px-4 py-3 space-y-2">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs uppercase tracking-wider text-foreground-muted">
                {activeEvent.type} of the Week
              </span>
              <p className="text-foreground font-medium">{activeEvent.displayName}</p>
            </div>
            <span className="text-xs text-foreground-muted">
              Ends {new Date(activeEvent.endTime).toLocaleDateString()}
            </span>
          </div>
          {!confirmEnd ? (
            <button
              onClick={() => setConfirmEnd(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md border border-red-500/40 text-red-400 hover:bg-red-500/10 transition-colors"
            >
              <Square size={13} />
              End Event
            </button>
          ) : (
            <div className="flex items-center gap-2">
              <button
                onClick={endEvent}
                disabled={ending}
                className="px-3 py-1 text-sm rounded-md bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 transition-colors"
              >
                {ending ? "Ending..." : "Confirm End"}
              </button>
              <button
                onClick={() => setConfirmEnd(false)}
                className="px-3 py-1 text-sm rounded-md border border-border text-foreground-muted hover:text-foreground transition-colors"
              >
                Cancel
              </button>
            </div>
          )}
        </div>
      )}

      <div className="space-y-3 max-w-sm">
        <div className="space-y-1">
          <label className="text-xs text-foreground-muted">Event Type</label>
          <select
            value={eventType}
            onChange={(e) => { setEventType(e.target.value as EventType); setMetric(""); }}
            disabled={!hasWom}
            className="w-full text-sm rounded-lg border border-input bg-transparent px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-1 focus:ring-primary disabled:opacity-50"
          >
            {EVENT_TYPES.map((t) => (
              <option key={t.value} value={t.value}>{t.label}</option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-xs text-foreground-muted">Metric</label>
          <select
            value={metric}
            onChange={(e) => setMetric(e.target.value)}
            disabled={!hasWom}
            className="w-full text-sm rounded-lg border border-input bg-transparent px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-1 focus:ring-primary disabled:opacity-50"
          >
            <option value="">Select metric...</option>
            {metricEntries.map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-xs text-foreground-muted">Duration (days)</label>
          <Input
            type="number"
            value={duration}
            onChange={(e) => setDuration(Number(e.target.value))}
            min={1}
            max={30}
            disabled={!hasWom}
          />
        </div>

        <button
          onClick={startEvent}
          disabled={!hasWom || !metric || starting}
          className="flex items-center gap-1.5 px-4 py-2 text-sm rounded-md bg-primary/20 text-primary hover:bg-primary/30 disabled:opacity-50 transition-colors"
        >
          <Play size={13} />
          {starting ? "Starting..." : activeEvent ? "Replace Event" : "Start Event"}
        </button>
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Build to verify**

```bash
cd C:\dev\clan-platform-web && npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:\dev\clan-platform-web
git add src/app/[slug]/admin/settings-tab.tsx
git commit -m "feat: add WOM integration and weekly events admin sections"
```

---

### Task 8: Plugin — Rewire AdminService + Plugin

**Files:**
- Modify: `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\AdminService.java`
- Modify: `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\ClanManagementPlugin.java`

- [ ] **Step 1: Add platform event methods to AdminService**

In `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\AdminService.java`:

Add two new methods that call the platform API. These are alternatives to the existing Google Sheets methods — the plugin will call the platform versions when `clanCode` is configured.

After the existing `endEvent` method (line 146), add:

```java
/**
 * Start a weekly event via the platform API.
 * POST /admin/{slug}/events
 */
public String startEventPlatform(String baseUrl, String apiKey, String slug,
                                  String eventType, String metric, String displayName) throws IOException
{
    JsonObject payload = new JsonObject();
    payload.addProperty("type", eventType);
    payload.addProperty("metric", metric);
    payload.addProperty("displayName", displayName);
    payload.addProperty("durationDays", 7);

    RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
    Request request = new Request.Builder()
        .url(baseUrl + "/admin/" + slug + "/events")
        .header("Authorization", "Bearer " + apiKey)
        .post(body)
        .build();

    try (Response response = httpClient.newCall(request).execute())
    {
        if (!response.isSuccessful())
        {
            throw new IOException("Platform API returned status: " + response.code());
        }
        return "Event started";
    }
}

/**
 * End a weekly event via the platform API.
 * POST /admin/{slug}/events/{id}/end
 */
public String endEventPlatform(String baseUrl, String apiKey, String slug,
                                String eventId) throws IOException
{
    Request request = new Request.Builder()
        .url(baseUrl + "/admin/" + slug + "/events/" + eventId + "/end")
        .header("Authorization", "Bearer " + apiKey)
        .post(RequestBody.create(JSON_TYPE, "{}"))
        .build();

    try (Response response = httpClient.newCall(request).execute())
    {
        if (!response.isSuccessful())
        {
            throw new IOException("Platform API returned status: " + response.code());
        }
        return "Event ended";
    }
}
```

- [ ] **Step 2: Update ClanManagementPlugin to parse event ID and use platform methods**

In `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\ClanManagementPlugin.java`:

Add a new field after the existing event fields (around line 178):
```java
private String activeEventId = "";
```

In the bootstrap parsing section (around line 366), after parsing `activeEventType`, add parsing of the `id` field:
```java
activeEventId = event.has("id") ? event.get("id").getAsString() : "";
```

And in the else block (around line 376) that clears event state, add:
```java
activeEventId = "";
```

In the `setupAdminPanel()` method, in the `onStartEvent` callback (around line 2694-2710), wrap the existing call to use the platform API when `clanCode` is configured:

Replace the line:
```java
adminService.startEvent(clanApiUrl, apiKey, adminKey, type, metric, displayName);
```

With:
```java
if (isPlatformMode())
{
    adminService.startEventPlatform(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), type, metric, displayName);
}
else
{
    adminService.startEvent(clanApiUrl, apiKey, adminKey, type, metric, displayName);
}
```

Similarly for `onEndEvent` (around line 2748), replace:
```java
adminService.endEvent(clanApiUrl, apiKey, adminKey);
```

With:
```java
if (isPlatformMode())
{
    adminService.endEventPlatform(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), activeEventId);
}
else
{
    adminService.endEvent(clanApiUrl, apiKey, adminKey);
}
```

Note: `isPlatformMode()`, `getPlatformUrl()`, `getPlatformKey()`, and `getPlatformSlug()` are existing helper methods on the plugin that check if the clan code is configured.

- [ ] **Step 3: Build to verify**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin && ./gradlew build
```

- [ ] **Step 4: Commit**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin
git add src/main/java/com/droplogger/AdminService.java src/main/java/com/droplogger/ClanManagementPlugin.java
git commit -m "feat: rewire plugin event start/end to platform API"
```

---

### Task 9: Add WOM Sync Trigger Endpoint

**Files:**
- Modify: `C:\dev\clan-platform-api\src\routes\admin-settings.ts`

The web admin "Sync Roster to WOM" button needs an endpoint to trigger an immediate sync.

- [ ] **Step 1: Add wom-sync endpoint**

In `C:\dev\clan-platform-api\src\routes\admin-settings.ts`, add a new route after the purge endpoint:

```typescript
// POST /admin/:slug/settings/wom-sync — trigger immediate WOM group sync
app.post<{ Params: { slug: string } }>(
  "/admin/:slug/settings/wom-sync",
  { preHandler: [requireAdmin("manage_settings")] },
  async (request, reply) => {
    const clan = await getClanBySlug(request.params.slug);
    if (!clan) return reply.status(404).send({ error: "Clan not found" });

    const settings = (clan.settings ?? {}) as Record<string, unknown>;
    const womGroupId = settings.womGroupId as number | undefined;
    const womVerificationCode = settings.womVerificationCode as string | undefined;

    if (!womGroupId || !womVerificationCode) {
      return reply.status(400).send({ error: "WOM group ID and verification code required" });
    }

    const { syncGroupMembers } = await import("../lib/wom-service.js");

    const roster = await db.select().from(clanRoster)
      .where(eq(clanRoster.clanId, clan.id));

    if (roster.length === 0) {
      return reply.status(400).send({ error: "No roster members to sync" });
    }

    const members = roster.map((r) => ({ username: r.rsn }));

    try {
      await syncGroupMembers(womGroupId, womVerificationCode, members);
      return { success: true, synced: members.length };
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unknown error";
      return reply.status(502).send({ error: `WOM sync failed: ${message}` });
    }
  },
);
```

Add the missing import at the top of the file (if not already present):
```typescript
import { clanRoster } from "../db/schema/index.js";
```

- [ ] **Step 2: Add active event endpoint for admin**

In the same file, add a route the admin settings EventsSection can use to check the active event:

```typescript
// GET /admin/:slug/events/active — get active event for admin panel
app.get<{ Params: { slug: string } }>(
  "/admin/:slug/events/active",
  { preHandler: [requireAdmin("manage_events")] },
  async (request, reply) => {
    const clan = await getClanBySlug(request.params.slug);
    if (!clan) return reply.status(404).send({ error: "Clan not found" });

    const [event] = await db.select()
      .from(events)
      .where(and(eq(events.clanId, clan.id), eq(events.status, "active")))
      .limit(1);

    return { event: event ?? null };
  },
);
```

Add the `events` import at the top:
```typescript
import { clans, discordWebhooks, whitelist, clanRoster, events } from "../db/schema/index.js";
```

And add the `and` import from drizzle-orm if not already present.

- [ ] **Step 3: Build to verify**

```bash
cd C:\dev\clan-platform-api && npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:\dev\clan-platform-api
git add src/routes/admin-settings.ts
git commit -m "feat: add WOM sync trigger and admin active event endpoint"
```

---

### Task 10: Build, Deploy, and Test

- [ ] **Step 1: Build and restart API**

```bash
cd C:\dev\clan-platform-api && npm run build && npx pm2 restart clan-api
```

- [ ] **Step 2: Build and restart web**

```bash
cd C:\dev\clan-platform-web && npm run build && npx pm2 restart clan-web
```

- [ ] **Step 3: Build plugin**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin && ./gradlew build
cp build/libs/drop-logger-plugin-*.jar "$APPDATA/runelite/externalPlugins/clan-management.jar"
```

- [ ] **Step 4: Verify**

1. Visit admin → Settings → WOM Integration section appears
2. Enter WOM group ID and verification code, save
3. Click "Sync Roster to WOM" — should succeed if credentials are valid
4. Go to Settings → Weekly Events, select Boss of the Week → Zulrah → Start Event
5. Visit Events page — active event card with leaderboard (populates after cron runs)
6. In RuneLite plugin — bootstrap should show active event, event card on home tab
7. End event from either web or plugin — verify it ends and appears in past events
8. Start a Skill of the Week event — verify green accent
9. Check WOM group membership updates after 30 minutes
