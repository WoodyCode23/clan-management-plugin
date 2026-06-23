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
                                      String eventType, String metric, String displayName) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", eventType);
        payload.addProperty("metric", metric);
        payload.addProperty("displayName", displayName);
        payload.addProperty("durationDays", 7);

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
                throw new IOException("Platform API returned status: " + response.code());
            }
            return "Event started";
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
