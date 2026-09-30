package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Ryan, 2026-09-30: the bingo board only shows in the plugin once the draft is complete and teams
 * are decided (or the bingo has started). Before that the event is just a card with Sign up in the
 * Events list, and a board with no teams made the tab messy.
 */
public class BingoBoardReadyTest
{
    private static final Gson GSON = new Gson();

    private static PlatformApiService.BingoCard card(String eventJson)
    {
        return PlatformApiService.parseBingoCard(GSON.fromJson(
            "{\"event\":" + eventJson + ",\"board\":{\"rows\":5,\"cols\":5,\"tiles\":[]},\"teams\":[],\"standings\":[],\"progress\":[],\"bounties\":[]}",
            JsonObject.class));
    }

    @Test public void boardHiddenWhileTheDraftIsNotDone()
    {
        PlatformApiService.BingoCard c = card("{\"id\":\"b1\",\"name\":\"BBB26\",\"status\":\"setup\",\"boardReady\":false}");
        assertFalse(c.event.boardReady);
        assertFalse(ClanPanel.showBingoBoard(c));
    }

    @Test public void boardShownOnceReady()
    {
        PlatformApiService.BingoCard c = card("{\"id\":\"b1\",\"name\":\"BBB26\",\"status\":\"draft\",\"boardReady\":true}");
        assertTrue(ClanPanel.showBingoBoard(c));
    }

    @Test public void olderServerWithoutTheFlagKeepsShowingTheBoard()
    {
        PlatformApiService.BingoCard c = card("{\"id\":\"b1\",\"name\":\"BBB26\",\"status\":\"active\"}");
        assertTrue(c.event.boardReady);
        assertTrue(ClanPanel.showBingoBoard(c));
    }

    @Test public void noEventMeansNoBoard()
    {
        assertFalse(ClanPanel.showBingoBoard(null));
    }
}
