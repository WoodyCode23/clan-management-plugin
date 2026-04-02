/**
 * SWB26 Board API — Google Apps Script endpoint
 *
 * Returns bingo board state from the SWB26 Leadsheet.
 * Supports team-specific data via the ?team= parameter.
 *
 * Endpoints:
 *   ?action=ping              → { status: "ok" }
 *   ?action=board             → Global board (all teams' standings, bounty schedule)
 *   ?action=board&team=WOO   → Team-specific board (WOO's tile completion, drops, points)
 *
 * Deploy as Web App (Execute as: Me, Access: Anyone).
 *
 * NOTE: Cell references are mapped to the SWB26 Leadsheet layout.
 * Adjust references if your sheet structure differs.
 */

// ── API Keys ── Stored in Script Properties for easy rotation.
// Fallback defaults used only on first deploy before properties are set.
var DEFAULT_API_KEY = "changeme";
var DEFAULT_ADMIN_KEY = "changeme-admin";

function getApiKeyFromProps() {
  return PropertiesService.getScriptProperties().getProperty("API_KEY") || DEFAULT_API_KEY;
}
function getAdminKeyFromProps() {
  return PropertiesService.getScriptProperties().getProperty("ADMIN_API_KEY") || DEFAULT_ADMIN_KEY;
}

var TEAM_CODES = ["WOO","MB","TT","DN","DRY","MCD","BB","SRY"];
var TEAM_NAMES = {
  "WOO": "Warriors of Olm",
  "MB": "Master Blasters",
  "TT": "Torta Ticklers",
  "DN": "Doobie Nation",
  "DRY": "Dry Streak",
  "MCD": "McDouble's Thrall Emporium",
  "BB": "Boobie Brigade",
  "SRY": "Shrimpy's Rowdy Yappers"
};

// Tile codes in the order they appear on each team sheet (rows 4-39)
var TILE_CODES = [
  "A1","A2","A3","A4","A5","A6",
  "B1","B2","B3","B4","B5","B6",
  "C1","C2","C3","C4","C5","C6",
  "D1","D2","D3","D4","D5","D6",
  "E1","E2","E3","E4","E5","E6",
  "F1","F2","F3","F4","F5","F6"
];

var TILE_NAMES = {
  "A1":"Runecrafting + GOTR","A2":"Yama","A3":"TDs + Demonics","A4":"Cerberus + Sire","A5":"Chambers of Xeric","A6":"Thieving + PP",
  "B1":"Nightmare","B2":"Emote Enhancers","B3":"Good and Evil","B4":"The Gauntlet","B5":"Wildy Boss Trio","B6":"Shellbane Pet + Jar",
  "C1":"Draconic Boats","C2":"Araxxor","C3":"Doom of Mokhaiotl","C4":"Theatre of Blood","C5":"Law and War","C6":"Barrows + Moons",
  "D1":"Huey + Amoxi","D2":"Royal Tools","D3":"Master + Elite Clues","D4":"Nex","D5":"Leviathan + Whisperer","D6":"Wildy Shopping List",
  "E1":"Revenant Weapons","E2":"Duke + Vardorvis","E3":"Zulrah","E4":"Vorkath","E5":"Foot Enthusiast","E6":"Corporeal Beast",
  "F1":"Fishing + Tempoross","F2":"Tombs of Amascut","F3":"GG's + Hydra","F4":"Golden Tench","F5":"Cape Collector","F6":"Hunter + Rumors"
};

function doGet(e) {
  var key = (e.parameter && e.parameter.key) ? e.parameter.key : "";
  if (key !== getApiKeyFromProps()) {
    return jsonResponse({ status: "error", message: "Invalid API key" });
  }

  var action = (e.parameter && e.parameter.action) ? e.parameter.action : "board";
  var team = (e.parameter && e.parameter.team) ? e.parameter.team.toUpperCase() : "";

  if (action === "ping") {
    return jsonResponse({ status: "ok" });
  }

  if (action === "getConfig") {
    return jsonResponse(getPluginConfig());
  }

  if (action === "validDrops") {
    return jsonResponse(getValidDrops());
  }

  if (action === "findTeam") {
    var rsn = (e.parameter && e.parameter.rsn) ? e.parameter.rsn : "";
    if (!rsn) {
      return jsonResponse({ status: "error", message: "Missing rsn parameter" });
    }
    var foundTeam = findTeamByRsn(rsn);
    return jsonResponse({ rsn: rsn, team: foundTeam });
  }

  if (action === "board") {
    try {
      var data = {
        tiles: readTiles(team),
        teams: readTeamStandings(),
        bountyResults: readBountyResults(),
        teamDrops: team ? readTeamDrops(team) : [],
        teamTotalPoints: team ? readTeamTotalPoints(team) : 0,
        announcements: readAnnouncements()
      };
      return jsonResponse(data);
    } catch (err) {
      return jsonResponse({ status: "error", message: "Board read error: " + err.toString(),
        tiles: [], teams: [], bountyResults: [], teamDrops: [], teamTotalPoints: 0, announcements: [] });
    }
  }

  // ── Admin GET actions (require adminKey) ──
  var adminKey = (e.parameter && e.parameter.adminKey) ? e.parameter.adminKey : "";

  if (action === "adminPing") {
    if (adminKey !== getAdminKeyFromProps()) {
      return jsonResponse({ status: "error", message: "Invalid admin key" });
    }
    return jsonResponse({ status: "ok", admin: true });
  }

  if (action === "whitelistAll") {
    if (adminKey !== getAdminKeyFromProps()) {
      return jsonResponse({ status: "error", message: "Invalid admin key" });
    }
    return jsonResponse(getValidDrops());
  }

  if (action === "allRosters") {
    if (adminKey !== getAdminKeyFromProps()) {
      return jsonResponse({ status: "error", message: "Invalid admin key" });
    }
    return jsonResponse({ rosters: getAllRosters() });
  }

  if (action === "adminGetSettings") {
    if (adminKey !== getAdminKeyFromProps()) {
      return jsonResponse({ status: "error", message: "Invalid admin key" });
    }
    return jsonResponse(adminGetSettings());
  }

  if (action === "adminTeamDrops") {
    if (adminKey !== getAdminKeyFromProps()) {
      return jsonResponse({ status: "error", message: "Invalid admin key" });
    }
    var teamParam = (e.parameter && e.parameter.team) ? e.parameter.team.toUpperCase() : "";
    if (!teamParam || TEAM_CODES.indexOf(teamParam) === -1) {
      return jsonResponse({ status: "error", message: "Invalid or missing team parameter" });
    }
    return jsonResponse({ drops: readTeamDrops(teamParam) });
  }

  return jsonResponse({ status: "error", message: "Unknown action: " + action });
}

// ── POST handler ──
function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);

    if (payload.key !== getApiKeyFromProps()) {
      return jsonResponse({ status: "error", message: "Invalid API key" });
    }

    var action = payload.action || "";

    // ── Player-facing actions (no admin key required) ──
    if (action === "submitDrop") {
      return jsonResponse(submitDrop(payload));
    }

    // ── Admin actions (require admin key) ──
    if (payload.adminKey !== getAdminKeyFromProps()) {
      return jsonResponse({ status: "error", message: "Invalid admin key" });
    }

    if (action === "adminAddWhitelistItem") {
      return jsonResponse(adminAddWhitelistItem(payload));
    }
    if (action === "adminRemoveWhitelistItem") {
      return jsonResponse(adminRemoveWhitelistItem(payload));
    }
    if (action === "adminSubmitDrop") {
      return jsonResponse(adminSubmitDrop(payload));
    }
    if (action === "adminSetBountyWinner") {
      return jsonResponse(adminSetBountyWinner(payload));
    }
    if (action === "adminOverrideTilePoints") {
      return jsonResponse(adminOverrideTilePoints(payload));
    }
    if (action === "adminAssignPlayer") {
      return jsonResponse(adminAssignPlayer(payload));
    }
    if (action === "adminSaveSettings") {
      return jsonResponse(adminSaveSettings(payload));
    }
    if (action === "adminRotateApiKey") {
      return jsonResponse(adminRotateApiKey(payload));
    }
    if (action === "adminRemoveDrop") {
      return jsonResponse(adminRemoveDrop(payload));
    }

    return jsonResponse({ status: "error", message: "Unknown admin action: " + action });
  } catch (err) {
    return jsonResponse({ status: "error", message: err.toString() });
  }
}

/**
 * Read tile completion data.
 * If a team code is provided, returns that team's completion % per tile.
 * Otherwise returns global/average completion.
 *
 * Each team sheet has tile codes in column A (rows 4-39) and current points in column C.
 * Bingo thresholds come from the Board Detail sheet column D.
 */
function readTiles(teamCode) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var tiles = [];

  // Read bingo thresholds from Board Detail
  var detailSheet = ss.getSheetByName("Board Detail");
  var thresholds = {};
  if (detailSheet) {
    var detailData = detailSheet.getRange("A2:E38").getValues();
    for (var i = 0; i < detailData.length; i++) {
      var code = detailData[i][0];
      if (code && code !== "X1") {
        thresholds[code] = {
          bingo: parseFloat(detailData[i][3]) || 0,
          max: parseFloat(detailData[i][4]) || 0
        };
      }
    }
  }

  if (teamCode && TEAM_CODES.indexOf(teamCode) !== -1) {
    // Team-specific: read from team sheet
    var teamSheet = ss.getSheetByName(teamCode);
    if (!teamSheet) return tiles;

    // Column A = tile code, Column C = current points (rows 4-40, includes F6 + X1)
    var tileData = teamSheet.getRange("A4:C41").getValues();

    for (var i = 0; i < tileData.length; i++) {
      var code = tileData[i][0];
      if (!code || !TILE_NAMES[code]) continue;

      var points = parseFloat(tileData[i][2]) || 0;
      var bingoThreshold = thresholds[code] ? thresholds[code].bingo : 0;
      var maxThreshold = thresholds[code] ? thresholds[code].max : 0;
      var completion = bingoThreshold > 0 ? (points / bingoThreshold) * 100 : 0;
      // Cap at max threshold percentage
      if (maxThreshold > 0 && points >= maxThreshold) {
        completion = 100;
      }

      // Convert tile code to row/col (A=0, B=1... and 1-6 → 0-5)
      var letter = code.charAt(0);
      var number = parseInt(code.charAt(1));
      var col = letter.charCodeAt(0) - 65; // A=0, B=1, ..., F=5
      var row = number - 1;

      tiles.push({
        row: row,
        col: col,
        task: TILE_NAMES[code],
        completion: Math.round(completion * 100) / 100,
        tileCode: code,
        points: points,
        bingoThreshold: bingoThreshold,
        maxThreshold: maxThreshold
      });
    }
  } else {
    // No team selected: show average completion across all teams
    var allTeamPoints = {};
    for (var t = 0; t < TEAM_CODES.length; t++) {
      var teamSheet = ss.getSheetByName(TEAM_CODES[t]);
      if (!teamSheet) continue;
      var tileData = teamSheet.getRange("A4:C41").getValues();
      for (var i = 0; i < tileData.length; i++) {
        var code = tileData[i][0];
        if (!code || !TILE_NAMES[code]) continue;
        if (!allTeamPoints[code]) allTeamPoints[code] = [];
        allTeamPoints[code].push(parseFloat(tileData[i][2]) || 0);
      }
    }

    for (var code in allTeamPoints) {
      var avgPoints = allTeamPoints[code].reduce(function(a, b) { return a + b; }, 0) / allTeamPoints[code].length;
      var bingoThreshold = thresholds[code] ? thresholds[code].bingo : 0;
      var completion = bingoThreshold > 0 ? (avgPoints / bingoThreshold) * 100 : 0;

      var letter = code.charAt(0);
      var number = parseInt(code.charAt(1));
      var col = letter.charCodeAt(0) - 65;
      var row = number - 1;

      tiles.push({
        row: row,
        col: col,
        task: TILE_NAMES[code],
        completion: Math.round(completion * 100) / 100,
        tileCode: code,
        points: Math.round(avgPoints * 10) / 10,
        bingoThreshold: bingoThreshold
      });
    }
  }

  return tiles;
}

/**
 * Read team standings from the Board Summary sheet.
 * Falls back to computing from individual team sheets if Board Summary isn't available.
 */
function readTeamStandings() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var teams = [];

  // Try reading each team's total from their sheet
  var teamTotals = [];
  for (var i = 0; i < TEAM_CODES.length; i++) {
    var code = TEAM_CODES[i];
    var teamSheet = ss.getSheetByName(code);
    var totalPoints = 0;

    if (teamSheet) {
      // Total is in cell C41 (row after all 36 tiles + X1)
      // Scan column B for "TOTAL TEAM POINTS" to find it dynamically
      var bCol = teamSheet.getRange("B1:B50").getValues();
      for (var r = 0; r < bCol.length; r++) {
        if (bCol[r][0] && bCol[r][0].toString().indexOf("TOTAL") !== -1) {
          totalPoints = parseFloat(teamSheet.getRange("C" + (r + 1)).getValue()) || 0;
          break;
        }
      }
    }

    teamTotals.push({
      code: code,
      name: TEAM_NAMES[code] || code,
      points: totalPoints
    });
  }

  // Sort by points descending to compute rank
  teamTotals.sort(function(a, b) { return b.points - a.points; });

  for (var i = 0; i < teamTotals.length; i++) {
    teams.push({
      code: teamTotals[i].code,
      name: teamTotals[i].name,
      points: teamTotals[i].points,
      rank: i + 1
    });
  }

  return teams;
}

/**
 * Read the team's drop ledger from the Droplog sheet.
 * Filters to only the specified team's drops.
 */
function readTeamDrops(teamCode) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Droplog");
  if (!sheet) return [];

  var data = sheet.getDataRange().getValues();
  var drops = [];

  // Skip header row (row 1)
  for (var i = 1; i < data.length; i++) {
    var rowTeam = (data[i][0] || "").toString().toUpperCase();
    if (rowTeam !== teamCode) continue;

    var rsn = (data[i][2] || "").toString();
    var dropName = (data[i][3] || "").toString();
    var date = (data[i][4] || "").toString();
    var points = parseFloat(data[i][5]) || 0;
    var tileCode = (data[i][6] || "").toString();
    var tileName = (data[i][7] || "").toString();

    // Skip empty rows, header rows, and instruction text
    if (!dropName || !tileCode || dropName === "Drop Name" || dropName.indexOf("Insert data") !== -1) continue;

    drops.push({
      rsn: rsn,
      dropName: dropName,
      date: date,
      points: points,
      tileCode: tileCode,
      tileName: tileName
    });
  }

  return drops;
}

/**
 * Read a team's total points from their sheet.
 */
function readTeamTotalPoints(teamCode) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var teamSheet = ss.getSheetByName(teamCode);
  if (!teamSheet) return 0;

  // Scan for TOTAL row
  var bCol = teamSheet.getRange("B1:B50").getValues();
  for (var r = 0; r < bCol.length; r++) {
    if (bCol[r][0] && bCol[r][0].toString().indexOf("TOTAL") !== -1) {
      return parseFloat(teamSheet.getRange("C" + (r + 1)).getValue()) || 0;
    }
  }
  return 0;
}

/**
 * Read bounty results from Board Summary bounty tracking section.
 */
function readBountyResults() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Board Summary");
  if (!sheet) return [];

  // Bounty results are in the bounty tracking section
  // This is a simplified read — adjust cell references to match your sheet
  var results = [];
  for (var i = 1; i <= 9; i++) {
    results.push({
      bountyNumber: i,
      description: "",
      winner: ""
    });
  }
  return results;
}

/**
 * Find which team a player belongs to.
 * 1. First checks the "Roster" tab (admin-managed pre-assignment).
 *    Layout: Column A = RSN, Column B = Team Code
 * 2. Falls back to scanning the Droplog if not found in Roster.
 *
 * Returns the team code string, or empty string if not found.
 */
function findTeamByRsn(rsn) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var rsnLower = rsn.toLowerCase().replace(/[\s_-]/g, " ");

  // 1. Check Roster tab first (pre-assigned teams)
  var rosterSheet = ss.getSheetByName("Roster");
  if (rosterSheet) {
    var lastRow = rosterSheet.getLastRow();
    if (lastRow >= 2) {
      var rosterData = rosterSheet.getRange(2, 1, lastRow - 1, 2).getValues();
      for (var i = 0; i < rosterData.length; i++) {
        var rosterRsn = (rosterData[i][0] || "").toString().toLowerCase().replace(/[\s_-]/g, " ");
        if (rosterRsn === rsnLower) {
          var teamCode = (rosterData[i][1] || "").toString().toUpperCase();
          if (TEAM_CODES.indexOf(teamCode) !== -1) {
            return teamCode;
          }
        }
      }
    }
  }

  // 2. Fall back to Droplog
  var sheet = ss.getSheetByName("Droplog");
  if (!sheet) return "";

  var data = sheet.getDataRange().getValues();
  for (var i = 1; i < data.length; i++) {
    var rowRsn = (data[i][2] || "").toString().toLowerCase().replace(/[\s_-]/g, " ");
    if (rowRsn === rsnLower) {
      var teamCode = (data[i][0] || "").toString().toUpperCase();
      if (TEAM_CODES.indexOf(teamCode) !== -1) {
        return teamCode;
      }
    }
  }
  return "";
}

/**
 * Read valid drop names from the "Item Listing" tab.
 * Dynamically finds the header row by scanning column D for "DROP",
 * then reads all data rows below it.
 */
function getValidDrops() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  // Try common tab name variants
  var sheet = ss.getSheetByName("Item Listing");
  if (!sheet) sheet = ss.getSheetByName("Item listing");
  if (!sheet) sheet = ss.getSheetByName("item listing");
  if (!sheet) {
    // List all sheet names to help debug
    var names = ss.getSheets().map(function(s) { return s.getName(); });
    return { drops: [], message: "No 'Item Listing' tab found. Sheets: " + names.join(", ") };
  }

  var lastRow = sheet.getLastRow();
  var lastCol = Math.min(sheet.getLastColumn(), 10);
  if (lastRow < 2 || lastCol < 4) {
    return { drops: [], message: "Item Listing has insufficient data. lastRow=" + lastRow + " lastCol=" + lastCol };
  }

  // Scan for the header row containing "DROP" in column D (or any column)
  var headerRow = -1;
  var dropCol = -1;
  var tileCodeCol = -1;
  var scanRange = sheet.getRange(1, 1, Math.min(lastRow, 30), lastCol).getValues();

  for (var r = 0; r < scanRange.length; r++) {
    for (var c = 0; c < scanRange[r].length; c++) {
      var val = (scanRange[r][c] || "").toString().trim().toUpperCase();
      if (val === "DROP") {
        headerRow = r + 1; // 1-based
        dropCol = c;       // 0-based index in data array
      }
      if (val === "TILE CODE") {
        tileCodeCol = c;
      }
    }
    if (headerRow > 0) break;
  }

  if (headerRow < 0 || dropCol < 0) {
    // Return first few cells for debugging
    var sample = [];
    for (var r = 0; r < Math.min(scanRange.length, 5); r++) {
      sample.push("Row " + (r+1) + ": " + scanRange[r].slice(0, 6).join(" | "));
    }
    return { drops: [], message: "Could not find 'DROP' header column. Sample: " + sample.join("; ") };
  }

  var dataStartRow = headerRow + 1;
  if (dataStartRow > lastRow) {
    return { drops: [], message: "No data rows after header at row " + headerRow };
  }

  var data = sheet.getRange(dataStartRow, 1, lastRow - dataStartRow + 1, lastCol).getValues();
  var drops = [];
  var seen = {};

  for (var i = 0; i < data.length; i++) {
    var itemName = (data[i][dropCol] || "").toString().trim();
    if (!itemName || seen[itemName.toLowerCase()]) continue;
    var tileCode = tileCodeCol >= 0 ? (data[i][tileCodeCol] || "").toString().trim() : "";
    drops.push({ item: itemName, tile: tileCode });
    seen[itemName.toLowerCase()] = true;
  }

  return { drops: drops, headerRow: headerRow, dropCol: dropCol + 1, tileCodeCol: (tileCodeCol >= 0 ? tileCodeCol + 1 : -1) };
}

// ══════════════════════════════════════════
// Player drop submission
// ══════════════════════════════════════════

/**
 * Submit a bingo drop from the plugin.
 * Only writes RSN, drop name, and date into the correct team block on the Droplog.
 * The sheet's own formulas handle team assignment, points, tile code, stipulations, etc.
 *
 * The Droplog is pre-allocated: 999 rows per team starting at row 12.
 * Column A is an ARRAYFORMULA assigning teams. Only C/D/E are manual input.
 *
 * Payload: { key, action:"submitDrop", player, item, timestamp, team }
 */
function submitDrop(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(15000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Droplog");
    if (!sheet) {
      lock.releaseLock();
      return { status: "error", message: "Droplog sheet not found" };
    }

    var rsn = (payload.player || "").trim();
    var item = (payload.item || "").trim();
    var team = (payload.team || "").toUpperCase();
    if (!rsn || !item) {
      lock.releaseLock();
      return { status: "error", message: "Missing player or item" };
    }

    // Format date as MM/DD
    var date = payload.timestamp || "";
    if (date) {
      var parts = date.split(/[-\s]/);
      if (parts.length >= 3) {
        date = parts[1] + "/" + parts[2];
      }
    }
    if (!date) {
      var now = new Date();
      date = ("0" + (now.getMonth() + 1)).slice(-2) + "/" + ("0" + now.getDate()).slice(-2);
    }

    // Each team has 999 pre-allocated rows starting at row 12
    var DATA_START = 12;
    var BLOCK_SIZE = 999;
    var teamIndex = TEAM_CODES.indexOf(team);

    if (teamIndex === -1) {
      lock.releaseLock();
      return { status: "error", message: "Unknown team: " + team + ". Player may not be assigned." };
    }

    var blockStart = DATA_START + (teamIndex * BLOCK_SIZE);
    var blockEnd = blockStart + BLOCK_SIZE - 1;

    // Read column C (RSN) for this team's block to find first empty row
    var rsnRange = sheet.getRange(blockStart, 3, BLOCK_SIZE, 1).getValues();
    var emptyRow = -1;
    for (var i = 0; i < rsnRange.length; i++) {
      if (!rsnRange[i][0] || rsnRange[i][0].toString().trim() === "") {
        emptyRow = blockStart + i;
        break;
      }
    }

    if (emptyRow === -1) {
      lock.releaseLock();
      return { status: "error", message: "No empty rows left for team " + team + ". Contact an admin." };
    }

    // Write only C (RSN), D (Drop Name), E (Date) — formulas handle the rest
    sheet.getRange(emptyRow, 3, 1, 3).setValues([[rsn, item, date]]);

    lock.releaseLock();
    return {
      status: "ok",
      message: "Drop logged: " + item + " for " + rsn + " (" + team + " row " + emptyRow + ")"
    };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

// ══════════════════════════════════════════
// Admin handler functions
// ══════════════════════════════════════════

/**
 * Get all team rosters aggregated from the Droplog.
 */
function getAllRosters() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Droplog");
  if (!sheet) return {};

  var data = sheet.getDataRange().getValues();
  var rosters = {};

  for (var i = 0; i < TEAM_CODES.length; i++) {
    rosters[TEAM_CODES[i]] = {};
  }

  for (var i = 1; i < data.length; i++) {
    var team = (data[i][0] || "").toString().toUpperCase();
    if (TEAM_CODES.indexOf(team) === -1) continue;

    var rsn = (data[i][2] || "").toString().trim();
    var points = parseFloat(data[i][5]) || 0;
    var dropName = (data[i][3] || "").toString();

    if (!rsn || !dropName || dropName === "Drop Name" || dropName.indexOf("Insert data") !== -1) continue;

    if (!rosters[team][rsn]) {
      rosters[team][rsn] = { rsn: rsn, dropCount: 0, totalPoints: 0 };
    }
    rosters[team][rsn].dropCount++;
    rosters[team][rsn].totalPoints += points;
  }

  // Convert to arrays sorted by points
  var result = {};
  for (var team in rosters) {
    var players = [];
    for (var rsn in rosters[team]) {
      players.push(rosters[team][rsn]);
    }
    players.sort(function(a, b) { return b.totalPoints - a.totalPoints; });
    result[team] = players;
  }

  return result;
}

/**
 * Add an item to the Item Listing table (tbl_Listing).
 * Table starts at row 22 (headers) on "Item Listing" tab.
 * Columns: A=#, B=TILE CODE, C=TILE NAME, D=DROP, E=PTS, F=STIP CODE, G=STIPULATION
 */
function adminAddWhitelistItem(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Item Listing");
    if (!sheet) {
      lock.releaseLock();
      return { status: "error", message: "Item Listing tab not found" };
    }

    var lastRow = sheet.getLastRow();
    var nextRow = lastRow + 1;
    var tileName = TILE_NAMES[payload.tileCode] || "";
    // Write: #(auto), TILE CODE, TILE NAME, DROP, PTS
    sheet.getRange(nextRow, 2, 1, 4).setValues([[
      payload.tileCode || "",
      tileName,
      payload.item || "",
      payload.points || 0
    ]]);

    lock.releaseLock();
    return { status: "ok", message: "Added " + payload.item + " to Item Listing" };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

/**
 * Remove an item from the Item Listing table (case-insensitive match on DROP column D).
 */
function adminRemoveWhitelistItem(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Item Listing");
    if (!sheet) {
      lock.releaseLock();
      return { status: "error", message: "Item Listing tab not found" };
    }

    var itemLower = (payload.item || "").toLowerCase().trim();
    var lastRow = sheet.getLastRow();
    // Column D = DROP, data starts at row 23
    var data = sheet.getRange(23, 4, lastRow - 22, 1).getValues();

    for (var i = data.length - 1; i >= 0; i--) {
      if ((data[i][0] || "").toString().toLowerCase().trim() === itemLower) {
        sheet.deleteRow(23 + i);
        lock.releaseLock();
        return { status: "ok", message: "Removed " + payload.item + " from Item Listing" };
      }
    }

    lock.releaseLock();
    return { status: "error", message: "Item not found: " + payload.item };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

/**
 * Admin: submit a drop to the Droplog on behalf of a player.
 * Uses the same team-block approach as submitDrop — writes only C/D/E.
 */
function adminSubmitDrop(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Droplog");
    if (!sheet) {
      lock.releaseLock();
      return { status: "error", message: "Droplog sheet not found" };
    }

    var team = (payload.team || "").toUpperCase();
    var teamIndex = TEAM_CODES.indexOf(team);
    if (teamIndex === -1) {
      lock.releaseLock();
      return { status: "error", message: "Invalid team: " + team };
    }

    var DATA_START = 12;
    var BLOCK_SIZE = 999;
    var blockStart = DATA_START + (teamIndex * BLOCK_SIZE);

    // Find first empty row in team's block
    var rsnRange = sheet.getRange(blockStart, 3, BLOCK_SIZE, 1).getValues();
    var emptyRow = -1;
    for (var i = 0; i < rsnRange.length; i++) {
      if (!rsnRange[i][0] || rsnRange[i][0].toString().trim() === "") {
        emptyRow = blockStart + i;
        break;
      }
    }

    if (emptyRow === -1) {
      lock.releaseLock();
      return { status: "error", message: "No empty rows left for team " + team };
    }

    // Write only C (RSN), D (Drop Name), E (Date)
    sheet.getRange(emptyRow, 3, 1, 3).setValues([[
      payload.rsn || "",
      payload.dropName || "",
      payload.date || ""
    ]]);

    lock.releaseLock();
    return { status: "ok", message: "Drop logged for " + payload.rsn + " (" + team + " row " + emptyRow + ")" };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

/**
 * Set the winner for a bounty number on the Board Summary sheet.
 */
function adminSetBountyWinner(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Board Summary");
    if (!sheet) {
      lock.releaseLock();
      return { status: "error", message: "Board Summary sheet not found" };
    }

    // Search for "Bounty" in the sheet to find the bounty tracking section
    var data = sheet.getDataRange().getValues();
    var bountyNum = parseInt(payload.bountyNumber) || 0;
    var bountyRowFound = -1;

    for (var r = 0; r < data.length; r++) {
      for (var c = 0; c < data[r].length; c++) {
        var cell = (data[r][c] || "").toString();
        if (cell.indexOf("Bounty " + bountyNum) !== -1 || cell.indexOf("Bounty #" + bountyNum) !== -1 || cell === "" + bountyNum) {
          bountyRowFound = r + 1;
          break;
        }
      }
      if (bountyRowFound > 0) break;
    }

    if (bountyRowFound <= 0) {
      lock.releaseLock();
      return { status: "error", message: "Could not find Bounty #" + bountyNum + " in Board Summary" };
    }

    // Write description and winner to adjacent cells
    var lastCol = sheet.getLastColumn();
    if (payload.description) {
      sheet.getRange(bountyRowFound, Math.min(lastCol, 2)).setValue(payload.description);
    }
    if (payload.winnerTeam) {
      sheet.getRange(bountyRowFound, Math.min(lastCol, 3)).setValue(payload.winnerTeam);
    }

    lock.releaseLock();
    return { status: "ok", message: "Bounty #" + bountyNum + " updated" };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

/**
 * Override a team's tile points directly.
 */
function adminOverrideTilePoints(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);

  try {
    var teamCode = (payload.team || "").toUpperCase();
    var tileCode = (payload.tileCode || "").toUpperCase();
    var newPoints = parseFloat(payload.newPoints) || 0;

    if (TEAM_CODES.indexOf(teamCode) === -1) {
      lock.releaseLock();
      return { status: "error", message: "Invalid team: " + teamCode };
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var teamSheet = ss.getSheetByName(teamCode);
    if (!teamSheet) {
      lock.releaseLock();
      return { status: "error", message: "Team sheet not found: " + teamCode };
    }

    // Find the row where column A = tileCode
    var aCol = teamSheet.getRange("A1:A50").getValues();
    for (var r = 0; r < aCol.length; r++) {
      if ((aCol[r][0] || "").toString().toUpperCase() === tileCode) {
        teamSheet.getRange("C" + (r + 1)).setValue(newPoints);
        lock.releaseLock();
        return { status: "ok", message: teamCode + " tile " + tileCode + " set to " + newPoints };
      }
    }

    lock.releaseLock();
    return { status: "error", message: "Tile " + tileCode + " not found on " + teamCode + " sheet" };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

/**
 * Assign a player to a team.
 * Team-to-RSN mapping is managed via the Bank Checks / Player Listing tabs.
 * The Droplog's column A is formula-driven (pre-allocated blocks per team),
 * so player assignment should not modify the Droplog directly.
 *
 * This function is a placeholder — player assignment should be done
 * directly on the sheet (Bank Checks or Player Listing tab) by an admin.
 */
function adminAssignPlayer(payload) {
  var team = (payload.team || "").toUpperCase();
  var rsn = (payload.rsn || "").trim();

  if (TEAM_CODES.indexOf(team) === -1) {
    return { status: "error", message: "Invalid team: " + team };
  }

  return {
    status: "error",
    message: "Player assignment must be done directly on the Bank Checks or Player Listing tab. " +
             "Assign '" + rsn + "' to " + team + " there."
  };
}

/**
 * Read announcements from the "Announcements" sheet.
 * Expects column A to contain announcement messages, starting at row 2 (row 1 is header).
 * Empty rows are skipped.
 */
function readAnnouncements() {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Announcements");
    if (!sheet) return [];

    var lastRow = sheet.getLastRow();
    if (lastRow < 2) return [];

    var values = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
    var announcements = [];
    for (var i = 0; i < values.length; i++) {
      var msg = String(values[i][0]).trim();
      if (msg) announcements.push(msg);
    }
    return announcements;
  } catch (e) {
    return [];
  }
}

// ══════════════════════════════════════════
// Settings tab (shared admin config)
// ══════════════════════════════════════════

/**
 * Create the Settings tab if it doesn't exist.
 * Layout:
 *   A1: "Setting"    B1: "Value"
 *   A2: "webhookUrl" B2: (the URL)
 *   A3: "announcement" B3: (the text)
 */
function setupSettingsTab() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Settings");
  if (!sheet) {
    sheet = ss.insertSheet("Settings");
    sheet.getRange("A1:B1").setValues([["Setting", "Value"]]);
    sheet.getRange("A1:B1").setFontWeight("bold");
    sheet.getRange("A2:B8").setValues([
      ["discordWebhookUrl", ""],
      ["hiscoreApiUrl", ""],
      ["clanDropLogUrl", ""],
      ["announcement", ""],
      ["webhookUrl", ""],
      ["bingoStartDate", ""],
      ["bingoEndDate", ""]
    ]);
    sheet.setFrozenRows(1);
    sheet.setColumnWidth(1, 180);
    sheet.setColumnWidth(2, 400);
    // Add notes explaining date format
    sheet.getRange("A7").setNote("Format: YYYY-MM-DD (e.g. 2026-04-01). Bingo tab shows 7 days before start.");
    sheet.getRange("A8").setNote("Format: YYYY-MM-DD (e.g. 2026-04-30). Bingo tab hides after this date.");
  } else {
    // Ensure bingo date fields exist on existing Settings tabs
    var data = sheet.getDataRange().getValues();
    var keys = [];
    for (var i = 0; i < data.length; i++) keys.push((data[i][0] || "").toString().trim());
    if (keys.indexOf("bingoStartDate") === -1) {
      var nextRow = sheet.getLastRow() + 1;
      sheet.getRange(nextRow, 1, 2, 2).setValues([
        ["bingoStartDate", ""],
        ["bingoEndDate", ""]
      ]);
    }
  }
  return sheet;
}

/**
 * Return plugin configuration URLs from the Settings tab.
 * Called by the plugin on startup so players don't need to enter URLs manually.
 * Only requires the regular API key (not admin key).
 */
function getPluginConfig() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Settings");
  if (!sheet) {
    sheet = setupSettingsTab();
  }

  var data = sheet.getRange("A2:B100").getValues();
  var settings = {};
  for (var i = 0; i < data.length; i++) {
    var key = (data[i][0] || "").toString().trim();
    if (key) {
      settings[key] = (data[i][1] || "").toString();
    }
  }

  // Return config the plugin needs (URLs + bingo schedule + announcement)
  return {
    hiscoreApiUrl: settings["hiscoreApiUrl"] || "",
    clanDropLogUrl: settings["clanDropLogUrl"] || "",
    discordWebhookUrl: settings["discordWebhookUrl"] || "",
    bingoStartDate: settings["bingoStartDate"] || "",
    bingoEndDate: settings["bingoEndDate"] || "",
    announcement: settings["announcement"] || "",
    clanName: settings["clanName"] || ""
  };
}

/**
 * Create the Roster tab if it doesn't exist.
 * Layout:
 *   A1: "RSN"        B1: "Team"
 *   A2+: player RSNs, B2+: team codes (WOO, MB, TT, etc.)
 *
 * Run this once from the script editor before a bingo event.
 * Admins populate the roster with player assignments.
 */
function setupRosterTab() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Roster");
  if (!sheet) {
    sheet = ss.insertSheet("Roster");
    sheet.getRange("A1:B1").setValues([["RSN", "Team"]]);
    sheet.getRange("A1:B1").setFontWeight("bold");
    sheet.setFrozenRows(1);
    sheet.setColumnWidth(1, 200);
    sheet.setColumnWidth(2, 80);

    // Add data validation for team column
    var teamRule = SpreadsheetApp.newDataValidation()
      .requireValueInList(TEAM_CODES, true)
      .setAllowInvalid(false)
      .build();
    sheet.getRange("B2:B500").setDataValidation(teamRule);
  }
  return sheet;
}

/**
 * Read shared settings from the Settings tab.
 * Returns all key-value pairs from the Settings tab.
 */
function adminGetSettings() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("Settings");
  if (!sheet) {
    sheet = setupSettingsTab();
  }

  var data = sheet.getRange("A2:B100").getValues();
  var settings = {};
  for (var i = 0; i < data.length; i++) {
    var key = (data[i][0] || "").toString().trim();
    if (key) {
      settings[key] = (data[i][1] || "").toString();
    }
  }
  return settings;
}

/**
 * Save shared settings to the Settings tab.
 * Payload: { webhookUrl: "...", announcement: "..." }
 */
function adminSaveSettings(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Settings");
    if (!sheet) {
      sheet = setupSettingsTab();
    }

    // Read existing keys to find their rows
    var data = sheet.getRange("A2:A100").getValues();
    var keyRows = {};
    var lastUsedRow = 1;
    for (var i = 0; i < data.length; i++) {
      var key = (data[i][0] || "").toString().trim();
      if (key) {
        keyRows[key] = i + 2; // 1-based row
        lastUsedRow = i + 2;
      }
    }

    // Update or append each setting
    var settingsToSave = {};
    // Only save keys that are present in the payload (allows partial updates)
    var knownKeys = ["discordWebhookUrl", "hiscoreApiUrl", "clanDropLogUrl", "announcement", "webhookUrl", "bingoStartDate", "bingoEndDate", "clanName"];
    for (var k = 0; k < knownKeys.length; k++) {
      var key = knownKeys[k];
      if (payload.hasOwnProperty(key)) {
        settingsToSave[key] = payload[key] || "";
      }
    }
    for (var key in settingsToSave) {
      if (keyRows[key]) {
        sheet.getRange(keyRows[key], 2).setValue(settingsToSave[key]);
      } else {
        lastUsedRow++;
        sheet.getRange(lastUsedRow, 1, 1, 2).setValues([[key, settingsToSave[key]]]);
      }
    }

    lock.releaseLock();
    return { status: "ok", message: "Settings saved" };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

// ══════════════════════════════════════════
// Admin: Rotate API Key
// ══════════════════════════════════════════

/**
 * Update the API key stored in Script Properties.
 * Also propagates the new key to the Hiscore and Clan Drop Log services
 * if their URLs are configured in the Settings tab.
 *
 * Payload: { newApiKey: "new-key-here" }
 */
function adminRotateApiKey(payload) {
  var newKey = (payload.newApiKey || "").trim();
  if (!newKey || newKey.length < 6) {
    return { status: "error", message: "New API key must be at least 6 characters" };
  }

  var props = PropertiesService.getScriptProperties();
  var oldKey = props.getProperty("API_KEY") || DEFAULT_API_KEY;

  // Update this script's key
  props.setProperty("API_KEY", newKey);

  // Propagate to Hiscore and Clan Drop Log services
  var settings = getPluginConfig();
  var errors = [];

  if (settings.hiscoreApiUrl) {
    try {
      var resp = UrlFetchApp.fetch(settings.hiscoreApiUrl, {
        method: "post",
        contentType: "application/json",
        payload: JSON.stringify({
          key: oldKey,
          adminKey: payload.adminKey || "",
          action: "adminRotateApiKey",
          newApiKey: newKey
        }),
        muteHttpExceptions: true
      });
      var result = JSON.parse(resp.getContentText());
      if (result.status === "error") errors.push("Hiscore: " + result.message);
    } catch (e) {
      errors.push("Hiscore: " + e.toString());
    }
  }

  if (settings.clanDropLogUrl) {
    try {
      var resp = UrlFetchApp.fetch(settings.clanDropLogUrl, {
        method: "post",
        contentType: "application/json",
        payload: JSON.stringify({
          key: oldKey,
          adminKey: payload.adminKey || "",
          action: "adminRotateApiKey",
          newApiKey: newKey
        }),
        muteHttpExceptions: true
      });
      var result = JSON.parse(resp.getContentText());
      if (result.status === "error") errors.push("ClanDropLog: " + result.message);
    } catch (e) {
      errors.push("ClanDropLog: " + e.toString());
    }
  }

  if (errors.length > 0) {
    return { status: "partial", message: "Board key updated. Errors: " + errors.join("; "), newApiKey: newKey };
  }
  return { status: "ok", message: "API key rotated on all services", newApiKey: newKey };
}

// ══════════════════════════════════════════
// Admin: Remove a drop from the Droplog
// ══════════════════════════════════════════

/**
 * Remove a specific drop entry by clearing columns C/D/E (RSN, Drop Name, Date).
 * Matches on team block + RSN + dropName + date to find the exact row.
 *
 * Payload: { team, rsn, dropName, tileCode, date }
 */
function adminRemoveDrop(payload) {
  var lock = LockService.getScriptLock();
  lock.waitLock(15000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Droplog");
    if (!sheet) {
      lock.releaseLock();
      return { status: "error", message: "Droplog sheet not found" };
    }

    var team = (payload.team || "").toUpperCase();
    var teamIndex = TEAM_CODES.indexOf(team);
    if (teamIndex === -1) {
      lock.releaseLock();
      return { status: "error", message: "Invalid team: " + team };
    }

    var rsn = (payload.rsn || "").trim().toLowerCase();
    var dropName = (payload.dropName || "").trim().toLowerCase();
    var date = (payload.date || "").trim();

    var DATA_START = 12;
    var BLOCK_SIZE = 999;
    var blockStart = DATA_START + (teamIndex * BLOCK_SIZE);

    // Read the team's block: columns C (RSN), D (Drop Name), E (Date)
    var blockData = sheet.getRange(blockStart, 3, BLOCK_SIZE, 3).getValues();

    for (var i = 0; i < blockData.length; i++) {
      var rowRsn = (blockData[i][0] || "").toString().trim().toLowerCase();
      var rowDrop = (blockData[i][1] || "").toString().trim().toLowerCase();
      var rowDate = (blockData[i][2] || "").toString().trim();

      if (rowRsn === rsn && rowDrop === dropName && rowDate === date) {
        // Clear columns C, D, E for this row
        var rowNum = blockStart + i;
        sheet.getRange(rowNum, 3, 1, 3).clearContent();

        lock.releaseLock();
        return { status: "ok", message: "Removed " + payload.dropName + " from " + payload.rsn + " (row " + rowNum + ")" };
      }
    }

    lock.releaseLock();
    return { status: "error", message: "Drop not found: " + payload.dropName + " for " + payload.rsn + " on " + date };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

function jsonResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
