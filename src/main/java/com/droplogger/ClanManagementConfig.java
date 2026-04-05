package com.droplogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("droplogger")
public interface ClanManagementConfig extends Config
{
    // ── General ──

    @ConfigSection(
        name = "General",
        description = "Paste the board code from your clan admin to connect",
        position = -1
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "boardCode",
        name = "Clan Code",
        description = "Paste the code from your clan admin (connects you to the board)",
        section = generalSection,
        position = 0,
        secret = true
    )
    default String boardCode() { return ""; }

    // ── Clan Drop Log ──

    @ConfigSection(
        name = "Clan Drop Log",
        description = "Track all rare drops for the clan permanently",
        position = 1
    )
    String clanDropLogSection = "clanDropLog";

    @ConfigItem(
        keyName = "enableClanDropLog",
        name = "Enable Clan Drop Log",
        description = "Sends valuable drops to your clan's Google Sheet for permanent tracking",
        section = clanDropLogSection,
        position = 0
    )
    default boolean enableClanDropLog() { return false; }

    @ConfigItem(
        keyName = "clanDropMinValue",
        name = "Minimum Value",
        description = "Minimum GP value to track (default 100k)",
        section = clanDropLogSection,
        position = 1
    )
    default int clanDropMinValue() { return 100000; }

    @ConfigItem(
        keyName = "chatConfirmation",
        name = "Chat Confirmation",
        description = "Show logged drop confirmation in chat",
        section = clanDropLogSection,
        position = 2
    )
    default boolean chatConfirmation() { return true; }

    // ── Panel ──

    @ConfigSection(
        name = "Panel",
        description = "Settings for the side panel",
        position = 2
    )
    String panelSection = "panel";

    @ConfigItem(
        keyName = "refreshInterval",
        name = "Refresh Interval (sec)",
        description = "Seconds between data polls (minimum 30)",
        section = panelSection,
        position = 0
    )
    default int refreshInterval() { return 60; }

    // ── Hiscores ──

    @ConfigSection(
        name = "Hiscores",
        description = "Auto-submit personal best times to the clan hiscores",
        position = 4
    )
    String hiscoresSection = "hiscores";

    @ConfigItem(
        keyName = "enablePbSubmission",
        name = "Enable PB Submission",
        description = "Sends personal best times to your clan's Google Sheet when a new PB is detected",
        section = hiscoresSection,
        position = 0
    )
    default boolean enablePbSubmission() { return false; }

    @ConfigItem(
        keyName = "pbChatConfirmation",
        name = "PB Chat Confirmation",
        description = "Show chat message when a PB is submitted or rejected",
        section = hiscoresSection,
        position = 1
    )
    default boolean pbChatConfirmation() { return true; }

    // ── Discord ──

    @ConfigSection(
        name = "Discord",
        description = "Discord notification toggles (webhook URL is configured by admins)",
        position = 5
    )
    String discordSection = "discord";

    @ConfigItem(
        keyName = "postDrops",
        name = "Post Drops",
        description = "Sends valuable drops to Discord via your clan's webhook",
        section = discordSection,
        position = 0
    )
    default boolean postDrops() { return false; }

    @ConfigItem(
        keyName = "postPbs",
        name = "Post PBs",
        description = "Sends top 3 PB placements to Discord via your clan's webhook with a screenshot",
        section = discordSection,
        position = 2
    )
    default boolean postPbs() { return false; }

    // ── Admin ──

    @ConfigSection(
        name = "Admin",
        description = "Admin tools (requires admin API key)",
        position = 6
    )
    String adminSection = "admin";

    @ConfigItem(
        keyName = "adminApiKey",
        name = "Admin API Key",
        description = "Admin secret key — leave blank if you are not an admin",
        section = adminSection,
        position = 0,
        secret = true
    )
    default String adminApiKey() { return ""; }

    // ── Bingo ──

    @ConfigSection(
        name = "Bingo",
        description = "Bingo event settings (requires separate bingo sheet)",
        position = 7
    )
    String bingoSection = "bingo";

    @ConfigItem(
        keyName = "bingoApiUrl",
        name = "Bingo API URL",
        description = "Google Apps Script deployment URL for your bingo sheet (leave blank if no event)",
        section = bingoSection,
        position = 0,
        secret = true
    )
    default String bingoApiUrl() { return ""; }

    @ConfigItem(
        keyName = "bingoApiKey",
        name = "Bingo API Key",
        description = "API key for the bingo sheet",
        section = bingoSection,
        position = 1,
        secret = true
    )
    default String bingoApiKey() { return ""; }

    @ConfigItem(
        keyName = "bingoAdminKey",
        name = "Bingo Admin Key",
        description = "Admin key for bingo roster/progress management (leave blank if not an admin)",
        section = bingoSection,
        position = 2,
        secret = true
    )
    default String bingoAdminKey() { return ""; }

    @ConfigItem(
        keyName = "bingoHostKey",
        name = "Bingo Host Key",
        description = "Host key for full bingo board management (leave blank if not the host)",
        section = bingoSection,
        position = 3,
        secret = true
    )
    default String bingoHostKey() { return ""; }
}
