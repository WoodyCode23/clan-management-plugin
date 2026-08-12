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
import java.util.HashMap;
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
        submitPb(baseUrl, apiKey, clanSlug, rsn, bossKey, teamSize, timeMs, source, teamMembers, true);
    }

    /**
     * announce=false marks a NATURAL baseline (a completion that was not "(new personal best)")
     * so the server records the time quietly - no activity feed entry, no Discord posts.
     */
    public void submitPb(String baseUrl, String apiKey, String clanSlug,
                         String rsn, String bossKey, int teamSize, int timeMs, String source, String teamMembers,
                         boolean announce)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);
        payload.addProperty("source", source);
        payload.addProperty("announce", announce);
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
                            String teamMembers, String screenshotB64, boolean announce)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);
        payload.addProperty("source", source);
        payload.addProperty("announce", announce);
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
            checkAuth(response.code());
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

    /**
     * Sync this player's Achievement Diary + Quest standing (counts read from the in-game varbits /
     * Quest states). The server stores one summary row per player and shows it on the Achievements
     * page + profile; a newly-earned Quest/Diary Cape posts a milestone to the feed.
     */
    /** One diary region's tier completion, for the profile drill-down. */
    public static class DiaryRegion
    {
        public final String name;
        public final boolean easy, medium, hard, elite;
        public DiaryRegion(String name, boolean easy, boolean medium, boolean hard, boolean elite)
        {
            this.name = name; this.easy = easy; this.medium = medium; this.hard = hard; this.elite = elite;
        }
    }

    public void syncAchievementSummary(String baseUrl, String apiKey, String clanSlug, String rsn,
                                       int questPoints, int questsComplete, int questsTotal,
                                       int diaryEasy, int diaryMedium, int diaryHard, int diaryElite,
                                       java.util.List<DiaryRegion> diaries, java.util.List<String> questsMissing,
                                       String accountType)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        // Exact in-game account type (varbit 1777) — the only source that knows GIM flavours.
        if (accountType != null) payload.addProperty("accountType", accountType);
        payload.addProperty("questPoints", questPoints);
        payload.addProperty("questsComplete", questsComplete);
        payload.addProperty("questsTotal", questsTotal);
        payload.addProperty("diaryEasy", diaryEasy);
        payload.addProperty("diaryMedium", diaryMedium);
        payload.addProperty("diaryHard", diaryHard);
        payload.addProperty("diaryElite", diaryElite);
        if (diaries != null && !diaries.isEmpty())
        {
            JsonArray arr = new JsonArray();
            for (DiaryRegion r : diaries)
            {
                JsonObject o = new JsonObject();
                o.addProperty("name", r.name);
                o.addProperty("easy", r.easy);
                o.addProperty("medium", r.medium);
                o.addProperty("hard", r.hard);
                o.addProperty("elite", r.elite);
                arr.add(o);
            }
            payload.add("diaries", arr);
        }
        if (questsMissing != null)
        {
            JsonArray arr = new JsonArray();
            for (String q : questsMissing) arr.add(q);
            payload.add("questsMissing", arr);
        }
        postAsync(baseUrl + "/clans/" + clanSlug + "/achievement-summary", apiKey, payload, "Achievement summary sync");
    }

    /**
     * Report the texts of the in-game GIM Group side panel. The server keeps only strings that
     * match clan-roster names and auto-creates/extends the reporter's team from them; everything
     * else (labels, world numbers) is discarded server-side.
     */
    public JsonObject reportGimGroup(String baseUrl, String apiKey, String clanSlug, String rsn,
                                     String accountType, java.util.List<String> texts)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        if (accountType != null) payload.addProperty("accountType", accountType);
        JsonArray arr = new JsonArray();
        for (String t : texts) arr.add(t);
        payload.add("texts", arr);
        try
        {
            Request request = new Request.Builder()
                .url(baseUrl + "/clans/" + clanSlug + "/gim-group")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(JSON, gson.toJson(payload)))
                .build();
            try (Response response = httpClient.newCall(request).execute())
            {
                checkAuth(response.code());
                if (!response.isSuccessful() || response.body() == null) return null;
                return gson.fromJson(response.body().string(), JsonObject.class);
            }
        }
        catch (Exception e)
        {
            log.debug("GIM group report failed", e);
            return null;
        }
    }

    /**
     * Claim a clan rank. The plugin evaluates requirements LOCALLY and sends only the result
     * (eligible + what's missing) — never any bank/item data. The server pings staff.
     */
    public void requestRank(String baseUrl, String apiKey, String clanSlug, String rsn,
                            String rankName, boolean eligible, java.util.List<String> missing)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        payload.addProperty("rankName", rankName);
        payload.addProperty("eligible", eligible);
        JsonArray arr = new JsonArray();
        for (String m : missing) arr.add(m);
        payload.add("missing", arr);
        postAsync(baseUrl + "/clans/" + clanSlug + "/rank-request", apiKey, payload, "Rank request");
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
                                                         String skill, String period, boolean records)
    {
        HttpUrl.Builder ub = HttpUrl.parse(baseUrl + "/clans/" + clanSlug + "/leaderboard").newBuilder()
            .addQueryParameter("board", "xp")
            .addQueryParameter("period", period)
            .addQueryParameter("limit", "20");
        if (records) ub.addQueryParameter("view", "records"); // best-ever window instead of current
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
                LeaderboardEntry le = new LeaderboardEntry(rank, rsn, "member", value, 0, value);
                if (o.has("accountType") && !o.get("accountType").isJsonNull()) le.accountType = o.get("accountType").getAsString();
                entries.add(le);
            }
        }
        catch (Exception ex)
        {
            log.warn("fetchXpLeaderboard failed", ex);
        }
        return entries;
    }

    /** Boss-KC clan leaderboard (current standings or gained-over-period), served by our API
     *  from WiseOldMan bulk group data. */
    public List<LeaderboardEntry> fetchKcLeaderboard(String baseUrl, String apiKey, String clanSlug,
                                                     String boss, String period, boolean records)
    {
        HttpUrl.Builder ub = HttpUrl.parse(baseUrl + "/clans/" + clanSlug + "/leaderboard").newBuilder()
            .addQueryParameter("board", "kc")
            .addQueryParameter("period", period)
            .addQueryParameter("limit", "20");
        if (records) ub.addQueryParameter("view", "records"); // best-ever window instead of current
        if (boss != null && !boss.isEmpty()) ub.addQueryParameter("boss", boss);

        Request request = new Request.Builder().url(ub.build())
            .header("Authorization", "Bearer " + apiKey).get().build();
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Response response = httpClient.newCall(request).execute())
        {
            checkAuth(response.code());
            if (!response.isSuccessful() || response.body() == null) return entries;
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null || !root.has("entries")) return entries;
            for (JsonElement el : root.getAsJsonArray("entries"))
            {
                JsonObject o = el.getAsJsonObject();
                int rank = o.has("rank") ? o.get("rank").getAsInt() : entries.size() + 1;
                String rsn = o.has("rsn") ? o.get("rsn").getAsString() : "";
                long value = o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsLong() : 0;
                LeaderboardEntry le = new LeaderboardEntry(rank, rsn, "member", value, 0, value);
                if (o.has("accountType") && !o.get("accountType").isJsonNull()) le.accountType = o.get("accountType").getAsString();
                entries.add(le);
            }
        }
        catch (Exception ex)
        {
            log.warn("fetchKcLeaderboard failed", ex);
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
                           java.util.List<String[]> members, String clanName)
    {
        JsonObject payload = new JsonObject();
        if (clanName != null && !clanName.isEmpty())
        {
            payload.addProperty("clanName", clanName);
        }
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
        Map<String, Long> newestByBoss = new HashMap<>(); // bossKey -> newest createdAt (for Recent ordering)
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

            // When the time was set — display date + drives newest-first ordering of the
            // "Recent" overview so fresh times (new bosses) surface at the top.
            long createdMs = 0;
            String dateStr = "";
            if (pb.has("createdAt") && !pb.get("createdAt").isJsonNull())
            {
                try
                {
                    // Accept ISO ("…T…Z") and Postgres ("YYYY-MM-DD HH:MM:SS.ffffff+00") formats.
                    String raw = pb.get("createdAt").getAsString().trim()
                        .replace(' ', 'T').replaceAll("\\+00(:00)?$", "Z");
                    java.time.Instant inst = java.time.OffsetDateTime.parse(
                        raw.endsWith("Z") || raw.contains("+") ? raw : raw + "Z",
                        java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
                    createdMs = inst.toEpochMilli();
                    dateStr = new java.text.SimpleDateFormat("MM/dd").format(new java.util.Date(createdMs));
                }
                catch (Exception ignored) { /* unexpected format — no date shown */ }
            }
            newestByBoss.merge(bossKey, createdMs, Math::max);

            // Use bossKey directly as the category key (matches BossCategory.getKey())
            result.computeIfAbsent(bossKey, k -> new java.util.ArrayList<>())
                .add(new HiscoreEntry(0, timeSeconds, formattedTime, display, dateStr, bossKey, teamSize));
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

        // Order categories by their newest time (desc) so the "Recent" overview shows what the
        // clan just set — a brand-new boss's first times land at the top instead of alphabetically.
        Map<String, List<HiscoreEntry>> ordered = new java.util.LinkedHashMap<>();
        result.entrySet().stream()
            .sorted((a, b) -> Long.compare(
                newestByBoss.getOrDefault(b.getKey(), 0L), newestByBoss.getOrDefault(a.getKey(), 0L)))
            .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }

    /**
     * Synchronous GET — returns parsed JSON or null on error.
     */
    // Fired whenever the server rejects our key (401/403) so the plugin can WARN the member —
    // otherwise a bad/mispasted key silently drops every submission while the UI looks fine.
    private volatile Runnable onAuthFailure;
    public void setOnAuthFailure(Runnable cb) { this.onAuthFailure = cb; }

    private void checkAuth(int statusCode)
    {
        if ((statusCode == 401 || statusCode == 403) && onAuthFailure != null)
        {
            onAuthFailure.run();
        }
    }

    public JsonObject getSync(String url, String apiKey)
    {
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            checkAuth(response.code());
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
        public final String rank;       // in-game CC title (Senator, Xerician…)
        public final String ladderRank; // Discord-derived ladder rank id (heart_3, maxed…) — null if unlinked
        public final String accountType; // regular | ironman | hardcore | ultimate | gim | hcgim | unranked_gim
        public RosterMember(String rsn, String rank, String ladderRank, String accountType)
        { this.rsn = rsn; this.rank = rank; this.ladderRank = ladderRank; this.accountType = accountType; }
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
                o.has("rank") && !o.get("rank").isJsonNull() ? o.get("rank").getAsString() : null,
                o.has("ladderRank") && !o.get("ladderRank").isJsonNull() ? o.get("ladderRank").getAsString() : null,
                o.has("accountType") && !o.get("accountType").isJsonNull() ? o.get("accountType").getAsString() : null));
        }
        return out;
    }

    // ── Teams (GIM / shared profiles) ──
    public static class TeamMemberBrief
    {
        public final String rsn;
        public final String accountType;
        public TeamMemberBrief(String rsn, String accountType) { this.rsn = rsn; this.accountType = accountType; }
    }

    public static class TeamSummary
    {
        public final String name, slug, kind;
        public final List<TeamMemberBrief> members;
        public TeamSummary(String name, String slug, String kind, List<TeamMemberBrief> members)
        { this.name = name; this.slug = slug; this.kind = kind; this.members = members; }
    }

    public static class TeamProfileMember
    {
        public final String rsn, accountType;
        public final long exp;
        public final Double ehb;
        public final int clogObtained, clogTotal;
        public TeamProfileMember(String rsn, String accountType, long exp, Double ehb, int clogObtained, int clogTotal)
        { this.rsn = rsn; this.accountType = accountType; this.exp = exp; this.ehb = ehb; this.clogObtained = clogObtained; this.clogTotal = clogTotal; }
    }

    public static class TeamPb
    {
        public final String bossKey, holder;
        public final int teamSize, timeMs;
        public TeamPb(String bossKey, int teamSize, int timeMs, String holder)
        { this.bossKey = bossKey; this.teamSize = teamSize; this.timeMs = timeMs; this.holder = holder; }
    }

    public static class TeamDrop
    {
        public final String rsn, itemName, monsterName;
        public final int itemId;
        public final long value;
        public TeamDrop(String rsn, String itemName, int itemId, long value, String monsterName)
        { this.rsn = rsn; this.itemName = itemName; this.itemId = itemId; this.value = value; this.monsterName = monsterName; }
    }

    public static class TeamProfile
    {
        public final String name, slug, kind;
        public final long totalExp;
        public final double totalEhb;
        public final int clogUnion;
        public final List<TeamProfileMember> members;
        public final List<TeamDrop> recentDrops;
        public final List<TeamPb> pbs;
        public TeamProfile(String name, String slug, String kind, long totalExp, double totalEhb, int clogUnion,
                           List<TeamProfileMember> members, List<TeamDrop> recentDrops, List<TeamPb> pbs)
        { this.name = name; this.slug = slug; this.kind = kind; this.totalExp = totalExp; this.totalEhb = totalEhb;
          this.clogUnion = clogUnion; this.members = members; this.recentDrops = recentDrops; this.pbs = pbs; }
    }

    /** All teams (name + kind + member briefs) for the Members list. Never throws. */
    public List<TeamSummary> fetchTeams(String baseUrl, String apiKey, String clanSlug)
    {
        List<TeamSummary> out = new ArrayList<>();
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/teams", apiKey);
        if (root == null || !root.has("teams")) return out;
        for (JsonElement el : root.getAsJsonArray("teams"))
        {
            JsonObject o = el.getAsJsonObject();
            List<TeamMemberBrief> members = new ArrayList<>();
            if (o.has("members") && o.get("members").isJsonArray())
            {
                for (JsonElement me : o.getAsJsonArray("members"))
                {
                    JsonObject m = me.getAsJsonObject();
                    members.add(new TeamMemberBrief(
                        m.has("rsn") ? m.get("rsn").getAsString() : "",
                        m.has("accountType") && !m.get("accountType").isJsonNull() ? m.get("accountType").getAsString() : null));
                }
            }
            out.add(new TeamSummary(
                o.has("name") ? o.get("name").getAsString() : "",
                o.has("slug") ? o.get("slug").getAsString() : "",
                o.has("kind") ? o.get("kind").getAsString() : "gim",
                members));
        }
        return out;
    }

    /** One team's aggregated shared profile. Null on failure / unknown team. */
    public TeamProfile fetchTeamProfile(String baseUrl, String apiKey, String clanSlug, String teamSlug)
    {
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/teams/" + encodePath(teamSlug), apiKey);
        if (root == null || !root.has("team") || root.get("team").isJsonNull()) return null;
        JsonObject t = root.getAsJsonObject("team");

        List<TeamProfileMember> members = new ArrayList<>();
        if (root.has("members") && root.get("members").isJsonArray())
        {
            for (JsonElement el : root.getAsJsonArray("members"))
            {
                JsonObject m = el.getAsJsonObject();
                members.add(new TeamProfileMember(
                    m.has("rsn") ? m.get("rsn").getAsString() : "",
                    m.has("accountType") && !m.get("accountType").isJsonNull() ? m.get("accountType").getAsString() : null,
                    m.has("exp") && !m.get("exp").isJsonNull() ? m.get("exp").getAsLong() : 0,
                    m.has("ehb") && !m.get("ehb").isJsonNull() ? m.get("ehb").getAsDouble() : null,
                    m.has("clogObtained") && !m.get("clogObtained").isJsonNull() ? m.get("clogObtained").getAsInt() : 0,
                    m.has("clogTotal") && !m.get("clogTotal").isJsonNull() ? m.get("clogTotal").getAsInt() : 0));
            }
        }
        List<TeamDrop> drops = new ArrayList<>();
        if (root.has("recentDrops") && root.get("recentDrops").isJsonArray())
        {
            for (JsonElement el : root.getAsJsonArray("recentDrops"))
            {
                JsonObject d = el.getAsJsonObject();
                drops.add(new TeamDrop(
                    d.has("rsn") ? d.get("rsn").getAsString() : "",
                    d.has("itemName") ? d.get("itemName").getAsString() : "",
                    d.has("itemId") && !d.get("itemId").isJsonNull() ? d.get("itemId").getAsInt() : 0,
                    d.has("value") && !d.get("value").isJsonNull() ? d.get("value").getAsLong() : 0,
                    d.has("monsterName") && !d.get("monsterName").isJsonNull() ? d.get("monsterName").getAsString() : ""));
            }
        }
        List<TeamPb> pbs = new ArrayList<>();
        if (root.has("pbs") && root.get("pbs").isJsonArray())
        {
            for (JsonElement el : root.getAsJsonArray("pbs"))
            {
                JsonObject p = el.getAsJsonObject();
                pbs.add(new TeamPb(
                    p.has("bossKey") ? p.get("bossKey").getAsString() : "",
                    p.has("teamSize") && !p.get("teamSize").isJsonNull() ? p.get("teamSize").getAsInt() : 1,
                    p.has("timeMs") && !p.get("timeMs").isJsonNull() ? p.get("timeMs").getAsInt() : 0,
                    p.has("rsn") ? p.get("rsn").getAsString() : ""));
            }
        }
        JsonObject totals = root.has("totals") && root.get("totals").isJsonObject() ? root.getAsJsonObject("totals") : null;
        long totalExp = totals != null && totals.has("totalExp") && !totals.get("totalExp").isJsonNull() ? totals.get("totalExp").getAsLong() : 0;
        double totalEhb = totals != null && totals.has("totalEhb") && !totals.get("totalEhb").isJsonNull() ? totals.get("totalEhb").getAsDouble() : 0;
        int clogUnion = totals != null && totals.has("clogUnion") && !totals.get("clogUnion").isJsonNull() ? totals.get("clogUnion").getAsInt() : 0;

        return new TeamProfile(
            t.has("name") ? t.get("name").getAsString() : "",
            t.has("slug") ? t.get("slug").getAsString() : teamSlug,
            t.has("kind") ? t.get("kind").getAsString() : "gim",
            totalExp, totalEhb, clogUnion, members, drops, pbs);
    }

    public static class RankMode
    {
        public final String mode;                    // "default" | "clog_only" | "admin_set"
        public final String assignedRank;            // set only for admin_set
        public final java.util.List<String> heldRanks; // ranks held via Discord (current + everything below)
        public RankMode(String mode, String assignedRank, java.util.List<String> heldRanks)
        { this.mode = mode; this.assignedRank = assignedRank; this.heldRanks = heldRanks; }
    }

    /** How a member's ranks should be evaluated + which ranks they already hold via their Discord rank. */
    public RankMode fetchRankMode(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/rank-mode/" + encodePath(rsn), apiKey);
        if (root == null) return new RankMode("default", null, new ArrayList<>());
        String mode = root.has("mode") && !root.get("mode").isJsonNull() ? root.get("mode").getAsString() : "default";
        String assigned = root.has("assignedRank") && !root.get("assignedRank").isJsonNull()
            ? root.get("assignedRank").getAsString() : null;
        java.util.List<String> held = new ArrayList<>();
        if (root.has("heldRanks") && root.get("heldRanks").isJsonArray())
        {
            for (JsonElement el : root.getAsJsonArray("heldRanks")) held.add(el.getAsString());
        }
        return new RankMode(mode, assigned, held);
    }

    /** Boss kill counts (WiseOldMan, via our server) keyed by WOM metric name → KC. Public hiscore data. */
    public Map<String, Integer> fetchPlayerKc(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        Map<String, Integer> out = new HashMap<>();
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/player-kc/" + encodePath(rsn), apiKey);
        if (root == null || !root.has("bosses")) return out;
        JsonObject b = root.getAsJsonObject("bosses");
        for (Map.Entry<String, JsonElement> e : b.entrySet())
        {
            try { out.put(e.getKey().toLowerCase(), e.getValue().getAsInt()); }
            catch (Exception ignored) { /* skip non-numeric */ }
        }
        return out;
    }

    /** One collection-log slot + whether the viewed player owns it (and how many they've logged). */
    public static class ClogCatalogItem
    {
        public final int itemId;
        public final String name;
        public final String tab;
        public final String category;
        public final boolean owned;
        public final int quantity; // obtained count from the collection log (0 if not owned)
        public ClogCatalogItem(int itemId, String name, String tab, String category, boolean owned, int quantity)
        {
            this.itemId = itemId; this.name = name; this.tab = tab; this.category = category;
            this.owned = owned; this.quantity = quantity;
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

        // itemId -> obtained quantity (owned = present in the map)
        Map<Integer, Integer> owned = new HashMap<>();
        if (root.has("entries"))
        {
            for (JsonElement el : root.getAsJsonArray("entries"))
            {
                JsonObject o = el.getAsJsonObject();
                if (o.has("itemId") && !o.get("itemId").isJsonNull())
                {
                    int qty = o.has("quantity") && !o.get("quantity").isJsonNull() ? o.get("quantity").getAsInt() : 1;
                    owned.put(o.get("itemId").getAsInt(), Math.max(qty, 1));
                }
            }
        }

        List<ClogCatalogItem> items = new ArrayList<>();
        if (root.has("catalog"))
        {
            for (JsonElement el : root.getAsJsonArray("catalog"))
            {
                JsonObject o = el.getAsJsonObject();
                int itemId = o.has("itemId") ? o.get("itemId").getAsInt() : -1;
                Integer qty = owned.get(itemId);
                items.add(new ClogCatalogItem(
                    itemId,
                    o.has("itemName") && !o.get("itemName").isJsonNull() ? o.get("itemName").getAsString() : "",
                    o.has("tab") && !o.get("tab").isJsonNull() ? o.get("tab").getAsString() : "Other",
                    o.has("category") && !o.get("category").isJsonNull() ? o.get("category").getAsString() : "Other",
                    qty != null,
                    qty != null ? qty : 0));
            }
        }

        int obtained = root.has("obtained") && !root.get("obtained").isJsonNull() ? root.get("obtained").getAsInt() : owned.size();
        int total = root.has("totalSlots") && !root.get("totalSlots").isJsonNull() ? root.get("totalSlots").getAsInt() : items.size();
        return new PlayerClog(obtained, total, items);
    }

    /** One clog-race event's metadata. */
    public static class ClogRaceEvent
    {
        public final String id;
        public final String name;
        public final String status;
        public final String startTime;
        public final String endTime;
        public final String winnerTeamId;
        public ClogRaceEvent(String id, String name, String status, String startTime, String endTime, String winnerTeamId)
        {
            this.id = id; this.name = name; this.status = status;
            this.startTime = startTime; this.endTime = endTime; this.winnerTeamId = winnerTeamId;
        }
    }

    /** One board slot (target collection-log item) in the current clog race. */
    public static class ClogRaceBoardItem
    {
        public final String id;
        public final String itemName;
        public final int itemId;
        public final String raid;
        public final boolean isHardMode;
        public final int sortOrder;
        public ClogRaceBoardItem(String id, String itemName, int itemId, String raid, boolean isHardMode, int sortOrder)
        {
            this.id = id; this.itemName = itemName; this.itemId = itemId;
            this.raid = raid; this.isHardMode = isHardMode; this.sortOrder = sortOrder;
        }
    }

    /** One competing team in the clog race. */
    public static class ClogRaceTeam
    {
        public final String teamId;
        public final String name;
        public ClogRaceTeam(String teamId, String name) { this.teamId = teamId; this.name = name; }
    }

    /** One board-item fill: a team obtaining a target item. */
    public static class ClogRaceFill
    {
        public final String teamId;
        public final String boardItemId;
        public final String filledByRsn;
        public final String method;
        public final String filledAt;
        public ClogRaceFill(String teamId, String boardItemId, String filledByRsn, String method, String filledAt)
        {
            this.teamId = teamId; this.boardItemId = boardItemId;
            this.filledByRsn = filledByRsn; this.method = method; this.filledAt = filledAt;
        }
    }

    /** One team's standing (fill count) in the clog race. */
    public static class ClogRaceStanding
    {
        public final String teamId;
        public final String name;
        public final int count;
        public final String reachedAt;
        public ClogRaceStanding(String teamId, String name, int count, String reachedAt)
        {
            this.teamId = teamId; this.name = name; this.count = count; this.reachedAt = reachedAt;
        }
    }

    /** The full current clog-race snapshot (event + board + teams + fills + standings) for the Raid Race tab. */
    public static class ClogRace
    {
        public final ClogRaceEvent event;
        public final List<ClogRaceBoardItem> board;
        public final List<ClogRaceTeam> teams;
        public final List<ClogRaceFill> fills;
        public final List<ClogRaceStanding> standings;
        public ClogRace(ClogRaceEvent event, List<ClogRaceBoardItem> board, List<ClogRaceTeam> teams,
                         List<ClogRaceFill> fills, List<ClogRaceStanding> standings)
        {
            this.event = event; this.board = board; this.teams = teams;
            this.fills = fills; this.standings = standings;
        }
    }

    /**
     * Fetch the clan's current clog race (event + board + teams + fills + standings) for the
     * read-only Raid Race tab. Returns null when there is no current event (404) or on any
     * request/parse failure.
     */
    public ClogRace fetchClogRace(String baseUrl, String apiKey, String clanSlug)
    {
        JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/clog-events/current", apiKey);
        if (root == null) return null;

        try
        {
            ClogRaceEvent event = null;
            if (root.has("event") && root.get("event").isJsonObject())
            {
                JsonObject e = root.getAsJsonObject("event");
                event = new ClogRaceEvent(
                    e.has("id") && !e.get("id").isJsonNull() ? e.get("id").getAsString() : null,
                    e.has("name") && !e.get("name").isJsonNull() ? e.get("name").getAsString() : "",
                    e.has("status") && !e.get("status").isJsonNull() ? e.get("status").getAsString() : null,
                    e.has("startTime") && !e.get("startTime").isJsonNull() ? e.get("startTime").getAsString() : null,
                    e.has("endTime") && !e.get("endTime").isJsonNull() ? e.get("endTime").getAsString() : null,
                    e.has("winnerTeamId") && !e.get("winnerTeamId").isJsonNull() ? e.get("winnerTeamId").getAsString() : null);
            }

            List<ClogRaceBoardItem> board = new ArrayList<>();
            if (root.has("board") && root.get("board").isJsonArray())
            {
                for (JsonElement el : root.getAsJsonArray("board"))
                {
                    JsonObject o = el.getAsJsonObject();
                    board.add(new ClogRaceBoardItem(
                        o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsString() : null,
                        o.has("itemName") && !o.get("itemName").isJsonNull() ? o.get("itemName").getAsString() : "",
                        o.has("itemId") && !o.get("itemId").isJsonNull() ? o.get("itemId").getAsInt() : 0,
                        o.has("raid") && !o.get("raid").isJsonNull() ? o.get("raid").getAsString() : null,
                        o.has("isHardMode") && !o.get("isHardMode").isJsonNull() && o.get("isHardMode").getAsBoolean(),
                        o.has("sortOrder") && !o.get("sortOrder").isJsonNull() ? o.get("sortOrder").getAsInt() : 0));
                }
            }

            List<ClogRaceTeam> teams = new ArrayList<>();
            if (root.has("teams") && root.get("teams").isJsonArray())
            {
                for (JsonElement el : root.getAsJsonArray("teams"))
                {
                    JsonObject o = el.getAsJsonObject();
                    teams.add(new ClogRaceTeam(
                        o.has("teamId") && !o.get("teamId").isJsonNull() ? o.get("teamId").getAsString() : null,
                        o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : ""));
                }
            }

            List<ClogRaceFill> fills = new ArrayList<>();
            if (root.has("fills") && root.get("fills").isJsonArray())
            {
                for (JsonElement el : root.getAsJsonArray("fills"))
                {
                    JsonObject o = el.getAsJsonObject();
                    fills.add(new ClogRaceFill(
                        o.has("teamId") && !o.get("teamId").isJsonNull() ? o.get("teamId").getAsString() : null,
                        o.has("boardItemId") && !o.get("boardItemId").isJsonNull() ? o.get("boardItemId").getAsString() : null,
                        o.has("filledByRsn") && !o.get("filledByRsn").isJsonNull() ? o.get("filledByRsn").getAsString() : null,
                        o.has("method") && !o.get("method").isJsonNull() ? o.get("method").getAsString() : null,
                        o.has("filledAt") && !o.get("filledAt").isJsonNull() ? o.get("filledAt").getAsString() : null));
                }
            }

            List<ClogRaceStanding> standings = new ArrayList<>();
            if (root.has("standings") && root.get("standings").isJsonArray())
            {
                for (JsonElement el : root.getAsJsonArray("standings"))
                {
                    JsonObject o = el.getAsJsonObject();
                    standings.add(new ClogRaceStanding(
                        o.has("teamId") && !o.get("teamId").isJsonNull() ? o.get("teamId").getAsString() : null,
                        o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : "",
                        o.has("count") && !o.get("count").isJsonNull() ? o.get("count").getAsInt() : 0,
                        o.has("reachedAt") && !o.get("reachedAt").isJsonNull() ? o.get("reachedAt").getAsString() : null));
                }
            }

            return new ClogRace(event, board, teams, fills, standings);
        }
        catch (Exception ex)
        {
            log.debug("parse clog-race failed: {}", ex.getMessage());
            return null;
        }
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
        public final int itemId;
        public final long value;
        public final String monsterName;
        public final int killCount;
        public final double points; // precise; rounded to 1dp only at display
        public PlayerDrop(String itemName, int itemId, long value, String monsterName, int killCount, double points)
        {
            this.itemName = itemName; this.itemId = itemId; this.value = value; this.monsterName = monsterName;
            this.killCount = killCount; this.points = points;
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
        // Diary + quest standing (null when the member hasn't synced with a current plugin yet)
        public final DiariesAndQuests diariesAndQuests;
        public final String accountType; // exact type incl GIM flavours (null if unknown)
        public final Double ehb;         // WOM type-aware EHB (null if unknown)
        public PlayerProfile(int clogObtained, int clogTotal, int caCompleted, int caTotal,
                             List<PlayerPb> pbs, List<PlayerDrop> drops, DiariesAndQuests diariesAndQuests,
                             String accountType, Double ehb)
        {
            this.clogObtained = clogObtained; this.clogTotal = clogTotal;
            this.caCompleted = caCompleted; this.caTotal = caTotal;
            this.pbs = pbs; this.drops = drops; this.diariesAndQuests = diariesAndQuests;
            this.accountType = accountType; this.ehb = ehb;
        }
    }

    /** A member's diary + quest standing, with per-region / missing-quest drill-down detail. */
    public static class DiariesAndQuests
    {
        public final int questPoints, questsComplete, questsTotal;
        public final boolean questCape, diaryCape;
        public final int diaryEasy, diaryMedium, diaryHard, diaryElite;
        public final List<DiaryRegion> diaries;       // may be empty (older sync)
        public final List<String> questsMissing;      // may be empty
        public DiariesAndQuests(int questPoints, int questsComplete, int questsTotal,
                                boolean questCape, boolean diaryCape,
                                int diaryEasy, int diaryMedium, int diaryHard, int diaryElite,
                                List<DiaryRegion> diaries, List<String> questsMissing)
        {
            this.questPoints = questPoints; this.questsComplete = questsComplete; this.questsTotal = questsTotal;
            this.questCape = questCape; this.diaryCape = diaryCape;
            this.diaryEasy = diaryEasy; this.diaryMedium = diaryMedium;
            this.diaryHard = diaryHard; this.diaryElite = diaryElite;
            this.diaries = diaries; this.questsMissing = questsMissing;
        }
    }

    /** A member's customizable "about" — the fields they set on the website. Any can be null. */
    public static class MemberAbout
    {
        public final String bio;
        public final String favoriteBoss;
        public final String favoriteSkill;
        public final String goal;
        public MemberAbout(String bio, String favoriteBoss, String favoriteSkill, String goal)
        {
            this.bio = bio; this.favoriteBoss = favoriteBoss;
            this.favoriteSkill = favoriteSkill; this.goal = goal;
        }
        public boolean isEmpty()
        {
            return (bio == null || bio.isEmpty()) && (favoriteBoss == null || favoriteBoss.isEmpty())
                && (favoriteSkill == null || favoriteSkill.isEmpty()) && (goal == null || goal.isEmpty());
        }
    }

    /** Fetch a member's customizable profile (bio / favorite boss+skill / goal). Never throws. */
    public MemberAbout fetchMemberAbout(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        try
        {
            JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/players/" + encodePath(rsn) + "/profile", apiKey);
            if (root == null || !root.has("profile") || root.get("profile").isJsonNull()) return null;
            JsonObject p = root.getAsJsonObject("profile");
            return new MemberAbout(str(p, "bio"), str(p, "favoriteBoss"), str(p, "favoriteSkill"), str(p, "goal"));
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private static String str(JsonObject o, String k)
    {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
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
                    o.has("itemId") && !o.get("itemId").isJsonNull() ? o.get("itemId").getAsInt() : 0,
                    o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsLong() : 0,
                    o.has("monsterName") && !o.get("monsterName").isJsonNull() ? o.get("monsterName").getAsString() : "",
                    o.has("killCount") && !o.get("killCount").isJsonNull() ? o.get("killCount").getAsInt() : 0,
                    o.has("points") && !o.get("points").isJsonNull() ? o.get("points").getAsDouble() : 0.0));
            }
        }

        int clogObtained = root.has("collectionLogCount") && !root.get("collectionLogCount").isJsonNull() ? root.get("collectionLogCount").getAsInt() : 0;
        int clogTotal = root.has("collectionLogTotal") && !root.get("collectionLogTotal").isJsonNull() ? root.get("collectionLogTotal").getAsInt() : 0;
        int caCompleted = root.has("caCompleted") && !root.get("caCompleted").isJsonNull() ? root.get("caCompleted").getAsInt() : 0;
        int caTotal = root.has("caTotal") && !root.get("caTotal").isJsonNull() ? root.get("caTotal").getAsInt() : 0;

        DiariesAndQuests dq = null;
        if (root.has("diariesAndQuests") && !root.get("diariesAndQuests").isJsonNull())
        {
            JsonObject d = root.getAsJsonObject("diariesAndQuests");
            List<DiaryRegion> regions = new ArrayList<>();
            if (d.has("diaries") && d.get("diaries").isJsonArray())
            {
                for (JsonElement el : d.getAsJsonArray("diaries"))
                {
                    JsonObject r = el.getAsJsonObject();
                    regions.add(new DiaryRegion(
                        r.has("name") ? r.get("name").getAsString() : "",
                        r.has("easy") && r.get("easy").getAsBoolean(),
                        r.has("medium") && r.get("medium").getAsBoolean(),
                        r.has("hard") && r.get("hard").getAsBoolean(),
                        r.has("elite") && r.get("elite").getAsBoolean()));
                }
            }
            List<String> missing = new ArrayList<>();
            if (d.has("questsMissing") && d.get("questsMissing").isJsonArray())
            {
                for (JsonElement el : d.getAsJsonArray("questsMissing")) missing.add(el.getAsString());
            }
            dq = new DiariesAndQuests(
                d.has("questPoints") ? d.get("questPoints").getAsInt() : 0,
                d.has("questsComplete") ? d.get("questsComplete").getAsInt() : 0,
                d.has("questsTotal") ? d.get("questsTotal").getAsInt() : 0,
                d.has("questCape") && d.get("questCape").getAsBoolean(),
                d.has("diaryCape") && d.get("diaryCape").getAsBoolean(),
                d.has("diaryEasy") ? d.get("diaryEasy").getAsInt() : 0,
                d.has("diaryMedium") ? d.get("diaryMedium").getAsInt() : 0,
                d.has("diaryHard") ? d.get("diaryHard").getAsInt() : 0,
                d.has("diaryElite") ? d.get("diaryElite").getAsInt() : 0,
                regions, missing);
        }
        String accountType = null;
        Double ehb = null;
        if (root.has("player") && root.get("player").isJsonObject())
        {
            JsonObject p = root.getAsJsonObject("player");
            if (p.has("accountType") && !p.get("accountType").isJsonNull()) accountType = p.get("accountType").getAsString();
            if (p.has("ehb") && !p.get("ehb").isJsonNull()) ehb = p.get("ehb").getAsDouble();
        }
        return new PlayerProfile(clogObtained, clogTotal, caCompleted, caTotal, pbs, drops, dq, accountType, ehb);
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

    /** Active Boss/Skill-of-the-Week race (event + leaderboard). Null when none. */
    public JsonObject fetchActiveEvent(String baseUrl, String apiKey, String clanSlug)
    {
        try
        {
            JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/events/active", apiKey);
            boolean hasActive = root != null && root.has("event") && !root.get("event").isJsonNull();
            boolean hasUpcoming = root != null && root.has("upcoming") && !root.get("upcoming").isJsonNull();
            if (hasActive || hasUpcoming)
            {
                return root;
            }
        }
        catch (Exception e)
        {
            log.debug("active event fetch failed", e);
        }
        return null;
    }

    /** Item ids of every collection-log item (server catalog). Sync; empty set on failure. */
    public java.util.Set<Integer> fetchClogCatalogIds(String baseUrl, String apiKey, String clanSlug)
    {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        try
        {
            JsonObject root = getSync(baseUrl + "/clans/" + clanSlug + "/collection-log/catalog-ids", apiKey);
            if (root != null && root.has("itemIds"))
            {
                for (com.google.gson.JsonElement el : root.getAsJsonArray("itemIds"))
                {
                    ids.add(el.getAsInt());
                }
            }
        }
        catch (Exception e)
        {
            log.debug("clog catalog ids fetch failed", e);
        }
        return ids;
    }

    /**
     * Live clog quantity bumps from loot events (already-unlocked items only, server-side).
     * items: itemId -> quantity looted this kill.
     */
    public void submitClogIncrements(String baseUrl, String apiKey, String clanSlug,
                                     String rsn, java.util.Map<Integer, Integer> items)
    {
        if (items.isEmpty()) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (java.util.Map.Entry<Integer, Integer> e : items.entrySet())
        {
            JsonObject item = new JsonObject();
            item.addProperty("itemId", e.getKey());
            item.addProperty("quantity", e.getValue());
            arr.add(item);
        }
        payload.add("items", arr);
        postAsync(baseUrl + "/clans/" + clanSlug + "/collection-log/increment", apiKey, payload, "Clog increment");
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
                checkAuth(response.code());
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

    /** PUT/DELETE helper for admin mutations (rank overrides). */
    private void mutateAsync(String url, String apiKey, JsonObject payload, String method, String label)
    {
        Request.Builder b = new Request.Builder().url(url).header("Authorization", "Bearer " + apiKey);
        if ("DELETE".equals(method))
        {
            b.delete();
        }
        else
        {
            b.put(RequestBody.create(JSON, gson.toJson(payload == null ? new JsonObject() : payload)));
        }
        httpClient.newCall(b.build()).enqueue(new Callback()
        {
            @Override public void onFailure(Call call, IOException e) { log.error("Failed {}", label, e); }
            @Override public void onResponse(Call call, Response response)
            {
                int code = response.code();
                checkAuth(code);
                response.close();
                if (code >= 200 && code < 300) { log.debug("{} ok", label); }
                else { log.error("{} failed with status {}", label, code); }
            }
        });
    }

    /** Admin: set a member's rank eval override (clog_only / admin_set). Sticky until cleared. */
    public void setRankOverride(String baseUrl, String apiKey, String clanSlug, String rsn,
                                String mode, String assignedRank, String setBy)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("mode", mode);
        if (assignedRank != null) payload.addProperty("assignedRank", assignedRank);
        if (setBy != null) payload.addProperty("setBy", setBy);
        mutateAsync(baseUrl + "/clans/" + clanSlug + "/rank-overrides/" + encodePath(rsn), apiKey, payload, "PUT", "Set rank override");
    }

    /** Admin: clear a member's rank override → back to default auto-eval. */
    public void clearRankOverride(String baseUrl, String apiKey, String clanSlug, String rsn)
    {
        mutateAsync(baseUrl + "/clans/" + clanSlug + "/rank-overrides/" + encodePath(rsn), apiKey, null, "DELETE", "Clear rank override");
    }
}
