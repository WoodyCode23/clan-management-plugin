package com.droplogger;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class BingoService
{
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    private String apiUrl;
    private String apiKey;
    private String adminKey;

    // Cached config — refreshed every 5 minutes
    private BingoConfig cachedConfig;
    private long configFetchTime = 0;
    private static final long CONFIG_CACHE_TTL = 5 * 60 * 1000;

    @Inject
    public BingoService(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
        this.gson = gson;
    }

    public void configure(String apiUrl, String apiKey, String adminKey)
    {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.adminKey = adminKey;
        this.cachedConfig = null;
        this.configFetchTime = 0;
    }

    public boolean isConfigured()
    {
        return apiUrl != null && !apiUrl.isEmpty();
    }

    // ── GET methods ──

    public BingoConfig fetchBingoConfig() throws IOException
    {
        long now = System.currentTimeMillis();
        if (cachedConfig != null && (now - configFetchTime) < CONFIG_CACHE_TTL)
        {
            return cachedConfig;
        }

        JsonObject root = doGet("getBingoConfig");

        List<BingoTile> tiles = new ArrayList<>();
        if (root.has("tiles"))
        {
            for (JsonElement elem : root.getAsJsonArray("tiles"))
            {
                JsonObject t = elem.getAsJsonObject();
                tiles.add(new BingoTile(
                    t.get("code").getAsString(),
                    t.has("name") ? t.get("name").getAsString() : "",
                    t.has("type") ? t.get("type").getAsString() : "drop",
                    t.has("metric") ? t.get("metric").getAsString() : "",
                    t.has("threshold") ? t.get("threshold").getAsDouble() : 100,
                    t.has("max") ? t.get("max").getAsDouble() : 200,
                    t.has("row") ? t.get("row").getAsInt() : 0,
                    t.has("col") ? t.get("col").getAsInt() : 0
                ));
            }
        }

        List<BingoTeam> teams = new ArrayList<>();
        if (root.has("teams"))
        {
            for (JsonElement elem : root.getAsJsonArray("teams"))
            {
                JsonObject t = elem.getAsJsonObject();
                teams.add(new BingoTeam(
                    t.get("code").getAsString(),
                    t.has("name") ? t.get("name").getAsString() : "",
                    0, 0, 0, 0
                ));
            }
        }

        List<BingoBounty> bounties = new ArrayList<>();
        if (root.has("bounties"))
        {
            for (JsonElement elem : root.getAsJsonArray("bounties"))
            {
                JsonObject b = elem.getAsJsonObject();
                bounties.add(new BingoBounty(
                    b.get("number").getAsInt(),
                    b.has("description") ? b.get("description").getAsString() : "",
                    b.has("releaseTime") ? b.get("releaseTime").getAsString() : "",
                    b.has("points") ? b.get("points").getAsDouble() : 0,
                    b.has("winner") ? b.get("winner").getAsString() : "",
                    b.has("hintFired") && b.get("hintFired").getAsBoolean(),
                    b.has("releaseFired") && b.get("releaseFired").getAsBoolean()
                ));
            }
        }

        Map<String, String> roster = new LinkedHashMap<>();
        if (root.has("roster"))
        {
            for (JsonElement elem : root.getAsJsonArray("roster"))
            {
                JsonObject r = elem.getAsJsonObject();
                String rsn = r.has("rsn") ? r.get("rsn").getAsString() : "";
                String team = r.has("team") ? r.get("team").getAsString() : "";
                if (!rsn.isEmpty()) roster.put(rsn.toLowerCase(), team);
            }
        }

        cachedConfig = new BingoConfig(
            root.has("gridRows") ? root.get("gridRows").getAsInt() : 5,
            root.has("gridCols") ? root.get("gridCols").getAsInt() : 5,
            root.has("eventName") ? root.get("eventName").getAsString() : "Bingo",
            root.has("startDate") ? root.get("startDate").getAsString() : "",
            root.has("endDate") ? root.get("endDate").getAsString() : "",
            root.has("hintMinutesBefore") ? root.get("hintMinutesBefore").getAsInt() : 15,
            tiles, teams, bounties, roster
        );
        configFetchTime = now;
        return cachedConfig;
    }

    public Map<String, Double> fetchTeamProgress(String teamCode) throws IOException
    {
        JsonObject root = doGet("getTeamProgress", "team", teamCode);
        Map<String, Double> progress = new LinkedHashMap<>();
        if (root.has("progress"))
        {
            for (JsonElement elem : root.getAsJsonArray("progress"))
            {
                JsonObject p = elem.getAsJsonObject();
                String tileCode = p.has("tileCode") ? p.get("tileCode").getAsString() : "";
                double points = p.has("points") ? p.get("points").getAsDouble() : 0;
                if (!tileCode.isEmpty()) progress.put(tileCode, points);
            }
        }
        return progress;
    }

    public BingoStandings fetchAllStandings() throws IOException
    {
        JsonObject root = doGet("getAllStandings");

        List<BingoTeam> teamStandings = new ArrayList<>();
        if (root.has("teamStandings"))
        {
            for (JsonElement elem : root.getAsJsonArray("teamStandings"))
            {
                JsonObject t = elem.getAsJsonObject();
                teamStandings.add(new BingoTeam(
                    t.get("code").getAsString(),
                    t.has("name") ? t.get("name").getAsString() : "",
                    t.has("tilePoints") ? t.get("tilePoints").getAsDouble() : 0,
                    t.has("bountyBonus") ? t.get("bountyBonus").getAsDouble() : 0,
                    t.has("totalPoints") ? t.get("totalPoints").getAsDouble() : 0,
                    t.has("rank") ? t.get("rank").getAsInt() : 0
                ));
            }
        }

        List<BingoStandings.PlayerStanding> individualStandings = new ArrayList<>();
        if (root.has("individualStandings"))
        {
            for (JsonElement elem : root.getAsJsonArray("individualStandings"))
            {
                JsonObject p = elem.getAsJsonObject();
                individualStandings.add(new BingoStandings.PlayerStanding(
                    p.has("rank") ? p.get("rank").getAsInt() : 0,
                    p.has("rsn") ? p.get("rsn").getAsString() : "",
                    p.has("team") ? p.get("team").getAsString() : "",
                    p.has("points") ? p.get("points").getAsDouble() : 0
                ));
            }
        }

        return new BingoStandings(teamStandings, individualStandings);
    }

    public List<Map<String, Object>> fetchDroplog(String teamFilter, int limit) throws IOException
    {
        JsonObject root;
        if (teamFilter != null && !teamFilter.isEmpty())
        {
            root = doGet("getDroplog", "team", teamFilter, "limit", String.valueOf(limit));
        }
        else
        {
            root = doGet("getDroplog", "limit", String.valueOf(limit));
        }

        List<Map<String, Object>> drops = new ArrayList<>();
        if (root.has("drops"))
        {
            for (JsonElement elem : root.getAsJsonArray("drops"))
            {
                JsonObject d = elem.getAsJsonObject();
                Map<String, Object> drop = new LinkedHashMap<>();
                drop.put("timestamp", d.has("timestamp") ? d.get("timestamp").getAsString() : "");
                drop.put("rsn", d.has("rsn") ? d.get("rsn").getAsString() : "");
                drop.put("team", d.has("team") ? d.get("team").getAsString() : "");
                drop.put("item", d.has("item") ? d.get("item").getAsString() : "");
                drop.put("tile", d.has("tile") ? d.get("tile").getAsString() : "");
                drop.put("points", d.has("points") ? d.get("points").getAsDouble() : 0.0);
                drops.add(drop);
            }
        }
        return drops;
    }

    // ── POST methods ──

    public String submitDrop(String player, String item, String team, String timestamp) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "submitDrop");
        payload.addProperty("player", player);
        payload.addProperty("item", item);
        payload.addProperty("team", team);
        payload.addProperty("timestamp", timestamp);
        return doPost(payload, false);
    }

    public String updateTileProgress(String team, String tileCode, double points) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "updateTileProgress");
        payload.addProperty("team", team);
        payload.addProperty("tileCode", tileCode);
        payload.addProperty("points", points);
        return doPost(payload, false);
    }

    public String markBountyFired(int number, String field) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "markBountyFired");
        payload.addProperty("number", number);
        payload.addProperty("field", field);
        return doPost(payload, false);
    }

    // Admin methods

    public String adminUpdateBounty(int number, String winner) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminUpdateBounty");
        payload.addProperty("number", number);
        payload.addProperty("winner", winner);
        return doPost(payload, true);
    }

    public String adminManualProgress(String team, String tileCode, double points) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminManualProgress");
        payload.addProperty("team", team);
        payload.addProperty("tileCode", tileCode);
        payload.addProperty("points", points);
        return doPost(payload, true);
    }

    public String adminUpdateRoster(String rsn, String team) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminUpdateRoster");
        payload.addProperty("rsn", rsn);
        payload.addProperty("team", team);
        return doPost(payload, true);
    }

    public void invalidateConfigCache()
    {
        this.cachedConfig = null;
        this.configFetchTime = 0;
    }

    // ── HTTP helpers ──

    private JsonObject doGet(String action, String... params) throws IOException
    {
        if (!isConfigured()) throw new IOException("Bingo API not configured");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", action)
            .addQueryParameter("key", apiKey != null ? apiKey : "");

        for (int i = 0; i < params.length - 1; i += 2)
        {
            urlBuilder.addQueryParameter(params[i], params[i + 1]);
        }

        Request request = new Request.Builder().url(urlBuilder.build()).get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Bingo API returned status: " + response.code());
            }
            String body = response.body().string();
            return new JsonParser().parse(body).getAsJsonObject();
        }
    }

    private String doPost(JsonObject payload, boolean useAdminKey) throws IOException
    {
        if (!isConfigured()) throw new IOException("Bingo API not configured");

        payload.addProperty("key", apiKey != null ? apiKey : "");
        if (useAdminKey)
        {
            payload.addProperty("adminKey", adminKey != null ? adminKey : "");
        }

        RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
        Request request = new Request.Builder().url(apiUrl).post(body).build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Bingo API POST returned status: " + response.code());
            }
            String responseBody = response.body().string();
            JsonObject root = new JsonParser().parse(responseBody).getAsJsonObject();

            if (root.has("status") && "error".equals(root.get("status").getAsString()))
            {
                String message = root.has("message") ? root.get("message").getAsString() : "Unknown error";
                throw new IOException(message);
            }
            return root.has("message") ? root.get("message").getAsString() : "OK";
        }
    }
}
