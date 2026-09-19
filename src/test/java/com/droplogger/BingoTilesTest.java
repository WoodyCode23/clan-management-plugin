package com.droplogger;

import org.junit.Test;
import static org.junit.Assert.*;

public class BingoTilesTest
{
    // ── fillFraction ──
    @Test public void halfway() { assertEquals(0.5, BingoTiles.fillFraction(15, 30), 0.0001); }
    @Test public void zeroPoints() { assertEquals(0.0, BingoTiles.fillFraction(0, 30), 0.0001); }
    @Test public void fullPoints() { assertEquals(1.0, BingoTiles.fillFraction(30, 30), 0.0001); }
    @Test public void clampsAboveOne() { assertEquals(1.0, BingoTiles.fillFraction(45, 30), 0.0001); }
    @Test public void zeroThresholdWithPoints() { assertEquals(1.0, BingoTiles.fillFraction(5, 0), 0.0001); }
    @Test public void zeroThresholdNoPoints() { assertEquals(0.0, BingoTiles.fillFraction(0, 0), 0.0001); }
    @Test public void negativeThresholdWithPoints() { assertEquals(1.0, BingoTiles.fillFraction(5, -10), 0.0001); }
    @Test public void negativePointsIsZero() { assertEquals(0.0, BingoTiles.fillFraction(-5, 30), 0.0001); }
    @Test public void negativePointsZeroThreshold() { assertEquals(0.0, BingoTiles.fillFraction(-5, 0), 0.0001); }

    // ── bossSpriteId ──
    @Test public void findsVorkath() { assertTrue(BingoTiles.bossSpriteId("Vorkath") >= 0); }
    @Test public void findsZulrah() { assertTrue(BingoTiles.bossSpriteId("Zulrah") >= 0); }
    @Test public void findsNex() { assertTrue(BingoTiles.bossSpriteId("Nex") >= 0); }
    @Test public void findsChambersOfXeric() { assertTrue(BingoTiles.bossSpriteId("Chambers of Xeric") >= 0); }
    @Test public void findsTheatreOfBlood() { assertTrue(BingoTiles.bossSpriteId("Theatre of Blood") >= 0); }
    @Test public void findsTombsOfAmascut() { assertTrue(BingoTiles.bossSpriteId("Tombs of Amascut") >= 0); }

    @Test public void kreearraApostropheMatches()
    {
        assertEquals(BingoTiles.bossSpriteId("Kree'arra"), BingoTiles.bossSpriteId("kreearra"));
        assertEquals(BingoTiles.bossSpriteId("Kree'arra"), BingoTiles.bossSpriteId("Kree Arra"));
        assertTrue(BingoTiles.bossSpriteId("Kree'arra") >= 0);
    }

    @Test public void phosanisNightmareMatchesVariants()
    {
        assertEquals(BingoTiles.bossSpriteId("Phosani's Nightmare"), BingoTiles.bossSpriteId("phosanis nightmare"));
        assertTrue(BingoTiles.bossSpriteId("Phosani's Nightmare") >= 0);
    }

    @Test public void missReturnsMinusOne() { assertEquals(-1, BingoTiles.bossSpriteId("Not A Real Boss")); }
    @Test public void blankReturnsMinusOne() { assertEquals(-1, BingoTiles.bossSpriteId("")); assertEquals(-1, BingoTiles.bossSpriteId(null)); }

    // ── formatPoints ──
    @Test public void wholeNumbers() { assertEquals("12/30", BingoTiles.formatPoints(12, 30)); }
    @Test public void oneDecimal() { assertEquals("7.5/30", BingoTiles.formatPoints(7.5, 30)); }
    @Test public void zeroOverThreshold() { assertEquals("0/30", BingoTiles.formatPoints(0, 30)); }
    @Test public void bothDecimal() { assertEquals("7.5/22.5", BingoTiles.formatPoints(7.5, 22.5)); }
}
