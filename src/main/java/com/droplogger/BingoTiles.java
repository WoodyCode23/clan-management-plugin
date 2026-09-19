package com.droplogger;

import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

import java.util.Locale;

/**
 * Pure helpers for the bingo board renderer: fill fraction, boss-name to sprite lookup, and the
 * "12/30" points readout. No Swing, no network, no game state, so these are plain JUnit-testable.
 */
public final class BingoTiles
{
    private BingoTiles()
    {
    }

    /**
     * points/threshold, clamped to [0, 1]. A threshold that is zero or negative is treated as
     * "already there": a tile with any positive points is full, one with none is empty. Negative
     * points never count against the tile, so they always read as empty.
     */
    public static double fillFraction(double points, double threshold)
    {
        if (threshold <= 0)
        {
            return points > 0 ? 1.0 : 0.0;
        }
        if (points <= 0)
        {
            return 0.0;
        }
        double frac = points / threshold;
        if (frac > 1.0) return 1.0;
        return frac;
    }

    /**
     * Finds the HiscoreSkill entry (boss or boss-adjacent activity, e.g. a raid) whose name
     * matches bossName once both are normalized to lowercase letters/digits only, so "Kree'arra",
     * "kreearra" and "Kree Arra" all match the same entry. Returns that entry's sprite id, or -1
     * when nothing matches.
     */
    public static int bossSpriteId(String bossName)
    {
        String target = normalize(bossName);
        if (target.isEmpty())
        {
            return -1;
        }
        for (HiscoreSkill skill : HiscoreSkill.values())
        {
            HiscoreSkillType type = skill.getType();
            if (type != HiscoreSkillType.BOSS && type != HiscoreSkillType.ACTIVITY)
            {
                continue;
            }
            if (normalize(skill.getName()).equals(target))
            {
                return skill.getSpriteId();
            }
        }
        return -1;
    }

    private static String normalize(String s)
    {
        if (s == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c))
            {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** "12/30" for whole numbers, "7.5/30" when either side has a fractional part. */
    public static String formatPoints(double points, double threshold)
    {
        return formatNumber(points) + "/" + formatNumber(threshold);
    }

    private static String formatNumber(double v)
    {
        if (v == Math.rint(v) && !Double.isInfinite(v))
        {
            return String.valueOf((long) v);
        }
        return String.format(Locale.US, "%.1f", v);
    }
}
