# Clan Management Plugin for RuneLite

A RuneLite plugin that tracks drops, boss hiscores, and XP for your OSRS clan using Google Sheets as a backend.

## Features

- **Drop Logging** -- Automatically logs valuable drops to a shared Google Sheet
- **Clan Hiscores** -- Tracks boss completion times across the clan (top 3 per category)
- **XP Tracking** -- Clan XP leaderboards and gains via Wise Old Man integration
- **Activity Feed** -- Recent clan member joins, leaves, and rank changes
- **Discord Notifications** -- Posts drops and hiscore placements to a Discord webhook
- **Admin Tools** -- Manage clan settings, rotate API keys, and moderate hiscores from the panel

## Setup Guide

### 1. Create the Google Sheet

1. Create a new Google Sheet (this will be your clan's database)
2. Go to **Extensions > Apps Script**
3. Delete the default `Code.gs` file
4. Create three script files and paste the contents from the `google-apps-script/` folder:
   - `ClanDropLog.gs` -- Main API handler
   - `ClanDropLogSetup.gs` -- Sheet structure and formatting
   - `ClanDropWhitelist.gs` -- Drop whitelist management
5. In the Apps Script editor, run the `setupClanDropLog()` function
   - This creates the tabs: Dashboard, Drops, Leaderboard, Hiscores, Drop Whitelist
   - Grant permissions when prompted

### 2. Configure the Settings Tab

After running setup, a **Settings** tab is created with these fields:

| Setting | Description |
|---------|-------------|
| `apiKey` | Shared secret for all clan members (change from default!) |
| `adminKey` | Admin-only secret for managing settings |
| `clanName` | Your clan's display name |
| `discordWebhookUrl` | Discord webhook URL for notifications (optional) |
| `womGroupId` | Your Wise Old Man group ID for XP tracking (optional) |
| `announcement` | Message shown to all members on the Home tab (optional) |

**Change `apiKey` and `adminKey` from their defaults before sharing with anyone.**

### 3. Deploy as a Web App

1. In Apps Script, click **Deploy > New deployment**
2. Select type: **Web app**
3. Set "Execute as" to **Me**
4. Set "Who has access" to **Anyone**
5. Click **Deploy** and copy the deployment URL

### 4. Generate a Clan Code

The clan code encodes the deployment URL and API key together. Generate it in your browser console or any Base64 tool:

```
btoa("YOUR_DEPLOYMENT_URL|YOUR_API_KEY")
```

For example:
```
btoa("https://script.google.com/macros/s/ABC123/exec|my-secret-key")
```

Share this code with clan members. They paste it into the plugin's **Clan Code** setting.

### 5. Install the Plugin

**From RuneLite Plugin Hub:**
Search for "Clan Management" in the Plugin Hub and install.

**As an external plugin:**
1. Build with `./gradlew build`
2. Copy `build/libs/drop-logger-plugin-1.0.0.jar` to `~/.runelite/externalPlugins/`

### 6. Configure the Plugin

In RuneLite settings for Clan Management:

1. **Clan Code** -- Paste the code from step 4
2. **Enable Clan Drop Log** -- Toggle on to start logging drops
3. **Enable PB Submission** -- Toggle on to submit boss completion times
4. **Discord toggles** -- Enable to post drops/PBs to Discord
5. **Admin API Key** -- Admins enter their admin key to access the Admin tab

### 7. Optional: Discord Hiscores Wall

The script can post a formatted hiscores summary to Discord on a schedule:

1. In Apps Script, run `setupDiscordHiscores()` once to create the time-driven trigger
2. It posts to your configured Discord webhook hourly

### 8. Optional: Wise Old Man Integration

To enable XP tracking:

1. Create a group on [Wise Old Man](https://wiseoldman.net)
2. Copy the group ID from the URL (e.g., `wiseoldman.net/groups/4983` -> ID is `4983`)
3. Set `womGroupId` in the Settings tab (or via the Admin panel)

## Plugin Tabs

| Tab | Description |
|-----|-------------|
| **Home** | Connection status, announcements, and navigation |
| **Drops** | Recent clan drops with item icons and values |
| **Hiscores** | Boss completion time leaderboards (select group/boss/size) |
| **XP** | Wise Old Man XP gained and total XP leaderboards |
| **Activity** | Recent clan member activity from WOM |
| **Admin** | Manage shared settings, rotate keys, moderate hiscores |

## Admin Features

Admins (users with the admin key) can:

- **Edit shared settings** -- Clan name, Discord webhook, WOM group ID, announcements
- **Rotate the API key** -- Generates a new clan code (all members must update)
- **Moderate hiscores** -- Remove individual entries by boss/category and rank

## Google Apps Script Files

| File | Purpose |
|------|---------|
| `ClanDropLog.gs` | API endpoints for drops, hiscores, config, and admin actions |
| `ClanDropLogSetup.gs` | Creates and formats sheet tabs (run once) |
| `ClanDropWhitelist.gs` | Manages the drop whitelist from wiki data |

## License

BSD 2-Clause License. See [LICENSE](LICENSE).
