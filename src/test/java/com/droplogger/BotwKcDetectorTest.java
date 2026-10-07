package com.droplogger;

import org.junit.Test;
import static org.junit.Assert.*;

// The plugin's KC backup for a BOTW boss Wise Old Man doesn't track yet (spec 2026-10-07).
public class BotwKcDetectorTest
{
    @Test
    public void readsTheRaidCompletionCount()
    {
        assertEquals(Integer.valueOf(470), BotwKcDetector.match("Your completed Chambers of Xeric count is: 470.", "Chambers of Xeric"));
        assertEquals(Integer.valueOf(1234), BotwKcDetector.match("Your completed Raids Four count is: 1,234.", "Raids Four"));
    }

    @Test
    public void readsABossKillCount()
    {
        assertEquals(Integer.valueOf(50), BotwKcDetector.match("Your Duke Sucellus kill count is: 50.", "Duke Sucellus"));
    }

    @Test
    public void ignoresColourTagsAndCase()
    {
        assertEquals(Integer.valueOf(12), BotwKcDetector.match("Your completed <col=ff0000>raids four</col> count is: <col=ff0000>12</col>.", "Raids Four"));
    }

    @Test
    public void doesNotMatchAnotherBossOrALongerName()
    {
        assertNull(BotwKcDetector.match("Your completed Theatre of Blood count is: 5.", "Chambers of Xeric"));
        assertNull(BotwKcDetector.match("Your completed Chambers of Xeric: Challenge Mode count is: 5.", "Chambers of Xeric"));
        assertNull(BotwKcDetector.match("Your completed Chambers of Xeric count is: 5.", ""));
        assertNull(BotwKcDetector.match("Your completed Chambers of Xeric count is: 5.", null));
    }
}
