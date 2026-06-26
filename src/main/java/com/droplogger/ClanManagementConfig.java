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
        description = "Enter the API key from your clan admin to connect",
        position = 0
    )
    String connectionSection = "connection";

    @ConfigItem(
        keyName = "apiKey",
        name = "API Key",
        description = "Your clan API key (from the clan admin / dashboard). The plugin connects to the Solus platform automatically.",
        section = connectionSection,
        position = 0,
        secret = true
    )
    default String apiKey() { return ""; }

    @ConfigItem(
        keyName = "linkCode",
        name = "Link Code",
        description = "Paste a link code from the website (Settings) to connect this account to your Discord profile. Cleared automatically after use.",
        section = connectionSection,
        position = 1,
        secret = true
    )
    default String linkCode() { return ""; }

    // ── Data Sharing ──

    @ConfigSection(
        name = "Data Sharing",
        description = "Opt-in — everything here is off by default and is sent only to your Solus clan's server.",
        position = 1
    )
    String dataSection = "data";

    @ConfigItem(
        keyName = "enableDrops",
        name = "Track Drops",
        description = "Off by default. Sends each valuable drop (item, GP value, monster, kill count) with your RSN to the clan server, for the drop feed and leaderboards.",
        section = dataSection,
        position = 0
    )
    default boolean enableDrops() { return false; }

    @ConfigItem(
        keyName = "enableSpeedTimes",
        name = "Track Speed Times",
        description = "Off by default. Sends your personal-best boss times (boss, time, team) to the clan server. Your raid party is read locally to credit the right team; your location is never sent.",
        section = dataSection,
        position = 1
    )
    default boolean enableSpeedTimes() { return false; }

    @ConfigItem(
        keyName = "enableClogSync",
        name = "Sync Collection Log",
        description = "Off by default. Uploads your collection log progress (obtained items + counts) when you open it in-game, for clan clog tracking.",
        section = dataSection,
        position = 2
    )
    default boolean enableClogSync() { return false; }

    @ConfigItem(
        keyName = "enableStatTracking",
        name = "Track Stats",
        description = "Off by default. Sends only your RSN so the clan can read your public XP/KC from the official OSRS hiscores for leaderboards. No private game data is sent.",
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
