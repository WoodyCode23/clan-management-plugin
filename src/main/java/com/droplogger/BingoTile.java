package com.droplogger;

/**
 * Immutable bingo tile: identity, board position, progress, and an icon reference that is EITHER
 * a boss name (resolved to a sprite via {@link BingoTiles#bossSpriteId(String)}) OR an item id
 * (rendered via ItemManager). Exactly one of bossName/itemId is set; use the {@link #boss} /
 * {@link #item} factories rather than the constructor directly.
 *
 * Deliberately independent of any data source: {@link BingoBoardPanel} only ever sees a
 * List&lt;BingoTile&gt;, so sample data today and real server data later feed the same renderer
 * unchanged.
 */
public final class BingoTile
{
    public final String code; // e.g. "A1"
    public final String name;
    public final int row; // 0-based
    public final int col; // 0-based
    public final double points;
    public final double threshold;
    public final String bossName; // null when this tile uses an item icon instead
    public final int itemId; // 0 when this tile uses a boss icon instead

    private BingoTile(String code, String name, int row, int col, double points, double threshold,
        String bossName, int itemId)
    {
        this.code = code;
        this.name = name;
        this.row = row;
        this.col = col;
        this.points = points;
        this.threshold = threshold;
        this.bossName = bossName;
        this.itemId = itemId;
    }

    public static BingoTile boss(String code, String name, int row, int col, double points, double threshold,
        String bossName)
    {
        return new BingoTile(code, name, row, col, points, threshold, bossName, 0);
    }

    public static BingoTile item(String code, String name, int row, int col, double points, double threshold,
        int itemId)
    {
        return new BingoTile(code, name, row, col, points, threshold, null, itemId);
    }

    public boolean isBoss()
    {
        return bossName != null;
    }
}
