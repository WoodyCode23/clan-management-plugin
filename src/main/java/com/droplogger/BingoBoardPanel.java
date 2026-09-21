package com.droplogger;

import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders a rows x cols bingo board. Kept independent of any particular data source: it takes
 * rows, cols and a List&lt;BingoTile&gt; (plus the two game-icon managers), so the same renderer
 * draws today's sample data and tomorrow's real server data unchanged.
 *
 * Each tile shows its sprite/item icon with gold filling up from the bottom in proportion to
 * points/threshold, like a glass filling; at 100% the tile turns fully gold and gets a bright gold
 * border. Points read out underneath as "12/30". A tile whose icon could not be resolved (an
 * item-less tile, or a Wise Old Man metric with no matching sprite) draws its tile code in the
 * middle instead, so it still reads as a real tile.
 *
 * Every kind of tile renders identically here: a kc/xp tile's points arrive the same way a drop
 * tile's do, so the fill needs no special case.
 */
public class BingoBoardPanel extends JPanel
{
    /**
     * Places tiles into a rows x cols grid. row/col from the server are ONE-based: a board is
     * authored in a spreadsheet whose top-left tile is row 1, col 1, and the server's own
     * tileWithinGrid rejects anything below 1. Treating them as zero-based left the top-left cell
     * blank and silently dropped the whole last row and column off the board (the dev preview hid
     * it by using zero-based sample data). Anything outside the grid is ignored rather than
     * throwing: a host who shrinks a board mid-event leaves tiles behind.
     *
     * Package-private and pure so the indexing can be tested without building a panel.
     */
    static BingoTile[][] layOutForTest(java.util.List<BingoTile> tiles, int rows, int cols)
    {
        BingoTile[][] grid = new BingoTile[rows][cols];
        if (tiles == null) return grid;
        for (BingoTile t : tiles)
        {
            if (t != null && t.row >= 1 && t.row <= rows && t.col >= 1 && t.col <= cols)
            {
                grid[t.row - 1][t.col - 1] = t;
            }
        }
        return grid;
    }

    private static final Color GOLD = new Color(212, 175, 55);
    private static final Color GOLD_FILL = new Color(212, 175, 55, 195); // translucent so the icon stays readable
    private static final Color BRIGHT_GOLD_BORDER = new Color(255, 215, 0);
    private static final Font POINTS_FONT = new Font("Segoe UI", Font.PLAIN, 9);
    private static final Font CODE_FONT = new Font("Segoe UI", Font.BOLD, 11);

    private static final int USABLE_WIDTH = 225; // RuneLite side panel usable width
    private static final int GAP = 3;

    public BingoBoardPanel(int rows, int cols, List<BingoTile> tiles, ItemManager itemManager, SpriteManager spriteManager)
    {
        this(rows, cols, tiles, itemManager, spriteManager, null);
    }

    /**
     * Same as the three-manager constructor, plus an optional onTileClick callback (null = no click
     * behaviour, identical to the original constructor). Invoked on the EDT with the clicked tile;
     * a click on an empty grid cell (no tile mapped to that row/col) is a no-op.
     */
    public BingoBoardPanel(int rowsIn, int colsIn, List<BingoTile> tiles, ItemManager itemManager, SpriteManager spriteManager,
        java.util.function.Consumer<BingoTile> onTileClick)
    {
        // GridLayout throws when rows and cols are both zero, which a payload with a board but no
        // grid size would otherwise produce; one empty cell is a harmless board, an exception is not.
        int rows = Math.max(1, rowsIn);
        int cols = Math.max(1, colsIn);
        setLayout(new GridLayout(rows, cols, GAP, GAP));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setOpaque(true);

        int tileSize = Math.max(16, (USABLE_WIDTH - GAP * (cols - 1)) / Math.max(1, cols));
        int width = tileSize * cols + GAP * (cols - 1);
        int height = tileSize * rows + GAP * (rows - 1);
        Dimension size = new Dimension(width, height);
        setPreferredSize(size);
        setMaximumSize(size);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        BingoTile[][] grid = layOutForTest(tiles, rows, cols);

        // GridLayout adds row-major, matching this loop order.
        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                add(new TileComponent(grid[r][c], tileSize, itemManager, spriteManager, onTileClick));
            }
        }
    }

    /**
     * One tile's custom-painted cell: dark background, gold fill rising from the bottom, a
     * centred icon, the points readout, and (at 100%) a bright gold border. The icon is cached on
     * this component (one per tile) and loaded once; both SpriteManager and ItemManager callbacks
     * can land off the EDT, so every store-and-repaint hops through SwingUtilities.invokeLater. A
     * missing or failed icon lookup just leaves the tile without one, never throws.
     */
    private static final class TileComponent extends JComponent
    {
        private final BingoTile tile;
        private volatile BufferedImage icon;

        TileComponent(BingoTile tile, int size, ItemManager itemManager, SpriteManager spriteManager,
            java.util.function.Consumer<BingoTile> onTileClick)
        {
            this.tile = tile;
            setPreferredSize(new Dimension(size, size));
            setOpaque(false);
            if (tile != null)
            {
                // A kc/xp tile says so, since "30/50 pts" alone would not explain where its points
                // come from (Wise Old Man gains, not drops).
                String kindLabel = tile.kindLabel();
                String heading = kindLabel != null ? tile.name + " (" + kindLabel + ")" : tile.name;
                setToolTipText(heading + ": " + BingoTiles.formatPoints(tile.points, tile.threshold) + " pts");
                loadIcon(itemManager, spriteManager);
                if (onTileClick != null)
                {
                    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                    addMouseListener(new java.awt.event.MouseAdapter()
                    {
                        @Override public void mouseClicked(java.awt.event.MouseEvent e) { onTileClick.accept(tile); }
                    });
                }
            }
        }

        private void loadIcon(ItemManager itemManager, SpriteManager spriteManager)
        {
            try
            {
                if (tile.isBoss())
                {
                    if (spriteManager == null)
                    {
                        return;
                    }
                    // Already resolved when the tile was built (a kc/xp tile's sprite can be a skill,
                    // which the boss-only lookup would miss).
                    int spriteId = tile.spriteId;
                    if (spriteId < 0)
                    {
                        return; // no matching sprite: tile just renders its code instead of an icon
                    }
                    spriteManager.getSpriteAsync(spriteId, 0, img -> SwingUtilities.invokeLater(() ->
                    {
                        icon = img;
                        repaint();
                    }));
                }
                else if (itemManager != null && tile.itemId > 0)
                {
                    AsyncBufferedImage img = itemManager.getImage(tile.itemId);
                    icon = img;
                    img.onLoaded(() -> SwingUtilities.invokeLater(this::repaint));
                }
            }
            catch (Exception e)
            {
                // Never let an icon lookup failure take the board down with it.
                icon = null;
            }
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            if (tile == null)
            {
                return; // no tile mapped to this row/col: leave the cell blank
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            double frac = BingoTiles.fillFraction(tile.points, tile.threshold);
            // The server's own verdict wins when it says complete: a tile with a threshold of zero is
            // complete from the start with no points at all, which points/threshold cannot express.
            boolean full = tile.complete || frac >= 1.0;
            if (full) frac = 1.0;

            g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
            g2.fillRect(0, 0, w, h);

            int fillHeight = (int) Math.round(h * frac);
            if (fillHeight > 0)
            {
                g2.setColor(full ? GOLD : GOLD_FILL);
                g2.fillRect(0, h - fillHeight, w, fillHeight);
            }

            BufferedImage img = icon;
            if (img != null)
            {
                int iw = img.getWidth();
                int ih = img.getHeight();
                g2.drawImage(img, (w - iw) / 2, (h - ih) / 2 - 3, null);
            }
            else if (tile.code != null && !tile.code.isEmpty())
            {
                // No icon resolved (an item-less tile, or a Wise Old Man metric with no sprite): draw
                // the tile code so the cell still reads as a real tile rather than an empty square.
                g2.setFont(CODE_FONT);
                FontMetrics cfm = g2.getFontMetrics();
                int cx = (w - cfm.stringWidth(tile.code)) / 2;
                int cy = (h - cfm.getHeight()) / 2 + cfm.getAscent() - 3;
                g2.setColor(full ? new Color(70, 55, 10) : new Color(140, 140, 140));
                g2.drawString(tile.code, cx, cy);
            }

            String text = BingoTiles.formatPoints(tile.points, tile.threshold);
            g2.setFont(POINTS_FONT);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = h - 3;
            g2.setColor(Color.BLACK);
            g2.drawString(text, tx + 1, ty + 1);
            g2.setColor(Color.WHITE);
            g2.drawString(text, tx, ty);

            if (full)
            {
                g2.setColor(BRIGHT_GOLD_BORDER);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(1, 1, w - 3, h - 3);
            }

            g2.dispose();
        }
    }
}
