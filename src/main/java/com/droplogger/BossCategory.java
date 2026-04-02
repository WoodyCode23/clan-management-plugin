package com.droplogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven boss category registry for the expanded hiscore system.
 * Each entry maps a boss + party size to a unique key used for storage/lookup.
 * Replaces the old SpeedCategory enum's fixed sheet-row approach.
 */
public class BossCategory
{
    private static final List<BossCategory> ALL = new ArrayList<>();

    private final String key;           // e.g. "cox_solo", "bandos_duo"
    private final String group;         // internal group key, e.g. "cox", "bandos"
    private final String displayName;   // e.g. "Chambers of Xeric — Solo"
    private final String displayGroup;  // UI grouping, e.g. "Raids", "GWD"
    private final int minPlayers;
    private final int maxPlayers;
    private final boolean groupContent; // true = requires clan membership validation
    private final int legacySheetRow;   // row in old "3. Hiscore Tracking" sheet, -1 if none

    private BossCategory(String key, String group, String displayName, String displayGroup,
                         int minPlayers, int maxPlayers, boolean groupContent, int legacySheetRow)
    {
        this.key = key;
        this.group = group;
        this.displayName = displayName;
        this.displayGroup = displayGroup;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.groupContent = groupContent;
        this.legacySheetRow = legacySheetRow;
    }

    // ── Accessors ──

    public String getKey() { return key; }
    public String getGroup() { return group; }
    public String getDisplayName() { return displayName; }
    public String getDisplayGroup() { return displayGroup; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public boolean isGroupContent() { return groupContent; }
    public int getLegacySheetRow() { return legacySheetRow; }

    public String getSizeLabel()
    {
        if (minPlayers == 1 && maxPlayers == 1) return "Solo";
        if (minPlayers == 2 && maxPlayers == 2) return "Duo";
        if (minPlayers == 3 && maxPlayers == 3) return "Trio";
        if (minPlayers == 5 && maxPlayers == 5) return "5-Man";
        if (minPlayers == 4 && maxPlayers == 4) return "4-Man";
        if (minPlayers >= 6) return "6+";
        if (maxPlayers >= 100) return "Group";
        return minPlayers + "-" + maxPlayers;
    }

    // ── Registration helper ──

    private static BossCategory register(String key, String group, String displayName,
                                          String displayGroup, int minPlayers, int maxPlayers,
                                          boolean groupContent, int legacySheetRow)
    {
        BossCategory cat = new BossCategory(key, group, displayName, displayGroup,
            minPlayers, maxPlayers, groupContent, legacySheetRow);
        ALL.add(cat);
        return cat;
    }

    private static BossCategory register(String key, String group, String displayName,
                                          String displayGroup, int minPlayers, int maxPlayers,
                                          boolean groupContent)
    {
        return register(key, group, displayName, displayGroup, minPlayers, maxPlayers, groupContent, -1);
    }

    // ══════════════════════════════════════════════════════════════
    //  CATEGORY REGISTRY — ~70 categories across ~45 bosses
    // ══════════════════════════════════════════════════════════════

    // ── Raids ──

    public static final BossCategory COX_SOLO = register(
        "cox_solo", "cox", "Chambers of Xeric", "Raids", 1, 1, false, 10);
    public static final BossCategory COX_TRIO = register(
        "cox_trio", "cox", "Chambers of Xeric", "Raids", 3, 3, true, 11);
    public static final BossCategory COX_5MAN = register(
        "cox_5man", "cox", "Chambers of Xeric", "Raids", 5, 5, true, -1);
    public static final BossCategory COX_CM_SOLO = register(
        "cox_cm_solo", "cox_cm", "CM Chambers of Xeric", "Raids", 1, 1, false, 12);
    public static final BossCategory COX_CM_TRIO = register(
        "cox_cm_trio", "cox_cm", "CM Chambers of Xeric", "Raids", 3, 3, true, 13);
    public static final BossCategory COX_CM_5MAN = register(
        "cox_cm_5man", "cox_cm", "CM Chambers of Xeric", "Raids", 5, 5, true, 14);

    public static final BossCategory TOB_DUO = register(
        "tob_duo", "tob", "Theatre of Blood", "Raids", 2, 2, true, 15);
    public static final BossCategory TOB_TRIO = register(
        "tob_trio", "tob", "Theatre of Blood", "Raids", 3, 3, true, 16);
    public static final BossCategory TOB_4MAN = register(
        "tob_4man", "tob", "Theatre of Blood", "Raids", 4, 4, true, 17);
    public static final BossCategory TOB_5MAN = register(
        "tob_5man", "tob", "Theatre of Blood", "Raids", 5, 5, true, 18);

    public static final BossCategory TOA_SOLO = register(
        "toa_solo", "toa", "Tombs of Amascut", "Raids", 1, 1, false, 20);
    public static final BossCategory TOA_DUO = register(
        "toa_duo", "toa", "Tombs of Amascut", "Raids", 2, 2, true, 21);
    public static final BossCategory TOA_EXPERT_SOLO = register(
        "toa_expert_solo", "toa_expert", "Tombs of Amascut (Expert)", "Raids", 1, 1, false, 22);
    public static final BossCategory TOA_EXPERT_DUO = register(
        "toa_expert_duo", "toa_expert", "Tombs of Amascut (Expert)", "Raids", 2, 2, true, 23);

    // ── GWD ──

    public static final BossCategory BANDOS_SOLO = register(
        "bandos_solo", "bandos", "General Graardor", "GWD", 1, 1, false);
    public static final BossCategory BANDOS_DUO = register(
        "bandos_duo", "bandos", "General Graardor", "GWD", 2, 2, true);
    public static final BossCategory BANDOS_TRIO = register(
        "bandos_trio", "bandos", "General Graardor", "GWD", 3, 3, true);

    public static final BossCategory SARA_SOLO = register(
        "sara_solo", "sara", "Commander Zilyana", "GWD", 1, 1, false);
    public static final BossCategory SARA_DUO = register(
        "sara_duo", "sara", "Commander Zilyana", "GWD", 2, 2, true);
    public static final BossCategory SARA_TRIO = register(
        "sara_trio", "sara", "Commander Zilyana", "GWD", 3, 3, true);

    public static final BossCategory ZAMMY_SOLO = register(
        "zammy_solo", "zammy", "K'ril Tsutsaroth", "GWD", 1, 1, false);
    public static final BossCategory ZAMMY_DUO = register(
        "zammy_duo", "zammy", "K'ril Tsutsaroth", "GWD", 2, 2, true);
    public static final BossCategory ZAMMY_TRIO = register(
        "zammy_trio", "zammy", "K'ril Tsutsaroth", "GWD", 3, 3, true);

    public static final BossCategory ARMA_SOLO = register(
        "arma_solo", "arma", "Kree'arra", "GWD", 1, 1, false);
    public static final BossCategory ARMA_DUO = register(
        "arma_duo", "arma", "Kree'arra", "GWD", 2, 2, true);
    public static final BossCategory ARMA_TRIO = register(
        "arma_trio", "arma", "Kree'arra", "GWD", 3, 3, true);

    // ── DT2 ──

    public static final BossCategory DUKE = register(
        "duke_solo", "duke", "Duke Sucellus", "DT2", 1, 1, false, 31);
    public static final BossCategory LEVIATHAN = register(
        "levi_solo", "leviathan", "The Leviathan", "DT2", 1, 1, false, 32);
    public static final BossCategory VARDORVIS = register(
        "vardorvis_solo", "vardorvis", "Vardorvis", "DT2", 1, 1, false, 33);
    public static final BossCategory WHISPERER = register(
        "whisperer_solo", "whisperer", "The Whisperer", "DT2", 1, 1, false, 34);

    // ── Wave Content ──

    public static final BossCategory JAD = register(
        "jad_solo", "jad", "TzTok-Jad", "Wave", 1, 1, false, 24);
    public static final BossCategory ZUK = register(
        "zuk_solo", "zuk", "TzKal-Zuk", "Wave", 1, 1, false, 25);
    public static final BossCategory COLO = register(
        "colo_solo", "colo", "Sol Heredit", "Wave", 1, 1, false, 26);

    // ── Gauntlet ──

    public static final BossCategory GAUNTLET = register(
        "gauntlet_solo", "gaunt", "The Gauntlet", "Gauntlet", 1, 1, false, 27);
    public static final BossCategory CORRUPTED_GAUNTLET = register(
        "cg_solo", "gaunt_corrupted", "The Corrupted Gauntlet", "Gauntlet", 1, 1, false, 28);

    // ── Nightmare ──

    public static final BossCategory PHOSANIS = register(
        "phosani_solo", "phosanis", "Phosani's Nightmare", "Nightmare", 1, 1, false, 30);
    public static final BossCategory NIGHTMARE_GROUP = register(
        "nightmare_group", "nightmare", "The Nightmare", "Nightmare", 2, 100, true, 29);

    // ── Nex ──

    public static final BossCategory NEX_DUO = register(
        "nex_duo", "nex", "Nex", "Nex", 2, 2, true, 37);
    public static final BossCategory NEX_TRIO = register(
        "nex_trio", "nex", "Nex", "Nex", 3, 3, true, 38);

    // ── New Bosses ──

    public static final BossCategory ARAXXOR_SOLO = register(
        "araxxor_solo", "araxxor", "Araxxor", "New Bosses", 1, 1, false);

    public static final BossCategory TITANS_SOLO = register(
        "titans_solo", "titans", "Royal Titans", "New Bosses", 1, 1, false, 42);
    public static final BossCategory TITANS_DUO = register(
        "titans_duo", "titans", "Royal Titans", "New Bosses", 2, 2, true, 43);

    public static final BossCategory YAMA_SOLO = register(
        "yama_solo", "yama", "Yama", "New Bosses", 1, 1, false, 44);
    public static final BossCategory YAMA_DUO = register(
        "yama_duo", "yama", "Yama", "New Bosses", 2, 2, true, 45);

    public static final BossCategory HUEYCOATL_SOLO = register(
        "hueycoatl_solo", "hueycoatl", "Hueycoatl", "New Bosses", 1, 1, false);
    public static final BossCategory HUEYCOATL_DUO = register(
        "hueycoatl_duo", "hueycoatl", "Hueycoatl", "New Bosses", 2, 2, true);

    public static final BossCategory AMOXLIATL_SOLO = register(
        "amoxliatl_solo", "amoxliatl", "Amoxliatl", "New Bosses", 1, 1, false);

    // ── Slayer Bosses ──

    public static final BossCategory CERBERUS = register(
        "cerberus_solo", "cerberus", "Cerberus", "Slayer", 1, 1, false);
    public static final BossCategory HYDRA = register(
        "hydra_solo", "hydra", "Alchemical Hydra", "Slayer", 1, 1, false);
    public static final BossCategory THERMY = register(
        "thermy_solo", "thermy", "Thermonuclear Smoke Devil", "Slayer", 1, 1, false);
    public static final BossCategory KRAKEN = register(
        "kraken_solo", "kraken", "Kraken", "Slayer", 1, 1, false);
    public static final BossCategory SIRE = register(
        "sire_solo", "sire", "Abyssal Sire", "Slayer", 1, 1, false);
    public static final BossCategory GROTESQUE = register(
        "grotesque_solo", "grotesque", "Grotesque Guardians", "Slayer", 1, 1, false);
    public static final BossCategory SKOTIZO = register(
        "skotizo_solo", "skotizo", "Skotizo", "Slayer", 1, 1, false);

    // ── Other Bosses ──

    public static final BossCategory ZULRAH = register(
        "zulrah_solo", "zulrah", "Zulrah", "Other", 1, 1, false);
    public static final BossCategory VORKATH = register(
        "vorkath_solo", "vorkath", "Vorkath", "Other", 1, 1, false);
    public static final BossCategory KQ = register(
        "kq_solo", "kq", "Kalphite Queen", "Other", 1, 1, false);
    public static final BossCategory CORP_SOLO = register(
        "corp_solo", "corp", "Corporeal Beast", "Other", 1, 1, false);
    public static final BossCategory CORP_GROUP = register(
        "corp_group", "corp", "Corporeal Beast", "Other", 2, 100, true);
    public static final BossCategory MOLE = register(
        "mole_solo", "mole", "Giant Mole", "Other", 1, 1, false);
    public static final BossCategory SARACHNIS = register(
        "sarachnis_solo", "sarachnis", "Sarachnis", "Other", 1, 1, false);
    public static final BossCategory KBD = register(
        "kbd_solo", "kbd", "King Black Dragon", "Other", 1, 1, false);
    public static final BossCategory DKS = register(
        "dks_solo", "dks", "Dagannoth Kings", "Other", 1, 1, false);
    public static final BossCategory BARROWS = register(
        "barrows_solo", "barrows", "Barrows", "Other", 1, 1, false);
    public static final BossCategory BRYOPHYTA = register(
        "bryophyta_solo", "bryophyta", "Bryophyta", "Other", 1, 1, false);
    public static final BossCategory OBOR = register(
        "obor_solo", "obor", "Obor", "Other", 1, 1, false);
    public static final BossCategory HESPORI = register(
        "hespori_solo", "hespori", "Hespori", "Other", 1, 1, false);

    // ── Wilderness Bosses ──

    public static final BossCategory CALLISTO = register(
        "callisto_solo", "callisto", "Callisto", "Wilderness", 1, 1, false);
    public static final BossCategory VETION = register(
        "vetion_solo", "vetion", "Vet'ion", "Wilderness", 1, 1, false);
    public static final BossCategory VENENATIS = register(
        "venenatis_solo", "venenatis", "Venenatis", "Wilderness", 1, 1, false);
    public static final BossCategory CHAOS_ELE = register(
        "chaos_ele_solo", "chaos_ele", "Chaos Elemental", "Wilderness", 1, 1, false);
    public static final BossCategory SCORPIA = register(
        "scorpia_solo", "scorpia", "Scorpia", "Wilderness", 1, 1, false);
    public static final BossCategory CRAZY_ARCH = register(
        "crazy_arch_solo", "crazy_arch", "Crazy Archaeologist", "Wilderness", 1, 1, false);

    // ── Challenges ──

    public static final BossCategory RAKS_1ST = register(
        "raks_1st", "raks", "TzHaar-Ket-Rak 1st", "Challenges", 1, 1, false, 39);
    public static final BossCategory RAKS_3RD = register(
        "raks_3rd", "raks", "TzHaar-Ket-Rak 3rd", "Challenges", 1, 1, false, 40);
    public static final BossCategory RAKS_5TH = register(
        "raks_5th", "raks", "TzHaar-Ket-Rak 5th", "Challenges", 1, 1, false, 41);
    public static final BossCategory BA = register(
        "ba_5man", "ba", "Barbarian Assault", "Challenges", 5, 5, true, 36);

    // ── Sepulchre ──

    public static final BossCategory SEPULCHRE = register(
        "sepulchre_solo", "sep", "Hallowed Sepulchre", "Sepulchre", 1, 1, false);

    // ══════════════════════════════════════════════════════════════
    //  LOOKUP METHODS
    // ══════════════════════════════════════════════════════════════

    /**
     * Find the best matching category for a group key and party size.
     * Same logic as the old SpeedCategory.find() — prefers the most specific match.
     */
    public static BossCategory find(String group, int partySize)
    {
        BossCategory best = null;
        for (BossCategory cat : ALL)
        {
            if (!cat.group.equals(group))
            {
                continue;
            }
            if (partySize >= cat.minPlayers && partySize <= cat.maxPlayers)
            {
                if (best == null || (cat.maxPlayers - cat.minPlayers) < (best.maxPlayers - best.minPlayers))
                {
                    best = cat;
                }
            }
        }
        return best;
    }

    /**
     * Look up a category by its unique key (e.g. "cox_solo").
     */
    public static BossCategory getByKey(String key)
    {
        for (BossCategory cat : ALL)
        {
            if (cat.key.equals(key))
            {
                return cat;
            }
        }
        return null;
    }

    /**
     * Get all categories grouped by display group, preserving registration order.
     * Returns a map like {"Raids": [...], "GWD": [...], ...}.
     */
    public static Map<String, List<BossCategory>> getAllByDisplayGroup()
    {
        Map<String, List<BossCategory>> grouped = new LinkedHashMap<>();
        for (BossCategory cat : ALL)
        {
            grouped.computeIfAbsent(cat.displayGroup, k -> new ArrayList<>()).add(cat);
        }
        return Collections.unmodifiableMap(grouped);
    }

    /**
     * Get all registered categories.
     */
    public static List<BossCategory> getAll()
    {
        return Collections.unmodifiableList(ALL);
    }

    /**
     * Get distinct boss names within a display group, preserving order.
     * Useful for populating the boss dropdown in the UI.
     */
    public static List<String> getBossNamesInGroup(String displayGroup)
    {
        List<String> names = new ArrayList<>();
        for (BossCategory cat : ALL)
        {
            if (cat.displayGroup.equals(displayGroup) && !names.contains(cat.displayName))
            {
                names.add(cat.displayName);
            }
        }
        return names;
    }

    /**
     * Get all categories matching a display group and boss display name.
     * Useful for populating the size dropdown after boss selection.
     */
    public static List<BossCategory> getCategoriesForBoss(String displayGroup, String displayName)
    {
        List<BossCategory> result = new ArrayList<>();
        for (BossCategory cat : ALL)
        {
            if (cat.displayGroup.equals(displayGroup) && cat.displayName.equals(displayName))
            {
                result.add(cat);
            }
        }
        return result;
    }

    /**
     * Get all display group names in registration order.
     */
    public static List<String> getDisplayGroupNames()
    {
        List<String> names = new ArrayList<>();
        for (BossCategory cat : ALL)
        {
            if (!names.contains(cat.displayGroup))
            {
                names.add(cat.displayGroup);
            }
        }
        return names;
    }

    @Override
    public String toString()
    {
        return key + " (" + displayName + " — " + getSizeLabel() + ")";
    }
}
