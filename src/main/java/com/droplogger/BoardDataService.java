package com.droplogger;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reads clan board data from the clan-platform REST API.
 * Endpoints: {base}/clans/{slug}/{leaderboard,drops,whitelist}, Bearer-authed.
 * Return shapes are kept identical to the legacy version so ClanPanel consumes them unchanged.
 */
@Slf4j
@Singleton
public class BoardDataService
{
    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public BoardDataService(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = gson;
    }

    /** GET {base}/clans/{slug}/{path}?query with Bearer auth. Returns parsed object, or null on failure. */
    private JsonObject get(String base, String slug, String key, String path, Map<String, String> query) throws IOException
    {
        if (base == null || base.isEmpty() || slug == null || slug.isEmpty())
        {
            return null;
        }
        HttpUrl parsed = HttpUrl.parse(base);
        if (parsed == null)
        {
            return null;
        }
        HttpUrl.Builder b = parsed.newBuilder().addPathSegment("clans").addPathSegment(slug);
        for (String seg : path.split("/"))
        {
            if (!seg.isEmpty()) b.addPathSegment(seg);
        }
        if (query != null)
        {
            for (Map.Entry<String, String> e : query.entrySet()) b.addQueryParameter(e.getKey(), e.getValue());
        }
        Request request = new Request.Builder().url(b.build())
            .header("Authorization", "Bearer " + (key != null ? key : ""))
            .get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException(path + " returned status " + response.code());
            }
            JsonElement el = new JsonParser().parse(response.body().string());
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        }
    }

    private static String str(JsonObject o, String k)
    {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    /**
     * Drops leaderboard ranked by points; merges value + drop count per member.
     * period: "monthly" | "yearly" | "all". Keys per row: rank, rsn, points, value, drops.
     */
    public List<Map<String, Object>> fetchLeaderboard(String base, String slug, String key, String period) throws IOException
    {
        String p = "monthly".equals(period) ? "month" : "yearly".equals(period) ? "year" : "all";

        Map<String, long[]> byRsn = new LinkedHashMap<>(); // rsn -> [points, value, drops]
        mergeBoard(base, slug, key, "points", p, byRsn, 0);
        mergeBoard(base, slug, key, "value", p, byRsn, 1);
        mergeBoard(base, slug, key, "count", p, byRsn, 2);

        List<Map<String, Object>> players = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byRsn.entrySet())
        {
            Map<String, Object> player = new LinkedHashMap<>();
            player.put("rsn", e.getKey());
            player.put("points", (int) e.getValue()[0]);
            player.put("value", e.getValue()[1]);
            player.put("drops", (int) e.getValue()[2]);
            players.add(player);
        }
        players.sort((a, b) -> Integer.compare((int) b.get("points"), (int) a.get("points")));
        int rank = 1;
        for (Map<String, Object> player : players) player.put("rank", rank++);
        return players;
    }

    private void mergeBoard(String base, String slug, String key, String board, String period,
                            Map<String, long[]> byRsn, int idx) throws IOException
    {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("board", board);
        q.put("period", period);
        q.put("limit", "100");
        JsonObject root = get(base, slug, key, "/leaderboard", q);
        if (root == null || !root.has("entries")) return;
        for (JsonElement el : root.getAsJsonArray("entries"))
        {
            JsonObject e = el.getAsJsonObject();
            String rsn = str(e, "rsn");
            long v = e.has("value") ? e.get("value").getAsLong() : 0L;
            byRsn.computeIfAbsent(rsn, k -> new long[3])[idx] = v;
        }
    }

    /** GET /drops?limit=N → recent drops. Keys: player, item, value, monster, points, timestamp. */
    public List<Map<String, Object>> fetchRecentDrops(String base, String slug, String key, int limit) throws IOException
    {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("limit", String.valueOf(limit));
        return mapDrops(get(base, slug, key, "/drops", q), true);
    }

    /** GET /drops?player=RSN → a player's drops. Keys: item, value, monster, kc, points, timestamp. */
    public List<Map<String, Object>> fetchPlayerDrops(String base, String slug, String key, String rsn) throws IOException
    {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("player", rsn);
        q.put("limit", "100");
        return mapDrops(get(base, slug, key, "/drops", q), false);
    }

    private List<Map<String, Object>> mapDrops(JsonObject root, boolean includePlayer)
    {
        List<Map<String, Object>> drops = new ArrayList<>();
        if (root == null || !root.has("drops")) return drops;
        for (JsonElement el : root.getAsJsonArray("drops"))
        {
            JsonObject d = el.getAsJsonObject();
            Map<String, Object> drop = new LinkedHashMap<>();
            if (includePlayer) drop.put("player", str(d, "rsn"));
            drop.put("item", str(d, "itemName"));
            drop.put("value", d.has("value") ? d.get("value").getAsLong() : 0L);
            drop.put("monster", str(d, "monsterName"));
            drop.put("kc", d.has("killCount") && !d.get("killCount").isJsonNull() ? d.get("killCount").getAsInt() : 0);
            drop.put("points", d.has("points") ? d.get("points").getAsInt() : 0);
            drop.put("timestamp", str(d, "createdAt"));
            drops.add(drop);
        }
        return drops;
    }

    /** GET /whitelist → items. Keys: item, source, points, dropRate, kph, category. */
    public List<Map<String, String>> fetchClanWhitelist(String base, String slug, String key) throws IOException
    {
        List<Map<String, String>> items = new ArrayList<>();
        JsonObject root = get(base, slug, key, "/whitelist", null);
        if (root == null || !root.has("whitelist")) return items;
        for (JsonElement el : root.getAsJsonArray("whitelist"))
        {
            JsonObject d = el.getAsJsonObject();
            Map<String, String> item = new LinkedHashMap<>();
            item.put("item", str(d, "itemName"));
            item.put("source", "");
            item.put("points", d.has("points") ? String.valueOf(d.get("points").getAsInt()) : "0");
            item.put("dropRate", "");
            item.put("kph", "");
            item.put("category", "");
            items.add(item);
        }
        return items;
    }
}
