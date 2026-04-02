package com.droplogger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class WomService
{
    private static final String WOM_API_BASE = "https://api.wiseoldman.net/v2";
    private static final int GROUP_ID = 4983;

    private final OkHttpClient httpClient;

    // Cached role lookup: lowercase username → role string
    private Map<String, String> roleCache = new HashMap<>();
    private long roleCacheTime = 0;
    private static final long ROLE_CACHE_TTL = 30 * 60 * 1000; // 30 minutes

    // Cached role icons: role name → icon
    private final Map<String, ImageIcon> roleIconCache = new HashMap<>();
    private static final String ROLE_ICON_BASE = "https://wiseoldman.net/img/group_roles/";

    @Inject
    public WomService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient.newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Fetch group member roles (cached for 30 min).
     */
    private Map<String, String> fetchRoles() throws IOException
    {
        long now = System.currentTimeMillis();
        if (!roleCache.isEmpty() && (now - roleCacheTime) < ROLE_CACHE_TTL)
        {
            return roleCache;
        }

        HttpUrl url = HttpUrl.parse(WOM_API_BASE + "/groups/" + GROUP_ID).newBuilder().build();

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "ClanManagementPlugin")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("WOM group fetch returned status: {}", response.code());
                return roleCache;
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();

            Map<String, String> roles = new HashMap<>();
            if (root.has("memberships"))
            {
                JsonArray memberships = root.getAsJsonArray("memberships");
                for (JsonElement elem : memberships)
                {
                    JsonObject m = elem.getAsJsonObject();
                    String role = m.has("role") ? m.get("role").getAsString() : "member";
                    if (m.has("player"))
                    {
                        JsonObject p = m.getAsJsonObject("player");
                        String name = p.has("displayName")
                            ? p.get("displayName").getAsString()
                            : p.get("username").getAsString();
                        roles.put(name.toLowerCase(), role);
                    }
                }
            }

            roleCache = roles;
            roleCacheTime = now;
            log.debug("Fetched {} WOM group roles", roles.size());
            return roles;
        }
    }

    /**
     * Fetch group hiscores — ranks members by level/experience in a skill.
     */
    public List<WomEntry> fetchHiscores(String metric) throws IOException
    {
        Map<String, String> roles = fetchRoles();

        HttpUrl url = HttpUrl.parse(WOM_API_BASE + "/groups/" + GROUP_ID + "/hiscores").newBuilder()
            .addQueryParameter("metric", metric)
            .addQueryParameter("limit", "20")
            .addQueryParameter("offset", "0")
            .build();

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "ClanManagementPlugin")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("WOM API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonArray arr = new JsonParser().parse(body).getAsJsonArray();

            List<WomEntry> entries = new ArrayList<>();
            int rank = 1;
            for (JsonElement elem : arr)
            {
                JsonObject obj = elem.getAsJsonObject();
                JsonObject player = obj.getAsJsonObject("player");

                String username = player.has("displayName")
                    ? player.get("displayName").getAsString()
                    : player.get("username").getAsString();

                long experience = obj.has("experience") ? obj.get("experience").getAsLong() : 0;
                int level = obj.has("level") ? obj.get("level").getAsInt() : 0;
                String role = roles.getOrDefault(username.toLowerCase(), "member");

                WomEntry entry = new WomEntry(rank++, username, role, experience, level, 0);
                entry.roleIcon = getRoleIcon(role);
                entries.add(entry);
            }
            return entries;
        }
    }

    /**
     * Fetch XP gains for the group over a time period.
     */
    public List<WomEntry> fetchGained(String metric, String period) throws IOException
    {
        Map<String, String> roles = fetchRoles();

        HttpUrl url = HttpUrl.parse(WOM_API_BASE + "/groups/" + GROUP_ID + "/gained").newBuilder()
            .addQueryParameter("metric", metric)
            .addQueryParameter("period", period)
            .addQueryParameter("limit", "20")
            .addQueryParameter("offset", "0")
            .build();

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "ClanManagementPlugin")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("WOM API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonArray arr = new JsonParser().parse(body).getAsJsonArray();

            List<WomEntry> entries = new ArrayList<>();
            int rank = 1;
            for (JsonElement elem : arr)
            {
                JsonObject obj = elem.getAsJsonObject();
                JsonObject player = obj.getAsJsonObject("player");
                JsonObject data = obj.getAsJsonObject("data");

                String username = player.has("displayName")
                    ? player.get("displayName").getAsString()
                    : player.get("username").getAsString();

                long gained = data.has("gained") ? data.get("gained").getAsLong() : 0;
                long end = data.has("end") ? data.get("end").getAsLong() : 0;
                String role = roles.getOrDefault(username.toLowerCase(), "member");

                if (gained <= 0) continue;

                WomEntry entry = new WomEntry(rank++, username, role, end, 0, gained);
                entry.roleIcon = getRoleIcon(role);
                entries.add(entry);
            }
            return entries;
        }
    }

    /**
     * Represents a single entry in a WOM leaderboard.
     */
    public static class WomEntry
    {
        public final int rank;
        public final String username;
        public final String role;
        public final long experience;
        public final int level;
        public final long gained;
        public ImageIcon roleIcon;

        public WomEntry(int rank, String username, String role, long experience, int level, long gained)
        {
            this.rank = rank;
            this.username = username;
            this.role = role;
            this.experience = experience;
            this.level = level;
            this.gained = gained;
        }
    }

    /**
     * Fetch recent clan activity (joins, leaves, rank changes).
     */
    public List<ActivityEntry> fetchActivity(int limit) throws IOException
    {
        HttpUrl url = HttpUrl.parse(WOM_API_BASE + "/groups/" + GROUP_ID + "/activity").newBuilder()
            .addQueryParameter("limit", String.valueOf(limit))
            .build();

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "ClanManagementPlugin")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("WOM activity API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonArray arr = new JsonParser().parse(body).getAsJsonArray();

            List<ActivityEntry> entries = new ArrayList<>();
            for (JsonElement elem : arr)
            {
                JsonObject obj = elem.getAsJsonObject();
                JsonObject player = obj.getAsJsonObject("player");

                String username = player.has("displayName")
                    ? player.get("displayName").getAsString()
                    : player.get("username").getAsString();
                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                String role = obj.has("role") && !obj.get("role").isJsonNull()
                    ? obj.get("role").getAsString() : "";
                String previousRole = obj.has("previousRole") && !obj.get("previousRole").isJsonNull()
                    ? obj.get("previousRole").getAsString() : "";
                String createdAt = obj.has("createdAt") ? obj.get("createdAt").getAsString() : "";

                entries.add(new ActivityEntry(username, type, role, previousRole, createdAt));
            }
            return entries;
        }
    }

    /**
     * Get the clan rank icon for a role. Downloads from WOM and caches.
     * Returns null if the icon can't be loaded.
     */
    public ImageIcon getRoleIcon(String role)
    {
        if (role == null || role.isEmpty()) return null;

        ImageIcon cached = roleIconCache.get(role);
        if (cached != null) return cached;

        try
        {
            Request request = new Request.Builder()
                .url(ROLE_ICON_BASE + role + ".png")
                .header("User-Agent", "ClanManagementPlugin")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful()) return null;
                byte[] bytes = response.body().bytes();
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null)
                {
                    // Scale to 14x14 for the panel
                    java.awt.Image scaled = img.getScaledInstance(14, 14, java.awt.Image.SCALE_SMOOTH);
                    ImageIcon icon = new ImageIcon(scaled);
                    roleIconCache.put(role, icon);
                    return icon;
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to load role icon for {}", role);
        }
        return null;
    }

    public static class ActivityEntry
    {
        public final String username;
        public final String type; // "joined", "left", "changed_role"
        public final String role;
        public final String previousRole;
        public final String createdAt;

        public ActivityEntry(String username, String type, String role, String previousRole, String createdAt)
        {
            this.username = username;
            this.type = type;
            this.role = role;
            this.previousRole = previousRole;
            this.createdAt = createdAt;
        }
    }
}
