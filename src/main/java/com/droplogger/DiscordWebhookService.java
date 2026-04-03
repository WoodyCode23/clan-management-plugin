package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import javax.imageio.ImageIO;

@Slf4j
@Singleton
public class DiscordWebhookService
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private final OkHttpClient httpClient;
    private final Gson gson;
    private String clanName = "Clan";

    public void setClanName(String name)
    {
        this.clanName = (name != null && !name.isEmpty()) ? name : "Clan";
    }

    @Inject
    public DiscordWebhookService(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public void postDrop(String webhookUrl, DropEntry drop)
    {
        if (!isValidWebhookUrl(webhookUrl))
        {
            return;
        }

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Valuable Drop!");
        embed.addProperty("color", 0xFFD700); // Gold

        StringBuilder desc = new StringBuilder();
        desc.append("**Item:** ").append(drop.getItemName()).append("\n");
        desc.append("**Value:** ").append(GP_FORMAT.format(drop.getValue())).append(" gp\n");
        desc.append("**Monster:** ").append(drop.getMonsterName()).append("\n");
        desc.append("**KC:** ").append(drop.getKillCount()).append("\n");
        desc.append("**Player:** ").append(drop.getPlayerName());
        embed.addProperty("description", desc.toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName);
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postPb(String webhookUrl, String formattedTime, int placed, String categoryName,
                       String rsns, BufferedImage screenshot)
    {
        if (!isValidWebhookUrl(webhookUrl))
        {
            return;
        }

        String[] medals = {"", "\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"}; // gold, silver, bronze
        String medal = placed >= 1 && placed <= 3 ? medals[placed] : "";

        JsonObject embed = new JsonObject();
        embed.addProperty("title", medal + " New #" + placed + " — " + categoryName);
        embed.addProperty("color", placed == 1 ? 0xFFD700 : placed == 2 ? 0xC0C0C0 : 0xCD7F32);

        StringBuilder desc = new StringBuilder();
        desc.append("**Time:** ").append(formattedTime).append("\n");
        desc.append("**Player(s):** ").append(rsns).append("\n");
        desc.append("**Rank:** #").append(placed);
        embed.addProperty("description", desc.toString());

        if (screenshot != null)
        {
            embed.addProperty("image", new JsonObject().toString()); // placeholder, set below
        }

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Hiscores");
        embed.add("footer", footer);

        if (screenshot != null)
        {
            // Use multipart to attach screenshot
            sendEmbedWithImage(webhookUrl, embed, screenshot);
        }
        else
        {
            sendEmbed(webhookUrl, embed);
        }
    }

    private void sendEmbedWithImage(String webhookUrl, JsonObject embed, BufferedImage screenshot)
    {
        try
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenshot, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            // Set embed image to the attached file
            JsonObject image = new JsonObject();
            image.addProperty("url", "attachment://screenshot.png");
            embed.add("image", image);

            JsonObject payload = new JsonObject();
            payload.addProperty("username", clanName);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            payload.add("embeds", embeds);

            RequestBody fileBody = RequestBody.create(
                MediaType.parse("image/png"), imageBytes);

            RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", gson.toJson(payload))
                .addFormDataPart("file", "screenshot.png", fileBody)
                .build();

            Request request = new Request.Builder()
                .url(webhookUrl)
                .post(multipart)
                .build();

            httpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    log.error("Failed to post PB to Discord", e);
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    response.close();
                    if (!response.isSuccessful())
                    {
                        log.error("Discord PB webhook returned status: {}", response.code());
                    }
                }
            });
        }
        catch (IOException e)
        {
            log.error("Failed to encode screenshot", e);
            sendEmbed(webhookUrl, embed);
        }
    }

    private void sendEmbed(String webhookUrl, JsonObject embed)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", clanName);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.error("Failed to post to Discord webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                response.close();
                if (!response.isSuccessful())
                {
                    log.error("Discord webhook returned status: {}", response.code());
                }
            }
        });
    }

    private boolean isValidWebhookUrl(String url)
    {
        if (url == null || url.isEmpty())
        {
            return false;
        }
        if (!url.startsWith("https://discord.com/api/webhooks/"))
        {
            log.warn("Invalid Discord webhook URL: {}", url);
            return false;
        }
        return true;
    }
}
