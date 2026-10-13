package com.droplogger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * A team time is clan-verified only when every party member is a clan MEMBER. On 2026-10-11 a ToB
 * team with a waitlisted guest was verified because guests share the clan chat channel.
 */
public class PartyClanCheckTest
{
    private static final Set<String> MEMBERS = new HashSet<>(Arrays.asList("That Guy BTW", "Nextris", "VerzikDaCuck"));

    @Test
    public void allMembersPasses()
    {
        assertTrue(PartyClanCheck.allMembers(Arrays.asList("Nextris", "VerzikDaCuck"), MEMBERS));
    }

    @Test
    public void aGuestFails()
    {
        assertFalse(PartyClanCheck.allMembers(Arrays.asList("Aeryen", "Nextris", "VerzikDaCuck"), MEMBERS));
    }

    @Test
    public void namesMatchIgnoringCaseAndUnderscoresVersusSpaces()
    {
        assertTrue(PartyClanCheck.allMembers(Arrays.asList("that_guy_btw", "NEXTRIS"), MEMBERS));
    }

    @Test
    public void aSeparatorCannotBeDroppedToMatchAnotherName()
    {
        // "Nex tris" is a different account from "Nextris"; only the separator kind is interchangeable.
        assertFalse(PartyClanCheck.allMembers(Arrays.asList("Nex tris"), MEMBERS));
        assertFalse(PartyClanCheck.allMembers(Arrays.asList("ThatGuyBTW"), MEMBERS));
        assertTrue(PartyClanCheck.allMembers(Arrays.asList("That-Guy BTW"), MEMBERS));
    }

    @Test
    public void noMemberListMeansUnverified()
    {
        assertFalse(PartyClanCheck.allMembers(Arrays.asList("Nextris"), null));
        assertFalse(PartyClanCheck.allMembers(Arrays.asList("Nextris"), new HashSet<>()));
    }
}
