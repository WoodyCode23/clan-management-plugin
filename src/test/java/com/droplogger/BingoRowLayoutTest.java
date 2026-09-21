package com.droplogger;

import org.junit.Test;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Layout regression tests for the bingo card's rows.
 *
 * The side panel is narrow. These rows are BorderLayout, which hands EAST its full preferred width
 * and leaves WEST the remainder, and a plain JLabel neither wraps nor ellipsises, so a long RSN or
 * item name was simply cut off. The fix is a wrapping HTML label at a reserved width. These tests
 * pin the two properties that matter and would both have failed before it:
 *
 *   1. the label never grows past the width it was given (so it cannot push into the stat column),
 *   2. text too long for one line actually gets a second line instead of being clipped.
 *
 * Preferred sizes are computed from font metrics, so this runs headless like the rest of the suite.
 */
public class BingoRowLayoutTest
{
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final int WIDTH = ClanPanel.BINGO_ROW_TEXT_WIDTH;

    private static JLabel label(String text)
    {
        return ClanPanel.bingoWrapLabel(text, WIDTH, FONT, Color.WHITE);
    }

    private static int lineHeight()
    {
        return label("A").getPreferredSize().height;
    }

    @Test
    public void reservesRoomForTheStatColumn()
    {
        // The text column plus the stat column must fit the panel's own wrap width, or the row is
        // over-wide no matter how the text behaves.
        assertTrue("text + stat column must fit the panel",
            ClanPanel.BINGO_ROW_TEXT_WIDTH + ClanPanel.BINGO_STAT_COL_WIDTH <= ClanPanel.STANDINGS_TEXT_WIDTH);
        assertTrue("text column must still be usable", ClanPanel.BINGO_ROW_TEXT_WIDTH > 80);
    }

    @Test
    public void aShortNameStaysOnOneLine()
    {
        JLabel short1 = label("Woody");
        assertEquals("a short name should not wrap", lineHeight(), short1.getPreferredSize().height);
    }

    @Test
    public void aLongRsnWrapsInsteadOfOverflowing()
    {
        // 12 characters is the OSRS maximum, and this is the shape that was being clipped.
        JLabel long1 = label("BlG MOBY XxX");
        Dimension size = long1.getPreferredSize();
        assertTrue("label must not exceed the width it was given: " + size.width,
            size.width <= WIDTH + 1);
    }

    @Test
    public void aLongDropLineGetsASecondLine()
    {
        // The row text the drill-in builds: "<rsn>: <item> (<tile>)". This is far too wide for the
        // column, so it must take a second line rather than being cut off.
        JLabel drop = label("BlG MOBY: Osmumten's fang (Tombs of Amascut)");
        Dimension size = drop.getPreferredSize();
        assertTrue("must wrap onto at least a second line", size.height >= lineHeight() * 2);
        assertTrue("must not exceed the width it was given: " + size.width, size.width <= WIDTH + 1);
    }

    @Test
    public void markupInServerTextIsEscapedNotRendered()
    {
        // An RSN or item name is server data. The wrapping label is HTML, so unescaped markup would
        // be interpreted; the raw characters must survive instead.
        JLabel evil = label("<b>x</b>");
        assertTrue("angle brackets must be escaped, not rendered",
            evil.getText().contains("&lt;b&gt;x&lt;/b&gt;"));
    }

    @Test
    public void lockRowHeightToContentMatchesThePreferredHeight()
    {
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout());
        row.add(label("BlG MOBY: Osmumten's fang (Tombs of Amascut)"), java.awt.BorderLayout.WEST);
        ClanPanel.lockRowHeightToContent(row);
        assertEquals("a wrapped row must be allowed its full height",
            row.getPreferredSize().height, row.getMaximumSize().height);
        assertTrue("height must cover two lines", row.getMaximumSize().height >= lineHeight() * 2);
    }
}
