# Clan Platform Phase 1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Fastify API with PostgreSQL, implement auth and core CRUD routes, and add dual-mode backend support to the RuneLite plugin so it can send data to the new platform API.

**Architecture:** A standalone Fastify + TypeScript API server with Drizzle ORM managing a PostgreSQL database. The API handles JWT auth (via Discord OAuth2) and API key auth (for the plugin). The existing RuneLite plugin gains a `PlatformApiService` that mirrors `GoogleSheetsService` but targets the new API. Both backends can be active simultaneously.

**Tech Stack:** Fastify 5, TypeScript, Drizzle ORM, PostgreSQL, Zod (validation), Vitest (testing), bcrypt (API key hashing), jose (JWT), OkHttp (plugin HTTP client, already in use)

---

## File Structure

### New repo: `clan-platform-api/`

```
clan-platform-api/
├── package.json
├── tsconfig.json
├── drizzle.config.ts
├── .env.example
├── .env                            (gitignored)
├── .gitignore
├── vitest.config.ts
├── src/
│   ├── index.ts                    — entry point, starts server
│   ├── app.ts                      — Fastify app factory, registers plugins + routes
│   ├── config.ts                   — env validation with Zod
│   ├── db/
│   │   ├── index.ts                — Drizzle client singleton
│   │   └── schema/
│   │       ├── clans.ts            — clans table
│   │       ├── users.ts            — users + user_roles tables
│   │       ├── players.ts          — players table
│   │       ├── roles.ts            — roles table
│   │       ├── drops.ts            — drops table
│   │       ├── personal-bests.ts   — personal_bests table
│   │       ├── collection-log.ts   — collection_log_entries table
│   │       ├── achievements.ts     — achievements table
│   │       ├── stat-snapshots.ts   — stat_snapshots table
│   │       ├── events.ts           — events + event_leaderboard tables
│   │       ├── bingo.ts            — all bingo tables
│   │       ├── config.ts           — whitelist + discord_webhooks tables
│   │       └── index.ts            — re-exports all schemas
│   ├── auth/
│   │   ├── jwt.ts                  — sign/verify JWT tokens
│   │   ├── discord.ts              — Discord OAuth2 token exchange + user fetch
│   │   └── middleware.ts           — requireAuth (JWT) + requireApiKey middleware
│   ├── routes/
│   │   ├── auth.ts                 — /auth/* routes
│   │   ├── drops.ts                — /clans/:slug/drops routes
│   │   ├── collection-log.ts       — /clans/:slug/collection-log routes
│   │   ├── personal-bests.ts       — /clans/:slug/pbs routes
│   │   ├── players.ts              — /clans/:slug/players routes
│   │   └── achievements.ts         — /clans/:slug/achievements routes
│   └── lib/
│       └── clan-lookup.ts          — shared helper: resolve clan slug → clan record
├── drizzle/                        — (generated migration SQL files)
└── tests/
    ├── setup.ts                    — test db setup/teardown, app factory
    ├── drops.test.ts
    ├── collection-log.test.ts
    ├── personal-bests.test.ts
    ├── players.test.ts
    └── achievements.test.ts
```

### Modified in existing repo: `clan-management-plugin/`

```
src/main/java/com/droplogger/
├── PlatformApiService.java         — NEW: sends data to platform API
├── ClanManagementConfig.java       — MODIFIED: add platformApiUrl, platformApiKey fields
└── ClanManagementPlugin.java       — MODIFIED: wire dual-mode dispatch
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `clan-platform-api/package.json`
- Create: `clan-platform-api/tsconfig.json`
- Create: `clan-platform-api/drizzle.config.ts`
- Create: `clan-platform-api/.env.example`
- Create: `clan-platform-api/.gitignore`
- Create: `clan-platform-api/vitest.config.ts`
- Create: `clan-platform-api/src/config.ts`
- Create: `clan-platform-api/src/app.ts`
- Create: `clan-platform-api/src/index.ts`

- [ ] **Step 1: Create the repo directory and initialize**

```bash
mkdir -p ~/dev/clan-platform-api
cd ~/dev/clan-platform-api
git init
```

- [ ] **Step 2: Create package.json**

Create `package.json`:

```json
{
  "name": "clan-platform-api",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "tsx watch src/index.ts",
    "build": "tsc",
    "start": "node dist/index.js",
    "db:generate": "drizzle-kit generate",
    "db:migrate": "drizzle-kit migrate",
    "db:studio": "drizzle-kit studio",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "fastify": "^5.3.3",
    "@fastify/cors": "^11.0.1",
    "@fastify/multipart": "^9.0.3",
    "drizzle-orm": "^0.44.2",
    "postgres": "^3.4.7",
    "zod": "^3.24.4",
    "jose": "^6.0.11",
    "bcrypt": "^6.0.0",
    "dotenv": "^16.5.0"
  },
  "devDependencies": {
    "typescript": "^5.8.3",
    "tsx": "^4.19.4",
    "drizzle-kit": "^0.31.1",
    "@types/node": "^22.15.3",
    "@types/bcrypt": "^5.0.2",
    "vitest": "^3.1.3"
  }
}
```

- [ ] **Step 3: Create tsconfig.json**

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "esModuleInterop": true,
    "strict": true,
    "outDir": "dist",
    "rootDir": "src",
    "skipLibCheck": true,
    "resolveJsonModule": true,
    "declaration": true
  },
  "include": ["src"],
  "exclude": ["node_modules", "dist", "tests"]
}
```

- [ ] **Step 4: Create drizzle.config.ts**

Create `drizzle.config.ts`:

```typescript
import { defineConfig } from "drizzle-kit";

export default defineConfig({
  schema: "./src/db/schema/index.ts",
  out: "./drizzle",
  dialect: "postgresql",
  dbCredentials: {
    url: process.env.DATABASE_URL!,
  },
});
```

- [ ] **Step 5: Create .env.example and .gitignore**

Create `.env.example`:

```
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/clan_platform
JWT_SECRET=change-me-to-a-random-64-char-string
DISCORD_CLIENT_ID=your-discord-app-client-id
DISCORD_CLIENT_SECRET=your-discord-app-client-secret
DISCORD_REDIRECT_URI=http://localhost:3001/auth/discord/callback
PORT=3001
```

Create `.gitignore`:

```
node_modules/
dist/
.env
*.log
```

- [ ] **Step 6: Create vitest.config.ts**

Create `vitest.config.ts`:

```typescript
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    environment: "node",
    setupFiles: ["./tests/setup.ts"],
  },
});
```

- [ ] **Step 7: Create src/config.ts**

Create `src/config.ts`:

```typescript
import { z } from "zod";
import "dotenv/config";

const envSchema = z.object({
  DATABASE_URL: z.string().url(),
  JWT_SECRET: z.string().min(32),
  DISCORD_CLIENT_ID: z.string().min(1),
  DISCORD_CLIENT_SECRET: z.string().min(1),
  DISCORD_REDIRECT_URI: z.string().url(),
  PORT: z.coerce.number().default(3001),
});

export const env = envSchema.parse(process.env);
```

- [ ] **Step 8: Create src/app.ts**

Create `src/app.ts`:

```typescript
import Fastify, { FastifyInstance } from "fastify";
import cors from "@fastify/cors";

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true });

  await app.register(cors, { origin: true, credentials: true });

  app.get("/health", async () => ({ status: "ok" }));

  return app;
}
```

- [ ] **Step 9: Create src/index.ts**

Create `src/index.ts`:

```typescript
import { env } from "./config.js";
import { buildApp } from "./app.js";

async function main() {
  const app = await buildApp();

  await app.listen({ port: env.PORT, host: "0.0.0.0" });
  console.log(`Server listening on port ${env.PORT}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
```

- [ ] **Step 10: Install dependencies and verify**

```bash
cd ~/dev/clan-platform-api
npm install
npx tsx src/index.ts
# Should print: Server listening on port 3001
# (will fail on DATABASE_URL validation if no .env — that's expected)
```

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: project scaffolding — Fastify + TypeScript + Drizzle setup"
```

---

### Task 2: Database Schema

**Files:**
- Create: `clan-platform-api/src/db/index.ts`
- Create: `clan-platform-api/src/db/schema/clans.ts`
- Create: `clan-platform-api/src/db/schema/users.ts`
- Create: `clan-platform-api/src/db/schema/players.ts`
- Create: `clan-platform-api/src/db/schema/roles.ts`
- Create: `clan-platform-api/src/db/schema/drops.ts`
- Create: `clan-platform-api/src/db/schema/personal-bests.ts`
- Create: `clan-platform-api/src/db/schema/collection-log.ts`
- Create: `clan-platform-api/src/db/schema/achievements.ts`
- Create: `clan-platform-api/src/db/schema/stat-snapshots.ts`
- Create: `clan-platform-api/src/db/schema/events.ts`
- Create: `clan-platform-api/src/db/schema/bingo.ts`
- Create: `clan-platform-api/src/db/schema/config.ts`
- Create: `clan-platform-api/src/db/schema/index.ts`

- [ ] **Step 1: Create src/db/index.ts**

```typescript
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import { env } from "../config.js";
import * as schema from "./schema/index.js";

const client = postgres(env.DATABASE_URL);
export const db = drizzle(client, { schema });
export type Database = typeof db;
```

- [ ] **Step 2: Create src/db/schema/clans.ts**

```typescript
import { pgTable, uuid, varchar, jsonb, timestamp } from "drizzle-orm/pg-core";

export const clans = pgTable("clans", {
  id: uuid("id").primaryKey().defaultRandom(),
  name: varchar("name", { length: 100 }).notNull(),
  slug: varchar("slug", { length: 100 }).notNull().unique(),
  discordGuildId: varchar("discord_guild_id", { length: 30 }).unique(),
  apiKeyHash: varchar("api_key_hash", { length: 255 }).notNull(),
  settings: jsonb("settings").$type<Record<string, unknown>>().default({}),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});
```

- [ ] **Step 3: Create src/db/schema/users.ts**

```typescript
import { pgTable, uuid, varchar, timestamp } from "drizzle-orm/pg-core";
import { roles } from "./roles.js";

export const users = pgTable("users", {
  id: uuid("id").primaryKey().defaultRandom(),
  discordId: varchar("discord_id", { length: 30 }).notNull().unique(),
  discordUsername: varchar("discord_username", { length: 100 }).notNull(),
  avatarUrl: varchar("avatar_url", { length: 500 }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const userRoles = pgTable("user_roles", {
  userId: uuid("user_id").notNull().references(() => users.id),
  roleId: uuid("role_id").notNull().references(() => roles.id),
});
```

- [ ] **Step 4: Create src/db/schema/roles.ts**

```typescript
import { pgTable, uuid, varchar, jsonb, integer, unique } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";

export const roles = pgTable("roles", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  name: varchar("name", { length: 100 }).notNull(),
  discordRoleId: varchar("discord_role_id", { length: 30 }),
  permissions: jsonb("permissions").$type<string[]>().default([]),
  priority: integer("priority").notNull().default(0),
}, (t) => [
  unique().on(t.clanId, t.name),
]);
```

- [ ] **Step 5: Create src/db/schema/players.ts**

```typescript
import { pgTable, uuid, varchar, boolean, timestamp, unique } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";
import { users } from "./users.js";

export const players = pgTable("players", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id").references(() => users.id),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  rsn: varchar("rsn", { length: 30 }).notNull(),
  verified: boolean("verified").notNull().default(false),
  verifiedAt: timestamp("verified_at", { withTimezone: true }),
  joinedAt: timestamp("joined_at", { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  unique().on(t.clanId, t.rsn),
]);
```

- [ ] **Step 6: Create src/db/schema/drops.ts**

```typescript
import { pgTable, uuid, varchar, integer, bigint, timestamp } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";
import { players } from "./players.js";

export const drops = pgTable("drops", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  playerId: uuid("player_id").notNull().references(() => players.id),
  itemName: varchar("item_name", { length: 200 }).notNull(),
  itemId: integer("item_id"),
  value: bigint("value", { mode: "number" }).notNull().default(0),
  monsterName: varchar("monster_name", { length: 200 }).notNull(),
  killCount: integer("kill_count"),
  screenshotUrl: varchar("screenshot_url", { length: 500 }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});
```

- [ ] **Step 7: Create src/db/schema/personal-bests.ts**

```typescript
import { pgTable, uuid, varchar, integer, timestamp } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";
import { players } from "./players.js";

export const personalBests = pgTable("personal_bests", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  playerId: uuid("player_id").notNull().references(() => players.id),
  bossKey: varchar("boss_key", { length: 100 }).notNull(),
  teamSize: integer("team_size").notNull().default(1),
  timeMs: integer("time_ms").notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});
```

- [ ] **Step 8: Create src/db/schema/collection-log.ts**

```typescript
import { pgTable, uuid, varchar, integer, timestamp, unique } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";
import { players } from "./players.js";

export const collectionLogEntries = pgTable("collection_log_entries", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  playerId: uuid("player_id").notNull().references(() => players.id),
  itemName: varchar("item_name", { length: 200 }).notNull(),
  itemId: integer("item_id"),
  obtainedAt: timestamp("obtained_at", { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  unique().on(t.clanId, t.playerId, t.itemName),
]);
```

- [ ] **Step 9: Create src/db/schema/achievements.ts**

```typescript
import { pgTable, uuid, varchar, timestamp } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";
import { players } from "./players.js";

export const achievements = pgTable("achievements", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  playerId: uuid("player_id").notNull().references(() => players.id),
  type: varchar("type", { length: 50 }).notNull(),
  detail: varchar("detail", { length: 200 }).notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});
```

- [ ] **Step 10: Create src/db/schema/stat-snapshots.ts**

```typescript
import { pgTable, uuid, jsonb, timestamp } from "drizzle-orm/pg-core";
import { players } from "./players.js";

export const statSnapshots = pgTable("stat_snapshots", {
  id: uuid("id").primaryKey().defaultRandom(),
  playerId: uuid("player_id").notNull().references(() => players.id),
  skills: jsonb("skills").$type<Record<string, number>>().notNull(),
  timestamp: timestamp("timestamp", { withTimezone: true }).notNull().defaultNow(),
});
```

- [ ] **Step 11: Create src/db/schema/events.ts**

```typescript
import { pgTable, uuid, varchar, bigint, timestamp } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";
import { players } from "./players.js";

export const events = pgTable("events", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  type: varchar("type", { length: 20 }).notNull(),
  metric: varchar("metric", { length: 100 }).notNull(),
  displayName: varchar("display_name", { length: 200 }).notNull(),
  startTime: timestamp("start_time", { withTimezone: true }).notNull(),
  endTime: timestamp("end_time", { withTimezone: true }).notNull(),
  status: varchar("status", { length: 20 }).notNull().default("active"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const eventLeaderboard = pgTable("event_leaderboard", {
  id: uuid("id").primaryKey().defaultRandom(),
  eventId: uuid("event_id").notNull().references(() => events.id),
  playerId: uuid("player_id").notNull().references(() => players.id),
  score: bigint("score", { mode: "number" }).notNull().default(0),
  lastUpdated: timestamp("last_updated", { withTimezone: true }).notNull().defaultNow(),
});
```

- [ ] **Step 12: Create src/db/schema/bingo.ts**

```typescript
import { pgTable, uuid, varchar, integer, numeric, timestamp, jsonb } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";
import { players } from "./players.js";

export const bingoEvents = pgTable("bingo_events", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  name: varchar("name", { length: 200 }).notNull(),
  gridRows: integer("grid_rows").notNull(),
  gridCols: integer("grid_cols").notNull(),
  startTime: timestamp("start_time", { withTimezone: true }).notNull(),
  endTime: timestamp("end_time", { withTimezone: true }).notNull(),
  status: varchar("status", { length: 20 }).notNull().default("active"),
  settings: jsonb("settings").$type<Record<string, unknown>>().default({}),
});

export const bingoTiles = pgTable("bingo_tiles", {
  id: uuid("id").primaryKey().defaultRandom(),
  bingoEventId: uuid("bingo_event_id").notNull().references(() => bingoEvents.id),
  row: integer("row").notNull(),
  col: integer("col").notNull(),
  name: varchar("name", { length: 200 }).notNull(),
  code: varchar("code", { length: 20 }).notNull(),
  points: numeric("points", { precision: 10, scale: 2 }).notNull(),
});

export const bingoTeams = pgTable("bingo_teams", {
  id: uuid("id").primaryKey().defaultRandom(),
  bingoEventId: uuid("bingo_event_id").notNull().references(() => bingoEvents.id),
  name: varchar("name", { length: 100 }).notNull(),
  code: varchar("code", { length: 20 }).notNull(),
  color: varchar("color", { length: 20 }).notNull(),
});

export const bingoTeamMembers = pgTable("bingo_team_members", {
  id: uuid("id").primaryKey().defaultRandom(),
  teamId: uuid("team_id").notNull().references(() => bingoTeams.id),
  playerId: uuid("player_id").notNull().references(() => players.id),
});

export const bingoProgress = pgTable("bingo_progress", {
  id: uuid("id").primaryKey().defaultRandom(),
  tileId: uuid("tile_id").notNull().references(() => bingoTiles.id),
  teamId: uuid("team_id").notNull().references(() => bingoTeams.id),
  playerId: uuid("player_id").notNull().references(() => players.id),
  completedAt: timestamp("completed_at", { withTimezone: true }).notNull().defaultNow(),
});

export const bingoBounties = pgTable("bingo_bounties", {
  id: uuid("id").primaryKey().defaultRandom(),
  bingoEventId: uuid("bingo_event_id").notNull().references(() => bingoEvents.id),
  number: integer("number").notNull(),
  description: varchar("description", { length: 500 }).notNull(),
  points: numeric("points", { precision: 10, scale: 2 }).notNull(),
  releaseTime: timestamp("release_time", { withTimezone: true }).notNull(),
  claimedBy: uuid("claimed_by").references(() => players.id),
  claimedAt: timestamp("claimed_at", { withTimezone: true }),
});
```

- [ ] **Step 13: Create src/db/schema/config.ts**

```typescript
import { pgTable, uuid, varchar, bigint, integer } from "drizzle-orm/pg-core";
import { clans } from "./clans.js";

export const whitelist = pgTable("whitelist", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  itemName: varchar("item_name", { length: 200 }).notNull(),
  itemId: integer("item_id"),
  minValue: bigint("min_value", { mode: "number" }),
});

export const discordWebhooks = pgTable("discord_webhooks", {
  id: uuid("id").primaryKey().defaultRandom(),
  clanId: uuid("clan_id").notNull().references(() => clans.id),
  type: varchar("type", { length: 50 }).notNull(),
  webhookUrl: varchar("webhook_url", { length: 500 }).notNull(),
});
```

- [ ] **Step 14: Create src/db/schema/index.ts**

```typescript
export * from "./clans.js";
export * from "./users.js";
export * from "./players.js";
export * from "./roles.js";
export * from "./drops.js";
export * from "./personal-bests.js";
export * from "./collection-log.js";
export * from "./achievements.js";
export * from "./stat-snapshots.js";
export * from "./events.js";
export * from "./bingo.js";
export * from "./config.js";
```

- [ ] **Step 15: Generate and run migrations**

```bash
cd ~/dev/clan-platform-api
# Create the database first
psql -U postgres -c "CREATE DATABASE clan_platform;"

# Copy .env.example to .env, fill in DATABASE_URL
cp .env.example .env
# Edit .env with real values

# Generate migration SQL from schema
npx drizzle-kit generate

# Apply migrations
npx drizzle-kit migrate
```

- [ ] **Step 16: Verify tables exist**

```bash
psql -U postgres -d clan_platform -c "\dt"
# Should list all tables: clans, users, players, roles, user_roles, drops,
# personal_bests, collection_log_entries, achievements, stat_snapshots,
# events, event_leaderboard, bingo_events, bingo_tiles, bingo_teams,
# bingo_team_members, bingo_progress, bingo_bounties, whitelist, discord_webhooks
```

- [ ] **Step 17: Commit**

```bash
git add -A
git commit -m "feat: database schema — all tables via Drizzle ORM"
```

---

### Task 3: Auth — JWT + API Key Middleware

**Files:**
- Create: `clan-platform-api/src/auth/jwt.ts`
- Create: `clan-platform-api/src/auth/middleware.ts`
- Create: `clan-platform-api/src/lib/clan-lookup.ts`
- Create: `clan-platform-api/tests/setup.ts`
- Create: `clan-platform-api/tests/auth.test.ts`

- [ ] **Step 1: Create src/auth/jwt.ts**

```typescript
import { SignJWT, jwtVerify } from "jose";
import { env } from "../config.js";

const secret = new TextEncoder().encode(env.JWT_SECRET);

export interface JwtPayload {
  sub: string;        // user id
  discordId: string;
  username: string;
}

export async function signAccessToken(payload: JwtPayload): Promise<string> {
  return new SignJWT({ ...payload })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime("1h")
    .sign(secret);
}

export async function signRefreshToken(userId: string): Promise<string> {
  return new SignJWT({ sub: userId })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime("30d")
    .sign(secret);
}

export async function verifyToken(token: string): Promise<JwtPayload> {
  const { payload } = await jwtVerify(token, secret);
  return payload as unknown as JwtPayload;
}
```

- [ ] **Step 2: Create src/lib/clan-lookup.ts**

```typescript
import { eq } from "drizzle-orm";
import { db } from "../db/index.js";
import { clans } from "../db/schema/index.js";

export async function getClanBySlug(slug: string) {
  const results = await db.select().from(clans).where(eq(clans.slug, slug)).limit(1);
  return results[0] ?? null;
}
```

- [ ] **Step 3: Create src/auth/middleware.ts**

```typescript
import { FastifyRequest, FastifyReply } from "fastify";
import bcrypt from "bcrypt";
import { verifyToken, JwtPayload } from "./jwt.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

declare module "fastify" {
  interface FastifyRequest {
    user?: JwtPayload;
    clanId?: string;
  }
}

/**
 * Middleware for JWT-authenticated routes (website users).
 * Reads Bearer token from Authorization header.
 */
export async function requireAuth(request: FastifyRequest, reply: FastifyReply) {
  const header = request.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    return reply.status(401).send({ error: "Missing or invalid authorization header" });
  }

  try {
    const token = header.slice(7);
    request.user = await verifyToken(token);
  } catch {
    return reply.status(401).send({ error: "Invalid or expired token" });
  }
}

/**
 * Middleware for plugin API key auth.
 * Reads Bearer token from Authorization header, compares against clan's hashed API key.
 * Requires :slug route param to resolve the clan.
 */
export async function requireApiKey(request: FastifyRequest, reply: FastifyReply) {
  const header = request.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    return reply.status(401).send({ error: "Missing API key" });
  }

  const apiKey = header.slice(7);
  const slug = (request.params as { slug?: string }).slug;
  if (!slug) {
    return reply.status(400).send({ error: "Missing clan slug" });
  }

  const clan = await getClanBySlug(slug);
  if (!clan) {
    return reply.status(404).send({ error: "Clan not found" });
  }

  const valid = await bcrypt.compare(apiKey, clan.apiKeyHash);
  if (!valid) {
    return reply.status(401).send({ error: "Invalid API key" });
  }

  request.clanId = clan.id;
}
```

- [ ] **Step 4: Create tests/setup.ts**

```typescript
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import bcrypt from "bcrypt";
import * as schema from "../src/db/schema/index.js";

// Use a test database
const TEST_DB_URL = process.env.TEST_DATABASE_URL ?? "postgresql://postgres:postgres@localhost:5432/clan_platform_test";

const client = postgres(TEST_DB_URL);
export const testDb = drizzle(client, { schema });

export const TEST_API_KEY = "test-api-key-12345";
export let testClanId: string;
export let testPlayerId: string;

export async function seedTestData() {
  const apiKeyHash = await bcrypt.hash(TEST_API_KEY, 10);

  const [clan] = await testDb.insert(schema.clans).values({
    name: "Test Clan",
    slug: "test-clan",
    apiKeyHash,
  }).returning();
  testClanId = clan.id;

  const [player] = await testDb.insert(schema.players).values({
    clanId: clan.id,
    rsn: "TestPlayer",
  }).returning();
  testPlayerId = player.id;
}

export async function cleanTestData() {
  // Delete in reverse FK order
  await testDb.delete(schema.bingoProgress);
  await testDb.delete(schema.bingoBounties);
  await testDb.delete(schema.bingoTeamMembers);
  await testDb.delete(schema.bingoTeams);
  await testDb.delete(schema.bingoTiles);
  await testDb.delete(schema.bingoEvents);
  await testDb.delete(schema.eventLeaderboard);
  await testDb.delete(schema.events);
  await testDb.delete(schema.statSnapshots);
  await testDb.delete(schema.achievements);
  await testDb.delete(schema.collectionLogEntries);
  await testDb.delete(schema.personalBests);
  await testDb.delete(schema.drops);
  await testDb.delete(schema.whitelist);
  await testDb.delete(schema.discordWebhooks);
  await testDb.delete(schema.userRoles);
  await testDb.delete(schema.roles);
  await testDb.delete(schema.players);
  await testDb.delete(schema.users);
  await testDb.delete(schema.clans);
}

beforeAll(async () => {
  await cleanTestData();
  await seedTestData();
});

afterAll(async () => {
  await cleanTestData();
  await client.end();
});
```

- [ ] **Step 5: Create tests/auth.test.ts**

```typescript
import { describe, it, expect } from "vitest";
import { signAccessToken, verifyToken } from "../src/auth/jwt.js";

describe("JWT", () => {
  it("signs and verifies an access token", async () => {
    const payload = { sub: "user-123", discordId: "111222333", username: "TestUser" };
    const token = await signAccessToken(payload);

    expect(typeof token).toBe("string");
    expect(token.split(".")).toHaveLength(3);

    const verified = await verifyToken(token);
    expect(verified.sub).toBe("user-123");
    expect(verified.discordId).toBe("111222333");
    expect(verified.username).toBe("TestUser");
  });

  it("rejects an invalid token", async () => {
    await expect(verifyToken("invalid.token.here")).rejects.toThrow();
  });
});
```

- [ ] **Step 6: Run tests**

```bash
cd ~/dev/clan-platform-api
# Create test database
psql -U postgres -c "CREATE DATABASE clan_platform_test;"
npx drizzle-kit migrate  # run against test db with TEST_DATABASE_URL

npx vitest run tests/auth.test.ts
# Expected: 2 passing
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: JWT auth + API key middleware with tests"
```

---

### Task 4: Auth — Discord OAuth2

**Files:**
- Create: `clan-platform-api/src/auth/discord.ts`
- Create: `clan-platform-api/src/routes/auth.ts`
- Modify: `clan-platform-api/src/app.ts`

- [ ] **Step 1: Create src/auth/discord.ts**

```typescript
import { env } from "../config.js";

const DISCORD_API = "https://discord.com/api/v10";

interface DiscordTokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
  scope: string;
}

interface DiscordUser {
  id: string;
  username: string;
  avatar: string | null;
  global_name: string | null;
}

export async function exchangeCode(code: string): Promise<DiscordTokenResponse> {
  const params = new URLSearchParams({
    client_id: env.DISCORD_CLIENT_ID,
    client_secret: env.DISCORD_CLIENT_SECRET,
    grant_type: "authorization_code",
    code,
    redirect_uri: env.DISCORD_REDIRECT_URI,
  });

  const res = await fetch(`${DISCORD_API}/oauth2/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: params.toString(),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Discord token exchange failed: ${res.status} ${text}`);
  }

  return res.json() as Promise<DiscordTokenResponse>;
}

export async function fetchDiscordUser(accessToken: string): Promise<DiscordUser> {
  const res = await fetch(`${DISCORD_API}/users/@me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!res.ok) {
    throw new Error(`Discord user fetch failed: ${res.status}`);
  }

  return res.json() as Promise<DiscordUser>;
}

export function avatarUrl(user: DiscordUser): string | null {
  if (!user.avatar) return null;
  return `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png`;
}
```

- [ ] **Step 2: Create src/routes/auth.ts**

```typescript
import { FastifyInstance } from "fastify";
import { z } from "zod";
import { eq } from "drizzle-orm";
import { db } from "../db/index.js";
import { users } from "../db/schema/index.js";
import { exchangeCode, fetchDiscordUser, avatarUrl } from "../auth/discord.js";
import { signAccessToken, signRefreshToken, verifyToken } from "../auth/jwt.js";

const callbackSchema = z.object({ code: z.string().min(1) });
const refreshSchema = z.object({ refreshToken: z.string().min(1) });

export async function authRoutes(app: FastifyInstance) {
  // Discord OAuth2 callback
  app.post("/auth/discord/callback", async (request, reply) => {
    const parsed = callbackSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({ error: "Missing code" });
    }

    const tokens = await exchangeCode(parsed.data.code);
    const discordUser = await fetchDiscordUser(tokens.access_token);

    // Upsert user
    const existing = await db.select().from(users)
      .where(eq(users.discordId, discordUser.id)).limit(1);

    let user;
    if (existing.length > 0) {
      [user] = await db.update(users)
        .set({
          discordUsername: discordUser.global_name ?? discordUser.username,
          avatarUrl: avatarUrl(discordUser),
        })
        .where(eq(users.discordId, discordUser.id))
        .returning();
    } else {
      [user] = await db.insert(users).values({
        discordId: discordUser.id,
        discordUsername: discordUser.global_name ?? discordUser.username,
        avatarUrl: avatarUrl(discordUser),
      }).returning();
    }

    const accessToken = await signAccessToken({
      sub: user.id,
      discordId: user.discordId,
      username: user.discordUsername,
    });
    const refreshToken = await signRefreshToken(user.id);

    return { accessToken, refreshToken, user: { id: user.id, username: user.discordUsername, avatarUrl: user.avatarUrl } };
  });

  // Refresh token
  app.post("/auth/refresh", async (request, reply) => {
    const parsed = refreshSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({ error: "Missing refreshToken" });
    }

    try {
      const payload = await verifyToken(parsed.data.refreshToken);
      const [user] = await db.select().from(users).where(eq(users.id, payload.sub)).limit(1);
      if (!user) {
        return reply.status(401).send({ error: "User not found" });
      }

      const accessToken = await signAccessToken({
        sub: user.id,
        discordId: user.discordId,
        username: user.discordUsername,
      });
      const refreshToken = await signRefreshToken(user.id);

      return { accessToken, refreshToken };
    } catch {
      return reply.status(401).send({ error: "Invalid refresh token" });
    }
  });
}
```

- [ ] **Step 3: Register auth routes in app.ts**

Modify `src/app.ts`:

```typescript
import Fastify, { FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import { authRoutes } from "./routes/auth.js";

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true });

  await app.register(cors, { origin: true, credentials: true });

  app.get("/health", async () => ({ status: "ok" }));

  await app.register(authRoutes);

  return app;
}
```

- [ ] **Step 4: Verify build**

```bash
cd ~/dev/clan-platform-api
npx tsc --noEmit
# Expected: no errors
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: Discord OAuth2 login + token refresh routes"
```

---

### Task 5: Drops Routes

**Files:**
- Create: `clan-platform-api/src/routes/drops.ts`
- Modify: `clan-platform-api/src/app.ts`
- Create: `clan-platform-api/tests/drops.test.ts`

- [ ] **Step 1: Create tests/drops.test.ts**

```typescript
import { describe, it, expect } from "vitest";
import { testDb, testClanId, testPlayerId, TEST_API_KEY } from "./setup.js";
import { drops } from "../src/db/schema/index.js";
import { eq } from "drizzle-orm";

const BASE = "http://localhost:3001";

// These tests assume the server is running (integration tests).
// For unit-style tests we use Fastify's inject method instead.
// We'll test the route logic via direct DB + route handler calls.

import { buildApp } from "../src/app.js";

let app: Awaited<ReturnType<typeof buildApp>>;

beforeAll(async () => {
  app = await buildApp();
});

afterAll(async () => {
  await app.close();
});

describe("POST /clans/:slug/drops", () => {
  it("creates a drop with valid API key", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/drops",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: {
        rsn: "TestPlayer",
        itemName: "Twisted bow",
        itemId: 20997,
        value: 1200000000,
        monsterName: "Chambers of Xeric",
        killCount: 150,
      },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.id).toBeDefined();
    expect(body.itemName).toBe("Twisted bow");
  });

  it("rejects with invalid API key", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/drops",
      headers: { authorization: "Bearer wrong-key" },
      payload: {
        rsn: "TestPlayer",
        itemName: "Abyssal whip",
        value: 2000000,
        monsterName: "Abyssal demon",
      },
    });

    expect(res.statusCode).toBe(401);
  });

  it("rejects with missing fields", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/drops",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: { rsn: "TestPlayer" },
    });

    expect(res.statusCode).toBe(400);
  });
});

describe("GET /clans/:slug/drops", () => {
  it("returns paginated drops", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/drops?limit=10",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.drops)).toBe(true);
    expect(body.drops.length).toBeGreaterThanOrEqual(1);
    expect(body.drops[0].itemName).toBe("Twisted bow");
  });

  it("filters by player", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/drops?player=TestPlayer",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.drops.length).toBeGreaterThanOrEqual(1);
  });
});
```

- [ ] **Step 2: Create src/routes/drops.ts**

```typescript
import { FastifyInstance } from "fastify";
import { z } from "zod";
import { eq, and, desc, ilike } from "drizzle-orm";
import { db } from "../db/index.js";
import { drops, players } from "../db/schema/index.js";
import { requireApiKey } from "../auth/middleware.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

const createDropSchema = z.object({
  rsn: z.string().min(1).max(30),
  itemName: z.string().min(1).max(200),
  itemId: z.number().int().optional(),
  value: z.number().int().default(0),
  monsterName: z.string().min(1).max(200),
  killCount: z.number().int().optional(),
  screenshotUrl: z.string().max(500).optional(),
});

const querySchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(20),
  offset: z.coerce.number().int().min(0).default(0),
  player: z.string().optional(),
  item: z.string().optional(),
  monster: z.string().optional(),
});

export async function dropsRoutes(app: FastifyInstance) {
  // POST /clans/:slug/drops — plugin submits a drop
  app.post<{ Params: { slug: string } }>(
    "/clans/:slug/drops",
    { preHandler: [requireApiKey] },
    async (request, reply) => {
      const parsed = createDropSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: "Invalid payload", details: parsed.error.flatten() });
      }

      const { rsn, itemName, itemId, value, monsterName, killCount, screenshotUrl } = parsed.data;
      const clanId = request.clanId!;

      // Find or create player
      let [player] = await db.select().from(players)
        .where(and(eq(players.clanId, clanId), eq(players.rsn, rsn)))
        .limit(1);

      if (!player) {
        [player] = await db.insert(players).values({ clanId, rsn }).returning();
      }

      const [drop] = await db.insert(drops).values({
        clanId,
        playerId: player.id,
        itemName,
        itemId,
        value,
        monsterName,
        killCount,
        screenshotUrl,
      }).returning();

      return reply.status(201).send(drop);
    },
  );

  // GET /clans/:slug/drops — paginated drop feed
  app.get<{ Params: { slug: string } }>(
    "/clans/:slug/drops",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) {
        return reply.status(404).send({ error: "Clan not found" });
      }

      const query = querySchema.parse(request.query);

      const conditions = [eq(drops.clanId, clan.id)];

      if (query.player) {
        // Join to players to filter by RSN
        const [player] = await db.select().from(players)
          .where(and(eq(players.clanId, clan.id), eq(players.rsn, query.player)))
          .limit(1);
        if (player) {
          conditions.push(eq(drops.playerId, player.id));
        } else {
          return reply.send({ drops: [], total: 0 });
        }
      }

      if (query.item) {
        conditions.push(ilike(drops.itemName, `%${query.item}%`));
      }

      if (query.monster) {
        conditions.push(ilike(drops.monsterName, `%${query.monster}%`));
      }

      const results = await db.select({
        id: drops.id,
        itemName: drops.itemName,
        itemId: drops.itemId,
        value: drops.value,
        monsterName: drops.monsterName,
        killCount: drops.killCount,
        screenshotUrl: drops.screenshotUrl,
        createdAt: drops.createdAt,
        rsn: players.rsn,
      })
        .from(drops)
        .innerJoin(players, eq(drops.playerId, players.id))
        .where(and(...conditions))
        .orderBy(desc(drops.createdAt))
        .limit(query.limit)
        .offset(query.offset);

      return { drops: results };
    },
  );
}
```

- [ ] **Step 3: Register drops routes in app.ts**

Modify `src/app.ts` — add import and registration:

```typescript
import Fastify, { FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import { authRoutes } from "./routes/auth.js";
import { dropsRoutes } from "./routes/drops.js";

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true });

  await app.register(cors, { origin: true, credentials: true });

  app.get("/health", async () => ({ status: "ok" }));

  await app.register(authRoutes);
  await app.register(dropsRoutes);

  return app;
}
```

- [ ] **Step 4: Run tests**

```bash
npx vitest run tests/drops.test.ts
# Expected: 4 passing
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: drops CRUD routes with API key auth"
```

---

### Task 6: Collection Log Routes

**Files:**
- Create: `clan-platform-api/src/routes/collection-log.ts`
- Modify: `clan-platform-api/src/app.ts`
- Create: `clan-platform-api/tests/collection-log.test.ts`

- [ ] **Step 1: Create tests/collection-log.test.ts**

```typescript
import { describe, it, expect, beforeAll } from "vitest";
import { TEST_API_KEY } from "./setup.js";
import { buildApp } from "../src/app.js";

let app: Awaited<ReturnType<typeof buildApp>>;

beforeAll(async () => {
  app = await buildApp();
});

afterAll(async () => {
  await app.close();
});

describe("POST /clans/:slug/collection-log", () => {
  it("creates a collection log entry", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/collection-log",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: { rsn: "TestPlayer", itemName: "Pet snakeling", itemId: 12921 },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.itemName).toBe("Pet snakeling");
  });

  it("ignores duplicate entries", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/collection-log",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: { rsn: "TestPlayer", itemName: "Pet snakeling" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.existing).toBe(true);
  });
});

describe("POST /clans/:slug/collection-log/bulk", () => {
  it("bulk inserts multiple entries", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/collection-log/bulk",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: {
        rsn: "TestPlayer",
        items: [
          { itemName: "Tanzanite fang", itemId: 12922 },
          { itemName: "Magic fang", itemId: 12932 },
          { itemName: "Pet snakeling", itemId: 12921 },
        ],
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.inserted).toBe(2);
    expect(body.skipped).toBe(1);
  });
});

describe("GET /clans/:slug/collection-log", () => {
  it("returns leaderboard by unique count", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/collection-log",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.leaderboard)).toBe(true);
    expect(body.leaderboard[0].rsn).toBe("TestPlayer");
    expect(body.leaderboard[0].uniqueCount).toBeGreaterThanOrEqual(3);
  });
});

describe("GET /clans/:slug/collection-log/:rsn", () => {
  it("returns a player's entries", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/collection-log/TestPlayer",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.entries)).toBe(true);
    expect(body.entries.length).toBeGreaterThanOrEqual(3);
  });
});
```

- [ ] **Step 2: Create src/routes/collection-log.ts**

```typescript
import { FastifyInstance } from "fastify";
import { z } from "zod";
import { eq, and, desc, sql } from "drizzle-orm";
import { db } from "../db/index.js";
import { collectionLogEntries, players } from "../db/schema/index.js";
import { requireApiKey } from "../auth/middleware.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

const singleEntrySchema = z.object({
  rsn: z.string().min(1).max(30),
  itemName: z.string().min(1).max(200),
  itemId: z.number().int().optional(),
});

const bulkSchema = z.object({
  rsn: z.string().min(1).max(30),
  items: z.array(z.object({
    itemName: z.string().min(1).max(200),
    itemId: z.number().int().optional(),
  })).min(1).max(2000),
});

async function findOrCreatePlayer(clanId: string, rsn: string) {
  let [player] = await db.select().from(players)
    .where(and(eq(players.clanId, clanId), eq(players.rsn, rsn)))
    .limit(1);
  if (!player) {
    [player] = await db.insert(players).values({ clanId, rsn }).returning();
  }
  return player;
}

export async function collectionLogRoutes(app: FastifyInstance) {
  // POST /clans/:slug/collection-log — single entry
  app.post<{ Params: { slug: string } }>(
    "/clans/:slug/collection-log",
    { preHandler: [requireApiKey] },
    async (request, reply) => {
      const parsed = singleEntrySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: "Invalid payload", details: parsed.error.flatten() });
      }

      const clanId = request.clanId!;
      const { rsn, itemName, itemId } = parsed.data;
      const player = await findOrCreatePlayer(clanId, rsn);

      // Check for existing entry (unique constraint: clan + player + item)
      const [existing] = await db.select().from(collectionLogEntries)
        .where(and(
          eq(collectionLogEntries.clanId, clanId),
          eq(collectionLogEntries.playerId, player.id),
          eq(collectionLogEntries.itemName, itemName),
        )).limit(1);

      if (existing) {
        return reply.status(200).send({ ...existing, existing: true });
      }

      const [entry] = await db.insert(collectionLogEntries).values({
        clanId,
        playerId: player.id,
        itemName,
        itemId,
      }).returning();

      return reply.status(201).send(entry);
    },
  );

  // POST /clans/:slug/collection-log/bulk — bulk sync from plugin
  app.post<{ Params: { slug: string } }>(
    "/clans/:slug/collection-log/bulk",
    { preHandler: [requireApiKey] },
    async (request, reply) => {
      const parsed = bulkSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: "Invalid payload", details: parsed.error.flatten() });
      }

      const clanId = request.clanId!;
      const { rsn, items } = parsed.data;
      const player = await findOrCreatePlayer(clanId, rsn);

      let inserted = 0;
      let skipped = 0;

      for (const item of items) {
        try {
          await db.insert(collectionLogEntries).values({
            clanId,
            playerId: player.id,
            itemName: item.itemName,
            itemId: item.itemId,
          }).onConflictDoNothing();
          inserted++;
        } catch {
          skipped++;
        }
      }

      // Recount — onConflictDoNothing doesn't tell us which were skipped
      const [{ count }] = await db.select({ count: sql<number>`count(*)` })
        .from(collectionLogEntries)
        .where(and(eq(collectionLogEntries.clanId, clanId), eq(collectionLogEntries.playerId, player.id)));

      const actualInserted = items.length - skipped;
      const actualSkipped = items.length - actualInserted;

      return { inserted: actualInserted, skipped: actualSkipped, total: Number(count) };
    },
  );

  // GET /clans/:slug/collection-log — leaderboard
  app.get<{ Params: { slug: string } }>(
    "/clans/:slug/collection-log",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const leaderboard = await db.select({
        rsn: players.rsn,
        uniqueCount: sql<number>`count(*)`.as("unique_count"),
      })
        .from(collectionLogEntries)
        .innerJoin(players, eq(collectionLogEntries.playerId, players.id))
        .where(eq(collectionLogEntries.clanId, clan.id))
        .groupBy(players.rsn)
        .orderBy(sql`count(*) desc`)
        .limit(50);

      return { leaderboard };
    },
  );

  // GET /clans/:slug/collection-log/:rsn — player's entries
  app.get<{ Params: { slug: string; rsn: string } }>(
    "/clans/:slug/collection-log/:rsn",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const [player] = await db.select().from(players)
        .where(and(eq(players.clanId, clan.id), eq(players.rsn, request.params.rsn)))
        .limit(1);

      if (!player) return reply.status(404).send({ error: "Player not found" });

      const entries = await db.select({
        id: collectionLogEntries.id,
        itemName: collectionLogEntries.itemName,
        itemId: collectionLogEntries.itemId,
        obtainedAt: collectionLogEntries.obtainedAt,
      })
        .from(collectionLogEntries)
        .where(and(eq(collectionLogEntries.clanId, clan.id), eq(collectionLogEntries.playerId, player.id)))
        .orderBy(desc(collectionLogEntries.obtainedAt));

      return { entries, total: entries.length };
    },
  );
}
```

- [ ] **Step 3: Register in app.ts**

Add to `src/app.ts` imports and registration:

```typescript
import { collectionLogRoutes } from "./routes/collection-log.js";
```

Add inside `buildApp()`:

```typescript
await app.register(collectionLogRoutes);
```

- [ ] **Step 4: Run tests**

```bash
npx vitest run tests/collection-log.test.ts
# Expected: 4 passing
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: collection log routes — single entry, bulk sync, leaderboard"
```

---

### Task 7: Personal Bests Routes

**Files:**
- Create: `clan-platform-api/src/routes/personal-bests.ts`
- Modify: `clan-platform-api/src/app.ts`
- Create: `clan-platform-api/tests/personal-bests.test.ts`

- [ ] **Step 1: Create tests/personal-bests.test.ts**

```typescript
import { describe, it, expect } from "vitest";
import { TEST_API_KEY } from "./setup.js";
import { buildApp } from "../src/app.js";

let app: Awaited<ReturnType<typeof buildApp>>;

beforeAll(async () => {
  app = await buildApp();
});

afterAll(async () => {
  await app.close();
});

describe("POST /clans/:slug/pbs", () => {
  it("creates a personal best", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/pbs",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: {
        rsn: "TestPlayer",
        bossKey: "zulrah",
        teamSize: 1,
        timeMs: 54200,
      },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.bossKey).toBe("zulrah");
    expect(body.timeMs).toBe(54200);
  });

  it("records a faster time for the same boss", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/pbs",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: {
        rsn: "TestPlayer",
        bossKey: "zulrah",
        teamSize: 1,
        timeMs: 48300,
      },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.timeMs).toBe(48300);
  });
});

describe("GET /clans/:slug/pbs", () => {
  it("returns leaderboard grouped by boss", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/pbs",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.leaderboard)).toBe(true);
  });
});

describe("GET /clans/:slug/pbs/:boss", () => {
  it("returns rankings for a specific boss", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/pbs/zulrah",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.rankings)).toBe(true);
    expect(body.rankings.length).toBeGreaterThanOrEqual(1);
    // Fastest time should be first
    expect(body.rankings[0].timeMs).toBe(48300);
  });
});
```

- [ ] **Step 2: Create src/routes/personal-bests.ts**

```typescript
import { FastifyInstance } from "fastify";
import { z } from "zod";
import { eq, and, asc, sql } from "drizzle-orm";
import { db } from "../db/index.js";
import { personalBests, players } from "../db/schema/index.js";
import { requireApiKey } from "../auth/middleware.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

const createPbSchema = z.object({
  rsn: z.string().min(1).max(30),
  bossKey: z.string().min(1).max(100),
  teamSize: z.number().int().min(1).default(1),
  timeMs: z.number().int().positive(),
});

export async function personalBestsRoutes(app: FastifyInstance) {
  // POST /clans/:slug/pbs — plugin submits a PB
  app.post<{ Params: { slug: string } }>(
    "/clans/:slug/pbs",
    { preHandler: [requireApiKey] },
    async (request, reply) => {
      const parsed = createPbSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: "Invalid payload", details: parsed.error.flatten() });
      }

      const clanId = request.clanId!;
      const { rsn, bossKey, teamSize, timeMs } = parsed.data;

      let [player] = await db.select().from(players)
        .where(and(eq(players.clanId, clanId), eq(players.rsn, rsn)))
        .limit(1);
      if (!player) {
        [player] = await db.insert(players).values({ clanId, rsn }).returning();
      }

      const [pb] = await db.insert(personalBests).values({
        clanId,
        playerId: player.id,
        bossKey,
        teamSize,
        timeMs,
      }).returning();

      return reply.status(201).send(pb);
    },
  );

  // GET /clans/:slug/pbs — best time per boss per player
  app.get<{ Params: { slug: string } }>(
    "/clans/:slug/pbs",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      // For each boss+teamSize combo, get the fastest time with player name
      const leaderboard = await db.select({
        bossKey: personalBests.bossKey,
        teamSize: personalBests.teamSize,
        rsn: players.rsn,
        timeMs: sql<number>`min(${personalBests.timeMs})`.as("best_time"),
      })
        .from(personalBests)
        .innerJoin(players, eq(personalBests.playerId, players.id))
        .where(eq(personalBests.clanId, clan.id))
        .groupBy(personalBests.bossKey, personalBests.teamSize, players.rsn)
        .orderBy(personalBests.bossKey, asc(sql`min(${personalBests.timeMs})`));

      return { leaderboard };
    },
  );

  // GET /clans/:slug/pbs/:boss — rankings for a specific boss
  app.get<{ Params: { slug: string; boss: string } }>(
    "/clans/:slug/pbs/:boss",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const teamSize = Number((request.query as { teamSize?: string }).teamSize) || 1;

      // Best time per player for this boss/teamSize
      const rankings = await db.select({
        rsn: players.rsn,
        timeMs: sql<number>`min(${personalBests.timeMs})`.as("best_time"),
        createdAt: sql<string>`max(${personalBests.createdAt})`.as("latest"),
      })
        .from(personalBests)
        .innerJoin(players, eq(personalBests.playerId, players.id))
        .where(and(
          eq(personalBests.clanId, clan.id),
          eq(personalBests.bossKey, request.params.boss),
          eq(personalBests.teamSize, teamSize),
        ))
        .groupBy(players.rsn)
        .orderBy(asc(sql`min(${personalBests.timeMs})`))
        .limit(50);

      return { rankings };
    },
  );
}
```

- [ ] **Step 3: Register in app.ts**

Add to `src/app.ts`:

```typescript
import { personalBestsRoutes } from "./routes/personal-bests.js";
```

```typescript
await app.register(personalBestsRoutes);
```

- [ ] **Step 4: Run tests**

```bash
npx vitest run tests/personal-bests.test.ts
# Expected: 4 passing
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: personal bests routes — submit PB, leaderboard, per-boss rankings"
```

---

### Task 8: Players + Achievements Routes

**Files:**
- Create: `clan-platform-api/src/routes/players.ts`
- Create: `clan-platform-api/src/routes/achievements.ts`
- Modify: `clan-platform-api/src/app.ts`
- Create: `clan-platform-api/tests/players.test.ts`
- Create: `clan-platform-api/tests/achievements.test.ts`

- [ ] **Step 1: Create src/routes/players.ts**

```typescript
import { FastifyInstance } from "fastify";
import { eq, and, desc, sql } from "drizzle-orm";
import { db } from "../db/index.js";
import { players, drops, personalBests, collectionLogEntries, achievements } from "../db/schema/index.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

export async function playersRoutes(app: FastifyInstance) {
  // GET /clans/:slug/players — clan roster
  app.get<{ Params: { slug: string } }>(
    "/clans/:slug/players",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const roster = await db.select({
        id: players.id,
        rsn: players.rsn,
        verified: players.verified,
        joinedAt: players.joinedAt,
      })
        .from(players)
        .where(eq(players.clanId, clan.id))
        .orderBy(players.rsn);

      return { players: roster };
    },
  );

  // GET /clans/:slug/players/:rsn — full player profile
  app.get<{ Params: { slug: string; rsn: string } }>(
    "/clans/:slug/players/:rsn",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const [player] = await db.select().from(players)
        .where(and(eq(players.clanId, clan.id), eq(players.rsn, request.params.rsn)))
        .limit(1);

      if (!player) return reply.status(404).send({ error: "Player not found" });

      // Recent drops
      const recentDrops = await db.select({
        itemName: drops.itemName,
        value: drops.value,
        monsterName: drops.monsterName,
        createdAt: drops.createdAt,
      })
        .from(drops)
        .where(eq(drops.playerId, player.id))
        .orderBy(desc(drops.createdAt))
        .limit(10);

      // Collection log count
      const [clogCount] = await db.select({ count: sql<number>`count(*)` })
        .from(collectionLogEntries)
        .where(eq(collectionLogEntries.playerId, player.id));

      // Best PBs
      const pbs = await db.select({
        bossKey: personalBests.bossKey,
        teamSize: personalBests.teamSize,
        timeMs: sql<number>`min(${personalBests.timeMs})`.as("best_time"),
      })
        .from(personalBests)
        .where(eq(personalBests.playerId, player.id))
        .groupBy(personalBests.bossKey, personalBests.teamSize);

      // Recent achievements
      const recentAchievements = await db.select({
        type: achievements.type,
        detail: achievements.detail,
        createdAt: achievements.createdAt,
      })
        .from(achievements)
        .where(eq(achievements.playerId, player.id))
        .orderBy(desc(achievements.createdAt))
        .limit(10);

      return {
        player: {
          id: player.id,
          rsn: player.rsn,
          verified: player.verified,
          joinedAt: player.joinedAt,
        },
        recentDrops,
        collectionLogCount: Number(clogCount.count),
        personalBests: pbs,
        recentAchievements,
      };
    },
  );
}
```

- [ ] **Step 2: Create src/routes/achievements.ts**

```typescript
import { FastifyInstance } from "fastify";
import { z } from "zod";
import { eq, and, desc } from "drizzle-orm";
import { db } from "../db/index.js";
import { achievements, players } from "../db/schema/index.js";
import { requireApiKey } from "../auth/middleware.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

const createAchievementSchema = z.object({
  rsn: z.string().min(1).max(30),
  type: z.enum(["pet", "diary", "quest", "combat_achievement", "99", "clue"]),
  detail: z.string().min(1).max(200),
});

const querySchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(20),
  offset: z.coerce.number().int().min(0).default(0),
  type: z.string().optional(),
  player: z.string().optional(),
});

export async function achievementsRoutes(app: FastifyInstance) {
  // POST /clans/:slug/achievements — plugin submits achievement
  app.post<{ Params: { slug: string } }>(
    "/clans/:slug/achievements",
    { preHandler: [requireApiKey] },
    async (request, reply) => {
      const parsed = createAchievementSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: "Invalid payload", details: parsed.error.flatten() });
      }

      const clanId = request.clanId!;
      const { rsn, type, detail } = parsed.data;

      let [player] = await db.select().from(players)
        .where(and(eq(players.clanId, clanId), eq(players.rsn, rsn)))
        .limit(1);
      if (!player) {
        [player] = await db.insert(players).values({ clanId, rsn }).returning();
      }

      const [achievement] = await db.insert(achievements).values({
        clanId,
        playerId: player.id,
        type,
        detail,
      }).returning();

      return reply.status(201).send(achievement);
    },
  );

  // GET /clans/:slug/achievements — achievement feed
  app.get<{ Params: { slug: string } }>(
    "/clans/:slug/achievements",
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      const query = querySchema.parse(request.query);

      const conditions = [eq(achievements.clanId, clan.id)];

      if (query.type) {
        conditions.push(eq(achievements.type, query.type));
      }

      if (query.player) {
        const [player] = await db.select().from(players)
          .where(and(eq(players.clanId, clan.id), eq(players.rsn, query.player)))
          .limit(1);
        if (player) {
          conditions.push(eq(achievements.playerId, player.id));
        } else {
          return { achievements: [] };
        }
      }

      const results = await db.select({
        id: achievements.id,
        type: achievements.type,
        detail: achievements.detail,
        createdAt: achievements.createdAt,
        rsn: players.rsn,
      })
        .from(achievements)
        .innerJoin(players, eq(achievements.playerId, players.id))
        .where(and(...conditions))
        .orderBy(desc(achievements.createdAt))
        .limit(query.limit)
        .offset(query.offset);

      return { achievements: results };
    },
  );
}
```

- [ ] **Step 3: Create tests/players.test.ts**

```typescript
import { describe, it, expect } from "vitest";
import { buildApp } from "../src/app.js";

let app: Awaited<ReturnType<typeof buildApp>>;

beforeAll(async () => {
  app = await buildApp();
});

afterAll(async () => {
  await app.close();
});

describe("GET /clans/:slug/players", () => {
  it("returns clan roster", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/players",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.players)).toBe(true);
    expect(body.players.length).toBeGreaterThanOrEqual(1);
    expect(body.players[0].rsn).toBeDefined();
  });

  it("returns 404 for unknown clan", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/nonexistent/players",
    });
    expect(res.statusCode).toBe(404);
  });
});

describe("GET /clans/:slug/players/:rsn", () => {
  it("returns full player profile", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/players/TestPlayer",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.player.rsn).toBe("TestPlayer");
    expect(Array.isArray(body.recentDrops)).toBe(true);
    expect(typeof body.collectionLogCount).toBe("number");
    expect(Array.isArray(body.personalBests)).toBe(true);
    expect(Array.isArray(body.recentAchievements)).toBe(true);
  });
});
```

- [ ] **Step 4: Create tests/achievements.test.ts**

```typescript
import { describe, it, expect } from "vitest";
import { TEST_API_KEY } from "./setup.js";
import { buildApp } from "../src/app.js";

let app: Awaited<ReturnType<typeof buildApp>>;

beforeAll(async () => {
  app = await buildApp();
});

afterAll(async () => {
  await app.close();
});

describe("POST /clans/:slug/achievements", () => {
  it("creates a pet achievement", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/achievements",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: { rsn: "TestPlayer", type: "pet", detail: "Vorki" },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.type).toBe("pet");
    expect(body.detail).toBe("Vorki");
  });

  it("creates a 99 achievement", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/clans/test-clan/achievements",
      headers: { authorization: `Bearer ${TEST_API_KEY}` },
      payload: { rsn: "TestPlayer", type: "99", detail: "Mining" },
    });

    expect(res.statusCode).toBe(201);
  });
});

describe("GET /clans/:slug/achievements", () => {
  it("returns achievement feed", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/achievements",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.achievements.length).toBeGreaterThanOrEqual(2);
  });

  it("filters by type", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/clans/test-clan/achievements?type=pet",
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.achievements.length).toBe(1);
    expect(body.achievements[0].detail).toBe("Vorki");
  });
});
```

- [ ] **Step 5: Register routes in app.ts**

Final `src/app.ts`:

```typescript
import Fastify, { FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import { authRoutes } from "./routes/auth.js";
import { dropsRoutes } from "./routes/drops.js";
import { collectionLogRoutes } from "./routes/collection-log.js";
import { personalBestsRoutes } from "./routes/personal-bests.js";
import { playersRoutes } from "./routes/players.js";
import { achievementsRoutes } from "./routes/achievements.js";

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true });

  await app.register(cors, { origin: true, credentials: true });

  app.get("/health", async () => ({ status: "ok" }));

  await app.register(authRoutes);
  await app.register(dropsRoutes);
  await app.register(collectionLogRoutes);
  await app.register(personalBestsRoutes);
  await app.register(playersRoutes);
  await app.register(achievementsRoutes);

  return app;
}
```

- [ ] **Step 6: Run all tests**

```bash
npx vitest run
# Expected: all tests passing
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: players roster/profile + achievements routes with tests"
```

---

### Task 9: Plugin — PlatformApiService

**Files:**
- Create: `clan-management-plugin/src/main/java/com/droplogger/PlatformApiService.java`

- [ ] **Step 1: Create PlatformApiService.java**

Create `src/main/java/com/droplogger/PlatformApiService.java`:

```java
package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class PlatformApiService
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public PlatformApiService(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient.newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = gson;
    }

    /**
     * Submit a drop to the platform API.
     */
    public void submitDrop(String baseUrl, String apiKey, String clanSlug, DropEntry drop)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", drop.getPlayerName());
        payload.addProperty("itemName", drop.getItemName());
        payload.addProperty("value", drop.getValue());
        payload.addProperty("monsterName", drop.getMonsterName());
        if (drop.getKillCount() > 0)
        {
            payload.addProperty("killCount", drop.getKillCount());
        }

        postAsync(baseUrl + "/clans/" + clanSlug + "/drops", apiKey, payload, "Platform drop");
    }

    /**
     * Submit a personal best to the platform API.
     */
    public void submitPb(String baseUrl, String apiKey, String clanSlug,
                         String rsn, String bossKey, int teamSize, int timeMs)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);

        postAsync(baseUrl + "/clans/" + clanSlug + "/pbs", apiKey, payload, "Platform PB");
    }

    /**
     * Submit a single collection log entry to the platform API.
     */
    public void submitCollectionLogEntry(String baseUrl, String apiKey, String clanSlug,
                                          String rsn, String itemName)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        payload.addProperty("itemName", itemName);

        postAsync(baseUrl + "/clans/" + clanSlug + "/collection-log", apiKey, payload, "Platform clog");
    }

    /**
     * Bulk sync collection log entries to the platform API.
     */
    public void bulkSyncCollectionLog(String baseUrl, String apiKey, String clanSlug,
                                       String rsn, List<String> itemNames,
                                       Callback callback)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);

        JsonArray items = new JsonArray();
        for (String name : itemNames)
        {
            JsonObject item = new JsonObject();
            item.addProperty("itemName", name);
            items.add(item);
        }
        payload.add("items", items);

        String url = baseUrl + "/clans/" + clanSlug + "/collection-log/bulk";
        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .post(body)
            .build();

        httpClient.newCall(request).enqueue(callback);
    }

    /**
     * Submit an achievement to the platform API.
     */
    public void submitAchievement(String baseUrl, String apiKey, String clanSlug,
                                   String rsn, String type, String detail)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        payload.addProperty("type", type);
        payload.addProperty("detail", detail);

        postAsync(baseUrl + "/clans/" + clanSlug + "/achievements", apiKey, payload, "Platform achievement");
    }

    private void postAsync(String url, String apiKey, JsonObject payload, String label)
    {
        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .post(body)
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.error("Failed to submit {}", label, e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                response.close();
                if (response.isSuccessful())
                {
                    log.debug("{} submitted successfully", label);
                }
                else
                {
                    log.error("{} submit failed with status: {}", label, response.code());
                }
            }
        });
    }
}
```

- [ ] **Step 2: Verify build**

```bash
cd ~/dev/OSRS_Bingo/drop-logger-plugin
./gradlew build
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/PlatformApiService.java
git commit -m "feat: add PlatformApiService for platform API backend"
```

---

### Task 10: Plugin — Config + Dual-Mode Wiring

**Files:**
- Modify: `clan-management-plugin/src/main/java/com/droplogger/ClanManagementConfig.java`
- Modify: `clan-management-plugin/src/main/java/com/droplogger/ClanManagementPlugin.java`

- [ ] **Step 1: Add platform config fields to ClanManagementConfig.java**

Add after the `bingoSection` section (after line 204):

```java
    // ── Platform ──

    @ConfigSection(
        name = "Platform",
        description = "Connect to the clan platform API for advanced features (optional)",
        position = 8
    )
    String platformSection = "platform";

    @ConfigItem(
        keyName = "platformApiUrl",
        name = "Platform API URL",
        description = "Base URL of your clan platform API (e.g. https://api.yourclan.gg). Leave blank to use Google Sheets only.",
        section = platformSection,
        position = 0
    )
    default String platformApiUrl() { return ""; }

    @ConfigItem(
        keyName = "platformApiKey",
        name = "Platform API Key",
        description = "API key for your clan on the platform",
        section = platformSection,
        position = 1,
        secret = true
    )
    default String platformApiKey() { return ""; }

    @ConfigItem(
        keyName = "platformClanSlug",
        name = "Clan Slug",
        description = "Your clan's URL slug on the platform (e.g. 'solus')",
        section = platformSection,
        position = 2
    )
    default String platformClanSlug() { return ""; }
```

- [ ] **Step 2: Add PlatformApiService injection to ClanManagementPlugin.java**

Add the `@Inject` field alongside the other service injections at the top of the class:

```java
    @Inject
    private PlatformApiService platformApiService;
```

- [ ] **Step 3: Add platform helper method to ClanManagementPlugin.java**

Add a helper method to check if platform mode is configured:

```java
    private boolean isPlatformConfigured()
    {
        String url = config.platformApiUrl();
        String key = config.platformApiKey();
        String slug = config.platformClanSlug();
        return url != null && !url.isEmpty()
            && key != null && !key.isEmpty()
            && slug != null && !slug.isEmpty();
    }
```

- [ ] **Step 4: Wire platform drop submission**

Find the `handleDropLogging()` method in `ClanManagementPlugin.java`. After the existing Google Sheets `logClanDrop()` call AND after the Discord webhook call, add:

```java
        // Platform API (dual mode)
        if (isPlatformConfigured())
        {
            platformApiService.submitDrop(
                config.platformApiUrl(),
                config.platformApiKey(),
                config.platformClanSlug(),
                drop
            );
        }
```

- [ ] **Step 5: Wire platform PB submission**

Find where `hiscoreService.checkAndSubmitPbV2()` is called (or the PB submission logic). After the existing Google Sheets PB submit, add:

```java
            // Platform API (dual mode)
            if (isPlatformConfigured())
            {
                int timeMs = (int) (timeSeconds * 1000);
                platformApiService.submitPb(
                    config.platformApiUrl(),
                    config.platformApiKey(),
                    config.platformClanSlug(),
                    rsn,
                    categoryKey,
                    partySize,
                    timeMs
                );
            }
```

- [ ] **Step 6: Wire platform collection log submission**

Find `handleCollectionLogEntry()`. After the existing `logClanDrop()` call with type "collection_log", add:

```java
        // Platform API (dual mode)
        if (isPlatformConfigured())
        {
            platformApiService.submitCollectionLogEntry(
                config.platformApiUrl(),
                config.platformApiKey(),
                config.platformClanSlug(),
                playerName,
                itemName
            );
        }
```

- [ ] **Step 7: Verify build**

```bash
cd ~/dev/OSRS_Bingo/drop-logger-plugin
./gradlew build
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/droplogger/ClanManagementConfig.java src/main/java/com/droplogger/ClanManagementPlugin.java
git commit -m "feat: dual-mode backend — plugin sends to platform API when configured"
```

---

### Task 11: Integration Verification

**Files:** None (testing only)

- [ ] **Step 1: Start PostgreSQL and create database**

```bash
# Ensure PostgreSQL is running on your Proxmox box or locally
psql -U postgres -c "CREATE DATABASE clan_platform;" 2>/dev/null || true
```

- [ ] **Step 2: Configure and start the API**

```bash
cd ~/dev/clan-platform-api
cp .env.example .env
# Edit .env with your PostgreSQL connection string and a JWT secret

npx drizzle-kit generate
npx drizzle-kit migrate
npx tsx src/index.ts
# Should print: Server listening on port 3001
```

- [ ] **Step 3: Seed a test clan via API**

```bash
# Create a clan directly in the DB for testing
# (Admin clan creation route is Phase 5 — for now, seed via psql)
HASH=$(node -e "const b=require('bcrypt');b.hash('my-test-key',10).then(h=>console.log(h))")
psql -U postgres -d clan_platform -c "INSERT INTO clans (name, slug, api_key_hash) VALUES ('Solus', 'solus', '$HASH');"
```

- [ ] **Step 4: Test drop submission via curl**

```bash
curl -X POST http://localhost:3001/clans/solus/drops \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer my-test-key" \
  -d '{"rsn":"TestPlayer","itemName":"Twisted bow","value":1200000000,"monsterName":"Chambers of Xeric","killCount":150}'

# Expected: 201 with drop JSON
```

- [ ] **Step 5: Test collection log bulk sync via curl**

```bash
curl -X POST http://localhost:3001/clans/solus/collection-log/bulk \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer my-test-key" \
  -d '{"rsn":"TestPlayer","items":[{"itemName":"Pet snakeling"},{"itemName":"Tanzanite fang"},{"itemName":"Magic fang"}]}'

# Expected: 200 with {"inserted":3,"skipped":0,"total":3}
```

- [ ] **Step 6: Test drop feed via curl**

```bash
curl http://localhost:3001/clans/solus/drops
# Expected: 200 with drops array containing Twisted bow

curl http://localhost:3001/clans/solus/collection-log
# Expected: 200 with leaderboard array

curl http://localhost:3001/clans/solus/players/TestPlayer
# Expected: 200 with full player profile including drops, clog count, etc.
```

- [ ] **Step 7: Run full API test suite**

```bash
cd ~/dev/clan-platform-api
npx vitest run
# Expected: all tests passing
```

- [ ] **Step 8: Build the plugin**

```bash
cd ~/dev/OSRS_Bingo/drop-logger-plugin
./gradlew build
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 9: Commit any fixes**

If any issues were found and fixed during verification:

```bash
git add -A
git commit -m "fix: integration test fixes"
```

---

## Summary

After completing all 11 tasks, you will have:

**`clan-platform-api` repo:**
- Fastify + TypeScript project with Drizzle ORM
- PostgreSQL with 20 tables (full schema from spec)
- Discord OAuth2 login + JWT tokens
- API key auth for plugin
- CRUD routes: drops, collection log (single + bulk), personal bests, players, achievements
- Test suite with Vitest

**`clan-management-plugin` repo:**
- `PlatformApiService.java` — HTTP client for the platform API
- Config fields: `platformApiUrl`, `platformApiKey`, `platformClanSlug`
- Dual-mode dispatch: drops, PBs, and collection log entries go to both Google Sheets and platform API when configured
- No breaking changes to existing Google Sheets functionality

**Next phase:** Phase 2 (Website MVP) will build the Next.js frontend that consumes these API routes.
