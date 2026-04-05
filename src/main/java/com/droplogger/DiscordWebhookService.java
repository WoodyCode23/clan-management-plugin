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

    public void postDrop(String webhookUrl, DropEntry drop, BufferedImage screenshot)
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

        if (screenshot != null)
        {
            sendEmbedWithImage(webhookUrl, embed, screenshot);
        }
        else
        {
            sendEmbed(webhookUrl, embed);
        }
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

    public void postEventStart(String webhookUrl, String type, String displayName, String endTime)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        String title = EventMetrics.labelFromType(type);
        int color = EventMetrics.discordColorFromType(type);

        JsonObject embed = new JsonObject();
        embed.addProperty("title", title + " Started!");
        embed.addProperty("color", color);

        StringBuilder desc = new StringBuilder();
        desc.append("**").append(displayName).append("**\n\n");
        desc.append("Ends: ").append(endTime.replace("T", " ")).append(" ET");
        embed.addProperty("description", desc.toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Events");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postEventEnd(String webhookUrl, String type, String displayName,
                             java.util.List<WomService.WomEntry> topEntries)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        String title = EventMetrics.labelFromType(type);
        int color = EventMetrics.discordColorFromType(type);

        JsonObject embed = new JsonObject();
        embed.addProperty("title", title + " Ended: " + displayName);
        embed.addProperty("color", color);

        StringBuilder desc = new StringBuilder();
        if (topEntries != null && !topEntries.isEmpty())
        {
            String[] medals = {"\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"};
            String unit = EventMetrics.unitFromType(type);
            for (int i = 0; i < Math.min(3, topEntries.size()); i++)
            {
                WomService.WomEntry e = topEntries.get(i);
                String medal = i < medals.length ? medals[i] : "#" + (i + 1);
                desc.append(medal).append(" **").append(e.username).append("** — ");
                desc.append(GP_FORMAT.format(e.gained)).append(unit).append("\n");
            }
        }
        else
        {
            desc.append("No participants recorded.");
        }
        embed.addProperty("description", desc.toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Events");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postBingoEventStart(String webhookUrl, String eventName, int gridRows, int gridCols,
                                     java.util.List<BingoTeam> teams, String endDate)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", eventName + " Has Begun!");
        embed.addProperty("color", 0x2ECC71);

        StringBuilder desc = new StringBuilder();
        desc.append("**Grid:** ").append(gridRows).append("x").append(gridCols).append("\n");
        desc.append("**Teams:** ");
        for (int i = 0; i < teams.size(); i++)
        {
            if (i > 0) desc.append(", ");
            desc.append(teams.get(i).getName());
        }
        desc.append("\n**Ends:** ").append(endDate.replace("T", " ")).append(" ET");
        embed.addProperty("description", desc.toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Bingo");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postBingoEventEnd(String webhookUrl, String eventName, BingoStandings standings)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", eventName + " — Final Results!");
        embed.addProperty("color", 0xFFD700);

        StringBuilder desc = new StringBuilder();
        desc.append("**Team Standings:**\n");
        if (standings.getTeamStandings() != null)
        {
            String[] medals = {"\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"};
            int shown = Math.min(3, standings.getTeamStandings().size());
            for (int i = 0; i < shown; i++)
            {
                BingoTeam t = standings.getTeamStandings().get(i);
                String medal = i < medals.length ? medals[i] : "#" + (i + 1);
                desc.append(medal).append(" **").append(t.getName()).append("** — ");
                desc.append(GP_FORMAT.format(t.getTotalPoints())).append(" pts\n");
            }
        }
        desc.append("\n**Top Players:**\n");
        if (standings.getIndividualStandings() != null)
        {
            String[] medals = {"\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"};
            int shown = Math.min(3, standings.getIndividualStandings().size());
            for (int i = 0; i < shown; i++)
            {
                BingoStandings.PlayerStanding p = standings.getIndividualStandings().get(i);
                String medal = i < medals.length ? medals[i] : "#" + (i + 1);
                desc.append(medal).append(" **").append(p.getRsn()).append("** — ");
                desc.append(GP_FORMAT.format(p.getPoints())).append(" pts\n");
            }
        }
        embed.addProperty("description", desc.toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Bingo");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postBingoDrop(String webhookUrl, String player, String item, String team,
                               String tileCode, double points)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Bingo Drop!");
        embed.addProperty("color", 0xFFD700);

        StringBuilder desc = new StringBuilder();
        desc.append("**Item:** ").append(item).append("\n");
        desc.append("**Player:** ").append(player).append("\n");
        desc.append("**Team:** ").append(team).append("\n");
        desc.append("**Tile:** ").append(tileCode).append("\n");
        desc.append("**Points:** +").append(GP_FORMAT.format(points));
        embed.addProperty("description", desc.toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Bingo");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postBountyHint(String webhookUrl, BingoBounty bounty, int minutesBefore)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Bounty #" + bounty.getNumber() + " — Hint!");
        embed.addProperty("color", 0xF1C40F);
        embed.addProperty("description", "Releasing in ~" + minutesBefore + " minutes!");

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Bingo");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postBountyLive(String webhookUrl, BingoBounty bounty)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Bounty #" + bounty.getNumber() + " is NOW LIVE!");
        embed.addProperty("color", 0xE74C3C);
        embed.addProperty("description", bounty.getDescription() +
            "\n**Reward:** " + GP_FORMAT.format(bounty.getPoints()) + " bonus points");

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Bingo");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postBountyWinner(String webhookUrl, BingoBounty bounty, String winner)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Bounty #" + bounty.getNumber() + " — Winner!");
        embed.addProperty("color", 0xFFD700);
        embed.addProperty("description", "**" + bounty.getDescription() + "**\n\n" +
            "Won by **" + winner + "**\n+" + GP_FORMAT.format(bounty.getPoints()) + " bonus points!");

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Bingo");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
    }

    public void postTileCompleted(String webhookUrl, String teamName, String tileCode, String tileName)
    {
        if (!isValidWebhookUrl(webhookUrl)) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Tile Completed!");
        embed.addProperty("color", 0x2ECC71);
        embed.addProperty("description", "**" + teamName + "** completed " + tileCode + ": " + tileName + "!");

        JsonObject footer = new JsonObject();
        footer.addProperty("text", clanName + " Bingo");
        embed.add("footer", footer);

        sendEmbed(webhookUrl, embed);
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
