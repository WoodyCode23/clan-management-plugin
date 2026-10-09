package com.droplogger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** The KC line on a member's collection log page: raids show each mode, other bosses one count. */
public class CategoryKcTest
{
    private static Map<String, Integer> kc()
    {
        Map<String, Integer> m = new HashMap<>();
        // Keys as the server sends them (official hiscore names).
        m.put("chambers_of_xeric", 1234);
        m.put("chambers_of_xeric:_challenge_mode", 56);
        m.put("theatre_of_blood", 300);
        m.put("theatre_of_blood:_hard_mode", 12);
        m.put("tombs_of_amascut", 450);
        m.put("tombs_of_amascut:_expert_mode", 88);
        m.put("abyssal_sire", 77);
        return m;
    }

    @Test
    public void coxShowsNormalAndChallengeMode()
    {
        assertEquals("KC: 1,234  ·  CM: 56", CategoryKc.line("Chambers of Xeric", kc()));
    }

    @Test
    public void tobShowsNormalAndHardMode()
    {
        assertEquals("KC: 300  ·  HM: 12", CategoryKc.line("Theatre of Blood", kc()));
    }

    @Test
    public void toaShowsNormalAndExpert()
    {
        assertEquals("KC: 450  ·  Expert: 88", CategoryKc.line("Tombs of Amascut", kc()));
    }

    @Test
    public void aRaidModeWithNoKillsIsLeftOut()
    {
        Map<String, Integer> m = new HashMap<>();
        m.put("chambers_of_xeric", 10);
        assertEquals("KC: 10", CategoryKc.line("Chambers of Xeric", m));
        Map<String, Integer> cmOnly = new HashMap<>();
        cmOnly.put("chambers_of_xeric:_challenge_mode", 5);
        assertEquals("CM: 5", CategoryKc.line("Chambers of Xeric", cmOnly));
    }

    @Test
    public void otherBossesShowOneCount()
    {
        assertEquals("KC: 77", CategoryKc.line("Abyssal Sire", kc()));
    }

    @Test
    public void nothingForCategoriesWithoutKc()
    {
        assertNull(CategoryKc.line("Clue scrolls (easy)", kc()));
        assertNull(CategoryKc.line("Chambers of Xeric", new HashMap<>()));
        assertNull(CategoryKc.line(null, kc()));
    }
}
