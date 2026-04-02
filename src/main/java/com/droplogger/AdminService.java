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
public class AdminService
{
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public AdminService(OkHttpClient httpClient, Gson gson)
    {
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

    // ── Read operations (GET) ──

    public boolean verifyAdmin(String boardApiUrl, String apiKey, String adminKey) throws IOException
    {
        HttpUrl url = HttpUrl.parse(boardApiUrl).newBuilder()
            .addQueryParameter("action", "adminPing")
            .addQueryParameter("key", apiKey)
            .addQueryParameter("adminKey", adminKey)
            .build();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful()) return false;
            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            return root.has("admin") && root.get("admin").getAsBoolean();
        }
    }

    public List<BingoModels.WhitelistItem> getWhitelist(String boardApiUrl, String apiKey, String adminKey) throws IOException
    {
        HttpUrl url = HttpUrl.parse(boardApiUrl).newBuilder()
            .addQueryParameter("action", "whitelistAll")
            .addQueryParameter("key", apiKey)
            .addQueryParameter("adminKey", adminKey)
            .build();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Whitelist fetch returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<BingoModels.WhitelistItem> items = new ArrayList<>();
            if (root.has("drops"))
            {
                JsonArray drops = root.getAsJsonArray("drops");
                for (JsonElement elem : drops)
                {
                    JsonObject d = elem.getAsJsonObject();
                    items.add(new BingoModels.WhitelistItem(
                        d.has("item") ? d.get("item").getAsString() : "",
                        d.has("tile") ? d.get("tile").getAsString() : ""
                    ));
                }
            }
            return items;
        }
    }

    public Map<String, List<BingoModels.RosterPlayer>> getAllRosters(String boardApiUrl, String apiKey, String adminKey) throws IOException
    {
        HttpUrl url = HttpUrl.parse(boardApiUrl).newBuilder()
            .addQueryParameter("action", "allRosters")
            .addQueryParameter("key", apiKey)
            .addQueryParameter("adminKey", adminKey)
            .build();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Rosters fetch returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            Map<String, List<BingoModels.RosterPlayer>> rosters = new LinkedHashMap<>();
            if (root.has("rosters"))
            {
                JsonObject rostersObj = root.getAsJsonObject("rosters");
                for (Map.Entry<String, JsonElement> entry : rostersObj.entrySet())
                {
                    List<BingoModels.RosterPlayer> players = new ArrayList<>();
                    JsonArray arr = entry.getValue().getAsJsonArray();
                    for (JsonElement elem : arr)
                    {
                        JsonObject p = elem.getAsJsonObject();
                        players.add(new BingoModels.RosterPlayer(
                            p.has("rsn") ? p.get("rsn").getAsString() : "",
                            p.has("dropCount") ? p.get("dropCount").getAsInt() : 0,
                            p.has("totalPoints") ? p.get("totalPoints").getAsDouble() : 0
                        ));
                    }
                    rosters.put(entry.getKey(), players);
                }
            }
            return rosters;
        }
    }

    public Map<String, String> getSharedSettings(String boardApiUrl, String apiKey, String adminKey) throws IOException
    {
        HttpUrl url = HttpUrl.parse(boardApiUrl).newBuilder()
            .addQueryParameter("action", "adminGetSettings")
            .addQueryParameter("key", apiKey)
            .addQueryParameter("adminKey", adminKey)
            .build();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Settings fetch returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            Map<String, String> settings = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet())
            {
                if (entry.getValue().isJsonPrimitive())
                {
                    settings.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return settings;
        }
    }

    public List<BingoModels.TeamDrop> getTeamDrops(String boardApiUrl, String apiKey, String adminKey,
                                                     String teamCode) throws IOException
    {
        HttpUrl url = HttpUrl.parse(boardApiUrl).newBuilder()
            .addQueryParameter("action", "adminTeamDrops")
            .addQueryParameter("key", apiKey)
            .addQueryParameter("adminKey", adminKey)
            .addQueryParameter("team", teamCode)
            .build();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Team drops fetch returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<BingoModels.TeamDrop> drops = new ArrayList<>();
            if (root.has("drops"))
            {
                JsonArray dropsArr = root.getAsJsonArray("drops");
                for (JsonElement elem : dropsArr)
                {
                    JsonObject d = elem.getAsJsonObject();
                    drops.add(new BingoModels.TeamDrop(
                        d.has("rsn") ? d.get("rsn").getAsString() : "",
                        d.has("dropName") ? d.get("dropName").getAsString() : "",
                        d.has("date") ? d.get("date").getAsString() : "",
                        d.has("points") ? d.get("points").getAsDouble() : 0,
                        d.has("tileCode") ? d.get("tileCode").getAsString() : "",
                        d.has("tileName") ? d.get("tileName").getAsString() : ""
                    ));
                }
            }
            return drops;
        }
    }

    // ── Write operations (POST) ──

    private String adminPost(String apiUrl, String apiKey, String adminKey, JsonObject payload) throws IOException
    {
        payload.addProperty("key", apiKey);
        payload.addProperty("adminKey", adminKey);

        RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(apiUrl)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Admin API returned status: " + response.code());
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

    public String addWhitelistItem(String boardApiUrl, String apiKey, String adminKey,
                                    String item, String tileCode) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminAddWhitelistItem");
        payload.addProperty("item", item);
        payload.addProperty("tileCode", tileCode);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String removeWhitelistItem(String boardApiUrl, String apiKey, String adminKey,
                                       String item) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminRemoveWhitelistItem");
        payload.addProperty("item", item);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String submitDropForPlayer(String boardApiUrl, String apiKey, String adminKey,
                                       String team, String rsn, String dropName,
                                       String tileCode, double points, String date) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminSubmitDrop");
        payload.addProperty("team", team);
        payload.addProperty("rsn", rsn);
        payload.addProperty("dropName", dropName);
        payload.addProperty("tileCode", tileCode);
        payload.addProperty("points", points);
        payload.addProperty("date", date);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String setBountyWinner(String boardApiUrl, String apiKey, String adminKey,
                                   int bountyNumber, String winnerTeam, String description) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminSetBountyWinner");
        payload.addProperty("bountyNumber", bountyNumber);
        payload.addProperty("winnerTeam", winnerTeam);
        payload.addProperty("description", description);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String overrideTilePoints(String boardApiUrl, String apiKey, String adminKey,
                                      String team, String tileCode, double newPoints) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminOverrideTilePoints");
        payload.addProperty("team", team);
        payload.addProperty("tileCode", tileCode);
        payload.addProperty("newPoints", newPoints);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String assignPlayer(String boardApiUrl, String apiKey, String adminKey,
                                String rsn, String team) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminAssignPlayer");
        payload.addProperty("rsn", rsn);
        payload.addProperty("team", team);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String saveSharedSettings(String boardApiUrl, String apiKey, String adminKey,
                                      String discordWebhookUrl, String announcement,
                                      String hiscoreApiUrl, String clanDropLogUrl,
                                      String bingoStartDate, String bingoEndDate,
                                      String clanName) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminSaveSettings");
        payload.addProperty("discordWebhookUrl", discordWebhookUrl);
        payload.addProperty("announcement", announcement);
        payload.addProperty("hiscoreApiUrl", hiscoreApiUrl);
        payload.addProperty("clanDropLogUrl", clanDropLogUrl);
        payload.addProperty("bingoStartDate", bingoStartDate);
        payload.addProperty("bingoEndDate", bingoEndDate);
        payload.addProperty("clanName", clanName);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String removeDrop(String boardApiUrl, String apiKey, String adminKey,
                              String team, String rsn, String dropName,
                              String tileCode, String date) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminRemoveDrop");
        payload.addProperty("team", team);
        payload.addProperty("rsn", rsn);
        payload.addProperty("dropName", dropName);
        payload.addProperty("tileCode", tileCode);
        payload.addProperty("date", date);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String rotateApiKey(String boardApiUrl, String apiKey, String adminKey,
                               String newApiKey) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminRotateApiKey");
        payload.addProperty("newApiKey", newApiKey);
        return adminPost(boardApiUrl, apiKey, adminKey, payload);
    }

    public String removeHiscoreEntry(String hiscoreApiUrl, String apiKey, String adminKey,
                                      int row, int rank) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminRemoveEntry");
        payload.addProperty("row", row);
        payload.addProperty("rank", rank);
        return adminPost(hiscoreApiUrl, apiKey, adminKey, payload);
    }

    /**
     * Remove a hiscore entry by category key and rank (v2 — ClanDropLog.gs).
     */
    public String removeHiscoreEntryV2(String clanDropLogUrl, String apiKey, String adminKey,
                                        String categoryKey, int rank) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminRemoveHiscore");
        payload.addProperty("category", categoryKey);
        payload.addProperty("rank", rank);
        return adminPost(clanDropLogUrl, apiKey, adminKey, payload);
    }
}
