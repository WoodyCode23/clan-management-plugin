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

    /**
     * Start a weekly event via the platform API.
     * POST /admin/{slug}/events
     */
    public String startEventPlatform(String baseUrl, String apiKey, String slug,
                                      String eventType, String metric, String displayName,
                                      String startAtIso, String endAtIso) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", eventType);
        payload.addProperty("metric", metric);
        payload.addProperty("displayName", displayName);
        if (startAtIso != null) payload.addProperty("startAt", startAtIso);
        if (endAtIso != null) payload.addProperty("endAt", endAtIso);

        RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(baseUrl + "/admin/" + slug + "/events")
            .header("Authorization", "Bearer " + apiKey)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                // Surface the server's reason (e.g. the event-overlap rejection) instead of a bare code.
                String detail = null;
                try
                {
                    JsonObject err = gson.fromJson(response.body() != null ? response.body().string() : null, JsonObject.class);
                    if (err != null && err.has("error")) detail = err.get("error").getAsString();
                }
                catch (Exception ignored) { }
                throw new IOException(detail != null ? detail : "Platform API returned status: " + response.code());
            }
            return "Event started";
        }
    }

    /** Running + scheduled events for the admin calendar. GET /clans/{slug}/events */
    public java.util.List<JsonObject> fetchEventsList(String baseUrl, String apiKey, String slug) throws IOException
    {
        Request request = new Request.Builder()
            .url(baseUrl + "/clans/" + slug + "/events?limit=8")
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("Platform API returned status: " + response.code());
            }
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            java.util.List<JsonObject> out = new java.util.ArrayList<>();
            if (root != null && root.has("events"))
            {
                for (com.google.gson.JsonElement el : root.getAsJsonArray("events"))
                {
                    out.add(el.getAsJsonObject());
                }
            }
            return out;
        }
    }

    /**
     * End a weekly event via the platform API.
     * POST /admin/{slug}/events/{id}/end
     */
    public String endEventPlatform(String baseUrl, String apiKey, String slug,
                                    String eventId) throws IOException
    {
        Request request = new Request.Builder()
            .url(baseUrl + "/admin/" + slug + "/events/" + eventId + "/end")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(JSON_TYPE, "{}"))
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Platform API returned status: " + response.code());
            }
            return "Event ended";
        }
    }
}
