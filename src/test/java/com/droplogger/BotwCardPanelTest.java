package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javax.swing.JPanel;
import org.junit.Test;
import static org.junit.Assert.*;

public class BotwCardPanelTest
{
    private static PlatformApiService.BotwView view(String json)
    {
        return PlatformApiService.parseBotwView(new Gson().fromJson(json, JsonObject.class));
    }

    @Test
    public void labels()
    {
        assertEquals("Page 2 of 3", BotwCardPanel.pageLabel(2, 3));
        assertEquals("Starts soon", BotwCardPanel.phaseLabel("upcoming"));
        assertEquals("LIVE", BotwCardPanel.phaseLabel("live"));
        assertEquals("Ended", BotwCardPanel.phaseLabel("ended"));
        assertEquals("1. Alpha: 30 KC", BotwCardPanel.kcLine(1, new PlatformApiService.BotwRank("Alpha", 30, "wom")));
    }

    @Test
    public void ownedCountLine()
    {
        PlatformApiService.BotwView v = view("{\"grid\":[{\"itemId\":1,\"itemName\":\"A\",\"count\":2},{\"itemId\":2,\"itemName\":\"B\",\"count\":0}]}");
        assertEquals("1 of 2 items obtained", BotwCardPanel.ownedLine(v));
    }

    @Test
    public void buildsWhateverTheViewHolds()
    {
        JPanel empty = BotwCardPanel.build(view("{\"phase\":\"upcoming\"}"), null, () -> { }, () -> { }, rsn -> { }, null, null);
        assertNotNull(empty);
        PlatformApiService.BotwView full = view("{\"category\":\"Zulrah\",\"phase\":\"live\","
            + "\"grid\":[{\"itemId\":0,\"itemName\":\"Tanzanite fang\",\"count\":1}],"
            + "\"ranking\":[{\"rsn\":\"Alpha\",\"kc\":3,\"source\":\"plugin\"}],"
            + "\"drops\":{\"page\":1,\"pages\":2,\"total\":21,\"items\":[{\"rsn\":\"Alpha\",\"itemName\":\"Tanzanite fang\",\"itemId\":0,\"at\":\"2026-10-21T10:00:00.000Z\"}]}}");
        PlatformApiService.BotwPlayer player = PlatformApiService.parseBotwPlayer(new Gson().fromJson(
            "{\"rsn\":\"Alpha\",\"items\":[{\"itemId\":0,\"itemName\":\"Tanzanite fang\",\"count\":1,\"firstAt\":\"2026-10-21T10:00:00.000Z\"}]}", JsonObject.class));
        assertTrue(BotwCardPanel.build(full, null, () -> { }, () -> { }, rsn -> { }, "Alpha", player).getComponentCount() > 0);
    }
}
