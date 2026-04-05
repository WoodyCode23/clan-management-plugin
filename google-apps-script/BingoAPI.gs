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

function readBounties_(includeDescriptions) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Bounties");
  if (!sheet || sheet.getLastRow() < 2) return [];
  // New column order: Number, Release Time, Points, Winner, Hint Fired, Release Fired, Description
  var data = sheet.getRange(2, 1, sheet.getLastRow() - 1, 7).getValues();
  var hostDescriptions = readHostBountyDescriptions_();
  var bounties = [];
  for (var i = 0; i < data.length; i++) {
    var num = parseInt(data[i][0]);
    if (!num) continue;
    var releaseFired = (data[i][5] || "").toString().toUpperCase() === "TRUE";

    var description;
    if (releaseFired) {
      description = (data[i][6] || "").toString().trim() || hostDescriptions[num] || "";
    } else if (includeDescriptions) {
      description = hostDescriptions[num] || "";
    } else {
      description = "???";
    }

    bounties.push({
      number: num,
      description: description,
      releaseTime: (data[i][1] || "").toString().trim(),
      points: parseFloat(data[i][2]) || 0,
      winner: (data[i][3] || "").toString().trim(),
      hintFired: (data[i][4] || "").toString().toUpperCase() === "TRUE",
      releaseFired: releaseFired
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

function readHostBountyDescriptions_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Host");
  if (!sheet || sheet.getLastRow() < 6) return {};
  var data = sheet.getRange(6, 1, sheet.getLastRow() - 5, 2).getValues();
  var descriptions = {};
  for (var i = 0; i < data.length; i++) {
    var num = parseInt(data[i][0]);
    if (!num) continue;
    descriptions[num] = (data[i][1] || "").toString().trim();
  }
  return descriptions;
}

function getHostConfigValue_(key) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Host");
  if (!sheet) return null;
  var data = sheet.getRange("A1:B4").getValues();
  for (var i = 0; i < data.length; i++) {
    if ((data[i][0] || "").toString().trim() === key) {
      return (data[i][1] || "").toString().trim();
    }
  }
  return null;
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
    var bounties = readBounties_(false);
    var roster = readRoster_();

    // Ensure team progress sheets exist
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    for (var i = 0; i < teams.length; i++) {
      ensureTeamProgressSheet_(ss, teams[i].code, tiles);
    }

    // hintMinutesBefore: try Host tab first, fall back to Config tab
    var hintMin = getHostConfigValue_("Hint Minutes Before");
    if (!hintMin) hintMin = getConfigValue_("hintMinutesBefore");

    return resp_({
      gridRows: parseInt(getConfigValue_("gridRows")) || 5,
      gridCols: parseInt(getConfigValue_("gridCols")) || 5,
      eventName: getConfigValue_("eventName") || "Bingo Event",
      startDate: getConfigValue_("startDate") || "",
      endDate: getConfigValue_("endDate") || "",
      hintMinutesBefore: parseInt(hintMin) || 60,
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
    return resp_({ bounties: readBounties_(false) });
  }

  return resp_({ status: "error", message: "Unknown action: " + action });
}

function computeAllStandings_() {
  var teams = readTeams_();
  var bounties = readBounties_(false);
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

    // ── Player-tier actions ──

    if (action === "submitDrop") return resp_(handleSubmitDrop_(data));
    if (action === "updateTileProgress") return resp_(handleUpdateTileProgress_(data));
    if (action === "markBountyFired") return resp_(handleMarkBountyFired_(data));

    // ── Host-tier actions ──

    if (action.indexOf("host") === 0) {
      var storedHostKey = PropertiesService.getScriptProperties().getProperty("hostKey") || "";
      if (!storedHostKey || (data.hostKey || "") !== storedHostKey) {
        return resp_({ status: "error", message: "Invalid host key" });
      }
    }

    if (action === "hostUpdateTile") return resp_(handleAdminUpdateTile_(data));
    if (action === "hostAddTile") return resp_(handleAdminAddTile_(data));
    if (action === "hostRemoveTile") return resp_(handleAdminRemoveTile_(data));
    if (action === "hostUpdateTeam") return resp_(handleAdminUpdateTeam_(data));
    if (action === "hostRemoveTeam") return resp_(handleAdminRemoveTeam_(data));
    if (action === "hostUpdateBounty") return resp_(handleAdminUpdateBounty_(data));
    if (action === "hostAddBounty") return resp_(handleHostAddBounty_(data));
    if (action === "hostRemoveBounty") return resp_(handleAdminRemoveBounty_(data));
    if (action === "hostUpdateConfig") return resp_(handleAdminUpdateConfig_(data));
    if (action === "hostUpdateWhitelist") return resp_(handleAdminUpdateWhitelist_(data));

    // ── Admin-tier actions ──

    if (action.indexOf("admin") === 0) {
      var adminKey = getConfigValue_("adminKey") || "changeme-admin";
      if ((data.adminKey || "") !== adminKey) {
        return resp_({ status: "error", message: "Invalid admin key" });
      }
    }

    if (action === "adminManualProgress") return resp_(handleAdminManualProgress_(data));
    if (action === "adminUpdateRoster") return resp_(handleAdminUpdateRoster_(data));
    if (action === "adminRemoveRoster") return resp_(handleAdminRemoveRoster_(data));

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

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Bounties");
  if (!sheet || sheet.getLastRow() < 2) {
    return { status: "error", message: "Bounties sheet not found" };
  }

  // New column order: Number(1), ReleaseTime(2), Points(3), Winner(4), HintFired(5), ReleaseFired(6), Description(7)
  var colIndex = field === "hintFired" ? 5 : 6;
  var bountyData = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
  for (var i = 0; i < bountyData.length; i++) {
    if (parseInt(bountyData[i][0]) === number) {
      sheet.getRange(i + 2, colIndex).setValue("TRUE");

      // On release: copy description from Host tab to public Bounties tab
      if (field === "releaseFired") {
        var hostDescriptions = readHostBountyDescriptions_();
        var desc = hostDescriptions[number] || "";
        if (desc) {
          sheet.getRange(i + 2, 7).setValue(desc);
        }
      }

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

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Bounties");
  if (!sheet) return { status: "error", message: "Bounties sheet not found" };

  // New column order: Number(1), ReleaseTime(2), Points(3), Winner(4), HintFired(5), ReleaseFired(6), Description(7)
  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 7).getValues();
  for (var i = 0; i < rows.length; i++) {
    if (parseInt(rows[i][0]) === number) {
      var r = i + 2;
      if (data.hasOwnProperty("releaseTime")) sheet.getRange(r, 2).setValue(data.releaseTime);
      if (data.hasOwnProperty("points")) sheet.getRange(r, 3).setValue(data.points);
      if (data.hasOwnProperty("winner")) sheet.getRange(r, 4).setValue(data.winner);

      // If description is provided, update in Host tab (not public tab)
      if (data.hasOwnProperty("description")) {
        var hostSheet = ss.getSheetByName("Host");
        if (hostSheet) {
          var hostData = hostSheet.getRange(6, 1, Math.max(hostSheet.getLastRow() - 5, 1), 2).getValues();
          var found = false;
          for (var j = 0; j < hostData.length; j++) {
            if (parseInt(hostData[j][0]) === number) {
              hostSheet.getRange(j + 6, 2).setValue(data.description);
              found = true;
              break;
            }
          }
          if (!found) {
            var newRow = hostSheet.getLastRow() + 1;
            hostSheet.getRange(newRow, 1, 1, 2).setValues([[number, data.description]]);
          }
        }
      }

      return { status: "ok", message: "Bounty #" + number + " updated" };
    }
  }
  return { status: "error", message: "Bounty #" + number + " not found" };
}

function handleHostAddBounty_(data) {
  var number = parseInt(data.number);
  if (!number) return { status: "error", message: "Missing bounty number" };

  var ss = SpreadsheetApp.getActiveSpreadsheet();

  // Add to public Bounties tab (no description)
  var sheet = ss.getSheetByName("Bounties");
  if (!sheet) return { status: "error", message: "Bounties sheet not found" };

  var newRow = sheet.getLastRow() + 1;
  // New column order: Number, Release Time, Points, Winner, Hint Fired, Release Fired, Description
  sheet.getRange(newRow, 1, 1, 7).setValues([[
    number,
    data.releaseTime || "",
    data.points || 0,
    "",
    "FALSE",
    "FALSE",
    ""
  ]]);

  // Store description in Host tab
  var description = (data.description || "").trim();
  if (description) {
    var hostSheet = ss.getSheetByName("Host");
    if (hostSheet) {
      var hostRow = hostSheet.getLastRow() + 1;
      hostSheet.getRange(hostRow, 1, 1, 2).setValues([[number, description]]);
    }
  }

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
