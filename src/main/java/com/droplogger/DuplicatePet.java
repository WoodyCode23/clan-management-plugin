package com.droplogger;

import java.util.Map;

/**
 * Works out which pet a duplicate was. The game's line ("You have a funny feeling like you would have
 * been followed...") names no pet, so: a boss killed within the last minute means that boss's pet;
 * otherwise the one pet skill trained in the last 30 seconds means its skilling pet; anything else
 * posts nothing, because a wrong pet in the clan feed is worse than a missing one.
 */
final class DuplicatePet
{
    static final long BOSS_WINDOW_MS = 60_000;
    static final long SKILL_WINDOW_MS = 30_000;

    /** Skill name (as RuneLite's Skill.getName()) to its pet. */
    private static final Map<String, String> SKILL_PET = Map.of(
        "Woodcutting", "Beaver",
        "Mining", "Rock golem",
        "Fishing", "Heron",
        "Agility", "Giant squirrel",
        "Thieving", "Rocky",
        "Runecraft", "Rift guardian",
        "Farming", "Tangleroot",
        "Hunter", "Baby chinchompa");

    private DuplicatePet()
    {
    }

    /**
     * @param bossPet         the last boss's pet, or null when the boss has none mapped
     * @param bossName        the last boss, as named in its kill-count line
     * @param msSinceBoss     how long ago that kill-count line arrived
     * @param msSinceSkillXp  skill name to how long ago it last gained XP
     * @return {pet, source}, or null to post nothing
     */
    static String[] resolve(String bossPet, String bossName, long msSinceBoss, Map<String, Long> msSinceSkillXp)
    {
        if (bossPet != null && msSinceBoss >= 0 && msSinceBoss <= BOSS_WINDOW_MS)
        {
            return new String[]{bossPet, bossName};
        }
        String skill = null;
        for (Map.Entry<String, Long> e : msSinceSkillXp.entrySet())
        {
            Long age = e.getValue();
            if (!SKILL_PET.containsKey(e.getKey()) || age == null || age < 0 || age > SKILL_WINDOW_MS) continue;
            if (skill != null) return null; // two pet skills at once: can't tell which pet
            skill = e.getKey();
        }
        return skill == null ? null : new String[]{SKILL_PET.get(skill), skill};
    }
}
