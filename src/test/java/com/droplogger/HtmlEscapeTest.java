package com.droplogger;

import org.junit.Test;
import static org.junit.Assert.*;

// Plugin Hub review (rsbfox, PR #19125): any text from the server or a user that goes into Swing
// HTML must be escaped, so a name like "<b>x" or "&" shows as typed instead of as markup.
public class HtmlEscapeTest
{
    @Test
    public void escapesMarkupAndQuotes()
    {
        assertEquals("&lt;img src=x&gt; &amp; &quot;hi&quot;", Html.esc("<img src=x> & \"hi\""));
    }

    @Test
    public void nullIsEmpty()
    {
        assertEquals("", Html.esc(null));
    }

    @Test
    public void eventNotesShowServerTextAsTyped()
    {
        String text = ClanPanel.eventNote("#1 <b>Rsn</b> → A&B").getText();
        assertTrue(text, text.contains("&lt;b&gt;Rsn&lt;/b&gt;"));
        assertTrue(text, text.contains("A&amp;B"));
        assertFalse(text, text.contains("<b>"));
    }

    @Test
    public void wrappedLabelsShowServerTextAsTyped()
    {
        String text = ClanPanel.bingoWrapLabel("<i>team</i>", 100, null, null).getText();
        assertTrue(text, text.contains("&lt;i&gt;team&lt;/i&gt;"));
    }
}
