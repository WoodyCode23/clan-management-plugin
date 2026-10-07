package com.droplogger;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

// Reading a collection log page from the game's data (the new raid on release day; spec 2026-10-07).
public class ClogCategoryReaderTest
{
    /** Two tabs; "Raids" holds the new raid, with one item id the game remaps and one with no name. */
    private static ClogCategoryReader.GameData fake()
    {
        Map<Integer, int[]> cats = new HashMap<>();
        cats.put(10, new int[]{100});
        cats.put(20, new int[]{200, 201});
        Map<Integer, String> names = new HashMap<>();
        names.put(10, "Bosses"); names.put(20, "Raids");
        names.put(100, "Zulrah"); names.put(200, "Chambers of Xeric"); names.put(201, "Raids Four");
        Map<Integer, int[]> items = new HashMap<>();
        items.put(100, new int[]{12922});
        items.put(200, new int[]{20997});
        items.put(201, new int[]{30001, 39999, 30002, 30001});
        Map<Integer, String> itemNames = new HashMap<>();
        itemNames.put(12922, "Tanzanite fang"); itemNames.put(20997, "Twisted bow");
        itemNames.put(30001, "Raid helm"); itemNames.put(30002, "Raid pet"); itemNames.put(30003, "Raid sword");
        return new ClogCategoryReader.GameData()
        {
            @Override public int[] tabStructIds() { return new int[]{10, 20}; }
            @Override public String tabName(int tab) { return names.get(tab); }
            @Override public int[] categoryStructIds(int tab) { return cats.get(tab); }
            @Override public String categoryName(int cat) { return names.get(cat); }
            @Override public int[] itemIds(int cat) { return items.get(cat); }
            @Override public int remap(int raw) { return raw == 39999 ? 30003 : raw; }
            @Override public String itemName(int id) { return itemNames.get(id); }
        };
    }

    @Test
    public void findsAPageByNameWithItsItemsInOrder()
    {
        ClogCategoryReader.Category c = ClogCategoryReader.find(fake(), "raids four");
        assertNotNull(c);
        assertEquals("Raids", c.tab);
        assertEquals("Raids Four", c.category);
        assertEquals(3, c.items.size()); // the repeated 30001 is listed once
        assertEquals("Raid helm", c.items.get(0).itemName);
        assertEquals(30003, c.items.get(1).itemId); // remapped
        assertEquals("Raid sword", c.items.get(1).itemName);
        assertEquals(2, c.items.get(2).sortOrder);
    }

    @Test
    public void isNullForAPageThatDoesNotExist()
    {
        assertNull(ClogCategoryReader.find(fake(), "Raids Five"));
        assertNull(ClogCategoryReader.find(fake(), ""));
        assertNull(ClogCategoryReader.find(fake(), null));
    }

    @Test
    public void namesAnItemTheGameHasNoNameForByItsId()
    {
        ClogCategoryReader.GameData base = fake();
        ClogCategoryReader.GameData noNames = new ClogCategoryReader.GameData()
        {
            @Override public int[] tabStructIds() { return base.tabStructIds(); }
            @Override public String tabName(int tab) { return base.tabName(tab); }
            @Override public int[] categoryStructIds(int tab) { return base.categoryStructIds(tab); }
            @Override public String categoryName(int cat) { return base.categoryName(cat); }
            @Override public int[] itemIds(int cat) { return base.itemIds(cat); }
            @Override public int remap(int raw) { return raw; }
            @Override public String itemName(int id) { return id == 30002 ? "null" : base.itemName(id); }
        };
        ClogCategoryReader.Category c = ClogCategoryReader.find(noNames, "Raids Four");
        assertEquals("Item 30002", c.items.get(2).itemName);
    }
}
