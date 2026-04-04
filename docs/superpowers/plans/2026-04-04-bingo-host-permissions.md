# Bingo Host Permissions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three-tier permission system (Player/Admin/Host) to the bingo API with bounty description hiding, a protected Host tab, and action reclassification.

**Architecture:** The Google Sheet gets a new hidden+protected "Host" tab storing the host key, bounty descriptions, and hint timing. The API validates host actions against `PropertiesService`, redacts bounty descriptions until released, and reclassifies board-management actions from `admin*` to `host*` prefix. The Java plugin adds `bingoAdminKey` and `bingoHostKey` config items and wires them through `BingoService`.

**Tech Stack:** Google Apps Script (server), Java/Swing/RuneLite (client), OkHttp + Gson (HTTP)

**Spec:** `docs/superpowers/specs/2026-04-04-bingo-host-permissions-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `google-apps-script/BingoSetup.gs` | Modify | Add `createHostTab_()`, update `setupBingoSheet()` prompts, update `createBountiesTab_()` column order, update `migrateSWB26()`, move `hintMinutesBefore` to Host tab |
| `google-apps-script/BingoAPI.gs` | Modify | Three-tier auth in `doPost()`, bounty redaction in `readBounties_()`, rename `admin*` board actions to `host*`, add `readHostBountyDescriptions_()` and `getHostConfigValue_()`, copy description on release |
| `src/main/java/com/droplogger/ClanManagementConfig.java` | Modify | Add `bingoAdminKey` and `bingoHostKey` config items |
| `src/main/java/com/droplogger/BingoService.java` | Modify | Add `hostKey` field, add `doPostHost()`, rename host-tier methods |
| `src/main/java/com/droplogger/ClanManagementPlugin.java` | Modify | Pass new keys to `BingoService.configure()` |

---

### Task 1: BingoSetup.gs — Host Tab and Bounties Tab Changes

This task creates the Host tab infrastructure and updates the Bounties tab to remove the Description column (descriptions live in Host tab now).

**Files:**
- Modify: `google-apps-script/BingoSetup.gs:6-72` (setupBingoSheet), `google-apps-script/BingoSetup.gs:74-100` (createConfigTab_), `google-apps-script/BingoSetup.gs:174-182` (createBountiesTab_), `google-apps-script/BingoSetup.gs:618-700` (migrateSWB26)

- [ ] **Step 1: Add `createHostTab_()` function**

Add this function after `createBountiesTab_()` (after line 182) in `BingoSetup.gs`:

```javascript
function createHostTab_(ss, hostEmail, hostKey) {
  var sheet = ss.getSheetByName("Host");
  if (!sheet) sheet = ss.insertSheet("Host");
  sheet.clear();

  // Settings section (rows 1-3)
  sheet.getRange("A1:B1").setValues([["Host Email", hostEmail]]);
  sheet.getRange("A2:B2").setValues([["Host Key", hostKey]]);
  sheet.getRange("A3:B3").setValues([["Hint Minutes Before", 60]]);
  sheet.getRange("A1:A3").setFontWeight("bold");
  sheet.setColumnWidth(1, 200);
  sheet.setColumnWidth(2, 400);

  // Blank row 4, then bounty descriptions header at row 5
  sheet.getRange("A5:B5").setValues([["Bounty #", "Description"]]);
  sheet.getRange("A5:B5").setFontWeight("bold");

  // Hide the sheet
  sheet.hideSheet();

  // Protect — only host email can edit
  var protection = sheet.protect().setDescription("Host only — do not unhide");
  protection.addEditor(hostEmail);
  // Remove all other editors
  var editors = protection.getEditors();
  for (var i = 0; i < editors.length; i++) {
    if (editors[i].getEmail() !== hostEmail) {
      protection.removeEditor(editors[i]);
    }
  }

  // Store host key in PropertiesService
  PropertiesService.getScriptProperties().setProperty("hostKey", hostKey);
}
```

- [ ] **Step 2: Add `generateHostKey_()` helper**

Add this utility function right before `createHostTab_()`:

```javascript
function generateHostKey_() {
  var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  var key = "";
  for (var i = 0; i < 32; i++) {
    key += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return key;
}
```

- [ ] **Step 3: Update `setupBingoSheet()` — add host email prompt and call `createHostTab_()`**

In `setupBingoSheet()`, after the team count prompt block (after line 32 `return;` closing brace), add a host email prompt:

```javascript
  var hostResult = ui.prompt(
    "Bingo Setup",
    "Enter host email (Google account for Host tab protection):",
    ui.ButtonSet.OK_CANCEL
  );

  if (hostResult.getSelectedButton() !== ui.Button.OK) return;
  var hostEmail = hostResult.getResponseText().trim();
  if (!hostEmail) {
    ui.alert("Host email is required.");
    return;
  }

  var hostKey = generateHostKey_();
```

Then, after the `createBountiesTab_(ss);` call (line 55), add:

```javascript
  // ── Host tab (hidden, protected) ──
  createHostTab_(ss, hostEmail, hostKey);
```

- [ ] **Step 4: Update `createConfigTab_()` — remove `hintMinutesBefore`**

In `createConfigTab_()`, remove the `["hintMinutesBefore", "15"]` entry from the `settings` array (line 95). The settings array becomes:

```javascript
  var settings = [
    ["gridRows", gridSize],
    ["gridCols", gridSize],
    ["eventName", "My Bingo Event"],
    ["startDate", startStr],
    ["endDate", endStr],
    ["apiKey", "changeme"],
    ["adminKey", "changeme-admin"]
  ];
```

- [ ] **Step 5: Update `createBountiesTab_()` — new column order without Description**

Replace the `createBountiesTab_()` function with:

```javascript
function createBountiesTab_(ss) {
  var sheet = ss.getSheetByName("Bounties");
  if (!sheet) sheet = ss.insertSheet("Bounties");
  sheet.clear();

  var headers = ["Number", "Release Time", "Points", "Winner", "Hint Fired", "Release Fired", "Description"];
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");
}
```

Note: The "Description" column exists but starts empty. It gets populated when a bounty's `releaseFired` becomes true.

- [ ] **Step 6: Update `setupBingoSheet()` success message**

Replace the `ui.alert(...)` success message at the end of `setupBingoSheet()` with:

```javascript
  ui.alert("Bingo sheet setup complete!\n\n" +
    "Host Key (save this — you'll need it in the plugin):\n" +
    hostKey + "\n\n" +
    "Created " + teamCount + " teams (rename them in the Teams tab).\n" +
    "Board tab shows your tile grid (updates automatically from Tiles).\n" +
    "Whitelist has autocomplete from the Item Database (~800 items).\n\n" +
    "Fill in tiles, teams, roster, whitelist, and bounties.\n" +
    "Then deploy as Web App and paste the URL into the plugin.");
```

- [ ] **Step 7: Update `migrateSWB26()` — create Host tab, move bounty descriptions**

At the end of `migrateSWB26()`, before the final `ui.alert(...)`, add Host tab creation and bounty description migration. Find the section after `applyWhitelistValidation_(ss);` and add:

```javascript
  // ── Host tab: create with bounty description migration ──
  var migrateHostEmail = ui.prompt(
    "Host Permissions",
    "Enter host email (Google account for Host tab protection):",
    ui.ButtonSet.OK_CANCEL
  );
  var hostEmail = "owner@gmail.com";
  if (migrateHostEmail.getSelectedButton() === ui.Button.OK && migrateHostEmail.getResponseText().trim()) {
    hostEmail = migrateHostEmail.getResponseText().trim();
  }
  var hostKey = generateHostKey_();
  createHostTab_(ss, hostEmail, hostKey);

  // Move existing bounty descriptions from public Bounties tab to Host tab
  var bountiesSheet = ss.getSheetByName("Bounties");
  var hostSheet = ss.getSheetByName("Host");
  if (bountiesSheet && hostSheet && bountiesSheet.getLastRow() > 1) {
    var bountyData = bountiesSheet.getRange(2, 1, bountiesSheet.getLastRow() - 1, 7).getValues();
    var hostDescRow = 6; // Row 6+ in Host tab (row 5 is header)
    for (var i = 0; i < bountyData.length; i++) {
      var num = parseInt(bountyData[i][0]);
      if (!num) continue;
      // Old format: col 2 is Description
      var desc = (bountyData[i][1] || "").toString().trim();
      if (desc) {
        hostSheet.getRange(hostDescRow, 1).setValue(num);
        hostSheet.getRange(hostDescRow, 2).setValue(desc);
        hostDescRow++;
        // Clear description from public tab
        bountiesSheet.getRange(i + 2, 2).setValue("");
      }
    }
  }

  // Remove hintMinutesBefore from Config tab (now in Host tab)
  var cfgSheet = ss.getSheetByName("Config");
  if (cfgSheet) {
    var cfgData = cfgSheet.getRange("A2:A100").getValues();
    for (var i = 0; i < cfgData.length; i++) {
      if ((cfgData[i][0] || "").toString().trim() === "hintMinutesBefore") {
        cfgSheet.deleteRow(i + 2);
        break;
      }
    }
  }
```

Update the final success message in `migrateSWB26()` to include the host key:

```javascript
  ui.alert("Migration complete!\n\n" +
    "Host Key (save this — you'll need it in the plugin):\n" +
    hostKey + "\n\n" +
    "Created standardized tabs from SWB26 format.\n" +
    "Board tab shows your tile grid.\n" +
    "Whitelist has autocomplete from the Item Database.\n" +
    "Host tab is hidden and protected (only " + hostEmail + " can edit).\n\n" +
    "Review the new tabs, then deploy as Web App.");
```

- [ ] **Step 8: Verify BingoSetup.gs has no syntax errors**

Open the Apps Script editor and check for red underlines. Run `setupBingoSheet()` in a test sheet to verify the Host tab is created, hidden, and protected.

- [ ] **Step 9: Commit**

```bash
git add google-apps-script/BingoSetup.gs
git commit -m "feat(bingo): add Host tab with protection, move hintMinutesBefore and bounty descriptions"
```

---

### Task 2: BingoAPI.gs — Three-Tier Auth, Bounty Redaction, Action Reclassification

This task modifies the API to validate three permission tiers, redact bounty descriptions, copy descriptions on release, and rename board-management actions from `admin*` to `host*`.

**Files:**
- Modify: `google-apps-script/BingoAPI.gs`

- [ ] **Step 1: Add Host tab helper functions**

Add these two helper functions after `readWhitelist_()` (after line 141):

```javascript
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
```

- [ ] **Step 2: Update `readBounties_()` — add `includeDescriptions` parameter and new column order**

Replace the `readBounties_()` function (lines 105-124) with:

```javascript
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

    // Description logic:
    // - If releaseFired: use the public tab description (col 7, copied on release)
    // - If includeDescriptions: use host tab description (host-only)
    // - Otherwise: redact with "???"
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
```

- [ ] **Step 3: Update `getBingoConfig` in `doGet()` — use `readBounties_(false)` and source `hintMinutesBefore` from Host tab**

Replace the `getBingoConfig` handler (lines 177-201) with:

```javascript
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
```

- [ ] **Step 4: Update `getBounties` in `doGet()` — use `readBounties_(false)`**

Replace line 224:
```javascript
    return resp_({ bounties: readBounties_() });
```
with:
```javascript
    return resp_({ bounties: readBounties_(false) });
```

- [ ] **Step 5: Update `doPost()` — three-tier auth with host key validation**

Replace the `doPost()` function (lines 330-383) with:

```javascript
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var key = data.key || "";
    var apiKey = getConfigValue_("apiKey") || "changeme";
    if (key !== apiKey) {
      return resp_({ status: "error", message: "Invalid API key" });
    }

    var action = (data.action || "").trim();

    // ── Regular key actions (player tier) ──

    if (action === "submitDrop") {
      return resp_(handleSubmitDrop_(data));
    }

    if (action === "updateTileProgress") {
      return resp_(handleUpdateTileProgress_(data));
    }

    if (action === "markBountyFired") {
      return resp_(handleMarkBountyFired_(data));
    }

    // ── Host key actions ──

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

    // ── Admin key actions ──

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
```

- [ ] **Step 6: Add `handleHostAddBounty_()` — stores description in Host tab instead of public tab**

Add this new handler after `handleAdminAddBounty_()`:

```javascript
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
```

- [ ] **Step 7: Update `handleMarkBountyFired_()` — copy description to public tab on release**

Replace `handleMarkBountyFired_()` (lines 508-529) with:

```javascript
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
```

- [ ] **Step 8: Update `handleAdminUpdateBounty_()` — adjust column indices for new Bounties tab layout**

Replace `handleAdminUpdateBounty_()` (lines 673-692) with:

```javascript
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
```

- [ ] **Step 9: Verify BingoAPI.gs — manual test**

Deploy the updated script. Test:
1. GET `getBingoConfig` with apiKey — bounty descriptions should show `"???"` for unreleased bounties
2. POST `hostAddBounty` with hostKey — should succeed and store description in Host tab
3. POST `hostAddBounty` without hostKey — should fail with "Invalid host key"
4. POST `adminManualProgress` with adminKey — should succeed
5. POST `markBountyFired` with `field: "releaseFired"` — should copy description to public tab

- [ ] **Step 10: Commit**

```bash
git add google-apps-script/BingoAPI.gs
git commit -m "feat(bingo): add three-tier auth, bounty redaction, host action reclassification"
```

---

### Task 3: ClanManagementConfig.java — Add Admin and Host Key Config Items

**Files:**
- Modify: `src/main/java/com/droplogger/ClanManagementConfig.java:157-185`

- [ ] **Step 1: Add `bingoAdminKey` and `bingoHostKey` config items**

In `ClanManagementConfig.java`, after the `bingoApiKey` config item (line 184), add:

```java
    @ConfigItem(
        keyName = "bingoAdminKey",
        name = "Bingo Admin Key",
        description = "Admin key for bingo roster/progress management (leave blank if not an admin)",
        section = bingoSection,
        position = 2,
        secret = true
    )
    default String bingoAdminKey() { return ""; }

    @ConfigItem(
        keyName = "bingoHostKey",
        name = "Bingo Host Key",
        description = "Host key for full bingo board management (leave blank if not the host)",
        section = bingoSection,
        position = 3,
        secret = true
    )
    default String bingoHostKey() { return ""; }
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/ClanManagementConfig.java
git commit -m "feat(bingo): add bingoAdminKey and bingoHostKey config items"
```

---

### Task 4: BingoService.java — Host Key Support and Method Renames

**Files:**
- Modify: `src/main/java/com/droplogger/BingoService.java`

- [ ] **Step 1: Add `hostKey` field and update `configure()`**

In `BingoService.java`, add `hostKey` field after `adminKey` (line 24):

```java
    private String hostKey;
```

Update the `configure()` method (line 44) to accept the host key:

```java
    public void configure(String apiUrl, String apiKey, String adminKey, String hostKey)
    {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.adminKey = adminKey;
        this.hostKey = hostKey;
        this.cachedConfig = null;
        this.configFetchTime = 0;
    }
```

- [ ] **Step 2: Add `doPostHost()` private method**

Add this method after the existing `doPost()` method (after line 357):

```java
    private String doPostHost(JsonObject payload) throws IOException
    {
        if (!isConfigured()) throw new IOException("Bingo API not configured");

        payload.addProperty("key", apiKey != null ? apiKey : "");
        payload.addProperty("hostKey", hostKey != null ? hostKey : "");

        RequestBody body = RequestBody.create(JSON_TYPE, gson.toJson(payload));
        Request request = new Request.Builder().url(apiUrl).post(body).build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Bingo API POST returned status: " + response.code());
            }
            String responseBody = response.body().string();
            JsonObject root = new JsonParser().parse(responseBody).getAsJsonObject();

            if (root.has("status") && "error".equals(root.get("status").getAsString()))
            {
                String message = root.has("message") ? root.get("message").getAsString() : "Unknown error";
                throw new IOException(message);
            }
            return root.has("message") ? root.get("message").getAsString() : "OK";
        }
    }
```

- [ ] **Step 3: Add host-tier methods**

Add these methods after `adminUpdateRoster()`:

```java
    // Host-tier methods

    public String hostUpdateTile(String tileCode, String name, String type, String metric,
                                  double threshold, double max) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "hostUpdateTile");
        payload.addProperty("tileCode", tileCode);
        if (name != null) payload.addProperty("name", name);
        if (type != null) payload.addProperty("type", type);
        if (metric != null) payload.addProperty("metric", metric);
        payload.addProperty("threshold", threshold);
        payload.addProperty("max", max);
        return doPostHost(payload);
    }

    public String hostAddBounty(int number, String description, String releaseTime,
                                 double points) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "hostAddBounty");
        payload.addProperty("number", number);
        payload.addProperty("description", description);
        payload.addProperty("releaseTime", releaseTime);
        payload.addProperty("points", points);
        return doPostHost(payload);
    }

    public String hostUpdateBounty(int number, String winner) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "hostUpdateBounty");
        payload.addProperty("number", number);
        if (winner != null) payload.addProperty("winner", winner);
        return doPostHost(payload);
    }

    public String hostRemoveBounty(int number) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "hostRemoveBounty");
        payload.addProperty("number", number);
        return doPostHost(payload);
    }

    public String hostUpdateConfig(String configKey, String configValue) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "hostUpdateConfig");
        payload.addProperty("configKey", configKey);
        payload.addProperty("configValue", configValue);
        return doPostHost(payload);
    }

    public String hostUpdateWhitelist(String whitelistAction, String item,
                                       String tileCode, double points) throws IOException
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "hostUpdateWhitelist");
        payload.addProperty("whitelistAction", whitelistAction);
        payload.addProperty("item", item);
        if (tileCode != null) payload.addProperty("tileCode", tileCode);
        payload.addProperty("points", points);
        return doPostHost(payload);
    }
```

- [ ] **Step 4: Update existing `adminUpdateBounty()` to use host key**

The existing `adminUpdateBounty()` method (line 267) now routes through the host tier. Replace it:

```java
    public String adminUpdateBounty(int number, String winner) throws IOException
    {
        return hostUpdateBounty(number, winner);
    }
```

- [ ] **Step 5: Build to verify**

Run: `./gradlew build`
Expected: BUILD FAILURE — `ClanManagementPlugin.java` calls `configure(url, key, adminKey)` with 3 args but we changed it to 4. This is expected; Task 5 fixes it.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/droplogger/BingoService.java
git commit -m "feat(bingo): add host key support and host-tier methods to BingoService"
```

---

### Task 5: ClanManagementPlugin.java — Wire New Keys

**Files:**
- Modify: `src/main/java/com/droplogger/ClanManagementPlugin.java:886-890`

- [ ] **Step 1: Update `bingoService.configure()` call to pass all four keys**

In `refreshBingo()` (around line 887-889), replace:

```java
            bingoService.configure(bingoUrl, bingoKey,
                config.adminApiKey() != null ? config.adminApiKey() : "");
```

with:

```java
            bingoService.configure(bingoUrl, bingoKey,
                config.bingoAdminKey() != null ? config.bingoAdminKey() : "",
                config.bingoHostKey() != null ? config.bingoHostKey() : "");
```

- [ ] **Step 2: Build to verify everything compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/droplogger/ClanManagementPlugin.java
git commit -m "feat(bingo): wire bingoAdminKey and bingoHostKey through to BingoService"
```

---

### Task 6: Full Build Verification

- [ ] **Step 1: Clean build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no regressions**

> **Note on AdminPanel split:** The spec describes splitting AdminPanel into "Bingo Admin" and "Bingo Host" sections. AdminPanel currently has no bingo-specific UI — it only has clan management controls (shared settings, events, hiscore moderation). The bingo admin/host panel UI is a separate feature that depends on this backend work being complete. It should be planned as a follow-up after this foundation is in place.

Check that:
- Plugin compiles without warnings
- All existing admin actions still work (they route through the same handler functions, just with different action names for host-tier)
- Config panel shows the new Bingo Admin Key and Bingo Host Key fields

- [ ] **Step 3: Test in RuneLite dev client (optional)**

Run: `./gradlew runClient`
- Open plugin config, verify new "Bingo Admin Key" and "Bingo Host Key" fields appear in the Bingo section
- If a bingo API is configured, verify bounty descriptions show "???" for unreleased bounties
