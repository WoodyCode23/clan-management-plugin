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
import java.util.List;
import java.util.Map;
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
    public void submitDrop(String baseUrl, String apiKey, String clanSlug, DropEntry drop)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", drop.getPlayerName());
        addAccountHash(payload);
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
     * Submit a personal best to the platform API (defaults to "live" source).
     */
    public void submitPb(String baseUrl, String apiKey, String clanSlug,
                         String rsn, String bossKey, int teamSize, int timeMs)
    {
        submitPb(baseUrl, apiKey, clanSlug, rsn, bossKey, teamSize, timeMs, "live");
    }

    /**
     * Submit a personal best to the platform API with a specific source.
     */
    public void submitPb(String baseUrl, String apiKey, String clanSlug,
                         String rsn, String bossKey, int teamSize, int timeMs, String source)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);
        payload.addProperty("source", source);

        postAsync(baseUrl + "/clans/" + clanSlug + "/pbs", apiKey, payload, "Platform PB");
    }

    /**
     * Submit a PB synchronously and return its clan placement (1 = new clan record),
     * or 0 on failure / non-live. Call off the client thread (it blocks on the network).
     */
    public int submitPbSync(String baseUrl, String apiKey, String clanSlug,
                            String rsn, String bossKey, int teamSize, int timeMs, String source)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
        addAccountHash(payload);
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);
        payload.addProperty("source", source);

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
     * Redeem a website link code, binding this in-game account to the Discord user who
     * generated it. Sends the live account hash + RSN as proof of in-game control.
     * Blocks on the network — call off the client thread. Returns a human-readable result.
     */
    public String redeemLinkCode(String baseUrl, String apiKey, String clanSlug, String code, String rsn)
    {
        if (accountHash == null || accountHash.isEmpty())
        {
            return "Link failed: log in to the account you want to link first.";
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("code", code);
        payload.addProperty("rsn", rsn);
        payload.addProperty("accountHash", accountHash);

        Request request = new Request.Builder()
            .url(baseUrl + "/clans/" + clanSlug + "/link/redeem")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(JSON, gson.toJson(payload)))
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            String bodyStr = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful())
            {
                return "Account linked to your Solus profile.";
            }
            try
            {
                JsonObject err = gson.fromJson(bodyStr, JsonObject.class);
                if (err != null && err.has("error"))
                {
                    return "Link failed: " + err.get("error").getAsString();
                }
            }
            catch (Exception ignored) { /* fall through to generic message */ }
            return "Link failed (HTTP " + response.code() + ").";
        }
        catch (Exception ex)
        {
            log.warn("redeemLinkCode failed", ex);
            return "Link failed: could not reach the server.";
        }
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

    /** A clan activity feed item from the backend (replaces Wise Old Man's group activity). */
    public static class ActivityItem
    {
        public final String type;     // "achievement" | "pb" | "drop"
        public final String rsn;
        public final String detail;
        public final long value;       // pb: timeMs; drop: gp value; achievement: 0
        public final String createdAt;

        public ActivityItem(String type, String rsn, String detail, long value, String createdAt)
        {
            this.type = type;
            this.rsn = rsn;
            this.detail = detail;
            this.value = value;
            this.createdAt = createdAt;
        }
    }

    /** Fetch the clan activity feed (recent achievements, PBs, notable drops) from the backend. */
    public List<ActivityItem> fetchActivity(String baseUrl, String apiKey, String clanSlug, int limit)
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/clans/" + clanSlug + "/activity").newBuilder()
            .addQueryParameter("limit", String.valueOf(limit)).build();
        Request request = new Request.Builder().url(url)
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
                    o.has("detail") && !o.get("detail").isJsonNull() ? o.get("detail").getAsString() : "",
                    o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsLong() : 0,
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
    public Map<String, List<HiscoreEntry>> fetchAllPbs(String baseUrl, String apiKey, String clanSlug)
    {
        String url = baseUrl + "/clans/" + clanSlug + "/pbs";
        JsonObject response = getSync(url, apiKey);
        if (response == null || !response.has("leaderboard")) return null;

        Map<String, List<HiscoreEntry>> result = new java.util.LinkedHashMap<>();
        com.google.gson.JsonArray leaderboard = response.getAsJsonArray("leaderboard");

        for (com.google.gson.JsonElement elem : leaderboard)
        {
            JsonObject pb = elem.getAsJsonObject();
            String bossKey = pb.has("bossKey") ? pb.get("bossKey").getAsString() : "";
            int teamSize = pb.has("teamSize") ? pb.get("teamSize").getAsInt() : 1;
            String rsn = pb.has("rsn") ? pb.get("rsn").getAsString() : "";
            int timeMs = pb.has("timeMs") ? pb.get("timeMs").getAsInt() : 0;

            double timeSeconds = timeMs / 1000.0;
            int totalSec = (int) timeSeconds;
            int min = totalSec / 60;
            double sec = timeSeconds - (min * 60);
            String formattedTime = String.format("%d:%05.2f", min, sec);

            // Use bossKey directly as the category key (matches BossCategory.getKey())
            result.computeIfAbsent(bossKey, k -> new java.util.ArrayList<>())
                .add(new HiscoreEntry(0, timeSeconds, formattedTime, rsn, "", bossKey, teamSize));
        }

        // Sort each category by time and assign ranks
        for (List<HiscoreEntry> entries : result.values())
        {
            entries.sort(java.util.Comparator.comparingDouble(HiscoreEntry::getTimeSeconds));
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
