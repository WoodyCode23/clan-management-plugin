package com.droplogger;

import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeamColorsTest
{
    @Test public void readsHex() { assertEquals(new Color(0x1E88E5), TeamColors.parse("#1E88E5")); }
    @Test public void readsOldNames() { assertEquals(new Color(0xE53935), TeamColors.parse("red")); }
    @Test public void namesAreCaseInsensitive() { assertEquals(new Color(0xE53935), TeamColors.parse("Red")); }
    @Test public void unknownIsNull() { assertNull(TeamColors.parse("chartreuse")); }
    @Test public void badHexIsNull() { assertNull(TeamColors.parse("#zzz")); }
    @Test public void blankIsNull() { assertNull(TeamColors.parse("  ")); assertNull(TeamColors.parse(null)); }
}
