# Bingo Enhancements Design

## Overview

Three enhancements to the generalized bingo system:
1. Team count prompt in setup (instead of hardcoded 2 example teams)
2. Visual Board tab in the Google Sheet for admin reference
3. Curated item database (~800-1000 items) with autocomplete on the Whitelist tab

All changes are in `BingoSetup.gs`. No Java plugin changes needed. The `migrateSWB26()` function also gets the Board tab generation.

## Enhancement 1: Team Count Prompt

### Current Behavior
`setupBingoSheet()` prompts for grid size, then creates 2 hardcoded example teams ("Team One", "Team Two") in the Teams tab.

### New Behavior
After the grid size prompt, a second prompt asks: "How many teams? (2-20)". Default 2. Creates that many rows with placeholder names ("Team 1", "Team 2", ... "Team N") for the admin to rename.

### Changes
- `setupBingoSheet()` — add second prompt
- `createTeamsTab_(ss, teamCount)` — accept team count parameter instead of hardcoding 2

## Enhancement 2: Visual Board Tab

### Purpose
Admins can see the bingo board layout directly in Google Sheets while editing tiles, without needing to open the RuneLite plugin.

### Design
`setupBingoSheet()` creates a **"Board"** tab after all data tabs:
- Row 1: header with column letters (A, B, C, ...)
- Column A: row numbers (1, 2, 3, ...)
- Grid cells show tile code + name, pulled from the Tiles tab via formula: `=IFERROR(INDEX(Tiles!B:B, MATCH("<code>", Tiles!A:A, 0)), "")`
- Formatting: centered text, borders, light gray background (#f0f0f0), bold tile codes
- Cell width/height sized for readability (~150px wide, ~40px tall)

### Formula Approach
Each cell uses `MATCH` + `INDEX` to look up the tile name from the Tiles tab by code. This means:
- If the admin edits a tile name in the Tiles tab, the Board updates automatically
- The tile code is hardcoded in each cell formula (e.g., "A1", "B3"), which is fine since grid layout doesn't change after setup

### Migration
`migrateSWB26()` also calls `createBoardTab_()` after creating all data tabs.

## Enhancement 3: Curated Item Database + Whitelist Autocomplete

### Item Database Tab

A new **"Item Database"** tab created by `setupBingoSheet()`:
- Two columns: `Item Name`, `Source`
- Hidden by default (admin can unhide to browse)
- ~800-1000 items sorted alphabetically within each source category

### Source Categories and Items

**Chambers of Xeric (CoX):**
Twisted bow, Kodai insignia, Elder maul, Dragon claws, Ancestral hat, Ancestral robe top, Ancestral robe bottom, Dexterous prayer scroll, Arcane prayer scroll, Dinh's bulwark, Dragon hunter crossbow, Twisted buckler, Tome of water (ornament kit), Olmlet, Jar of stone

**Theatre of Blood (ToB):**
Scythe of vitur, Ghrazi rapier, Sanguinesti staff, Justiciar faceguard, Justiciar chestguard, Justiciar legguards, Avernic defender hilt, Lil' zik, Jar of darkness, Holy ornament kit, Sanguine ornament kit

**Tombs of Amascut (ToA):**
Tumeken's shadow, Osmumten's fang, Masori mask, Masori body, Masori chaps, Lightbearer, Elidinis' ward, Jewel of the sun, Jar of amascut

**General Graardor (Bandos):**
Bandos chestplate, Bandos tassets, Bandos boots, Bandos hilt, Pet general graardor, Jar of spirits

**Kree'arra (Armadyl):**
Armadyl helmet, Armadyl chestplate, Armadyl chainskirt, Armadyl hilt, Pet kree'arra, Jar of wind

**Commander Zilyana (Saradomin):**
Saradomin sword, Armadyl crossbow, Saradomin hilt, Saradomin's light, Pet zilyana, Jar of light

**K'ril Tsutsaroth (Zamorak):**
Staff of the dead, Zamorakian spear, Zamorak hilt, Steam battlestaff, Pet k'ril tsutsaroth, Jar of smoke

**Nex:**
Torva full helm, Torva platebody, Torva platelegs, Zaryte vambraces, Nihil horn, Ancient hilt, Nexling, Jar of shadows

**Nightmare / Phosani's Nightmare:**
Nightmare staff, Inquisitor's great helm, Inquisitor's hauberk, Inquisitor's plateskirt, Inquisitor's mace, Eldritch orb, Harmonised orb, Volatile orb, Parasitic egg, Jar of dreams

**Corporeal Beast:**
Spectral sigil, Arcane sigil, Elysian sigil, Spirit shield, Holy elixir, Pet dark core, Jar of spirits

**Cerberus:**
Primordial crystal, Pegasian crystal, Eternal crystal, Smouldering stone, Hellpuppy, Jar of souls

**Alchemical Hydra:**
Hydra's claw, Hydra tail, Hydra leather, Hydra's eye, Hydra's fang, Hydra's heart, Ikkle hydra, Jar of chemicals, Brimstone ring (assembled)

**Kraken:**
Trident of the seas, Kraken tentacle, Pet kraken, Jar of dirt

**Thermonuclear Smoke Devil:**
Occult necklace, Smoke battlestaff, Dragon chainbody, Pet smoke devil, Jar of smoke

**Abyssal Sire:**
Unsired, Abyssal dagger, Abyssal bludgeon, Abyssal orphan, Jar of miasma

**Grotesque Guardians:**
Granite maul (ornate handle), Black tourmaline core, Granite gloves, Granite ring, Granite hammer, Noon, Jar of stone

**Zulrah:**
Tanzanite fang, Magic fang, Serpentine visage, Uncut onyx, Tanzanite mutagen, Magma mutagen, Pet snakeling, Jar of swamp

**Vorkath:**
Skeletal visage, Draconic visage, Dragonbone necklace, Vorki, Jar of decay

**The Gauntlet / Corrupted Gauntlet:**
Enhanced crystal weapon seed, Crystal armour seed, Youngllef, Jar of imprisonment, Blade of saeldor (created from seed)

**Dagannoth Kings:**
Berserker ring, Archers ring, Seers ring, Warrior ring, Dragon axe, Mud battlestaff, Seercull, Pet dagannoth prime, Pet dagannoth rex, Pet dagannoth supreme, Jar of stone

**King Black Dragon:**
Dragon pickaxe, Draconic visage, Prince black dragon, Jar of decay, KBD heads

**Giant Mole:**
Baby mole, Jar of dirt, Mole claw, Mole skin

**Sarachnis:**
Sarachnis cudgel, Giant egg sac(full), Sraracha, Jar of eyes

**Skotizo:**
Dark claw, Uncut onyx, Skotos, Jar of darkness

**Kalphite Queen:**
Dragon chainbody, Dragon 2h sword, Kalphite princess, Jar of sand, KQ head

**Duke Sucellus (DT2):**
Virtus mask, Virtus robe top, Virtus robe bottom, Magus ring, Chromium ingot, Baron, Jar of the duke

**The Leviathan (DT2):**
Leviathan's lure, Venator ring, Lil'viathan, Jar of the leviathan

**Vardorvis (DT2):**
Executioner's axe head, Ultor ring, Butch, Jar of vardorvis

**The Whisperer (DT2):**
Bellator ring, Chromium ingot, Wisp, Jar of the whisperer

**Wilderness Bosses (Reworked):**
Voidwaker blade, Voidwaker hilt, Voidwaker gem, Ursine chainmace, Webweaver bow, Accursed sceptre, Fangs of venenatis, Skull of vet'ion, Claws of callisto, Dragon pickaxe, Ring of the gods, Treasonous ring, Tyrannical ring, Venenatis spiderling, Vet'ion jr., Callisto cub, Scorpia's offspring, Chaos elemental pet

**Chaos Fanatic / Crazy Archaeologist / Scorpia:**
Odium shard, Malediction shard, Dragon pickaxe (Chaos Elemental), Scorpia's offspring

**Tempoross:**
Dragon harpoon, Tome of water, Tiny tempor, Big harpoonfish, Spirit flakes, Soaked page, Fish barrel, Tackle box

**Wintertodt:**
Dragon axe, Tome of fire, Phoenix, Warm gloves, Bruma torch, Pyromancer garb (set pieces)

**Guardians of the Rift:**
Abyssal needle, Abyssal lantern lens, Ring of the elements, Abyssal protector

**Zalcano:**
Crystal tool seed, Zalcano shard, Smolcano

**Clue Scroll (Easy):**
Black beret, Blue beret, White beret, Red beret, Highwayman mask, Team cape zero, Team cape i, Team cape x, Amulet of magic (t), Black full helm (t), Black platebody (t), Black platelegs (t), Black plateskirt (t), Black kiteshield (t), Black full helm (g), Black platebody (g), Black platelegs (g), Black plateskirt (g), Black kiteshield (g), Black shield (h1-h5), Black helm (h1-h5), Studded body (t), Studded body (g), Studded chaps (t), Studded chaps (g), Blue wizard hat (t), Blue wizard hat (g), Blue wizard robe (t), Blue wizard robe (g), Blue skirt (t), Blue skirt (g), Saradomin page 1-4, Zamorak page 1-4, Guthix page 1-4, Bronze pickaxe (or), Iron pickaxe (or), Wooden shield (g), Flared trousers, Bob's red shirt, Bob's blue shirt, Bob's green shirt, Bob's purple shirt, Bob's black shirt, A powdered wig, Beanie, Imp mask, Monk's robe top (t), Monk's robe (t), Amulet of defence (t), Sandwich lady hat, Shoulder parrot, Cape of skulls, Rain bow

**Clue Scroll (Medium):**
Ranger boots, Wizard boots, Holy sandals, Spiked manacles, Climbing boots (g), Adamant full helm (t), Adamant platebody (t), Adamant platelegs (t), Adamant plateskirt (t), Adamant kiteshield (t), Adamant full helm (g), Adamant platebody (g), Adamant platelegs (g), Adamant plateskirt (g), Adamant kiteshield (g), Mithril full helm (t), Mithril platebody (t), Mithril platelegs (t), Mithril plateskirt (t), Mithril kiteshield (t), Mithril full helm (g), Mithril platebody (g), Mithril platelegs (g), Mithril plateskirt (g), Mithril kiteshield (g), Green d'hide body (t), Green d'hide body (g), Green d'hide chaps (t), Green d'hide chaps (g), Saradomin mitre, Guthix mitre, Zamorak mitre, Saradomin cloak, Guthix cloak, Zamorak cloak, Ancient mitre, Ancient cloak, Ancient stole, Bandos mitre, Bandos cloak, Bandos stole, Armadyl mitre, Armadyl cloak, Armadyl stole, Gnomish firelighter, Purple firelighter, White firelighter, Cat mask, Penguin mask, Leprechaun hat, Black unicorn mask, White unicorn mask, Crier hat, Crier coat, Crier bell

**Clue Scroll (Hard):**
Robin hood hat, Gilded full helm, Gilded platebody, Gilded platelegs, Gilded plateskirt, Gilded kiteshield, Gilded med helm, Gilded chainbody, Gilded sq shield, Gilded boots, Gilded scimitar, Gilded spear, Gilded hasta, 3rd age full helmet, 3rd age platebody, 3rd age platelegs, 3rd age plateskirt, 3rd age kiteshield, 3rd age range top, 3rd age range legs, 3rd age range coif, 3rd age vambraces, 3rd age mage hat, 3rd age robe top, 3rd age robe, 3rd age amulet, Rune full helm (t), Rune platebody (t), Rune platelegs (t), Rune plateskirt (t), Rune kiteshield (t), Rune full helm (g), Rune platebody (g), Rune platelegs (g), Rune plateskirt (g), Rune kiteshield (g), Rune shield (h1-h5), Rune helm (h1-h5), Saradomin d'hide body, Saradomin d'hide boots, Saradomin d'hide shield, Saradomin bracers, Saradomin coif, Saradomin chaps, Guthix d'hide body, Guthix d'hide boots, Guthix d'hide shield, Guthix bracers, Guthix coif, Guthix chaps, Zamorak d'hide body, Zamorak d'hide boots, Zamorak d'hide shield, Zamorak bracers, Zamorak coif, Zamorak chaps, Ancient d'hide body, Ancient d'hide boots, Ancient d'hide shield, Ancient bracers, Ancient coif, Ancient chaps, Armadyl d'hide body, Armadyl d'hide boots, Armadyl d'hide shield, Armadyl bracers, Armadyl coif, Armadyl chaps, Bandos d'hide body, Bandos d'hide boots, Bandos d'hide shield, Bandos bracers, Bandos coif, Bandos chaps, Enchanted hat, Enchanted top, Enchanted robe, Zombie head, Cyclops head, Pirate hat, Red pirate hat

**Clue Scroll (Elite):**
Dragon full helm ornament kit, Dragon chainbody ornament kit, Dragon platebody ornament kit, Dragon platelegs/skirt ornament kit, Dragon sq shield ornament kit, Dragon scimitar ornament kit, Light infinity colour kit, Dark infinity colour kit, Fury ornament kit, 3rd age longsword, 3rd age wand, 3rd age cloak, 3rd age bow, Royal crown, Royal gown top, Royal gown bottom, Royal sceptre, Musketeer hat, Musketeer tabard, Musketeer pants, Dark bow tie, Dark tuxedo jacket, Dark tuxedo cuffs, Dark tuxedo shoes, Light bow tie, Light tuxedo jacket, Light tuxedo cuffs, Light tuxedo shoes, Briefcase, Sagacious spectacles, Fremennik kilt, Rangers' tunic, Holy wraps, Ranger gloves, Gilded 2h sword, Gilded pickaxe, Gilded axe, Gilded spade

**Clue Scroll (Master):**
3rd age pickaxe, 3rd age axe, 3rd age druidic staff, 3rd age druidic robe top, 3rd age druidic robe bottoms, 3rd age druidic cloak, Bucket helm (g), Ring of coins, Ring of nature, Left eye patch, Obsidian cape (r), Samurai kasa, Samurai shirt, Samurai gloves, Samurai greaves, Samurai boots, Arceuus scarf, Hosidius scarf, Lovakengj scarf, Piscarilius scarf, Shayzien scarf, Ankou mask, Ankou top, Ankou gloves, Ankou socks, Mummy's head, Mummy's body, Mummy's hands, Mummy's legs, Mummy's feet, Dragon defender ornament kit, Occult necklace ornament kit, Torture ornament kit, Anguish ornament kit, Tormented ornament kit, Dragon platebody ornament kit, Gilded zenyte, Bowl wig, Bloodhound

**Miscellaneous / Other:**
Basilisk jaw, Leaf-bladed battleaxe, Granite longsword, Granite boots, Drake's tooth, Drake's claw, Brittle key, Ancient shard, Dark totem, Brimstone key rewards, Long bone, Curved bone, Giant key, Shield left half, Dragon spear, Dragon med helm

### Whitelist Autocomplete

The Whitelist tab's "Item" column (column A, rows 2+) gets data validation:
```javascript
var rule = SpreadsheetApp.newDataValidation()
    .requireValueInRange(itemDbSheet.getRange("A2:A" + lastRow), true)
    .setAllowInvalid(true) // Allow custom items not in the list
    .build();
```

`setAllowInvalid(true)` means admins can still type custom item names not in the database. The dropdown suggests matches as they type, but doesn't force them to pick from the list.

## Files Modified

- `google-apps-script/BingoSetup.gs`:
  - `setupBingoSheet()` — add team count prompt, call `createBoardTab_()` and `createItemDatabaseTab_()`
  - `createTeamsTab_(ss, teamCount)` — accept count parameter
  - New: `createBoardTab_(ss, gridSize)` — visual board with formulas
  - New: `createItemDatabaseTab_(ss)` — curated item list
  - New: `applyWhitelistValidation_(ss)` — data validation on Whitelist item column
  - `migrateSWB26()` — also create Board tab

## Key Design Decisions

1. **Data validation allows custom items** — `setAllowInvalid(true)` lets admins add items not in the curated list. The list is a convenience, not a restriction.
2. **Board tab uses formulas** — pulls tile names from Tiles tab automatically. Admin edits one place, board updates.
3. **Item Database is hidden** — keeps the sheet clean. Admin can unhide to browse if needed.
4. **Items use RuneLite-matching names** — item names must match what RuneLite reports in drop messages for the whitelist to work at runtime.
