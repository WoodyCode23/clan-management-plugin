package com.droplogger;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BingoTeamViewTest
{
    // ── normalizeName / isSamePlayer ──
    @Test public void normalizeLowercasesAndCollapsesSpaces()
    {
        assertEquals("woody code", BingoTeamView.normalizeName("Woody_Code"));
        assertEquals("woody code", BingoTeamView.normalizeName("woody-code"));
        assertEquals("woody code", BingoTeamView.normalizeName("  Woody   Code  "));
    }

    @Test public void normalizeNullIsEmpty() { assertEquals("", BingoTeamView.normalizeName(null)); }

    @Test public void isSamePlayerMatchesAcrossSeparators()
    {
        assertTrue(BingoTeamView.isSamePlayer("Woody Code", "woody_code"));
        assertFalse(BingoTeamView.isSamePlayer("Woody Code", "Someone Else"));
        assertFalse(BingoTeamView.isSamePlayer(null, null));
        assertFalse(BingoTeamView.isSamePlayer("", ""));
    }

    // ── findOwnTeamId ──
    private PlatformApiService.BingoTeam team(String id, String... members)
    {
        List<String> m = new ArrayList<>();
        for (String s : members) m.add(s);
        return new PlatformApiService.BingoTeam(id, "Team " + id, "#FF0000", m);
    }

    private PlatformApiService.BingoCard cardWithTeams(PlatformApiService.BingoTeam... teams)
    {
        List<PlatformApiService.BingoTeam> list = new ArrayList<>();
        for (PlatformApiService.BingoTeam t : teams) list.add(t);
        return new PlatformApiService.BingoCard(null, null, list, new ArrayList<>(), new HashMap<>(), new ArrayList<>());
    }

    @Test public void findsOwnTeamByNormalizedRsn()
    {
        PlatformApiService.BingoCard card = cardWithTeams(
            team("t1", "Alice", "Bob"),
            team("t2", "Woody_Code", "Someone"));
        assertEquals("t2", BingoTeamView.findOwnTeamId(card, "woody code"));
    }

    @Test public void noMatchReturnsNull()
    {
        PlatformApiService.BingoCard card = cardWithTeams(team("t1", "Alice"));
        assertNull(BingoTeamView.findOwnTeamId(card, "Nobody"));
    }

    @Test public void nullCardOrNameReturnsNull()
    {
        assertNull(BingoTeamView.findOwnTeamId(null, "Alice"));
        assertNull(BingoTeamView.findOwnTeamId(cardWithTeams(team("t1", "Alice")), null));
        assertNull(BingoTeamView.findOwnTeamId(cardWithTeams(team("t1", "Alice")), ""));
    }

    // ── rankOrdinal ──
    @Test public void rankOrdinals()
    {
        assertEquals("1st", BingoTeamView.rankOrdinal(1));
        assertEquals("2nd", BingoTeamView.rankOrdinal(2));
        assertEquals("3rd", BingoTeamView.rankOrdinal(3));
        assertEquals("4th", BingoTeamView.rankOrdinal(4));
        assertEquals("11th", BingoTeamView.rankOrdinal(11));
        assertEquals("12th", BingoTeamView.rankOrdinal(12));
        assertEquals("13th", BingoTeamView.rankOrdinal(13));
        assertEquals("21st", BingoTeamView.rankOrdinal(21));
    }

    // ── formatTeamStatusLine ──
    @Test public void leaderWithLead()
    {
        String line = BingoTeamView.formatTeamStatusLine(128, 9, 25, 1, 4,
            null, new PlatformApiService.BingoGap(20, 1));
        assertEquals("128 pts | 9/25 tiles | Rank 1 of 4 | Leading by 20 pts", line);
    }

    @Test public void secondPlaceWithGap()
    {
        String line = BingoTeamView.formatTeamStatusLine(108, 8, 25, 2, 4,
            new PlatformApiService.BingoGap(20, 1), new PlatformApiService.BingoGap(5, 0));
        assertEquals("108 pts | 8/25 tiles | Rank 2 of 4 | 20 pts behind 1st", line);
    }

    @Test public void thirdPlaceBehindSecond()
    {
        String line = BingoTeamView.formatTeamStatusLine(90, 6, 25, 3, 4,
            new PlatformApiService.BingoGap(18, 2), null);
        assertEquals("90 pts | 6/25 tiles | Rank 3 of 4 | 18 pts behind 2nd", line);
    }

    @Test public void noGapDataOmitsTrailingClause()
    {
        String line = BingoTeamView.formatTeamStatusLine(50, 3, 25, 4, 4, null, null);
        assertEquals("50 pts | 3/25 tiles | Rank 4 of 4", line);
    }

    @Test public void onePointIsSingular()
    {
        String line = BingoTeamView.formatTeamStatusLine(1, 0, 25, 1, 2, null, new PlatformApiService.BingoGap(1, 0));
        assertEquals("1 pt | 0/25 tiles | Rank 1 of 2 | Leading by 1 pt", line);
    }

    @Test public void noRankKnownOmitsRankClause()
    {
        String line = BingoTeamView.formatTeamStatusLine(10, 1, 25, 0, 0, null, null);
        assertEquals("10 pts | 1/25 tiles", line);
    }

    // ── resolveTile / toBoardTiles icon resolution ──
    private PlatformApiService.BingoBoardTile bossTile(String code, String bossName)
    {
        return new PlatformApiService.BingoBoardTile(code, bossName, "boss", 0, 0, 30, 0, bossName, new ArrayList<>());
    }

    @Test public void resolvesBossIconByName()
    {
        BingoTile t = BingoTeamView.resolveTile(bossTile("A1", "Vorkath"),
            new PlatformApiService.BingoTileProgress(15, false, new ArrayList<>()));
        assertTrue(t.isBoss());
        assertEquals("Vorkath", t.bossName);
        assertEquals(15, t.points, 0.0001);
        assertEquals(30, t.threshold, 0.0001);
    }

    @Test public void resolvesNumericIconAsItemId()
    {
        PlatformApiService.BingoBoardTile tile = new PlatformApiService.BingoBoardTile(
            "C3", "Twisted bow", "item", 0, 0, 1, 0, "20997", new ArrayList<>());
        BingoTile t = BingoTeamView.resolveTile(tile, null);
        assertFalse(t.isBoss());
        assertEquals(20997, t.itemId);
        assertEquals(0, t.points, 0.0001); // null progress reads as untouched
    }

    @Test public void resolvesItemNameAgainstTileItems()
    {
        List<PlatformApiService.BingoItem> items = new ArrayList<>();
        items.add(new PlatformApiService.BingoItem("Dragon warhammer", 13576, 1));
        PlatformApiService.BingoBoardTile tile = new PlatformApiService.BingoBoardTile(
            "D3", "DWH drop", "item", 0, 0, 1, 0, "Dragon Warhammer", items);
        BingoTile t = BingoTeamView.resolveTile(tile, null);
        assertEquals(13576, t.itemId);
    }

    @Test public void emptyIconFallsBackToFirstItem()
    {
        List<PlatformApiService.BingoItem> items = new ArrayList<>();
        items.add(new PlatformApiService.BingoItem("Zulrah's scales", 12934, 1));
        PlatformApiService.BingoBoardTile tile = new PlatformApiService.BingoBoardTile(
            "E1", "Scales", "item", 0, 0, 1000, 0, "", items);
        BingoTile t = BingoTeamView.resolveTile(tile, null);
        assertEquals(12934, t.itemId);
    }

    @Test public void unresolvableIconAndNoItemsNeverThrows()
    {
        PlatformApiService.BingoBoardTile tile = new PlatformApiService.BingoBoardTile(
            "F1", "Mystery", "item", 0, 0, 1, 0, "Not A Real Boss Or Item", new ArrayList<>());
        BingoTile t = BingoTeamView.resolveTile(tile, null);
        assertFalse(t.isBoss());
        assertEquals(0, t.itemId);
    }

    @Test public void thresholdFallsBackToMaxWhenThresholdIsZero()
    {
        PlatformApiService.BingoBoardTile tile = new PlatformApiService.BingoBoardTile(
            "G1", "KC tile", "boss", 0, 0, 0, 50, "Zulrah", new ArrayList<>());
        BingoTile t = BingoTeamView.resolveTile(tile, new PlatformApiService.BingoTileProgress(10, false, new ArrayList<>()));
        assertEquals(50, t.threshold, 0.0001);
    }

    @Test public void toBoardTilesReadsPerTeamProgress()
    {
        List<PlatformApiService.BingoBoardTile> defs = new ArrayList<>();
        defs.add(bossTile("A1", "Zulrah"));
        defs.add(bossTile("A2", "Vorkath"));
        PlatformApiService.BingoBoard board = new PlatformApiService.BingoBoard(1, 2, defs);

        Map<String, PlatformApiService.BingoTileProgress> teamAProgress = new HashMap<>();
        teamAProgress.put("A1", new PlatformApiService.BingoTileProgress(30, true, new ArrayList<>()));
        Map<String, Map<String, PlatformApiService.BingoTileProgress>> progress = new HashMap<>();
        progress.put("teamA", teamAProgress);

        PlatformApiService.BingoCard card = new PlatformApiService.BingoCard(null, board,
            new ArrayList<>(), new ArrayList<>(), progress, new ArrayList<>());

        List<BingoTile> tiles = BingoTeamView.toBoardTiles(card, "teamA");
        assertEquals(2, tiles.size());
        assertEquals(30, tiles.get(0).points, 0.0001); // A1: has progress
        assertEquals(0, tiles.get(1).points, 0.0001);  // A2: untouched by this team
    }

    @Test public void toBoardTilesUnknownTeamReadsAllZero()
    {
        List<PlatformApiService.BingoBoardTile> defs = new ArrayList<>();
        defs.add(bossTile("A1", "Zulrah"));
        PlatformApiService.BingoBoard board = new PlatformApiService.BingoBoard(1, 1, defs);
        PlatformApiService.BingoCard card = new PlatformApiService.BingoCard(null, board,
            new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new ArrayList<>());
        List<BingoTile> tiles = BingoTeamView.toBoardTiles(card, "nope");
        assertEquals(1, tiles.size());
        assertEquals(0, tiles.get(0).points, 0.0001);
    }

    @Test public void totalTilesCountsBoardDefinition()
    {
        List<PlatformApiService.BingoBoardTile> defs = new ArrayList<>();
        defs.add(bossTile("A1", "Zulrah"));
        defs.add(bossTile("A2", "Vorkath"));
        PlatformApiService.BingoCard card = new PlatformApiService.BingoCard(null,
            new PlatformApiService.BingoBoard(1, 2, defs), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new ArrayList<>());
        assertEquals(2, BingoTeamView.totalTiles(card));
        assertEquals(0, BingoTeamView.totalTiles(null));
    }

    @Test public void standingForTeamFindsByTeamId()
    {
        List<PlatformApiService.BingoStanding> standings = new ArrayList<>();
        standings.add(new PlatformApiService.BingoStanding("t1", "Alpha", "#FF0000", 1, 50, 3, null, null, new ArrayList<>(), new ArrayList<>()));
        PlatformApiService.BingoCard card = new PlatformApiService.BingoCard(null, null, new ArrayList<>(), standings, new HashMap<>(), new ArrayList<>());
        assertEquals("Alpha", BingoTeamView.standingForTeam(card, "t1").name);
        assertNull(BingoTeamView.standingForTeam(card, "nope"));
    }
}
