package com.droplogger;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * A Boss of the Week card in the Events tab (spec 2026-10-07): the KC ranking, the boss's collection
 * log page with how many of each item the clan got this week, and who got what, newest first, a page
 * at a time. Clicking a name shows that player's items for the week right under that line.
 */
final class BotwCardPanel
{
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_BOLD = FONT_SMALL.deriveFont(Font.BOLD);
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color LIVE = new Color(90, 200, 90);
    private static final Color LINK = new Color(140, 190, 255);
    private static final int GRID_COLUMNS = 5;
    private static final int TOP_KC = 10;
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, h:mma", Locale.ENGLISH);

    private BotwCardPanel()
    {
    }

    static String pageLabel(int page, int pages)
    {
        return "Page " + page + " of " + pages;
    }

    static String phaseLabel(String phase)
    {
        if ("live".equals(phase)) return "LIVE";
        if ("ended".equals(phase)) return "Ended";
        return "Starts soon";
    }

    static String kcLine(int place, PlatformApiService.BotwRank r)
    {
        return place + ". " + r.rsn + ": " + r.kc + " KC";
    }

    static String ownedLine(PlatformApiService.BotwView v)
    {
        int owned = 0;
        for (PlatformApiService.BotwItem i : v.grid) if (i.count > 0) owned++;
        return owned + " of " + v.grid.size() + " items obtained";
    }

    /**
     * @param onToggleRsn  called with the clicked player's name (show or hide their items)
     * @param expandedRsn  the player whose items are shown, or null
     * @param player       that player's items, or null while loading
     */
    static JPanel build(PlatformApiService.BotwView view, ItemManager itemManager, Runnable onPrev, Runnable onNext,
                        Consumer<String> onToggleRsn, String expandedRsn, PlatformApiService.BotwPlayer player)
    {
        JPanel body = MassCardPanel.column();
        body.add(MassCardPanel.label(phaseLabel(view.phase), FONT_SMALL, "live".equals(view.phase) ? LIVE : MUTED));

        body.add(MassCardPanel.label("KC", FONT_BOLD, Color.WHITE));
        if (view.ranking.isEmpty()) body.add(MassCardPanel.label("No KC yet", FONT_SMALL, MUTED));
        for (int i = 0; i < Math.min(TOP_KC, view.ranking.size()); i++)
        {
            body.add(MassCardPanel.label(kcLine(i + 1, view.ranking.get(i)), FONT_SMALL, i == 0 ? Color.YELLOW : MUTED));
        }

        body.add(MassCardPanel.label("Collection log" + (view.category != null ? ": " + view.category : ""), FONT_BOLD, Color.WHITE));
        if (view.grid.isEmpty())
        {
            body.add(MassCardPanel.label(view.category == null ? "No collection log page for this event" : "Waiting for the page to load", FONT_SMALL, MUTED));
        }
        else
        {
            JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 2, 2));
            grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            grid.setBorder(new EmptyBorder(4, 0, 2, 0));
            for (PlatformApiService.BotwItem item : view.grid)
            {
                grid.add(MassCardPanel.itemCell(item.itemId, item.itemName, item.count, itemManager));
            }
            body.add(grid);
            body.add(MassCardPanel.label(ownedLine(view), FONT_SMALL, MUTED));
        }

        body.add(MassCardPanel.label("Drops", FONT_BOLD, Color.WHITE));
        if (view.drops.isEmpty())
        {
            body.add(MassCardPanel.label("No drops yet", FONT_SMALL, MUTED));
            return body;
        }
        boolean shownPlayer = false;
        for (PlatformApiService.BotwDrop d : view.drops)
        {
            body.add(dropRow(d, onToggleRsn));
            // The expanded player's items go under their first line on this page.
            if (!shownPlayer && d.rsn != null && d.rsn.equalsIgnoreCase(expandedRsn))
            {
                body.add(playerBlock(player));
                shownPlayer = true;
            }
        }
        if (view.pages > 1)
        {
            JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            nav.setBackground(ColorScheme.DARK_GRAY_COLOR);
            nav.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton prev = new JButton("Previous");
            prev.setEnabled(view.page > 1);
            prev.addActionListener(e -> onPrev.run());
            JButton next = new JButton("Next");
            next.setEnabled(view.page < view.pages);
            next.addActionListener(e -> onNext.run());
            nav.add(prev);
            nav.add(MassCardPanel.label(pageLabel(view.page, view.pages), FONT_SMALL, MUTED));
            nav.add(next);
            body.add(nav);
        }
        return body;
    }

    private static JPanel dropRow(PlatformApiService.BotwDrop d, Consumer<String> onToggleRsn)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        javax.swing.JLabel name = MassCardPanel.label(d.rsn, FONT_SMALL, LINK);
        name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        name.setToolTipText("Show what " + d.rsn + " got this week");
        name.addMouseListener(new MouseAdapter()
        {
            @Override public void mousePressed(MouseEvent e) { onToggleRsn.accept(d.rsn); }
        });
        row.add(name);
        row.add(MassCardPanel.label(" · " + d.itemName + " · " + MassCardPanel.local(d.at, DAY_TIME), FONT_SMALL, Color.WHITE));
        return row;
    }

    private static JPanel playerBlock(PlatformApiService.BotwPlayer player)
    {
        JPanel block = MassCardPanel.column();
        block.setBorder(new EmptyBorder(0, 12, 4, 0));
        if (player == null)
        {
            block.add(MassCardPanel.label("Loading...", FONT_SMALL, MUTED));
            return block;
        }
        if (player.items.isEmpty()) block.add(MassCardPanel.label("Nothing from this page yet", FONT_SMALL, MUTED));
        for (PlatformApiService.BotwPlayerItem i : player.items)
        {
            block.add(MassCardPanel.label(i.itemName + (i.count > 1 ? " x" + i.count : "") + " · " + MassCardPanel.local(i.firstAt, DAY_TIME), FONT_SMALL, MUTED));
        }
        return block;
    }
}
