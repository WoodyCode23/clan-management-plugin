/**
 * Clan Drop Log — Google Apps Script
 *
 * Tracks all rare/valuable boss drops for clan members permanently.
 * Deployed on a separate "Clan Drop Log" Google Sheet.
 * Requires "Drop Whitelist" sheet (created by ClanDropWhitelist.gs).
 *
 * POST: { key, player, item, value, monster, kc, timestamp, x, y, plane }
 *        → Validates item against whitelist, records points
 * GET:  ?action=ping                        → { status: "ok" }
 *       ?action=clanWhitelist               → Full whitelist with points
 *       ?action=recent&limit=50             → Last N drops
 *       ?action=playerDrops&rsn=X           → All drops for a player
 *       ?action=stats                       → Summary stats
 *       ?action=leaderboard&period=monthly  → Leaderboard by points
 *
 * Deploy as Web App (Execute as: Me, Access: Anyone).
 */

// ── API Keys ── Stored in Script Properties for easy rotation.
var DEFAULT_API_KEY = "changeme";
var DEFAULT_ADMIN_KEY = "changeme-admin";

function getApiKeyFromProps() {
  return PropertiesService.getScriptProperties().getProperty("API_KEY") || DEFAULT_API_KEY;
}
function getAdminKeyFromProps() {
  return PropertiesService.getScriptProperties().getProperty("ADMIN_API_KEY") || DEFAULT_ADMIN_KEY;
}

var SHEET_NAME = "Drops";
var WHITELIST_SHEET = "Drop Whitelist";
var HISCORE_SHEET = "Hiscores";

function doGet(e) {
  var key = (e.parameter && e.parameter.key) ? e.parameter.key : "";
  if (key !== getApiKeyFromProps()) {
    return resp({ status: "error", message: "Invalid API key" });
  }

  var action = (e.parameter && e.parameter.action) ? e.parameter.action : "ping";

  if (action === "ping") {
    return resp({ status: "ok", message: "Clan Drop Log is running" });
  }

  if (action === "clanWhitelist") {
    return resp(getClanWhitelist());
  }

  if (action === "recent") {
    var limit = parseInt(e.parameter.limit) || 50;
    return resp(getRecentDrops(limit));
  }

  if (action === "playerDrops") {
    var rsn = (e.parameter.rsn || "").trim();
    if (!rsn) return resp({ status: "error", message: "Missing rsn" });
    return resp(getPlayerDrops(rsn));
  }

  if (action === "stats") {
    return resp(getStats());
  }

  if (action === "leaderboard") {
    var period = (e.parameter.period || "monthly").toLowerCase();
    return resp(getLeaderboard(period));
  }

  // ── Hiscore GET actions ──

  if (action === "topTimes") {
    var category = (e.parameter.category || "").trim();
    var limit = parseInt(e.parameter.limit) || 3;
    if (!category) return resp({ status: "error", message: "Missing category" });
    return resp(getTopTimes(category, limit));
  }

  if (action === "allTopTimes") {
    var limit = parseInt(e.parameter.limit) || 3;
    return resp(getAllTopTimes(limit));
  }

  if (action === "categories") {
    return resp(getHiscoreCategories());
  }

  return resp({ status: "error", message: "Unknown action: " + action });
}

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);

    if (data.key !== getApiKeyFromProps()) {
      return resp({ status: "error", message: "Invalid API key" });
    }

    // ── Admin: Rotate API key ──
    var action = (data.action || "").trim();
    if (action === "adminRotateApiKey") {
      var adminKey = data.adminKey || "";
      if (adminKey !== getAdminKeyFromProps()) {
        return resp({ status: "error", message: "Invalid admin key" });
      }
      var newKey = (data.newApiKey || "").trim();
      if (!newKey || newKey.length < 6) {
        return resp({ status: "error", message: "New API key must be at least 6 characters" });
      }
      PropertiesService.getScriptProperties().setProperty("API_KEY", newKey);
      return resp({ status: "ok", message: "API key updated" });
    }

    // ── Hiscore: Submit PB ──
    if (action === "submitPb") {
      return resp(handleSubmitPb(data));
    }

    // ── Admin: Remove hiscore entry ──
    if (action === "adminRemoveHiscore") {
      var adminKey = data.adminKey || "";
      if (adminKey !== getAdminKeyFromProps()) {
        return resp({ status: "error", message: "Invalid admin key" });
      }
      return resp(handleRemoveHiscore(data));
    }

    // ── Default: Log a drop ──
    var itemName = (data.item || "").trim();
    if (!itemName) {
      return resp({ status: "error", message: "Missing item name" });
    }

    var type = (data.type || "drop").toLowerCase();

    // Look up item in whitelist for points
    var whitelistEntry = lookupWhitelistItem(itemName);

    // For regular drops, require whitelist match; for collection log, allow all
    if (!whitelistEntry && type !== "collection_log") {
      return resp({ status: "skipped", message: "Item not on clan drop whitelist: " + itemName });
    }

    var points = whitelistEntry ? whitelistEntry.points : 0;
    var source = whitelistEntry ? whitelistEntry.source : (data.monster || "Unknown");

    var lock = LockService.getScriptLock();
    lock.waitLock(10000);

    try {
      var ss = SpreadsheetApp.getActiveSpreadsheet();
      var sheet = ss.getSheetByName(SHEET_NAME);
      if (!sheet) {
        setupSheet();
        sheet = ss.getSheetByName(SHEET_NAME);
      }

      sheet.appendRow([
        data.timestamp || new Date().toISOString(),
        data.player || "Unknown",
        itemName,
        data.value || 0,
        data.monster || "Unknown",
        data.kc || 0,
        points,
        source,
        data.x || 0,
        data.y || 0,
        data.plane || 0,
        type
      ]);

      return resp({ status: "ok", points: points, type: type });
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    return resp({ status: "error", message: err.toString() });
  }
}

/**
 * Look up an item in the Drop Whitelist sheet.
 * Returns { item, source, points } or null if not found.
 */
function lookupWhitelistItem(itemName) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(WHITELIST_SHEET);
  if (!sheet) return null;

  var data = sheet.getDataRange().getValues();
  var nameLower = itemName.toLowerCase();

  for (var i = 1; i < data.length; i++) {
    if ((data[i][0] || "").toString().toLowerCase() === nameLower) {
      return {
        item: data[i][0].toString(),
        source: (data[i][1] || "").toString(),
        points: parseInt(data[i][2]) || 0
      };
    }
  }
  return null;
}

/**
 * Return the full clan drop whitelist with point values.
 */
function getClanWhitelist() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(WHITELIST_SHEET);
  if (!sheet) return { items: [], error: "Drop Whitelist sheet not found" };

  var data = sheet.getDataRange().getValues();
  var items = [];

  for (var i = 1; i < data.length; i++) {
    var name = (data[i][0] || "").toString().trim();
    if (name) {
      items.push({
        item: name,
        source: (data[i][1] || "").toString(),
        points: parseInt(data[i][2]) || 0,
        dropRate: (data[i][3] || "").toString(),
        kph: (data[i][4] || "").toString(),
        category: (data[i][5] || "").toString()
      });
    }
  }

  return { items: items };
}

function getRecentDrops(limit) {
  var sheet = getSheet();
  if (!sheet) return { drops: [] };

  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return { drops: [] };

  var startRow = Math.max(2, lastRow - limit + 1);
  var numRows = lastRow - startRow + 1;
  var data = sheet.getRange(startRow, 1, numRows, 11).getValues();

  var drops = [];
  for (var i = data.length - 1; i >= 0; i--) {
    drops.push({
      timestamp: (data[i][0] || "").toString(),
      player: (data[i][1] || "").toString(),
      item: (data[i][2] || "").toString(),
      value: parseFloat(data[i][3]) || 0,
      monster: (data[i][4] || "").toString(),
      kc: parseInt(data[i][5]) || 0,
      points: parseInt(data[i][6]) || 0,
      source: (data[i][7] || "").toString()
    });
  }

  return { drops: drops, total: lastRow - 1 };
}

function getPlayerDrops(rsn) {
  var sheet = getSheet();
  if (!sheet) return { drops: [] };

  var data = sheet.getDataRange().getValues();
  var rsnLower = rsn.toLowerCase();
  var drops = [];

  for (var i = 1; i < data.length; i++) {
    if ((data[i][1] || "").toString().toLowerCase() === rsnLower) {
      drops.push({
        timestamp: (data[i][0] || "").toString(),
        item: (data[i][2] || "").toString(),
        value: parseFloat(data[i][3]) || 0,
        monster: (data[i][4] || "").toString(),
        kc: parseInt(data[i][5]) || 0,
        points: parseInt(data[i][6]) || 0,
        source: (data[i][7] || "").toString()
      });
    }
  }

  return { player: rsn, drops: drops };
}

function getStats() {
  var sheet = getSheet();
  if (!sheet) return { totalDrops: 0, uniquePlayers: 0, totalValue: 0, totalPoints: 0 };

  var data = sheet.getDataRange().getValues();
  var players = {};
  var totalValue = 0;
  var totalPoints = 0;

  for (var i = 1; i < data.length; i++) {
    var player = (data[i][1] || "").toString();
    var value = parseFloat(data[i][3]) || 0;
    var points = parseInt(data[i][6]) || 0;
    if (player) players[player.toLowerCase()] = true;
    totalValue += value;
    totalPoints += points;
  }

  return {
    totalDrops: data.length - 1,
    uniquePlayers: Object.keys(players).length,
    totalValue: totalValue,
    totalPoints: totalPoints
  };
}

/**
 * Build a leaderboard ranked by total drop points.
 * period: "monthly" (current month), "yearly" (current year), or "all"
 */
function getLeaderboard(period) {
  var sheet = getSheet();
  if (!sheet) return { period: period, players: [] };

  var data = sheet.getDataRange().getValues();
  var now = new Date();
  var currentMonth = now.getMonth();
  var currentYear = now.getFullYear();

  var playerMap = {}; // rsn -> { points, drops, value }

  for (var i = 1; i < data.length; i++) {
    var timestamp = data[i][0];
    var dropDate;
    if (timestamp instanceof Date) {
      dropDate = timestamp;
    } else {
      dropDate = new Date(timestamp.toString());
    }

    // Filter by period
    if (period === "monthly") {
      if (dropDate.getMonth() !== currentMonth || dropDate.getFullYear() !== currentYear) continue;
    } else if (period === "yearly") {
      if (dropDate.getFullYear() !== currentYear) continue;
    }
    // "all" = no filter

    var rsn = (data[i][1] || "").toString().trim();
    if (!rsn) continue;

    var value = parseFloat(data[i][3]) || 0;
    var points = parseInt(data[i][6]) || 0;

    var key = rsn.toLowerCase();
    if (!playerMap[key]) {
      playerMap[key] = { rsn: rsn, points: 0, drops: 0, value: 0 };
    }
    playerMap[key].points += points;
    playerMap[key].drops += 1;
    playerMap[key].value += value;
  }

  // Sort by points descending
  var players = [];
  for (var k in playerMap) {
    players.push(playerMap[k]);
  }
  players.sort(function(a, b) { return b.points - a.points; });

  // Add rank
  for (var r = 0; r < players.length; r++) {
    players[r].rank = r + 1;
  }

  return { period: period, players: players };
}

function setupSheet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
  }
  sheet.getRange("A1:L1").setValues([[
    "Timestamp", "Player", "Item", "Value", "Monster", "KC", "Points", "Source", "X", "Y", "Plane", "Type"
  ]]);
  sheet.getRange("A1:L1").setFontWeight("bold");
  sheet.setFrozenRows(1);
}

function getSheet() {
  return SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME);
}

// ═══════════════════════════════════════════════════════════════
// HISCORE FUNCTIONS
// ═══════════════════════════════════════════════════════════════

/**
 * Get top N times for a specific category, sorted by TimeSeconds ascending.
 */
function getTopTimes(category, limit) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(HISCORE_SHEET);
  if (!sheet) return { status: "ok", category: category, top3: [] };

  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return { status: "ok", category: category, top3: [] };

  var data = sheet.getRange(2, 1, lastRow - 1, 7).getValues();
  var entries = [];

  for (var i = 0; i < data.length; i++) {
    if ((data[i][0] || "").toString().trim() === category) {
      entries.push({
        row: i + 2, // sheet row (1-indexed, +1 for header)
        time: (data[i][1] || "").toString(),
        timeSeconds: parseFloat(data[i][2]) || 0,
        rsns: (data[i][3] || "").toString(),
        date: (data[i][4] || "").toString(),
        partySize: parseInt(data[i][5]) || 1,
        submitted: (data[i][6] || "").toString()
      });
    }
  }

  // Sort by timeSeconds ascending (fastest first)
  entries.sort(function(a, b) { return a.timeSeconds - b.timeSeconds; });

  // Limit
  entries = entries.slice(0, limit);

  // Add rank
  var result = [];
  for (var r = 0; r < entries.length; r++) {
    result.push({
      rank: r + 1,
      formattedTime: entries[r].time,
      timeSeconds: entries[r].timeSeconds,
      rsns: entries[r].rsns,
      date: entries[r].date,
      partySize: entries[r].partySize
    });
  }

  return { status: "ok", category: category, top3: result };
}

/**
 * Get top N times for ALL categories in a single batch call.
 * Returns { status, categories: { "cox_solo": [...], "bandos_duo": [...], ... } }
 */
function getAllTopTimes(limit) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(HISCORE_SHEET);
  if (!sheet) return { status: "ok", categories: {} };

  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return { status: "ok", categories: {} };

  var data = sheet.getRange(2, 1, lastRow - 1, 7).getValues();

  // Group by category
  var byCategory = {};
  for (var i = 0; i < data.length; i++) {
    var cat = (data[i][0] || "").toString().trim();
    if (!cat) continue;

    if (!byCategory[cat]) byCategory[cat] = [];
    byCategory[cat].push({
      time: (data[i][1] || "").toString(),
      timeSeconds: parseFloat(data[i][2]) || 0,
      rsns: (data[i][3] || "").toString(),
      date: (data[i][4] || "").toString(),
      partySize: parseInt(data[i][5]) || 1
    });
  }

  // Sort each category and limit
  var result = {};
  for (var cat in byCategory) {
    byCategory[cat].sort(function(a, b) { return a.timeSeconds - b.timeSeconds; });
    var top = byCategory[cat].slice(0, limit);
    result[cat] = [];
    for (var r = 0; r < top.length; r++) {
      result[cat].push({
        rank: r + 1,
        formattedTime: top[r].time,
        timeSeconds: top[r].timeSeconds,
        rsns: top[r].rsns,
        date: top[r].date,
        partySize: top[r].partySize
      });
    }
  }

  return { status: "ok", categories: result };
}

/**
 * Get list of categories that have at least one entry.
 */
function getHiscoreCategories() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(HISCORE_SHEET);
  if (!sheet) return { status: "ok", categories: [] };

  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return { status: "ok", categories: [] };

  var data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
  var seen = {};
  var cats = [];

  for (var i = 0; i < data.length; i++) {
    var cat = (data[i][0] || "").toString().trim();
    if (cat && !seen[cat]) {
      seen[cat] = true;
      cats.push(cat);
    }
  }

  return { status: "ok", categories: cats };
}

/**
 * Submit a PB time. Inserts the entry, then trims to top N for that category.
 * Returns the placement (1-3) or 0 if it didn't qualify.
 */
function handleSubmitPb(data) {
  var category = (data.category || "").trim();
  var time = (data.time || "").trim();
  var timeSeconds = parseFloat(data.timeSeconds) || 0;
  var rsns = (data.rsns || "").trim();
  var date = (data.date || "").trim();
  var partySize = parseInt(data.partySize) || 1;
  var maxEntries = parseInt(data.maxEntries) || 3;

  if (!category || !time || timeSeconds <= 0) {
    return { status: "error", message: "Missing required fields (category, time, timeSeconds)" };
  }

  var lock = LockService.getScriptLock();
  lock.waitLock(15000);

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName(HISCORE_SHEET);
    if (!sheet) {
      // Auto-create if missing
      sheet = ss.insertSheet(HISCORE_SHEET);
      sheet.getRange("A1:G1").setValues([["Category", "Time", "TimeSeconds", "RSN(s)", "Date", "PartySize", "Submitted"]]);
      sheet.getRange("A1:G1").setFontWeight("bold");
      sheet.setFrozenRows(1);
    }

    // Read all existing entries for this category
    var lastRow = sheet.getLastRow();
    var entries = []; // { row, timeSeconds }
    if (lastRow > 1) {
      var data2 = sheet.getRange(2, 1, lastRow - 1, 7).getValues();
      for (var i = 0; i < data2.length; i++) {
        if ((data2[i][0] || "").toString().trim() === category) {
          entries.push({
            row: i + 2,
            timeSeconds: parseFloat(data2[i][2]) || 0,
            rsns: (data2[i][3] || "").toString()
          });
        }
      }
    }

    // Check if same RSN already has a faster time
    for (var e = 0; e < entries.length; e++) {
      if (entries[e].rsns.toLowerCase() === rsns.toLowerCase() && entries[e].timeSeconds <= timeSeconds) {
        return { status: "ok", placed: 0, message: "Existing time is faster or equal" };
      }
    }

    // Add the new entry temporarily to see where it ranks
    entries.push({ row: -1, timeSeconds: timeSeconds, rsns: rsns });
    entries.sort(function(a, b) { return a.timeSeconds - b.timeSeconds; });

    // Find placement of the new entry
    var placed = 0;
    for (var p = 0; p < entries.length; p++) {
      if (entries[p].row === -1) {
        placed = p + 1;
        break;
      }
    }

    // If it doesn't qualify for top N, reject
    if (placed > maxEntries) {
      return { status: "ok", placed: 0, message: "Does not qualify for top " + maxEntries };
    }

    // Remove any existing entry for same RSN (we'll replace with the new faster time)
    var rowsToDelete = [];
    if (lastRow > 1) {
      var allData = sheet.getRange(2, 1, lastRow - 1, 7).getValues();
      for (var d = allData.length - 1; d >= 0; d--) {
        if ((allData[d][0] || "").toString().trim() === category &&
            (allData[d][3] || "").toString().toLowerCase() === rsns.toLowerCase()) {
          rowsToDelete.push(d + 2);
        }
      }
    }

    // Delete from bottom up to avoid row shift issues
    rowsToDelete.sort(function(a, b) { return b - a; });
    for (var del = 0; del < rowsToDelete.length; del++) {
      sheet.deleteRow(rowsToDelete[del]);
    }

    // Append new entry
    var submitted = new Date().toISOString();
    sheet.appendRow([category, time, timeSeconds, rsns, date, partySize, submitted]);

    // Now trim: keep only top N for this category
    trimCategoryToTop(sheet, category, maxEntries);

    // Re-read to confirm final placement
    var finalTop = getTopTimes(category, maxEntries);
    var finalPlaced = 0;
    for (var f = 0; f < finalTop.top3.length; f++) {
      if (finalTop.top3[f].rsns.toLowerCase() === rsns.toLowerCase()) {
        finalPlaced = f + 1;
        break;
      }
    }

    return { status: "ok", placed: finalPlaced };
  } finally {
    lock.releaseLock();
  }
}

/**
 * Trim a category to only keep the top N fastest entries.
 */
function trimCategoryToTop(sheet, category, maxEntries) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return;

  var data = sheet.getRange(2, 1, lastRow - 1, 7).getValues();
  var catEntries = [];

  for (var i = 0; i < data.length; i++) {
    if ((data[i][0] || "").toString().trim() === category) {
      catEntries.push({
        row: i + 2,
        timeSeconds: parseFloat(data[i][2]) || 0
      });
    }
  }

  if (catEntries.length <= maxEntries) return;

  // Sort by time ascending
  catEntries.sort(function(a, b) { return a.timeSeconds - b.timeSeconds; });

  // Delete entries beyond the top N (from bottom up)
  var toDelete = catEntries.slice(maxEntries);
  toDelete.sort(function(a, b) { return b.row - a.row; });
  for (var d = 0; d < toDelete.length; d++) {
    sheet.deleteRow(toDelete[d].row);
  }
}

/**
 * Admin: Remove a hiscore entry by category and rank.
 */
function handleRemoveHiscore(data) {
  var category = (data.category || "").trim();
  var rank = parseInt(data.rank) || 0;

  if (!category || rank <= 0) {
    return { status: "error", message: "Missing category or rank" };
  }

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);

  try {
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(HISCORE_SHEET);
    if (!sheet) return { status: "error", message: "Hiscores sheet not found" };

    var lastRow = sheet.getLastRow();
    if (lastRow <= 1) return { status: "error", message: "No entries found" };

    var allData = sheet.getRange(2, 1, lastRow - 1, 7).getValues();
    var catEntries = [];

    for (var i = 0; i < allData.length; i++) {
      if ((allData[i][0] || "").toString().trim() === category) {
        catEntries.push({
          row: i + 2,
          timeSeconds: parseFloat(allData[i][2]) || 0,
          rsns: (allData[i][3] || "").toString()
        });
      }
    }

    catEntries.sort(function(a, b) { return a.timeSeconds - b.timeSeconds; });

    if (rank > catEntries.length) {
      return { status: "error", message: "Rank " + rank + " does not exist (only " + catEntries.length + " entries)" };
    }

    var entry = catEntries[rank - 1];
    sheet.deleteRow(entry.row);

    return { status: "ok", message: "Removed rank " + rank + " (" + entry.rsns + ") from " + category };
  } finally {
    lock.releaseLock();
  }
}

// ═══════════════════════════════════════════════════════════════
// MIGRATION
// ═══════════════════════════════════════════════════════════════

/**
 * One-time migration: Copy entries from the old "3. Hiscore Tracking" sheet
 * (on the bingo spreadsheet) to the new "Hiscores" tab on this sheet.
 *
 * Run this manually from the Apps Script editor after setting up the Hiscores tab.
 * You must set BINGO_SHEET_ID to the ID of your bingo Google Sheet.
 */
function migrateV1Hiscores() {
  var BINGO_SHEET_ID = ""; // <-- Set this to your bingo spreadsheet ID
  if (!BINGO_SHEET_ID) {
    SpreadsheetApp.getUi().alert("Set BINGO_SHEET_ID in migrateV1Hiscores() before running.");
    return;
  }

  // Row number → category key mapping (matches SpeedCategory legacy rows)
  var ROW_MAP = {
    10: "cox_solo", 11: "cox_trio", 12: "cox_cm_solo", 13: "cox_cm_trio", 14: "cox_cm_5man",
    15: "tob_duo", 16: "tob_trio", 17: "tob_4man", 18: "tob_5man", 19: "tob_hm_5man",
    20: "toa_150_solo", 21: "toa_150_duo", 22: "toa_300_solo", 23: "toa_300_duo",
    24: "jad_solo", 25: "zuk_solo", 26: "colo_solo",
    27: "gauntlet_solo", 28: "cg_solo",
    29: "nightmare_group", 30: "phosani_solo",
    31: "duke_solo", 32: "levi_solo", 33: "vardorvis_solo", 34: "whisperer_solo",
    36: "ba_5man",
    37: "nex_duo", 38: "nex_trio",
    39: "raks_1st", 40: "raks_3rd", 41: "raks_5th",
    42: "titans_solo", 43: "titans_duo",
    44: "yama_solo", 45: "yama_duo"
  };

  try {
    var bingoSS = SpreadsheetApp.openById(BINGO_SHEET_ID);
    var oldSheet = bingoSS.getSheetByName("3. Hiscore Tracking");
    if (!oldSheet) {
      SpreadsheetApp.getUi().alert("Could not find '3. Hiscore Tracking' tab in bingo sheet.");
      return;
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var newSheet = ss.getSheetByName(HISCORE_SHEET);
    if (!newSheet) {
      SpreadsheetApp.getUi().alert("Run setupClanDropLog() first to create the Hiscores tab.");
      return;
    }

    // Old sheet layout: each row has 3 entries across columns
    // Columns: B=Time1, C=RSN1, D=Date1, E=TimeSeconds1 (approx layout — adjust as needed)
    // The exact column layout depends on the HiscoreAPI.gs sheet structure
    // Typical: Row N has 3 ranked entries, each with Time, RSNs, Date across columns

    var migrated = 0;
    var now = new Date().toISOString();

    for (var row in ROW_MAP) {
      var sheetRow = parseInt(row);
      var categoryKey = ROW_MAP[row];

      // Read columns B through M for this row (3 entries × 4 columns each)
      var rowData = oldSheet.getRange(sheetRow, 2, 1, 12).getValues()[0];

      for (var entry = 0; entry < 3; entry++) {
        var offset = entry * 4;
        var time = (rowData[offset] || "").toString().trim();
        var rsns = (rowData[offset + 1] || "").toString().trim();
        var date = (rowData[offset + 2] || "").toString().trim();
        var timeSec = parseFloat(rowData[offset + 3]) || 0;

        if (!time || timeSec <= 0) continue;

        newSheet.appendRow([categoryKey, time, timeSec, rsns, date, 1, now + " (migrated)"]);
        migrated++;
      }
    }

    SpreadsheetApp.getUi().alert("Migration complete. Migrated " + migrated + " entries to the Hiscores tab.");
  } catch (err) {
    SpreadsheetApp.getUi().alert("Migration error: " + err.toString());
  }
}

function resp(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

// ═══════════════════════════════════════════════════════════════
// DISCORD HISCORES WALL — Webhook-based leaderboard
// ═══════════════════════════════════════════════════════════════
//
// Posts and updates a persistent hiscore leaderboard in Discord
// using webhook messages. No bot hosting required.
//
// Required Script Properties (set via configureDiscordHiscores):
//   DISCORD_WEBHOOK_URL  — Webhook URL for the hiscores channel
//   DISCORD_BOT_TOKEN    — Bot token (only needed for clearing channel)
//   DISCORD_CHANNEL_ID   — Channel ID (only needed for clearing channel)
//   DISCORD_IMAGE_FOLDER — Google Drive folder ID containing banner PNGs (optional)
//
// Functions to run:
//   configureDiscordHiscores() — Interactive setup prompts
//   setupDiscordHiscores()     — Clear channel & post full leaderboard
//   updateDiscordHiscores()    — Edit existing messages in place
//
// For auto-updates, add a time-driven trigger on updateDiscordHiscores
// (e.g. every hour via Edit > Triggers in the Apps Script editor).

/**
 * Category registry — mirrors BossCategory.java display groups.
 * Each group has a name, embed color, optional image filename, and categories.
 */
var HISCORE_DISPLAY_GROUPS = [
  {
    name: "CoX", color: 0x58C4DD, image: "cox.png",
    categories: [
      { key: "cox_solo", title: "Chambers of Xeric \u2014 Solo" },
      { key: "cox_trio", title: "Chambers of Xeric \u2014 Trio" },
      { key: "cox_5man", title: "Chambers of Xeric \u2014 5-Man" },
      { key: "cox_cm_solo", title: "CM Chambers of Xeric \u2014 Solo" },
      { key: "cox_cm_trio", title: "CM Chambers of Xeric \u2014 Trio" },
      { key: "cox_cm_5man", title: "CM Chambers of Xeric \u2014 5-Man" }
    ]
  },
  {
    name: "ToB", color: 0x58C4DD, image: "tob.png",
    categories: [
      { key: "tob_duo", title: "Theatre of Blood \u2014 Duo" },
      { key: "tob_trio", title: "Theatre of Blood \u2014 Trio" },
      { key: "tob_4man", title: "Theatre of Blood \u2014 4-Man" },
      { key: "tob_5man", title: "Theatre of Blood \u2014 5-Man" }
    ]
  },
  {
    name: "ToA", color: 0x58C4DD, image: "toa.png",
    categories: [
      { key: "toa_solo", title: "Tombs of Amascut \u2014 Solo" },
      { key: "toa_duo", title: "Tombs of Amascut \u2014 Duo" },
      { key: "toa_expert_solo", title: "Tombs of Amascut (Expert) \u2014 Solo" },
      { key: "toa_expert_duo", title: "Tombs of Amascut (Expert) \u2014 Duo" }
    ]
  },
  {
    name: "Jad", color: 0xFF6400, image: "jad.png",
    categories: [
      { key: "jad_solo", title: "TzTok-Jad" }
    ]
  },
  {
    name: "Zuk", color: 0xFF6400, image: "zuk.png",
    categories: [
      { key: "zuk_solo", title: "TzKal-Zuk" }
    ]
  },
  {
    name: "Colo", color: 0xFF6400, image: "colo.png",
    categories: [
      { key: "colo_solo", title: "Sol Heredit" }
    ]
  },
  {
    name: "Gauntlet", color: 0x00B478, image: "gaunt.png",
    categories: [
      { key: "gauntlet_solo", title: "The Gauntlet" },
      { key: "cg_solo", title: "The Corrupted Gauntlet" }
    ]
  },
  {
    name: "Nightmare", color: 0x8232B4, image: "nightmare.png",
    categories: [
      { key: "phosani_solo", title: "Phosani's Nightmare \u2014 Solo" },
      { key: "nightmare_group", title: "The Nightmare \u2014 Group" }
    ]
  },
  {
    name: "DT2", color: 0x5078C8, image: "dt2.png",
    categories: [
      { key: "duke_solo", title: "Duke Sucellus \u2014 Solo" },
      { key: "levi_solo", title: "The Leviathan \u2014 Solo" },
      { key: "vardorvis_solo", title: "Vardorvis \u2014 Solo" },
      { key: "whisperer_solo", title: "The Whisperer \u2014 Solo" }
    ]
  },
  {
    name: "Sepulchre", color: 0xBE7832, image: "sep.png",
    categories: [
      { key: "sepulchre_solo", title: "Hallowed Sepulchre" }
    ]
  },
  {
    name: "BA", color: 0x3C8C28, image: "ba.png",
    categories: [
      { key: "ba_5man", title: "Barbarian Assault \u2014 5-Man" }
    ]
  },
  {
    name: "Nex", color: 0x6450A0, image: "nex.png",
    categories: [
      { key: "nex_duo", title: "Nex \u2014 Duo" },
      { key: "nex_trio", title: "Nex \u2014 Trio" }
    ]
  },
  {
    name: "Raks", color: 0x3C8C28, image: "raks.png",
    categories: [
      { key: "raks_1st", title: "TzHaar-Ket-Rak 1st Challenge" },
      { key: "raks_3rd", title: "TzHaar-Ket-Rak 3rd Challenge" },
      { key: "raks_5th", title: "TzHaar-Ket-Rak 5th Challenge" }
    ]
  },
  {
    name: "Titans", color: 0xDCB432, image: "titans.png",
    categories: [
      { key: "titans_solo", title: "Royal Titans \u2014 Solo" },
      { key: "titans_duo", title: "Royal Titans \u2014 Duo" }
    ]
  },
  {
    name: "Yama", color: 0xDCB432, image: "yama.png",
    categories: [
      { key: "yama_solo", title: "Yama \u2014 Solo" },
      { key: "yama_duo", title: "Yama \u2014 Duo" }
    ]
  }
];

var DISCORD_HEADER_TEXT =
  "# Personal Best Hiscores\n" +
  "The Hiscores are a compilation of the fastest personal bests that clan members have achieved. " +
  "PB times are now **auto-submitted** by the Clan Management RuneLite plugin when you get a new personal best in-game. " +
  "All participants must be a member of the clan for group content submissions.\n" +
  "> - *Solo times are auto-detected and submitted*\n" +
  "> - *Group times require all members to be in clan chat*\n" +
  "> - *Fight tracking ensures accurate party size detection*\n" +
  "> - *Submit manually:* <#1346516321206276096>";

// ── Script Property helpers ──

function getDiscordProp(key) {
  return PropertiesService.getScriptProperties().getProperty(key) || "";
}
function setDiscordProp(key, value) {
  PropertiesService.getScriptProperties().setProperty(key, value);
}

// ── Discord API helpers ──

function webhookSend(webhookUrl, payload) {
  var options = {
    method: "post",
    contentType: "application/json",
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  };
  var response = UrlFetchApp.fetch(webhookUrl + "?wait=true", options);
  if (response.getResponseCode() !== 200) {
    throw new Error("Webhook send failed: " + response.getResponseCode() + " — " + response.getContentText());
  }
  return JSON.parse(response.getContentText());
}

function webhookSendImage(webhookUrl, imageBlob) {
  var response = UrlFetchApp.fetch(webhookUrl + "?wait=true", {
    method: "post",
    payload: { file: imageBlob },
    muteHttpExceptions: true
  });
  if (response.getResponseCode() !== 200) {
    Logger.log("Image upload failed: " + response.getResponseCode());
    return null;
  }
  return JSON.parse(response.getContentText());
}

function webhookEdit(webhookUrl, messageId, payload) {
  var response = UrlFetchApp.fetch(webhookUrl + "/messages/" + messageId, {
    method: "patch",
    contentType: "application/json",
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });
  return response.getResponseCode() === 200;
}

function webhookDeleteMsg(webhookUrl, messageId) {
  UrlFetchApp.fetch(webhookUrl + "/messages/" + messageId, {
    method: "delete",
    muteHttpExceptions: true
  });
}

/**
 * Clear all messages from the hiscores channel.
 * Requires DISCORD_BOT_TOKEN and DISCORD_CHANNEL_ID in Script Properties.
 */
function clearDiscordChannel_() {
  var token = getDiscordProp("DISCORD_BOT_TOKEN");
  var channelId = getDiscordProp("DISCORD_CHANNEL_ID");
  if (!token || !channelId) {
    Logger.log("Cannot clear channel: DISCORD_BOT_TOKEN or DISCORD_CHANNEL_ID not set.");
    return;
  }

  var baseUrl = "https://discord.com/api/v10/channels/" + channelId;
  var headers = { "Authorization": "Bot " + token };
  var deleted = 0;

  // Fetch and delete in batches
  for (var batch = 0; batch < 10; batch++) {
    var resp = UrlFetchApp.fetch(baseUrl + "/messages?limit=100", {
      method: "get", headers: headers, muteHttpExceptions: true
    });
    if (resp.getResponseCode() !== 200) break;

    var messages = JSON.parse(resp.getContentText());
    if (messages.length === 0) break;

    for (var i = 0; i < messages.length; i++) {
      UrlFetchApp.fetch(baseUrl + "/messages/" + messages[i].id, {
        method: "delete", headers: headers, muteHttpExceptions: true
      });
      deleted++;
      Utilities.sleep(600);
    }
  }
  Logger.log("Cleared " + deleted + " messages from channel.");
}

// ── Formatting helpers ──

function formatDiscordTimes_(entries) {
  var medals = ["\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"]; // 🥇🥈🥉
  var lines = [];
  for (var i = 0; i < 3; i++) {
    if (i < entries.length) {
      var e = entries[i];
      lines.push(medals[i] + " `" + (e.formattedTime || "??:??") + "` " +
                  (e.rsns || "Unknown") + " \u2014 " + (e.date || "??/??"));
    } else {
      lines.push(medals[i] + " `--:--.--.--` \u2014 \u2014");
    }
  }
  return lines.join("\n");
}

function buildGroupEmbeds_(group, allTimes) {
  var embeds = [];
  for (var i = 0; i < group.categories.length; i++) {
    var cat = group.categories[i];
    var entries = allTimes[cat.key] || [];
    embeds.push({
      title: "**" + cat.title + "**",
      description: formatDiscordTimes_(entries),
      color: group.color
    });
  }
  // Footer on last embed
  if (embeds.length > 0) {
    var now = new Date();
    var months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
    var timeStr = ("0" + now.getHours()).slice(-2) + ":" + ("0" + now.getMinutes()).slice(-2);
    embeds[embeds.length - 1].footer = {
      text: "Updated " + months[now.getMonth()] + " " + now.getDate() + " " + timeStr
    };
  }
  return embeds;
}

/**
 * Try to load a banner image from the configured Google Drive folder.
 * Returns a Blob or null.
 */
function getImageBlob_(filename) {
  if (!filename) return null;
  var folderId = getDiscordProp("DISCORD_IMAGE_FOLDER");
  if (!folderId) return null;

  try {
    var folder = DriveApp.getFolderById(folderId);
    var files = folder.getFilesByName(filename);
    if (files.hasNext()) {
      return files.next().getBlob();
    }
  } catch (e) {
    Logger.log("Image load error for " + filename + ": " + e);
  }
  return null;
}

// ── Main functions ──

/**
 * Interactive setup: prompts for webhook URL, bot token, channel ID, and image folder.
 * Run this once from the Apps Script editor before using the other functions.
 */
function configureDiscordHiscores() {
  var ui = SpreadsheetApp.getUi();

  var r1 = ui.prompt("Discord Hiscores Setup (1/4)",
    "Enter your Discord webhook URL for the hiscores channel:", ui.ButtonSet.OK_CANCEL);
  if (r1.getSelectedButton() !== ui.Button.OK) return;
  if (r1.getResponseText().trim()) {
    setDiscordProp("DISCORD_WEBHOOK_URL", r1.getResponseText().trim());
  }

  var r2 = ui.prompt("Discord Hiscores Setup (2/4)",
    "Enter your Discord bot token (needed to clear channel on startup).\n" +
    "Leave blank to skip:", ui.ButtonSet.OK_CANCEL);
  if (r2.getSelectedButton() !== ui.Button.OK) return;
  if (r2.getResponseText().trim()) {
    setDiscordProp("DISCORD_BOT_TOKEN", r2.getResponseText().trim());
  }

  var r3 = ui.prompt("Discord Hiscores Setup (3/4)",
    "Enter the Discord channel ID for the hiscores channel.\n" +
    "Leave blank to skip:", ui.ButtonSet.OK_CANCEL);
  if (r3.getSelectedButton() !== ui.Button.OK) return;
  if (r3.getResponseText().trim()) {
    setDiscordProp("DISCORD_CHANNEL_ID", r3.getResponseText().trim());
  }

  var r4 = ui.prompt("Discord Hiscores Setup (4/4)",
    "Enter a Google Drive folder ID containing banner images (cox.png, dt2.png, etc).\n" +
    "Leave blank to skip images:", ui.ButtonSet.OK_CANCEL);
  if (r4.getSelectedButton() !== ui.Button.OK) return;
  if (r4.getResponseText().trim()) {
    setDiscordProp("DISCORD_IMAGE_FOLDER", r4.getResponseText().trim());
  }

  ui.alert("Configuration saved! Run setupDiscordHiscores() to post the leaderboard.");
}

/**
 * Full setup: clears the channel, then posts header + all group embeds.
 * Run from the Apps Script editor or via a custom menu.
 */
function setupDiscordHiscores() {
  var webhookUrl = getDiscordProp("DISCORD_WEBHOOK_URL");
  if (!webhookUrl) {
    try {
      SpreadsheetApp.getUi().alert("Run configureDiscordHiscores() first to set the webhook URL.");
    } catch (e) {
      Logger.log("ERROR: DISCORD_WEBHOOK_URL not set. Run configureDiscordHiscores().");
    }
    return;
  }

  Logger.log("Starting Discord hiscores setup...");

  // 1. Clear existing webhook messages we know about
  var props = PropertiesService.getScriptProperties().getProperties();
  for (var key in props) {
    if (key.indexOf("DISCORD_MSG_") === 0) {
      var ids = props[key].split(",");
      for (var i = 0; i < ids.length; i++) {
        if (ids[i]) {
          try { webhookDeleteMsg(webhookUrl, ids[i]); } catch (e) {}
          Utilities.sleep(400);
        }
      }
      PropertiesService.getScriptProperties().deleteProperty(key);
    }
  }

  // 2. Clear all other messages in channel (needs bot token)
  clearDiscordChannel_();
  Utilities.sleep(2000);

  // 3. Fetch all hiscore data
  var allTimes = getAllTopTimes(3).categories || {};
  Logger.log("Fetched times for " + Object.keys(allTimes).length + " categories");

  // 4. Post header message
  var headerMsg = webhookSend(webhookUrl, { content: DISCORD_HEADER_TEXT });
  setDiscordProp("DISCORD_MSG_HEADER", headerMsg.id);
  Utilities.sleep(1000);

  // 5. Post each group (image + embeds)
  for (var g = 0; g < HISCORE_DISPLAY_GROUPS.length; g++) {
    var group = HISCORE_DISPLAY_GROUPS[g];
    var safeName = group.name.replace(/ /g, "_").toUpperCase();
    var msgIds = [];

    // Post banner image if available
    var imageBlob = getImageBlob_(group.image);
    if (imageBlob) {
      var imgMsg = webhookSendImage(webhookUrl, imageBlob);
      if (imgMsg) {
        msgIds.push(imgMsg.id);
        Utilities.sleep(1000);
      }
    }

    // Build and send embeds (max 10 per message)
    var embeds = buildGroupEmbeds_(group, allTimes);
    for (var c = 0; c < embeds.length; c += 10) {
      var chunk = embeds.slice(c, c + 10);
      var msg = webhookSend(webhookUrl, { embeds: chunk });
      msgIds.push(msg.id);
      Utilities.sleep(1000);
    }

    setDiscordProp("DISCORD_MSG_" + safeName, msgIds.join(","));
    Logger.log("Posted: " + group.name + " (" + msgIds.length + " messages)");
  }

  Logger.log("Discord hiscores setup complete!");
  try {
    SpreadsheetApp.getUi().alert("Discord hiscores wall posted successfully!");
  } catch (e) {}
}

/**
 * Update existing leaderboard messages in place.
 * Safe to run on a time trigger (e.g. every hour).
 * If any message is missing, falls back to full setupDiscordHiscores().
 */
function updateDiscordHiscores() {
  var webhookUrl = getDiscordProp("DISCORD_WEBHOOK_URL");
  if (!webhookUrl) {
    Logger.log("DISCORD_WEBHOOK_URL not set — skipping update.");
    return;
  }

  // Check that we have message IDs stored
  var headerId = getDiscordProp("DISCORD_MSG_HEADER");
  if (!headerId) {
    Logger.log("No header message ID found. Running full setup...");
    setupDiscordHiscores();
    return;
  }

  Logger.log("Starting Discord hiscores update...");
  var allTimes = getAllTopTimes(3).categories || {};

  for (var g = 0; g < HISCORE_DISPLAY_GROUPS.length; g++) {
    var group = HISCORE_DISPLAY_GROUPS[g];
    var safeName = group.name.replace(/ /g, "_").toUpperCase();
    var msgIdsStr = getDiscordProp("DISCORD_MSG_" + safeName);

    if (!msgIdsStr) {
      Logger.log("Missing message IDs for " + group.name + ". Running full setup...");
      setupDiscordHiscores();
      return;
    }

    var msgIds = msgIdsStr.split(",");
    var embeds = buildGroupEmbeds_(group, allTimes);

    // Find the embed message IDs (skip image message if group has an image)
    var embedStartIdx = group.image ? 1 : 0;

    for (var c = 0; c < embeds.length; c += 10) {
      var chunk = embeds.slice(c, c + 10);
      var msgIndex = embedStartIdx + Math.floor(c / 10);

      if (msgIndex >= msgIds.length) {
        Logger.log("Not enough stored message IDs for " + group.name + ". Running full setup...");
        setupDiscordHiscores();
        return;
      }

      var success = webhookEdit(webhookUrl, msgIds[msgIndex], { embeds: chunk });
      if (!success) {
        Logger.log("Edit failed for " + group.name + " message " + msgIds[msgIndex] + ". Running full setup...");
        setupDiscordHiscores();
        return;
      }
      Utilities.sleep(500);
    }
  }

  Logger.log("Discord hiscores update complete.");
}
