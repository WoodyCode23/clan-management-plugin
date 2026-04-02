/**
 * SWB26 Hiscore API — Google Apps Script endpoint
 *
 * Reads and writes personal best times to the "3. Hiscore Tracking" sheet.
 *
 * Endpoints:
 *   GET  ?action=ping               → { status: "ok" }
 *   GET  ?action=categories          → All categories with titles and top 3 times
 *   GET  ?action=topTimes&row=10     → Top 3 times for a specific row
 *   POST {action:"submitPb", row:10, time:"22:15.00", rsns:"Player1", date:"03/20"}
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

var HISCORE_SHEET_NAME = "3. Hiscore Tracking";

// Column indices (1-based)
var COL_TITLE = 14;    // N - subcategory title
var COL_FIRST = 15;    // O - 1st place
var COL_SECOND = 16;   // P - 2nd place
var COL_THIRD = 17;    // Q - 3rd place

// All tracked rows with their group key
var CATEGORY_ROWS = {
  10: "cox", 11: "cox", 12: "cox", 13: "cox", 14: "cox",
  15: "tob", 16: "tob", 17: "tob", 18: "tob", 19: "tob",
  20: "toa", 21: "toa", 22: "toa", 23: "toa",
  24: "jad",
  25: "zuk",
  26: "colo",
  27: "gaunt", 28: "gaunt",
  29: "nightmare", 30: "nightmare",
  31: "dt2", 32: "dt2", 33: "dt2", 34: "dt2",
  36: "ba",
  37: "nex", 38: "nex",
  39: "raks", 40: "raks", 41: "raks",
  42: "titans", 43: "titans",
  44: "yama", 45: "yama"
};

function doGet(e) {
  var key = (e.parameter && e.parameter.key) ? e.parameter.key : "";
  if (key !== getApiKeyFromProps()) {
    return jsonResp({ status: "error", message: "Invalid API key" });
  }

  var action = (e.parameter && e.parameter.action) ? e.parameter.action : "categories";

  if (action === "ping") {
    return jsonResp({ status: "ok" });
  }

  if (action === "categories") {
    return jsonResp(getAllCategories());
  }

  if (action === "topTimes") {
    var row = parseInt(e.parameter.row);
    if (!row || !CATEGORY_ROWS[row]) {
      return jsonResp({ status: "error", message: "Invalid row: " + e.parameter.row });
    }
    return jsonResp(getTopTimes(row));
  }

  return jsonResp({ status: "error", message: "Unknown action: " + action });
}

function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);

    if (payload.key !== getApiKeyFromProps()) {
      return jsonResp({ status: "error", message: "Invalid API key" });
    }

    var action = payload.action || "";

    if (action === "submitPb") {
      var row = parseInt(payload.row);
      var time = payload.time || "";
      var rsns = payload.rsns || "";
      var date = payload.date || "";

      if (!row || !CATEGORY_ROWS[row]) {
        return jsonResp({ status: "error", message: "Invalid row: " + row });
      }
      if (!time) {
        return jsonResp({ status: "error", message: "Missing time" });
      }

      var result = submitPb(row, time, rsns, date);
      return jsonResp(result);
    }

    // ── Admin: Remove hiscore entry ──
    if (action === "adminRemoveEntry") {
      var adminKey = payload.adminKey || "";
      if (adminKey !== getAdminKeyFromProps()) {
        return jsonResp({ status: "error", message: "Invalid admin key" });
      }

      var row = parseInt(payload.row);
      var rank = parseInt(payload.rank);

      if (!row || !CATEGORY_ROWS[row]) {
        return jsonResp({ status: "error", message: "Invalid row: " + row });
      }
      if (rank < 1 || rank > 3) {
        return jsonResp({ status: "error", message: "Rank must be 1-3" });
      }

      var lock = LockService.getScriptLock();
      lock.waitLock(10000);

      try {
        var sheet = getSheet();
        var cols = [COL_FIRST, COL_SECOND, COL_THIRD];
        sheet.getRange(row, cols[rank - 1]).setValue("XX:XX RSN(s) MM/DD");
        lock.releaseLock();
        return jsonResp({ status: "ok", message: "Removed rank #" + rank + " from row " + row });
      } catch (err) {
        lock.releaseLock();
        return jsonResp({ status: "error", message: err.toString() });
      }
    }

    // ── Admin: Rotate API key ──
    if (action === "adminRotateApiKey") {
      var adminKey = payload.adminKey || "";
      if (adminKey !== getAdminKeyFromProps()) {
        return jsonResp({ status: "error", message: "Invalid admin key" });
      }
      var newKey = (payload.newApiKey || "").trim();
      if (!newKey || newKey.length < 6) {
        return jsonResp({ status: "error", message: "New API key must be at least 6 characters" });
      }
      PropertiesService.getScriptProperties().setProperty("API_KEY", newKey);
      return jsonResp({ status: "ok", message: "API key updated" });
    }

    return jsonResp({ status: "error", message: "Unknown action: " + action });
  } catch (err) {
    return jsonResp({ status: "error", message: err.toString() });
  }
}

/**
 * Returns all categories with their titles and current top 3.
 */
function getAllCategories() {
  var sheet = getSheet();
  var categories = [];

  var rows = Object.keys(CATEGORY_ROWS);
  for (var i = 0; i < rows.length; i++) {
    var row = parseInt(rows[i]);
    var title = sheet.getRange(row, COL_TITLE).getValue() || "";
    var group = CATEGORY_ROWS[row];
    var top3 = readTop3(sheet, row);

    categories.push({
      row: row,
      group: group,
      title: title.toString(),
      top3: top3
    });
  }

  return { categories: categories };
}

/**
 * Returns top 3 times for a specific row.
 */
function getTopTimes(row) {
  var sheet = getSheet();
  var title = sheet.getRange(row, COL_TITLE).getValue() || "";
  var top3 = readTop3(sheet, row);

  return {
    row: row,
    group: CATEGORY_ROWS[row],
    title: title.toString(),
    top3: top3
  };
}

/**
 * Submit a PB. Checks if it's top 3, inserts if so.
 * Also handles updating an existing entry for the same RSN(s).
 */
function submitPb(row, time, rsns, date) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
  } catch (err) {
    return { status: "error", message: "Could not acquire lock" };
  }

  try {
    var sheet = getSheet();
    var top3 = readTop3(sheet, row);
    var newTimeSeconds = parseTimeToSeconds(time);

    if (newTimeSeconds <= 0) {
      return { status: "error", message: "Invalid time format: " + time };
    }

    // Check if this RSN(s) already has an entry - if so, remove it first
    var rsnsLower = rsns.toLowerCase().trim();
    var filtered = [];
    for (var i = 0; i < top3.length; i++) {
      if (top3[i].rsns.toLowerCase().trim() !== rsnsLower) {
        filtered.push(top3[i]);
      }
    }

    // Find insertion position
    var position = -1;
    for (var i = 0; i < filtered.length; i++) {
      if (newTimeSeconds < filtered[i].timeSeconds) {
        position = i;
        break;
      }
    }

    // If not faster than any existing, check if there's room
    if (position === -1) {
      if (filtered.length < 3) {
        position = filtered.length;
      } else {
        lock.releaseLock();
        return { status: "ok", placed: 0, message: "Time does not qualify for top 3" };
      }
    }

    // Build the formatted entry: "TIME RSN(s) MM/DD"
    var entry = time + " " + rsns + " " + date;

    // Insert at position
    filtered.splice(position, 0, {
      raw: entry,
      timeSeconds: newTimeSeconds,
      formattedTime: time,
      rsns: rsns,
      date: date
    });

    // Keep only top 3
    while (filtered.length > 3) {
      filtered.pop();
    }

    // Write back to sheet
    writeTop3(sheet, row, filtered);

    lock.releaseLock();
    return { status: "ok", placed: position + 1 };
  } catch (err) {
    lock.releaseLock();
    return { status: "error", message: err.toString() };
  }
}

/**
 * Read the top 3 entries from a row.
 */
function readTop3(sheet, row) {
  var cols = [COL_FIRST, COL_SECOND, COL_THIRD];
  var entries = [];

  for (var i = 0; i < cols.length; i++) {
    var cellValue = (sheet.getRange(row, cols[i]).getValue() || "").toString().trim();
    if (!cellValue || cellValue === "XX:XX RSN(s) MM/DD" || cellValue.indexOf("XX:XX") === 0) {
      continue;
    }

    var parsed = parseEntry(cellValue);
    if (parsed && parsed.timeSeconds > 0) {
      parsed.rank = i + 1;
      entries.push(parsed);
    }
  }

  return entries;
}

/**
 * Write top 3 entries back to the sheet.
 */
function writeTop3(sheet, row, entries) {
  var cols = [COL_FIRST, COL_SECOND, COL_THIRD];
  var placeholder = "XX:XX RSN(s) MM/DD";

  for (var i = 0; i < 3; i++) {
    if (i < entries.length && entries[i].raw) {
      sheet.getRange(row, cols[i]).setValue(entries[i].raw);
    } else {
      sheet.getRange(row, cols[i]).setValue(placeholder);
    }
  }
}

/**
 * Parse a cell entry into components.
 * Supports two formats:
 *   Legacy:  "TIME - RSN(s) - MM/DD/YYYY - [Proof](url)"
 *   Plugin:  "TIME RSN(s) MM/DD"
 */
function parseEntry(cellValue) {
  if (!cellValue) return null;

  // Check if cell uses " - " delimiters (legacy/manual format)
  if (cellValue.indexOf(" - ") !== -1) {
    var segments = cellValue.split(" - ");
    var time = segments[0].trim();
    var timeSeconds = parseTimeToSeconds(time);
    var rsns = segments.length > 1 ? segments[1].trim() : "";
    var date = "";

    // Find the date segment (looks like M/D/YYYY or M/D/YY)
    for (var i = 2; i < segments.length; i++) {
      var seg = segments[i].trim();
      if (seg.match(/^\d{1,2}\/\d{1,2}/)) {
        date = seg;
        break;
      }
    }

    return {
      raw: cellValue,
      timeSeconds: timeSeconds,
      formattedTime: time,
      rsns: rsns,
      date: date
    };
  }

  // Simple space-separated format: "TIME RSN(s) MM/DD"
  var parts = cellValue.split(" ");
  if (parts.length < 1) return null;

  var time = parts[0];
  var timeSeconds = parseTimeToSeconds(time);

  var date = parts.length > 1 ? parts[parts.length - 1] : "";
  var rsns = "";
  if (parts.length > 2) {
    rsns = parts.slice(1, parts.length - 1).join(" ");
  } else if (parts.length === 2) {
    if (date.match(/^\d{1,2}\/\d{1,2}$/)) {
      rsns = "";
    } else {
      rsns = date;
      date = "";
    }
  }

  return {
    raw: cellValue,
    timeSeconds: timeSeconds,
    formattedTime: time,
    rsns: rsns,
    date: date
  };
}

/**
 * Parse a time string to total seconds.
 * Supports: "MM:SS.ss", "H:MM:SS.ss", "SS.ss"
 */
function parseTimeToSeconds(timeStr) {
  if (!timeStr) return 0;

  var parts = timeStr.split(":");
  var seconds = 0;

  if (parts.length === 3) {
    // H:MM:SS.ss
    seconds = parseFloat(parts[0]) * 3600 + parseFloat(parts[1]) * 60 + parseFloat(parts[2]);
  } else if (parts.length === 2) {
    // MM:SS.ss
    seconds = parseFloat(parts[0]) * 60 + parseFloat(parts[1]);
  } else if (parts.length === 1) {
    // SS.ss
    seconds = parseFloat(parts[0]);
  }

  return isNaN(seconds) ? 0 : seconds;
}

function getSheet() {
  return SpreadsheetApp.getActiveSpreadsheet().getSheetByName(HISCORE_SHEET_NAME);
}

function jsonResp(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
