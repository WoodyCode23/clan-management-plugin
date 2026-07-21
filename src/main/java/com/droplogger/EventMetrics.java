package com.droplogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static WOM metric name to display name mappings for weekly events.
 */
public final class EventMetrics
{
    private EventMetrics() {}

    /** Skill metrics: WOM metric name -> display name. */
    public static final Map<String, String> SKILLS = new LinkedHashMap<>();

    /** Boss metrics: WOM metric name -> display name. */
    public static final Map<String, String> BOSSES = new LinkedHashMap<>();

    /** Activity metrics: WOM metric name -> display name. */
    public static final Map<String, String> ACTIVITIES = new LinkedHashMap<>();

    /** Clue metrics: WOM metric name -> display name. */
    public static final Map<String, String> CLUES = new LinkedHashMap<>();

    static
    {
        // Skills
        SKILLS.put("overall", "Overall");
        SKILLS.put("attack", "Attack");
        SKILLS.put("defence", "Defence");
        SKILLS.put("strength", "Strength");
        SKILLS.put("hitpoints", "Hitpoints");
        SKILLS.put("ranged", "Ranged");
        SKILLS.put("prayer", "Prayer");
        SKILLS.put("magic", "Magic");
        SKILLS.put("cooking", "Cooking");
        SKILLS.put("woodcutting", "Woodcutting");
        SKILLS.put("fletching", "Fletching");
        SKILLS.put("fishing", "Fishing");
        SKILLS.put("firemaking", "Firemaking");
        SKILLS.put("crafting", "Crafting");
        SKILLS.put("smithing", "Smithing");
        SKILLS.put("mining", "Mining");
        SKILLS.put("herblore", "Herblore");
        SKILLS.put("agility", "Agility");
        SKILLS.put("thieving", "Thieving");
        SKILLS.put("slayer", "Slayer");
        SKILLS.put("farming", "Farming");
        SKILLS.put("runecrafting", "Runecrafting");
        SKILLS.put("hunter", "Hunter");
        SKILLS.put("construction", "Construction");

        // Bosses (alphabetical by display name)
        BOSSES.put("abyssal_sire", "Abyssal Sire");
        BOSSES.put("alchemical_hydra", "Alchemical Hydra");
        BOSSES.put("amoxliatl", "Amoxliatl");
        BOSSES.put("araxxor", "Araxxor");
        BOSSES.put("artio", "Artio");
        BOSSES.put("barrows_chests", "Barrows Chests");
        BOSSES.put("bryophyta", "Bryophyta");
        BOSSES.put("callisto", "Callisto");
        BOSSES.put("calvarion", "Calvar'ion");
        BOSSES.put("cerberus", "Cerberus");
        BOSSES.put("chambers_of_xeric", "Chambers of Xeric");
        BOSSES.put("chambers_of_xeric_challenge_mode", "Chambers of Xeric (CM)");
        BOSSES.put("chaos_elemental", "Chaos Elemental");
        BOSSES.put("chaos_fanatic", "Chaos Fanatic");
        BOSSES.put("commander_zilyana", "Commander Zilyana");
        BOSSES.put("corporeal_beast", "Corporeal Beast");
        BOSSES.put("crazy_archaeologist", "Crazy Archaeologist");
        BOSSES.put("dagannoth_prime", "Dagannoth Prime");
        BOSSES.put("dagannoth_rex", "Dagannoth Rex");
        BOSSES.put("dagannoth_supreme", "Dagannoth Supreme");
        BOSSES.put("deranged_archaeologist", "Deranged Archaeologist");
        BOSSES.put("duke_sucellus", "Duke Sucellus");
        BOSSES.put("general_graardor", "General Graardor");
        BOSSES.put("giant_mole", "Giant Mole");
        BOSSES.put("grotesque_guardians", "Grotesque Guardians");
        BOSSES.put("hespori", "Hespori");
        BOSSES.put("hueycoatl", "Hueycoatl");
        BOSSES.put("kalphite_queen", "Kalphite Queen");
        BOSSES.put("king_black_dragon", "King Black Dragon");
        BOSSES.put("kraken", "Kraken");
        BOSSES.put("kreearra", "Kree'arra");
        BOSSES.put("kril_tsutsaroth", "K'ril Tsutsaroth");
        BOSSES.put("lunar_chests", "Lunar Chests");
        BOSSES.put("mimic", "Mimic");
        BOSSES.put("nex", "Nex");
        BOSSES.put("nightmare", "Nightmare");
        BOSSES.put("obor", "Obor");
        BOSSES.put("phantom_muspah", "Phantom Muspah");
        BOSSES.put("phosanis_nightmare", "Phosani's Nightmare");
        BOSSES.put("royal_titans", "Royal Titans");
        BOSSES.put("sarachnis", "Sarachnis");
        BOSSES.put("scorpia", "Scorpia");
        BOSSES.put("scurrius", "Scurrius");
        BOSSES.put("skotizo", "Skotizo");
        BOSSES.put("sol_heredit", "Sol Heredit");
        BOSSES.put("spindel", "Spindel");
        BOSSES.put("tempoross", "Tempoross");
        BOSSES.put("the_gauntlet", "The Gauntlet");
        BOSSES.put("the_corrupted_gauntlet", "The Corrupted Gauntlet");
        BOSSES.put("the_leviathan", "The Leviathan");
        BOSSES.put("the_whisperer", "The Whisperer");
        BOSSES.put("theatre_of_blood", "Theatre of Blood");
        BOSSES.put("theatre_of_blood_hard_mode", "Theatre of Blood (HM)");
        BOSSES.put("thermonuclear_smoke_devil", "Thermonuclear Smoke Devil");
        BOSSES.put("tombs_of_amascut", "Tombs of Amascut");
        BOSSES.put("tombs_of_amascut_expert", "Tombs of Amascut (Expert)");
        BOSSES.put("tzkal_zuk", "TzKal-Zuk");
        BOSSES.put("tztok_jad", "TzTok-Jad");
        BOSSES.put("vardorvis", "Vardorvis");
        BOSSES.put("venenatis", "Venenatis");
        BOSSES.put("vetion", "Vet'ion");
        BOSSES.put("vorkath", "Vorkath");
        BOSSES.put("wintertodt", "Wintertodt");
        BOSSES.put("yama", "Yama");
        BOSSES.put("zalcano", "Zalcano");
        BOSSES.put("zulrah", "Zulrah");

        // Activities (alphabetical by display name)
        ACTIVITIES.put("bounty_hunter_hunter", "Bounty Hunter — Hunter");
        ACTIVITIES.put("bounty_hunter_rogue", "Bounty Hunter — Rogue");
        ACTIVITIES.put("colosseum_glory", "Colosseum Glory");
        ACTIVITIES.put("guardians_of_the_rift", "Guardians of the Rift");
        ACTIVITIES.put("last_man_standing", "Last Man Standing");
        ACTIVITIES.put("league_points", "League Points");
        ACTIVITIES.put("pvp_arena", "PvP Arena");
        ACTIVITIES.put("rifts_closed", "Rifts Closed");
        ACTIVITIES.put("soul_wars_zeal", "Soul Wars");
        ACTIVITIES.put("volcanic_mine", "Volcanic Mine");

        // Clue tiers
        CLUES.put("clue_scrolls_all", "All Clues");
        CLUES.put("clue_scrolls_beginner", "Beginner");
        CLUES.put("clue_scrolls_easy", "Easy");
        CLUES.put("clue_scrolls_medium", "Medium");
        CLUES.put("clue_scrolls_hard", "Hard");
        CLUES.put("clue_scrolls_elite", "Elite");
        CLUES.put("clue_scrolls_master", "Master");
    }

    /** Get display names sorted for a dropdown. */
    public static String[] getSkillDisplayNames()
    {
        return SKILLS.values().toArray(new String[0]);
    }

    public static String[] getBossDisplayNames()
    {
        return BOSSES.values().toArray(new String[0]);
    }

    public static String[] getActivityDisplayNames()
    {
        return ACTIVITIES.values().toArray(new String[0]);
    }

    public static String[] getClueDisplayNames()
    {
        return CLUES.values().toArray(new String[0]);
    }

    /** Look up WOM metric name from display name. */
    public static String metricFromDisplayName(String displayName)
    {
        for (Map.Entry<String, String> entry : SKILLS.entrySet())
        {
            if (entry.getValue().equals(displayName)) return entry.getKey();
        }
        for (Map.Entry<String, String> entry : BOSSES.entrySet())
        {
            if (entry.getValue().equals(displayName)) return entry.getKey();
        }
        for (Map.Entry<String, String> entry : ACTIVITIES.entrySet())
        {
            if (entry.getValue().equals(displayName)) return entry.getKey();
        }
        for (Map.Entry<String, String> entry : CLUES.entrySet())
        {
            if (entry.getValue().equals(displayName)) return entry.getKey();
        }
        return null;
    }

    // WOM metric -> item id for icons (generated from the website's BOSS_KC_ICONS so the
    // two surfaces can't drift; clue tiers added on top).
    private static final java.util.Map<String, Integer> ICON_ITEM_IDS = new java.util.HashMap<>();
    static
    {
        ICON_ITEM_IDS.put("abyssal_sire", 13262);
        ICON_ITEM_IDS.put("alchemical_hydra", 22746);
        ICON_ITEM_IDS.put("amoxliatl", 30154);
        ICON_ITEM_IDS.put("araxxor", 29836);
        ICON_ITEM_IDS.put("artio", 13178);
        ICON_ITEM_IDS.put("barrows_chests", 4708);
        ICON_ITEM_IDS.put("bryophyta", 22372);
        ICON_ITEM_IDS.put("callisto", 13178);
        ICON_ITEM_IDS.put("calvarion", 13179);
        ICON_ITEM_IDS.put("cerberus", 13247);
        ICON_ITEM_IDS.put("chambers_of_xeric", 20851);
        ICON_ITEM_IDS.put("chambers_of_xeric_challenge_mode", 22386);
        ICON_ITEM_IDS.put("chaos_elemental", 11995);
        ICON_ITEM_IDS.put("chaos_fanatic", 11995);
        ICON_ITEM_IDS.put("commander_zilyana", 12651);
        ICON_ITEM_IDS.put("corporeal_beast", 12816);
        ICON_ITEM_IDS.put("crazy_archaeologist", 11990);
        ICON_ITEM_IDS.put("dagannoth_prime", 12644);
        ICON_ITEM_IDS.put("dagannoth_rex", 12645);
        ICON_ITEM_IDS.put("dagannoth_supreme", 12643);
        ICON_ITEM_IDS.put("deranged_archaeologist", 11990);
        ICON_ITEM_IDS.put("duke_sucellus", 28250);
        ICON_ITEM_IDS.put("fortis_colosseum", 28960);
        ICON_ITEM_IDS.put("general_graardor", 12650);
        ICON_ITEM_IDS.put("giant_mole", 12646);
        ICON_ITEM_IDS.put("grotesque_guardians", 21748);
        ICON_ITEM_IDS.put("hespori", 22875);
        ICON_ITEM_IDS.put("hueycoatl", 30152);
        ICON_ITEM_IDS.put("kalphite_queen", 12647);
        ICON_ITEM_IDS.put("king_black_dragon", 12653);
        ICON_ITEM_IDS.put("kraken", 12655);
        ICON_ITEM_IDS.put("kreearra", 12649);
        ICON_ITEM_IDS.put("kril_tsutsaroth", 12652);
        ICON_ITEM_IDS.put("lunar_chests", 29836);
        ICON_ITEM_IDS.put("nex", 26348);
        ICON_ITEM_IDS.put("nightmare", 24491);
        ICON_ITEM_IDS.put("obor", 20756);
        ICON_ITEM_IDS.put("phantom_muspah", 27590);
        ICON_ITEM_IDS.put("phosanis_nightmare", 24491);
        ICON_ITEM_IDS.put("sarachnis", 23495);
        ICON_ITEM_IDS.put("scorpia", 13181);
        ICON_ITEM_IDS.put("scurrius", 28801);
        ICON_ITEM_IDS.put("skotizo", 21273);
        ICON_ITEM_IDS.put("sol_heredit", 28960);
        ICON_ITEM_IDS.put("spindel", 13177);
        ICON_ITEM_IDS.put("tempoross", 25602);
        ICON_ITEM_IDS.put("the_corrupted_gauntlet", 23759);
        ICON_ITEM_IDS.put("the_gauntlet", 23757);
        ICON_ITEM_IDS.put("the_leviathan", 28252);
        ICON_ITEM_IDS.put("the_whisperer", 28246);
        ICON_ITEM_IDS.put("theatre_of_blood", 22473);
        ICON_ITEM_IDS.put("theatre_of_blood_hard_mode", 22473);
        ICON_ITEM_IDS.put("thermonuclear_smoke_devil", 12648);
        ICON_ITEM_IDS.put("tombs_of_amascut", 27352);
        ICON_ITEM_IDS.put("tombs_of_amascut_expert", 27352);
        ICON_ITEM_IDS.put("tzkal_zuk", 21291);
        ICON_ITEM_IDS.put("tztok_jad", 13225);
        ICON_ITEM_IDS.put("vardorvis", 28248);
        ICON_ITEM_IDS.put("venenatis", 13177);
        ICON_ITEM_IDS.put("vetion", 13179);
        ICON_ITEM_IDS.put("vorkath", 21992);
        ICON_ITEM_IDS.put("yama", 29622);
        ICON_ITEM_IDS.put("zalcano", 23760);
        ICON_ITEM_IDS.put("zulrah", 12921);
        ICON_ITEM_IDS.put("clue_scrolls_all", 2714);
        ICON_ITEM_IDS.put("clue_scrolls_beginner", 23182);
        ICON_ITEM_IDS.put("clue_scrolls_easy", 2677);
        ICON_ITEM_IDS.put("clue_scrolls_medium", 2801);
        ICON_ITEM_IDS.put("clue_scrolls_hard", 2722);
        ICON_ITEM_IDS.put("clue_scrolls_elite", 12073);
        ICON_ITEM_IDS.put("clue_scrolls_master", 19835);
    }

    /** Item id to draw as this metric's icon, or null when only a sprite (skills) fits. */
    public static Integer iconItemId(String metric)
    {
        return metric == null ? null : ICON_ITEM_IDS.get(metric.toLowerCase());
    }

    /** Get the event type string from an event type label. */
    public static String typeFromLabel(String label)
    {
        if (label == null) return null;
        if (label.startsWith("Boss")) return "boss";
        if (label.startsWith("Skill")) return "skill";
        if (label.startsWith("Gamer")) return "gamer";
        if (label.startsWith("Clue")) return "clue";
        return null;
    }

    /** Get the display label for an event type. */
    public static String labelFromType(String type)
    {
        if (type == null) return "Event";
        switch (type)
        {
            case "boss": return "Boss of the Week";
            case "skill": return "Skill of the Week";
            case "gamer": return "Gamer of the Week";
            case "clue": return "Clue Hunter of the Week";
            default: return "Event";
        }
    }

    /** Get the accent color for an event type. */
    public static java.awt.Color colorFromType(String type)
    {
        if (type == null) return new java.awt.Color(200, 200, 200);
        switch (type)
        {
            case "boss": return new java.awt.Color(231, 76, 60);      // red
            case "skill": return new java.awt.Color(46, 204, 113);     // green
            case "gamer": return new java.awt.Color(155, 89, 182);     // purple
            case "clue": return new java.awt.Color(243, 156, 18);      // orange
            default: return new java.awt.Color(200, 200, 200);
        }
    }

    /** Get the Discord embed color for an event type. */
    public static int discordColorFromType(String type)
    {
        if (type == null) return 0x95A5A6;
        switch (type)
        {
            case "boss": return 0xE74C3C;
            case "skill": return 0x2ECC71;
            case "gamer": return 0x9B59B6;
            case "clue": return 0xF39C12;
            default: return 0x95A5A6;
        }
    }

    /** Get the unit string for leaderboard display. */
    public static String unitFromType(String type)
    {
        if (type == null) return "";
        switch (type)
        {
            case "boss": return " KC";
            case "skill": return " XP";
            case "gamer": return " score";
            case "clue": return " completed";
            default: return "";
        }
    }
}
