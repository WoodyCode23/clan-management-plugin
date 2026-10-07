package com.droplogger;

import java.util.List;

/**
 * Reads one collection log page (category) straight from the game's data, for a BOTW whose page the
 * server doesn't have yet (the new raid on release day; spec 2026-10-07).
 */
final class ClogCategoryReader
{
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
}
