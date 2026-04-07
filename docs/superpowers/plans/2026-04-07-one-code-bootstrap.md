# One-Code Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the four separate platform config fields with a single base64-encoded clan code, and add a bootstrap API endpoint that returns all plugin config in one call.

**Architecture:** The clan code encodes `apiUrl|slug|apiKey` as base64. The plugin decodes it on startup, caches the three values, and calls `/clans/:slug/bootstrap` to fetch shared settings (webhooks, min drop value, active event, etc.). The API also gets a `POST /clans/:slug/generate-code` endpoint for admins to create codes.

**Tech Stack:** Fastify API (TypeScript, Drizzle ORM), RuneLite plugin (Java), base64 encoding

**Spec:** `docs/superpowers/specs/2026-04-07-one-code-admin-center-design.md` — Section 1

---

## File Structure

### API (`C:\dev\clan-platform-api\src\`)

| File | Responsibility |
|------|---------------|
| `routes/bootstrap.ts` | **Create** — `GET /clans/:slug/bootstrap` and `POST /clans/:slug/generate-code` |
| `app.ts` | **Modify** — register bootstrap routes |

### Plugin (`C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\`)

| File | Responsibility |
|------|---------------|
| `ClanCodeUtil.java` | **Create** — decode/encode base64 clan codes |
| `ClanManagementConfig.java` | **Modify** — add `clanCode` field, keep legacy fields hidden |
| `ClanManagementPlugin.java` | **Modify** — update `isPlatformConfigured()` and add helper methods for decoded values |

---

### Task 1: Bootstrap API Route

**Files:**
- Create: `C:\dev\clan-platform-api\src\routes\bootstrap.ts`
- Modify: `C:\dev\clan-platform-api\src\app.ts`

- [ ] **Step 1: Create the bootstrap routes file**

```typescript
// C:\dev\clan-platform-api\src\routes\bootstrap.ts
import { FastifyInstance } from "fastify";
import { eq } from "drizzle-orm";
import { db } from "../db/index.js";
import { clans, discordWebhooks } from "../db/schema/index.js";
import { requireApiKey } from "../auth/middleware.js";
import { getClanBySlug } from "../lib/clan-lookup.js";

export async function bootstrapRoutes(app: FastifyInstance) {
  // GET /clans/:slug/bootstrap — plugin fetches all config in one call
  app.get<{ Params: { slug: string } }>(
    "/clans/:slug/bootstrap",
    { preHandler: [requireApiKey] },
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      // Fetch discord webhooks
      const webhooks = await db.select()
        .from(discordWebhooks)
        .where(eq(discordWebhooks.clanId, clan.id));

      const webhookMap: Record<string, string> = {};
      for (const wh of webhooks) {
        webhookMap[wh.type] = wh.webhookUrl;
      }

      const settings = (clan.settings ?? {}) as Record<string, unknown>;

      return {
        clanName: clan.name,
        settings: {
          minDropValue: settings.minDropValue ?? 100000,
          ...settings,
        },
        discordWebhooks: webhookMap,
        activeEvent: settings.eventType ? {
          type: settings.eventType,
          metric: settings.eventMetric,
          displayName: settings.eventDisplayName,
          startTime: settings.eventStartTime,
          endTime: settings.eventEndTime,
        } : null,
      };
    },
  );

  // POST /clans/:slug/generate-code — admin generates a clan code
  // For now, uses API key auth (admin JWT auth added in Phase 2)
  app.post<{ Params: { slug: string } }>(
    "/clans/:slug/generate-code",
    { preHandler: [requireApiKey] },
    async (request, reply) => {
      const clan = await getClanBySlug(request.params.slug);
      if (!clan) return reply.status(404).send({ error: "Clan not found" });

      // The API key is in the Authorization header — we need the raw key
      // to encode it. For now, require it in the body.
      const body = request.body as { apiKey?: string };
      if (!body.apiKey) {
        return reply.status(400).send({ error: "apiKey required in body to encode into clan code" });
      }

      const apiUrl = `${request.protocol}://${request.hostname}`;
      const raw = `${apiUrl}|${clan.slug}|${body.apiKey}`;
      const code = Buffer.from(raw).toString("base64");

      return { code };
    },
  );
}
```

- [ ] **Step 2: Register bootstrap routes in app.ts**

In `C:\dev\clan-platform-api\src\app.ts`, add:

```typescript
import { bootstrapRoutes } from "./routes/bootstrap.js";
```

And in the `buildApp` function, add after the snapshotAdminRoutes registration:

```typescript
  await app.register(bootstrapRoutes);
```

- [ ] **Step 3: Verify API builds**

```bash
cd C:\dev\clan-platform-api && npm run build
```

Expected: `tsc` completes with no errors.

- [ ] **Step 4: Commit**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin
git -C C:\dev\clan-platform-api add src/routes/bootstrap.ts src/app.ts
git -C C:\dev\clan-platform-api commit -m "feat: add bootstrap and generate-code API endpoints"
```

---

### Task 2: ClanCodeUtil — Base64 Encode/Decode

**Files:**
- Create: `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\ClanCodeUtil.java`

- [ ] **Step 1: Create the ClanCodeUtil class**

```java
// C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\ClanCodeUtil.java
package com.droplogger;

import java.util.Base64;

/**
 * Encodes and decodes clan codes.
 * Format: base64(apiUrl|slug|apiKey)
 */
public class ClanCodeUtil
{
    private ClanCodeUtil() {}

    public static String[] decode(String clanCode)
    {
        if (clanCode == null || clanCode.trim().isEmpty())
        {
            return null;
        }

        try
        {
            String decoded = new String(Base64.getDecoder().decode(clanCode.trim()));
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3)
            {
                return null;
            }

            String apiUrl = parts[0].trim();
            String slug = parts[1].trim();
            String apiKey = parts[2].trim();

            if (apiUrl.isEmpty() || slug.isEmpty() || apiKey.isEmpty())
            {
                return null;
            }

            return new String[]{apiUrl, slug, apiKey};
        }
        catch (IllegalArgumentException e)
        {
            // Invalid base64
            return null;
        }
    }

    public static String encode(String apiUrl, String slug, String apiKey)
    {
        String raw = apiUrl + "|" + slug + "|" + apiKey;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }
}
```

- [ ] **Step 2: Verify plugin builds**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin && ./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/ClanCodeUtil.java
git commit -m "feat: add ClanCodeUtil for base64 clan code encode/decode"
```

---

### Task 3: Add clanCode Config Field

**Files:**
- Modify: `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\ClanManagementConfig.java`

- [ ] **Step 1: Add clanCode field to the General section**

In `ClanManagementConfig.java`, replace the existing `boardCode` config item (lines 20-28) with:

```java
    @ConfigItem(
        keyName = "clanCode",
        name = "Clan Code",
        description = "Paste the code from your clan admin to connect to the platform",
        section = generalSection,
        position = 0,
        secret = true
    )
    default String clanCode() { return ""; }

    @ConfigItem(
        keyName = "boardCode",
        name = "Legacy Board Code",
        description = "Legacy Google Sheets code (not needed if Clan Code is set)",
        section = generalSection,
        position = 1,
        secret = true
    )
    default String boardCode() { return ""; }
```

- [ ] **Step 2: Remove the Platform section config items**

Delete the entire Platform section from `ClanManagementConfig.java` (lines 206-268): the `platformSection` declaration, `platformApiUrl`, `platformApiKey`, `platformClanSlug`, `enableClogSync`, `enablePbSync`, and `enableStatTracking` config items.

Move the three toggle items (`enableClogSync`, `enablePbSync`, `enableStatTracking`) into the General section with higher position numbers:

```java
    @ConfigItem(
        keyName = "enableClogSync",
        name = "Auto-Sync Collection Log",
        description = "Automatically sync your collection log to the platform when you open it in-game",
        section = generalSection,
        position = 10
    )
    default boolean enableClogSync() { return false; }

    @ConfigItem(
        keyName = "enablePbSync",
        name = "Auto-Sync Personal Bests",
        description = "Automatically sync personal best times from your adventure log to the platform",
        section = generalSection,
        position = 11
    )
    default boolean enablePbSync() { return false; }

    @ConfigItem(
        keyName = "enableStatTracking",
        name = "Auto-Track Stats",
        description = "Automatically detect clan member logoffs and trigger stat snapshots on the platform",
        section = generalSection,
        position = 12
    )
    default boolean enableStatTracking() { return false; }
```

- [ ] **Step 3: Verify plugin builds**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin && ./gradlew build
```

Expected: BUILD FAILED — compilation errors in ClanManagementPlugin.java and RsHiscoreTracker.java referencing the removed methods. This is expected; we fix them in Task 4.

- [ ] **Step 4: Commit config changes (even with build errors — they're fixed next task)**

Do NOT commit yet. Proceed to Task 4 to fix the compilation errors first.

---

### Task 4: Update Plugin to Use Clan Code

**Files:**
- Modify: `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\ClanManagementPlugin.java`
- Modify: `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\RsHiscoreTracker.java`

- [ ] **Step 1: Add decoded clan code fields and helpers to ClanManagementPlugin**

Near the top of `ClanManagementPlugin.java`, add these fields with the other instance variables:

```java
    // Decoded clan code values (cached on config change)
    private String decodedPlatformUrl = "";
    private String decodedPlatformSlug = "";
    private String decodedPlatformKey = "";
```

Add three helper methods right after the existing `isPlatformConfigured()` method:

```java
    private String getPlatformUrl()
    {
        return decodedPlatformUrl;
    }

    private String getPlatformKey()
    {
        return decodedPlatformKey;
    }

    private String getPlatformSlug()
    {
        return decodedPlatformSlug;
    }

    private void decodeClanCode()
    {
        String code = config.clanCode();
        String[] parts = ClanCodeUtil.decode(code);
        if (parts != null)
        {
            decodedPlatformUrl = parts[0];
            decodedPlatformSlug = parts[1];
            decodedPlatformKey = parts[2];
        }
        else
        {
            decodedPlatformUrl = "";
            decodedPlatformSlug = "";
            decodedPlatformKey = "";
        }
    }
```

- [ ] **Step 2: Update isPlatformConfigured()**

Replace the existing `isPlatformConfigured()` method (lines 280-288):

```java
    private boolean isPlatformConfigured()
    {
        return !decodedPlatformUrl.isEmpty()
            && !decodedPlatformKey.isEmpty()
            && !decodedPlatformSlug.isEmpty();
    }
```

- [ ] **Step 3: Call decodeClanCode() on startup and config change**

In the `startUp()` method, after config is available, add:

```java
        decodeClanCode();
```

If there's a config change handler (e.g. `onConfigChanged`), also call `decodeClanCode()` there.

- [ ] **Step 4: Replace all config.platformApiUrl() / config.platformApiKey() / config.platformClanSlug() calls**

Search and replace throughout `ClanManagementPlugin.java`:
- `config.platformApiUrl()` → `getPlatformUrl()`
- `config.platformApiKey()` → `getPlatformKey()`
- `config.platformClanSlug()` → `getPlatformSlug()`

There are approximately 9 `platformApiUrl`, 9 `platformApiKey`, and 9 `platformClanSlug` references to replace.

- [ ] **Step 5: Update RsHiscoreTracker.java**

In `RsHiscoreTracker.java`, the `onGameTick` and `syncRoster` methods read config directly. Change the method signatures to accept the decoded values instead:

Replace `onGameTick(ClanManagementConfig config)` internals — change:
```java
        String baseUrl = config.platformApiUrl();
        String apiKey = config.platformApiKey();
        String slug = config.platformClanSlug();
```
to accept these as parameters passed from the plugin:
```java
    public void onGameTick(String baseUrl, String apiKey, String slug, boolean enableStatTracking)
```

And update `syncRoster` similarly:
```java
    public int syncRoster(String baseUrl, String apiKey, String slug)
```

And `onLoginIfAdmin`:
```java
    public void onLoginIfAdmin(String baseUrl, String apiKey, String slug, String adminKey)
```

Update all call sites in `ClanManagementPlugin.java` to pass the decoded values.

- [ ] **Step 6: Verify plugin builds**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin && ./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Copy JAR**

```bash
cp build/libs/*.jar ~/.runelite/externalPlugins/clan-management.jar
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/droplogger/ClanManagementConfig.java \
        src/main/java/com/droplogger/ClanManagementPlugin.java \
        src/main/java/com/droplogger/RsHiscoreTracker.java \
        src/main/java/com/droplogger/ClanCodeUtil.java
git commit -m "feat: replace platform config fields with single clan code"
```

---

### Task 5: Add Bootstrap Fetch on Plugin Startup

**Files:**
- Modify: `C:\dev\OSRS_Bingo\drop-logger-plugin\src\main\java\com\droplogger\ClanManagementPlugin.java`

- [ ] **Step 1: Add a fetchBootstrapConfig method**

```java
    /**
     * Fetch shared config from the platform bootstrap endpoint.
     * Updates cached values for Discord webhook URL, min drop value, active event, etc.
     */
    private void fetchBootstrapConfig()
    {
        if (!isPlatformConfigured())
        {
            return;
        }

        String url = getPlatformUrl() + "/clans/" + getPlatformSlug() + "/bootstrap";
        JsonObject response = platformApiService.getSync(url, getPlatformKey());
        if (response == null)
        {
            log.warn("Failed to fetch bootstrap config from platform");
            return;
        }

        // Discord webhook
        if (response.has("discordWebhooks"))
        {
            JsonObject webhooks = response.getAsJsonObject("discordWebhooks");
            if (webhooks.has("drops"))
            {
                fetchedDiscordWebhookUrl = webhooks.get("drops").getAsString();
            }
        }

        // Settings
        if (response.has("settings"))
        {
            JsonObject settings = response.getAsJsonObject("settings");
            if (settings.has("minDropValue"))
            {
                // Store for use in drop filtering
                fetchedMinDropValue = settings.get("minDropValue").getAsInt();
            }
        }

        // Active event
        if (response.has("activeEvent") && !response.get("activeEvent").isJsonNull())
        {
            JsonObject event = response.getAsJsonObject("activeEvent");
            activeEventType = event.has("type") ? event.get("type").getAsString() : "";
            activeEventMetric = event.has("metric") ? event.get("metric").getAsString() : "";
            activeEventDisplayName = event.has("displayName") ? event.get("displayName").getAsString() : "";
            activeEventEndTime = event.has("endTime") ? event.get("endTime").getAsString() : "";
        }
        else
        {
            activeEventType = "";
            activeEventMetric = "";
            activeEventDisplayName = "";
            activeEventEndTime = "";
        }

        log.info("Bootstrap config loaded from platform");
    }
```

- [ ] **Step 2: Call fetchBootstrapConfig during startup/refresh**

In the `refreshData()` method or equivalent startup flow, call `fetchBootstrapConfig()` after `decodeClanCode()` and before any code that uses webhooks or event state.

- [ ] **Step 3: Verify plugin builds**

```bash
cd C:\dev\OSRS_Bingo\drop-logger-plugin && ./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Copy JAR**

```bash
cp build/libs/*.jar ~/.runelite/externalPlugins/clan-management.jar
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/droplogger/ClanManagementPlugin.java
git commit -m "feat: fetch bootstrap config from platform on startup"
```

---

### Task 6: Build, Deploy, and Test

- [ ] **Step 1: Build and restart API**

```bash
cd C:\dev\clan-platform-api && npm run build
npx pm2 restart clan-api
```

- [ ] **Step 2: Build and restart web**

```bash
cd C:\dev\clan-platform-web && npm run build
npx pm2 restart clan-web
```

- [ ] **Step 3: Generate a test clan code**

Using the existing API key for the Solus clan, generate a code:

```bash
# Get the raw API key you used when creating the clan, then:
echo -n "http://localhost:3001|solus|YOUR_API_KEY" | base64
```

Paste the resulting string into the plugin's new "Clan Code" field.

- [ ] **Step 4: Verify plugin connects**

Restart RuneLite. Check the RuneLite logs for:
- "Bootstrap config loaded from platform"
- No errors about missing platform config

- [ ] **Step 5: Verify drops and PBs still submit**

Test a drop or PB submission and confirm it reaches the API.
