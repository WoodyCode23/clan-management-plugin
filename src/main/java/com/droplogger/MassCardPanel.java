package com.droplogger;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The body of a mass's event card (spec 2026-09-29-masses-design.md, "What people see").
 *
 * Before the mass: when it starts, how many are going, and Sign up (the server DMs a link to the
 * Discord event; clicking Interested there is the signup). While it runs: the boss's collection-log
 * grid with how many of each item were obtained, then who got what, newest first. After it ends the
 * same results stay as its Past card, headed "Ended" with how long it ran (Ryan, 2026-09-30).
 */
final class MassCardPanel
{
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color LIVE = new Color(90, 200, 90);
    private static final int GRID_COLUMNS = 5;
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, h:mma", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);

    private MassCardPanel() { }

    /**
     * @param signupResult the last Sign up result for this mass ("sent", "dm_closed", "failed"), or null
     * @param onSignUp     called when Sign up is clicked
     */
    static JPanel build(PlatformApiService.MassCard card, ItemManager itemManager, String signupResult, Runnable onSignUp)
    {
        JPanel body = column();
        if (card.bossName == null)
        {
            body.add(label("Boss not recognised, edit the event title", FONT_SMALL, MUTED));
        }
        if ("live".equals(card.phase) || "ended".equals(card.phase))
        {
            // Live and ended show the same results; an ended card is the Past entry, frozen at the end.
            buildResults(body, card, itemManager, "ended".equals(card.phase));
        }
        else
        {
            buildUpcoming(body, card, signupResult, onSignUp);
        }
        return body;
    }

    private static void buildUpcoming(JPanel body, PlatformApiService.MassCard card, String signupResult, Runnable onSignUp)
    {
        if (card.bossName != null) body.add(label(card.bossName, FONT, Color.WHITE));
        body.add(label("Starts " + local(card.start, DAY_TIME), FONT_SMALL, MUTED));
        body.add(label(card.goingCount + " going", FONT_SMALL, MUTED));

        if (card.youreGoing)
        {
            body.add(label("You're going ✓", FONT_SMALL, LIVE));
            return;
        }
        if ("sent".equals(signupResult))
        {
            body.add(label("Check your Discord DMs for the event link", FONT_SMALL, Color.WHITE));
            return;
        }
        if ("dm_closed".equals(signupResult))
        {
            body.add(label("Couldn't DM you. Find the event in Discord's Events list.", FONT_SMALL, Color.ORANGE));
        }
        else if ("failed".equals(signupResult))
        {
            body.add(label("Sign up didn't go through. Try again.", FONT_SMALL, Color.ORANGE));
        }
        JButton signUp = new JButton("Sign up");
        signUp.setFont(FONT_SMALL);
        signUp.setFocusPainted(false);
        signUp.setAlignmentX(Component.LEFT_ALIGNMENT);
        signUp.addActionListener(ev ->
        {
            signUp.setEnabled(false);
            signUp.setText("Sending...");
            onSignUp.run();
        });
        body.add(signUp);
    }

    private static void buildResults(JPanel body, PlatformApiService.MassCard card, ItemManager itemManager, boolean ended)
    {
        if (ended)
        {
            String ran = duration(card.start, card.end);
            body.add(label("Ended" + (ran.isEmpty() ? "" : " · ran " + ran), FONT_SMALL, MUTED));
        }
        else
        {
            body.add(label("LIVE · ends " + local(card.end, TIME), FONT_SMALL, LIVE));
        }
        String kills;
        if (card.totalKills == null) kills = ended ? "Kills: not recorded" : "Kills: waiting for WOM";
        else kills = "Kills: " + card.totalKills
            + (!ended && card.killsUpdatedAt != null ? " · updated " + minutesAgo(card.killsUpdatedAt) : "");
        body.add(label(kills, FONT_SMALL, Color.WHITE));
        for (int i = 0; i < card.killers.size(); i++)
        {
            PlatformApiService.MassKiller k = card.killers.get(i);
            body.add(label((i + 1) + ". " + k.rsn + ": " + k.kills, FONT_SMALL, MUTED));
        }

        if (!card.items.isEmpty())
        {
            JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 2, 2));
            grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            grid.setBorder(new EmptyBorder(4, 0, 4, 0));
            for (PlatformApiService.MassItem item : card.items)
            {
                grid.add(itemCell(item, itemManager));
            }
            body.add(grid);
        }

        if (card.drops.isEmpty())
        {
            body.add(label(ended ? "No drops recorded" : "No drops yet", FONT_SMALL, MUTED));
            return;
        }
        for (PlatformApiService.MassDrop d : card.drops)
        {
            body.add(label(d.itemName + " · " + d.rsn + " · " + local(d.at, TIME), FONT_SMALL, Color.WHITE));
        }
    }

    /** One grid square: the item icon with its count, faded when nobody has had it yet. */
    private static JPanel itemCell(PlatformApiService.MassItem item, ItemManager itemManager)
    {
        final boolean none = item.count == 0;
        JPanel cell = new JPanel(new BorderLayout())
        {
            @Override
            protected void paintChildren(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                if (none) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                super.paintChildren(g2);
                g2.dispose();
            }
        };
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setPreferredSize(new Dimension(36, 40));
        cell.setToolTipText(item.itemName + (none ? "" : " x" + item.count));

        JLabel icon = new JLabel();
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        if (itemManager != null && item.itemId > 0)
        {
            AsyncBufferedImage img = itemManager.getImage(item.itemId);
            icon.setIcon(new ImageIcon(img));
            img.onLoaded(() -> { icon.setIcon(new ImageIcon(img)); icon.revalidate(); icon.repaint(); });
        }
        cell.add(icon, BorderLayout.CENTER);

        JLabel count = new JLabel(none ? "" : String.valueOf(item.count), SwingConstants.RIGHT);
        count.setFont(FONT_SMALL.deriveFont(Font.BOLD));
        count.setForeground(Color.YELLOW);
        cell.add(count, BorderLayout.SOUTH);
        return cell;
    }

    private static JPanel column()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return p;
    }

    private static JLabel label(String text, Font font, Color color)
    {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setToolTipText(text);
        return l;
    }

    /** An ISO timestamp from the server, in the member's own time zone. */
    static String local(String iso, DateTimeFormatter fmt)
    {
        if (iso == null) return "";
        try
        {
            return Instant.parse(iso).atZone(ZoneId.systemDefault()).format(fmt).replace("AM", "am").replace("PM", "pm");
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /** "2h 15m" between two ISO timestamps, or "" when either is missing or unreadable. */
    static String duration(String startIso, String endIso)
    {
        try
        {
            long mins = Math.max(0, (Instant.parse(endIso).toEpochMilli() - Instant.parse(startIso).toEpochMilli()) / 60000);
            long h = mins / 60, m = mins % 60;
            return h > 0 ? h + "h " + m + "m" : m + "m";
        }
        catch (Exception e)
        {
            return "";
        }
    }

    static String minutesAgo(String iso)
    {
        try
        {
            long mins = Math.max(0, (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 60000);
            return mins == 0 ? "just now" : mins + " min ago";
        }
        catch (Exception e)
        {
            return "";
        }
    }

}
