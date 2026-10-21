package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.time.Instant;
import javax.swing.JPanel;
import org.junit.Test;
import static org.junit.Assert.*;

// The redesigned BOTW card (Ryan, 2026-10-08): header with a LIVE dot and time left, KC list, collection
// log with a progress bar, a 10-per-page drops feed, and one player panel. The panel opens right under
// the list the name was clicked in (Ryan, 2026-10-21: at the bottom it meant scrolling down to find it).
public class BotwCardPanelTest
{
    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    private static PlatformApiService.BotwView view(String json)
    {
        return PlatformApiService.parseBotwView(new Gson().fromJson(json, JsonObject.class));
    }

    @Test
    public void timeLeft()
    {
        PlatformApiService.BotwView live = view("{\"phase\":\"live\",\"start\":\"2026-10-20T00:00:00Z\",\"end\":\"2026-10-27T00:00:00Z\"}");
        assertEquals("5d left", BotwCardPanel.timeLeft(live, NOW));
        assertEquals("3h left", BotwCardPanel.timeLeft(view("{\"phase\":\"live\",\"end\":\"2026-10-22T03:10:00Z\"}"), NOW));
        assertEquals("starts in 2d", BotwCardPanel.timeLeft(view("{\"phase\":\"upcoming\",\"start\":\"2026-10-24T00:00:00Z\"}"), NOW));
        assertEquals("Ended", BotwCardPanel.timeLeft(view("{\"phase\":\"ended\"}"), NOW));
    }

    @Test
    public void ago()
    {
        assertEquals("now", BotwCardPanel.ago("2026-10-21T23:59:40Z", NOW));
        assertEquals("12m", BotwCardPanel.ago("2026-10-21T23:48:00Z", NOW));
        assertEquals("5h", BotwCardPanel.ago("2026-10-21T19:00:00Z", NOW));
        assertEquals("2d", BotwCardPanel.ago("2026-10-20T00:00:00Z", NOW));
        assertEquals("", BotwCardPanel.ago(null, NOW));
    }

    @Test
    public void podiumColours()
    {
        assertNotEquals(BotwCardPanel.placeColor(1), BotwCardPanel.placeColor(2));
        assertNotEquals(BotwCardPanel.placeColor(2), BotwCardPanel.placeColor(3));
        assertEquals(BotwCardPanel.placeColor(4), BotwCardPanel.placeColor(9));
        assertTrue(BotwCardPanel.placeColor(1) instanceof Color);
    }

    @Test
    public void ownedCount()
    {
        PlatformApiService.BotwView v = view("{\"grid\":[{\"itemId\":1,\"itemName\":\"A\",\"count\":2},{\"itemId\":2,\"itemName\":\"B\",\"count\":0}]}");
        assertEquals(1, BotwCardPanel.owned(v));
        assertEquals("1 / 2", BotwCardPanel.ownedLabel(v));
    }

    @Test
    public void buildsWhateverTheViewHolds()
    {
        JPanel empty = BotwCardPanel.build(view("{\"phase\":\"upcoming\"}"), null, () -> { }, () -> { }, (rsn, from) -> { }, () -> { }, null, null, null, NOW);
        assertNotNull(empty);
        PlatformApiService.BotwView full = view("{\"category\":\"Zulrah\",\"phase\":\"live\",\"start\":\"2026-10-20T00:00:00Z\",\"end\":\"2026-10-27T00:00:00Z\","
            + "\"grid\":[{\"itemId\":0,\"itemName\":\"Tanzanite fang\",\"count\":1}],"
            + "\"ranking\":[{\"rsn\":\"Alpha\",\"kc\":3,\"source\":\"plugin\"}],"
            + "\"drops\":{\"page\":1,\"pages\":2,\"total\":11,\"items\":[{\"rsn\":\"Alpha\",\"itemName\":\"Tanzanite fang\",\"itemId\":0,\"at\":\"2026-10-21T10:00:00.000Z\"}]}}");
        PlatformApiService.BotwPlayer player = PlatformApiService.parseBotwPlayer(new Gson().fromJson(
            "{\"rsn\":\"Alpha\",\"kc\":3,\"kcSource\":\"plugin\",\"items\":[{\"itemId\":0,\"itemName\":\"Tanzanite fang\",\"count\":1,\"firstAt\":\"2026-10-21T10:00:00.000Z\"}]}", JsonObject.class));
        assertEquals(3, player.kc);
        assertTrue(BotwCardPanel.build(full, null, () -> { }, () -> { }, (rsn, from) -> { }, () -> { }, "Alpha", BotwCardPanel.From.KC, player, NOW).getComponentCount() > 0);
    }

    private static final String FULL = "{\"category\":\"Zulrah\",\"phase\":\"live\",\"start\":\"2026-10-20T00:00:00Z\",\"end\":\"2026-10-27T00:00:00Z\","
        + "\"grid\":[{\"itemId\":0,\"itemName\":\"Tanzanite fang\",\"count\":1}],"
        + "\"ranking\":[{\"rsn\":\"Alpha\",\"kc\":3,\"source\":\"plugin\"},{\"rsn\":\"Beta\",\"kc\":2,\"source\":\"plugin\"}],"
        + "\"drops\":{\"page\":1,\"pages\":2,\"total\":11,\"items\":[{\"rsn\":\"Alpha\",\"itemName\":\"Tanzanite fang\",\"itemId\":0,\"at\":\"2026-10-21T10:00:00.000Z\"}]}}";

    private static int indexOf(JPanel body, String name)
    {
        for (int i = 0; i < body.getComponentCount(); i++)
        {
            if (name.equals(body.getComponent(i).getName())) return i;
        }
        return -1;
    }

    @Test
    public void aNameFromTheKcListOpensRightUnderTheKcList()
    {
        JPanel body = BotwCardPanel.build(view(FULL), null, () -> { }, () -> { }, (rsn, from) -> { }, () -> { }, "Alpha", BotwCardPanel.From.KC, null, NOW);
        int panel = indexOf(body, "botw-player");
        assertTrue(panel > 0);
        assertEquals(indexOf(body, "botw-section-collection") - 1, panel);
    }

    @Test
    public void aNameFromTheDropsOpensRightUnderTheDrops()
    {
        JPanel body = BotwCardPanel.build(view(FULL), null, () -> { }, () -> { }, (rsn, from) -> { }, () -> { }, "Alpha", BotwCardPanel.From.DROPS, null, NOW);
        int panel = indexOf(body, "botw-player");
        // After the drops and their pager, at the end of the card.
        assertEquals(indexOf(body, "botw-drops-pager") + 1, panel);
        assertEquals(body.getComponentCount() - 1, panel);
    }

    @Test
    public void nothingOpensWithoutASelection()
    {
        JPanel body = BotwCardPanel.build(view(FULL), null, () -> { }, () -> { }, (rsn, from) -> { }, () -> { }, null, null, null, NOW);
        assertEquals(-1, indexOf(body, "botw-player"));
    }
}
