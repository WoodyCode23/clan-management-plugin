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

  var teamResult = ui.prompt(
    "Bingo Setup",
    "How many teams? (2-20, default 2):",
    ui.ButtonSet.OK_CANCEL
  );

  if (teamResult.getSelectedButton() !== ui.Button.OK) return;
  var teamCount = parseInt(teamResult.getResponseText()) || 2;
  if (teamCount < 2 || teamCount > 20) {
    ui.alert("Team count must be between 2 and 20.");
    return;
  }

  var ss = SpreadsheetApp.getActiveSpreadsheet();

  // ── Config tab ──
  createConfigTab_(ss, gridSize);

  // ── Tiles tab ──
  createTilesTab_(ss, gridSize);

  // ── Teams tab ──
  createTeamsTab_(ss, teamCount);

  // ── Roster tab ──
  createRosterTab_(ss);

  // ── Whitelist tab ──
  createWhitelistTab_(ss);

  // ── Droplog tab ──
  createDroplogTab_(ss);

  // ── Bounties tab ──
  createBountiesTab_(ss);

  // ── Board tab (visual reference) ──
  createBoardTab_(ss, gridSize);

  // ── Item Database tab (hidden, for autocomplete) ──
  createItemDatabaseTab_(ss);

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

function createTeamsTab_(ss, teamCount) {
  var sheet = ss.getSheetByName("Teams");
  if (!sheet) sheet = ss.insertSheet("Teams");
  sheet.clear();

  sheet.getRange("A1:B1").setValues([["Code", "Name"]]);
  sheet.getRange("A1:B1").setFontWeight("bold");

  // Generate placeholder teams
  var teams = [];
  for (var i = 1; i <= teamCount; i++) {
    teams.push(["TEAM" + i, "Team " + i]);
  }
  sheet.getRange(2, 1, teams.length, 2).setValues(teams);
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

function createBoardTab_(ss, gridSize) {
  var sheet = ss.getSheetByName("Board");
  if (!sheet) sheet = ss.insertSheet("Board");
  sheet.clear();

  // Row 1: header with column letters
  var headerRow = [""];
  for (var c = 0; c < gridSize; c++) {
    headerRow.push(String.fromCharCode(65 + c));
  }
  var headerRange = sheet.getRange(1, 1, 1, headerRow.length);
  headerRange.setValues([headerRow]);
  headerRange.setFontWeight("bold");
  headerRange.setHorizontalAlignment("center");

  // Grid cells: row numbers in column A, formulas in grid
  for (var r = 0; r < gridSize; r++) {
    // Row number label in column A
    var labelCell = sheet.getRange(r + 2, 1);
    labelCell.setValue(r + 1);
    labelCell.setFontWeight("bold");
    labelCell.setHorizontalAlignment("center");

    for (var c = 0; c < gridSize; c++) {
      var tileCode = String.fromCharCode(65 + c) + (r + 1);
      var formula = '=IFERROR(INDEX(Tiles!B:B, MATCH("' + tileCode + '", Tiles!A:A, 0)), "")';
      var cell = sheet.getRange(r + 2, c + 2);
      cell.setFormula(formula);
      cell.setHorizontalAlignment("center");
      cell.setVerticalAlignment("middle");
      cell.setNote(tileCode);
    }
  }

  // Formatting
  var gridRange = sheet.getRange(1, 1, gridSize + 1, gridSize + 1);
  gridRange.setBackground("#f0f0f0");
  gridRange.setBorder(true, true, true, true, true, true);

  // Size columns and rows for readability
  for (var c = 1; c <= gridSize + 1; c++) {
    sheet.setColumnWidth(c, c === 1 ? 40 : 150);
  }
  for (var r = 1; r <= gridSize + 1; r++) {
    sheet.setRowHeight(r, r === 1 ? 25 : 40);
  }
}

function createItemDatabaseTab_(ss) {
  var sheet = ss.getSheetByName("Item Database");
  if (!sheet) sheet = ss.insertSheet("Item Database");
  sheet.clear();

  sheet.getRange("A1:B1").setValues([["Item Name", "Source"]]);
  sheet.getRange("A1:B1").setFontWeight("bold");

  var items = [];

  // ── Chambers of Xeric (CoX) ──
  var cox = ["Twisted bow", "Kodai insignia", "Elder maul", "Dragon claws",
    "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom",
    "Dexterous prayer scroll", "Arcane prayer scroll", "Dinh's bulwark",
    "Dragon hunter crossbow", "Twisted buckler", "Tome of water (ornament kit)",
    "Olmlet", "Jar of stone"];
  for (var i = 0; i < cox.length; i++) items.push([cox[i], "Chambers of Xeric"]);

  // ── Theatre of Blood (ToB) ──
  var tob = ["Scythe of vitur", "Ghrazi rapier", "Sanguinesti staff",
    "Justiciar faceguard", "Justiciar chestguard", "Justiciar legguards",
    "Avernic defender hilt", "Lil' zik", "Jar of darkness",
    "Holy ornament kit", "Sanguine ornament kit"];
  for (var i = 0; i < tob.length; i++) items.push([tob[i], "Theatre of Blood"]);

  // ── Tombs of Amascut (ToA) ──
  var toa = ["Tumeken's shadow", "Osmumten's fang", "Masori mask", "Masori body",
    "Masori chaps", "Lightbearer", "Elidinis' ward", "Jewel of the sun",
    "Jar of amascut"];
  for (var i = 0; i < toa.length; i++) items.push([toa[i], "Tombs of Amascut"]);

  // ── General Graardor (Bandos) ──
  var bandos = ["Bandos chestplate", "Bandos tassets", "Bandos boots", "Bandos hilt",
    "Pet general graardor", "Jar of spirits"];
  for (var i = 0; i < bandos.length; i++) items.push([bandos[i], "General Graardor"]);

  // ── Kree'arra (Armadyl) ──
  var arma = ["Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt",
    "Armadyl hilt", "Pet kree'arra", "Jar of wind"];
  for (var i = 0; i < arma.length; i++) items.push([arma[i], "Kree'arra"]);

  // ── Commander Zilyana (Saradomin) ──
  var sara = ["Saradomin sword", "Armadyl crossbow", "Saradomin hilt",
    "Saradomin's light", "Pet zilyana", "Jar of light"];
  for (var i = 0; i < sara.length; i++) items.push([sara[i], "Commander Zilyana"]);

  // ── K'ril Tsutsaroth (Zamorak) ──
  var zammy = ["Staff of the dead", "Zamorakian spear", "Zamorak hilt",
    "Steam battlestaff", "Pet k'ril tsutsaroth", "Jar of smoke"];
  for (var i = 0; i < zammy.length; i++) items.push([zammy[i], "K'ril Tsutsaroth"]);

  // ── Nex ──
  var nex = ["Torva full helm", "Torva platebody", "Torva platelegs",
    "Zaryte vambraces", "Nihil horn", "Ancient hilt", "Nexling", "Jar of shadows"];
  for (var i = 0; i < nex.length; i++) items.push([nex[i], "Nex"]);

  // ── Nightmare / Phosani's Nightmare ──
  var nightmare = ["Nightmare staff", "Inquisitor's great helm", "Inquisitor's hauberk",
    "Inquisitor's plateskirt", "Inquisitor's mace", "Eldritch orb",
    "Harmonised orb", "Volatile orb", "Parasitic egg", "Jar of dreams"];
  for (var i = 0; i < nightmare.length; i++) items.push([nightmare[i], "Nightmare"]);

  // ── Corporeal Beast ──
  var corp = ["Spectral sigil", "Arcane sigil", "Elysian sigil", "Spirit shield",
    "Holy elixir", "Pet dark core", "Jar of spirits"];
  for (var i = 0; i < corp.length; i++) items.push([corp[i], "Corporeal Beast"]);

  // ── Cerberus ──
  var cerb = ["Primordial crystal", "Pegasian crystal", "Eternal crystal",
    "Smouldering stone", "Hellpuppy", "Jar of souls"];
  for (var i = 0; i < cerb.length; i++) items.push([cerb[i], "Cerberus"]);

  // ── Alchemical Hydra ──
  var hydra = ["Hydra's claw", "Hydra tail", "Hydra leather", "Hydra's eye",
    "Hydra's fang", "Hydra's heart", "Ikkle hydra", "Jar of chemicals",
    "Brimstone ring (assembled)"];
  for (var i = 0; i < hydra.length; i++) items.push([hydra[i], "Alchemical Hydra"]);

  // ── Kraken ──
  var kraken = ["Trident of the seas", "Kraken tentacle", "Pet kraken", "Jar of dirt"];
  for (var i = 0; i < kraken.length; i++) items.push([kraken[i], "Kraken"]);

  // ── Thermonuclear Smoke Devil ──
  var thermy = ["Occult necklace", "Smoke battlestaff", "Dragon chainbody",
    "Pet smoke devil", "Jar of smoke"];
  for (var i = 0; i < thermy.length; i++) items.push([thermy[i], "Thermonuclear Smoke Devil"]);

  // ── Abyssal Sire ──
  var sire = ["Unsired", "Abyssal dagger", "Abyssal bludgeon", "Abyssal orphan",
    "Jar of miasma"];
  for (var i = 0; i < sire.length; i++) items.push([sire[i], "Abyssal Sire"]);

  // ── Grotesque Guardians ──
  var ggs = ["Granite maul (ornate handle)", "Black tourmaline core", "Granite gloves",
    "Granite ring", "Granite hammer", "Noon", "Jar of stone"];
  for (var i = 0; i < ggs.length; i++) items.push([ggs[i], "Grotesque Guardians"]);

  // ── Zulrah ──
  var zulrah = ["Tanzanite fang", "Magic fang", "Serpentine visage", "Uncut onyx",
    "Tanzanite mutagen", "Magma mutagen", "Pet snakeling", "Jar of swamp"];
  for (var i = 0; i < zulrah.length; i++) items.push([zulrah[i], "Zulrah"]);

  // ── Vorkath ──
  var vorkath = ["Skeletal visage", "Draconic visage", "Dragonbone necklace",
    "Vorki", "Jar of decay"];
  for (var i = 0; i < vorkath.length; i++) items.push([vorkath[i], "Vorkath"]);

  // ── The Gauntlet / Corrupted Gauntlet ──
  var gauntlet = ["Enhanced crystal weapon seed", "Crystal armour seed", "Youngllef",
    "Jar of imprisonment", "Blade of saeldor (created from seed)"];
  for (var i = 0; i < gauntlet.length; i++) items.push([gauntlet[i], "The Gauntlet"]);

  // ── Dagannoth Kings ──
  var dks = ["Berserker ring", "Archers ring", "Seers ring", "Warrior ring",
    "Dragon axe", "Mud battlestaff", "Seercull", "Pet dagannoth prime",
    "Pet dagannoth rex", "Pet dagannoth supreme", "Jar of stone"];
  for (var i = 0; i < dks.length; i++) items.push([dks[i], "Dagannoth Kings"]);

  // ── King Black Dragon ──
  var kbd = ["Dragon pickaxe", "Draconic visage", "Prince black dragon",
    "Jar of decay", "KBD heads"];
  for (var i = 0; i < kbd.length; i++) items.push([kbd[i], "King Black Dragon"]);

  // ── Giant Mole ──
  var mole = ["Baby mole", "Jar of dirt", "Mole claw", "Mole skin"];
  for (var i = 0; i < mole.length; i++) items.push([mole[i], "Giant Mole"]);

  // ── Sarachnis ──
  var sarachnis = ["Sarachnis cudgel", "Giant egg sac(full)", "Sraracha", "Jar of eyes"];
  for (var i = 0; i < sarachnis.length; i++) items.push([sarachnis[i], "Sarachnis"]);

  // ── Skotizo ──
  var skotizo = ["Dark claw", "Uncut onyx", "Skotos", "Jar of darkness"];
  for (var i = 0; i < skotizo.length; i++) items.push([skotizo[i], "Skotizo"]);

  // ── Kalphite Queen ──
  var kq = ["Dragon chainbody", "Dragon 2h sword", "Kalphite princess",
    "Jar of sand", "KQ head"];
  for (var i = 0; i < kq.length; i++) items.push([kq[i], "Kalphite Queen"]);

  // ── Duke Sucellus (DT2) ──
  var duke = ["Virtus mask", "Virtus robe top", "Virtus robe bottom", "Magus ring",
    "Chromium ingot", "Baron", "Jar of the duke"];
  for (var i = 0; i < duke.length; i++) items.push([duke[i], "Duke Sucellus"]);

  // ── The Leviathan (DT2) ──
  var levi = ["Leviathan's lure", "Venator ring", "Lil'viathan", "Jar of the leviathan"];
  for (var i = 0; i < levi.length; i++) items.push([levi[i], "The Leviathan"]);

  // ── Vardorvis (DT2) ──
  var vard = ["Executioner's axe head", "Ultor ring", "Butch", "Jar of vardorvis"];
  for (var i = 0; i < vard.length; i++) items.push([vard[i], "Vardorvis"]);

  // ── The Whisperer (DT2) ──
  var whisp = ["Bellator ring", "Chromium ingot", "Wisp", "Jar of the whisperer"];
  for (var i = 0; i < whisp.length; i++) items.push([whisp[i], "The Whisperer"]);

  // ── Wilderness Bosses (Reworked) ──
  var wildy = ["Voidwaker blade", "Voidwaker hilt", "Voidwaker gem",
    "Ursine chainmace", "Webweaver bow", "Accursed sceptre",
    "Fangs of venenatis", "Skull of vet'ion", "Claws of callisto",
    "Dragon pickaxe", "Ring of the gods", "Treasonous ring", "Tyrannical ring",
    "Venenatis spiderling", "Vet'ion jr.", "Callisto cub",
    "Scorpia's offspring", "Chaos elemental pet"];
  for (var i = 0; i < wildy.length; i++) items.push([wildy[i], "Wilderness Bosses"]);

  // ── Chaos Fanatic / Crazy Archaeologist / Scorpia ──
  var wildyMinor = ["Odium shard", "Malediction shard",
    "Dragon pickaxe (Chaos Elemental)", "Scorpia's offspring"];
  for (var i = 0; i < wildyMinor.length; i++) items.push([wildyMinor[i], "Chaos Fanatic / Scorpia"]);

  // ── Tempoross ──
  var tempoross = ["Dragon harpoon", "Tome of water", "Tiny tempor",
    "Big harpoonfish", "Spirit flakes", "Soaked page", "Fish barrel", "Tackle box"];
  for (var i = 0; i < tempoross.length; i++) items.push([tempoross[i], "Tempoross"]);

  // ── Wintertodt ──
  var wt = ["Dragon axe", "Tome of fire", "Phoenix", "Warm gloves",
    "Bruma torch", "Pyromancer garb (set pieces)"];
  for (var i = 0; i < wt.length; i++) items.push([wt[i], "Wintertodt"]);

  // ── Guardians of the Rift ──
  var gotr = ["Abyssal needle", "Abyssal lantern lens", "Ring of the elements",
    "Abyssal protector"];
  for (var i = 0; i < gotr.length; i++) items.push([gotr[i], "Guardians of the Rift"]);

  // ── Zalcano ──
  var zalcano = ["Crystal tool seed", "Zalcano shard", "Smolcano"];
  for (var i = 0; i < zalcano.length; i++) items.push([zalcano[i], "Zalcano"]);

  // ── Clue Scroll (Easy) ──
  var clueEasy = [
    "Black beret", "Blue beret", "White beret", "Red beret", "Highwayman mask",
    "Team cape zero", "Team cape i", "Team cape x",
    "Amulet of magic (t)", "Black full helm (t)", "Black platebody (t)",
    "Black platelegs (t)", "Black plateskirt (t)", "Black kiteshield (t)",
    "Black full helm (g)", "Black platebody (g)", "Black platelegs (g)",
    "Black plateskirt (g)", "Black kiteshield (g)",
    "Black shield (h1-h5)", "Black helm (h1-h5)",
    "Studded body (t)", "Studded body (g)", "Studded chaps (t)", "Studded chaps (g)",
    "Blue wizard hat (t)", "Blue wizard hat (g)", "Blue wizard robe (t)",
    "Blue wizard robe (g)", "Blue skirt (t)", "Blue skirt (g)",
    "Saradomin page 1-4", "Zamorak page 1-4", "Guthix page 1-4",
    "Bronze pickaxe (or)", "Iron pickaxe (or)", "Wooden shield (g)",
    "Flared trousers", "Bob's red shirt", "Bob's blue shirt", "Bob's green shirt",
    "Bob's purple shirt", "Bob's black shirt", "A powdered wig", "Beanie",
    "Imp mask", "Monk's robe top (t)", "Monk's robe (t)", "Amulet of defence (t)",
    "Sandwich lady hat", "Shoulder parrot", "Cape of skulls", "Rain bow"
  ];
  for (var i = 0; i < clueEasy.length; i++) items.push([clueEasy[i], "Clue Scroll (Easy)"]);

  // ── Clue Scroll (Medium) ──
  var clueMed = [
    "Ranger boots", "Wizard boots", "Holy sandals", "Spiked manacles",
    "Climbing boots (g)",
    "Adamant full helm (t)", "Adamant platebody (t)", "Adamant platelegs (t)",
    "Adamant plateskirt (t)", "Adamant kiteshield (t)",
    "Adamant full helm (g)", "Adamant platebody (g)", "Adamant platelegs (g)",
    "Adamant plateskirt (g)", "Adamant kiteshield (g)",
    "Mithril full helm (t)", "Mithril platebody (t)", "Mithril platelegs (t)",
    "Mithril plateskirt (t)", "Mithril kiteshield (t)",
    "Mithril full helm (g)", "Mithril platebody (g)", "Mithril platelegs (g)",
    "Mithril plateskirt (g)", "Mithril kiteshield (g)",
    "Green d'hide body (t)", "Green d'hide body (g)",
    "Green d'hide chaps (t)", "Green d'hide chaps (g)",
    "Saradomin mitre", "Guthix mitre", "Zamorak mitre",
    "Saradomin cloak", "Guthix cloak", "Zamorak cloak",
    "Ancient mitre", "Ancient cloak", "Ancient stole",
    "Bandos mitre", "Bandos cloak", "Bandos stole",
    "Armadyl mitre", "Armadyl cloak", "Armadyl stole",
    "Gnomish firelighter", "Purple firelighter", "White firelighter",
    "Cat mask", "Penguin mask", "Leprechaun hat",
    "Black unicorn mask", "White unicorn mask",
    "Crier hat", "Crier coat", "Crier bell"
  ];
  for (var i = 0; i < clueMed.length; i++) items.push([clueMed[i], "Clue Scroll (Medium)"]);

  // ── Clue Scroll (Hard) ──
  var clueHard = [
    "Robin hood hat",
    "Gilded full helm", "Gilded platebody", "Gilded platelegs",
    "Gilded plateskirt", "Gilded kiteshield", "Gilded med helm",
    "Gilded chainbody", "Gilded sq shield", "Gilded boots",
    "Gilded scimitar", "Gilded spear", "Gilded hasta",
    "3rd age full helmet", "3rd age platebody", "3rd age platelegs",
    "3rd age plateskirt", "3rd age kiteshield",
    "3rd age range top", "3rd age range legs", "3rd age range coif",
    "3rd age vambraces",
    "3rd age mage hat", "3rd age robe top", "3rd age robe", "3rd age amulet",
    "Rune full helm (t)", "Rune platebody (t)", "Rune platelegs (t)",
    "Rune plateskirt (t)", "Rune kiteshield (t)",
    "Rune full helm (g)", "Rune platebody (g)", "Rune platelegs (g)",
    "Rune plateskirt (g)", "Rune kiteshield (g)",
    "Rune shield (h1-h5)", "Rune helm (h1-h5)",
    "Saradomin d'hide body", "Saradomin d'hide boots",
    "Saradomin d'hide shield", "Saradomin bracers",
    "Saradomin coif", "Saradomin chaps",
    "Guthix d'hide body", "Guthix d'hide boots",
    "Guthix d'hide shield", "Guthix bracers", "Guthix coif", "Guthix chaps",
    "Zamorak d'hide body", "Zamorak d'hide boots",
    "Zamorak d'hide shield", "Zamorak bracers", "Zamorak coif", "Zamorak chaps",
    "Ancient d'hide body", "Ancient d'hide boots",
    "Ancient d'hide shield", "Ancient bracers", "Ancient coif", "Ancient chaps",
    "Armadyl d'hide body", "Armadyl d'hide boots",
    "Armadyl d'hide shield", "Armadyl bracers", "Armadyl coif", "Armadyl chaps",
    "Bandos d'hide body", "Bandos d'hide boots",
    "Bandos d'hide shield", "Bandos bracers", "Bandos coif", "Bandos chaps",
    "Enchanted hat", "Enchanted top", "Enchanted robe",
    "Zombie head", "Cyclops head", "Pirate hat", "Red pirate hat"
  ];
  for (var i = 0; i < clueHard.length; i++) items.push([clueHard[i], "Clue Scroll (Hard)"]);

  // ── Clue Scroll (Elite) ──
  var clueElite = [
    "Dragon full helm ornament kit", "Dragon chainbody ornament kit",
    "Dragon platebody ornament kit", "Dragon platelegs/skirt ornament kit",
    "Dragon sq shield ornament kit", "Dragon scimitar ornament kit",
    "Light infinity colour kit", "Dark infinity colour kit", "Fury ornament kit",
    "3rd age longsword", "3rd age wand", "3rd age cloak", "3rd age bow",
    "Royal crown", "Royal gown top", "Royal gown bottom", "Royal sceptre",
    "Musketeer hat", "Musketeer tabard", "Musketeer pants",
    "Dark bow tie", "Dark tuxedo jacket", "Dark tuxedo cuffs", "Dark tuxedo shoes",
    "Light bow tie", "Light tuxedo jacket", "Light tuxedo cuffs", "Light tuxedo shoes",
    "Briefcase", "Sagacious spectacles", "Fremennik kilt", "Rangers' tunic",
    "Holy wraps", "Ranger gloves",
    "Gilded 2h sword", "Gilded pickaxe", "Gilded axe", "Gilded spade"
  ];
  for (var i = 0; i < clueElite.length; i++) items.push([clueElite[i], "Clue Scroll (Elite)"]);

  // ── Clue Scroll (Master) ──
  var clueMaster = [
    "3rd age pickaxe", "3rd age axe", "3rd age druidic staff",
    "3rd age druidic robe top", "3rd age druidic robe bottoms",
    "3rd age druidic cloak",
    "Bucket helm (g)", "Ring of coins", "Ring of nature", "Left eye patch",
    "Obsidian cape (r)", "Samurai kasa", "Samurai shirt", "Samurai gloves",
    "Samurai greaves", "Samurai boots",
    "Arceuus scarf", "Hosidius scarf", "Lovakengj scarf",
    "Piscarilius scarf", "Shayzien scarf",
    "Ankou mask", "Ankou top", "Ankou gloves", "Ankou socks",
    "Mummy's head", "Mummy's body", "Mummy's hands", "Mummy's legs", "Mummy's feet",
    "Dragon defender ornament kit", "Occult necklace ornament kit",
    "Torture ornament kit", "Anguish ornament kit", "Tormented ornament kit",
    "Dragon platebody ornament kit", "Gilded zenyte", "Bowl wig", "Bloodhound"
  ];
  for (var i = 0; i < clueMaster.length; i++) items.push([clueMaster[i], "Clue Scroll (Master)"]);

  // ── Miscellaneous / Other ──
  var misc = ["Basilisk jaw", "Leaf-bladed battleaxe", "Granite longsword",
    "Granite boots", "Drake's tooth", "Drake's claw", "Brittle key",
    "Ancient shard", "Dark totem", "Brimstone key rewards",
    "Long bone", "Curved bone", "Giant key", "Shield left half",
    "Dragon spear", "Dragon med helm"];
  for (var i = 0; i < misc.length; i++) items.push([misc[i], "Miscellaneous"]);

  // Write all items
  if (items.length > 0) {
    sheet.getRange(2, 1, items.length, 2).setValues(items);
  }

  // Formatting
  sheet.setColumnWidth(1, 300);
  sheet.setColumnWidth(2, 200);

  // Hide the tab by default
  sheet.hideSheet();
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
  createTeamsTab_(ss, foundTeams.length);
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

  // ── Board tab (visual reference) ──
  createBoardTab_(ss, 6);

  // ── Item Database tab (hidden, for autocomplete) ──
  createItemDatabaseTab_(ss);

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
