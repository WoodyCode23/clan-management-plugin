package com.droplogger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * A duplicate pet's chat line names no pet, so it is worked out from context. On 2026-10-09 a beaver
 * dupe while woodcutting was posted as an Olmlet because the last boss, hours earlier, was a raid.
 */
public class DuplicatePetTest
{
    private static Map<String, Long> xp(Object... skillAndAge)
    {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < skillAndAge.length; i += 2) m.put((String) skillAndAge[i], ((Number) skillAndAge[i + 1]).longValue());
        return m;
    }

    @Test
    public void aBossKilledJustNowGetsItsPet()
    {
        assertArrayEquals(new String[]{"Olmlet", "Chambers of Xeric"},
            DuplicatePet.resolve("Olmlet", "Chambers of Xeric", 5_000, xp()));
    }

    @Test
    public void aStaleBossIsIgnoredAndTheSkillBeingTrainedWins()
    {
        assertArrayEquals(new String[]{"Beaver", "Woodcutting"},
            DuplicatePet.resolve("Olmlet", "Chambers of Xeric", 3 * 3600_000L, xp("Woodcutting", 2_000)));
    }

    @Test
    public void noBossAndNoRecentSkillPostsNothing()
    {
        assertNull(DuplicatePet.resolve("Olmlet", "Chambers of Xeric", 3 * 3600_000L, xp("Woodcutting", 5 * 60_000)));
        assertNull(DuplicatePet.resolve(null, null, Long.MAX_VALUE, xp()));
    }

    @Test
    public void twoPetSkillsAtOnceIsAmbiguousSoNothingIsPosted()
    {
        // Barbarian fishing trains Fishing and Agility together: Heron or Giant squirrel.
        assertNull(DuplicatePet.resolve(null, null, Long.MAX_VALUE, xp("Fishing", 1_000, "Agility", 1_000)));
    }

    @Test
    public void skillsWithoutAPetDoNotCount()
    {
        assertArrayEquals(new String[]{"Rock golem", "Mining"},
            DuplicatePet.resolve(null, null, Long.MAX_VALUE, xp("Mining", 1_000, "Strength", 1_000, "Hitpoints", 1_000)));
    }

    @Test
    public void eachSkillingPet()
    {
        String[][] cases = {
            {"Woodcutting", "Beaver"}, {"Mining", "Rock golem"}, {"Fishing", "Heron"}, {"Agility", "Giant squirrel"},
            {"Thieving", "Rocky"}, {"Runecraft", "Rift guardian"}, {"Farming", "Tangleroot"}, {"Hunter", "Baby chinchompa"},
        };
        for (String[] c : cases)
        {
            assertArrayEquals(new String[]{c[1], c[0]}, DuplicatePet.resolve(null, null, Long.MAX_VALUE, xp(c[0], 1_000)));
        }
    }
}
