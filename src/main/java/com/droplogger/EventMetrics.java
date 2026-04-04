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
        return null;
    }
}
