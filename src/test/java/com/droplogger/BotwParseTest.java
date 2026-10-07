package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

// The BOTW collection log view from GET /events/:id/clog, and the bootstrap's "botw" list.
public class BotwParseTest
{
    private static JsonObject json(String s) { return new Gson().fromJson(s, JsonObject.class); }

    @Test
    public void parsesAView()
    {
        PlatformApiService.BotwView v = PlatformApiService.parseBotwView(json(
            "{\"category\":\"Zulrah\",\"phase\":\"live\","
            + "\"grid\":[{\"itemId\":1,\"itemName\":\"Tanzanite fang\",\"count\":2},{\"itemId\":2,\"itemName\":\"Magic fang\",\"count\":0}],"
            + "\"ranking\":[{\"rsn\":\"Alpha\",\"kc\":30,\"source\":\"wom\"}],"
            + "\"drops\":{\"page\":2,\"pages\":3,\"total\":45,\"items\":[{\"rsn\":\"Alpha\",\"itemName\":\"Tanzanite fang\",\"itemId\":1,\"at\":\"2026-10-21T10:00:00.000Z\"},"
            + "{\"rsn\":\"Beta\",\"itemName\":\"Visage\",\"itemId\":null,\"at\":\"2026-10-21T09:00:00.000Z\"}]}}"));
        assertEquals("Zulrah", v.category);
        assertEquals("live", v.phase);
        assertEquals(2, v.grid.size());
        assertEquals(2, v.grid.get(0).count);
        assertEquals(30, v.ranking.get(0).kc);
        assertEquals(2, v.page);
        assertEquals(3, v.pages);
        assertEquals(45, v.total);
        assertNull(v.drops.get(1).itemId);
    }

    @Test
    public void toleratesMissingParts()
    {
        PlatformApiService.BotwView v = PlatformApiService.parseBotwView(json("{\"category\":null,\"phase\":\"upcoming\"}"));
        assertNull(v.category);
        assertTrue(v.grid.isEmpty());
        assertTrue(v.ranking.isEmpty());
        assertTrue(v.drops.isEmpty());
        assertEquals(1, v.page);
        assertEquals(1, v.pages);
    }

    @Test
    public void parsesAPlayer()
    {
        PlatformApiService.BotwPlayer p = PlatformApiService.parseBotwPlayer(json(
            "{\"rsn\":\"Alpha\",\"items\":[{\"itemId\":1,\"itemName\":\"Tanzanite fang\",\"count\":1,\"firstAt\":\"2026-10-20T01:00:00.000Z\"}]}"));
        assertEquals("Alpha", p.rsn);
        assertEquals(1, p.items.size());
        assertEquals("Tanzanite fang", p.items.get(0).itemName);
    }

    @Test
    public void parsesTheBootstrapList()
    {
        List<PlatformApiService.BotwInfo> list = PlatformApiService.parseBotwInfo(json(
            "{\"botw\":[{\"eventId\":\"e1\",\"gameName\":\"Raids Four\",\"clogCategoryNeeded\":\"Raids 4\"},"
            + "{\"eventId\":\"e2\",\"gameName\":null,\"clogCategoryNeeded\":null}]}"));
        assertEquals(2, list.size());
        assertEquals("Raids Four", list.get(0).gameName);
        assertEquals("Raids 4", list.get(0).clogCategoryNeeded);
        assertNull(list.get(1).gameName);
        assertTrue(PlatformApiService.parseBotwInfo(json("{}")).isEmpty());
    }
}
