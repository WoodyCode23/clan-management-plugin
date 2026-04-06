package com.droplogger;

/**
 * Represents a collection log item with its category metadata.
 */
public class ClogItem
{
    public final String name;
    public final int itemId;
    public final String tab;
    public final String category;
    public final int quantity;

    public ClogItem(String name, int itemId, String tab, String category, int quantity)
    {
        this.name = name;
        this.itemId = itemId;
        this.tab = tab;
        this.category = category;
        this.quantity = quantity;
    }
}
