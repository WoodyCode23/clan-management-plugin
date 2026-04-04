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

    public Map<String, String> getSharedSettings(String apiUrl, String apiKey, String adminKey) throws IOException
    {
        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", "getSharedSettings")
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

    public String saveSharedSettings(String apiUrl, String apiKey, String adminKey,
                                      String clanName, String discordWebhookUrl,
                                      String womGroupId, String announcement) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminSaveSettings");
        payload.addProperty("clanName", clanName);
        payload.addProperty("discordWebhookUrl", discordWebhookUrl);
        payload.addProperty("womGroupId", womGroupId);
        payload.addProperty("announcement", announcement);
        return adminPost(apiUrl, apiKey, adminKey, payload);
    }

    public String rotateApiKey(String apiUrl, String apiKey, String adminKey,
                               String newApiKey) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminRotateApiKey");
        payload.addProperty("newApiKey", newApiKey);
        return adminPost(apiUrl, apiKey, adminKey, payload);
    }

    /**
     * Start a weekly event (boss or skill of the week).
     */
    public String startEvent(String apiUrl, String apiKey, String adminKey,
                             String eventType, String metric, String displayName) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminStartEvent");
        payload.addProperty("eventType", eventType);
        payload.addProperty("eventMetric", metric);
        payload.addProperty("eventDisplayName", displayName);
        return adminPost(apiUrl, apiKey, adminKey, payload);
    }

    /**
     * End the current weekly event.
     */
    public String endEvent(String apiUrl, String apiKey, String adminKey) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminEndEvent");
        return adminPost(apiUrl, apiKey, adminKey, payload);
    }

    /**
     * Remove a hiscore entry by category key and rank.
     */
    public String removeHiscoreEntryV2(String apiUrl, String apiKey, String adminKey,
                                        String categoryKey, int rank) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminRemoveHiscore");
        payload.addProperty("category", categoryKey);
        payload.addProperty("rank", rank);
        return adminPost(apiUrl, apiKey, adminKey, payload);
    }
}
