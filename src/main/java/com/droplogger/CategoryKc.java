package com.droplogger;

import java.util.Map;

/**
 * The KC line on a member's collection log page. Most pages are one boss with one count, matched by
 * name ("Abyssal Sire" to "abyssal_sire"). The three raids share one page across their modes, so
 * they list each mode the player has kills in: CoX and Challenge Mode, ToB and Hard Mode, ToA and
 * Expert. Keys are the official hiscore names the server sends.
 */
final class CategoryKc
{
    private static final String SEP = "  ·  ";

    /** Page name to {label, hiscore key} pairs, in display order. */
    private static final Map<String, String[][]> RAID_MODES = Map.of(
        "chambers of xeric", new String[][]{
            {"KC", "chambers_of_xeric"}, {"CM", "chambers_of_xeric:_challenge_mode"}},
        "theatre of blood", new String[][]{
            {"KC", "theatre_of_blood"}, {"HM", "theatre_of_blood:_hard_mode"}},
        "tombs of amascut", new String[][]{
            {"KC", "tombs_of_amascut"}, {"Expert", "tombs_of_amascut:_expert_mode"}});

    private CategoryKc()
    {
    }

    /** "KC: 1,234  ·  CM: 56", "KC: 77", or null when the page has no KC to show. */
    static String line(String category, Map<String, Integer> kc)
    {
        if (category == null || kc == null || kc.isEmpty()) return null;
        String[][] modes = RAID_MODES.get(category.toLowerCase().trim());
        if (modes != null)
        {
            StringBuilder sb = new StringBuilder();
            for (String[] mode : modes)
            {
                Integer v = find(kc, mode[1]);
                if (v == null) continue;
                if (sb.length() > 0) sb.append(SEP);
                sb.append(mode[0]).append(": ").append(String.format("%,d", v));
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        Integer v = find(kc, category);
        return v == null ? null : "KC: " + String.format("%,d", v);
    }

    /** A positive count whose key matches on letters and digits only, ignoring case. */
    private static Integer find(Map<String, Integer> kc, String name)
    {
        String norm = norm(name);
        for (Map.Entry<String, Integer> e : kc.entrySet())
        {
            Integer v = e.getValue();
            if (v != null && v > 0 && norm(e.getKey()).equals(norm)) return v;
        }
        return null;
    }

    private static String norm(String s)
    {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
