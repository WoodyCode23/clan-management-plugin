package com.droplogger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads one collection log page (category) straight from the game's data, for a BOTW whose page the
 * server doesn't have yet (the new raid on release day; spec 2026-10-07). It walks the same structure
 * as the plugin's drop filter: the tabs enum, each tab's categories, each category's items. The game
 * lookups sit behind {@link GameData} so the walk can be tested without a client.
 */
final class ClogCategoryReader
{
    /** The game lookups the walk needs. The plugin's implementation must run on the client thread. */
    interface GameData
    {
        int[] tabStructIds();

        String tabName(int tabStructId);

        int[] categoryStructIds(int tabStructId);

        String categoryName(int categoryStructId);

        int[] itemIds(int categoryStructId);

        /** The game lists a few items under a duplicate id; this gives the real one. */
        int remap(int rawItemId);

        String itemName(int itemId);
    }

    static final class Item
    {
        final int itemId;
        final String itemName;
        final int sortOrder;

        Item(int itemId, String itemName, int sortOrder)
        {
            this.itemId = itemId;
            this.itemName = itemName;
            this.sortOrder = sortOrder;
        }
    }

    static final class Category
    {
        final String tab;
        final String category;
        final List<Item> items;

        Category(String tab, String category, List<Item> items)
        {
            this.tab = tab;
            this.category = category;
            this.items = items;
        }
    }

    private ClogCategoryReader()
    {
    }

    /** The page with this name (ignoring case and surrounding spaces), or null when the game has none. */
    static Category find(GameData data, String categoryName)
    {
        if (categoryName == null || categoryName.trim().isEmpty()) return null;
        String wanted = categoryName.trim();
        for (int tab : data.tabStructIds())
        {
            for (int cat : data.categoryStructIds(tab))
            {
                String name = data.categoryName(cat);
                if (name == null || !name.trim().equalsIgnoreCase(wanted)) continue;

                Set<Integer> ids = new LinkedHashSet<>();
                for (int raw : data.itemIds(cat)) ids.add(data.remap(raw));
                List<Item> items = new ArrayList<>();
                int order = 0;
                for (int id : ids)
                {
                    String itemName = data.itemName(id);
                    if (itemName == null || itemName.trim().isEmpty() || itemName.equals("null")) itemName = "Item " + id;
                    items.add(new Item(id, itemName, order++));
                }
                return new Category(data.tabName(tab), name.trim(), items);
            }
        }
        return null;
    }
}
