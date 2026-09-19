package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pure parsing tests for the bingo client model: PlatformApiService.parseBingoCard/parseBingoPlayer
 * take a JsonObject and never touch the network, so a hand-built payload string exercises them
 * exactly like a real /bingo/current response would, including partial/missing-field shapes.
 */
public class PlatformApiServiceBingoTest
{
    private static final Gson GSON = new Gson();

    private JsonObject json(String s) { return GSON.fromJson(s, JsonObject.class); }

    @Test public void nullEventMeansNoCard()
    {
        PlatformApiService.BingoCard card = PlatformApiService.parseBingoCard(json("{\"event\":null}"));
        assertNotNull(card);
        assertNull(card.event);
        assertNotNull(card.teams);
        assertTrue(card.teams.isEmpty());
        assertNotNull(card.standings);
        assertNotNull(card.progress);
        assertNotNull(card.bounties);
    }

    @Test public void missingEventKeyEntirelyAlsoMeansNoCard()
    {
        PlatformApiService.BingoCard card = PlatformApiService.parseBingoCard(json("{}"));
        assertNotNull(card);
        assertNull(card.event);
    }

    @Test public void nullRootReturnsNull()
    {
        assertNull(PlatformApiService.parseBingoCard(null));
    }

    @Test public void fullShapeParsesEveryField()
    {
        // Real route shape (src/routes/bingo.ts loadFullBingoEvent): event.settings is whitelisted
        // down to { rows, cols, teamSize, hasDraft } for a public/full-detail read - no winRule, so
        // this payload includes one anyway (as a real server response predating a settings change
        // never would, but a stale/future field must still be silently ignored) to prove parsing
        // never reads or stores it; teams[] carries the rich per-team fields (members + points/
        // tilesComplete/rank/gapToAbove/leadOverBelow/roster/recentDrops) together; standings[] is
        // the plain leaderboard (teamId/name/color/points/tilesComplete/rank only - no gap/roster/
        // recentDrops); a progress[team][tile].drops entry has no tileCode key of its own (it's
        // implied by the outer tile-code key); bounties never send "released"/"claimed" booleans,
        // only description (null pre-release) and claimedTeamId (null = unclaimed).
        String payload = "{"
            + "\"event\":{\"id\":\"ev1\",\"name\":\"Autumn Bingo\",\"status\":\"active\","
            + "  \"startTime\":\"2026-09-01T00:00:00Z\",\"endTime\":\"2026-09-15T00:00:00Z\","
            + "  \"winnerTeamId\":null,\"settings\":{\"rows\":2,\"cols\":2,\"winRule\":\"points\"}},"
            + "\"board\":{\"rows\":2,\"cols\":2,\"tiles\":["
            + "  {\"code\":\"A1\",\"name\":\"Zulrah\",\"row\":0,\"col\":0,\"kind\":\"boss\","
            + "   \"threshold\":30,\"max\":0,\"icon\":\"Zulrah\",\"items\":["
            + "     {\"itemName\":\"Zulrah's scales\",\"itemId\":12934,\"points\":1}]},"
            + "  {\"code\":\"A2\",\"name\":\"Twisted bow\",\"row\":0,\"col\":1,\"kind\":\"item\","
            + "   \"threshold\":1,\"max\":0,\"icon\":\"20997\",\"items\":[]}"
            + "]},"
            + "\"teams\":[{\"teamId\":\"t1\",\"name\":\"Alpha\",\"color\":\"#FF0000\",\"members\":[\"Alice\",\"Bob\"],"
            + "  \"points\":30,\"tilesComplete\":1,\"rank\":1,\"gapToAbove\":null,"
            + "  \"leadOverBelow\":{\"points\":10,\"tiles\":0},"
            + "  \"roster\":[{\"rsn\":\"Alice\",\"points\":30,\"dropCount\":1}],"
            + "  \"recentDrops\":[{\"rsn\":\"Alice\",\"item\":\"Zulrah's scales\",\"tileCode\":\"A1\",\"points\":1,"
            + "     \"proofUrl\":\"https://discord.com/channels/1/2/3\",\"droppedAt\":\"2026-09-02T00:00:00Z\"}]}],"
            + "\"standings\":[{\"teamId\":\"t1\",\"name\":\"Alpha\",\"color\":\"#FF0000\",\"points\":30,"
            + "  \"tilesComplete\":1,\"rank\":1}],"
            + "\"progress\":{\"t1\":{\"A1\":{\"points\":30,\"complete\":true,\"drops\":[{\"rsn\":\"Alice\","
            + "  \"item\":\"Zulrah's scales\",\"points\":1,\"proofUrl\":null,\"droppedAt\":null}]}}},"
            + "\"bounties\":[{\"id\":\"b1\",\"number\":1,\"title\":\"Mystery bounty\",\"description\":null,"
            + "  \"points\":50,\"releaseAt\":\"2026-09-10T00:00:00Z\",\"claimedTeamId\":null,\"claimedAt\":null}]"
            + "}";

        PlatformApiService.BingoCard card = PlatformApiService.parseBingoCard(json(payload));
        assertNotNull(card);
        assertEquals("ev1", card.event.id);
        assertEquals("active", card.event.status);
        assertEquals(2, card.board.rows);
        assertEquals(2, card.board.tiles.size());
        assertEquals("Zulrah", card.board.tiles.get(0).icon);
        assertEquals(1, card.board.tiles.get(0).items.size());

        assertEquals(1, card.teams.size());
        assertEquals(2, card.teams.get(0).members.size());
        assertEquals(1, card.teams.get(0).rank);
        assertEquals(30, card.teams.get(0).points, 0.0001);
        assertEquals(1, card.teams.get(0).tilesComplete);
        assertNull(card.teams.get(0).gapToAbove);
        assertEquals(10, card.teams.get(0).leadOverBelow.points, 0.0001);
        assertEquals(1, card.teams.get(0).roster.size());
        assertEquals(1, card.teams.get(0).recentDrops.size());
        // The payload includes a proofUrl field (as a real server response might); the client model
        // has no such field at all, so this only proves it is silently ignored, never stored or thrown on.
        assertEquals("Alice", card.teams.get(0).recentDrops.get(0).rsn);

        // standings[] is the lean leaderboard: no gap/roster/recentDrops fields exist on this class.
        assertEquals(1, card.standings.size());
        assertEquals(1, card.standings.get(0).rank);
        assertEquals(30, card.standings.get(0).points, 0.0001);
        assertEquals(1, card.standings.get(0).tilesComplete);

        assertTrue(card.progress.get("t1").get("A1").complete);
        assertEquals(1, card.progress.get("t1").get("A1").drops.size());

        assertEquals(1, card.bounties.size());
        assertEquals(1, card.bounties.get(0).number);
        assertEquals(50, card.bounties.get(0).points, 0.0001);
        assertFalse(card.bounties.get(0).released);
        assertNull(card.bounties.get(0).description);
        assertFalse(card.bounties.get(0).claimed);
        assertNull(card.bounties.get(0).claimedTeamId);
    }

    @Test public void bountyIsReleasedAndClaimedWhenDescriptionAndClaimedTeamIdArePresent()
    {
        // description present (non-null) means released; a non-empty claimedTeamId means claimed.
        // The server never sends "released"/"claimed" booleans directly - both are client-derived.
        String payload = "{\"bounties\":[{\"id\":\"b1\",\"number\":2,\"title\":\"Claimed bounty\","
            + "\"description\":\"Kill the boss\",\"points\":25,\"releaseAt\":\"2026-09-01T00:00:00Z\","
            + "\"claimedTeamId\":\"t1\",\"claimedAt\":\"2026-09-05T00:00:00Z\"}]}";
        PlatformApiService.BingoCard card = PlatformApiService.parseBingoCard(json(payload));
        assertNotNull(card);
        assertEquals(1, card.bounties.size());
        assertTrue(card.bounties.get(0).released);
        assertEquals("Kill the boss", card.bounties.get(0).description);
        assertTrue(card.bounties.get(0).claimed);
        assertEquals("t1", card.bounties.get(0).claimedTeamId);
        assertEquals("2026-09-05T00:00:00Z", card.bounties.get(0).claimedAt);
    }

    @Test public void missingOptionalFieldsNeverThrow()
    {
        // Only the bare minimum: an event with no other fields, no board/teams/standings/progress/bounties keys at all.
        PlatformApiService.BingoCard card = PlatformApiService.parseBingoCard(json("{\"event\":{}}"));
        assertNotNull(card);
        assertNotNull(card.event);
        assertNull(card.event.id);
        assertNull(card.event.status);
        assertEquals(0, card.board.rows);
        assertTrue(card.board.tiles.isEmpty());
        assertTrue(card.teams.isEmpty());
        assertTrue(card.standings.isEmpty());
        assertTrue(card.progress.isEmpty());
        assertTrue(card.bounties.isEmpty());
    }

    @Test public void malformedJsonNeverThrowsAndReturnsNull()
    {
        // A tile entry that isn't an object at all: parse must skip it, not throw.
        String payload = "{\"board\":{\"rows\":1,\"cols\":1,\"tiles\":[\"not an object\"]}}";
        PlatformApiService.BingoCard card = PlatformApiService.parseBingoCard(json(payload));
        assertNotNull(card);
        assertTrue(card.board.tiles.isEmpty());
    }

    // ── parseBingoPlayer (drill-in) ──
    @Test public void playerDrillInParsesTilesAndDrops()
    {
        String payload = "{\"rsn\":\"Alice\",\"teamId\":\"t1\",\"points\":42,"
            + "\"tiles\":[{\"code\":\"A1\",\"points\":30},{\"code\":\"A2\",\"points\":12}],"
            + "\"drops\":[{\"rsn\":\"Alice\",\"item\":\"Zulrah's scales\",\"tileCode\":\"A1\",\"points\":1,"
            + "  \"proofUrl\":\"https://discord.com/channels/1/2/3\",\"droppedAt\":\"2026-09-02T00:00:00Z\"}]}";
        PlatformApiService.BingoPlayer p = PlatformApiService.parseBingoPlayer(json(payload));
        assertNotNull(p);
        assertEquals("Alice", p.rsn);
        assertEquals("t1", p.teamId);
        assertEquals(42, p.points, 0.0001);
        assertEquals(2, p.tiles.size());
        assertEquals("A1", p.tiles.get(0).code);
        assertEquals(1, p.drops.size());
    }

    @Test public void playerDrillInNullRootReturnsNull()
    {
        assertNull(PlatformApiService.parseBingoPlayer(null));
    }

    @Test public void playerDrillInEmptyObjectNeverThrows()
    {
        PlatformApiService.BingoPlayer p = PlatformApiService.parseBingoPlayer(json("{}"));
        assertNotNull(p);
        assertNull(p.rsn);
        assertTrue(p.tiles.isEmpty());
        assertTrue(p.drops.isEmpty());
    }
}
