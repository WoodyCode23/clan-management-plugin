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
        String payload = "{"
            + "\"event\":{\"id\":\"ev1\",\"name\":\"Autumn Bingo\",\"status\":\"active\","
            + "  \"startTime\":\"2026-09-01T00:00:00Z\",\"endTime\":\"2026-09-15T00:00:00Z\","
            + "  \"winCondition\":\"most_points\",\"winnerTeamId\":null},"
            + "\"board\":{\"rows\":2,\"cols\":2,\"tiles\":["
            + "  {\"code\":\"A1\",\"name\":\"Zulrah\",\"row\":0,\"col\":0,\"kind\":\"boss\","
            + "   \"threshold\":30,\"max\":0,\"icon\":\"Zulrah\",\"items\":["
            + "     {\"itemName\":\"Zulrah's scales\",\"itemId\":12934,\"points\":1}]},"
            + "  {\"code\":\"A2\",\"name\":\"Twisted bow\",\"row\":0,\"col\":1,\"kind\":\"item\","
            + "   \"threshold\":1,\"max\":0,\"icon\":\"20997\",\"items\":[]}"
            + "]},"
            + "\"teams\":[{\"teamId\":\"t1\",\"name\":\"Alpha\",\"color\":\"#FF0000\",\"members\":[\"Alice\",\"Bob\"]}],"
            + "\"standings\":[{\"teamId\":\"t1\",\"name\":\"Alpha\",\"color\":\"#FF0000\",\"rank\":1,\"points\":30,"
            + "  \"tilesComplete\":1,\"gapToAbove\":null,\"leadOverBelow\":{\"points\":10,\"tiles\":0},"
            + "  \"roster\":[{\"rsn\":\"Alice\",\"points\":30,\"dropCount\":1}],"
            + "  \"recentDrops\":[{\"rsn\":\"Alice\",\"item\":\"Zulrah's scales\",\"tileCode\":\"A1\",\"points\":1,"
            + "     \"proofUrl\":\"https://discord.com/channels/1/2/3\",\"droppedAt\":\"2026-09-02T00:00:00Z\"}]}],"
            + "\"progress\":{\"t1\":{\"A1\":{\"points\":30,\"complete\":true,\"drops\":[{\"rsn\":\"Alice\","
            + "  \"item\":\"Zulrah's scales\",\"tileCode\":\"A1\",\"points\":1,\"droppedAt\":null}]}}},"
            + "\"bounties\":[{\"id\":\"b1\",\"title\":\"Mystery bounty\",\"description\":null,"
            + "  \"releaseAt\":\"2026-09-10T00:00:00Z\",\"released\":false,\"claimed\":false,\"claimedByTeamId\":null}]"
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
        assertEquals(1, card.standings.size());
        assertEquals(1, card.standings.get(0).rank);
        assertNull(card.standings.get(0).gapToAbove);
        assertEquals(10, card.standings.get(0).leadOverBelow.points, 0.0001);
        assertEquals(1, card.standings.get(0).roster.size());
        assertEquals(1, card.standings.get(0).recentDrops.size());
        // The payload includes a proofUrl field (as a real server response might); the client model
        // has no such field at all, so this only proves it is silently ignored, never stored or thrown on.
        assertEquals("Alice", card.standings.get(0).recentDrops.get(0).rsn);
        assertTrue(card.progress.get("t1").get("A1").complete);
        assertEquals(1, card.progress.get("t1").get("A1").drops.size());
        assertEquals(1, card.bounties.size());
        assertFalse(card.bounties.get(0).released);
        assertNull(card.bounties.get(0).description);
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
