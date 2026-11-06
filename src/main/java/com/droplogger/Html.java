package com.droplogger;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Escaping for text that goes into Swing HTML labels. Anything from the server or a user (names,
 * event and team titles, descriptions) passes through here, so it always shows as typed and is
 * never read as markup.
 */
final class Html
{
    private Html() { }

    static String esc(String s)
    {
        return s == null ? "" : StringEscapeUtils.escapeHtml4(s);
    }
}
