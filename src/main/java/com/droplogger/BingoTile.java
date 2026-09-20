package com.droplogger;

/**
 * Immutable bingo tile: identity, board position, progress, and an icon reference that is EITHER a
 * sprite (a boss/activity/skill icon, already resolved to a sprite id) OR an item id (rendered via
 * ItemManager). Exactly one of the two is set; use the {@link #boss} / {@link #item} /
 * {@link #sprite} factories rather than the constructor directly.
 *
 * kind mirrors the server's tile kind: "drop" (scored from item drops) or "kc"/"xp" (scored from
 * Wise Old Man gains). The renderer treats them identically for progress, since every kind reports
 * its points the same way; kind only changes where the icon comes from and how the tooltip reads.
 *
 * complete is the server's own completion verdict for this team. It is carried separately from
 * points/threshold because a tile with a threshold of zero is complete from the start with no points
 * at all, which points/threshold alone cannot express.
 *
 * Deliberately independent of any data source: {@link BingoBoardPanel} only ever sees a
 * List&lt;BingoTile&gt;, so sample data and real server data feed the same renderer unchanged.
 */
public final class BingoTile
{
    public static final String KIND_DROP = "drop";
    public static final String KIND_KC = "kc";
    public static final String KIND_XP = "xp";

    public final String code; // e.g. "A1"
    public final String name;
    public final int row; // 0-based
    public final int col; // 0-based
    public final double points;
    public final double threshold;
    public final String bossName; // sprite label (boss/activity/skill name or WOM metric); null when this tile uses an item icon
    public final int spriteId; // resolved sprite id, -1 when none resolved
    public final int itemId; // 0 when this tile uses a sprite icon instead
    public final String kind; // "drop" | "kc" | "xp", never null
    public final boolean complete; // the server's completion verdict for this team

    private BingoTile(String code, String name, int row, int col, double points, double threshold,
        String bossName, int spriteId, int itemId, String kind, boolean complete)
    {
        this.code = code;
        this.name = name;
        this.row = row;
        this.col = col;
        this.points = points;
        this.threshold = threshold;
        this.bossName = bossName;
        this.spriteId = spriteId;
        this.itemId = itemId;
        this.kind = kind == null || kind.isEmpty() ? KIND_DROP : kind;
        this.complete = complete;
    }

    /** Sprite-icon tile whose label is looked up as a boss/activity name. */
    public static BingoTile boss(String code, String name, int row, int col, double points, double threshold,
        String bossName)
    {
        return boss(code, name, row, col, points, threshold, bossName, KIND_DROP, false);
    }

    public static BingoTile boss(String code, String name, int row, int col, double points, double threshold,
        String bossName, String kind, boolean complete)
    {
        return new BingoTile(code, name, row, col, points, threshold, bossName,
            BingoTiles.bossSpriteId(bossName), 0, kind, complete);
    }

    /** Sprite-icon tile with an already-resolved sprite id (used for kc/xp tiles, whose icon comes
     *  from the WOM metric and may be a skill rather than a boss). */
    public static BingoTile sprite(String code, String name, int row, int col, double points, double threshold,
        String label, int spriteId, String kind, boolean complete)
    {
        return new BingoTile(code, name, row, col, points, threshold, label, spriteId, 0, kind, complete);
    }

    public static BingoTile item(String code, String name, int row, int col, double points, double threshold,
        int itemId)
    {
        return item(code, name, row, col, points, threshold, itemId, KIND_DROP, false);
    }

    public static BingoTile item(String code, String name, int row, int col, double points, double threshold,
        int itemId, String kind, boolean complete)
    {
        return new BingoTile(code, name, row, col, points, threshold, null, -1, itemId, kind, complete);
    }

    /** True when this tile draws a sprite rather than an item image. */
    public boolean isBoss()
    {
        return bossName != null;
    }

    /** True for a Wise Old Man scored tile, which has no items and never matches a drop. */
    public boolean isWomTile()
    {
        return KIND_KC.equalsIgnoreCase(kind) || KIND_XP.equalsIgnoreCase(kind);
    }

    /** "KC" / "XP" for a Wise Old Man tile, else null: a short suffix for the tile tooltip. */
    public String kindLabel()
    {
        if (KIND_KC.equalsIgnoreCase(kind)) return "KC";
        if (KIND_XP.equalsIgnoreCase(kind)) return "XP";
        return null;
    }
}
