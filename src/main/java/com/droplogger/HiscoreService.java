package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
public class HiscoreService
{
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public HiscoreService(OkHttpClient httpClient, Gson gson)
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

    /**
     * Fetch the current top 3 times for a specific sheet row.
     */
    public List<HiscoreEntry> fetchTopTimes(String apiUrl, int sheetRow, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Hiscore API URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "topTimes")
            .addQueryParameter("row", String.valueOf(sheetRow))
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Hiscore API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<HiscoreEntry> entries = new ArrayList<>();
            if (root.has("top3"))
            {
                JsonArray top3 = root.getAsJsonArray("top3");
                for (JsonElement elem : top3)
                {
                    JsonObject e = elem.getAsJsonObject();
                    entries.add(new HiscoreEntry(
                        e.has("rank") ? e.get("rank").getAsInt() : 0,
                        e.has("timeSeconds") ? e.get("timeSeconds").getAsDouble() : 0,
                        e.has("formattedTime") ? e.get("formattedTime").getAsString() : "",
                        e.has("rsns") ? e.get("rsns").getAsString() : "",
                        e.has("date") ? e.get("date").getAsString() : ""
                    ));
                }
            }
            return entries;
        }
    }

    /**
     * Submit a PB time. Returns the placement (1-3) or 0 if it didn't qualify.
     */
    public int submitPb(String apiUrl, int sheetRow, String time, String rsns, String date, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Hiscore API URL is not configured");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("key", apiKey != null ? apiKey : "");
        payload.addProperty("action", "submitPb");
        payload.addProperty("row", sheetRow);
        payload.addProperty("time", time);
        payload.addProperty("rsns", rsns);
        payload.addProperty("date", date);

        RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(apiUrl)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Hiscore submit returned status: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject root = new JsonParser().parse(responseBody).getAsJsonObject();

            if (root.has("status") && "error".equals(root.get("status").getAsString()))
            {
                String message = root.has("message") ? root.get("message").getAsString() : "Unknown error";
                throw new IOException("Submit failed: " + message);
            }

            return root.has("placed") ? root.get("placed").getAsInt() : 0;
        }
    }

    /**
     * Check if a time qualifies for top 3, and submit if so.
     * Returns placement (1-3) or 0 if it didn't qualify.
     */
    public int checkAndSubmitPb(String apiUrl, int sheetRow, String formattedTime,
                                double timeSeconds, String rsns, String date, String apiKey)
    {
        try
        {
            // First check current top 3 to avoid unnecessary POST
            List<HiscoreEntry> top3 = fetchTopTimes(apiUrl, sheetRow, apiKey);

            boolean qualifies = false;
            if (top3.size() < 3)
            {
                qualifies = true;
            }
            else
            {
                // Check if faster than the slowest top 3 entry
                for (HiscoreEntry entry : top3)
                {
                    if (timeSeconds < entry.getTimeSeconds())
                    {
                        qualifies = true;
                        break;
                    }
                    // Also qualifies if same RSN (update their time)
                    if (entry.getRsns().equalsIgnoreCase(rsns))
                    {
                        qualifies = true;
                        break;
                    }
                }
            }

            if (!qualifies)
            {
                log.info("PB {} does not qualify for top 3 in row {}", formattedTime, sheetRow);
                return 0;
            }

            // Submit
            int placed = submitPb(apiUrl, sheetRow, formattedTime, rsns, date, apiKey);
            log.info("PB {} submitted to row {}, placed #{}", formattedTime, sheetRow, placed);
            return placed;
        }
        catch (IOException e)
        {
            log.error("Failed to check/submit PB", e);
            return -1;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  V2 METHODS — hit ClanDropLog.gs hiscore endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetch the current top times for a category key (v2).
     */
    public List<HiscoreEntry> fetchTopTimesV2(String apiUrl, String categoryKey, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Clan Drop Log URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "topTimes")
            .addQueryParameter("category", categoryKey)
            .addQueryParameter("limit", "3")
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Hiscore v2 API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            List<HiscoreEntry> entries = new ArrayList<>();
            if (root.has("top3"))
            {
                JsonArray top3 = root.getAsJsonArray("top3");
                for (JsonElement elem : top3)
                {
                    JsonObject e = elem.getAsJsonObject();
                    entries.add(new HiscoreEntry(
                        e.has("rank") ? e.get("rank").getAsInt() : 0,
                        e.has("timeSeconds") ? e.get("timeSeconds").getAsDouble() : 0,
                        e.has("formattedTime") ? e.get("formattedTime").getAsString() : "",
                        e.has("rsns") ? e.get("rsns").getAsString() : "",
                        e.has("date") ? e.get("date").getAsString() : "",
                        categoryKey,
                        e.has("partySize") ? e.get("partySize").getAsInt() : 1
                    ));
                }
            }
            return entries;
        }
    }

    /**
     * Submit a PB time via ClanDropLog.gs (v2).
     * Returns the placement (1-3) or 0 if it didn't qualify.
     */
    public int submitPbV2(String apiUrl, String categoryKey, String time, double timeSeconds,
                          String rsns, String date, int partySize, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Clan Drop Log URL is not configured");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("key", apiKey != null ? apiKey : "");
        payload.addProperty("action", "submitPb");
        payload.addProperty("category", categoryKey);
        payload.addProperty("time", time);
        payload.addProperty("timeSeconds", timeSeconds);
        payload.addProperty("rsns", rsns);
        payload.addProperty("date", date);
        payload.addProperty("partySize", partySize);

        RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(apiUrl)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Hiscore v2 submit returned status: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject root = new JsonParser().parse(responseBody).getAsJsonObject();

            if (root.has("status") && "error".equals(root.get("status").getAsString()))
            {
                String message = root.has("message") ? root.get("message").getAsString() : "Unknown error";
                throw new IOException("Submit v2 failed: " + message);
            }

            return root.has("placed") ? root.get("placed").getAsInt() : 0;
        }
    }

    /**
     * Check if a time qualifies for top 3 via v2, and submit if so.
     * Returns placement (1-3), 0 if didn't qualify, or -1 on error.
     */
    public int checkAndSubmitPbV2(String apiUrl, String categoryKey, String formattedTime,
                                   double timeSeconds, String rsns, String date,
                                   int partySize, String apiKey)
    {
        try
        {
            List<HiscoreEntry> top3 = fetchTopTimesV2(apiUrl, categoryKey, apiKey);

            boolean qualifies = false;
            if (top3.size() < 3)
            {
                qualifies = true;
            }
            else
            {
                for (HiscoreEntry entry : top3)
                {
                    if (timeSeconds < entry.getTimeSeconds())
                    {
                        qualifies = true;
                        break;
                    }
                    if (entry.getRsns().equalsIgnoreCase(rsns))
                    {
                        qualifies = true;
                        break;
                    }
                }
            }

            if (!qualifies)
            {
                log.info("PB {} does not qualify for top 3 in {}", formattedTime, categoryKey);
                return 0;
            }

            int placed = submitPbV2(apiUrl, categoryKey, formattedTime, timeSeconds,
                rsns, date, partySize, apiKey);
            log.info("PB {} submitted to {}, placed #{}", formattedTime, categoryKey, placed);
            return placed;
        }
        catch (IOException e)
        {
            log.error("Failed to check/submit PB v2 for {}", categoryKey, e);
            return -1;
        }
    }

    /**
     * Fetch top times for ALL categories in a single batch call (v2).
     * Returns a map of categoryKey → list of entries.
     */
    public Map<String, List<HiscoreEntry>> fetchAllTopTimes(String apiUrl, String apiKey) throws IOException
    {
        if (apiUrl == null || apiUrl.isEmpty())
        {
            throw new IOException("Clan Drop Log URL is not configured");
        }

        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "allTopTimes")
            .addQueryParameter("limit", "3")
            .addQueryParameter("key", apiKey != null ? apiKey : "")
            .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Hiscore v2 allTopTimes returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            Map<String, List<HiscoreEntry>> result = new java.util.LinkedHashMap<>();

            if (root.has("categories"))
            {
                JsonObject cats = root.getAsJsonObject("categories");
                for (String catKey : cats.keySet())
                {
                    JsonArray entries = cats.getAsJsonArray(catKey);
                    List<HiscoreEntry> list = new ArrayList<>();
                    for (JsonElement elem : entries)
                    {
                        JsonObject e = elem.getAsJsonObject();
                        list.add(new HiscoreEntry(
                            e.has("rank") ? e.get("rank").getAsInt() : 0,
                            e.has("timeSeconds") ? e.get("timeSeconds").getAsDouble() : 0,
                            e.has("formattedTime") ? e.get("formattedTime").getAsString() : "",
                            e.has("rsns") ? e.get("rsns").getAsString() : "",
                            e.has("date") ? e.get("date").getAsString() : "",
                            catKey,
                            e.has("partySize") ? e.get("partySize").getAsInt() : 1
                        ));
                    }
                    result.put(catKey, list);
                }
            }

            return result;
        }
    }

    /**
     * Parse a time string (MM:SS.ss or H:MM:SS.ss) to total seconds.
     */
    public static double parseTimeToSeconds(String timeStr)
    {
        if (timeStr == null || timeStr.isEmpty())
        {
            return 0;
        }

        String[] parts = timeStr.split(":");
        double seconds = 0;

        try
        {
            if (parts.length == 3)
            {
                seconds = Double.parseDouble(parts[0]) * 3600
                    + Double.parseDouble(parts[1]) * 60
                    + Double.parseDouble(parts[2]);
            }
            else if (parts.length == 2)
            {
                seconds = Double.parseDouble(parts[0]) * 60
                    + Double.parseDouble(parts[1]);
            }
            else if (parts.length == 1)
            {
                seconds = Double.parseDouble(parts[0]);
            }
        }
        catch (NumberFormatException e)
        {
            return 0;
        }

        return seconds;
    }
}
