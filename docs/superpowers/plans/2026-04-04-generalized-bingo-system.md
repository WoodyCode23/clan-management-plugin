# Generalized Bingo System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully customizable bingo event system where admins define board layout, tiles, teams, scoring, and bounties in a Google Sheet, and the RuneLite plugin renders it all dynamically.

**Architecture:** Separate Google Sheet with BingoSetup.gs (creates tabs) + BingoAPI.gs (all endpoints). Plugin reads config via API, renders dynamic board in a new Bingo tab. WOM data pushed to sheet for KC/XP tiles. Bounty alerts with persistent fired state. Admin controls in-plugin and in-sheet.

**Tech Stack:** Java 11 (RuneLite plugin, Swing UI, OkHttp3, Gson, Lombok), Google Apps Script (JavaScript), Wise Old Man API v2

**Spec:** `docs/superpowers/specs/2026-04-04-generalized-bingo-system-design.md`

---

## File Map

### New Files — Google Apps Script
- `google-apps-script/BingoSetup.gs` — Sheet setup + SWB26 migration
- `google-apps-script/BingoAPI.gs` — All bingo GET/POST endpoints

### New Files — Java (src/main/java/com/droplogger/)
- `BingoTile.java` — Tile data model
- `BingoTeam.java` — Team data model
- `BingoBounty.java` — Bounty data model
- `BingoConfig.java` — Full bingo config container (grid, tiles, teams, bounties, dates)
- `BingoStandings.java` — Team + individual standings data model
- `BingoService.java` — HTTP service for all BingoAPI.gs calls
- `BingoPanel.java` — Bingo tab UI (grid, standings, bounties, drops)
- `BountyScheduler.java` — Timed bounty hint/release alerts

### Modified Files
- `ClanManagementConfig.java` — Add `bingoApiUrl` config item
- `ClanManagementPlugin.java` — Inject BingoService, wire bingo refresh cycle, bounty scheduler, admin callbacks, WOM push
- `ClanPanel.java` — Dynamic Bingo tab add/remove
- `AdminPanel.java` — Bingo management section
- `DiscordWebhookService.java` — Bingo Discord embeds

---

## Task 1: BingoSetup.gs — Sheet Setup Script

**Files:**
- Create: `google-apps-script/BingoSetup.gs`

This creates the entire sheet structure when an admin runs it.

- [ ] **Step 1: Write setupBingoSheet() with Config and Tiles tabs**

```javascript
/**
 * Bingo Sheet Setup — Run this once to create all required tabs.
 * Extensions > Apps Script > Run > setupBingoSheet
 */

function setupBingoSheet() {
  var ui = SpreadsheetApp.getUi();
  var result = ui.prompt(
    "Bingo Setup",
    "Enter grid size (e.g., 5 for 5x5, 6 for 6x6):",
    ui.ButtonSet.OK_CANCEL
  );

  if (result.getSelectedButton() !== ui.Button.OK) return;
  var gridSize = parseInt(result.getResponseText()) || 5;
  if (gridSize < 2 || gridSize > 10) {
    ui.alert("Grid size must be between 2 and 10.");
    return;
  }

  var ss = SpreadsheetApp.getActiveSpreadsheet();

  // ── Config tab ──
  createConfigTab_(ss, gridSize);

  // ── Tiles tab ──
  createTilesTab_(ss, gridSize);

  // ── Teams tab ──
  createTeamsTab_(ss);

  // ── Roster tab ──
  createRosterTab_(ss);

  // ── Whitelist tab ──
  createWhitelistTab_(ss);

  // ── Droplog tab ──
  createDroplogTab_(ss);

  // ── Bounties tab ──
  createBountiesTab_(ss);

  ui.alert("Bingo sheet setup complete!\n\nFill in your tiles, teams, roster, whitelist, and bounties.\nThen deploy as Web App and paste the URL into the plugin.");
}

function createConfigTab_(ss, gridSize) {
  var sheet = ss.getSheetByName("Config");
  if (!sheet) sheet = ss.insertSheet("Config");
  sheet.clear();

  sheet.getRange("A1:B1").setValues([["Key", "Value"]]);
  sheet.getRange("A1:B1").setFontWeight("bold");

  var now = new Date();
  var twoWeeks = new Date(now.getTime() + 14 * 24 * 60 * 60 * 1000);
  var startStr = Utilities.formatDate(now, "America/New_York", "yyyy-MM-dd'T'HH:mm");
  var endStr = Utilities.formatDate(twoWeeks, "America/New_York", "yyyy-MM-dd'T'HH:mm");

  var settings = [
    ["gridRows", gridSize],
    ["gridCols", gridSize],
    ["eventName", "My Bingo Event"],
    ["startDate", startStr],
    ["endDate", endStr],
    ["apiKey", "changeme"],
    ["adminKey", "changeme-admin"],
    ["hintMinutesBefore", "15"]
  ];
  sheet.getRange(2, 1, settings.length, 2).setValues(settings);
  sheet.setColumnWidth(1, 200);
  sheet.setColumnWidth(2, 300);
}

function createTilesTab_(ss, gridSize) {
  var sheet = ss.getSheetByName("Tiles");
  if (!sheet) sheet = ss.insertSheet("Tiles");
  sheet.clear();

  var headers = ["Code", "Name", "Type", "Metric", "Threshold", "Max", "Row", "Col"];
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");

  // Auto-generate tile codes: A1, A2, ... B1, B2, ...
  var rows = [];
  for (var col = 0; col < gridSize; col++) {
    var letter = String.fromCharCode(65 + col); // A, B, C, ...
    for (var row = 0; row < gridSize; row++) {
      var code = letter + (row + 1);
      rows.push([code, "Tile " + code, "drop", "", 100, 200, row, col]);
    }
  }
  sheet.getRange(2, 1, rows.length, headers.length).setValues(rows);

  // Data validation for Type column
  var typeRule = SpreadsheetApp.newDataValidation()
    .requireValueInList(["drop", "kc", "xp"])
    .setAllowInvalid(false)
    .build();
  sheet.getRange(2, 3, rows.length, 1).setDataValidation(typeRule);
}

function createTeamsTab_(ss) {
  var sheet = ss.getSheetByName("Teams");
  if (!sheet) sheet = ss.insertSheet("Teams");
  sheet.clear();

  sheet.getRange("A1:B1").setValues([["Code", "Name"]]);
  sheet.getRange("A1:B1").setFontWeight("bold");

  // Example teams
  sheet.getRange(2, 1, 2, 2).setValues([
    ["TEAM1", "Team One"],
    ["TEAM2", "Team Two"]
  ]);
}

function createRosterTab_(ss) {
  var sheet = ss.getSheetByName("Roster");
  if (!sheet) sheet = ss.insertSheet("Roster");
  sheet.clear();

  sheet.getRange("A1:B1").setValues([["RSN", "Team"]]);
  sheet.getRange("A1:B1").setFontWeight("bold");
}

function createWhitelistTab_(ss) {
  var sheet = ss.getSheetByName("Whitelist");
  if (!sheet) sheet = ss.insertSheet("Whitelist");
  sheet.clear();

  sheet.getRange("A1:C1").setValues([["Item", "Tile Code", "Points"]]);
  sheet.getRange("A1:C1").setFontWeight("bold");
}

function createDroplogTab_(ss) {
  var sheet = ss.getSheetByName("Droplog");
  if (!sheet) sheet = ss.insertSheet("Droplog");
  sheet.clear();

  var headers = ["Timestamp", "RSN", "Team", "Item", "Tile", "Points"];
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");
}

function createBountiesTab_(ss) {
  var sheet = ss.getSheetByName("Bounties");
  if (!sheet) sheet = ss.insertSheet("Bounties");
  sheet.clear();

  var headers = ["Number", "Description", "Release Time", "Points", "Winner", "Hint Fired", "Release Fired"];
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");
}

/**
 * Create a team progress sheet for a specific team.
 * Called by BingoAPI.gs when teams are read.
 */
function ensureTeamProgressSheet_(ss, teamCode, tiles) {
  var sheetName = "Progress_" + teamCode;
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    sheet.getRange("A1:C1").setValues([["Tile Code", "Tile Name", "Points"]]);
    sheet.getRange("A1:C1").setFontWeight("bold");
  }

  // Ensure all tiles have a row
  var existing = {};
  if (sheet.getLastRow() > 1) {
    var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
    for (var i = 0; i < data.length; i++) {
      existing[(data[i][0] || "").toString().trim()] = true;
    }
  }

  var toAdd = [];
  for (var i = 0; i < tiles.length; i++) {
    if (!existing[tiles[i].code]) {
      toAdd.push([tiles[i].code, tiles[i].name, 0]);
    }
  }
  if (toAdd.length > 0) {
    var startRow = sheet.getLastRow() + 1;
    sheet.getRange(startRow, 1, toAdd.length, 3).setValues(toAdd);
  }

  return sheet;
}
```

- [ ] **Step 2: Commit**

```bash
git add google-apps-script/BingoSetup.gs
git commit -m "feat: add BingoSetup.gs — creates all bingo sheet tabs with configurable grid size"
```

---

## Task 2: BingoAPI.gs — Backend API (GET Endpoints)

**Files:**
- Create: `google-apps-script/BingoAPI.gs`

- [ ] **Step 1: Write helper functions and doGet handler with all GET endpoints**

```javascript
/**
 * Bingo API — Google Apps Script
 *
 * Deploy as Web App (Execute as: Me, Access: Anyone).
 *
 * GET endpoints:
 *   ?action=ping                    → {status: "ok"}
 *   ?action=getBingoConfig          → Full config + tiles + teams + bounties
 *   ?action=getTeamProgress&team=X  → Per-tile points for team X
 *   ?action=getAllStandings         → Team + individual rankings
 *   ?action=getDroplog              → Recent drops (optional &team=X, &limit=N)
 *   ?action=getWhitelist            → Drop whitelist
 *   ?action=getBounties             → Full bounty list with fired state
 */

// ── Helpers ──

function getConfigValue_(key) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Config");
  if (!sheet) return null;
  var data = sheet.getRange("A2:B100").getValues();
  for (var i = 0; i < data.length; i++) {
    if ((data[i][0] || "").toString().trim() === key) {
      return (data[i][1] || "").toString().trim();
    }
  }
  return null;
}

function setConfigValue_(key, value) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Config");
  if (!sheet) return;
  var data = sheet.getRange("A2:A100").getValues();
  for (var i = 0; i < data.length; i++) {
    if ((data[i][0] || "").toString().trim() === key) {
      sheet.getRange(i + 2, 2).setValue(value);
      return;
    }
  }
  // Key not found — append
  var lastRow = sheet.getLastRow();
  sheet.getRange(lastRow + 1, 1, 1, 2).setValues([[key, value]]);
}

function resp_(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

function readTiles_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Tiles");
  if (!sheet || sheet.getLastRow() < 2) return [];
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 8).getValues();
  var tiles = [];
  for (var i = 0; i < data.length; i++) {
    var code = (data[i][0] || "").toString().trim();
    if (!code) continue;
    tiles.push({
      code: code,
      name: (data[i][1] || "").toString().trim(),
      type: (data[i][2] || "drop").toString().trim().toLowerCase(),
      metric: (data[i][3] || "").toString().trim(),
      threshold: parseFloat(data[i][4]) || 100,
      max: parseFloat(data[i][5]) || 200,
      row: parseInt(data[i][6]) || 0,
      col: parseInt(data[i][7]) || 0
    });
  }
  return tiles;
}

function readTeams_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Teams");
  if (!sheet || sheet.getLastRow() < 2) return [];
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 2).getValues();
  var teams = [];
  for (var i = 0; i < data.length; i++) {
    var code = (data[i][0] || "").toString().trim();
    if (!code) continue;
    teams.push({
      code: code,
      name: (data[i][1] || "").toString().trim()
    });
  }
  return teams;
}

function readRoster_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Roster");
  if (!sheet || sheet.getLastRow() < 2) return [];
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 2).getValues();
  var roster = [];
  for (var i = 0; i < data.length; i++) {
    var rsn = (data[i][0] || "").toString().trim();
    if (!rsn) continue;
    roster.push({
      rsn: rsn,
      team: (data[i][1] || "").toString().trim()
    });
  }
  return roster;
}

function readBounties_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Bounties");
  if (!sheet || sheet.getLastRow() < 2) return [];
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 7).getValues();
  var bounties = [];
  for (var i = 0; i < data.length; i++) {
    var num = parseInt(data[i][0]);
    if (!num) continue;
    bounties.push({
      number: num,
      description: (data[i][1] || "").toString().trim(),
      releaseTime: (data[i][2] || "").toString().trim(),
      points: parseFloat(data[i][3]) || 0,
      winner: (data[i][4] || "").toString().trim(),
      hintFired: (data[i][5] || "").toString().toUpperCase() === "TRUE",
      releaseFired: (data[i][6] || "").toString().toUpperCase() === "TRUE"
    });
  }
  return bounties;
}

function readWhitelist_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Whitelist");
  if (!sheet || sheet.getLastRow() < 2) return [];
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 3).getValues();
  var items = [];
  for (var i = 0; i < data.length; i++) {
    var item = (data[i][0] || "").toString().trim();
    if (!item) continue;
    items.push({
      item: item,
      tileCode: (data[i][1] || "").toString().trim(),
      points: parseFloat(data[i][2]) || 0
    });
  }
  return items;
}

function readTeamProgress_(teamCode) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheetName = "Progress_" + teamCode;
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet || sheet.getLastRow() < 2) return [];
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 3).getValues();
  var progress = [];
  for (var i = 0; i < data.length; i++) {
    var code = (data[i][0] || "").toString().trim();
    if (!code) continue;
    progress.push({
      tileCode: code,
      tileName: (data[i][1] || "").toString().trim(),
      points: parseFloat(data[i][2]) || 0
    });
  }
  return progress;
}

// ── doGet ──

function doGet(e) {
  var key = (e.parameter && e.parameter.key) ? e.parameter.key : "";
  var apiKey = getConfigValue_("apiKey") || "changeme";
  if (key !== apiKey) {
    return resp_({ status: "error", message: "Invalid API key" });
  }

  var action = (e.parameter && e.parameter.action) ? e.parameter.action : "ping";

  if (action === "ping") {
    return resp_({ status: "ok", message: "Bingo API is running" });
  }

  if (action === "getBingoConfig") {
    var tiles = readTiles_();
    var teams = readTeams_();
    var bounties = readBounties_();
    var roster = readRoster_();

    // Ensure team progress sheets exist
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    for (var i = 0; i < teams.length; i++) {
      ensureTeamProgressSheet_(ss, teams[i].code, tiles);
    }

    return resp_({
      gridRows: parseInt(getConfigValue_("gridRows")) || 5,
      gridCols: parseInt(getConfigValue_("gridCols")) || 5,
      eventName: getConfigValue_("eventName") || "Bingo Event",
      startDate: getConfigValue_("startDate") || "",
      endDate: getConfigValue_("endDate") || "",
      hintMinutesBefore: parseInt(getConfigValue_("hintMinutesBefore")) || 15,
      tiles: tiles,
      teams: teams,
      bounties: bounties,
      roster: roster
    });
  }

  if (action === "getTeamProgress") {
    var team = (e.parameter.team || "").trim();
    if (!team) return resp_({ status: "error", message: "Missing team parameter" });
    return resp_({ team: team, progress: readTeamProgress_(team) });
  }

  if (action === "getAllStandings") {
    return resp_(computeAllStandings_());
  }

  if (action === "getDroplog") {
    var team = (e.parameter.team || "").trim();
    var limit = parseInt(e.parameter.limit) || 50;
    return resp_(getDroplog_(team, limit));
  }

  if (action === "getWhitelist") {
    return resp_({ items: readWhitelist_() });
  }

  if (action === "getBounties") {
    return resp_({ bounties: readBounties_() });
  }

  return resp_({ status: "error", message: "Unknown action: " + action });
}

function computeAllStandings_() {
  var teams = readTeams_();
  var bounties = readBounties_();
  var roster = readRoster_();

  // Build team → bounty bonus map
  var bountyBonus = {};
  for (var i = 0; i < bounties.length; i++) {
    var w = bounties[i].winner;
    if (w) {
      bountyBonus[w] = (bountyBonus[w] || 0) + bounties[i].points;
    }
  }

  // Build RSN → team map
  var rsnTeam = {};
  for (var i = 0; i < roster.length; i++) {
    rsnTeam[roster[i].rsn.toLowerCase()] = roster[i].team;
  }

  // Team standings: sum all tile progress + bounty bonus
  var teamStandings = [];
  for (var i = 0; i < teams.length; i++) {
    var progress = readTeamProgress_(teams[i].code);
    var tilePoints = 0;
    for (var j = 0; j < progress.length; j++) {
      tilePoints += progress[j].points;
    }
    var bonus = bountyBonus[teams[i].code] || 0;
    teamStandings.push({
      code: teams[i].code,
      name: teams[i].name,
      tilePoints: tilePoints,
      bountyBonus: bonus,
      totalPoints: tilePoints + bonus
    });
  }
  teamStandings.sort(function(a, b) { return b.totalPoints - a.totalPoints; });
  for (var i = 0; i < teamStandings.length; i++) {
    teamStandings[i].rank = i + 1;
  }

  // Individual standings: sum drops per player from Droplog
  var playerPoints = {};
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Droplog");
  if (sheet && sheet.getLastRow() > 1) {
    var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 6).getValues();
    for (var i = 0; i < data.length; i++) {
      var rsn = (data[i][1] || "").toString().trim();
      var pts = parseFloat(data[i][5]) || 0;
      if (rsn) {
        playerPoints[rsn] = (playerPoints[rsn] || 0) + pts;
      }
    }
  }

  var individualStandings = [];
  for (var rsn in playerPoints) {
    var team = rsnTeam[rsn.toLowerCase()] || "";
    individualStandings.push({
      rsn: rsn,
      team: team,
      points: playerPoints[rsn]
    });
  }
  individualStandings.sort(function(a, b) { return b.points - a.points; });
  for (var i = 0; i < individualStandings.length; i++) {
    individualStandings[i].rank = i + 1;
  }

  return {
    teamStandings: teamStandings,
    individualStandings: individualStandings
  };
}

function getDroplog_(teamFilter, limit) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Droplog");
  if (!sheet || sheet.getLastRow() < 2) return { drops: [] };
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 6).getValues();

  var drops = [];
  // Read from bottom (most recent first)
  for (var i = data.length - 1; i >= 0 && drops.length < limit; i--) {
    var team = (data[i][2] || "").toString().trim();
    if (teamFilter && team !== teamFilter) continue;
    drops.push({
      timestamp: (data[i][0] || "").toString(),
      rsn: (data[i][1] || "").toString().trim(),
      team: team,
      item: (data[i][3] || "").toString().trim(),
      tile: (data[i][4] || "").toString().trim(),
      points: parseFloat(data[i][5]) || 0
    });
  }
  return { drops: drops };
}
```

- [ ] **Step 2: Commit**

```bash
git add google-apps-script/BingoAPI.gs
git commit -m "feat: add BingoAPI.gs GET endpoints — config, progress, standings, drops, whitelist, bounties"
```

---

## Task 3: BingoAPI.gs — POST Endpoints

**Files:**
- Modify: `google-apps-script/BingoAPI.gs`

- [ ] **Step 1: Add doPost handler with all POST endpoints**

Append to `BingoAPI.gs`:

```javascript
// ── doPost ──

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var key = data.key || "";
    var apiKey = getConfigValue_("apiKey") || "changeme";
    if (key !== apiKey) {
      return resp_({ status: "error", message: "Invalid API key" });
    }

    var action = (data.action || "").trim();
    var adminKey = getConfigValue_("adminKey") || "changeme-admin";

    // ── Regular key actions ──

    if (action === "submitDrop") {
      return resp_(handleSubmitDrop_(data));
    }

    if (action === "updateTileProgress") {
      return resp_(handleUpdateTileProgress_(data));
    }

    if (action === "markBountyFired") {
      return resp_(handleMarkBountyFired_(data));
    }

    // ── Admin key actions ──

    if (action.indexOf("admin") === 0) {
      if ((data.adminKey || "") !== adminKey) {
        return resp_({ status: "error", message: "Invalid admin key" });
      }
    }

    if (action === "adminUpdateTile") return resp_(handleAdminUpdateTile_(data));
    if (action === "adminAddTile") return resp_(handleAdminAddTile_(data));
    if (action === "adminRemoveTile") return resp_(handleAdminRemoveTile_(data));
    if (action === "adminUpdateTeam") return resp_(handleAdminUpdateTeam_(data));
    if (action === "adminRemoveTeam") return resp_(handleAdminRemoveTeam_(data));
    if (action === "adminUpdateRoster") return resp_(handleAdminUpdateRoster_(data));
    if (action === "adminRemoveRoster") return resp_(handleAdminRemoveRoster_(data));
    if (action === "adminUpdateBounty") return resp_(handleAdminUpdateBounty_(data));
    if (action === "adminAddBounty") return resp_(handleAdminAddBounty_(data));
    if (action === "adminRemoveBounty") return resp_(handleAdminRemoveBounty_(data));
    if (action === "adminManualProgress") return resp_(handleAdminManualProgress_(data));
    if (action === "adminUpdateConfig") return resp_(handleAdminUpdateConfig_(data));
    if (action === "adminUpdateWhitelist") return resp_(handleAdminUpdateWhitelist_(data));

    return resp_({ status: "error", message: "Unknown action: " + action });

  } catch (err) {
    return resp_({ status: "error", message: err.toString() });
  }
}

// ── Drop submission ──

function handleSubmitDrop_(data) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var player = (data.player || "").trim();
    var item = (data.item || "").trim();
    var team = (data.team || "").trim();
    var timestamp = data.timestamp || new Date().toISOString();

    if (!player || !item) {
      lock.releaseLock();
      return { status: "error", message: "Missing player or item" };
    }

    // Look up in whitelist
    var whitelist = readWhitelist_();
    var match = null;
    for (var i = 0; i < whitelist.length; i++) {
      if (whitelist[i].item.toLowerCase() === item.toLowerCase()) {
        match = whitelist[i];
        break;
      }
    }

    if (!match) {
      lock.releaseLock();
      return { status: "error", message: "Item not in whitelist: " + item };
    }

    // Log to Droplog
    var dropSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Droplog");
    if (!dropSheet) {
      lock.releaseLock();
      return { status: "error", message: "Droplog sheet not found" };
    }
    var newRow = dropSheet.getLastRow() + 1;
    dropSheet.getRange(newRow, 1, 1, 6).setValues([
      [timestamp, player, team, item, match.tileCode, match.points]
    ]);

    // Add points to team progress
    if (team && match.tileCode) {
      addPointsToTeamTile_(team, match.tileCode, match.points);
    }

    lock.releaseLock();
    return { status: "ok", message: "Drop logged", tile: match.tileCode, points: match.points };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

function addPointsToTeamTile_(teamCode, tileCode, points) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheetName = "Progress_" + teamCode;
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    // Create progress sheet with current tiles
    var tiles = readTiles_();
    sheet = ensureTeamProgressSheet_(ss, teamCode, tiles);
  }

  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 3).getValues();
  for (var i = 0; i < data.length; i++) {
    if ((data[i][0] || "").toString().trim() === tileCode) {
      var current = parseFloat(data[i][2]) || 0;
      sheet.getRange(i + 2, 3).setValue(current + points);
      return;
    }
  }
  // Tile not found in progress — add it
  var newRow = sheet.getLastRow() + 1;
  sheet.getRange(newRow, 1, 1, 3).setValues([[tileCode, "", points]]);
}

// ── KC/XP tile progress update ──

function handleUpdateTileProgress_(data) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var team = (data.team || "").trim();
    var tileCode = (data.tileCode || "").trim();
    var points = parseFloat(data.points) || 0;
    if (!team || !tileCode) {
      lock.releaseLock();
      return { status: "error", message: "Missing team or tileCode" };
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheetName = "Progress_" + team;
    var sheet = ss.getSheetByName(sheetName);
    if (!sheet) {
      var tiles = readTiles_();
      sheet = ensureTeamProgressSheet_(ss, team, tiles);
    }

    // Set (not add) the points — WOM data is absolute
    var sheetData = sheet.getRange(2, 1, sheet.getLastRow() - 1, 3).getValues();
    for (var i = 0; i < sheetData.length; i++) {
      if ((sheetData[i][0] || "").toString().trim() === tileCode) {
        sheet.getRange(i + 2, 3).setValue(points);
        lock.releaseLock();
        return { status: "ok", message: "Progress updated" };
      }
    }

    // Not found — add row
    var newRow = sheet.getLastRow() + 1;
    sheet.getRange(newRow, 1, 1, 3).setValues([[tileCode, "", points]]);
    lock.releaseLock();
    return { status: "ok", message: "Progress added" };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

// ── Bounty fired state ──

function handleMarkBountyFired_(data) {
  var number = parseInt(data.number);
  var field = (data.field || "").trim();
  if (!number || (field !== "hintFired" && field !== "releaseFired")) {
    return { status: "error", message: "Invalid number or field" };
  }

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Bounties");
  if (!sheet || sheet.getLastRow() < 2) {
    return { status: "error", message: "Bounties sheet not found" };
  }

  var colIndex = field === "hintFired" ? 6 : 7;
  var bountyData = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
  for (var i = 0; i < bountyData.length; i++) {
    if (parseInt(bountyData[i][0]) === number) {
      sheet.getRange(i + 2, colIndex).setValue("TRUE");
      return { status: "ok", message: field + " marked for bounty #" + number };
    }
  }
  return { status: "error", message: "Bounty #" + number + " not found" };
}

// ── Admin: Tile management ──

function handleAdminUpdateTile_(data) {
  var tileCode = (data.tileCode || "").trim();
  if (!tileCode) return { status: "error", message: "Missing tileCode" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Tiles");
  if (!sheet) return { status: "error", message: "Tiles sheet not found" };

  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 8).getValues();
  for (var i = 0; i < rows.length; i++) {
    if ((rows[i][0] || "").toString().trim() === tileCode) {
      var r = i + 2;
      if (data.hasOwnProperty("name")) sheet.getRange(r, 2).setValue(data.name);
      if (data.hasOwnProperty("type")) sheet.getRange(r, 3).setValue(data.type);
      if (data.hasOwnProperty("metric")) sheet.getRange(r, 4).setValue(data.metric);
      if (data.hasOwnProperty("threshold")) sheet.getRange(r, 5).setValue(data.threshold);
      if (data.hasOwnProperty("max")) sheet.getRange(r, 6).setValue(data.max);
      return { status: "ok", message: "Tile " + tileCode + " updated" };
    }
  }
  return { status: "error", message: "Tile " + tileCode + " not found" };
}

function handleAdminAddTile_(data) {
  var code = (data.code || "").trim();
  if (!code) return { status: "error", message: "Missing tile code" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Tiles");
  if (!sheet) return { status: "error", message: "Tiles sheet not found" };

  var newRow = sheet.getLastRow() + 1;
  sheet.getRange(newRow, 1, 1, 8).setValues([[
    code,
    data.name || "",
    data.type || "drop",
    data.metric || "",
    data.threshold || 100,
    data.max || 200,
    data.row || 0,
    data.col || 0
  ]]);
  return { status: "ok", message: "Tile " + code + " added" };
}

function handleAdminRemoveTile_(data) {
  var tileCode = (data.tileCode || "").trim();
  if (!tileCode) return { status: "error", message: "Missing tileCode" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Tiles");
  if (!sheet) return { status: "error", message: "Tiles sheet not found" };

  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
  for (var i = 0; i < rows.length; i++) {
    if ((rows[i][0] || "").toString().trim() === tileCode) {
      sheet.deleteRow(i + 2);
      return { status: "ok", message: "Tile " + tileCode + " removed" };
    }
  }
  return { status: "error", message: "Tile " + tileCode + " not found" };
}

// ── Admin: Team management ──

function handleAdminUpdateTeam_(data) {
  var code = (data.code || "").trim();
  if (!code) return { status: "error", message: "Missing team code" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Teams");
  if (!sheet) return { status: "error", message: "Teams sheet not found" };

  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 2).getValues();
  for (var i = 0; i < rows.length; i++) {
    if ((rows[i][0] || "").toString().trim() === code) {
      if (data.hasOwnProperty("name")) sheet.getRange(i + 2, 2).setValue(data.name);
      return { status: "ok", message: "Team " + code + " updated" };
    }
  }
  // Not found — add new team
  var newRow = sheet.getLastRow() + 1;
  sheet.getRange(newRow, 1, 1, 2).setValues([[code, data.name || code]]);
  return { status: "ok", message: "Team " + code + " added" };
}

function handleAdminRemoveTeam_(data) {
  var code = (data.code || "").trim();
  if (!code) return { status: "error", message: "Missing team code" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Teams");
  if (!sheet) return { status: "error", message: "Teams sheet not found" };

  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
  for (var i = 0; i < rows.length; i++) {
    if ((rows[i][0] || "").toString().trim() === code) {
      sheet.deleteRow(i + 2);
      return { status: "ok", message: "Team " + code + " removed" };
    }
  }
  return { status: "error", message: "Team " + code + " not found" };
}

// ── Admin: Roster management ──

function handleAdminUpdateRoster_(data) {
  var rsn = (data.rsn || "").trim();
  var team = (data.team || "").trim();
  if (!rsn || !team) return { status: "error", message: "Missing rsn or team" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Roster");
  if (!sheet) return { status: "error", message: "Roster sheet not found" };

  var rows = sheet.getRange(2, 1, Math.max(sheet.getLastRow() - 1, 0), 2).getValues();
  for (var i = 0; i < rows.length; i++) {
    if ((rows[i][0] || "").toString().trim().toLowerCase() === rsn.toLowerCase()) {
      sheet.getRange(i + 2, 2).setValue(team);
      return { status: "ok", message: rsn + " moved to " + team };
    }
  }
  var newRow = sheet.getLastRow() + 1;
  sheet.getRange(newRow, 1, 1, 2).setValues([[rsn, team]]);
  return { status: "ok", message: rsn + " added to " + team };
}

function handleAdminRemoveRoster_(data) {
  var rsn = (data.rsn || "").trim();
  if (!rsn) return { status: "error", message: "Missing rsn" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Roster");
  if (!sheet) return { status: "error", message: "Roster sheet not found" };

  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
  for (var i = 0; i < rows.length; i++) {
    if ((rows[i][0] || "").toString().trim().toLowerCase() === rsn.toLowerCase()) {
      sheet.deleteRow(i + 2);
      return { status: "ok", message: rsn + " removed from roster" };
    }
  }
  return { status: "error", message: rsn + " not found in roster" };
}

// ── Admin: Bounty management ──

function handleAdminUpdateBounty_(data) {
  var number = parseInt(data.number);
  if (!number) return { status: "error", message: "Missing bounty number" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Bounties");
  if (!sheet) return { status: "error", message: "Bounties sheet not found" };

  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 7).getValues();
  for (var i = 0; i < rows.length; i++) {
    if (parseInt(rows[i][0]) === number) {
      var r = i + 2;
      if (data.hasOwnProperty("description")) sheet.getRange(r, 2).setValue(data.description);
      if (data.hasOwnProperty("releaseTime")) sheet.getRange(r, 3).setValue(data.releaseTime);
      if (data.hasOwnProperty("points")) sheet.getRange(r, 4).setValue(data.points);
      if (data.hasOwnProperty("winner")) sheet.getRange(r, 5).setValue(data.winner);
      return { status: "ok", message: "Bounty #" + number + " updated" };
    }
  }
  return { status: "error", message: "Bounty #" + number + " not found" };
}

function handleAdminAddBounty_(data) {
  var number = parseInt(data.number);
  if (!number) return { status: "error", message: "Missing bounty number" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Bounties");
  if (!sheet) return { status: "error", message: "Bounties sheet not found" };

  var newRow = sheet.getLastRow() + 1;
  sheet.getRange(newRow, 1, 1, 7).setValues([[
    number,
    data.description || "",
    data.releaseTime || "",
    data.points || 0,
    "",
    "FALSE",
    "FALSE"
  ]]);
  return { status: "ok", message: "Bounty #" + number + " added" };
}

function handleAdminRemoveBounty_(data) {
  var number = parseInt(data.number);
  if (!number) return { status: "error", message: "Missing bounty number" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Bounties");
  if (!sheet) return { status: "error", message: "Bounties sheet not found" };

  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
  for (var i = 0; i < rows.length; i++) {
    if (parseInt(rows[i][0]) === number) {
      sheet.deleteRow(i + 2);
      return { status: "ok", message: "Bounty #" + number + " removed" };
    }
  }
  return { status: "error", message: "Bounty #" + number + " not found" };
}

// ── Admin: Manual progress & config ──

function handleAdminManualProgress_(data) {
  var team = (data.team || "").trim();
  var tileCode = (data.tileCode || "").trim();
  var points = parseFloat(data.points);
  if (!team || !tileCode || isNaN(points)) {
    return { status: "error", message: "Missing team, tileCode, or points" };
  }

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    addPointsToTeamTile_(team, tileCode, points);
    lock.releaseLock();
    return { status: "ok", message: "Added " + points + " to " + team + " / " + tileCode };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

function handleAdminUpdateConfig_(data) {
  var key = (data.configKey || "").trim();
  var value = (data.hasOwnProperty("configValue")) ? data.configValue : "";
  if (!key) return { status: "error", message: "Missing configKey" };
  setConfigValue_(key, value);
  return { status: "ok", message: "Config " + key + " updated" };
}

function handleAdminUpdateWhitelist_(data) {
  var whiteAction = (data.whitelistAction || "").trim().toLowerCase();
  var item = (data.item || "").trim();
  if (!item) return { status: "error", message: "Missing item name" };

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Whitelist");
  if (!sheet) return { status: "error", message: "Whitelist sheet not found" };

  if (whiteAction === "add") {
    var tileCode = (data.tileCode || "").trim();
    var points = parseFloat(data.points) || 0;
    var newRow = sheet.getLastRow() + 1;
    sheet.getRange(newRow, 1, 1, 3).setValues([[item, tileCode, points]]);
    return { status: "ok", message: "Added " + item + " to whitelist" };
  }

  if (whiteAction === "remove") {
    var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
    for (var i = 0; i < rows.length; i++) {
      if ((rows[i][0] || "").toString().trim().toLowerCase() === item.toLowerCase()) {
        sheet.deleteRow(i + 2);
        return { status: "ok", message: "Removed " + item + " from whitelist" };
      }
    }
    return { status: "error", message: item + " not found in whitelist" };
  }

  return { status: "error", message: "Invalid whitelistAction: use 'add' or 'remove'" };
}
```

- [ ] **Step 2: Commit**

```bash
git add google-apps-script/BingoAPI.gs
git commit -m "feat: add BingoAPI.gs POST endpoints — drops, progress, admin CRUD for tiles/teams/bounties/roster"
```

---

## Task 4: Java Data Models

**Files:**
- Create: `src/main/java/com/droplogger/BingoTile.java`
- Create: `src/main/java/com/droplogger/BingoTeam.java`
- Create: `src/main/java/com/droplogger/BingoBounty.java`
- Create: `src/main/java/com/droplogger/BingoConfig.java`
- Create: `src/main/java/com/droplogger/BingoStandings.java`

- [ ] **Step 1: Create BingoTile.java**

```java
package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BingoTile
{
    private final String code;
    private final String name;
    private final String type; // "drop", "kc", "xp"
    private final String metric; // WOM metric for kc/xp tiles
    private final double threshold;
    private final double max;
    private final int row;
    private final int col;
}
```

- [ ] **Step 2: Create BingoTeam.java**

```java
package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class BingoTeam
{
    private final String code;
    private final String name;
    @Setter private double tilePoints;
    @Setter private double bountyBonus;
    @Setter private double totalPoints;
    @Setter private int rank;
}
```

- [ ] **Step 3: Create BingoBounty.java**

```java
package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BingoBounty
{
    private final int number;
    private final String description;
    private final String releaseTime; // ISO format EST
    private final double points;
    private final String winner;
    private final boolean hintFired;
    private final boolean releaseFired;
}
```

- [ ] **Step 4: Create BingoConfig.java**

```java
package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class BingoConfig
{
    private final int gridRows;
    private final int gridCols;
    private final String eventName;
    private final String startDate;
    private final String endDate;
    private final int hintMinutesBefore;
    private final List<BingoTile> tiles;
    private final List<BingoTeam> teams;
    private final List<BingoBounty> bounties;
    private final Map<String, String> roster; // lowercase RSN -> team code
}
```

- [ ] **Step 5: Create BingoStandings.java**

```java
package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BingoStandings
{
    private final List<BingoTeam> teamStandings;
    private final List<PlayerStanding> individualStandings;

    @Getter
    @AllArgsConstructor
    public static class PlayerStanding
    {
        private final int rank;
        private final String rsn;
        private final String team;
        private final double points;
    }
}
```

- [ ] **Step 6: Build to verify models compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/droplogger/BingoTile.java src/main/java/com/droplogger/BingoTeam.java src/main/java/com/droplogger/BingoBounty.java src/main/java/com/droplogger/BingoConfig.java src/main/java/com/droplogger/BingoStandings.java
git commit -m "feat: add bingo data models — BingoTile, BingoTeam, BingoBounty, BingoConfig, BingoStandings"
```

---

## Task 5: BingoService.java — HTTP Service

**Files:**
- Create: `src/main/java/com/droplogger/BingoService.java`

- [ ] **Step 1: Create BingoService with all API methods**

```java
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
public class BingoService
{
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    private String apiUrl;
    private String apiKey;
    private String adminKey;

    // Cached config — refreshed every 5 minutes
    private BingoConfig cachedConfig;
    private long configFetchTime = 0;
    private static final long CONFIG_CACHE_TTL = 5 * 60 * 1000;

    @Inject
    public BingoService(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
        this.gson = gson;
    }

    public void configure(String apiUrl, String apiKey, String adminKey)
    {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.adminKey = adminKey;
        this.cachedConfig = null;
        this.configFetchTime = 0;
    }

    public boolean isConfigured()
    {
        return apiUrl != null && !apiUrl.isEmpty();
    }

    // ── GET methods ──

    public BingoConfig fetchBingoConfig() throws IOException
    {
        long now = System.currentTimeMillis();
        if (cachedConfig != null && (now - configFetchTime) < CONFIG_CACHE_TTL)
        {
            return cachedConfig;
        }

        JsonObject root = doGet("getBingoConfig");

        List<BingoTile> tiles = new ArrayList<>();
        if (root.has("tiles"))
        {
            for (JsonElement elem : root.getAsJsonArray("tiles"))
            {
                JsonObject t = elem.getAsJsonObject();
                tiles.add(new BingoTile(
                    t.get("code").getAsString(),
                    t.has("name") ? t.get("name").getAsString() : "",
                    t.has("type") ? t.get("type").getAsString() : "drop",
                    t.has("metric") ? t.get("metric").getAsString() : "",
                    t.has("threshold") ? t.get("threshold").getAsDouble() : 100,
                    t.has("max") ? t.get("max").getAsDouble() : 200,
                    t.has("row") ? t.get("row").getAsInt() : 0,
                    t.has("col") ? t.get("col").getAsInt() : 0
                ));
            }
        }

        List<BingoTeam> teams = new ArrayList<>();
        if (root.has("teams"))
        {
            for (JsonElement elem : root.getAsJsonArray("teams"))
            {
                JsonObject t = elem.getAsJsonObject();
                teams.add(new BingoTeam(
                    t.get("code").getAsString(),
                    t.has("name") ? t.get("name").getAsString() : "",
                    0, 0, 0, 0
                ));
            }
        }

        List<BingoBounty> bounties = new ArrayList<>();
        if (root.has("bounties"))
        {
            for (JsonElement elem : root.getAsJsonArray("bounties"))
            {
                JsonObject b = elem.getAsJsonObject();
                bounties.add(new BingoBounty(
                    b.get("number").getAsInt(),
                    b.has("description") ? b.get("description").getAsString() : "",
                    b.has("releaseTime") ? b.get("releaseTime").getAsString() : "",
                    b.has("points") ? b.get("points").getAsDouble() : 0,
                    b.has("winner") ? b.get("winner").getAsString() : "",
                    b.has("hintFired") && b.get("hintFired").getAsBoolean(),
                    b.has("releaseFired") && b.get("releaseFired").getAsBoolean()
                ));
            }
        }

        Map<String, String> roster = new LinkedHashMap<>();
        if (root.has("roster"))
        {
            for (JsonElement elem : root.getAsJsonArray("roster"))
            {
                JsonObject r = elem.getAsJsonObject();
                String rsn = r.has("rsn") ? r.get("rsn").getAsString() : "";
                String team = r.has("team") ? r.get("team").getAsString() : "";
                if (!rsn.isEmpty()) roster.put(rsn.toLowerCase(), team);
            }
        }

        cachedConfig = new BingoConfig(
            root.has("gridRows") ? root.get("gridRows").getAsInt() : 5,
            root.has("gridCols") ? root.get("gridCols").getAsInt() : 5,
            root.has("eventName") ? root.get("eventName").getAsString() : "Bingo",
            root.has("startDate") ? root.get("startDate").getAsString() : "",
            root.has("endDate") ? root.get("endDate").getAsString() : "",
            root.has("hintMinutesBefore") ? root.get("hintMinutesBefore").getAsInt() : 15,
            tiles, teams, bounties, roster
        );
        configFetchTime = now;
        return cachedConfig;
    }

    public Map<String, Double> fetchTeamProgress(String teamCode) throws IOException
    {
        JsonObject root = doGet("getTeamProgress", "team", teamCode);
        Map<String, Double> progress = new LinkedHashMap<>();
        if (root.has("progress"))
        {
            for (JsonElement elem : root.getAsJsonArray("progress"))
            {
                JsonObject p = elem.getAsJsonObject();
                String tileCode = p.has("tileCode") ? p.get("tileCode").getAsString() : "";
                double points = p.has("points") ? p.get("points").getAsDouble() : 0;
                if (!tileCode.isEmpty()) progress.put(tileCode, points);
            }
        }
        return progress;
    }

    public BingoStandings fetchAllStandings() throws IOException
    {
        JsonObject root = doGet("getAllStandings");

        List<BingoTeam> teamStandings = new ArrayList<>();
        if (root.has("teamStandings"))
        {
            for (JsonElement elem : root.getAsJsonArray("teamStandings"))
            {
                JsonObject t = elem.getAsJsonObject();
                teamStandings.add(new BingoTeam(
                    t.get("code").getAsString(),
                    t.has("name") ? t.get("name").getAsString() : "",
                    t.has("tilePoints") ? t.get("tilePoints").getAsDouble() : 0,
                    t.has("bountyBonus") ? t.get("bountyBonus").getAsDouble() : 0,
                    t.has("totalPoints") ? t.get("totalPoints").getAsDouble() : 0,
                    t.has("rank") ? t.get("rank").getAsInt() : 0
                ));
            }
        }

        List<BingoStandings.PlayerStanding> individualStandings = new ArrayList<>();
        if (root.has("individualStandings"))
        {
            for (JsonElement elem : root.getAsJsonArray("individualStandings"))
            {
                JsonObject p = elem.getAsJsonObject();
                individualStandings.add(new BingoStandings.PlayerStanding(
                    p.has("rank") ? p.get("rank").getAsInt() : 0,
                    p.has("rsn") ? p.get("rsn").getAsString() : "",
                    p.has("team") ? p.get("team").getAsString() : "",
                    p.has("points") ? p.get("points").getAsDouble() : 0
                ));
            }
        }

        return new BingoStandings(teamStandings, individualStandings);
    }

    public List<Map<String, Object>> fetchDroplog(String teamFilter, int limit) throws IOException
    {
        JsonObject root;
        if (teamFilter != null && !teamFilter.isEmpty())
        {
            root = doGet("getDroplog", "team", teamFilter, "limit", String.valueOf(limit));
        }
        else
        {
            root = doGet("getDroplog", "limit", String.valueOf(limit));
        }

        List<Map<String, Object>> drops = new ArrayList<>();
        if (root.has("drops"))
        {
            for (JsonElement elem : root.getAsJsonArray("drops"))
            {
                JsonObject d = elem.getAsJsonObject();
                Map<String, Object> drop = new LinkedHashMap<>();
                drop.put("timestamp", d.has("timestamp") ? d.get("timestamp").getAsString() : "");
                drop.put("rsn", d.has("rsn") ? d.get("rsn").getAsString() : "");
                drop.put("team", d.has("team") ? d.get("team").getAsString() : "");
                drop.put("item", d.has("item") ? d.get("item").getAsString() : "");
                drop.put("tile", d.has("tile") ? d.get("tile").getAsString() : "");
                drop.put("points", d.has("points") ? d.get("points").getAsDouble() : 0.0);
                drops.add(drop);
            }
        }
        return drops;
    }

    // ── POST methods ──

    public String submitDrop(String player, String item, String team, String timestamp) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "submitDrop");
        payload.addProperty("player", player);
        payload.addProperty("item", item);
        payload.addProperty("team", team);
        payload.addProperty("timestamp", timestamp);
        return doPost(payload, false);
    }

    public String updateTileProgress(String team, String tileCode, double points) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "updateTileProgress");
        payload.addProperty("team", team);
        payload.addProperty("tileCode", tileCode);
        payload.addProperty("points", points);
        return doPost(payload, false);
    }

    public String markBountyFired(int number, String field) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "markBountyFired");
        payload.addProperty("number", number);
        payload.addProperty("field", field);
        return doPost(payload, false);
    }

    // Admin methods

    public String adminUpdateBounty(int number, String winner) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminUpdateBounty");
        payload.addProperty("number", number);
        payload.addProperty("winner", winner);
        return doPost(payload, true);
    }

    public String adminManualProgress(String team, String tileCode, double points) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminManualProgress");
        payload.addProperty("team", team);
        payload.addProperty("tileCode", tileCode);
        payload.addProperty("points", points);
        return doPost(payload, true);
    }

    public String adminUpdateRoster(String rsn, String team) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "adminUpdateRoster");
        payload.addProperty("rsn", rsn);
        payload.addProperty("team", team);
        return doPost(payload, true);
    }

    public void invalidateConfigCache()
    {
        this.cachedConfig = null;
        this.configFetchTime = 0;
    }

    // ── HTTP helpers ──

    private JsonObject doGet(String action, String... params) throws IOException
    {
        if (!isConfigured()) throw new IOException("Bingo API not configured");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(apiUrl).newBuilder()
            .addQueryParameter("action", action)
            .addQueryParameter("key", apiKey != null ? apiKey : "");

        for (int i = 0; i < params.length - 1; i += 2)
        {
            urlBuilder.addQueryParameter(params[i], params[i + 1]);
        }

        Request request = new Request.Builder().url(urlBuilder.build()).get().build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Bingo API returned status: " + response.code());
            }
            String body = response.body().string();
            return new JsonParser().parse(body).getAsJsonObject();
        }
    }

    private String doPost(JsonObject payload, boolean useAdminKey) throws IOException
    {
        if (!isConfigured()) throw new IOException("Bingo API not configured");

        payload.addProperty("key", apiKey != null ? apiKey : "");
        if (useAdminKey)
        {
            payload.addProperty("adminKey", adminKey != null ? adminKey : "");
        }

        RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
        Request request = new Request.Builder().url(apiUrl).post(body).build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Bingo API POST returned status: " + response.code());
            }
            String responseBody = response.body().string();
            JsonObject root = new JsonParser().parse(responseBody).getAsJsonObject();

            if (root.has("status") && "error".equals(root.get("status").getAsString()))
            {
                String message = root.has("message") ? root.get("message").getAsString() : "Unknown error";
                throw new IOException(message);
            }
            return root.has("message") ? root.get("message").getAsString() : "OK";
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/BingoService.java
git commit -m "feat: add BingoService — HTTP client for all bingo API endpoints with config caching"
```

---

## Task 6: BountyScheduler.java

**Files:**
- Create: `src/main/java/com/droplogger/BountyScheduler.java`

- [ ] **Step 1: Create BountyScheduler**

```java
package com.droplogger;

import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Slf4j
public class BountyScheduler
{
    private static final ZoneId EST = ZoneId.of("America/New_York");
    private static final DateTimeFormatter PARSER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ScheduledExecutorService executor;
    private final BingoService bingoService;
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

    // Callbacks: (bountyNumber, message) for chat and Discord
    private BiConsumer<BingoBounty, String> onHint;
    private BiConsumer<BingoBounty, String> onRelease;

    public BountyScheduler(ScheduledExecutorService executor, BingoService bingoService)
    {
        this.executor = executor;
        this.bingoService = bingoService;
    }

    public void setOnHint(BiConsumer<BingoBounty, String> cb) { this.onHint = cb; }
    public void setOnRelease(BiConsumer<BingoBounty, String> cb) { this.onRelease = cb; }

    public void schedule(List<BingoBounty> bounties, int hintMinutesBefore)
    {
        cancel();

        ZonedDateTime now = ZonedDateTime.now(EST);
        long sequentialDelay = 0; // For firing past-but-unfired bounties sequentially

        for (BingoBounty bounty : bounties)
        {
            if (bounty.getReleaseTime() == null || bounty.getReleaseTime().isEmpty()) continue;

            ZonedDateTime releaseTime;
            try
            {
                releaseTime = java.time.LocalDateTime.parse(bounty.getReleaseTime(), PARSER).atZone(EST);
            }
            catch (Exception e)
            {
                log.debug("Failed to parse bounty #{} release time: {}", bounty.getNumber(), bounty.getReleaseTime());
                continue;
            }

            ZonedDateTime hintTime = releaseTime.minusMinutes(hintMinutesBefore);

            // Schedule hint
            if (!bounty.isHintFired())
            {
                long delayMs = ChronoUnit.MILLIS.between(now, hintTime);
                if (delayMs < 0)
                {
                    // Past but never fired — skip, don't spam old hints
                }
                else
                {
                    scheduledTasks.add(executor.schedule(
                        () -> fireHint(bounty, hintMinutesBefore),
                        delayMs, TimeUnit.MILLISECONDS));
                }
            }

            // Schedule release
            if (!bounty.isReleaseFired())
            {
                long delayMs = ChronoUnit.MILLIS.between(now, releaseTime);
                if (delayMs < 0)
                {
                    // Past but never fired — fire with sequential delay to avoid spam
                    sequentialDelay += 5000;
                    final long delay = sequentialDelay;
                    scheduledTasks.add(executor.schedule(
                        () -> fireRelease(bounty),
                        delay, TimeUnit.MILLISECONDS));
                }
                else
                {
                    scheduledTasks.add(executor.schedule(
                        () -> fireRelease(bounty),
                        delayMs, TimeUnit.MILLISECONDS));
                }
            }
        }

        log.info("Bounty scheduler: {} tasks scheduled", scheduledTasks.size());
    }

    public void cancel()
    {
        for (ScheduledFuture<?> task : scheduledTasks)
        {
            task.cancel(false);
        }
        scheduledTasks.clear();
    }

    /**
     * Get the next upcoming bounty and time until release, for countdown display.
     */
    public String getNextBountyCountdown(List<BingoBounty> bounties)
    {
        ZonedDateTime now = ZonedDateTime.now(EST);
        BingoBounty next = null;
        long minDelay = Long.MAX_VALUE;

        for (BingoBounty bounty : bounties)
        {
            if (bounty.getReleaseTime() == null || bounty.getReleaseTime().isEmpty()) continue;
            if (!bounty.getWinner().isEmpty()) continue; // Already completed

            try
            {
                ZonedDateTime releaseTime = java.time.LocalDateTime.parse(bounty.getReleaseTime(), PARSER).atZone(EST);
                long delay = ChronoUnit.MILLIS.between(now, releaseTime);
                if (delay > 0 && delay < minDelay)
                {
                    minDelay = delay;
                    next = bounty;
                }
            }
            catch (Exception ignored) {}
        }

        if (next == null) return null;

        long hours = minDelay / (1000 * 60 * 60);
        long minutes = (minDelay / (1000 * 60)) % 60;
        return "Bounty #" + next.getNumber() + " in " + hours + "h " + minutes + "m";
    }

    private void fireHint(BingoBounty bounty, int minutesBefore)
    {
        String message = "[Bingo] Bounty #" + bounty.getNumber() +
            " hint: releasing in ~" + minutesBefore + " minutes!";
        log.info(message);

        if (onHint != null) onHint.accept(bounty, message);

        // Persist fired state
        try { bingoService.markBountyFired(bounty.getNumber(), "hintFired"); }
        catch (Exception e) { log.warn("Failed to persist hintFired for bounty #{}", bounty.getNumber(), e); }
    }

    private void fireRelease(BingoBounty bounty)
    {
        String message = "[Bingo] Bounty #" + bounty.getNumber() +
            " is NOW LIVE: " + bounty.getDescription();
        log.info(message);

        if (onRelease != null) onRelease.accept(bounty, message);

        // Persist fired state
        try { bingoService.markBountyFired(bounty.getNumber(), "releaseFired"); }
        catch (Exception e) { log.warn("Failed to persist releaseFired for bounty #{}", bounty.getNumber(), e); }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/BountyScheduler.java
git commit -m "feat: add BountyScheduler — timed bounty alerts with persistent fired state and sequential firing"
```

---

## Task 7: BingoPanel.java — Bingo Tab UI

**Files:**
- Create: `src/main/java/com/droplogger/BingoPanel.java`

This is the largest single file. It renders the dynamic board grid, standings, bounties, and drop log.

- [ ] **Step 1: Create BingoPanel.java**

```java
package com.droplogger;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BingoPanel extends JPanel
{
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_ITALIC = new Font("Segoe UI", Font.ITALIC, 10);
    private static final Color ACCENT_GOLD = new Color(0xFF, 0xD7, 0x00);
    private static final NumberFormat NUM_FMT = NumberFormat.getNumberInstance(Locale.US);

    // Tile colors by completion
    private static final Color TILE_GRAY = new Color(60, 60, 60);
    private static final Color TILE_YELLOW = new Color(180, 160, 50);
    private static final Color TILE_GREEN = new Color(50, 150, 60);
    private static final Color TILE_BLACK = new Color(20, 20, 20);

    // Sections
    private final JLabel eventNameLabel = new JLabel("Bingo");
    private final JLabel countdownLabel = new JLabel("");
    private final JLabel teamLabel = new JLabel("");
    private final JPanel gridPanel = new JPanel();
    private final JPanel tileDetailPanel = new JPanel();
    private final JPanel teamStandingsPanel = new JPanel();
    private final JPanel individualStandingsPanel = new JPanel();
    private final JPanel bountyPanel = new JPanel();
    private final JPanel dropLogPanel = new JPanel();

    private BingoConfig config;
    private Map<String, Double> teamProgress;
    private String selectedTileCode;

    public BingoPanel()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Header
        eventNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        eventNameLabel.setForeground(ACCENT_GOLD);
        eventNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        eventNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        eventNameLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        content.add(eventNameLabel);
        content.add(Box.createVerticalStrut(2));

        countdownLabel.setFont(FONT_SMALL);
        countdownLabel.setForeground(new Color(170, 170, 170));
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        countdownLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        content.add(countdownLabel);
        content.add(Box.createVerticalStrut(2));

        teamLabel.setFont(FONT_BOLD);
        teamLabel.setForeground(new Color(46, 204, 113));
        teamLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        teamLabel.setHorizontalAlignment(SwingConstants.CENTER);
        teamLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        content.add(teamLabel);
        content.add(Box.createVerticalStrut(8));

        // Grid
        gridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        gridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(gridPanel);
        content.add(Box.createVerticalStrut(4));

        // Tile detail (hidden initially)
        tileDetailPanel.setLayout(new BoxLayout(tileDetailPanel, BoxLayout.Y_AXIS));
        tileDetailPanel.setBackground(new Color(40, 40, 40));
        tileDetailPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        tileDetailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.setVisible(false);
        tileDetailPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        content.add(tileDetailPanel);
        content.add(Box.createVerticalStrut(8));

        // Team standings
        content.add(createSectionLabel("Team Standings"));
        content.add(Box.createVerticalStrut(4));
        teamStandingsPanel.setLayout(new BoxLayout(teamStandingsPanel, BoxLayout.Y_AXIS));
        teamStandingsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        teamStandingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(teamStandingsPanel);
        content.add(Box.createVerticalStrut(8));

        // Individual standings
        content.add(createSectionLabel("Individual Standings"));
        content.add(Box.createVerticalStrut(4));
        individualStandingsPanel.setLayout(new BoxLayout(individualStandingsPanel, BoxLayout.Y_AXIS));
        individualStandingsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        individualStandingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(individualStandingsPanel);
        content.add(Box.createVerticalStrut(8));

        // Bounties
        content.add(createSectionLabel("Bounties"));
        content.add(Box.createVerticalStrut(4));
        bountyPanel.setLayout(new BoxLayout(bountyPanel, BoxLayout.Y_AXIS));
        bountyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bountyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(bountyPanel);
        content.add(Box.createVerticalStrut(8));

        // Drop log
        content.add(createSectionLabel("Recent Drops"));
        content.add(Box.createVerticalStrut(4));
        dropLogPanel.setLayout(new BoxLayout(dropLogPanel, BoxLayout.Y_AXIS));
        dropLogPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropLogPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(dropLogPanel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateConfig(BingoConfig config, String playerTeamCode, String playerTeamName)
    {
        this.config = config;
        SwingUtilities.invokeLater(() -> {
            eventNameLabel.setText(config.getEventName());
            teamLabel.setText(playerTeamName != null ? playerTeamName : "No team assigned");
            rebuildGrid();
        });
    }

    public void updateCountdown(String text)
    {
        SwingUtilities.invokeLater(() -> countdownLabel.setText(text != null ? text : ""));
    }

    public void updateTeamProgress(Map<String, Double> progress)
    {
        this.teamProgress = progress;
        SwingUtilities.invokeLater(this::refreshGridColors);
    }

    public void updateStandings(BingoStandings standings, String playerRsn, String playerTeamCode)
    {
        SwingUtilities.invokeLater(() -> {
            // Team standings
            teamStandingsPanel.removeAll();
            if (standings.getTeamStandings() != null)
            {
                for (BingoTeam team : standings.getTeamStandings())
                {
                    boolean isMyTeam = team.getCode().equals(playerTeamCode);
                    String text = "#" + team.getRank() + " " + team.getName() + " — " +
                        NUM_FMT.format(team.getTotalPoints()) + " pts";
                    if (team.getBountyBonus() > 0)
                    {
                        text += " (+" + NUM_FMT.format(team.getBountyBonus()) + " bounty)";
                    }
                    JLabel row = new JLabel(text);
                    row.setFont(isMyTeam ? FONT_BOLD : FONT);
                    row.setForeground(isMyTeam ? new Color(46, 204, 113) : new Color(200, 200, 200));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    teamStandingsPanel.add(row);
                    teamStandingsPanel.add(Box.createVerticalStrut(2));
                }
            }
            teamStandingsPanel.revalidate();
            teamStandingsPanel.repaint();

            // Individual standings
            individualStandingsPanel.removeAll();
            if (standings.getIndividualStandings() != null)
            {
                int shown = Math.min(20, standings.getIndividualStandings().size());
                for (int i = 0; i < shown; i++)
                {
                    BingoStandings.PlayerStanding ps = standings.getIndividualStandings().get(i);
                    boolean isMe = ps.getRsn().equalsIgnoreCase(playerRsn);
                    String text = "#" + ps.getRank() + " " + ps.getRsn() + " — " +
                        NUM_FMT.format(ps.getPoints()) + " pts";
                    JLabel row = new JLabel(text);
                    row.setFont(isMe ? FONT_BOLD : FONT);
                    row.setForeground(isMe ? new Color(46, 204, 113) : new Color(200, 200, 200));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    individualStandingsPanel.add(row);
                    individualStandingsPanel.add(Box.createVerticalStrut(2));
                }
            }
            individualStandingsPanel.revalidate();
            individualStandingsPanel.repaint();
        });
    }

    public void updateBounties(List<BingoBounty> bounties, String nextCountdown)
    {
        SwingUtilities.invokeLater(() -> {
            bountyPanel.removeAll();

            if (nextCountdown != null)
            {
                JLabel countdown = new JLabel(nextCountdown);
                countdown.setFont(FONT_BOLD);
                countdown.setForeground(new Color(241, 196, 15));
                countdown.setAlignmentX(Component.LEFT_ALIGNMENT);
                bountyPanel.add(countdown);
                bountyPanel.add(Box.createVerticalStrut(4));
            }

            if (bounties != null)
            {
                for (BingoBounty b : bounties)
                {
                    String status;
                    Color color;
                    if (!b.getWinner().isEmpty())
                    {
                        status = "Won by " + b.getWinner() + " (+" + NUM_FMT.format(b.getPoints()) + " pts)";
                        color = new Color(46, 204, 113);
                    }
                    else if (b.isReleaseFired())
                    {
                        status = "LIVE";
                        color = new Color(231, 76, 60);
                    }
                    else
                    {
                        status = "Upcoming";
                        color = new Color(150, 150, 150);
                    }

                    JLabel row = new JLabel("#" + b.getNumber() + " " + b.getDescription() + " — " + status);
                    row.setFont(FONT_SMALL);
                    row.setForeground(color);
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    bountyPanel.add(row);
                    bountyPanel.add(Box.createVerticalStrut(2));
                }
            }

            bountyPanel.revalidate();
            bountyPanel.repaint();
        });
    }

    public void updateDropLog(List<Map<String, Object>> drops)
    {
        SwingUtilities.invokeLater(() -> {
            dropLogPanel.removeAll();
            if (drops == null || drops.isEmpty())
            {
                JLabel none = new JLabel("No drops yet");
                none.setFont(FONT_ITALIC);
                none.setForeground(new Color(100, 100, 100));
                dropLogPanel.add(none);
            }
            else
            {
                int shown = Math.min(20, drops.size());
                for (int i = 0; i < shown; i++)
                {
                    Map<String, Object> d = drops.get(i);
                    String text = d.get("rsn") + " — " + d.get("item") + " [" + d.get("tile") + "] +" +
                        NUM_FMT.format(((Number) d.get("points")).doubleValue()) + " pts";
                    JLabel row = new JLabel(text);
                    row.setFont(FONT_SMALL);
                    row.setForeground(new Color(200, 200, 200));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    dropLogPanel.add(row);
                    dropLogPanel.add(Box.createVerticalStrut(2));
                }
            }
            dropLogPanel.revalidate();
            dropLogPanel.repaint();
        });
    }

    private void rebuildGrid()
    {
        gridPanel.removeAll();
        if (config == null) return;

        int rows = config.getGridRows();
        int cols = config.getGridCols();
        gridPanel.setLayout(new GridLayout(rows, cols, 1, 1));

        // Calculate cell size to fit panel width (~220px for RuneLite side panel)
        int cellSize = Math.max(20, Math.min(40, 210 / Math.max(cols, 1)));
        gridPanel.setMaximumSize(new Dimension(cols * (cellSize + 1), rows * (cellSize + 1)));
        gridPanel.setPreferredSize(new Dimension(cols * (cellSize + 1), rows * (cellSize + 1)));

        // Build grid sorted by row, col
        BingoTile[][] grid = new BingoTile[rows][cols];
        for (BingoTile tile : config.getTiles())
        {
            if (tile.getRow() < rows && tile.getCol() < cols)
            {
                grid[tile.getRow()][tile.getCol()] = tile;
            }
        }

        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                BingoTile tile = grid[r][c];
                JLabel cell = new JLabel(tile != null ? tile.getCode() : "", SwingConstants.CENTER);
                cell.setFont(new Font("Segoe UI", Font.BOLD, cellSize > 30 ? 9 : 7));
                cell.setForeground(Color.WHITE);
                cell.setOpaque(true);
                cell.setBackground(TILE_GRAY);
                cell.setPreferredSize(new Dimension(cellSize, cellSize));
                cell.setBorder(BorderFactory.createLineBorder(new Color(30, 30, 30), 1));

                if (tile != null)
                {
                    cell.setToolTipText(tile.getName());
                    final BingoTile t = tile;
                    cell.addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            showTileDetail(t);
                        }
                    });
                }

                gridPanel.add(cell);
            }
        }

        refreshGridColors();
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void refreshGridColors()
    {
        if (config == null || teamProgress == null) return;

        int cols = config.getGridCols();
        Component[] cells = gridPanel.getComponents();

        for (BingoTile tile : config.getTiles())
        {
            int idx = tile.getRow() * cols + tile.getCol();
            if (idx < 0 || idx >= cells.length) continue;

            double points = teamProgress.getOrDefault(tile.getCode(), 0.0);
            double pct = tile.getMax() > 0 ? (points / tile.getMax()) * 100 : 0;

            Color bg;
            if (pct >= 100) bg = TILE_BLACK;
            else if (pct >= 50) bg = TILE_GREEN;
            else if (pct >= 5) bg = TILE_YELLOW;
            else bg = TILE_GRAY;

            cells[idx].setBackground(bg);
        }
    }

    private void showTileDetail(BingoTile tile)
    {
        selectedTileCode = tile.getCode();
        tileDetailPanel.removeAll();

        JLabel name = new JLabel(tile.getCode() + ": " + tile.getName());
        name.setFont(FONT_BOLD);
        name.setForeground(ACCENT_GOLD);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.add(name);

        String typeLabel = "drop".equals(tile.getType()) ? "Drops" :
            "kc".equals(tile.getType()) ? "Kill Count" : "XP";
        JLabel type = new JLabel("Type: " + typeLabel +
            (tile.getMetric().isEmpty() ? "" : " (" + tile.getMetric() + ")"));
        type.setFont(FONT_SMALL);
        type.setForeground(new Color(170, 170, 170));
        type.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.add(type);

        double points = teamProgress != null ? teamProgress.getOrDefault(tile.getCode(), 0.0) : 0;
        double pct = tile.getMax() > 0 ? (points / tile.getMax()) * 100 : 0;
        JLabel progress = new JLabel("Progress: " + NUM_FMT.format(points) + " / " +
            NUM_FMT.format(tile.getMax()) + " (" + (int) pct + "%)");
        progress.setFont(FONT_SMALL);
        progress.setForeground(new Color(200, 200, 200));
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.add(progress);

        tileDetailPanel.setVisible(true);
        tileDetailPanel.revalidate();
        tileDetailPanel.repaint();
    }

    private JLabel createSectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(ACCENT_GOLD);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/BingoPanel.java
git commit -m "feat: add BingoPanel — dynamic bingo board UI with grid, standings, bounties, drop log"
```

---

## Task 8: DiscordWebhookService — Bingo Embeds

**Files:**
- Modify: `src/main/java/com/droplogger/DiscordWebhookService.java`

- [ ] **Step 1: Add bingo Discord embed methods**

Add these methods before `isValidWebhookUrl()` in `DiscordWebhookService.java`:

```java
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
```

- [ ] **Step 2: Add missing import at top of file**

Add to imports in `DiscordWebhookService.java`:

```java
import java.util.List;
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/droplogger/DiscordWebhookService.java
git commit -m "feat: add bingo Discord embeds — event start/end, drops, bounty alerts, tile completion"
```

---

## Task 9: ClanManagementConfig — Add Bingo URL

**Files:**
- Modify: `src/main/java/com/droplogger/ClanManagementConfig.java`

- [ ] **Step 1: Add bingoApiUrl config item**

Add before the closing brace of the interface, after the `adminApiKey` config item:

```java
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
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/ClanManagementConfig.java
git commit -m "feat: add bingo API URL and key config items"
```

---

## Task 10: ClanPanel — Dynamic Bingo Tab

**Files:**
- Modify: `src/main/java/com/droplogger/ClanPanel.java`

- [ ] **Step 1: Add showBingoTab and hideBingoTab methods**

Add near the `showAdminTab` method (around line 2078):

```java
    private BingoPanel bingoPanel;
    private boolean bingoTabVisible = false;

    public void showBingoTab(BingoPanel panel)
    {
        if (bingoTabVisible) return;
        this.bingoPanel = panel;
        SwingUtilities.invokeLater(() ->
        {
            // Insert before Admin tab if it exists, otherwise at end
            int adminIdx = tabbedPane.indexOfTab("Admin");
            if (adminIdx >= 0)
            {
                tabbedPane.insertTab("Bingo", null, panel, null, adminIdx);
            }
            else
            {
                tabbedPane.addTab("Bingo", panel);
            }
            bingoTabVisible = true;
            revalidate();
            repaint();
        });
    }

    public void hideBingoTab()
    {
        if (!bingoTabVisible) return;
        SwingUtilities.invokeLater(() ->
        {
            int idx = tabbedPane.indexOfTab("Bingo");
            if (idx >= 0) tabbedPane.removeTabAt(idx);
            bingoTabVisible = false;
            bingoPanel = null;
            revalidate();
            repaint();
        });
    }

    public BingoPanel getBingoPanel()
    {
        return bingoPanel;
    }
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/ClanPanel.java
git commit -m "feat: add dynamic Bingo tab to ClanPanel — show/hide based on event state"
```

---

## Task 11: ClanManagementPlugin — Wire Bingo System

**Files:**
- Modify: `src/main/java/com/droplogger/ClanManagementPlugin.java`

This is the integration task that connects everything.

- [ ] **Step 1: Add bingo fields and injection**

Add after the existing `@Inject` blocks (around line 106):

```java
    @Inject
    private BingoService bingoService;
```

Add after the existing field declarations (around line 145):

```java
    // Bingo state
    private BingoPanel bingoPanel;
    private BountyScheduler bountyScheduler;
    private BingoConfig bingoConfig;
    private boolean bingoActive = false;
    private String playerBingoTeam = ""; // team code for logged-in player
```

- [ ] **Step 2: Add bingo initialization in startUp/shutDown**

Find the `startUp` method and add after the existing panel setup and before the refresh task scheduling:

```java
        // Initialize bingo panel (hidden until config says active)
        bingoPanel = new BingoPanel();
```

Find the `shutDown` method and add bingo cleanup:

```java
        if (bountyScheduler != null)
        {
            bountyScheduler.cancel();
            bountyScheduler = null;
        }
        panel.hideBingoTab();
        bingoActive = false;
```

- [ ] **Step 3: Add refreshBingo method**

Add after the `refreshEventLeaderboard` method:

```java
    private void refreshBingo()
    {
        String bingoUrl = config.bingoApiUrl();
        String bingoKey = config.bingoApiKey();
        if (bingoUrl == null || bingoUrl.isEmpty()) {
            if (bingoActive) {
                panel.hideBingoTab();
                bingoActive = false;
                if (bountyScheduler != null) bountyScheduler.cancel();
            }
            return;
        }

        // Configure service if URL changed
        if (!bingoUrl.equals(bingoService.isConfigured() ? bingoUrl : "")) {
            bingoService.configure(bingoUrl, bingoKey,
                config.adminApiKey() != null ? config.adminApiKey() : "");
        }

        try
        {
            // Fetch config (cached for 5 min)
            bingoConfig = bingoService.fetchBingoConfig();

            // Check if event is active based on dates
            boolean shouldBeActive = isEventActive(bingoConfig);

            if (shouldBeActive && !bingoActive)
            {
                // Show bingo tab
                panel.showBingoTab(bingoPanel);
                bingoActive = true;

                // Set up bounty scheduler
                bountyScheduler = new BountyScheduler(executor, bingoService);
                bountyScheduler.setOnHint((bounty, message) -> {
                    sendChatMessage(message);
                    if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
                    {
                        discordService.postBountyHint(fetchedDiscordWebhookUrl, bounty,
                            bingoConfig.getHintMinutesBefore());
                    }
                });
                bountyScheduler.setOnRelease((bounty, message) -> {
                    sendChatMessage(message);
                    if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
                    {
                        discordService.postBountyLive(fetchedDiscordWebhookUrl, bounty);
                    }
                });
                bountyScheduler.schedule(bingoConfig.getBounties(), bingoConfig.getHintMinutesBefore());
            }
            else if (!shouldBeActive && bingoActive)
            {
                panel.hideBingoTab();
                bingoActive = false;
                if (bountyScheduler != null) bountyScheduler.cancel();
            }

            if (!bingoActive) return;

            // Resolve player's team
            String playerName = getLocalPlayerName();
            if (playerName != null)
            {
                playerBingoTeam = bingoConfig.getRoster().getOrDefault(playerName.toLowerCase(), "");
            }

            // Find team name
            String playerTeamName = null;
            for (BingoTeam team : bingoConfig.getTeams())
            {
                if (team.getCode().equals(playerBingoTeam))
                {
                    playerTeamName = team.getName();
                    break;
                }
            }

            bingoPanel.updateConfig(bingoConfig, playerBingoTeam, playerTeamName);

            // Fetch team progress for player's team
            if (!playerBingoTeam.isEmpty())
            {
                Map<String, Double> progress = bingoService.fetchTeamProgress(playerBingoTeam);
                bingoPanel.updateTeamProgress(progress);
            }

            // Fetch standings
            BingoStandings standings = bingoService.fetchAllStandings();
            bingoPanel.updateStandings(standings, playerName, playerBingoTeam);

            // Fetch drops
            List<Map<String, Object>> drops = bingoService.fetchDroplog(playerBingoTeam, 20);
            bingoPanel.updateDropLog(drops);

            // Update bounty display
            String nextBounty = bountyScheduler != null ?
                bountyScheduler.getNextBountyCountdown(bingoConfig.getBounties()) : null;
            bingoPanel.updateBounties(bingoConfig.getBounties(), nextBounty);

            // Update countdown
            bingoPanel.updateCountdown(getBingoCountdown(bingoConfig));

            // Push WOM data for KC/XP tiles
            pushWomBingoProgress();
        }
        catch (Exception e)
        {
            log.debug("Failed to refresh bingo data", e);
        }
    }

    private boolean isEventActive(BingoConfig config)
    {
        try
        {
            java.time.ZoneId est = java.time.ZoneId.of("America/New_York");
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(est);

            if (!config.getStartDate().isEmpty())
            {
                java.time.ZonedDateTime start = java.time.LocalDateTime.parse(config.getStartDate()).atZone(est);
                if (now.isBefore(start)) return false;
            }
            if (!config.getEndDate().isEmpty())
            {
                java.time.ZonedDateTime end = java.time.LocalDateTime.parse(config.getEndDate()).atZone(est);
                if (now.isAfter(end)) return false;
            }
            return true;
        }
        catch (Exception e)
        {
            return true; // If dates can't be parsed, show anyway
        }
    }

    private String getBingoCountdown(BingoConfig config)
    {
        try
        {
            java.time.ZoneId est = java.time.ZoneId.of("America/New_York");
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(est);

            if (!config.getEndDate().isEmpty())
            {
                java.time.ZonedDateTime end = java.time.LocalDateTime.parse(config.getEndDate()).atZone(est);
                java.time.Duration remaining = java.time.Duration.between(now, end);
                if (!remaining.isNegative())
                {
                    long days = remaining.toDays();
                    long hours = remaining.toHours() % 24;
                    long minutes = remaining.toMinutes() % 60;
                    return "Ends in " + days + "d " + hours + "h " + minutes + "m";
                }
            }
        }
        catch (Exception ignored) {}
        return "";
    }

    private void pushWomBingoProgress()
    {
        if (bingoConfig == null || !womService.isConfigured()) return;

        // Only push if we're the alphabetically first online player
        String playerName = getLocalPlayerName();
        if (playerName == null) return;

        // Check if we should be the one pushing (simple: only push if player is on a team)
        if (playerBingoTeam.isEmpty()) return;

        // Find KC/XP tiles and group by metric
        Map<String, List<BingoTile>> metricTiles = new LinkedHashMap<>();
        for (BingoTile tile : bingoConfig.getTiles())
        {
            if (("kc".equals(tile.getType()) || "xp".equals(tile.getType())) && !tile.getMetric().isEmpty())
            {
                metricTiles.computeIfAbsent(tile.getMetric(), k -> new ArrayList<>()).add(tile);
            }
        }

        if (metricTiles.isEmpty()) return;

        for (Map.Entry<String, List<BingoTile>> entry : metricTiles.entrySet())
        {
            String metric = entry.getKey();
            try
            {
                List<WomService.WomEntry> gained = womService.fetchGained(metric, "week");
                // Sum per team
                Map<String, Double> teamGains = new LinkedHashMap<>();
                for (WomService.WomEntry we : gained)
                {
                    String team = bingoConfig.getRoster().getOrDefault(we.username.toLowerCase(), "");
                    if (!team.isEmpty())
                    {
                        teamGains.merge(team, (double) we.gained, Double::sum);
                    }
                }

                // Push to sheet for each team/tile
                for (BingoTile tile : entry.getValue())
                {
                    for (Map.Entry<String, Double> tg : teamGains.entrySet())
                    {
                        bingoService.updateTileProgress(tg.getKey(), tile.getCode(), tg.getValue());
                    }
                }
            }
            catch (Exception e)
            {
                log.debug("Failed to push WOM progress for metric: {}", metric, e);
            }
        }
    }

    private String getLocalPlayerName()
    {
        if (client.getLocalPlayer() != null)
        {
            return client.getLocalPlayer().getName();
        }
        return null;
    }
```

- [ ] **Step 4: Add refreshBingo() call to refreshData()**

In `refreshData()`, add at the end (after `refreshClanActivity()` and `refreshEventLeaderboard()`):

```java
        refreshBingo();
```

- [ ] **Step 5: Add missing imports**

Add to the imports at the top of the file:

```java
import java.util.stream.Collectors;
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/droplogger/ClanManagementPlugin.java
git commit -m "feat: wire bingo system — config refresh, bounty scheduler, WOM push, dynamic tab"
```

---

## Task 12: SWB26 Migration Function

**Files:**
- Modify: `google-apps-script/BingoSetup.gs`

- [ ] **Step 1: Add migrateSWB26() function**

Append to `BingoSetup.gs`:

```javascript
/**
 * Migrate an existing SWB26-format bingo sheet to the new standardized format.
 * Run this in an existing SWB26 sheet — creates new tabs alongside old ones.
 * Non-destructive: does not modify or delete any existing tabs.
 */
function migrateSWB26() {
  var ui = SpreadsheetApp.getUi();
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  // Detect SWB26 structure: look for known team sheets
  var swb26Teams = [
    { code: "WOO", name: "Warriors of Olm" },
    { code: "MB", name: "Master Blasters" },
    { code: "TT", name: "Torta Ticklers" },
    { code: "DN", name: "Doobie Nation" },
    { code: "DRY", name: "Dry Streak" },
    { code: "MCD", name: "McDouble's Thrall Emporium" },
    { code: "BB", name: "Boobie Brigade" },
    { code: "SRY", name: "Shrimpy's Rowdy Yappers" }
  ];

  var foundTeams = [];
  for (var i = 0; i < swb26Teams.length; i++) {
    if (ss.getSheetByName(swb26Teams[i].code)) {
      foundTeams.push(swb26Teams[i]);
    }
  }

  if (foundTeams.length === 0) {
    ui.alert("No SWB26 team sheets found. Are you running this in the correct sheet?");
    return;
  }

  ui.alert("Found " + foundTeams.length + " SWB26 teams. Starting migration...");

  // Create Config tab
  createConfigTab_(ss, 6);
  var configSheet = ss.getSheetByName("Config");
  // Try to read existing Settings for event name and dates
  var oldSettings = ss.getSheetByName("Settings");
  if (oldSettings) {
    var oldData = oldSettings.getRange("A2:B100").getValues();
    for (var i = 0; i < oldData.length; i++) {
      var key = (oldData[i][0] || "").toString().trim();
      var val = (oldData[i][1] || "").toString().trim();
      if (key === "bingoStartDate" || key === "startDate") setConfigValue_wrapper_(configSheet, "startDate", val);
      if (key === "bingoEndDate" || key === "endDate") setConfigValue_wrapper_(configSheet, "endDate", val);
    }
  }

  // Create Teams tab from found teams
  createTeamsTab_(ss);
  var teamsSheet = ss.getSheetByName("Teams");
  teamsSheet.getRange(2, 1, foundTeams.length, 2).setValues(
    foundTeams.map(function(t) { return [t.code, t.name]; })
  );

  // Create Tiles tab from Board Detail or first team sheet
  createTilesTab_(ss, 6);

  // Try to read tile names from a Board Detail sheet
  var boardDetail = ss.getSheetByName("Board Detail");
  if (boardDetail) {
    var bdData = boardDetail.getRange("A2:E100").getValues();
    var tilesSheet = ss.getSheetByName("Tiles");
    var tileRow = 2;
    for (var i = 0; i < bdData.length; i++) {
      var code = (bdData[i][0] || "").toString().trim();
      if (!code) continue;
      var name = (bdData[i][1] || "").toString().trim() || "Tile " + code;
      var threshold = parseFloat(bdData[i][3]) || 100;
      var max = parseFloat(bdData[i][4]) || threshold * 2;

      // Calculate row/col from code (e.g., A1 -> row=0, col=0)
      var colLetter = code.charAt(0);
      var rowNum = parseInt(code.substring(1)) - 1;
      var colNum = colLetter.charCodeAt(0) - 65;

      tilesSheet.getRange(tileRow, 1, 1, 8).setValues([[
        code, name, "drop", "", threshold, max, rowNum, colNum
      ]]);
      tileRow++;
    }
  }

  // Create Roster from existing Roster sheet
  createRosterTab_(ss);
  var oldRoster = ss.getSheetByName("Roster");
  if (oldRoster && oldRoster.getLastRow() > 1) {
    var rosterData = oldRoster.getRange(2, 1, oldRoster.getLastRow() - 1, 2).getValues();
    var newRoster = ss.getSheetByName("Roster");
    // Clear the new roster (keep header) and copy
    if (rosterData.length > 0) {
      newRoster.getRange(2, 1, rosterData.length, 2).setValues(rosterData);
    }
  }

  // Create Whitelist from Item Listing
  createWhitelistTab_(ss);
  var itemListing = ss.getSheetByName("Item Listing");
  if (itemListing) {
    var wlSheet = ss.getSheetByName("Whitelist");
    var ilData = itemListing.getRange(2, 1, Math.max(itemListing.getLastRow() - 1, 1), 3).getValues();
    var wlRow = 2;
    for (var i = 0; i < ilData.length; i++) {
      var item = (ilData[i][0] || "").toString().trim();
      if (!item) continue;
      var tileCode = (ilData[i][1] || "").toString().trim();
      var points = parseFloat(ilData[i][2]) || 10;
      wlSheet.getRange(wlRow, 1, 1, 3).setValues([[item, tileCode, points]]);
      wlRow++;
    }
  }

  // Create Droplog
  createDroplogTab_(ss);

  // Migrate existing drops from old Droplog
  var oldDroplog = ss.getSheetByName("Droplog");
  if (oldDroplog && oldDroplog.getLastRow() > 1) {
    var newDroplog = ss.getSheetByName("Droplog");
    // Old format: Team(A), ?(B), RSN(C), Item(D), Date(E), Points(F), TileCode(G)
    var oldDrops = oldDroplog.getRange(2, 1, Math.min(oldDroplog.getLastRow() - 1, 5000), 7).getValues();
    var newRows = [];
    for (var i = 0; i < oldDrops.length; i++) {
      var rsn = (oldDrops[i][2] || "").toString().trim();
      if (!rsn) continue;
      newRows.push([
        (oldDrops[i][4] || "").toString(), // timestamp/date
        rsn,
        (oldDrops[i][0] || "").toString().trim(), // team
        (oldDrops[i][3] || "").toString().trim(), // item
        (oldDrops[i][6] || "").toString().trim(), // tile code
        parseFloat(oldDrops[i][5]) || 0 // points
      ]);
    }
    if (newRows.length > 0) {
      newDroplog.getRange(2, 1, newRows.length, 6).setValues(newRows);
    }
  }

  // Create Bounties tab (empty — admin fills in for new events)
  createBountiesTab_(ss);

  // Create team progress sheets from existing team sheets
  var tiles = readTiles_from_sheet_(ss);
  for (var i = 0; i < foundTeams.length; i++) {
    var teamSheet = ss.getSheetByName(foundTeams[i].code);
    if (!teamSheet) continue;

    var progressSheet = ensureTeamProgressSheet_(ss, foundTeams[i].code, tiles);
    // Read existing points from team sheet (column A = tile code, column C = points)
    var teamData = teamSheet.getRange(4, 1, Math.min(teamSheet.getLastRow() - 3, 100), 3).getValues();
    for (var j = 0; j < teamData.length; j++) {
      var code = (teamData[j][0] || "").toString().trim();
      var pts = parseFloat(teamData[j][2]) || 0;
      if (!code || pts === 0) continue;

      // Find and update in progress sheet
      var pData = progressSheet.getRange(2, 1, progressSheet.getLastRow() - 1, 3).getValues();
      for (var k = 0; k < pData.length; k++) {
        if ((pData[k][0] || "").toString().trim() === code) {
          progressSheet.getRange(k + 2, 3).setValue(pts);
          break;
        }
      }
    }
  }

  ui.alert("Migration complete!\n\n" +
    "Created tabs: Config, Tiles, Teams, Roster, Whitelist, Droplog, Bounties\n" +
    "Created progress sheets for " + foundTeams.length + " teams.\n\n" +
    "Original tabs are untouched. Deploy as Web App to start using the new API.");
}

/** Helper: read tiles from the Tiles sheet (used during migration). */
function readTiles_from_sheet_(ss) {
  var sheet = ss.getSheetByName("Tiles");
  if (!sheet || sheet.getLastRow() < 2) return [];
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 8).getValues();
  var tiles = [];
  for (var i = 0; i < data.length; i++) {
    var code = (data[i][0] || "").toString().trim();
    if (!code) continue;
    tiles.push({ code: code, name: (data[i][1] || "").toString().trim() });
  }
  return tiles;
}

/** Helper: set a config value by key (for migration). */
function setConfigValue_wrapper_(configSheet, key, value) {
  var data = configSheet.getRange("A2:A100").getValues();
  for (var i = 0; i < data.length; i++) {
    if ((data[i][0] || "").toString().trim() === key) {
      configSheet.getRange(i + 2, 2).setValue(value);
      return;
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add google-apps-script/BingoSetup.gs
git commit -m "feat: add migrateSWB26() — non-destructive migration from SWB26 format to new bingo structure"
```

---

## Task 13: Build and Full Verification

**Files:** All files from Tasks 1-12

- [ ] **Step 1: Clean build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all new files exist**

Run: `ls src/main/java/com/droplogger/Bingo*.java src/main/java/com/droplogger/BountyScheduler.java google-apps-script/BingoSetup.gs google-apps-script/BingoAPI.gs`

Expected: All 8 files listed

- [ ] **Step 3: Verify no compilation warnings related to bingo**

Run: `./gradlew compileJava 2>&1 | grep -i error`
Expected: No output (no errors)

- [ ] **Step 4: Final commit if any uncommitted changes remain**

```bash
git status
# If clean, skip. If changes, commit appropriately.
```
