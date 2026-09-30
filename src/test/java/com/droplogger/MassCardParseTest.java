package com.droplogger;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class MassCardParseTest
{
    @Test
    public void parsesALiveCard()
    {
        JsonObject o = new Gson().fromJson("{\"id\":\"m1\",\"title\":\"Corp Mass\",\"bossKey\":\"corporeal_beast\",\"bossName\":\"Corporeal Beast\","
            + "\"phase\":\"live\",\"start\":\"2026-10-03T20:00:00Z\",\"end\":\"2026-10-04T01:00:00Z\",\"goingCount\":3,\"youreGoing\":true,"
            + "\"totalKills\":12,\"killsUpdatedAt\":\"2026-10-03T20:30:00Z\","
            + "\"items\":[{\"itemId\":1,\"itemName\":\"Spectral sigil\",\"count\":1}],"
            + "\"drops\":[{\"itemId\":1,\"itemName\":\"Spectral sigil\",\"rsn\":\"BlG Moby\",\"at\":\"2026-10-03T20:42:00Z\"}]}", JsonObject.class);
        PlatformApiService.MassCard c = PlatformApiService.parseMassCard(o);
        assertEquals("live", c.phase);
        assertEquals(Integer.valueOf(12), c.totalKills);
        assertTrue(c.youreGoing);
        assertEquals(1, c.items.size());
        assertEquals("BlG Moby", c.drops.get(0).rsn);
    }

    @Test
    public void parsesUnknownKillsAndNoBoss()
    {
        JsonObject o = new Gson().fromJson("{\"id\":\"m2\",\"title\":\"Mystery mass\",\"bossKey\":null,\"bossName\":null,\"phase\":\"upcoming\","
            + "\"start\":\"2026-10-03T20:00:00Z\",\"end\":\"2026-10-03T23:00:00Z\",\"goingCount\":0,\"youreGoing\":false,"
            + "\"totalKills\":null,\"killsUpdatedAt\":null,\"items\":[],\"drops\":[]}", JsonObject.class);
        PlatformApiService.MassCard c = PlatformApiService.parseMassCard(o);
        assertNull(c.totalKills);
        assertNull(c.bossName);
        assertTrue(c.items.isEmpty());
    }

    @Test
    public void parsesTheTopKillers()
    {
        JsonObject o = new Gson().fromJson("{\"id\":\"m3\",\"title\":\"Corp Mass\",\"bossName\":\"Corporeal Beast\",\"phase\":\"ended\","
            + "\"start\":\"2026-10-03T20:00:00Z\",\"end\":\"2026-10-03T22:15:00Z\",\"goingCount\":0,\"youreGoing\":false,"
            + "\"totalKills\":9,\"killsUpdatedAt\":null,\"killers\":[{\"rsn\":\"A\",\"kills\":6},{\"rsn\":\"B\",\"kills\":3}],"
            + "\"items\":[],\"drops\":[]}", JsonObject.class);
        PlatformApiService.MassCard c = PlatformApiService.parseMassCard(o);
        assertEquals(2, c.killers.size());
        assertEquals("A", c.killers.get(0).rsn);
        assertEquals(6, c.killers.get(0).kills);
    }

    @Test
    public void olderServerWithoutKillersParsesAsEmpty()
    {
        JsonObject o = new Gson().fromJson("{\"id\":\"m4\",\"title\":\"X mass\",\"phase\":\"live\",\"items\":[],\"drops\":[]}", JsonObject.class);
        assertTrue(PlatformApiService.parseMassCard(o).killers.isEmpty());
    }

    @Test
    public void durationReadsHoursAndMinutes()
    {
        assertEquals("2h 15m", MassCardPanel.duration("2026-10-03T20:00:00Z", "2026-10-03T22:15:00Z"));
        assertEquals("45m", MassCardPanel.duration("2026-10-03T20:00:00Z", "2026-10-03T20:45:00Z"));
        assertEquals("", MassCardPanel.duration(null, "2026-10-03T20:45:00Z"));
    }
}
