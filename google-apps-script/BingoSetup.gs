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
