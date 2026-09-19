package com.droplogger;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Team colours as the server sends them. New teams are #RRGGBB; teams created before 2026-09-19 were
 * stored as names ("red"), which Color.decode cannot read, so their clan-chat dots silently never
 * drew. The names map to the same hex as the server's palette (src/lib/team-colors.ts).
 */
final class TeamColors
{
    private static final Map<String, Integer> NAMED = new HashMap<>();
    static
    {
        NAMED.put("red", 0xE53935);
        NAMED.put("blue", 0x1E88E5);
        NAMED.put("green", 0x43A047);
        NAMED.put("purple", 0x8E24AA);
        NAMED.put("orange", 0xFB8C00);
        NAMED.put("teal", 0x00897B);
        NAMED.put("pink", 0xD81B60);
        NAMED.put("yellow", 0xFDD835);
    }

    private TeamColors() {}

    /** The colour, or null when it cannot be read. Never throws. */
    static Color parse(String value)
    {
        if (value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        if (v.startsWith("#"))
        {
            try { return Color.decode(v); }
            catch (NumberFormatException e) { return null; }
        }
        Integer rgb = NAMED.get(v.toLowerCase(Locale.ROOT));
        return rgb == null ? null : new Color(rgb);
    }
}
