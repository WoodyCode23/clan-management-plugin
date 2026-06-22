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
import java.util.Map;
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
        payload.addProperty("bossKey", bossKey);
        payload.addProperty("teamSize", teamSize);
        payload.addProperty("timeMs", timeMs);
        payload.addProperty("source", source);

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
                                       String rsn, java.util.List<ClogItem> itemList,
                                       int uniqueObtained, int uniqueTotal,
                                       Callback callback)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("rsn", rsn);
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
        payload.addProperty("type", type);
        payload.addProperty("detail", detail);

        postAsync(baseUrl + "/clans/" + clanSlug + "/achievements", apiKey, payload, "Platform achievement");
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
