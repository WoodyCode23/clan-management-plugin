package com.droplogger;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Hard-mode raid KC (CoX CM, ToB HM, ToA Expert) counts 1.5x toward raid KC requirements. */
public class RankRaidKcWeightTest
{
    private static final String COX = "chambers_of_xeric", COX_CM = "chambers_of_xeric:_challenge_mode";
    private static final String TOB = "theatre_of_blood", TOB_HM = "theatre_of_blood:_hard_mode";
    private static final String TOA = "tombs_of_amascut", TOA_EX = "tombs_of_amascut:_expert_mode";

    private static RankSystem.PlayerSnapshot snap(Object... kv)
    {
        RankSystem.PlayerSnapshot s = new RankSystem.PlayerSnapshot();
        for (int i = 0; i < kv.length; i += 2) s.kc.put((String) kv[i], (Integer) kv[i + 1]);
        return s;
    }

    private static boolean meets(String key, int count, RankSystem.PlayerSnapshot s)
    {
        return RankSystem.evalCheck(RankSystem.Check.kc("req", count, key), s);
    }

    @Test public void seventyFiveCmAloneMeetsHundredRaids()
    {
        assertTrue(meets("raids_combined", 100, snap(COX_CM, 75)));
        assertTrue(meets("cox_total", 100, snap(COX_CM, 75)));
        assertEquals(112.5, RankSystem.effectiveKc("raids_combined", snap(COX_CM, 75).kc), 0.0);
    }

    @Test public void fiftyNormalPlusThirtyFourCmMeetsHundred()
    {
        assertTrue(meets("cox_total", 100, snap(COX, 50, COX_CM, 34)));     // 50 + 51 = 101
        assertTrue(meets("raids_combined", 100, snap(COX, 50, COX_CM, 34)));
    }

    @Test public void fiftyNormalPlusThirtyThreeCmDoesNotMeetHundred()
    {
        // 50 + 49.5 = 99.5: compared unrounded, so it falls short.
        assertFalse(meets("cox_total", 100, snap(COX, 50, COX_CM, 33)));
        assertFalse(meets("raids_combined", 100, snap(COX, 50, COX_CM, 33)));
        assertEquals(99.5, RankSystem.effectiveKc("cox_total", snap(COX, 50, COX_CM, 33).kc), 0.0);
    }

    @Test public void tobHardModeWeightsTheSame()
    {
        assertTrue(meets("tob_total", 100, snap(TOB_HM, 75)));
        assertTrue(meets("tob_total", 100, snap(TOB, 50, TOB_HM, 34)));
        assertFalse(meets("tob_total", 100, snap(TOB, 50, TOB_HM, 33)));
        assertTrue(meets(TOB, 100, snap(TOB, 50, TOB_HM, 34)));
    }

    @Test public void toaExpertWeightsTheSame()
    {
        assertTrue(meets("toa_total", 100, snap(TOA_EX, 75)));
        assertTrue(meets("toa_total", 100, snap(TOA, 50, TOA_EX, 34)));
        assertFalse(meets("toa_total", 100, snap(TOA, 50, TOA_EX, 33)));
        assertTrue(meets(TOA, 100, snap(TOA, 50, TOA_EX, 34)));
    }

    @Test public void combinedSumsAllThreeWeighted()
    {
        // 10 + 1.5*10 + 10 + 1.5*10 + 10 + 1.5*10 = 75
        RankSystem.PlayerSnapshot s = snap(COX, 10, COX_CM, 10, TOB, 10, TOB_HM, 10, TOA, 10, TOA_EX, 10);
        assertEquals(75.0, RankSystem.effectiveKc("raids_combined", s.kc), 0.0);
        assertTrue(meets("raids_combined", 75, s));
        assertFalse(meets("raids_combined", 76, s));
        // Normal KC alone still counts 1x, exactly as before.
        assertTrue(meets("raids_combined", 100, snap(COX, 40, TOB, 30, TOA, 30)));
        assertFalse(meets("raids_combined", 100, snap(COX, 40, TOB, 30, TOA, 29)));
    }

    @Test public void hardModeSpecificRequirementsAreNotWeighted()
    {
        assertTrue(meets(COX_CM, 50, snap(COX_CM, 50)));
        assertFalse(meets(COX_CM, 50, snap(COX_CM, 49, COX, 500)));
        assertFalse(meets(TOB_HM, 50, snap(TOB_HM, 34)));   // 34 HM is 34, not 51
        assertFalse(meets(TOA_EX, 50, snap(TOA_EX, 34)));
        assertEquals(34.0, RankSystem.effectiveKc(TOA_EX, snap(TOA_EX, 34).kc), 0.0);
    }

    @Test public void nonRaidBossKcUnchanged()
    {
        assertTrue(meets("zulrah", 512, snap("zulrah", 512)));
        assertFalse(meets("zulrah", 512, snap("zulrah", 511, COX_CM, 1000)));
        assertTrue(meets("god_wars_dungeon", 508, snap("god_wars_dungeon", 508)));
        assertFalse(meets("vorkath", 1, snap()));
    }

    @Test public void keysAreCaseInsensitive()
    {
        assertTrue(meets("RAIDS_COMBINED", 100, snap(COX_CM, 75)));
        assertTrue(meets("Chambers_Of_Xeric", 100, snap(COX_CM, 75)));
    }

    @Test public void serverJsonRequirementIsWeighted()
    {
        // Same shape the website rank editor saves (BOSS_KC with an explicit key).
        String json = "[{\"id\":\"r\",\"name\":\"R\",\"path\":\"\",\"desc\":\"\",\"requires\":[],"
            + "\"groups\":[{\"label\":\"g\",\"need\":1,\"options\":["
            + "{\"kind\":\"BOSS_KC\",\"label\":\"100 combined raid KC\",\"boss\":\"raids_combined\",\"count\":100,\"key\":\"raids_combined\"}"
            + "]}]}]";
        List<RankSystem.Rank> ranks = RankSystem.parseRanks(json);
        RankSystem.Check c = ranks.get(0).groups.get(0).options.get(0);
        assertTrue(RankSystem.evalCheck(c, snap(TOB_HM, 75)));
        assertFalse(RankSystem.evalCheck(c, snap(TOB, 50, TOB_HM, 33)));
    }
}
