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

@Slf4j
@Singleton
public class BoardDataService
{
    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public BoardDataService(OkHttpClient httpClient, Gson gson)
    {
        // Build a client with longer timeouts for Google Apps Script cold starts
        this.httpClient = httpClient.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
        this.gson = gson;
    }

    /**
     * Fetch plugin configuration from the Settings tab.
     * Returns a map with keys: clanDropLogUrl, discordWebhookUrl, clanName, announcement
     */
    public Map<String, String> fetchConfig(String apiUrl, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Board API URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "getConfig")
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("getConfig API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            Map<String, String> config = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet())
            {
                if (entry.getValue().isJsonPrimitive())
                {
                    config.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return config;
        }
    }

    /**
     * Fetch clan drop log leaderboard from ClanDropLog.gs.
     * @param period "monthly", "yearly", or "all"
     */
    public List<Map<String, Object>> fetchLeaderboard(String clanDropLogUrl, String apiKey, String period) throws IOException
    {
        if (clanDropLogUrl == null || clanDropLogUrl.isEmpty())
        {
            throw new IOException("Clan Drop Log URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(clanDropLogUrl).newBuilder()
            .addQueryParameter("action", "leaderboard")
            .addQueryParameter("period", period)
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Leaderboard API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<Map<String, Object>> players = new ArrayList<>();
            if (root.has("players"))
            {
                JsonArray arr = root.getAsJsonArray("players");
                for (JsonElement elem : arr)
                {
                    JsonObject p = elem.getAsJsonObject();
                    Map<String, Object> player = new LinkedHashMap<>();
                    player.put("rank", p.has("rank") ? p.get("rank").getAsInt() : 0);
                    player.put("rsn", p.has("rsn") ? p.get("rsn").getAsString() : "");
                    player.put("points", p.has("points") ? p.get("points").getAsInt() : 0);
                    player.put("drops", p.has("drops") ? p.get("drops").getAsInt() : 0);
                    player.put("value", p.has("value") ? p.get("value").getAsLong() : 0L);
                    players.add(player);
                }
            }
            return players;
        }
    }

    /**
     * Fetch recent drops from ClanDropLog.gs.
     */
    public List<Map<String, Object>> fetchRecentDrops(String clanDropLogUrl, String apiKey, int limit) throws IOException
    {
        if (clanDropLogUrl == null || clanDropLogUrl.isEmpty())
        {
            throw new IOException("Clan Drop Log URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(clanDropLogUrl).newBuilder()
            .addQueryParameter("action", "recent")
            .addQueryParameter("limit", String.valueOf(limit))
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Recent drops API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<Map<String, Object>> drops = new ArrayList<>();
            if (root.has("drops"))
            {
                JsonArray arr = root.getAsJsonArray("drops");
                for (JsonElement elem : arr)
                {
                    JsonObject d = elem.getAsJsonObject();
                    Map<String, Object> drop = new LinkedHashMap<>();
                    drop.put("player", d.has("player") ? d.get("player").getAsString() : "");
                    drop.put("item", d.has("item") ? d.get("item").getAsString() : "");
                    drop.put("value", d.has("value") ? d.get("value").getAsLong() : 0L);
                    drop.put("monster", d.has("monster") ? d.get("monster").getAsString() : "");
                    drop.put("points", d.has("points") ? d.get("points").getAsInt() : 0);
                    drop.put("timestamp", d.has("timestamp") ? d.get("timestamp").getAsString() : "");
                    drops.add(drop);
                }
            }
            return drops;
        }
    }

    /**
     * Fetch all drops for a specific player from ClanDropLog.gs.
     */
    public List<Map<String, Object>> fetchPlayerDrops(String clanDropLogUrl, String apiKey, String rsn) throws IOException
    {
        if (clanDropLogUrl == null || clanDropLogUrl.isEmpty())
        {
            throw new IOException("Clan Drop Log URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(clanDropLogUrl).newBuilder()
            .addQueryParameter("action", "playerDrops")
            .addQueryParameter("rsn", rsn)
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Player drops API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<Map<String, Object>> drops = new ArrayList<>();
            if (root.has("drops"))
            {
                JsonArray arr = root.getAsJsonArray("drops");
                for (JsonElement elem : arr)
                {
                    JsonObject d = elem.getAsJsonObject();
                    Map<String, Object> drop = new LinkedHashMap<>();
                    drop.put("item", d.has("item") ? d.get("item").getAsString() : "");
                    drop.put("value", d.has("value") ? d.get("value").getAsLong() : 0L);
                    drop.put("monster", d.has("monster") ? d.get("monster").getAsString() : "");
                    drop.put("kc", d.has("kc") ? d.get("kc").getAsInt() : 0);
                    drop.put("points", d.has("points") ? d.get("points").getAsInt() : 0);
                    drop.put("timestamp", d.has("timestamp") ? d.get("timestamp").getAsString() : "");
                    drops.add(drop);
                }
            }
            return drops;
        }
    }

    /**
     * Fetch the full clan drop whitelist with points, drop rates, KPH, and categories.
     */
    public List<Map<String, String>> fetchClanWhitelist(String clanDropLogUrl, String apiKey) throws IOException
    {
        if (clanDropLogUrl == null || clanDropLogUrl.isEmpty())
        {
            throw new IOException("Clan Drop Log URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(clanDropLogUrl).newBuilder()
            .addQueryParameter("action", "clanWhitelist")
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("clanWhitelist API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<Map<String, String>> items = new ArrayList<>();
            if (root.has("items"))
            {
                JsonArray arr = root.getAsJsonArray("items");
                for (JsonElement elem : arr)
                {
                    JsonObject d = elem.getAsJsonObject();
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("item", d.has("item") ? d.get("item").getAsString() : "");
                    item.put("source", d.has("source") ? d.get("source").getAsString() : "");
                    item.put("points", d.has("points") ? String.valueOf(d.get("points").getAsInt()) : "0");
                    item.put("dropRate", d.has("dropRate") ? d.get("dropRate").getAsString() : "");
                    item.put("kph", d.has("kph") ? d.get("kph").getAsString() : "");
                    item.put("category", d.has("category") ? d.get("category").getAsString() : "");
                    items.add(item);
                }
            }
            return items;
        }
    }

}
