package com.droplogger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * A Boss of the Week card in the Events tab (spec 2026-10-07; layout Ryan approved 2026-10-08):
 * a header with a LIVE dot and the time left, the KC list, the boss's collection log with a progress
 * bar, a live feed of the week's drops ten at a time, and, once a name in the KC list or the feed is
 * clicked, one player panel at the bottom with that player's KC and what they got.
 */
final class BotwCardPanel
{
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_HEAD = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_BOLD = FONT.deriveFont(Font.BOLD);
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color LIVE = new Color(90, 200, 90);
    private static final Color LINK = new Color(140, 190, 255);
    private static final Color GOLD = new Color(255, 205, 60);
    private static final Color SILVER = new Color(200, 200, 210);
    private static final Color BRONZE = new Color(205, 127, 50);
    private static final Color BAR_BG = new Color(55, 55, 55);
    private static final Color SECTION_LINE = new Color(70, 70, 70);
    private static final int GRID_COLUMNS = 5;
    private static final int TOP_KC = 10;
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE h:mma", Locale.ENGLISH);

    private BotwCardPanel()
    {
    }

    // ── Text helpers (tested) ────────────────────────────────────────────────────────────────
    static String timeLeft(PlatformApiService.BotwView v, Instant now)
    {
        if ("ended".equals(v.phase)) return "Ended";
        boolean upcoming = "upcoming".equals(v.phase);
        String iso = upcoming ? v.start : v.end;
        if (iso == null) return upcoming ? "Starts soon" : "";
        try
        {
            String span = span(Duration.between(now, Instant.parse(iso)));
            return upcoming ? "starts in " + span : span + " left";
        }
        catch (Exception e)
        {
            return "";
        }
    }

    static String ago(String iso, Instant now)
    {
        if (iso == null) return "";
        try
        {
            Duration d = Duration.between(Instant.parse(iso), now);
            return d.toMinutes() < 1 ? "now" : span(d);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private static String span(Duration d)
    {
        long m = Math.max(0, d.toMinutes());
        if (m < 60) return m + "m";
        if (m < 48 * 60) return (m / 60) + "h";
        return (m / (24 * 60)) + "d";
    }

    static Color placeColor(int place)
    {
        return place == 1 ? GOLD : place == 2 ? SILVER : place == 3 ? BRONZE : MUTED;
    }

    static int owned(PlatformApiService.BotwView v)
    {
        int n = 0;
        for (PlatformApiService.BotwItem i : v.grid) if (i.count > 0) n++;
        return n;
    }

    static String ownedLabel(PlatformApiService.BotwView v)
    {
        return owned(v) + " / " + v.grid.size();
    }

    // ── The card ─────────────────────────────────────────────────────────────────────────────
    /**
     * @param onSelectPlayer called with a clicked name (KC list or drops feed)
     * @param onClosePlayer  the player panel's close button
     * @param selectedRsn    the player whose panel is open, or null
     * @param player         that player's data, or null while it loads
     */
    static JPanel build(PlatformApiService.BotwView view, ItemManager itemManager, Runnable onPrev, Runnable onNext,
                        Consumer<String> onSelectPlayer, Runnable onClosePlayer, String selectedRsn,
                        PlatformApiService.BotwPlayer player, Instant now)
    {
        JPanel body = MassCardPanel.column();

        // Status line: a green dot while live, and the time left.
        JPanel status = row();
        boolean live = "live".equals(view.phase);
        status.add(MassCardPanel.label(live ? "● LIVE" : "ended".equals(view.phase) ? "Ended" : "Upcoming", FONT_HEAD, live ? LIVE : MUTED), BorderLayout.WEST);
        String left = timeLeft(view, now);
        if (!left.isEmpty() && !"Ended".equals(left)) status.add(MassCardPanel.label(left, FONT_SMALL, MUTED), BorderLayout.EAST);
        body.add(status);

        // KC
        body.add(sectionHeader("KC", null));
        if (view.ranking.isEmpty()) body.add(MassCardPanel.label("No KC yet", FONT_SMALL, MUTED));
        for (int i = 0; i < Math.min(TOP_KC, view.ranking.size()); i++)
        {
            PlatformApiService.BotwRank r = view.ranking.get(i);
            JPanel line = row();
            JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            who.setOpaque(false);
            who.add(MassCardPanel.label((i + 1) + "  ", FONT_BOLD, placeColor(i + 1)));
            who.add(nameLink(r.rsn, r.rsn.equalsIgnoreCase(selectedRsn), onSelectPlayer));
            line.add(who, BorderLayout.WEST);
            line.add(MassCardPanel.label(String.valueOf(r.kc), FONT_BOLD, i < 3 ? placeColor(i + 1) : Color.WHITE), BorderLayout.EAST);
            body.add(line);
        }

        // Collection log
        body.add(sectionHeader("COLLECTION LOG" + (view.category != null ? ": " + view.category.toUpperCase(Locale.ENGLISH) : ""),
            view.grid.isEmpty() ? null : ownedLabel(view)));
        if (view.grid.isEmpty())
        {
            body.add(MassCardPanel.label(view.category == null ? "No collection log page for this event" : "Waiting for the page to load", FONT_SMALL, MUTED));
        }
        else
        {
            body.add(progressBar(owned(view), view.grid.size()));
            JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 2, 2));
            grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            grid.setBorder(new EmptyBorder(4, 0, 2, 0));
            for (PlatformApiService.BotwItem item : view.grid)
            {
                grid.add(MassCardPanel.itemCell(item.itemId, item.itemName, item.count, itemManager));
            }
            body.add(grid);
        }

        // Recent drops: the week's feed, ten at a time
        body.add(sectionHeader("RECENT DROPS", view.total > 0 ? String.valueOf(view.total) : null));
        if (view.drops.isEmpty())
        {
            body.add(MassCardPanel.label("No drops yet", FONT_SMALL, MUTED));
        }
        for (PlatformApiService.BotwDrop d : view.drops)
        {
            body.add(dropRow(d, itemManager, selectedRsn, onSelectPlayer, now));
        }
        if (view.pages > 1)
        {
            JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
            nav.setBackground(ColorScheme.DARK_GRAY_COLOR);
            nav.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.add(smallButton("‹ Prev", view.page > 1, onPrev));
            nav.add(MassCardPanel.label(view.page + " / " + view.pages, FONT_SMALL, MUTED));
            nav.add(smallButton("Next ›", view.page < view.pages, onNext));
            body.add(nav);
        }

        // The selected player's panel
        if (selectedRsn != null)
        {
            body.add(playerPanel(selectedRsn, player, itemManager, onClosePlayer));
        }
        return body;
    }

    private static JPanel row()
    {
        JPanel p = new JPanel(new BorderLayout(6, 0))
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(new EmptyBorder(1, 0, 1, 0));
        return p;
    }

    private static JPanel sectionHeader(String title, String right)
    {
        JPanel h = row();
        h.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, SECTION_LINE), new EmptyBorder(6, 0, 3, 0)));
        h.add(MassCardPanel.label(title, FONT_HEAD, Color.WHITE), BorderLayout.WEST);
        if (right != null) h.add(MassCardPanel.label(right, FONT_SMALL, MUTED), BorderLayout.EAST);
        return h;
    }

    private static JLabel nameLink(String rsn, boolean selected, Consumer<String> onSelect)
    {
        JLabel name = MassCardPanel.label(rsn, selected ? FONT_BOLD : FONT, selected ? Color.WHITE : LINK);
        name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        name.setToolTipText("Show " + rsn + "'s KC and drops");
        name.addMouseListener(new MouseAdapter()
        {
            @Override public void mousePressed(MouseEvent e) { onSelect.accept(rsn); }
        });
        return name;
    }

    private static JPanel progressBar(int done, int total)
    {
        final double frac = total == 0 ? 0 : Math.min(1, (double) done / total);
        JPanel bar = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                g.setColor(BAR_BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(LIVE);
                g.fillRect(0, 0, (int) Math.round(getWidth() * frac), getHeight());
            }

            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 6); }
        };
        bar.setPreferredSize(new Dimension(100, 6));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        return bar;
    }

    private static JLabel itemIcon(Integer itemId, ItemManager itemManager)
    {
        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(22, 20));
        if (itemManager != null && itemId != null && itemId > 0)
        {
            AsyncBufferedImage img = itemManager.getImage(itemId);
            Runnable set = () -> icon.setIcon(new ImageIcon(ImageUtil.resizeImage((BufferedImage) img, 22, 20)));
            set.run();
            img.onLoaded(() -> { set.run(); icon.revalidate(); icon.repaint(); });
        }
        return icon;
    }

    private static JPanel dropRow(PlatformApiService.BotwDrop d, ItemManager itemManager, String selectedRsn,
                                  Consumer<String> onSelect, Instant now)
    {
        JPanel line = row();
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(itemIcon(d.itemId, itemManager));
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(MassCardPanel.label(d.itemName, FONT_SMALL, Color.WHITE));
        text.add(nameLink(d.rsn, d.rsn != null && d.rsn.equalsIgnoreCase(selectedRsn), onSelect));
        left.add(text);
        line.add(left, BorderLayout.WEST);
        line.add(MassCardPanel.label(ago(d.at, now), FONT_SMALL, MUTED), BorderLayout.EAST);
        return line;
    }

    private static JButton smallButton(String text, boolean enabled, Runnable action)
    {
        JButton b = new JButton(text);
        b.setFont(FONT_SMALL);
        b.setEnabled(enabled);
        b.setFocusPainted(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JPanel playerPanel(String rsn, PlatformApiService.BotwPlayer player, ItemManager itemManager, Runnable onClose)
    {
        JPanel panel = MassCardPanel.column();
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, LINK), new EmptyBorder(6, 6, 6, 6)));

        JPanel head = row();
        head.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        head.add(MassCardPanel.label(rsn, FONT_BOLD, Color.WHITE), BorderLayout.WEST);
        JLabel close = MassCardPanel.label("✕", FONT_BOLD, MUTED);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setToolTipText("Close");
        close.addMouseListener(new MouseAdapter()
        {
            @Override public void mousePressed(MouseEvent e) { onClose.run(); }
        });
        head.add(close, BorderLayout.EAST);
        panel.add(head);

        if (player == null)
        {
            panel.add(MassCardPanel.label("Loading...", FONT_SMALL, MUTED));
            return panel;
        }
        panel.add(MassCardPanel.label(player.kc + " KC this week", FONT_SMALL, player.kc > 0 ? GOLD : MUTED));
        if (player.items.isEmpty())
        {
            panel.add(MassCardPanel.label("No drops from this page yet", FONT_SMALL, MUTED));
            return panel;
        }
        JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 2, 2));
        grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setBorder(new EmptyBorder(4, 0, 4, 0));
        for (PlatformApiService.BotwPlayerItem i : player.items)
        {
            grid.add(MassCardPanel.itemCell(i.itemId != null ? i.itemId : 0, i.itemName, i.count, itemManager));
        }
        panel.add(grid);
        for (PlatformApiService.BotwPlayerItem i : player.items)
        {
            JPanel line = row();
            line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            line.add(MassCardPanel.label(i.itemName + (i.count > 1 ? " x" + i.count : ""), FONT_SMALL, Color.WHITE), BorderLayout.WEST);
            line.add(MassCardPanel.label(MassCardPanel.local(i.firstAt, DAY_TIME), FONT_SMALL, MUTED), BorderLayout.EAST);
            panel.add(line);
        }
        return panel;
    }
}
