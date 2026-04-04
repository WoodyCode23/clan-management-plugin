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
