package com.droplogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("droplogger")
public interface ClanManagementConfig extends Config
{
    // ── Connection ──

    @ConfigSection(
        name = "Connection",
        description = "Paste the code from your clan admin to connect",
        position = 0
    )
    String connectionSection = "connection";

    @ConfigItem(
        keyName = "clanCode",
        name = "Clan Code",
        description = "Paste the code from your clan admin to connect to the platform",
        section = connectionSection,
        position = 0,
        secret = true
    )
    default String clanCode() { return ""; }

    // ── Data Sharing ──

    @ConfigSection(
        name = "Data Sharing",
        description = "Choose what data to share with your clan",
        position = 1
    )
    String dataSection = "data";

    @ConfigItem(
        keyName = "enableDrops",
        name = "Track Drops",
        description = "Send valuable drops to your clan automatically",
        section = dataSection,
        position = 0
    )
    default boolean enableDrops() { return false; }

    @ConfigItem(
        keyName = "enableSpeedTimes",
        name = "Track Speed Times",
        description = "Send personal best boss times to your clan automatically",
        section = dataSection,
        position = 1
    )
    default boolean enableSpeedTimes() { return false; }

    @ConfigItem(
        keyName = "enableClogSync",
        name = "Sync Collection Log",
        description = "Sync your collection log when you open it in-game",
        section = dataSection,
        position = 2
    )
    default boolean enableClogSync() { return false; }

    @ConfigItem(
        keyName = "enableStatTracking",
        name = "Track Stats",
        description = "Track XP and stats for clan leaderboards",
        section = dataSection,
        position = 3
    )
    default boolean enableStatTracking() { return false; }

    @ConfigItem(
        keyName = "chatConfirmation",
        name = "Chat Confirmations",
        description = "Show confirmation messages in chat when data is sent",
        section = dataSection,
        position = 4
    )
    default boolean chatConfirmation() { return true; }

    // ── Admin ──

    @ConfigSection(
        name = "Admin",
        description = "Admin tools (requires admin API key from your clan dashboard)",
        position = 2
    )
    String adminSection = "admin";

    @ConfigItem(
        keyName = "adminApiKey",
        name = "Admin API Key",
        description = "Admin key from the clan dashboard — leave blank if you are not an admin",
        section = adminSection,
        position = 0,
        secret = true
    )
    default String adminApiKey() { return ""; }
}
