package com.droplogger;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public BingoModels.BoardData fetchBoardData(String apiUrl, String teamCode, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Board API URL is not configured");
        }

        HttpUrl.Builder urlBuilder = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "board")
            .addQueryParameter("key", apiKey != null ? apiKey : "");

        if (teamCode != null && !teamCode.isEmpty())
        {
            urlBuilder.addQueryParameter("team", teamCode);
        }

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Board API returned status: " + response.code());
            }

            String body = response.body().string();
            return parseBoardData(body);
        }
    }

    /**
     * Fetch the set of valid drop item names from the "Drop Whitelist" sheet.
     * Returns a lowercase set for case-insensitive matching.
     */
    /**
     * Fetch valid drop items and their tile codes.
     * Returns a map of lowercase item name → tile code (e.g. "twisted bow" → "A5").
     */
    public Map<String, String> fetchValidDropMap(String apiUrl, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Board API URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "validDrops")
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("validDrops API returned status: " + response.code());
            }

            String body = response.body().string();
            log.debug("validDrops raw response (first 500 chars): {}", body.length() > 500 ? body.substring(0, 500) : body);
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            // Log if API returned a message (e.g. "No 'Item Listing' tab found")
            if (root.has("message"))
            {
                log.warn("validDrops API message: {}", root.get("message").getAsString());
            }

            Map<String, String> dropMap = new LinkedHashMap<>();
            if (root.has("drops"))
            {
                JsonArray drops = root.getAsJsonArray("drops");
                for (JsonElement elem : drops)
                {
                    JsonObject d = elem.getAsJsonObject();
                    String item = d.has("item") ? d.get("item").getAsString().trim() : "";
                    String tile = d.has("tile") ? d.get("tile").getAsString().trim() : "";
                    if (!item.isEmpty())
                    {
                        dropMap.put(item.toLowerCase(), tile);
                    }
                }
            }

            log.info("Loaded {} valid drop items from whitelist", dropMap.size());
            return dropMap;
        }
    }

    /**
     * Fetch plugin configuration (URLs) from the Settings tab via the BoardAPI.
     * Returns a map with keys: hiscoreApiUrl, clanDropLogUrl, discordWebhookUrl
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

    public String findTeam(String apiUrl, String rsn, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Board API URL is not configured");
        }

        HttpUrl.Builder urlBuilder = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "findTeam")
            .addQueryParameter("rsn", rsn)
            .addQueryParameter("key", apiKey != null ? apiKey : "");

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("findTeam API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            return root.has("team") ? root.get("team").getAsString() : "";
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

    private BingoModels.BoardData parseBoardData(String json)
    {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();

        List<BingoModels.Tile> tiles = new ArrayList<>();
        if (root.has("tiles"))
        {
            JsonArray tilesArray = root.getAsJsonArray("tiles");
            for (JsonElement elem : tilesArray)
            {
                JsonObject t = elem.getAsJsonObject();
                tiles.add(new BingoModels.Tile(
                    t.get("row").getAsInt(),
                    t.get("col").getAsInt(),
                    t.get("task").getAsString(),
                    t.get("completion").getAsDouble(),
                    t.has("points") ? t.get("points").getAsDouble() : 0,
                    t.has("bingoThreshold") ? t.get("bingoThreshold").getAsDouble() : 0,
                    t.has("maxThreshold") ? t.get("maxThreshold").getAsDouble() : 0
                ));
            }
        }

        List<BingoModels.Team> teams = new ArrayList<>();
        if (root.has("teams"))
        {
            JsonArray teamsArray = root.getAsJsonArray("teams");
            for (JsonElement elem : teamsArray)
            {
                JsonObject t = elem.getAsJsonObject();
                teams.add(new BingoModels.Team(
                    t.has("code") ? t.get("code").getAsString() : "",
                    t.get("name").getAsString(),
                    t.get("points").getAsDouble(),
                    t.get("rank").getAsInt()
                ));
            }
        }

        List<BingoModels.BountyResult> bountyResults = new ArrayList<>();
        if (root.has("bountyResults"))
        {
            JsonArray bountyArray = root.getAsJsonArray("bountyResults");
            for (JsonElement elem : bountyArray)
            {
                JsonObject b = elem.getAsJsonObject();
                bountyResults.add(new BingoModels.BountyResult(
                    b.get("bountyNumber").getAsInt(),
                    b.has("description") ? b.get("description").getAsString() : "",
                    b.has("winner") ? b.get("winner").getAsString() : ""
                ));
            }
        }

        List<BingoModels.TeamDrop> teamDrops = new ArrayList<>();
        if (root.has("teamDrops"))
        {
            JsonArray dropsArray = root.getAsJsonArray("teamDrops");
            for (JsonElement elem : dropsArray)
            {
                JsonObject d = elem.getAsJsonObject();
                teamDrops.add(new BingoModels.TeamDrop(
                    d.has("rsn") ? d.get("rsn").getAsString() : "",
                    d.has("dropName") ? d.get("dropName").getAsString() : "",
                    d.has("date") ? d.get("date").getAsString() : "",
                    d.has("points") ? d.get("points").getAsDouble() : 0,
                    d.has("tileCode") ? d.get("tileCode").getAsString() : "",
                    d.has("tileName") ? d.get("tileName").getAsString() : ""
                ));
            }
        }

        double teamTotalPoints = 0;
        if (root.has("teamTotalPoints"))
        {
            teamTotalPoints = root.get("teamTotalPoints").getAsDouble();
        }

        List<String> announcements = new ArrayList<>();
        if (root.has("announcements"))
        {
            JsonArray annArray = root.getAsJsonArray("announcements");
            for (JsonElement elem : annArray)
            {
                String msg = elem.getAsString().trim();
                if (!msg.isEmpty())
                {
                    announcements.add(msg);
                }
            }
        }

        return new BingoModels.BoardData(tiles, teams, bountyResults, teamDrops, teamTotalPoints, announcements);
    }
}
