package com.droplogger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the game's completion count for a BOTW boss from chat, as a KC backup until Wise Old Man
 * tracks the boss (the new raid on release day; spec 2026-10-07). The BOTW names the boss exactly as
 * the game prints it (gameName), so "Chambers of Xeric" never matches "Chambers of Xeric: Challenge Mode".
 */
final class BotwKcDetector
{
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    private BotwKcDetector()
    {
    }

    /** The count in "Your completed {gameName} count is: N" or "Your {gameName} kill count is: N", or null. */
    static Integer match(String message, String gameName)
    {
        if (message == null || gameName == null || gameName.trim().isEmpty()) return null;
        String text = TAGS.matcher(message).replaceAll("").trim();
        String name = Pattern.quote(gameName.trim());
        Pattern p = Pattern.compile("^Your (?:completed " + name + " count|" + name + " kill count) is: ([\\d,]+)\\.?$", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (!m.matches()) return null;
        try
        {
            return Integer.parseInt(m.group(1).replace(",", ""));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
