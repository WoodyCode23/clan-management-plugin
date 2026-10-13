package com.droplogger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Is every party member a clan member? Used to mark a team time clan-verified ("live").
 *
 * Checked against the clan's MEMBER list (ClanSettings), not the clan chat channel: guests sit in
 * the channel too, so on 2026-10-11 a ToB team with a waitlisted guest was verified. Names match
 * ignoring case, spaces, underscores and hyphens, which the game treats as the same.
 */
final class PartyClanCheck
{
    private PartyClanCheck()
    {
    }

    /** False when there is no member list to check against: the time is then left unverified. */
    static boolean allMembers(List<String> party, Collection<String> memberNames)
    {
        if (memberNames == null || memberNames.isEmpty()) return false;
        Set<String> members = new HashSet<>();
        for (String m : memberNames) members.add(norm(m));
        for (String p : party)
        {
            if (!members.contains(norm(p))) return false;
        }
        return true;
    }

    private static String norm(String name)
    {
        return name == null ? "" : name.toLowerCase().replaceAll("[\\s_\\- ]", "");
    }
}
