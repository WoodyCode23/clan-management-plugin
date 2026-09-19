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
 * Each tile shows its boss/item icon with gold filling up from the bottom in proportion to
 * points/threshold, like a glass filling; at 100% the tile turns fully gold and gets a bright gold
 * border. Points read out underneath as "12/30".
 */
public class BingoBoardPanel extends JPanel
{
    private static final Color GOLD = new Color(212, 175, 55);
    private static final Color GOLD_FILL = new Color(212, 175, 55, 195); // translucent so the icon stays readable
    private static final Color BRIGHT_GOLD_BORDER = new Color(255, 215, 0);
    private static final Font POINTS_FONT = new Font("Segoe UI", Font.PLAIN, 9);

    private static final int USABLE_WIDTH = 225; // RuneLite side panel usable width
    private static final int GAP = 3;

    public BingoBoardPanel(int rows, int cols, List<BingoTile> tiles, ItemManager itemManager, SpriteManager spriteManager)
    {
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

        BingoTile[][] grid = new BingoTile[rows][cols];
        for (BingoTile t : tiles)
        {
            if (t != null && t.row >= 0 && t.row < rows && t.col >= 0 && t.col < cols)
            {
                grid[t.row][t.col] = t;
            }
        }

        // GridLayout adds row-major, matching this loop order.
        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                add(new TileComponent(grid[r][c], tileSize, itemManager, spriteManager));
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

        TileComponent(BingoTile tile, int size, ItemManager itemManager, SpriteManager spriteManager)
        {
            this.tile = tile;
            setPreferredSize(new Dimension(size, size));
            setOpaque(false);
            if (tile != null)
            {
                setToolTipText(tile.name + ": " + BingoTiles.formatPoints(tile.points, tile.threshold) + " pts");
                loadIcon(itemManager, spriteManager);
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
                    int spriteId = BingoTiles.bossSpriteId(tile.bossName);
                    if (spriteId < 0)
                    {
                        return; // no matching boss sprite: tile just renders without an icon
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
            boolean full = frac >= 1.0;

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
