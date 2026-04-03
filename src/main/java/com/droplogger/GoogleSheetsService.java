package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class GoogleSheetsService
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.of("America/New_York"));

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public GoogleSheetsService(OkHttpClient httpClient, Gson gson)
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
     * Log a drop to the clan drop log (separate sheet, all columns).
     */
    public void logClanDrop(String webhookUrl, DropEntry drop, String apiKey)
    {
        logClanDrop(webhookUrl, drop, apiKey, "drop");
    }

    public void logClanDrop(String webhookUrl, DropEntry drop, String apiKey, String type)
    {
        if (webhookUrl == null || webhookUrl.isEmpty())
        {
            log.warn("Clan drop log URL is not configured");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("key", apiKey != null ? apiKey : "");
        payload.addProperty("timestamp", TIMESTAMP_FORMAT.format(drop.getTimestamp()));
        payload.addProperty("item", drop.getItemName());
        payload.addProperty("value", drop.getValue());
        payload.addProperty("monster", drop.getMonsterName());
        payload.addProperty("kc", drop.getKillCount());
        payload.addProperty("player", drop.getPlayerName());
        payload.addProperty("x", drop.getWorldX());
        payload.addProperty("y", drop.getWorldY());
        payload.addProperty("plane", drop.getPlane());
        payload.addProperty("type", type);

        postToApi(webhookUrl, payload, "Clan drop");
    }

    private void postToApi(String url, JsonObject payload, String label)
    {
        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.error("Failed to log {} to Google Sheets", label, e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                response.close();
                if (response.isSuccessful())
                {
                    log.debug("{} logged successfully", label);
                }
                else
                {
                    log.error("{} log failed with status: {}", label, response.code());
                }
            }
        });
    }
}
