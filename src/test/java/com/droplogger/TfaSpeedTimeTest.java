package com.droplogger;

import org.junit.Test;
import static org.junit.Assert.*;

// The Fractured Archive (raid 4, released 2026-10-20) on the speed boards. The game's lines, as
// RuneLite's own chat commands plugin reads them:
//   "Challenge complete: Ocellus. Duration: 2:25.80. Total: 2:25.80 (new personal best)"  (one per boss)
//   "The Fractured Archive: Total completion time: 2:25.80 (new personal best)"
//   "Your completed Fractured Archive count is: 1."
// The board takes the total completion time, like ToA's.
public class TfaSpeedTimeTest
{
    private static PbDetector.CompletionResult run(String... messages)
    {
        PbDetector d = new PbDetector();
        PbDetector.CompletionResult result = null;
        for (String m : messages)
        {
            d.processMessage(m);
            PbDetector.CompletionResult r = d.detectCompletion(m);
            if (r != null) result = r;
            PbDetector.CompletionResult drained = d.drainPendingCompletion();
            if (drained != null) result = drained;
        }
        return result;
    }

    @Test
    public void readsTheTotalCompletionTimeOnANewPersonalBest()
    {
        PbDetector.CompletionResult r = run("The Fractured Archive: Total completion time: 31:25.80 (new personal best)");
        assertNotNull(r);
        assertEquals("tfa", r.getGroup());
        assertEquals("31:25.80", r.getFormattedTime());
        assertTrue(r.isPersonalBest());
    }

    @Test
    public void readsTheTotalCompletionTimeAndTheStandingPersonalBest()
    {
        PbDetector.CompletionResult r = run("The Fractured Archive: Total completion time: 34:54.60. Personal best: 31:25.80");
        assertNotNull(r);
        assertEquals("tfa", r.getGroup());
        assertEquals("34:54.60", r.getFormattedTime());
        assertFalse(r.isPersonalBest());
        assertEquals("31:25.80", r.getPersonalBestTime());
    }

    @Test
    public void aBossLineIsNotARaidTime()
    {
        assertNull(run("Challenge complete: Ocellus. Duration: 2:25.80. Total: 2:25.80 (new personal best)"));
        // Nor can the count line that ends the raid claim it.
        assertNull(run(
            "Challenge complete: Ocellus. Duration: 2:25.80. Total: 2:25.80. Personal best: 2:20.00",
            "Your completed Fractured Archive count is: 3."));
    }

    @Test
    public void theWholeRaidBoardsTheTotal()
    {
        PbDetector.CompletionResult r = run(
            "Challenge complete: Ocellus. Duration: 2:25.80. Total: 2:25.80. Personal best: 2:20.00",
            "The Fractured Archive: Total completion time: 34:54.60. Personal best: 31:25.80",
            "Your completed Fractured Archive count is: 3.");
        assertNotNull(r);
        assertEquals("tfa", r.getGroup());
        assertEquals("34:54.60", r.getFormattedTime());
    }

    @Test
    public void everyTeamSizeHasABoard()
    {
        assertEquals("tfa_solo", BossCategory.find("tfa", 1).getKey());
        assertEquals("tfa_duo", BossCategory.find("tfa", 2).getKey());
        assertEquals("tfa_trio", BossCategory.find("tfa", 3).getKey());
        assertEquals("tfa_4man", BossCategory.find("tfa", 4).getKey());
        assertEquals("tfa_5man", BossCategory.find("tfa", 5).getKey());
        assertEquals("tfa_group", BossCategory.find("tfa", 6).getKey());
        assertEquals("tfa_group", BossCategory.find("tfa", 8).getKey());
        assertFalse(BossCategory.find("tfa", 1).isGroupContent());
        assertTrue(BossCategory.find("tfa", 2).isGroupContent());
        assertEquals("The Fractured Archive", BossCategory.find("tfa", 1).getDisplayName());
        assertEquals("Raids", BossCategory.find("tfa", 1).getDisplayGroup());
    }

    @Test
    public void aLiveBossOfTheWeekCardOpensByItself()
    {
        assertTrue(ClanPanel.autoOpens("boss", "live", false));
        // Not once the member has closed it, and not for anything else.
        assertFalse(ClanPanel.autoOpens("boss", "live", true));
        assertFalse(ClanPanel.autoOpens("boss", "upcoming", false));
        assertFalse(ClanPanel.autoOpens("boss", "ended", false));
        assertFalse(ClanPanel.autoOpens("mass", "live", false));
        assertFalse(ClanPanel.autoOpens("skill", "live", false));
    }
}
