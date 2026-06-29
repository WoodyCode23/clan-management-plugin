package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class PlatformApiService
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    // The logged-in account's immutable RuneLite account hash, set by the plugin on login.
    // Stamped into player-data payloads so the backend keys identity on the account (not the
    // RSN), making history rename-proof. Null until an account is logged in.
    private volatile String accountHash;

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

    /** Set (or clear, with null) the current account hash. Called by the plugin on login/logout. */
    public void setAccountHash(String accountHash)
    {
        this.accountHash = accountHash;
    }

    public String getAccountHash()
    {
        return accountHash;
    }

    /** Stamp the current account hash onto a player-data payload when known. */
    private void addAccountHash(JsonObject payload)
    {
        if (accountHash != null && !accountHash.isEmpty())
        {
            payload.addProperty("accountHash", accountHash);
        }
    }

    /**
     * Submit a drop to the platform API.
     */
    public void submitDrop(String baseUrl, String apiKey, String clanSlug, DropEntry drop, String screenshotB64)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", drop.getPlayerName());
        addAccountHash(payload);
        payload.addProperty("itemName", drop.getItemName());
        if (drop.getItemId() > 0)
        {
            payload.addProperty("itemId", drop.getItemId());
        }
        payload.addProperty("value", drop.getValue());
        payload.addProperty("monsterName", drop.getMonsterName());
        if (drop.getKillCount() > 0)
        {
            payload.addProperty("killCount", drop.getKillCount());
        }
        if (screenshotB64 != null)
        {
            payload.addProperty("screenshot", screenshotB64);
        }

        postAsync(baseUrl + "/clans/" + clanSlug + "/drops", apiKey, payload, "Platform drop");
    }

    /**
     * Submit a personal best to the platform API (defaults to "live" source).
     */
    public void submitPb(String baseUrl, String apiKey, String clanSlug,
                         String rsn, String bossKey, int teamSize, int timeMs)
    {
        submitPb(baseUrl, apiKey, clanSlug, rsn, bossKey, teamSize, timeMs, "live", null);
    }

    /**
     * Submit a personal best to the platform API with a specific source.
     * teamMembers = comma-joined roster for team content (null for solo).
     */
    public void submitPb(String baseUrl, String apiKey, String clanSlug,
                         String rsn, String bossKey, int teamSize, int timeMs, String source, String teamMembers)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);
        payload.addProperty("source", source);
        if (teamMembers != null && !teamMembers.isEmpty())
        {
            payload.addProperty("teamMembers", teamMembers);
        }

        postAsync(baseUrl + "/clans/" + clanSlug + "/pbs", apiKey, payload, "Platform PB");
    }

    /**
     * Submit a PB synchronously and return its clan placement (1 = new clan record),
     * or 0 on failure / non-live. Call off the client thread (it blocks on the network).
     */
    public int submitPbSync(String baseUrl, String apiKey, String clanSlug,
                            String rsn, String bossKey, int teamSize, int timeMs, String source,
                            String teamMembers, String screenshotB64)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);
        payload.addProperty("source", source);
        if (teamMembers != null && !teamMembers.isEmpty())
        {
            payload.addProperty("teamMembers", teamMembers);
        }
        if (screenshotB64 != null)
        {
            payload.addProperty("screenshot", screenshotB64);
        }

        Request request = new Request.Builder()
            .url(baseUrl + "/clans/" + clanSlug + "/pbs")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(JSON, gson.toJson(payload)))
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null) return 0;
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            return root != null && root.has("clanRank") && !root.get("clanRank").isJsonNull()
                ? root.get("clanRank").getAsInt() : 0;
        }
        catch (Exception e)
        {
            log.warn("submitPbSync failed", e);
            return 0;
        }
    }

    /**
     * Fastest clan-verified (live) time in ms for a category + team size, or 0 if none recorded.
     * Used to decide whether a non-personal-best completion is still a new clan PB worth submitting.
     */
    public int fetchClanBestTimeMs(String baseUrl, String apiKey, String clanSlug, String categoryKey, int teamSize)
    {
        try
        {
            HttpUrl url = HttpUrl.parse(baseUrl + "/clans/" + clanSlug + "/personal-bests/top").newBuilder()
                .addQueryParameter("category", categoryKey)
                .addQueryParameter("teamSize", String.valueOf(teamSize))
                .addQueryParameter("limit", "1")
                .build();
            Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + apiKey).get().build();
            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null) return 0;
                JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                if (root == null || !root.has("entries")) return 0;
                JsonArray entries = root.getAsJsonArray("entries");
                if (entries.size() == 0) return 0;
                JsonObject e = entries.get(0).getAsJsonObject();
                return e.has("timeMs") && !e.get("timeMs").isJsonNull() ? e.get("timeMs").getAsInt() : 0;
            }
        }
        catch (Exception ex)
        {
            log.warn("fetchClanBestTimeMs failed", ex);
            return 0;
        }
    }

    /** One Combat Achievement task read from the in-game CA interface. */
    public static class CaTask
    {
        public final String name;
        public final boolean completed;
        public CaTask(String name, boolean completed) { this.name = name; this.completed = completed; }
    }

    /**
     * Bulk-sync the player's Combat Achievement completion state. The server matches each task by
     * name to its catalog (tier/monster/type) and upserts the player's completion per task.
     */
    public void syncCombatAchievements(String baseUrl, String apiKey, String clanSlug,
                                       String rsn, java.util.List<CaTask> tasks)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        JsonArray arr = new JsonArray();
        for (CaTask t : tasks)
        {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.name);
            o.addProperty("completed", t.completed);
            arr.add(o);
        }
        payload.add("tasks", arr);
        postAsync(baseUrl + "/clans/" + clanSlug + "/combat-achievements/bulk", apiKey, payload, "Platform CA sync");
    }

    /** One catalog task with the viewed player's completion state. */
    public static class CaTaskInfo
    {
        public final String name;
        public final String tier;
        public final String monster;
        public final String type;
        public final int points;
        public final boolean completed;
        public CaTaskInfo(String name, String tier, String monster, String type, int points, boolean completed)
        {
            this.name = name; this.tier = tier; this.monster = monster;
            this.type = type; this.points = points; this.completed = completed;
        }
    }

    /** Per-tier rollup for the viewed player. */
    public static class CaTier
    {
        public final String tier;
        public final int completed;
        public final int total;
        public CaTier(String tier, int completed, int total)
        {
            this.tier = tier; this.completed = completed; this.total = total;
        }
    }

    /** A player's full Combat Achievements view: every task + per-tier and overall rollups. */
    public static class PlayerCa
    {
        public final int completed;
        public final int total;
        public final int pointsEarned;
        public final int pointsTotal;
        public final List<CaTier> tiers;
        public final List<CaTaskInfo> tasks;
        public PlayerCa(int completed, int total, int pointsEarned, int pointsTotal,
                        List<CaTier> tiers, List<CaTaskInfo> tasks)
        {
            this.completed = completed; this.total = total;
            this.pointsEarned = pointsEarned; this.pointsTotal = pointsTotal;
            this.tiers = tiers; this.tasks = tasks;
        }
    }

    /** Fetch a player's Combat Achievements (catalog + their completion), or null on error. */
    public PlayerCa fetchPlayerCa(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/combat-achievements/" + encodePath(rsn), apiKey);
        if (root == null) return null;

        List<CaTaskInfo> tasks = new ArrayList<>();
        if (root.has("tasks"))
        {
            for (JsonElement el : root.getAsJsonArray("tasks"))
            {
                JsonObject o = el.getAsJsonObject();
                tasks.add(new CaTaskInfo(
                    o.has("name") ? o.get("name").getAsString() : "",
                    o.has("tier") ? o.get("tier").getAsString() : "",
                    o.has("monster") && !o.get("monster").isJsonNull() ? o.get("monster").getAsString() : "",
                    o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : "",
                    o.has("points") && !o.get("points").isJsonNull() ? o.get("points").getAsInt() : 0,
                    o.has("completed") && o.get("completed").getAsBoolean()));
            }
        }

        List<CaTier> tiers = new ArrayList<>();
        if (root.has("tiers"))
        {
            for (JsonElement el : root.getAsJsonArray("tiers"))
            {
                JsonObject o = el.getAsJsonObject();
                tiers.add(new CaTier(
                    o.has("tier") ? o.get("tier").getAsString() : "",
                    o.has("completed") && !o.get("completed").isJsonNull() ? o.get("completed").getAsInt() : 0,
                    o.has("total") && !o.get("total").isJsonNull() ? o.get("total").getAsInt() : 0));
            }
        }

        int completed = root.has("completed") && !root.get("completed").isJsonNull() ? root.get("completed").getAsInt() : 0;
        int total = root.has("total") && !root.get("total").isJsonNull() ? root.get("total").getAsInt() : 0;
        int pointsEarned = root.has("pointsEarned") && !root.get("pointsEarned").isJsonNull() ? root.get("pointsEarned").getAsInt() : 0;
        int pointsTotal = root.has("pointsTotal") && !root.get("pointsTotal").isJsonNull() ? root.get("pointsTotal").getAsInt() : 0;
        return new PlayerCa(completed, total, pointsEarned, pointsTotal, tiers, tasks);
    }

    /**
     * Submit a single collection log entry to the platform API.
     */
    public void submitCollectionLogEntry(String baseUrl, String apiKey, String clanSlug,
                                          String rsn, String itemName)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("itemName", itemName);

        postAsync(baseUrl + "/clans/" + clanSlug + "/collection-log", apiKey, payload, "Platform clog");
    }

    /**
     * Bulk sync collection log entries to the platform API.
     */
    public void bulkSyncCollectionLog(String baseUrl, String apiKey, String clanSlug,
                                       String rsn, java.util.List<ClogItem> itemList,
                                       int uniqueObtained, int uniqueTotal,
                                       Callback callback)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        // Authoritative game counts (varp 2943/2944) — the X/Y headline the backend stores/serves.
        if (uniqueObtained > 0) payload.addProperty("uniqueObtained", uniqueObtained);
        if (uniqueTotal > 0) payload.addProperty("uniqueTotal", uniqueTotal);

        JsonArray items = new JsonArray();
        for (ClogItem ci : itemList)
        {
            JsonObject item = new JsonObject();
            item.addProperty("itemName", ci.name);
            if (ci.itemId > 0)
            {
                item.addProperty("itemId", ci.itemId);
            }
            if (ci.tab != null)
            {
                item.addProperty("tab", ci.tab);
            }
            if (ci.category != null)
            {
                item.addProperty("category", ci.category);
            }
            if (ci.quantity > 0)
            {
                item.addProperty("quantity", ci.quantity);
            }
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
     * Sync pre-resolved collection log catalog (all possible items) to the platform API.
     * Item names must be resolved on the client thread before calling this.
     */
    public void syncCatalogResolved(String baseUrl, String apiKey, String clanSlug,
                                     JsonArray catalogItems)
    {
        JsonObject payload = new JsonObject();
        payload.add("items", catalogItems);

        postAsync(baseUrl + "/clans/" + clanSlug + "/collection-log/catalog", apiKey, payload, "Catalog sync");
    }

    /**
     * Submit an achievement to the platform API.
     */
    public void submitAchievement(String baseUrl, String apiKey, String clanSlug,
                                   String rsn, String type, String detail)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("type", type);
        payload.addProperty("detail", detail);

        postAsync(baseUrl + "/clans/" + clanSlug + "/achievements", apiKey, payload, "Platform achievement");
    }

    /**
     * Fetch the clan XP leaderboard from the backend (replaces the old direct Wise Old Man
     * call). For an "all" period the value is the player's current total XP; for day/week/
     * month/year it's the XP gained in that window. skill="overall" (or null) ranks by total
     * XP, otherwise by that single skill. Returns WomEntry objects so the panel render is reused
     * (value stored in both experience and gained; the caller picks which to show by period).
     */
    public List<LeaderboardEntry> fetchXpLeaderboard(String baseUrl, String apiKey, String clanSlug,
                                                         String skill, String period)
    {
        HttpUrl.Builder ub = HttpUrl.parse(baseUrl + "/clans/" + clanSlug + "/leaderboard").newBuilder()
            .addQueryParameter("board", "xp")
            .addQueryParameter("period", period)
            .addQueryParameter("limit", "20");
        if (skill != null && !skill.isEmpty() && !skill.equals("overall"))
        {
            ub.addQueryParameter("skill", skill);
        }

        Request request = new Request.Builder().url(ub.build())
            .header("Authorization", "Bearer " + apiKey).get().build();
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null) return entries;
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null || !root.has("entries")) return entries;
            for (JsonElement el : root.getAsJsonArray("entries"))
            {
                JsonObject o = el.getAsJsonObject();
                int rank = o.has("rank") ? o.get("rank").getAsInt() : entries.size() + 1;
                String rsn = o.has("rsn") ? o.get("rsn").getAsString() : "";
                long value = o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsLong() : 0;
                entries.add(new LeaderboardEntry(rank, rsn, "member", value, 0, value));
            }
        }
        catch (Exception ex)
        {
            log.warn("fetchXpLeaderboard failed", ex);
        }
        return entries;
    }

    /** A clan activity feed item from the backend (joins, leaves, drops, PBs, clog unlocks). */
    public static class ActivityItem
    {
        public final String type;     // "join" | "leave" | "drop" | "pb" | "clog"
        public final String rsn;
        public final String title;    // item / boss label / "Joined the clan"
        public final String detail;   // "from Zulrah", formatted time, category, rank — may be ""
        public final long value;      // pb: timeMs; drop: gp value; else 0
        public final int itemId;      // drop/clog icon id, -1 if none
        public final String createdAt;

        public ActivityItem(String type, String rsn, String title, String detail, long value, int itemId, String createdAt)
        {
            this.type = type;
            this.rsn = rsn;
            this.title = title;
            this.detail = detail;
            this.value = value;
            this.itemId = itemId;
            this.createdAt = createdAt;
        }
    }

    /**
     * Fetch the clan activity feed from the backend. typeFilter is an optional comma-separated
     * subset (e.g. "drop,pb" or "join,leave"); null/empty returns everything.
     */
    public List<ActivityItem> fetchActivity(String baseUrl, String apiKey, String clanSlug, int limit, String typeFilter)
    {
        HttpUrl.Builder b = HttpUrl.parse(baseUrl + "/clans/" + clanSlug + "/activity").newBuilder()
            .addQueryParameter("limit", String.valueOf(limit));
        if (typeFilter != null && !typeFilter.isEmpty()) b.addQueryParameter("type", typeFilter);
        Request request = new Request.Builder().url(b.build())
            .header("Authorization", "Bearer " + apiKey).get().build();
        List<ActivityItem> out = new ArrayList<>();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null) return out;
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null || !root.has("activity")) return out;
            for (JsonElement el : root.getAsJsonArray("activity"))
            {
                JsonObject o = el.getAsJsonObject();
                out.add(new ActivityItem(
                    o.has("type") ? o.get("type").getAsString() : "",
                    o.has("rsn") ? o.get("rsn").getAsString() : "",
                    o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : "",
                    o.has("detail") && !o.get("detail").isJsonNull() ? o.get("detail").getAsString() : "",
                    o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsLong() : 0,
                    o.has("itemId") && !o.get("itemId").isJsonNull() ? o.get("itemId").getAsInt() : -1,
                    o.has("createdAt") ? o.get("createdAt").getAsString() : ""));
            }
        }
        catch (Exception ex) { log.warn("fetchActivity failed", ex); }
        return out;
    }

    /** Fetch the current active event's leaderboard from the backend (replaces the WOM call). */
    public List<LeaderboardEntry> fetchActiveEventLeaderboard(String baseUrl, String apiKey, String clanSlug)
    {
        Request request = new Request.Builder()
            .url(baseUrl + "/clans/" + clanSlug + "/events/active")
            .header("Authorization", "Bearer " + apiKey).get().build();
        List<LeaderboardEntry> out = new ArrayList<>();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null) return out;
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null || !root.has("leaderboard")) return out;
            int rank = 1;
            for (JsonElement el : root.getAsJsonArray("leaderboard"))
            {
                JsonObject o = el.getAsJsonObject();
                String rsn = o.has("rsn") ? o.get("rsn").getAsString() : "";
                long score = o.has("score") && !o.get("score").isJsonNull() ? o.get("score").getAsLong() : 0;
                out.add(new LeaderboardEntry(rank++, rsn, "member", score, 0, score));
            }
        }
        catch (Exception ex) { log.warn("fetchActiveEventLeaderboard failed", ex); }
        return out;
    }

    /**
     * Sync the full clan roster to the platform API. Admin only.
     */
    public void syncRoster(String baseUrl, String apiKey, String clanSlug,
                           java.util.List<String[]> members)
    {
        JsonObject payload = new JsonObject();
        JsonArray membersArr = new JsonArray();
        for (String[] member : members)
        {
            JsonObject m = new JsonObject();
            m.addProperty("rsn", member[0]);
            if (member.length > 1 && member[1] != null)
            {
                m.addProperty("rank", member[1]);
            }
            if (member.length > 2 && member[2] != null)
            {
                m.addProperty("joinDate", member[2]);
            }
            membersArr.add(m);
        }
        payload.add("members", membersArr);

        postAsync(baseUrl + "/clans/" + clanSlug + "/roster", apiKey, payload, "Roster sync");
    }

    /**
     * Trigger an immediate stat snapshot for a player.
     */
    public void triggerSnapshot(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        JsonObject payload = new JsonObject();
        postAsync(baseUrl + "/clans/" + clanSlug + "/players/" + rsn + "/snapshot-trigger",
                  apiKey, payload, "Snapshot trigger");
    }

    /**
     * Fetch all personal bests from the platform API and group by bossKey.
     * Returns a map of bossKey → list of HiscoreEntry, or null on error.
     */
    public Map<String, List<HiscoreEntry>> fetchAllPbs(String baseUrl, String apiKey, String clanSlug, String mode)
    {
        // mode = "clan" (live/clan-verified only) or "all" (each player's best across sources)
        String url = baseUrl + "/clans/" + clanSlug + "/pbs?mode=" + mode;
        JsonObject response = getSync(url, apiKey);
        if (response == null || !response.has("leaderboard")) return null;

        Map<String, List<HiscoreEntry>> result = new java.util.LinkedHashMap<>();
        com.google.gson.JsonArray leaderboard = response.getAsJsonArray("leaderboard");

        // Team content submits one row per member (same time + roster). Collapse those into a
        // single team line so a duo best shows "BlG Woody, BlG Moby" once, not one row each.
        java.util.Set<String> seenTeam = new java.util.HashSet<>();

        for (com.google.gson.JsonElement elem : leaderboard)
        {
            JsonObject pb = elem.getAsJsonObject();
            String bossKey = pb.has("bossKey") ? pb.get("bossKey").getAsString() : "";
            int teamSize = pb.has("teamSize") ? pb.get("teamSize").getAsInt() : 1;
            String rsn = pb.has("rsn") ? pb.get("rsn").getAsString() : "";
            int timeMs = pb.has("timeMs") ? pb.get("timeMs").getAsInt() : 0;
            String teamMembers = pb.has("teamMembers") && !pb.get("teamMembers").isJsonNull()
                ? pb.get("teamMembers").getAsString() : null;

            // For team content, show the full roster (the leaderboard render already lays out
            // multi-name entries when the name contains a comma). Fall back to the single rsn.
            String display = (teamSize > 1 && teamMembers != null && !teamMembers.isEmpty())
                ? teamMembers : rsn;

            if (teamSize > 1)
            {
                String dedupeKey = bossKey + "|" + teamSize + "|" + timeMs + "|" + display;
                if (!seenTeam.add(dedupeKey))
                {
                    continue; // already added this team's time from another member's row
                }
            }

            double timeSeconds = timeMs / 1000.0;
            int totalSec = (int) timeSeconds;
            int min = totalSec / 60;
            double sec = timeSeconds - (min * 60);
            String formattedTime = String.format("%d:%05.2f", min, sec);

            // Use bossKey directly as the category key (matches BossCategory.getKey())
            result.computeIfAbsent(bossKey, k -> new java.util.ArrayList<>())
                .add(new HiscoreEntry(0, timeSeconds, formattedTime, display, "", bossKey, teamSize));
        }

        // Sort each category by time and assign 1-based ranks. HiscoreEntry.rank is final, so
        // rebuild each entry with its position (previously every entry kept rank 0 → showed "#0").
        for (java.util.Map.Entry<String, List<HiscoreEntry>> e : result.entrySet())
        {
            List<HiscoreEntry> entries = e.getValue();
            entries.sort(java.util.Comparator.comparingDouble(HiscoreEntry::getTimeSeconds));
            List<HiscoreEntry> ranked = new java.util.ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++)
            {
                HiscoreEntry h = entries.get(i);
                ranked.add(new HiscoreEntry(i + 1, h.getTimeSeconds(), h.getFormattedTime(),
                    h.getRsns(), h.getDate(), h.getCategoryKey(), h.getPartySize()));
            }
            e.setValue(ranked);
        }

        return result;
    }

    /**
     * Synchronous GET — returns parsed JSON or null on error.
     */
    public JsonObject getSync(String url, String apiKey)
    {
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null) return null;
            return gson.fromJson(response.body().string(), JsonObject.class);
        }
        catch (Exception e)
        {
            log.debug("GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    /** A clan announcement from the backend. */
    public static class Announcement
    {
        public final String id;
        public final String message;
        public final String author;
        public final boolean pinned;

        public Announcement(String id, String message, String author, boolean pinned)
        {
            this.id = id;
            this.message = message;
            this.author = author;
            this.pinned = pinned;
        }
    }

    /** Fetch the clan's announcements (public list, pinned first). */
    public List<Announcement> fetchAnnouncements(String baseUrl, String apiKey, String clanSlug)
    {
        List<Announcement> out = new ArrayList<>();
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/announcements", apiKey);
        if (root == null || !root.has("announcements")) return out;
        for (JsonElement el : root.getAsJsonArray("announcements"))
        {
            JsonObject o = el.getAsJsonObject();
            out.add(new Announcement(
                o.has("id") ? o.get("id").getAsString() : "",
                o.has("message") && !o.get("message").isJsonNull() ? o.get("message").getAsString() : "",
                o.has("author") && !o.get("author").isJsonNull() ? o.get("author").getAsString() : null,
                o.has("pinned") && o.get("pinned").getAsBoolean()));
        }
        return out;
    }

    /** A clan roster member (for the in-panel member browser). */
    public static class RosterMember
    {
        public final String rsn;
        public final String rank;
        public RosterMember(String rsn, String rank) { this.rsn = rsn; this.rank = rank; }
    }

    /** Fetch the clan roster (names + ranks) for the Members tab. */
    public List<RosterMember> fetchRoster(String baseUrl, String apiKey, String clanSlug)
    {
        List<RosterMember> out = new ArrayList<>();
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/roster", apiKey);
        if (root == null || !root.has("roster")) return out;
        for (JsonElement el : root.getAsJsonArray("roster"))
        {
            JsonObject o = el.getAsJsonObject();
            out.add(new RosterMember(
                o.has("rsn") ? o.get("rsn").getAsString() : "",
                o.has("rank") && !o.get("rank").isJsonNull() ? o.get("rank").getAsString() : null));
        }
        return out;
    }

    /** One collection-log slot + whether the viewed player owns it. */
    public static class ClogCatalogItem
    {
        public final int itemId;
        public final String name;
        public final String tab;
        public final String category;
        public final boolean owned;
        public ClogCatalogItem(int itemId, String name, String tab, String category, boolean owned)
        {
            this.itemId = itemId; this.name = name; this.tab = tab; this.category = category; this.owned = owned;
        }
    }

    /** A player's full collection log: headline X/Y + every slot with owned status. */
    public static class PlayerClog
    {
        public final int obtained;
        public final int total;
        public final List<ClogCatalogItem> items;
        public PlayerClog(int obtained, int total, List<ClogCatalogItem> items)
        {
            this.obtained = obtained; this.total = total; this.items = items;
        }
    }

    /** Fetch any clan member's collection log (catalog + owned status) for the Members tab. */
    public PlayerClog fetchPlayerClog(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/collection-log/" + encodePath(rsn), apiKey);
        if (root == null) return null;

        Set<Integer> owned = new HashSet<>();
        if (root.has("entries"))
        {
            for (JsonElement el : root.getAsJsonArray("entries"))
            {
                JsonObject o = el.getAsJsonObject();
                if (o.has("itemId") && !o.get("itemId").isJsonNull()) owned.add(o.get("itemId").getAsInt());
            }
        }

        List<ClogCatalogItem> items = new ArrayList<>();
        if (root.has("catalog"))
        {
            for (JsonElement el : root.getAsJsonArray("catalog"))
            {
                JsonObject o = el.getAsJsonObject();
                int itemId = o.has("itemId") ? o.get("itemId").getAsInt() : -1;
                items.add(new ClogCatalogItem(
                    itemId,
                    o.has("itemName") && !o.get("itemName").isJsonNull() ? o.get("itemName").getAsString() : "",
                    o.has("tab") && !o.get("tab").isJsonNull() ? o.get("tab").getAsString() : "Other",
                    o.has("category") && !o.get("category").isJsonNull() ? o.get("category").getAsString() : "Other",
                    owned.contains(itemId)));
            }
        }

        int obtained = root.has("obtained") && !root.get("obtained").isJsonNull() ? root.get("obtained").getAsInt() : owned.size();
        int total = root.has("totalSlots") && !root.get("totalSlots").isJsonNull() ? root.get("totalSlots").getAsInt() : items.size();
        return new PlayerClog(obtained, total, items);
    }

    private static String encodePath(String s)
    {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return s; }
    }

    /** A player's best time at one boss/team-size. */
    public static class PlayerPb
    {
        public final String bossKey;
        public final int teamSize;
        public final int timeMs;
        public final String teamMembers; // comma-joined roster for team content, else null
        public PlayerPb(String bossKey, int teamSize, int timeMs, String teamMembers)
        {
            this.bossKey = bossKey; this.teamSize = teamSize; this.timeMs = timeMs;
            this.teamMembers = teamMembers;
        }
    }

    /** One of a player's recent drops. */
    public static class PlayerDrop
    {
        public final String itemName;
        public final long value;
        public final String monsterName;
        public PlayerDrop(String itemName, long value, String monsterName)
        {
            this.itemName = itemName; this.value = value; this.monsterName = monsterName;
        }
    }

    /** A member's profile for the Members tab: clog counts + their PBs + recent drops. */
    public static class PlayerProfile
    {
        public final int clogObtained;
        public final int clogTotal;
        public final int caCompleted;
        public final int caTotal;
        public final List<PlayerPb> pbs;
        public final List<PlayerDrop> drops;
        public PlayerProfile(int clogObtained, int clogTotal, int caCompleted, int caTotal,
                             List<PlayerPb> pbs, List<PlayerDrop> drops)
        {
            this.clogObtained = clogObtained; this.clogTotal = clogTotal;
            this.caCompleted = caCompleted; this.caTotal = caTotal;
            this.pbs = pbs; this.drops = drops;
        }
    }

    /** Fetch a member's profile (PBs + recent drops + clog counts) in one call. */
    public PlayerProfile fetchPlayerProfile(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/players/" + encodePath(rsn), apiKey);
        if (root == null) return null;

        List<PlayerPb> pbs = new ArrayList<>();
        if (root.has("personalBests"))
        {
            for (JsonElement el : root.getAsJsonArray("personalBests"))
            {
                JsonObject o = el.getAsJsonObject();
                pbs.add(new PlayerPb(
                    o.has("bossKey") ? o.get("bossKey").getAsString() : "",
                    o.has("teamSize") ? o.get("teamSize").getAsInt() : 1,
                    o.has("timeMs") && !o.get("timeMs").isJsonNull() ? o.get("timeMs").getAsInt() : 0,
                    o.has("teamMembers") && !o.get("teamMembers").isJsonNull() ? o.get("teamMembers").getAsString() : null));
            }
        }

        List<PlayerDrop> drops = new ArrayList<>();
        if (root.has("recentDrops"))
        {
            for (JsonElement el : root.getAsJsonArray("recentDrops"))
            {
                JsonObject o = el.getAsJsonObject();
                drops.add(new PlayerDrop(
                    o.has("itemName") ? o.get("itemName").getAsString() : "",
                    o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsLong() : 0,
                    o.has("monsterName") && !o.get("monsterName").isJsonNull() ? o.get("monsterName").getAsString() : ""));
            }
        }

        int clogObtained = root.has("collectionLogCount") && !root.get("collectionLogCount").isJsonNull() ? root.get("collectionLogCount").getAsInt() : 0;
        int clogTotal = root.has("collectionLogTotal") && !root.get("collectionLogTotal").isJsonNull() ? root.get("collectionLogTotal").getAsInt() : 0;
        int caCompleted = root.has("caCompleted") && !root.get("caCompleted").isJsonNull() ? root.get("caCompleted").getAsInt() : 0;
        int caTotal = root.has("caTotal") && !root.get("caTotal").isJsonNull() ? root.get("caTotal").getAsInt() : 0;
        return new PlayerProfile(clogObtained, clogTotal, caCompleted, caTotal, pbs, drops);
    }

    /** Create an announcement (admin — needs a key whose owner has manage_announcements/admin). */
    public boolean createAnnouncement(String baseUrl, String apiKey, String clanSlug, String message, boolean pinned)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        payload.addProperty("pinned", pinned);
        return mutateSync("POST", baseUrl + "/clans/" + clanSlug + "/announcements", apiKey, payload);
    }

    /** Edit an announcement's message and/or pinned flag (null = leave unchanged). */
    public boolean updateAnnouncement(String baseUrl, String apiKey, String clanSlug, String id, String message, Boolean pinned)
    {
        JsonObject payload = new JsonObject();
        if (message != null) payload.addProperty("message", message);
        if (pinned != null) payload.addProperty("pinned", pinned.booleanValue());
        return mutateSync("PATCH", baseUrl + "/clans/" + clanSlug + "/announcements/" + id, apiKey, payload);
    }

    public boolean deleteAnnouncement(String baseUrl, String apiKey, String clanSlug, String id)
    {
        return mutateSync("DELETE", baseUrl + "/clans/" + clanSlug + "/announcements/" + id, apiKey, null);
    }

    /** Synchronous POST/PATCH/DELETE returning whether the server accepted it. */
    private boolean mutateSync(String method, String url, String apiKey, JsonObject payload)
    {
        Request.Builder b = new Request.Builder().url(url).header("Authorization", "Bearer " + apiKey);
        if ("DELETE".equals(method)) b.delete();
        else b.method(method, RequestBody.create(JSON, payload != null ? gson.toJson(payload) : "{}"));
        try (Response response = httpClient.newCall(b.build()).execute())
        {
            return response.isSuccessful();
        }
        catch (Exception e)
        {
            log.warn("{} {} failed: {}", method, url, e.getMessage());
            return false;
        }
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
