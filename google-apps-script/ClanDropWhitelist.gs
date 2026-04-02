/**
 * Clan Drop Whitelist — Comprehensive OSRS Rare Drops
 *
 * Populates the "Drop Whitelist" sheet with all notable rare drops in OSRS.
 * Points = 100 × (Expected Hours to obtain on ironman)
 *        = 100 × (Drop Rate Denominator / Ironman Kills per Hour)
 *
 * Columns: Item | Source | Points | Drop Rate | Ironman KPH | Category
 *
 * The sheet is sortable/filterable by any column. Admins can add/remove
 * rows or change point values directly on the sheet.
 *
 * Run populateClanDropWhitelist() from the script editor.
 */

var WL_SHEET_NAME = "Drop Whitelist";

function populateClanDropWhitelist() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(WL_SHEET_NAME);

  if (!sheet) {
    sheet = ss.insertSheet(WL_SHEET_NAME);
  } else {
    sheet.clear();
  }

  // ── Headers ──
  var headers = ["Item", "Source", "Points", "Drop Rate", "Ironman KPH", "Category"];
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);

  // ── Build drop data ──
  var drops = getAllDrops_();

  // Sort by category then item name
  drops.sort(function(a, b) {
    if (a.category < b.category) return -1;
    if (a.category > b.category) return 1;
    return a.item.localeCompare(b.item);
  });

  if (drops.length > 0) {
    // Write data columns: Item, Source, (skip Points), Drop Rate, KPH, Category
    var values = drops.map(function(d) {
      return [d.item, d.source, "", "1/" + d.rateDenom, d.kph, d.category];
    });
    sheet.getRange(2, 1, values.length, 6).setValues(values);

    // Points column (C) = formula that auto-calculates from Drop Rate (D) and KPH (E)
    // Extracts denominator from "1/X" format, divides by KPH, multiplies by 100
    var formulas = [];
    for (var i = 0; i < drops.length; i++) {
      var row = i + 2;
      formulas.push(['=ROUND((VALUE(SUBSTITUTE(D' + row + ',"1/",""))/E' + row + ')*100)']);
    }
    sheet.getRange(2, 3, formulas.length, 1).setFormulas(formulas);
  }

  // ── Formatting ──
  formatWhitelistSheet_(sheet, drops.length);

  Logger.log("Populated " + drops.length + " drops in whitelist.");
  SpreadsheetApp.flush();
}

// ═══════════════════════════════════════════════════════════════
// FORMATTING
// ═══════════════════════════════════════════════════════════════

function formatWhitelistSheet_(sheet, dataRows) {
  var lastRow = dataRows + 1;
  var lastCol = 6;

  // Freeze header
  sheet.setFrozenRows(1);

  // Header style
  var headerRange = sheet.getRange(1, 1, 1, lastCol);
  headerRange.setBackground("#1a237e");
  headerRange.setFontColor("#ffffff");
  headerRange.setFontWeight("bold");
  headerRange.setFontFamily("Google Sans");
  headerRange.setFontSize(11);
  headerRange.setHorizontalAlignment("center");
  headerRange.setVerticalAlignment("middle");
  sheet.setRowHeight(1, 36);

  // Column widths
  sheet.setColumnWidth(1, 220); // Item
  sheet.setColumnWidth(2, 200); // Source
  sheet.setColumnWidth(3, 80);  // Points
  sheet.setColumnWidth(4, 100); // Drop Rate
  sheet.setColumnWidth(5, 100); // Ironman KPH
  sheet.setColumnWidth(6, 180); // Category

  // Data formatting
  if (dataRows > 0) {
    var dataRange = sheet.getRange(2, 1, dataRows, lastCol);
    dataRange.setFontFamily("Google Sans");
    dataRange.setFontSize(10);
    dataRange.setVerticalAlignment("middle");

    // Points column — number format, center-aligned
    sheet.getRange(2, 3, dataRows, 1).setNumberFormat("#,##0").setHorizontalAlignment("center");
    // Drop Rate — center
    sheet.getRange(2, 4, dataRows, 1).setHorizontalAlignment("center");
    // KPH — center
    sheet.getRange(2, 5, dataRows, 1).setHorizontalAlignment("center");

    // Alternating row colors
    for (var i = 0; i < dataRows; i++) {
      var bg = (i % 2 === 0) ? "#ffffff" : "#e8eaf6";
      sheet.getRange(i + 2, 1, 1, lastCol).setBackground(bg);
    }

    // Conditional formatting on Points column by tier
    var rules = sheet.getConditionalFormatRules();
    var ptsRange = sheet.getRange(2, 3, dataRows, 1);

    // 2000+ = deep red (mega rare, 20+ hrs)
    rules.push(SpreadsheetApp.newConditionalFormatRule()
      .whenNumberGreaterThanOrEqualTo(2000)
      .setBackground("#c62828").setFontColor("#ffffff")
      .setRanges([ptsRange]).build());

    // 1000-1999 = red
    rules.push(SpreadsheetApp.newConditionalFormatRule()
      .whenNumberBetween(1000, 1999)
      .setBackground("#e53935").setFontColor("#ffffff")
      .setRanges([ptsRange]).build());

    // 500-999 = orange
    rules.push(SpreadsheetApp.newConditionalFormatRule()
      .whenNumberBetween(500, 999)
      .setBackground("#ef6c00").setFontColor("#ffffff")
      .setRanges([ptsRange]).build());

    // 200-499 = green
    rules.push(SpreadsheetApp.newConditionalFormatRule()
      .whenNumberBetween(200, 499)
      .setBackground("#2e7d32").setFontColor("#ffffff")
      .setRanges([ptsRange]).build());

    // 100-199 = teal
    rules.push(SpreadsheetApp.newConditionalFormatRule()
      .whenNumberBetween(100, 199)
      .setBackground("#00695c").setFontColor("#ffffff")
      .setRanges([ptsRange]).build());

    // Under 100 = gray
    rules.push(SpreadsheetApp.newConditionalFormatRule()
      .whenNumberLessThan(100)
      .setBackground("#757575").setFontColor("#ffffff")
      .setRanges([ptsRange]).build());

    sheet.setConditionalFormatRules(rules);
  }

  // Add filter view for sorting
  if (dataRows > 0) {
    var filterRange = sheet.getRange(1, 1, lastRow, lastCol);
    var existingFilter = sheet.getFilter();
    if (existingFilter) existingFilter.remove();
    filterRange.createFilter();
  }

  // Protect header row
  sheet.getRange(1, 1, 1, lastCol).protect().setDescription("Header Row").setWarningOnly(true);
}

// ═══════════════════════════════════════════════════════════════
// DROP DATA — ALL OSRS RARE DROPS
// ═══════════════════════════════════════════════════════════════
// Each entry: { item, source, rateDenom, kph, category }
// rateDenom = denominator of drop rate (e.g. 512 for 1/512)
// kph = ironman kills/completions per hour (from WiseOldMan EHB)
// Points = (rateDenom / kph) * 100

function getAllDrops_() {
  var drops = [];

  // Helper to add drops
  function add(item, source, rateDenom, kph, category) {
    drops.push({ item: item, source: source, rateDenom: rateDenom, kph: kph, category: category });
  }

  // ═══════════════════════════════════════════════════════════
  // CHAMBERS OF XERIC
  // Overall: 1/8676 points per unique chance, ~65.7% at 570k pts
  // Solo ironman: ~3.5 raids/hr, ~30k personal pts = ~1/28.9 unique
  // Weights out of 69 total
  // ═══════════════════════════════════════════════════════════
  // Effective solo rate = 1/28.9 × (weight/69)
  var coxKph = 3.5;
  add("Dexterous prayer scroll",    "Chambers of Xeric", 100,  coxKph, "Raids — Chambers of Xeric");
  add("Arcane prayer scroll",       "Chambers of Xeric", 100,  coxKph, "Raids — Chambers of Xeric");
  add("Twisted buckler",            "Chambers of Xeric", 499,  coxKph, "Raids — Chambers of Xeric");
  add("Dragon hunter crossbow",     "Chambers of Xeric", 499,  coxKph, "Raids — Chambers of Xeric");
  add("Dinh's bulwark",             "Chambers of Xeric", 665,  coxKph, "Raids — Chambers of Xeric");
  add("Ancestral hat",              "Chambers of Xeric", 665,  coxKph, "Raids — Chambers of Xeric");
  add("Ancestral robe top",         "Chambers of Xeric", 665,  coxKph, "Raids — Chambers of Xeric");
  add("Ancestral robe bottom",      "Chambers of Xeric", 665,  coxKph, "Raids — Chambers of Xeric");
  add("Dragon claws",               "Chambers of Xeric", 665,  coxKph, "Raids — Chambers of Xeric");
  add("Elder maul",                 "Chambers of Xeric", 998,  coxKph, "Raids — Chambers of Xeric");
  add("Kodai insignia",             "Chambers of Xeric", 998,  coxKph, "Raids — Chambers of Xeric");
  add("Twisted bow",                "Chambers of Xeric", 998,  coxKph, "Raids — Chambers of Xeric");
  add("Olmlet",                     "Chambers of Xeric", 3000, coxKph, "Raids — Chambers of Xeric");

  // ═══════════════════════════════════════════════════════════
  // THEATRE OF BLOOD
  // ~11% unique chance per raid (1/9.1), weights out of 19
  // Solo ironman: ~3.2 raids/hr
  // Effective rate per item = 9.1 × (19/weight)
  // ═══════════════════════════════════════════════════════════
  var tobKph = 3.2;
  add("Avernic defender hilt",      "Theatre of Blood", 22,   tobKph, "Raids — Theatre of Blood");
  add("Ghrazi rapier",              "Theatre of Blood", 86,   tobKph, "Raids — Theatre of Blood");
  add("Sanguinesti staff",          "Theatre of Blood", 86,   tobKph, "Raids — Theatre of Blood");
  add("Justiciar faceguard",        "Theatre of Blood", 86,   tobKph, "Raids — Theatre of Blood");
  add("Justiciar chestguard",       "Theatre of Blood", 86,   tobKph, "Raids — Theatre of Blood");
  add("Justiciar legguards",        "Theatre of Blood", 86,   tobKph, "Raids — Theatre of Blood");
  add("Scythe of vitur",            "Theatre of Blood", 173,  tobKph, "Raids — Theatre of Blood");
  add("Lil' zik",                   "Theatre of Blood", 650,  tobKph, "Raids — Theatre of Blood");

  // ═══════════════════════════════════════════════════════════
  // TOMBS OF AMASCUT (300 invocation)
  // ~1/22.5 unique per solo, weights out of 290
  // Solo ironman: ~3.7 raids/hr
  // Effective rate = 22.5 × (290/weight)
  // ═══════════════════════════════════════════════════════════
  var toaKph = 3.7;
  add("Lightbearer",                "Tombs of Amascut", 93,   toaKph, "Raids — Tombs of Amascut");
  add("Osmumten's fang",            "Tombs of Amascut", 93,   toaKph, "Raids — Tombs of Amascut");
  add("Elidinis' ward",             "Tombs of Amascut", 218,  toaKph, "Raids — Tombs of Amascut");
  add("Masori mask",                "Tombs of Amascut", 326,  toaKph, "Raids — Tombs of Amascut");
  add("Masori body",                "Tombs of Amascut", 326,  toaKph, "Raids — Tombs of Amascut");
  add("Masori chaps",               "Tombs of Amascut", 326,  toaKph, "Raids — Tombs of Amascut");
  add("Tumeken's shadow",           "Tombs of Amascut", 653,  toaKph, "Raids — Tombs of Amascut");
  add("Tumeken's guardian",         "Tombs of Amascut", 2500, toaKph, "Raids — Tombs of Amascut");

  // ═══════════════════════════════════════════════════════════
  // GOD WARS DUNGEON — BANDOS
  // Ironman KPH: 31
  // ═══════════════════════════════════════════════════════════
  var bandosKph = 31;
  add("Bandos chestplate",          "General Graardor", 381,  bandosKph, "God Wars Dungeon");
  add("Bandos tassets",             "General Graardor", 381,  bandosKph, "God Wars Dungeon");
  add("Bandos boots",               "General Graardor", 381,  bandosKph, "God Wars Dungeon");
  add("Bandos hilt",                "General Graardor", 508,  bandosKph, "God Wars Dungeon");
  add("Pet general graardor",       "General Graardor", 5000, bandosKph, "God Wars Dungeon");

  // ═══════════════════════════════════════════════════════════
  // GOD WARS DUNGEON — SARADOMIN
  // Ironman KPH: 30
  // ═══════════════════════════════════════════════════════════
  var saraKph = 30;
  add("Saradomin sword",            "Commander Zilyana", 127,  saraKph, "God Wars Dungeon");
  add("Saradomin's light",          "Commander Zilyana", 254,  saraKph, "God Wars Dungeon");
  add("Armadyl crossbow",           "Commander Zilyana", 508,  saraKph, "God Wars Dungeon");
  add("Saradomin hilt",             "Commander Zilyana", 508,  saraKph, "God Wars Dungeon");
  add("Pet zilyana",                "Commander Zilyana", 5000, saraKph, "God Wars Dungeon");

  // ═══════════════════════════════════════════════════════════
  // GOD WARS DUNGEON — ZAMORAK
  // Ironman KPH: 32
  // ═══════════════════════════════════════════════════════════
  var zamKph = 32;
  add("Steam battlestaff",          "K'ril Tsutsaroth", 127,  zamKph, "God Wars Dungeon");
  add("Zamorakian spear",           "K'ril Tsutsaroth", 127,  zamKph, "God Wars Dungeon");
  add("Staff of the dead",          "K'ril Tsutsaroth", 508,  zamKph, "God Wars Dungeon");
  add("Zamorak hilt",               "K'ril Tsutsaroth", 508,  zamKph, "God Wars Dungeon");
  add("Pet k'ril tsutsaroth",       "K'ril Tsutsaroth", 5000, zamKph, "God Wars Dungeon");

  // ═══════════════════════════════════════════════════════════
  // GOD WARS DUNGEON — ARMADYL
  // Ironman KPH: 30
  // ═══════════════════════════════════════════════════════════
  var armaKph = 30;
  add("Armadyl helmet",             "Kree'arra", 381,  armaKph, "God Wars Dungeon");
  add("Armadyl chestplate",         "Kree'arra", 381,  armaKph, "God Wars Dungeon");
  add("Armadyl chainskirt",         "Kree'arra", 381,  armaKph, "God Wars Dungeon");
  add("Armadyl hilt",               "Kree'arra", 508,  armaKph, "God Wars Dungeon");
  add("Pet kree'arra",              "Kree'arra", 5000, armaKph, "God Wars Dungeon");

  // ═══════════════════════════════════════════════════════════
  // GODSWORD SHARDS (shared across all GWD bosses)
  // ═══════════════════════════════════════════════════════════
  add("Godsword shard 1",           "God Wars Dungeon", 762, 31, "God Wars Dungeon");
  add("Godsword shard 2",           "God Wars Dungeon", 762, 31, "God Wars Dungeon");
  add("Godsword shard 3",           "God Wars Dungeon", 762, 31, "God Wars Dungeon");

  // ═══════════════════════════════════════════════════════════
  // NEX
  // Ironman KPH: 20
  // ═══════════════════════════════════════════════════════════
  var nexKph = 20;
  add("Torva full helm (damaged)",  "Nex", 258,  nexKph, "Nex");
  add("Torva platebody (damaged)",  "Nex", 258,  nexKph, "Nex");
  add("Torva platelegs (damaged)",  "Nex", 258,  nexKph, "Nex");
  add("Nihil horn",                 "Nex", 258,  nexKph, "Nex");
  add("Zaryte vambraces",           "Nex", 172,  nexKph, "Nex");
  add("Ancient hilt",               "Nex", 516,  nexKph, "Nex");
  add("Nexling",                    "Nex", 500,  nexKph, "Nex");

  // ═══════════════════════════════════════════════════════════
  // CORPOREAL BEAST
  // Ironman KPH: 10 (solo spec transfer)
  // ═══════════════════════════════════════════════════════════
  var corpKph = 10;
  add("Spectral sigil",             "Corporeal Beast", 1365, corpKph, "Corporeal Beast");
  add("Arcane sigil",               "Corporeal Beast", 1365, corpKph, "Corporeal Beast");
  add("Elysian sigil",              "Corporeal Beast", 4095, corpKph, "Corporeal Beast");
  add("Spirit shield",              "Corporeal Beast", 64,   corpKph, "Corporeal Beast");
  add("Holy elixir",                "Corporeal Beast", 171,  corpKph, "Corporeal Beast");
  add("Jar of spirits",             "Corporeal Beast", 1000, corpKph, "Corporeal Beast");
  add("Pet dark core",              "Corporeal Beast", 5000, corpKph, "Corporeal Beast");

  // ═══════════════════════════════════════════════════════════
  // NIGHTMARE / PHOSANI'S NIGHTMARE
  // Phosani solo ironman KPH: 9.3
  // ═══════════════════════════════════════════════════════════
  var phosKph = 9.3;
  add("Inquisitor's great helm",    "Phosani's Nightmare", 700,  phosKph, "Nightmare");
  add("Inquisitor's hauberk",       "Phosani's Nightmare", 700,  phosKph, "Nightmare");
  add("Inquisitor's plateskirt",    "Phosani's Nightmare", 700,  phosKph, "Nightmare");
  add("Inquisitor's mace",          "Phosani's Nightmare", 1250, phosKph, "Nightmare");
  add("Nightmare staff",            "Phosani's Nightmare", 533,  phosKph, "Nightmare");
  add("Eldritch orb",               "Phosani's Nightmare", 1600, phosKph, "Nightmare");
  add("Harmonised orb",             "Phosani's Nightmare", 1600, phosKph, "Nightmare");
  add("Volatile orb",               "Phosani's Nightmare", 1600, phosKph, "Nightmare");
  add("Little nightmare",           "Phosani's Nightmare", 1400, phosKph, "Nightmare");
  add("Jar of dreams",              "Phosani's Nightmare", 4000, phosKph, "Nightmare");

  // ═══════════════════════════════════════════════════════════
  // DESERT TREASURE 2 BOSSES
  // ═══════════════════════════════════════════════════════════

  // Duke Sucellus — KPH: 37
  var dukeKph = 37;
  add("Chromium ingot",             "Duke Sucellus",  90,   dukeKph, "Desert Treasure 2");
  add("Virtus mask",                "Duke Sucellus",  720,  dukeKph, "Desert Treasure 2");
  add("Virtus robe top",            "Duke Sucellus",  720,  dukeKph, "Desert Treasure 2");
  add("Virtus robe bottom",         "Duke Sucellus",  720,  dukeKph, "Desert Treasure 2");
  add("Awakener's orb",             "Duke Sucellus",  48,   dukeKph, "Desert Treasure 2");
  add("Baron",                      "Duke Sucellus",  2500, dukeKph, "Desert Treasure 2");

  // The Leviathan — KPH: 27
  var levKph = 27;
  add("Leviathan's lure",           "The Leviathan",  96,   levKph, "Desert Treasure 2");
  add("Virtus mask",                "The Leviathan",  768,  levKph, "Desert Treasure 2");
  add("Virtus robe top",            "The Leviathan",  768,  levKph, "Desert Treasure 2");
  add("Virtus robe bottom",         "The Leviathan",  768,  levKph, "Desert Treasure 2");
  add("Awakener's orb",             "The Leviathan",  53,   levKph, "Desert Treasure 2");
  add("Lil'viathan",                "The Leviathan",  2500, levKph, "Desert Treasure 2");

  // The Whisperer — KPH: 21
  var whispKph = 21;
  add("Siren's staff",              "The Whisperer",  64,   whispKph, "Desert Treasure 2");
  add("Virtus mask",                "The Whisperer",  512,  whispKph, "Desert Treasure 2");
  add("Virtus robe top",            "The Whisperer",  512,  whispKph, "Desert Treasure 2");
  add("Virtus robe bottom",         "The Whisperer",  512,  whispKph, "Desert Treasure 2");
  add("Awakener's orb",             "The Whisperer",  34,   whispKph, "Desert Treasure 2");
  add("Wisp",                       "The Whisperer",  2000, whispKph, "Desert Treasure 2");

  // Vardorvis — KPH: 37
  var vardKph = 37;
  add("Executioner's axe head",     "Vardorvis",      136,  vardKph, "Desert Treasure 2");
  add("Virtus mask",                "Vardorvis",      1088, vardKph, "Desert Treasure 2");
  add("Virtus robe top",            "Vardorvis",      1088, vardKph, "Desert Treasure 2");
  add("Virtus robe bottom",         "Vardorvis",      1088, vardKph, "Desert Treasure 2");
  add("Awakener's orb",             "Vardorvis",      80,   vardKph, "Desert Treasure 2");
  add("Butch",                      "Vardorvis",      3000, vardKph, "Desert Treasure 2");

  // ═══════════════════════════════════════════════════════════
  // ALCHEMICAL HYDRA
  // Ironman KPH: 29
  // ═══════════════════════════════════════════════════════════
  var hydraKph = 29;
  add("Hydra's claw",               "Alchemical Hydra", 1001, hydraKph, "Slayer Bosses");
  add("Hydra leather",              "Alchemical Hydra", 514,  hydraKph, "Slayer Bosses");
  add("Hydra tail",                 "Alchemical Hydra", 513,  hydraKph, "Slayer Bosses");
  add("Hydra's eye",                "Alchemical Hydra", 181,  hydraKph, "Slayer Bosses");
  add("Hydra's fang",               "Alchemical Hydra", 181,  hydraKph, "Slayer Bosses");
  add("Hydra's heart",              "Alchemical Hydra", 181,  hydraKph, "Slayer Bosses");
  add("Jar of chemicals",           "Alchemical Hydra", 2000, hydraKph, "Slayer Bosses");
  add("Ikkle hydra",                "Alchemical Hydra", 3000, hydraKph, "Slayer Bosses");

  // ═══════════════════════════════════════════════════════════
  // CERBERUS
  // Ironman KPH: 54
  // ═══════════════════════════════════════════════════════════
  var cerbKph = 54;
  add("Primordial crystal",         "Cerberus", 520,  cerbKph, "Slayer Bosses");
  add("Pegasian crystal",           "Cerberus", 520,  cerbKph, "Slayer Bosses");
  add("Eternal crystal",            "Cerberus", 520,  cerbKph, "Slayer Bosses");
  add("Smouldering stone",          "Cerberus", 520,  cerbKph, "Slayer Bosses");
  add("Jar of souls",               "Cerberus", 2000, cerbKph, "Slayer Bosses");
  add("Hellpuppy",                  "Cerberus", 3000, cerbKph, "Slayer Bosses");

  // ═══════════════════════════════════════════════════════════
  // THERMONUCLEAR SMOKE DEVIL
  // Ironman KPH: 100
  // ═══════════════════════════════════════════════════════════
  var thermoKph = 100;
  add("Occult necklace",            "Thermonuclear Smoke Devil", 350,  thermoKph, "Slayer Bosses");
  add("Smoke battlestaff",          "Thermonuclear Smoke Devil", 512,  thermoKph, "Slayer Bosses");
  add("Dragon chainbody",           "Thermonuclear Smoke Devil", 2000, thermoKph, "Slayer Bosses");
  add("Jar of smoke",               "Thermonuclear Smoke Devil", 2000, thermoKph, "Slayer Bosses");
  add("Pet smoke devil",            "Thermonuclear Smoke Devil", 3000, thermoKph, "Slayer Bosses");

  // ═══════════════════════════════════════════════════════════
  // KRAKEN
  // Ironman KPH: 90
  // ═══════════════════════════════════════════════════════════
  var krakenKph = 90;
  add("Kraken tentacle",            "Kraken", 400,  krakenKph, "Slayer Bosses");
  add("Trident of the seas (full)", "Kraken", 512,  krakenKph, "Slayer Bosses");
  add("Jar of dirt",                "Kraken", 1000, krakenKph, "Slayer Bosses");
  add("Pet kraken",                 "Kraken", 3000, krakenKph, "Slayer Bosses");

  // ═══════════════════════════════════════════════════════════
  // GROTESQUE GUARDIANS
  // Ironman KPH: 34
  // 2 rolls per kill on unique table
  // ═══════════════════════════════════════════════════════════
  var ggKph = 34;
  add("Granite maul",               "Grotesque Guardians", 125,  ggKph, "Slayer Bosses");
  add("Granite gloves",             "Grotesque Guardians", 250,  ggKph, "Slayer Bosses");
  add("Granite ring",               "Grotesque Guardians", 250,  ggKph, "Slayer Bosses");
  add("Granite hammer",             "Grotesque Guardians", 375,  ggKph, "Slayer Bosses");
  add("Black tourmaline core",      "Grotesque Guardians", 500,  ggKph, "Slayer Bosses");
  add("Jar of stone",               "Grotesque Guardians", 5000, ggKph, "Slayer Bosses");
  add("Noon",                       "Grotesque Guardians", 3000, ggKph, "Slayer Bosses");

  // ═══════════════════════════════════════════════════════════
  // ABYSSAL SIRE
  // Ironman KPH: 44
  // 1/100 for unsired, then sub-table
  // ═══════════════════════════════════════════════════════════
  var sireKph = 44;
  add("Bludgeon claw",              "Abyssal Sire", 492,  sireKph, "Slayer Bosses");
  add("Bludgeon spine",             "Abyssal Sire", 492,  sireKph, "Slayer Bosses");
  add("Bludgeon axon",              "Abyssal Sire", 492,  sireKph, "Slayer Bosses");
  add("Abyssal dagger",             "Abyssal Sire", 492,  sireKph, "Slayer Bosses");
  add("Jar of miasma",              "Abyssal Sire", 492,  sireKph, "Slayer Bosses");
  add("Abyssal orphan",             "Abyssal Sire", 2560, sireKph, "Slayer Bosses");

  // ═══════════════════════════════════════════════════════════
  // ZULRAH
  // Ironman KPH: 42
  // 2 rolls per kill (effective 1/512 per item)
  // ═══════════════════════════════════════════════════════════
  var zulrahKph = 42;
  add("Tanzanite fang",             "Zulrah", 512,   zulrahKph, "Bosses");
  add("Magic fang",                 "Zulrah", 512,   zulrahKph, "Bosses");
  add("Serpentine visage",          "Zulrah", 512,   zulrahKph, "Bosses");
  add("Uncut onyx",                 "Zulrah", 512,   zulrahKph, "Bosses");
  add("Tanzanite mutagen",          "Zulrah", 6554,  zulrahKph, "Bosses");
  add("Magma mutagen",              "Zulrah", 6554,  zulrahKph, "Bosses");
  add("Jar of swamp",               "Zulrah", 3000,  zulrahKph, "Bosses");
  add("Pet snakeling",              "Zulrah", 4000,  zulrahKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // VORKATH
  // Ironman KPH: 34
  // ═══════════════════════════════════════════════════════════
  var vorkKph = 34;
  add("Skeletal visage",            "Vorkath", 5000, vorkKph, "Bosses");
  add("Draconic visage",            "Vorkath", 5000, vorkKph, "Bosses");
  add("Dragonbone necklace",        "Vorkath", 1000, vorkKph, "Bosses");
  add("Jar of decay",               "Vorkath", 3000, vorkKph, "Bosses");
  add("Vorki",                      "Vorkath", 3000, vorkKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // CORRUPTED GAUNTLET
  // Ironman KPH: 7.2
  // ═══════════════════════════════════════════════════════════
  var cgKph = 7.2;
  add("Enhanced crystal weapon seed", "Corrupted Gauntlet", 400, cgKph, "Corrupted Gauntlet");
  add("Crystal armour seed",          "Corrupted Gauntlet", 50,  cgKph, "Corrupted Gauntlet");
  add("Crystal weapon seed",          "Corrupted Gauntlet", 50,  cgKph, "Corrupted Gauntlet");
  add("Youngllef",                    "Corrupted Gauntlet", 800, cgKph, "Corrupted Gauntlet");

  // ═══════════════════════════════════════════════════════════
  // PHANTOM MUSPAH
  // Ironman KPH: 27
  // ═══════════════════════════════════════════════════════════
  var muspahKph = 27;
  add("Ancient icon",               "Phantom Muspah", 50,   muspahKph, "Bosses");
  add("Venator shard",              "Phantom Muspah", 100,  muspahKph, "Bosses");
  add("Muphin",                     "Phantom Muspah", 2500, muspahKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // ARAXXOR
  // Ironman KPH: 38
  // ═══════════════════════════════════════════════════════════
  var araxxKph = 38;
  add("Noxious blade",              "Araxxor", 200,  araxxKph, "Bosses");
  add("Noxious point",              "Araxxor", 200,  araxxKph, "Bosses");
  add("Noxious pommel",             "Araxxor", 200,  araxxKph, "Bosses");
  add("Araxyte fang",               "Araxxor", 600,  araxxKph, "Bosses");
  add("Nid",                        "Araxxor", 3000, araxxKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // DAGANNOTH KINGS
  // Ironman KPH: 100 (per king)
  // ═══════════════════════════════════════════════════════════
  var dksKph = 100;
  add("Berserker ring",             "Dagannoth Rex",     128, dksKph, "Dagannoth Kings");
  add("Warrior ring",               "Dagannoth Rex",     128, dksKph, "Dagannoth Kings");
  add("Dragon axe",                 "Dagannoth Rex",     128, dksKph, "Dagannoth Kings");
  add("Pet dagannoth rex",          "Dagannoth Rex",     5000, dksKph, "Dagannoth Kings");
  add("Archers ring",               "Dagannoth Supreme", 128, dksKph, "Dagannoth Kings");
  add("Seercull",                   "Dagannoth Supreme", 128, dksKph, "Dagannoth Kings");
  add("Dragon axe",                 "Dagannoth Supreme", 128, dksKph, "Dagannoth Kings");
  add("Pet dagannoth supreme",      "Dagannoth Supreme", 5000, dksKph, "Dagannoth Kings");
  add("Seers ring",                 "Dagannoth Prime",   128, dksKph, "Dagannoth Kings");
  add("Mud battlestaff",            "Dagannoth Prime",   128, dksKph, "Dagannoth Kings");
  add("Dragon axe",                 "Dagannoth Prime",   128, dksKph, "Dagannoth Kings");
  add("Pet dagannoth prime",        "Dagannoth Prime",   5000, dksKph, "Dagannoth Kings");

  // ═══════════════════════════════════════════════════════════
  // KALPHITE QUEEN
  // Ironman KPH: 37
  // ═══════════════════════════════════════════════════════════
  var kqKph = 37;
  add("Dragon chainbody",           "Kalphite Queen", 128,  kqKph, "Bosses");
  add("Dragon 2h sword",            "Kalphite Queen", 256,  kqKph, "Bosses");
  add("Jar of sand",                "Kalphite Queen", 2000, kqKph, "Bosses");
  add("Kalphite princess",          "Kalphite Queen", 3000, kqKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // KING BLACK DRAGON
  // Ironman KPH: 75
  // ═══════════════════════════════════════════════════════════
  var kbdKph = 75;
  add("Draconic visage",            "King Black Dragon", 5000, kbdKph, "Bosses");
  add("Dragon pickaxe",             "King Black Dragon", 1000, kbdKph, "Bosses");
  add("Prince black dragon",        "King Black Dragon", 3000, kbdKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // GIANT MOLE
  // Ironman KPH: 97
  // ═══════════════════════════════════════════════════════════
  add("Baby mole",                  "Giant Mole", 3000, 97, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // SARACHNIS
  // Ironman KPH: 67
  // ═══════════════════════════════════════════════════════════
  var saraKph2 = 67;
  add("Sarachnis cudgel",           "Sarachnis", 384,  saraKph2, "Bosses");
  add("Jar of eyes",                "Sarachnis", 2000, saraKph2, "Bosses");
  add("Sraracha",                   "Sarachnis", 3000, saraKph2, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // WILDERNESS BOSSES
  // ═══════════════════════════════════════════════════════════

  // Callisto — KPH: 142 (full boss), Artio: 50 (demi-boss)
  add("Tyrannical ring",            "Callisto",  512,  142, "Wilderness Bosses");
  add("Voidwaker hilt",             "Callisto",  360,  142, "Wilderness Bosses");
  add("Dragon pickaxe",             "Callisto",  256,  142, "Wilderness Bosses");
  add("Callisto cub",               "Callisto",  1500, 142, "Wilderness Bosses");

  // Vet'ion — KPH: 39 (full boss), Calvar'ion: 45 (demi-boss)
  add("Ring of the gods",           "Vet'ion",   512,  39, "Wilderness Bosses");
  add("Dragon pickaxe",             "Vet'ion",   256,  39, "Wilderness Bosses");
  add("Vet'ion jr.",                "Vet'ion",   1500, 39, "Wilderness Bosses");

  // Venenatis — KPH: 80 (full boss), Spindel: 50 (demi-boss)
  add("Treasonous ring",            "Venenatis", 512,  80, "Wilderness Bosses");
  add("Voidwaker gem",              "Venenatis", 360,  80, "Wilderness Bosses");
  add("Dragon pickaxe",             "Venenatis", 256,  80, "Wilderness Bosses");
  add("Venenatis spiderling",       "Venenatis", 1500, 80, "Wilderness Bosses");

  // Chaos Elemental — KPH: 48
  add("Dragon pickaxe",             "Chaos Elemental", 256, 48, "Wilderness Bosses");
  add("Pet chaos elemental",        "Chaos Elemental", 300, 48, "Wilderness Bosses");

  // Scorpia — KPH: 80
  add("Odium shard 3",              "Scorpia", 256, 80, "Wilderness Bosses");
  add("Malediction shard 3",        "Scorpia", 256, 80, "Wilderness Bosses");
  add("Scorpia's offspring",        "Scorpia", 2016, 80, "Wilderness Bosses");

  // ═══════════════════════════════════════════════════════════
  // BARROWS
  // Ironman KPH: 22 (chests)
  // 1/17 for ANY piece per chest (killing all 6 brothers)
  // All 24 pieces equally weighted — use "any unique" rate
  // ═══════════════════════════════════════════════════════════
  var barrowsKph = 22;
  var barrowsRate = 17; // ~1/17 for any barrows piece
  var barrowsBros = [
    ["Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt", "Ahrim's staff"],
    ["Dharok's helm", "Dharok's platebody", "Dharok's platelegs", "Dharok's greataxe"],
    ["Guthan's helm", "Guthan's platebody", "Guthan's chainskirt", "Guthan's warspear"],
    ["Karil's coif", "Karil's leathertop", "Karil's leatherskirt", "Karil's crossbow"],
    ["Torag's helm", "Torag's platebody", "Torag's platelegs", "Torag's hammers"],
    ["Verac's helm", "Verac's brassard", "Verac's plateskirt", "Verac's flail"]
  ];
  for (var b = 0; b < barrowsBros.length; b++) {
    for (var p = 0; p < barrowsBros[b].length; p++) {
      add(barrowsBros[b][p], "Barrows", barrowsRate, barrowsKph, "Barrows");
    }
  }

  // ═══════════════════════════════════════════════════════════
  // SKOTIZO
  // Ironman KPH: 38
  // ═══════════════════════════════════════════════════════════
  var skotizoKph = 38;
  add("Dark claw",                  "Skotizo", 25,   skotizoKph, "Bosses");
  add("Uncut onyx",                 "Skotizo", 1000, skotizoKph, "Bosses");
  add("Jar of darkness",            "Skotizo", 2500, skotizoKph, "Bosses");
  add("Skotos",                     "Skotizo", 65,   skotizoKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // INFERNO / FIGHT CAVES
  // ═══════════════════════════════════════════════════════════
  add("Jad pet (TzRek-Jad)",        "TzTok-Jad",  200, 2.2, "Minigames");
  add("Zuk pet (JalRek-Jad)",       "TzKal-Zuk",  100, 1.0, "Minigames");

  // ═══════════════════════════════════════════════════════════
  // SOL HEREDIT (Fortis Colosseum)
  // Ironman KPH: 2.7
  // ═══════════════════════════════════════════════════════════
  var solKph = 2.7;
  add("Dizana's quiver",            "Sol Heredit", 150,  solKph, "Minigames");
  add("Tonalztics of ralos",        "Sol Heredit", 100,  solKph, "Minigames");
  add("Smol heredit",               "Sol Heredit", 200,  solKph, "Minigames");
  add("Sunfire splinters",          "Sol Heredit", 3,    solKph, "Minigames");

  // ═══════════════════════════════════════════════════════════
  // DOOM OF MOKHAIOTL (Perilous Moons)
  // Ironman KPH: 18
  // 1/56 per boss, 1/14 completing all 3
  // ═══════════════════════════════════════════════════════════
  var doomKph = 18;
  add("Eclipse atlatl",             "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Eclipse moon helm",          "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Eclipse moon chestplate",    "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Eclipse moon tassets",       "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Blue moon helm",             "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Blue moon chestplate",       "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Blue moon tassets",          "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Blood moon helm",            "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Blood moon chestplate",      "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Blood moon tassets",         "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Dual macuahuitl",            "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");
  add("Sulphur blades",             "Perilous Moons", 56,  doomKph, "Raids — Perilous Moons");

  // ═══════════════════════════════════════════════════════════
  // HUEYCOATL
  // Ironman KPH: 20
  // ═══════════════════════════════════════════════════════════
  var hueyKph = 20;
  add("Hueycoatl hide",             "Hueycoatl", 30,   hueyKph, "Bosses");
  add("Huntsman's kit",             "Hueycoatl", 400,  hueyKph, "Bosses");
  add("Huey",                       "Hueycoatl", 3000, hueyKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // BRYOPHYTA & OBOR
  // ═══════════════════════════════════════════════════════════
  add("Bryophyta's essence",        "Bryophyta", 118, 9, "Free-to-Play Bosses");
  add("Obor's club",                "Obor",      118, 12, "Free-to-Play Bosses");

  // ═══════════════════════════════════════════════════════════
  // TEMPOROSS
  // ═══════════════════════════════════════════════════════════
  add("Tome of water",              "Tempoross", 1600, 15, "Skilling Bosses");
  add("Tiny tempor",                "Tempoross", 8000, 15, "Skilling Bosses");

  // ═══════════════════════════════════════════════════════════
  // WINTERTODT
  // ═══════════════════════════════════════════════════════════
  add("Tome of fire",               "Wintertodt", 1000, 15, "Skilling Bosses");
  add("Phoenix",                    "Wintertodt", 5000, 15, "Skilling Bosses");
  add("Dragon axe",                 "Wintertodt", 10000, 15, "Skilling Bosses");

  // ═══════════════════════════════════════════════════════════
  // ZALCANO
  // ═══════════════════════════════════════════════════════════
  add("Crystal tool seed",          "Zalcano", 200,  30, "Skilling Bosses");
  add("Zalcano shard",              "Zalcano", 1500, 30, "Skilling Bosses");
  add("Smolcano",                   "Zalcano", 2250, 30, "Skilling Bosses");

  // ═══════════════════════════════════════════════════════════
  // HESPORI
  // ═══════════════════════════════════════════════════════════
  add("Bottomless compost bucket",  "Hespori", 35,   50, "Skilling Bosses");
  add("Tangleroot",                 "Hespori", 9000, 50, "Skilling Bosses");

  // ═══════════════════════════════════════════════════════════
  // ROYAL TITANS
  // Ironman KPH: 55
  // ═══════════════════════════════════════════════════════════
  var royalKph = 55;
  add("Crown of the eye",           "Royal Titans", 128,  royalKph, "Bosses");
  add("Titan's sigil",              "Royal Titans", 400,  royalKph, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // NOTABLE SLAYER DROPS (non-boss)
  // ═══════════════════════════════════════════════════════════
  add("Abyssal whip",               "Abyssal demon",    512,  250, "Slayer Drops");
  add("Dark bow",                   "Dark beast",       512,  150, "Slayer Drops");
  add("Imbued heart",               "Superior slayer",  880,  2,   "Slayer Drops");
  add("Eternal gem",                "Superior slayer",  880,  2,   "Slayer Drops");
  add("Dragon full helm",           "Mithril dragon",   32768, 70, "Slayer Drops");
  add("Basilisk jaw",               "Basilisk Knight",  1000, 40,  "Slayer Drops");
  add("Drake's tooth",              "Drake",            2560, 100, "Slayer Drops");
  add("Drake's claw",               "Drake",            512,  100, "Slayer Drops");
  add("Draconic visage",            "Skeletal Wyvern",  10000, 80, "Slayer Drops");
  add("Leaf-bladed battleaxe",      "Kurask",           384,  80,  "Slayer Drops");
  add("Mystic robe top (dark)",     "Dark beast",       512,  150, "Slayer Drops");

  // ═══════════════════════════════════════════════════════════
  // SCURRIUS
  // Ironman KPH: 60
  // ═══════════════════════════════════════════════════════════
  add("Scurrius' spine",            "Scurrius", 25,   60, "Bosses");
  add("Scurry",                     "Scurrius", 3000, 60, "Bosses");

  // ═══════════════════════════════════════════════════════════
  // YAMA (Perilous Moons)
  // Ironman KPH: 18
  // ═══════════════════════════════════════════════════════════
  // Already covered under Perilous Moons / Doom of Mokhaiotl

  return drops;
}

// ═══════════════════════════════════════════════════════════════
// UPDATE HELPERS — For adding items via the plugin admin panel
// ═══════════════════════════════════════════════════════════════

/**
 * Add or update a single item on the whitelist.
 * Called from the admin panel or manually.
 */
function addWhitelistItem(itemName, source, points, dropRate, kph, category) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(WL_SHEET_NAME);
  if (!sheet) {
    Logger.log("Drop Whitelist sheet not found. Run populateClanDropWhitelist() first.");
    return;
  }

  var data = sheet.getDataRange().getValues();
  var nameLower = itemName.toLowerCase().trim();

  // Check if item already exists — update it
  for (var i = 1; i < data.length; i++) {
    if ((data[i][0] || "").toString().toLowerCase().trim() === nameLower) {
      var row = i + 1;
      sheet.getRange(row, 1, 1, 6).setValues([[itemName, source, "", dropRate || "", kph || "", category || ""]]);
      // Points formula auto-calculates from Drop Rate and KPH
      sheet.getRange(row, 3).setFormula('=ROUND((VALUE(SUBSTITUTE(D' + row + ',"1/",""))/E' + row + ')*100)');
      Logger.log("Updated: " + itemName);
      return;
    }
  }

  // Add new row
  var newRow = sheet.getLastRow() + 1;
  sheet.appendRow([itemName, source, "", dropRate || "", kph || "", category || ""]);
  // Set Points formula on the new row
  sheet.getRange(newRow, 3).setFormula('=ROUND((VALUE(SUBSTITUTE(D' + newRow + ',"1/",""))/E' + newRow + ')*100)');
  Logger.log("Added: " + itemName);
}

/**
 * Remove an item from the whitelist by name.
 */
function removeWhitelistItem(itemName) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(WL_SHEET_NAME);
  if (!sheet) return;

  var data = sheet.getDataRange().getValues();
  var nameLower = itemName.toLowerCase().trim();

  for (var i = data.length - 1; i >= 1; i--) {
    if ((data[i][0] || "").toString().toLowerCase().trim() === nameLower) {
      sheet.deleteRow(i + 1);
      Logger.log("Removed: " + itemName);
      return;
    }
  }
}
