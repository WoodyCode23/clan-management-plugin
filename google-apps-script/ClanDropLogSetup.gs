/**
 * Clan Drop Log — Sheet Setup & Dashboard Builder
 *
 * Run setupClanDropLog() from the Apps Script editor to build
 * a polished, fully-formulated Google Sheet companion for the
 * RuneLite drop-logger plugin.
 *
 * Tabs created / rebuilt:
 *   1. Drops        — Raw data (preserved on re-run)
 *   2. Dashboard    — Visual summary with live formulas
 *   3. Leaderboard  — Multi-view ranking tables
 *   4. Drop Whitelist — Note only (populated by ClanDropWhitelist.gs)
 *
 * Safe to re-run: Dashboard and Leaderboard are cleared and rebuilt;
 * Drops data and Drop Whitelist are never wiped.
 *
 * OPTIMIZED: Uses batch getRange/setValues to minimize API calls.
 */

var CLR_HEADER      = "#1a237e";
var CLR_ACCENT      = "#ff8f00";
var CLR_SECTION     = "#283593";
var CLR_WHITE       = "#ffffff";
var CLR_LIGHT_GRAY  = "#f5f5f5";
var CLR_MED_GRAY    = "#e0e0e0";
var CLR_DARK_TEXT   = "#212121";
var CLR_ALT_ROW_A   = "#ffffff";
var CLR_ALT_ROW_B   = "#e8eaf6";

var FONT_FAMILY     = "Google Sans";
var DROPS_SHEET     = "Drops";
var DASH_SHEET      = "Dashboard";
var LB_SHEET        = "Leaderboard";
var WL_SHEET        = "Drop Whitelist";
var HS_SHEET        = "Hiscores";

/**
 * Quick setup — only creates the Hiscores tab.
 * Use this if the full setupClanDropLog() times out.
 */
function setupHiscoresOnly() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  setupHiscoresTab_(ss);
  SpreadsheetApp.flush();
  SpreadsheetApp.getUi().alert("Hiscores tab created successfully.");
}

function setupClanDropLog() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  setupDropsTab_(ss);
  SpreadsheetApp.flush();

  setupDashboardTab_(ss);
  SpreadsheetApp.flush();

  setupLeaderboardTab_(ss);
  SpreadsheetApp.flush();

  setupWhitelistNote_(ss);
  setupHiscoresTab_(ss);
  SpreadsheetApp.flush();

  reorderSheets_(ss);

  SpreadsheetApp.flush();
  SpreadsheetApp.getUi().alert(
    "Clan Drop Log setup complete.\n\n" +
    "Tabs: Dashboard | Drops | Leaderboard | Hiscores | Drop Whitelist\n\n" +
    "Deploy ClanDropLog.gs as a Web App to start receiving drops."
  );
}

// ═══════════════════════════════════════════════════════════════
// 1. DROPS TAB
// ═══════════════════════════════════════════════════════════════

function setupDropsTab_(ss) {
  var sheet = ss.getSheetByName(DROPS_SHEET);
  if (!sheet) sheet = ss.insertSheet(DROPS_SHEET);

  var headers = ["Timestamp", "Player", "Item", "Value (GP)", "Monster", "KC", "Points", "Source", "Type"];
  var headerRange = sheet.getRange(1, 1, 1, headers.length);
  headerRange.setValues([headers])
    .setFontFamily(FONT_FAMILY).setFontSize(10).setFontWeight("bold")
    .setFontColor(CLR_WHITE).setBackground(CLR_HEADER)
    .setHorizontalAlignment("center").setVerticalAlignment("middle");
  sheet.setRowHeight(1, 32);
  sheet.setFrozenRows(1);

  // Column widths — batch
  var widths = [170, 140, 220, 120, 180, 60, 70, 180, 120];
  for (var i = 0; i < widths.length; i++) sheet.setColumnWidth(i + 1, widths[i]);

  // Number formats
  sheet.getRange("D2:D").setNumberFormat("#,##0");
  sheet.getRange("G2:G").setNumberFormat("#,##0");

  // Banding
  var bandings = sheet.getBandings();
  for (var b = 0; b < bandings.length; b++) bandings[b].remove();
  sheet.getRange(1, 1, sheet.getMaxRows(), headers.length)
    .applyRowBanding(SpreadsheetApp.BandingTheme.LIGHT_GREY, true, false);

  // Conditional formatting on Points (G)
  sheet.setConditionalFormatRules([]);
  var pointsRange = sheet.getRange("G2:G");
  var rules = [];
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberGreaterThanOrEqualTo(2000)
    .setBackground("#ffcdd2").setFontColor("#c62828").setBold(true).setRanges([pointsRange]).build());
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberBetween(1000, 1999)
    .setBackground("#ffcdd2").setFontColor("#e53935").setBold(true).setRanges([pointsRange]).build());
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberBetween(500, 999)
    .setBackground("#ffe0b2").setFontColor("#ef6c00").setBold(true).setRanges([pointsRange]).build());
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberBetween(200, 499)
    .setBackground("#c8e6c9").setFontColor("#2e7d32").setRanges([pointsRange]).build());
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberBetween(100, 199)
    .setBackground("#b2dfdb").setFontColor("#00695c").setRanges([pointsRange]).build());
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberLessThan(100)
    .setBackground("#eeeeee").setFontColor("#757575").setRanges([pointsRange]).build());
  sheet.setConditionalFormatRules(rules);

  // Sort by timestamp if data exists
  if (sheet.getLastRow() > 1) {
    sheet.getRange(2, 1, sheet.getLastRow() - 1, headers.length)
      .sort({column: 1, ascending: false});
  }
}

// ═══════════════════════════════════════════════════════════════
// 2. DASHBOARD TAB
// ═══════════════════════════════════════════════════════════════

function setupDashboardTab_(ss) {
  var sheet = ss.getSheetByName(DASH_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(DASH_SHEET);
  } else {
    sheet.clear();
    sheet.setConditionalFormatRules([]);
    var bandings = sheet.getBandings();
    for (var b = 0; b < bandings.length; b++) bandings[b].remove();
  }

  ensureSize_(sheet, 70, 7);

  // Global font in one call
  sheet.getRange(1, 1, sheet.getMaxRows(), 7).setFontFamily(FONT_FAMILY);

  // Column widths
  var widths = [60, 180, 140, 140, 140, 140, 140];
  for (var i = 0; i < widths.length; i++) sheet.setColumnWidth(i + 1, widths[i]);

  // ── HEADER (rows 1-3) ──
  sheet.getRange("A1:G1").merge().setValue("CLAN DROP LOG")
    .setFontSize(28).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground(CLR_HEADER).setHorizontalAlignment("center").setVerticalAlignment("middle");
  sheet.setRowHeight(1, 60);

  sheet.getRange("A2:G2").merge()
    .setFormula('="Live Dashboard   |   Updated: "&TEXT(NOW(),"ddd, mmmm d, yyyy  h:mm AM/PM")')
    .setFontSize(10).setFontColor("#b0bec5").setBackground("#0d1540").setHorizontalAlignment("center");
  sheet.setRowHeight(2, 28);

  sheet.getRange("A3:G3").merge().setBackground(CLR_ACCENT);
  sheet.setRowHeight(3, 4);

  // ── STATS (rows 5-8) ──
  sheet.getRange("A5:G5").merge().setValue("OVERVIEW")
    .setFontSize(14).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground(CLR_SECTION).setHorizontalAlignment("left");
  sheet.setRowHeight(5, 32);

  // Labels row
  sheet.getRange(6, 1, 1, 7).setValues([["", "Total Drops", "Unique Players", "Total GP Value",
    "Total Points", "Avg Value/Drop", "This Month"]])
    .setFontSize(9).setFontWeight("bold").setFontColor("#78909c")
    .setBackground(CLR_LIGHT_GRAY).setHorizontalAlignment("center");

  // Stats formulas — batch set values then individual formulas
  var statsFormulas = [
    ['B7', '=COUNTA(Drops!B2:B)'],
    ['C7', '=IFERROR(SUMPRODUCT(1/COUNTIF(Drops!B2:B,Drops!B2:B)),0)'],
    ['D7', '=SUM(Drops!D2:D)'],
    ['E7', '=SUM(Drops!G2:G)'],
    ['F7', '=IFERROR(SUM(Drops!D2:D)/COUNTA(Drops!B2:B),0)'],
    ['G7', '=COUNTIFS(Drops!A2:A,">="&DATE(YEAR(TODAY()),MONTH(TODAY()),1))']
  ];
  for (var i = 0; i < statsFormulas.length; i++) {
    sheet.getRange(statsFormulas[i][0]).setFormula(statsFormulas[i][1]);
  }

  // Format all stat cells at once
  var statsRow = sheet.getRange("B7:G7");
  statsRow.setNumberFormat("#,##0").setFontSize(22).setFontWeight("bold")
    .setHorizontalAlignment("center").setBackground(CLR_WHITE);
  sheet.getRange("B7").setFontColor(CLR_HEADER);
  sheet.getRange("C7").setFontColor(CLR_HEADER);
  sheet.getRange("D7").setFontColor("#2e7d32");
  sheet.getRange("E7").setFontColor(CLR_ACCENT);
  sheet.getRange("F7").setFontColor(CLR_HEADER);
  sheet.getRange("G7").setFontColor(CLR_HEADER);
  sheet.setRowHeight(7, 48);

  sheet.getRange("A8:G8").merge().setBackground(CLR_MED_GRAY);
  sheet.setRowHeight(8, 2);
  sheet.setRowHeight(9, 10);

  // ── MONTHLY LEADERBOARD (rows 10-27) ──
  buildDashboardLeaderboard_(sheet, 10, "TOP 15 \u2014 THIS MONTH",
    '=IFERROR(QUERY(Drops!A:I,"SELECT B, COUNT(B), SUM(G), SUM(D) WHERE MONTH(A)+1 = MONTH(NOW())+1 AND YEAR(A) = YEAR(NOW()) GROUP BY B ORDER BY SUM(G) DESC LIMIT 15 LABEL B \'Player\', COUNT(B) \'Drops\', SUM(G) \'Points\', SUM(D) \'Total GP\'"),"No data for this month yet.")');

  // ── ALL-TIME LEADERBOARD (rows 29-44) ──
  buildDashboardLeaderboard_(sheet, 29, "TOP 15 \u2014 ALL TIME",
    '=IFERROR(QUERY(Drops!A:I,"SELECT B, COUNT(B), SUM(G), SUM(D) WHERE B IS NOT NULL GROUP BY B ORDER BY SUM(G) DESC LIMIT 15 LABEL B \'Player\', COUNT(B) \'Drops\', SUM(G) \'Points\', SUM(D) \'Total GP\'"),"No drops recorded yet.")');

  // ── RECENT DROPS (rows 46-68) ──
  var rs = 46;
  sheet.getRange(rs, 1, 1, 7).merge().setValue("LAST 20 DROPS")
    .setFontSize(13).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground(CLR_SECTION).setHorizontalAlignment("left");
  sheet.setRowHeight(rs, 30);

  sheet.getRange(rs + 1, 1, 1, 7)
    .setValues([["#", "Date", "Player", "Item", "Value (GP)", "Monster", "Points"]])
    .setFontSize(9).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground("#37474f").setHorizontalAlignment("center");

  // Rank numbers — batch write
  var ranks = [];
  for (var r = 0; r < 20; r++) ranks.push([r + 1]);
  sheet.getRange(rs + 2, 1, 20, 1).setValues(ranks)
    .setFontSize(9).setFontColor("#9e9e9e").setHorizontalAlignment("center");

  // QUERY for recent drops
  sheet.getRange(rs + 2, 2).setFormula(
    '=IFERROR(QUERY(SORT(Drops!A:I,1,FALSE),"SELECT Col1, Col2, Col3, Col4, Col5, Col7 LIMIT 20 LABEL Col1 \'\', Col2 \'\', Col3 \'\', Col4 \'\', Col5 \'\', Col7 \'\'"),"No drops yet.")');

  // Format recent drops area
  sheet.getRange(rs + 2, 1, 20, 7).setFontSize(10).setVerticalAlignment("middle");
  sheet.getRange(rs + 2, 2, 20, 1).setNumberFormat("mmm d, yyyy hh:mm");
  sheet.getRange(rs + 2, 5, 20, 1).setNumberFormat("#,##0");
  sheet.getRange(rs + 2, 7, 20, 1).setNumberFormat("#,##0");

  // Alternating rows — batched
  applyAlternatingRows_(sheet, rs + 2, 20, 1, 7);

  sheet.getRange(rs + 22, 1, 1, 7).merge().setBackground(CLR_ACCENT);
  sheet.setRowHeight(rs + 22, 4);
  sheet.getRange(rs, 1, 23, 7)
    .setBorder(true, true, true, true, null, null, "#bdbdbd", SpreadsheetApp.BorderStyle.SOLID);

  sheet.setHiddenGridlines(true);
}

function buildDashboardLeaderboard_(sheet, startRow, title, queryFormula) {
  sheet.getRange(startRow, 1, 1, 7).merge().setValue(title)
    .setFontSize(13).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground(CLR_SECTION).setHorizontalAlignment("left");
  sheet.setRowHeight(startRow, 30);

  sheet.getRange(startRow + 1, 1, 1, 7)
    .setValues([["#", "Player", "Drops", "Points", "Total GP", "", ""]])
    .setFontSize(9).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground("#37474f").setHorizontalAlignment("center");

  // Rank numbers — batched
  var ranks = [];
  for (var i = 0; i < 15; i++) ranks.push([i + 1]);
  sheet.getRange(startRow + 2, 1, 15, 1).setValues(ranks)
    .setFontSize(10).setFontColor("#9e9e9e").setHorizontalAlignment("center");

  sheet.getRange(startRow + 2, 2).setFormula(queryFormula);

  sheet.getRange(startRow + 2, 3, 15, 1).setNumberFormat("#,##0");
  sheet.getRange(startRow + 2, 4, 15, 1).setNumberFormat("#,##0");
  sheet.getRange(startRow + 2, 5, 15, 1).setNumberFormat("#,##0");
  sheet.getRange(startRow + 2, 1, 15, 7).setFontSize(10).setVerticalAlignment("middle");

  applyAlternatingRows_(sheet, startRow + 2, 15, 1, 7);

  sheet.getRange(startRow, 1, 17, 7)
    .setBorder(true, true, true, true, null, null, "#bdbdbd", SpreadsheetApp.BorderStyle.SOLID);
  sheet.setRowHeight(startRow + 17, 8);
}

// ═══════════════════════════════════════════════════════════════
// 3. LEADERBOARD TAB
// ═══════════════════════════════════════════════════════════════

function setupLeaderboardTab_(ss) {
  var sheet = ss.getSheetByName(LB_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(LB_SHEET);
  } else {
    sheet.clear();
    sheet.setConditionalFormatRules([]);
    var bandings = sheet.getBandings();
    for (var b = 0; b < bandings.length; b++) bandings[b].remove();
  }

  ensureSize_(sheet, 210, 8);
  sheet.getRange(1, 1, sheet.getMaxRows(), 8).setFontFamily(FONT_FAMILY);
  sheet.setHiddenGridlines(true);

  var widths = [50, 170, 110, 90, 130, 120, 180, 140];
  for (var i = 0; i < widths.length; i++) sheet.setColumnWidth(i + 1, widths[i]);

  // Section 1: By Points (All Time) — rows 1-50
  buildLeaderboardSection_(sheet, 1,
    "LEADERBOARD \u2014 BY POINTS (ALL TIME)",
    ["Rank", "Player", "Total Points", "Drop Count", "Total GP", "Avg Pts/Drop", "", ""],
    '=IFERROR(QUERY(Drops!A:I,"SELECT B, SUM(G), COUNT(B), SUM(D) WHERE B IS NOT NULL GROUP BY B ORDER BY SUM(G) DESC LIMIT 45 LABEL B \'\', SUM(G) \'\', COUNT(B) \'\', SUM(D) \'\'"),"No data yet.")',
    45, true);

  // Section 2: By Points (This Month) — rows 52-102
  buildLeaderboardSection_(sheet, 52,
    "LEADERBOARD \u2014 BY POINTS (THIS MONTH)",
    ["Rank", "Player", "Total Points", "Drop Count", "Total GP", "Avg Pts/Drop", "", ""],
    '=IFERROR(QUERY(Drops!A:I,"SELECT B, SUM(G), COUNT(B), SUM(D) WHERE MONTH(A)+1 = MONTH(NOW())+1 AND YEAR(A) = YEAR(NOW()) AND B IS NOT NULL GROUP BY B ORDER BY SUM(G) DESC LIMIT 45 LABEL B \'\', SUM(G) \'\', COUNT(B) \'\', SUM(D) \'\'"),"No data for this month.")',
    45, true);

  // Section 3: By GP Value (All Time) — rows 104-154
  buildLeaderboardSection_(sheet, 104,
    "LEADERBOARD \u2014 BY GP VALUE (ALL TIME)",
    ["Rank", "Player", "Total GP", "Drop Count", "Total Points", "Avg GP/Drop", "", ""],
    '=IFERROR(QUERY(Drops!A:I,"SELECT B, SUM(D), COUNT(B), SUM(G) WHERE B IS NOT NULL GROUP BY B ORDER BY SUM(D) DESC LIMIT 45 LABEL B \'\', SUM(D) \'\', COUNT(B) \'\', SUM(G) \'\'"),"No data yet.")',
    45, true);

  // Section 4: Rarest Drops (high points) — rows 156-206
  var rs = 156;
  sheet.getRange(rs, 1, 1, 8).merge().setValue("RAREST DROPS \u2014 HIGH VALUE")
    .setFontSize(14).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground(CLR_HEADER).setHorizontalAlignment("left");
  sheet.setRowHeight(rs, 36);

  sheet.getRange(rs + 1, 1, 1, 8).merge().setBackground(CLR_ACCENT);
  sheet.setRowHeight(rs + 1, 3);

  sheet.getRange(rs + 2, 1, 1, 8)
    .setValues([["#", "Player", "Item", "Monster", "KC", "Points", "Date", ""]])
    .setFontSize(9).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground("#37474f").setHorizontalAlignment("center");

  var ranks = [];
  for (var i = 0; i < 45; i++) ranks.push([i + 1]);
  sheet.getRange(rs + 3, 1, 45, 1).setValues(ranks)
    .setFontSize(9).setFontColor("#9e9e9e").setHorizontalAlignment("center");

  sheet.getRange(rs + 3, 2).setFormula(
    '=IFERROR(QUERY(SORT(Drops!A:I,7,FALSE,1,FALSE),"SELECT Col2, Col3, Col5, Col6, Col7, Col1 WHERE Col7 >= 200 LIMIT 45 LABEL Col2 \'\', Col3 \'\', Col5 \'\', Col6 \'\', Col7 \'\', Col1 \'\'"),"No high-value drops recorded yet.")');

  sheet.getRange(rs + 3, 7, 45, 1).setNumberFormat("mmm d, yyyy");
  sheet.getRange(rs + 3, 5, 45, 1).setNumberFormat("#,##0");
  sheet.getRange(rs + 3, 6, 45, 1).setNumberFormat("#,##0");

  applyAlternatingRows_(sheet, rs + 3, 45, 1, 8);

  // Conditional formatting on points col (6) for rare drops
  var rules = sheet.getConditionalFormatRules();
  var rarePtsRange = sheet.getRange(rs + 3, 6, 45, 1);
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberGreaterThanOrEqualTo(2000)
    .setBackground("#ffcdd2").setFontColor("#c62828").setBold(true).setRanges([rarePtsRange]).build());
  rules.push(SpreadsheetApp.newConditionalFormatRule().whenNumberBetween(500, 1999)
    .setBackground("#ffe0b2").setFontColor("#ef6c00").setBold(true).setRanges([rarePtsRange]).build());
  sheet.setConditionalFormatRules(rules);

  sheet.getRange(rs, 1, 48, 8)
    .setBorder(true, true, true, true, null, null, "#bdbdbd", SpreadsheetApp.BorderStyle.SOLID);
}

function buildLeaderboardSection_(sheet, startRow, title, headers, queryFormula, maxRows, hasAvgCol) {
  sheet.getRange(startRow, 1, 1, 8).merge().setValue(title)
    .setFontSize(14).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground(CLR_HEADER).setHorizontalAlignment("left");
  sheet.setRowHeight(startRow, 36);

  sheet.getRange(startRow + 1, 1, 1, 8).merge().setBackground(CLR_ACCENT);
  sheet.setRowHeight(startRow + 1, 3);

  sheet.getRange(startRow + 2, 1, 1, 8).setValues([headers])
    .setFontSize(9).setFontWeight("bold").setFontColor(CLR_WHITE)
    .setBackground("#37474f").setHorizontalAlignment("center");

  // Batch rank numbers
  var ranks = [];
  for (var i = 0; i < maxRows; i++) ranks.push([i + 1]);
  sheet.getRange(startRow + 3, 1, maxRows, 1).setValues(ranks)
    .setFontSize(9).setFontColor("#9e9e9e").setHorizontalAlignment("center");

  sheet.getRange(startRow + 3, 2).setFormula(queryFormula);

  sheet.getRange(startRow + 3, 3, maxRows, 1).setNumberFormat("#,##0");
  sheet.getRange(startRow + 3, 4, maxRows, 1).setNumberFormat("#,##0");
  sheet.getRange(startRow + 3, 5, maxRows, 1).setNumberFormat("#,##0");

  // Avg column — batch with array of formulas
  if (hasAvgCol) {
    var avgFormulas = [];
    for (var r = 0; r < maxRows; r++) {
      var row = startRow + 3 + r;
      avgFormulas.push(['=IFERROR(C' + row + '/D' + row + ',"")']);
    }
    sheet.getRange(startRow + 3, 6, maxRows, 1).setFormulas(avgFormulas).setNumberFormat("#,##0.0");
  }

  sheet.getRange(startRow + 3, 1, maxRows, 8).setFontSize(10).setVerticalAlignment("middle");
  applyAlternatingRows_(sheet, startRow + 3, maxRows, 1, 8);

  sheet.getRange(startRow, 1, maxRows + 3, 8)
    .setBorder(true, true, true, true, null, null, "#bdbdbd", SpreadsheetApp.BorderStyle.SOLID);

  if (startRow + maxRows + 3 <= sheet.getMaxRows()) {
    sheet.setRowHeight(startRow + maxRows + 3, 8);
  }
}

// ═══════════════════════════════════════════════════════════════
// 4. DROP WHITELIST TAB (note only)
// ═══════════════════════════════════════════════════════════════

function setupWhitelistNote_(ss) {
  var sheet = ss.getSheetByName(WL_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(WL_SHEET);
    sheet.getRange("A1:C1").setValues([["Item", "Source", "Points"]])
      .setFontWeight("bold").setFontFamily(FONT_FAMILY)
      .setFontColor(CLR_WHITE).setBackground(CLR_HEADER);
    sheet.setFrozenRows(1);
    sheet.getRange("A3")
      .setValue("Run populateClanDropWhitelist() from ClanDropWhitelist.gs to populate this sheet.")
      .setFontStyle("italic").setFontColor("#9e9e9e");
  }
}

// ═══════════════════════════════════════════════════════════════
// 5. HISCORES TAB
// ═══════════════════════════════════════════════════════════════

function setupHiscoresTab_(ss) {
  var sheet = ss.getSheetByName(HS_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(HS_SHEET);
  } else {
    // Preserve data — only format the header if it doesn't exist
    var firstCell = sheet.getRange("A1").getValue();
    if (firstCell === "Category") return; // already set up
  }

  var headers = ["Category", "Time", "TimeSeconds", "RSN(s)", "Date", "PartySize", "Submitted"];
  var headerRange = sheet.getRange(1, 1, 1, headers.length);
  headerRange.setValues([headers])
    .setFontFamily(FONT_FAMILY).setFontSize(10).setFontWeight("bold")
    .setFontColor(CLR_WHITE).setBackground(CLR_HEADER)
    .setHorizontalAlignment("center").setVerticalAlignment("middle");
  sheet.setRowHeight(1, 32);
  sheet.setFrozenRows(1);

  // Column widths
  var widths = [200, 120, 100, 200, 80, 80, 170];
  for (var i = 0; i < widths.length; i++) sheet.setColumnWidth(i + 1, widths[i]);

  // Number formats
  sheet.getRange("C2:C").setNumberFormat("0.00");
  sheet.getRange("F2:F").setNumberFormat("0");
}

// ═══════════════════════════════════════════════════════════════
// UTILITIES
// ═══════════════════════════════════════════════════════════════

function ensureSize_(sheet, minRows, minCols) {
  if (sheet.getMaxRows() < minRows) sheet.insertRowsAfter(sheet.getMaxRows(), minRows - sheet.getMaxRows());
  if (sheet.getMaxColumns() < minCols) sheet.insertColumnsAfter(sheet.getMaxColumns(), minCols - sheet.getMaxColumns());
}

/**
 * Apply alternating row backgrounds using a SINGLE batch call.
 */
function applyAlternatingRows_(sheet, startRow, numRows, startCol, numCols) {
  var bgs = [];
  for (var i = 0; i < numRows; i++) {
    var row = [];
    var bg = (i % 2 === 0) ? CLR_ALT_ROW_A : CLR_ALT_ROW_B;
    for (var c = 0; c < numCols; c++) row.push(bg);
    bgs.push(row);
  }
  sheet.getRange(startRow, startCol, numRows, numCols).setBackgrounds(bgs);
}

function reorderSheets_(ss) {
  var order = [DASH_SHEET, DROPS_SHEET, LB_SHEET, HS_SHEET, WL_SHEET];
  for (var i = 0; i < order.length; i++) {
    var s = ss.getSheetByName(order[i]);
    if (s) { ss.setActiveSheet(s); ss.moveActiveSheet(i + 1); }
  }
  var dash = ss.getSheetByName(DASH_SHEET);
  if (dash) ss.setActiveSheet(dash);
}
