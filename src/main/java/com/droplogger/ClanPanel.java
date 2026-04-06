package com.droplogger;

import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class ClanPanel extends PluginPanel
{
    private static final Font READABLE_FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font READABLE_FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font READABLE_FONT_ITALIC = new Font("Segoe UI", Font.ITALIC, 10);

    // ── Home tab components ──
    private final JLabel homeStatusLabel = new JLabel("");
    private final JPanel announcementsPanel = new JPanel();
    private final JPanel activityPanel = new JPanel();

    // ── Event card components ──
    private final JPanel eventCardPanel = new JPanel();
    private final JLabel eventTitleLabel = new JLabel();
    private final JLabel eventCountdownLabel = new JLabel();
    private final JPanel eventLeaderboardPanel = new JPanel();
    private javax.swing.Timer eventCountdownTimer;
    private String eventEndTimeStr;

    // ── Layout ──
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JPanel notConnectedPanel = new JPanel();
    private final JPanel cardContainer = new JPanel(new CardLayout());
    private static final String CARD_NOT_CONNECTED = "notConnected";
    private static final String CARD_CONNECTED = "connected";

    // ── Bingo tab ──
    private BingoPanel bingoPanel;
    private boolean bingoTabVisible = false;
    private boolean connected = false;

    private Runnable onRefresh;

    // ── Hiscores tab components ──
    private final JPanel hiscoresContentPanel = new JPanel();
    private final JPanel hiscoreTimesPanel = new JPanel();
    private final JTextField hiscoreSearchField = new JTextField();
    private final JComboBox<String> hiscoreGroupCombo = new JComboBox<>();
    private final JComboBox<String> hiscoreBossCombo = new JComboBox<>();
    private final JComboBox<String> hiscoreSizeCombo = new JComboBox<>();
    private final JLabel hiscoreSizeLabel = new JLabel("Size:");
    private final JLabel hiscoreGroupLabel = new JLabel("Category:");
    private final JLabel hiscoreBossLabel = new JLabel("Boss:");
    private java.util.function.BiConsumer<BossCategory, JPanel> onFetchTimes;
    private Runnable onClearHiscoreCache;
    private boolean hiscoreDropdownsUpdating = false;
    private Set<String> recentCategoryKeys = new java.util.LinkedHashSet<>();
    private Map<String, List<HiscoreEntry>> recentCategoryEntries = new java.util.LinkedHashMap<>();

    // ── Drops tab components ──
    private final JPanel dropsLeaderboardPanel = new JPanel();
    private final JPanel dropsRecentPanel = new JPanel();
    private final JPanel playerDetailPanel = new JPanel();
    private Runnable onRefreshDropsTab;
    private java.util.function.Consumer<String> onFetchPlayerDrops;

    // ── Whitelist browser components ──
    private final JPanel whitelistBrowserPanel = new JPanel();
    private final JTextField whitelistSearchField = new JTextField();
    private final JComboBox<String> whitelistCategoryFilter = new JComboBox<>();
    private final JComboBox<String> whitelistSortCombo = new JComboBox<>(new String[]{"Points (High)", "Points (Low)", "Name (A-Z)", "Source (A-Z)"});
    private List<Map<String, String>> cachedClanWhitelist = Collections.emptyList();
    private Runnable onRefreshWhitelist;

    // ── WOM XP tab components ──
    private final JPanel womLeaderboardPanel = new JPanel();
    private final JComboBox<String> womMetricCombo = new JComboBox<>();
    private final JComboBox<String> womPeriodCombo = new JComboBox<>(new String[]{"Day", "Week", "Month", "Year"});
    private final JComboBox<String> womModeCombo = new JComboBox<>(new String[]{"XP Gained"});
    private java.util.function.BiConsumer<String, String> onFetchWomData;

    // Status indicators (bottom of home tab)
    private JLabel statusClogLabel;
    private JLabel statusXpLabel;
    private JLabel statusHiscoresLabel;

    // Collection log sync UI (automatic — kept for setClogSyncStatus)
    private JLabel clogCountLabel;
    private JLabel clogStatusLabel;

    // Dynamic clan name labels
    private JLabel notConnectedTitleLabel;
    private JLabel homeTitleLabel;

    public ClanPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // "Not connected" placeholder
        notConnectedPanel.setLayout(new BorderLayout());
        notConnectedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel msgBox = new JPanel();
        msgBox.setLayout(new BoxLayout(msgBox, BoxLayout.Y_AXIS));
        msgBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        msgBox.setBorder(new EmptyBorder(40, 20, 20, 20));
        notConnectedTitleLabel = new JLabel("Clan Management");
        notConnectedTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        notConnectedTitleLabel.setForeground(Color.WHITE);
        notConnectedTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel instrLabel = new JLabel("<html><center>Enter your Board Code in<br>plugin settings to connect.</center></html>");
        instrLabel.setFont(READABLE_FONT);
        instrLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        instrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        instrLabel.setBorder(new EmptyBorder(12, 0, 0, 0));
        msgBox.add(notConnectedTitleLabel);
        msgBox.add(instrLabel);
        notConnectedPanel.add(msgBox, BorderLayout.NORTH);

        tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(10f));

        // Home tab (always present, default)
        tabbedPane.addTab("Home", buildHomeTab());

        // Hiscores tab
        tabbedPane.addTab("Hiscores", buildHiscoresTab());

        // Drops tab (leaderboard + recent)
        tabbedPane.addTab("Drops", buildDropsTab());

        // XP tab (WOM leaderboards)
        tabbedPane.addTab("XP", buildWomTab());

        // Activity tab (clan joins, leaves, rank changes)
        tabbedPane.addTab("Activity", buildActivityTab());

        // CardLayout to switch between not-connected and connected views
        cardContainer.add(notConnectedPanel, CARD_NOT_CONNECTED);
        cardContainer.add(tabbedPane, CARD_CONNECTED);
        add(cardContainer, BorderLayout.CENTER);
    }

    // ══════════════════════════════════════════
    // Home Tab
    // ══════════════════════════════════════════

    private JPanel buildHomeTab()
    {
        JPanel home = new JPanel();
        home.setLayout(new BoxLayout(home, BoxLayout.Y_AXIS));
        home.setBackground(ColorScheme.DARK_GRAY_COLOR);
        home.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Title
        homeTitleLabel = new JLabel("Clan");
        homeTitleLabel.setFont(homeTitleLabel.getFont().deriveFont(Font.BOLD, 22f));
        homeTitleLabel.setForeground(ACCENT_GOLD);
        homeTitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        homeTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        homeTitleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        home.add(homeTitleLabel);
        home.add(Box.createVerticalStrut(2));

        JLabel subtitle = new JLabel("Clan Plugin");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 13f));
        subtitle.setForeground(new Color(170, 170, 170));
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        home.add(subtitle);
        home.add(Box.createVerticalStrut(20));

        // Navigation cards — click to switch tabs by name
        home.add(createNavCard("Hiscores", "PB times & clan speed leaderboards", new Color(100, 149, 237), "Hiscores"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("XP", "Clan XP leaderboards from Wise Old Man", new Color(76, 175, 80), "XP"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("Drops", "Clan drop log, leaderboard & whitelist", new Color(255, 180, 100), "Drops"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("Activity", "Clan joins, leaves & rank changes", new Color(100, 180, 255), "Activity"));
        home.add(Box.createVerticalStrut(20));

        // ── Active Event card ──
        eventCardPanel.setLayout(new BoxLayout(eventCardPanel, BoxLayout.Y_AXIS));
        eventCardPanel.setBackground(new Color(40, 40, 40));
        eventCardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60)),
            new EmptyBorder(10, 10, 10, 10)));
        eventCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventCardPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        eventCardPanel.setVisible(false);

        eventTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        eventTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventCardPanel.add(eventTitleLabel);
        eventCardPanel.add(Box.createVerticalStrut(2));

        eventCountdownLabel.setFont(READABLE_FONT_SMALL);
        eventCountdownLabel.setForeground(new Color(170, 170, 170));
        eventCountdownLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventCardPanel.add(eventCountdownLabel);
        eventCardPanel.add(Box.createVerticalStrut(6));

        eventLeaderboardPanel.setLayout(new BoxLayout(eventLeaderboardPanel, BoxLayout.Y_AXIS));
        eventLeaderboardPanel.setBackground(new Color(40, 40, 40));
        eventLeaderboardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventCardPanel.add(eventLeaderboardPanel);

        home.add(eventCardPanel);
        home.add(Box.createVerticalStrut(12));

        // Hidden labels for clog sync feedback (used by setClogSyncStatus/updateClogSyncCount)
        clogCountLabel = new JLabel("");
        clogCountLabel.setVisible(false);
        clogStatusLabel = new JLabel("");
        clogStatusLabel.setVisible(false);

        // ── Announcements section ──
        JLabel announcementsTitle = new JLabel("Announcements");
        announcementsTitle.setFont(announcementsTitle.getFont().deriveFont(Font.BOLD, 13f));
        announcementsTitle.setForeground(ACCENT_GOLD);
        announcementsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        home.add(announcementsTitle);
        home.add(Box.createVerticalStrut(6));

        announcementsPanel.setLayout(new BoxLayout(announcementsPanel, BoxLayout.Y_AXIS));
        announcementsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        announcementsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel noAnnouncements = new JLabel("No announcements");
        noAnnouncements.setFont(noAnnouncements.getFont().deriveFont(Font.ITALIC, 11f));
        noAnnouncements.setForeground(new Color(100, 100, 100));
        announcementsPanel.add(noAnnouncements);

        home.add(announcementsPanel);
        home.add(Box.createVerticalStrut(12));

        // ── Tracking Status (bottom) ──
        home.add(Box.createVerticalGlue());
        home.add(Box.createVerticalStrut(12));

        JPanel statusRow = new JPanel(new GridLayout(1, 3, 6, 0));
        statusRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        statusClogLabel = new JLabel("--", SwingConstants.CENTER);
        statusXpLabel = new JLabel("--", SwingConstants.CENTER);
        statusHiscoresLabel = new JLabel("--", SwingConstants.CENTER);

        statusRow.add(buildStatusBox("C-Log", statusClogLabel));
        statusRow.add(buildStatusBox("XP", statusXpLabel));
        statusRow.add(buildStatusBox("Hiscores", statusHiscoresLabel));
        home.add(statusRow);

        home.add(Box.createVerticalStrut(4));

        // Status
        homeStatusLabel.setFont(homeStatusLabel.getFont().deriveFont(10f));
        homeStatusLabel.setForeground(new Color(120, 120, 120));
        homeStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        home.add(homeStatusLabel);

        return home;
    }

    // ══════════════════════════════════════════
    // Activity Tab
    // ══════════════════════════════════════════

    private JPanel buildActivityTab()
    {
        JPanel tab = new JPanel();
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        tab.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tab.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Clan Activity");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(new Color(100, 180, 255));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        tab.add(title);
        tab.add(Box.createVerticalStrut(4));

        JLabel desc = new JLabel("Recent joins, leaves & rank changes");
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 11f));
        desc.setForeground(new Color(140, 140, 140));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        tab.add(desc);
        tab.add(Box.createVerticalStrut(10));

        activityPanel.setLayout(new BoxLayout(activityPanel, BoxLayout.Y_AXIS));
        activityPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        activityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel loading = new JLabel("Loading...");
        loading.setFont(loading.getFont().deriveFont(Font.ITALIC, 11f));
        loading.setForeground(new Color(100, 100, 100));
        activityPanel.add(loading);

        tab.add(activityPanel);

        return tab;
    }

    private JPanel buildStatusBox(String title, JLabel valueLabel)
    {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(new Color(35, 35, 35));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 55, 55)),
            new EmptyBorder(4, 4, 4, 4)));

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        titleLbl.setForeground(new Color(120, 120, 120));
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(titleLbl);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        valueLabel.setForeground(new Color(200, 200, 200));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(valueLabel);

        return box;
    }

    private JPanel createNavCard(String name, String description, Color accentColor, String tabName)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(new Color(40, 40, 40));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
            new EmptyBorder(10, 12, 10, 12)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Accent bar
        JPanel accent = new JPanel();
        accent.setBackground(accentColor);
        accent.setPreferredSize(new Dimension(4, 40));
        accent.setMaximumSize(new Dimension(4, 40));
        card.add(accent, BorderLayout.WEST);

        // Text
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(new Color(40, 40, 40));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setForeground(Color.WHITE);
        textPanel.add(nameLabel);
        textPanel.add(Box.createVerticalStrut(2));

        JLabel descLabel = new JLabel("<html>" + description + "</html>");
        descLabel.setFont(descLabel.getFont().deriveFont(11f));
        descLabel.setForeground(new Color(170, 170, 170));
        textPanel.add(descLabel);

        card.add(textPanel, BorderLayout.CENTER);

        // Arrow indicator
        JLabel arrow = new JLabel(">");
        arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, 12f));
        arrow.setForeground(new Color(100, 100, 100));
        card.add(arrow, BorderLayout.EAST);

        // Click to switch tab
        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                int idx = tabbedPane.indexOfTab(tabName);
                if (idx >= 0) tabbedPane.setSelectedIndex(idx);
            }
            @Override
            public void mouseEntered(MouseEvent e)
            {
                card.setBackground(new Color(50, 48, 35));
                textPanel.setBackground(new Color(50, 48, 35));
                accent.setBackground(accentColor.brighter());
                arrow.setForeground(accentColor);
            }
            @Override
            public void mouseExited(MouseEvent e)
            {
                card.setBackground(new Color(40, 40, 40));
                textPanel.setBackground(new Color(40, 40, 40));
                accent.setBackground(accentColor);
                arrow.setForeground(new Color(100, 100, 100));
            }
        });

        return card;
    }

    /**
     * Set announcements on the home tab. Each string is one announcement line.
     */
    public void setAnnouncements(List<String> messages)
    {
        SwingUtilities.invokeLater(() ->
        {
            announcementsPanel.removeAll();

            if (messages == null || messages.isEmpty())
            {
                JLabel none = new JLabel("No announcements");
                none.setFont(none.getFont().deriveFont(Font.ITALIC, 11f));
                none.setForeground(new Color(100, 100, 100));
                announcementsPanel.add(none);
            }
            else
            {
                for (String msg : messages)
                {
                    JPanel card = new JPanel(new BorderLayout(8, 0));
                    card.setBackground(new Color(30, 28, 15));
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_GOLD_DIM),
                        new EmptyBorder(8, 10, 8, 10)
                    ));
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

                    JLabel text = new JLabel("<html>" + msg + "</html>");
                    text.setFont(text.getFont().deriveFont(11f));
                    text.setForeground(ACCENT_GOLD_BRIGHT);
                    card.add(text, BorderLayout.CENTER);

                    announcementsPanel.add(card);
                    announcementsPanel.add(Box.createVerticalStrut(4));
                }
            }

            announcementsPanel.revalidate();
            announcementsPanel.repaint();
        });
    }

    /**
     * Update the active event card on the Home tab.
     */
    public void updateActiveEvent(String type, String displayName, String endTime,
                                  List<WomService.WomEntry> leaderboard)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (type == null || type.isEmpty())
            {
                eventCardPanel.setVisible(false);
                stopEventCountdown();
                return;
            }

            String title = EventMetrics.labelFromType(type);
            Color accentColor = EventMetrics.colorFromType(type);

            eventTitleLabel.setText(title + ": " + displayName);
            eventTitleLabel.setForeground(accentColor);

            // Store end time and start countdown
            eventEndTimeStr = endTime;
            updateEventCountdownText();
            startEventCountdown();

            // Update leaderboard
            eventLeaderboardPanel.removeAll();
            if (leaderboard != null && !leaderboard.isEmpty())
            {
                String unit = EventMetrics.unitFromType(type);
                int shown = Math.min(5, leaderboard.size());
                for (int i = 0; i < shown; i++)
                {
                    WomService.WomEntry entry = leaderboard.get(i);
                    String prefix = "#" + (i + 1) + " ";
                    JLabel row = new JLabel(prefix + entry.username + " — " +
                        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(entry.gained) + unit);
                    row.setFont(READABLE_FONT_SMALL);
                    row.setForeground(new Color(200, 200, 200));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    eventLeaderboardPanel.add(row);
                    eventLeaderboardPanel.add(Box.createVerticalStrut(2));
                }
            }
            else
            {
                JLabel noData = new JLabel("No participants yet");
                noData.setFont(READABLE_FONT_ITALIC);
                noData.setForeground(new Color(100, 100, 100));
                noData.setAlignmentX(Component.LEFT_ALIGNMENT);
                eventLeaderboardPanel.add(noData);
            }

            eventCardPanel.setVisible(true);
            eventCardPanel.revalidate();
            eventCardPanel.repaint();
        });
    }

    private void updateEventCountdownText()
    {
        if (eventEndTimeStr == null || eventEndTimeStr.isEmpty())
        {
            eventCountdownLabel.setText("");
            return;
        }
        try
        {
            java.time.LocalDateTime endDt = java.time.LocalDateTime.parse(eventEndTimeStr);
            java.time.ZonedDateTime endZoned = endDt.atZone(java.time.ZoneId.of("America/New_York"));
            java.time.Duration remaining = java.time.Duration.between(java.time.ZonedDateTime.now(
                java.time.ZoneId.of("America/New_York")), endZoned);
            if (remaining.isNegative())
            {
                eventCountdownLabel.setText("Event has ended");
            }
            else
            {
                long days = remaining.toDays();
                long hours = remaining.toHours() % 24;
                long minutes = remaining.toMinutes() % 60;
                eventCountdownLabel.setText("Ends in " + days + "d " + hours + "h " + minutes + "m");
            }
        }
        catch (Exception e)
        {
            eventCountdownLabel.setText("Ends: " + eventEndTimeStr);
        }
    }

    private void startEventCountdown()
    {
        stopEventCountdown();
        eventCountdownTimer = new javax.swing.Timer(60_000, e -> updateEventCountdownText());
        eventCountdownTimer.setInitialDelay(0);
        eventCountdownTimer.start();
    }

    private void stopEventCountdown()
    {
        if (eventCountdownTimer != null)
        {
            eventCountdownTimer.stop();
            eventCountdownTimer = null;
        }
    }

    /**
     * Update the clan activity feed on the Activity tab.
     */
    public void updateActivity(List<WomService.ActivityEntry> entries)
    {
        SwingUtilities.invokeLater(() ->
        {
            activityPanel.removeAll();

            if (entries == null || entries.isEmpty())
            {
                JLabel none = new JLabel("No recent activity");
                none.setFont(none.getFont().deriveFont(Font.ITALIC, 11f));
                none.setForeground(new Color(100, 100, 100));
                activityPanel.add(none);
            }
            else
            {
                for (WomService.ActivityEntry entry : entries)
                {
                    JPanel row = new JPanel(new BorderLayout());
                    row.setBackground(ColorScheme.DARK_GRAY_COLOR);
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
                    row.setBorder(new EmptyBorder(1, 0, 1, 0));

                    String icon;
                    String color;
                    String desc;
                    switch (entry.type)
                    {
                        case "joined":
                            icon = "+";
                            color = "#4CAF50";
                            desc = entry.username + " joined";
                            break;
                        case "left":
                            icon = "-";
                            color = "#FF6B6B";
                            desc = entry.username + " left";
                            break;
                        case "changed_role":
                            icon = "\u2191";
                            color = "#5B9BD5";
                            String fromRole = formatRole(entry.previousRole);
                            String toRole = formatRole(entry.role);
                            desc = entry.username + " " + fromRole + " \u2192 " + toRole;
                            break;
                        default:
                            icon = "\u2022";
                            color = "#888888";
                            desc = entry.username + " " + entry.type;
                    }

                    JLabel label = new JLabel("<html><span style='color:" + color + "'>" + icon
                        + "</span> " + desc + "</html>");
                    label.setFont(READABLE_FONT_SMALL);
                    label.setForeground(new Color(190, 190, 190));
                    row.add(label, BorderLayout.WEST);

                    // Time ago
                    String timeAgo = formatTimeAgo(entry.createdAt);
                    JLabel timeLabel = new JLabel(timeAgo);
                    timeLabel.setFont(READABLE_FONT_SMALL);
                    timeLabel.setForeground(new Color(100, 100, 100));
                    row.add(timeLabel, BorderLayout.EAST);

                    activityPanel.add(row);
                }
            }

            activityPanel.revalidate();
            activityPanel.repaint();
        });
    }

    private String formatRole(String role)
    {
        if (role == null || role.isEmpty()) return "member";
        return role.replace("_", " ");
    }

    private String formatTimeAgo(String isoDate)
    {
        try
        {
            java.time.Instant then = java.time.Instant.parse(isoDate);
            long seconds = java.time.Duration.between(then, java.time.Instant.now()).getSeconds();
            if (seconds < 60) return seconds + "s";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h";
            long days = hours / 24;
            return days + "d";
        }
        catch (Exception e)
        {
            return "";
        }
    }

    // Accent palette
    private static final Color ACCENT_GOLD = new Color(212, 175, 55);
    private static final Color ACCENT_GOLD_DIM = new Color(160, 130, 40);
    private static final Color ACCENT_GOLD_BRIGHT = new Color(245, 215, 110);

    // ══════════════════════════════════════════
    // WOM XP Tab
    // ══════════════════════════════════════════

    private JComponent buildWomTab()
    {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.setBorder(new EmptyBorder(6, 6, 6, 6));

        // Title row with refresh button
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel title = new JLabel("Clan XP Leaderboard");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(76, 175, 80));
        titleRow.add(title, BorderLayout.WEST);

        JButton refreshBtn = new JButton("\u21BB");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(12f));
        refreshBtn.setMargin(new Insets(0, 4, 0, 4));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setToolTipText("Refresh XP data");
        refreshBtn.addActionListener(e -> triggerWomFetch());
        titleRow.add(refreshBtn, BorderLayout.EAST);
        wrapper.add(titleRow);
        wrapper.add(Box.createVerticalStrut(6));

        // Row 1: Mode + Skill (2 columns)
        JPanel row1 = new JPanel(new GridLayout(1, 2, 4, 0));
        row1.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        womModeCombo.setFont(READABLE_FONT_SMALL);
        womMetricCombo.addItem("Overall");
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL) continue;
            womMetricCombo.addItem(skill.getName());
        }
        womMetricCombo.setFont(READABLE_FONT_SMALL);
        womMetricCombo.setRenderer(new SkillComboRenderer());

        row1.add(womModeCombo);
        row1.add(womMetricCombo);
        wrapper.add(row1);
        wrapper.add(Box.createVerticalStrut(4));

        // Row 2: Period (full width)
        womPeriodCombo.setFont(READABLE_FONT_SMALL);
        womPeriodCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        womPeriodCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        // Only trigger fetch on explicit refresh click, not on every combo change
        wrapper.add(womPeriodCombo);
        wrapper.add(Box.createVerticalStrut(8));

        // Leaderboard content
        womLeaderboardPanel.setLayout(new BoxLayout(womLeaderboardPanel, BoxLayout.Y_AXIS));
        womLeaderboardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        womLeaderboardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel placeholder = new JLabel("Click refresh to load data");
        placeholder.setFont(READABLE_FONT_ITALIC);
        placeholder.setForeground(Color.GRAY);
        womLeaderboardPanel.add(placeholder);
        wrapper.add(womLeaderboardPanel);

        // Wrap in scroll pane
        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void triggerWomFetch()
    {
        if (onFetchWomData == null) return;

        // Show loading indicator
        SwingUtilities.invokeLater(() -> {
            womLeaderboardPanel.removeAll();
            JLabel loading = new JLabel("Loading...");
            loading.setFont(READABLE_FONT_ITALIC);
            loading.setForeground(Color.GRAY);
            womLeaderboardPanel.add(loading);
            womLeaderboardPanel.revalidate();
            womLeaderboardPanel.repaint();
        });

        String metric = ((String) womMetricCombo.getSelectedItem()).toLowerCase();
        String period = ((String) womPeriodCombo.getSelectedItem()).toLowerCase();
        onFetchWomData.accept(metric, period);
    }

    public void setOnFetchWomData(java.util.function.BiConsumer<String, String> callback)
    {
        this.onFetchWomData = callback;
    }

    public void updateWomLeaderboard(List<WomService.WomEntry> entries, boolean isGained)
    {
        SwingUtilities.invokeLater(() ->
        {
            womLeaderboardPanel.removeAll();

            if (entries == null || entries.isEmpty())
            {
                JLabel empty = new JLabel("No data available");
                empty.setFont(READABLE_FONT_ITALIC);
                empty.setForeground(Color.GRAY);
                womLeaderboardPanel.add(empty);
                womLeaderboardPanel.revalidate();
                womLeaderboardPanel.repaint();
                return;
            }

            for (WomService.WomEntry entry : entries)
            {
                womLeaderboardPanel.add(createWomRow(entry, isGained));
                womLeaderboardPanel.add(Box.createVerticalStrut(1));
            }

            womLeaderboardPanel.revalidate();
            womLeaderboardPanel.repaint();
        });
    }

    private JPanel createWomRow(WomService.WomEntry entry, boolean isGained)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setBorder(new EmptyBorder(2, 4, 2, 4));

        // Alternating row color
        Color bg = (entry.rank % 2 == 0) ? new Color(35, 35, 35) : new Color(45, 45, 45);
        row.setBackground(bg);

        // Left: rank number + role icon + name
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        leftPanel.setOpaque(false);

        Color rankColor;
        if (entry.rank == 1) rankColor = new Color(255, 215, 0);
        else if (entry.rank == 2) rankColor = new Color(192, 192, 192);
        else if (entry.rank == 3) rankColor = new Color(205, 127, 50);
        else rankColor = new Color(170, 170, 170);

        JLabel rankLabel = new JLabel("#" + entry.rank);
        rankLabel.setFont(READABLE_FONT_SMALL);
        rankLabel.setForeground(rankColor);
        leftPanel.add(rankLabel);

        // Clan rank icon from WOM
        if (entry.roleIcon != null)
        {
            JLabel iconLabel = new JLabel(entry.roleIcon);
            leftPanel.add(iconLabel);
        }

        JLabel nameLabel = new JLabel(entry.username);
        nameLabel.setFont(READABLE_FONT);
        nameLabel.setForeground(Color.WHITE);
        leftPanel.add(nameLabel);

        row.add(leftPanel, BorderLayout.WEST);

        // Right: XP value
        String xpText;
        if (isGained)
        {
            xpText = "+" + formatXp(entry.gained) + " xp";
        }
        else
        {
            xpText = formatXp(entry.experience) + " xp";
            if (entry.level > 0) xpText = "Lvl " + entry.level + " | " + xpText;
        }

        JLabel xpLabel = new JLabel(xpText);
        xpLabel.setFont(READABLE_FONT_SMALL);
        xpLabel.setForeground(isGained ? new Color(76, 175, 80) : new Color(170, 170, 170));
        row.add(xpLabel, BorderLayout.EAST);

        return row;
    }

    private String formatXp(long xp)
    {
        if (xp >= 1_000_000_000) return String.format("%.1fB", xp / 1_000_000_000.0);
        if (xp >= 1_000_000) return String.format("%.1fM", xp / 1_000_000.0);
        if (xp >= 1_000) return String.format("%.1fK", xp / 1_000.0);
        return String.valueOf(xp);
    }

    /** Custom renderer for skill combo box — shows skill icon + name. */
    private static class SkillComboRenderer extends DefaultListCellRenderer
    {
        private final Map<String, ImageIcon> iconCache = new HashMap<>();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus)
        {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String skillName = (String) value;
            label.setFont(READABLE_FONT_SMALL);

            ImageIcon icon = iconCache.get(skillName);
            if (icon == null)
            {
                try
                {
                    String fileName = "/skill_icons_small/" + skillName.toLowerCase().replace(" ", "_") + ".png";
                    // Try RuneLite's skill icon resources
                    BufferedImage img = ImageUtil.loadImageResource(Skill.class, fileName);
                    if (img != null)
                    {
                        icon = new ImageIcon(ImageUtil.resizeImage(img, 14, 14));
                    }
                }
                catch (Exception e)
                {
                    // No icon found, that's fine
                }
                if (icon != null)
                {
                    iconCache.put(skillName, icon);
                }
            }

            if (icon != null)
            {
                label.setIcon(icon);
                label.setIconTextGap(4);
            }

            return label;
        }
    }

    // ══════════════════════════════════════════
    // Hiscores Tab
    // ══════════════════════════════════════════

    // Map group codes to display names and accent colors
    private static final Map<String, Color> DISPLAY_GROUP_COLORS = new LinkedHashMap<>();
    static
    {
        DISPLAY_GROUP_COLORS.put("Raids", new Color(88, 196, 221));
        DISPLAY_GROUP_COLORS.put("GWD", new Color(200, 80, 80));
        DISPLAY_GROUP_COLORS.put("DT2", new Color(80, 120, 200));
        DISPLAY_GROUP_COLORS.put("Wave", new Color(255, 100, 0));
        DISPLAY_GROUP_COLORS.put("Gauntlet", new Color(0, 180, 120));
        DISPLAY_GROUP_COLORS.put("Nightmare", new Color(130, 50, 180));
        DISPLAY_GROUP_COLORS.put("Nex", new Color(100, 80, 160));
        DISPLAY_GROUP_COLORS.put("New Bosses", new Color(220, 180, 50));
        DISPLAY_GROUP_COLORS.put("Slayer", new Color(160, 50, 50));
        DISPLAY_GROUP_COLORS.put("Other", new Color(150, 150, 150));
        DISPLAY_GROUP_COLORS.put("Wilderness", new Color(180, 30, 30));
        DISPLAY_GROUP_COLORS.put("Challenges", new Color(60, 140, 40));
        DISPLAY_GROUP_COLORS.put("Sepulchre", new Color(190, 120, 50));
    }

    /**
     * Set the callback for fetching hiscore times.
     * The consumer receives the BossCategory and a target JPanel to populate with results.
     */
    public void setOnFetchTimes(java.util.function.BiConsumer<BossCategory, JPanel> callback)
    {
        this.onFetchTimes = callback;
    }

    public void setOnClearHiscoreCache(Runnable callback)
    {
        this.onClearHiscoreCache = callback;
    }

    /**
     * Populate a times panel with fetched HiscoreEntry data.
     * Called by the plugin after fetching times for a category.
     */
    public void populateTimesPanel(JPanel timesPanel, List<HiscoreEntry> entries, Color accentColor)
    {
        SwingUtilities.invokeLater(() ->
        {
            timesPanel.removeAll();

            if (entries == null || entries.isEmpty())
            {
                JLabel none = new JLabel("No times recorded");
                none.setFont(none.getFont().deriveFont(Font.ITALIC, 11f));
                none.setForeground(new Color(80, 80, 80));
                none.setBorder(new EmptyBorder(6, 36, 6, 10));
                timesPanel.add(none);
            }
            else
            {
                for (HiscoreEntry entry : entries)
                {
                    timesPanel.add(createTimeEntry(entry, accentColor));
                }
            }

            timesPanel.revalidate();
            timesPanel.repaint();
        });
    }

    private JPanel createTimeEntry(HiscoreEntry entry, Color accentColor)
    {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(new Color(18, 18, 18));
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        String rsns = entry.getRsns() != null ? entry.getRsns().trim() : "";
        String date = entry.getDate() != null ? entry.getDate().trim() : "";
        boolean isSolo = !rsns.contains(",");

        // Rank color
        Color rankColor;
        String rankText;
        switch (entry.getRank())
        {
            case 1: rankText = "#1"; rankColor = new Color(255, 215, 0); break;
            case 2: rankText = "#2"; rankColor = new Color(192, 192, 192); break;
            case 3: rankText = "#3"; rankColor = new Color(205, 127, 50); break;
            default: rankText = "#" + entry.getRank(); rankColor = new Color(120, 120, 120); break;
        }

        if (isSolo)
        {
            // ── Solo layout: rank + time on top, RSN + date below, no dropdown ──
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(new Color(18, 18, 18));
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 30, 30)),
                new EmptyBorder(4, 20, 4, 6)
            ));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Top line: rank + time
            JPanel topLine = new JPanel(new BorderLayout());
            topLine.setBackground(new Color(18, 18, 18));
            topLine.setAlignmentX(Component.LEFT_ALIGNMENT);
            topLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

            JLabel rankLabel = new JLabel(rankText);
            rankLabel.setFont(rankLabel.getFont().deriveFont(Font.BOLD, 10f));
            rankLabel.setForeground(rankColor);
            rankLabel.setPreferredSize(new Dimension(20, 16));
            topLine.add(rankLabel, BorderLayout.WEST);

            JLabel timeLabel = new JLabel(entry.getFormattedTime());
            timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 11f));
            timeLabel.setForeground(Color.WHITE);
            timeLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
            topLine.add(timeLabel, BorderLayout.CENTER);

            card.add(topLine);

            // Bottom line: RSN + date
            JPanel bottomLine = new JPanel(new BorderLayout());
            bottomLine.setBackground(new Color(18, 18, 18));
            bottomLine.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottomLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
            bottomLine.setBorder(new EmptyBorder(1, 24, 0, 0));

            JLabel rsnLabel = new JLabel(rsns);
            rsnLabel.setFont(READABLE_FONT);
            rsnLabel.setForeground(new Color(200, 200, 200));
            bottomLine.add(rsnLabel, BorderLayout.WEST);

            if (!date.isEmpty())
            {
                JLabel dateLabel = new JLabel(date);
                dateLabel.setFont(READABLE_FONT_ITALIC);
                dateLabel.setForeground(new Color(110, 110, 110));
                bottomLine.add(dateLabel, BorderLayout.EAST);
            }

            card.add(bottomLine);
            container.add(card);
        }
        else
        {
            // ── Group layout: clickable time row with expandable player list ──

            // Detail panel (hidden until clicked)
            JPanel detailPanel = new JPanel();
            detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
            detailPanel.setBackground(new Color(15, 15, 15));
            detailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailPanel.setVisible(false);
            detailPanel.setBorder(new EmptyBorder(3, 28, 5, 6));

            for (String rsn : rsns.split(","))
            {
                JLabel rsnLabel = new JLabel(rsn.trim());
                rsnLabel.setFont(READABLE_FONT);
                rsnLabel.setForeground(new Color(200, 200, 200));
                rsnLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                detailPanel.add(rsnLabel);
            }
            if (!date.isEmpty())
            {
                JLabel dateLabel = new JLabel(date);
                dateLabel.setFont(READABLE_FONT_ITALIC);
                dateLabel.setForeground(new Color(110, 110, 110));
                dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                dateLabel.setBorder(new EmptyBorder(3, 0, 0, 0));
                detailPanel.add(dateLabel);
            }

            // Time row
            JPanel timeRow = new JPanel(new BorderLayout());
            timeRow.setBackground(new Color(18, 18, 18));
            timeRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 30, 30)),
                new EmptyBorder(4, 20, 4, 6)
            ));
            timeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            timeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            timeRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel rankLabel = new JLabel(rankText);
            rankLabel.setFont(rankLabel.getFont().deriveFont(Font.BOLD, 10f));
            rankLabel.setForeground(rankColor);
            rankLabel.setPreferredSize(new Dimension(20, 16));
            timeRow.add(rankLabel, BorderLayout.WEST);

            JLabel timeLabel = new JLabel(entry.getFormattedTime());
            timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 11f));
            timeLabel.setForeground(Color.WHITE);
            timeLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
            timeRow.add(timeLabel, BorderLayout.CENTER);

            // RSN preview on right
            String preview = rsns.length() > 12 ? rsns.substring(0, 11) + "\u2026" : rsns;
            JLabel previewLabel = new JLabel(preview);
            previewLabel.setFont(READABLE_FONT_SMALL);
            previewLabel.setForeground(new Color(120, 120, 120));
            timeRow.add(previewLabel, BorderLayout.EAST);

            timeRow.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    detailPanel.setVisible(!detailPanel.isVisible());
                    container.revalidate();
                    container.repaint();
                }
                @Override
                public void mouseEntered(MouseEvent e) { timeRow.setBackground(new Color(28, 28, 28)); }
                @Override
                public void mouseExited(MouseEvent e) { timeRow.setBackground(new Color(18, 18, 18)); }
            });

            container.add(timeRow);
            container.add(detailPanel);
        }

        return container;
    }

    private JPanel buildHiscoresTab()
    {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.setBorder(new EmptyBorder(6, 4, 6, 4));

        // Title row with refresh button
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel hiscoreTitle = new JLabel("Clan Hiscores");
        hiscoreTitle.setFont(hiscoreTitle.getFont().deriveFont(Font.BOLD, 13f));
        hiscoreTitle.setForeground(new Color(100, 149, 237));
        titleRow.add(hiscoreTitle, BorderLayout.WEST);

        JLabel refreshBtn = new JLabel("\u21BB");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(14f));
        refreshBtn.setForeground(new Color(100, 100, 100));
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.setToolTipText("Clear cache & refresh");
        refreshBtn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (onClearHiscoreCache != null)
                {
                    onClearHiscoreCache.run();
                }
                // Re-fetch for current selection
                fetchTimesForCurrentSelection();
                refreshBtn.setForeground(new Color(100, 200, 100));
                javax.swing.Timer timer = new javax.swing.Timer(1500, evt -> refreshBtn.setForeground(new Color(100, 100, 100)));
                timer.setRepeats(false);
                timer.start();
            }
            @Override
            public void mouseEntered(MouseEvent e) { refreshBtn.setForeground(new Color(150, 150, 150)); }
            @Override
            public void mouseExited(MouseEvent e) { refreshBtn.setForeground(new Color(100, 100, 100)); }
        });
        titleRow.add(refreshBtn, BorderLayout.EAST);

        wrapper.add(titleRow);
        wrapper.add(Box.createVerticalStrut(2));

        JLabel hiscoreDesc = new JLabel("<html>PB times auto-submit when you get a new personal best.</html>");
        hiscoreDesc.setFont(hiscoreDesc.getFont().deriveFont(10f));
        hiscoreDesc.setForeground(new Color(130, 130, 130));
        hiscoreDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(hiscoreDesc);
        wrapper.add(Box.createVerticalStrut(8));

        // ── Search field ──
        hiscoreSearchField.setBackground(new Color(30, 30, 30));
        hiscoreSearchField.setForeground(Color.WHITE);
        hiscoreSearchField.setCaretColor(Color.WHITE);
        hiscoreSearchField.setFont(READABLE_FONT);
        hiscoreSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscoreSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        hiscoreSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60)),
            new EmptyBorder(2, 6, 2, 6)
        ));
        hiscoreSearchField.setToolTipText("Search bosses...");

        // Placeholder text
        hiscoreSearchField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (hiscoreSearchField.getText().equals("Search bosses..."))
                {
                    hiscoreSearchField.setText("");
                    hiscoreSearchField.setForeground(Color.WHITE);
                }
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e)
            {
                if (hiscoreSearchField.getText().isEmpty())
                {
                    hiscoreSearchField.setText("Search bosses...");
                    hiscoreSearchField.setForeground(new Color(100, 100, 100));
                }
            }
        });
        hiscoreSearchField.setText("Search bosses...");
        hiscoreSearchField.setForeground(new Color(100, 100, 100));

        hiscoreSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
        });

        wrapper.add(hiscoreSearchField);
        wrapper.add(Box.createVerticalStrut(6));

        // ── Boss Group dropdown ──
        hiscoreGroupLabel.setFont(READABLE_FONT);
        hiscoreGroupLabel.setForeground(new Color(180, 180, 180));
        hiscoreGroupLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(hiscoreGroupLabel);
        wrapper.add(Box.createVerticalStrut(2));

        hiscoreGroupCombo.setBackground(new Color(30, 30, 30));
        hiscoreGroupCombo.setForeground(Color.WHITE);
        hiscoreGroupCombo.setFont(READABLE_FONT);
        hiscoreGroupCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscoreGroupCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        wrapper.add(hiscoreGroupCombo);
        wrapper.add(Box.createVerticalStrut(6));

        // ── Boss dropdown ──
        hiscoreBossLabel.setFont(READABLE_FONT);
        hiscoreBossLabel.setForeground(new Color(180, 180, 180));
        hiscoreBossLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(hiscoreBossLabel);
        wrapper.add(Box.createVerticalStrut(2));

        hiscoreBossCombo.setBackground(new Color(30, 30, 30));
        hiscoreBossCombo.setForeground(Color.WHITE);
        hiscoreBossCombo.setFont(READABLE_FONT);
        hiscoreBossCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscoreBossCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        wrapper.add(hiscoreBossCombo);
        wrapper.add(Box.createVerticalStrut(6));

        // ── Size dropdown (hidden if boss has only one size) ──
        hiscoreSizeLabel.setFont(READABLE_FONT);
        hiscoreSizeLabel.setForeground(new Color(180, 180, 180));
        hiscoreSizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(hiscoreSizeLabel);
        wrapper.add(Box.createVerticalStrut(2));

        hiscoreSizeCombo.setBackground(new Color(30, 30, 30));
        hiscoreSizeCombo.setForeground(Color.WHITE);
        hiscoreSizeCombo.setFont(READABLE_FONT);
        hiscoreSizeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscoreSizeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        wrapper.add(hiscoreSizeCombo);
        wrapper.add(Box.createVerticalStrut(8));

        // Populate group dropdown — "Recent" first, then all groups
        hiscoreGroupCombo.addItem("Recent");
        for (String groupName : BossCategory.getDisplayGroupNames())
        {
            hiscoreGroupCombo.addItem(groupName);
        }

        // Wire cascading dropdown logic
        hiscoreGroupCombo.addActionListener(e ->
        {
            if (hiscoreDropdownsUpdating) return;
            hiscoreDropdownsUpdating = true;
            populateBossCombo();
            hiscoreDropdownsUpdating = false;
        });

        hiscoreBossCombo.addActionListener(e ->
        {
            if (hiscoreDropdownsUpdating) return;
            hiscoreDropdownsUpdating = true;
            populateSizeCombo();
            hiscoreDropdownsUpdating = false;
        });

        hiscoreSizeCombo.addActionListener(e ->
        {
            if (hiscoreDropdownsUpdating) return;
            fetchTimesForCurrentSelection();
        });

        // ── Times display panel ──
        hiscoreTimesPanel.setLayout(new BoxLayout(hiscoreTimesPanel, BoxLayout.Y_AXIS));
        hiscoreTimesPanel.setBackground(new Color(18, 18, 18));
        hiscoreTimesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscoreTimesPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50, 50, 50)),
            new EmptyBorder(4, 4, 4, 4)
        ));

        JLabel selectPrompt = new JLabel("Select a boss to view times");
        selectPrompt.setFont(READABLE_FONT_ITALIC);
        selectPrompt.setForeground(new Color(80, 80, 80));
        selectPrompt.setBorder(new EmptyBorder(12, 10, 12, 10));
        hiscoreTimesPanel.add(selectPrompt);

        wrapper.add(hiscoreTimesPanel);

        // Initialize the boss combo for the first group
        hiscoreDropdownsUpdating = true;
        populateBossCombo();
        hiscoreDropdownsUpdating = false;

        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel outerWrapper = new JPanel(new BorderLayout());
        outerWrapper.add(scrollPane, BorderLayout.CENTER);
        return outerWrapper;
    }

    private void populateBossCombo()
    {
        hiscoreBossCombo.removeAllItems();
        String selectedGroup = (String) hiscoreGroupCombo.getSelectedItem();
        if (selectedGroup == null) return;

        if ("Recent".equals(selectedGroup))
        {
            // Hide boss/size dropdowns — show combined recent view directly
            hiscoreBossLabel.setVisible(false);
            hiscoreBossCombo.setVisible(false);
            hiscoreSizeLabel.setVisible(false);
            hiscoreSizeCombo.setVisible(false);
            showRecentPbsOverview();
            return;
        }
        // Show boss combo for non-Recent selections
        hiscoreBossLabel.setVisible(!"Search Results".equals(selectedGroup));
        hiscoreBossCombo.setVisible(true);

        if ("Search Results".equals(selectedGroup))
        {
            // Populated by onSearchChanged
        }
        else
        {
            for (String bossName : BossCategory.getBossNamesInGroup(selectedGroup))
            {
                hiscoreBossCombo.addItem(bossName);
            }
        }

        populateSizeCombo();
    }

    private void populateSizeCombo()
    {
        hiscoreSizeCombo.removeAllItems();
        String selectedGroup = (String) hiscoreGroupCombo.getSelectedItem();
        String selectedBoss = (String) hiscoreBossCombo.getSelectedItem();
        if (selectedGroup == null || selectedBoss == null) return;

        List<BossCategory> cats;
        if ("Recent".equals(selectedGroup) || "Search Results".equals(selectedGroup))
        {
            cats = BossCategory.getCategoriesForBossAnyGroup(selectedBoss);
        }
        else
        {
            cats = BossCategory.getCategoriesForBoss(selectedGroup, selectedBoss);
        }

        if (cats.size() <= 1)
        {
            // Single size — hide the size dropdown
            hiscoreSizeLabel.setVisible(false);
            hiscoreSizeCombo.setVisible(false);
            // Auto-fetch times for the single category
            if (!cats.isEmpty())
            {
                hiscoreSizeCombo.addItem(cats.get(0).getSizeLabel());
                fetchTimesForCurrentSelection();
            }
        }
        else
        {
            hiscoreSizeLabel.setVisible(true);
            hiscoreSizeCombo.setVisible(true);
            for (BossCategory cat : cats)
            {
                hiscoreSizeCombo.addItem(cat.getSizeLabel());
            }
            // Auto-fetch for the first size
            fetchTimesForCurrentSelection();
        }
    }

    private BossCategory getSelectedBossCategory()
    {
        String selectedGroup = (String) hiscoreGroupCombo.getSelectedItem();
        String selectedBoss = (String) hiscoreBossCombo.getSelectedItem();
        String selectedSize = (String) hiscoreSizeCombo.getSelectedItem();
        if (selectedGroup == null || selectedBoss == null) return null;

        List<BossCategory> cats;
        if ("Recent".equals(selectedGroup) || "Search Results".equals(selectedGroup))
        {
            cats = BossCategory.getCategoriesForBossAnyGroup(selectedBoss);
        }
        else
        {
            cats = BossCategory.getCategoriesForBoss(selectedGroup, selectedBoss);
        }
        if (cats.isEmpty()) return null;

        if (cats.size() == 1) return cats.get(0);

        // Match by size label
        if (selectedSize != null)
        {
            for (BossCategory cat : cats)
            {
                if (cat.getSizeLabel().equals(selectedSize))
                {
                    return cat;
                }
            }
        }

        return cats.get(0);
    }

    private void onSearchChanged()
    {
        String text = hiscoreSearchField.getText().trim();
        if (text.equals("Search bosses...") || text.isEmpty())
        {
            // Revert to normal mode — select "Recent" if available
            hiscoreDropdownsUpdating = true;
            hiscoreGroupCombo.setSelectedItem("Recent");
            hiscoreGroupLabel.setVisible(true);
            hiscoreGroupCombo.setVisible(true);
            populateBossCombo();
            hiscoreDropdownsUpdating = false;
            return;
        }

        // Search mode — hide group dropdown, show filtered results in boss combo
        hiscoreDropdownsUpdating = true;
        hiscoreGroupLabel.setVisible(false);
        hiscoreGroupCombo.setVisible(false);

        // Temporarily set group to "Search Results" for size/category lookups
        if (hiscoreGroupCombo.getItemCount() == 0 || !"Search Results".equals(hiscoreGroupCombo.getItemAt(0)))
        {
            hiscoreGroupCombo.insertItemAt("Search Results", 0);
        }
        hiscoreGroupCombo.setSelectedItem("Search Results");

        hiscoreBossCombo.removeAllItems();
        List<String> matches = BossCategory.searchBossNames(text);
        for (String name : matches)
        {
            hiscoreBossCombo.addItem(name);
        }
        if (matches.isEmpty())
        {
            hiscoreBossCombo.addItem("No matches");
        }

        hiscoreDropdownsUpdating = false;
        populateSizeCombo();
    }

    /**
     * Set the category keys and entries that have recent PB data (from hiscore cache).
     * Called by the plugin after loading hiscore data.
     */
    public Set<String> getRecentCategoryKeys()
    {
        return recentCategoryKeys;
    }

    public void setRecentCategories(Set<String> categoryKeys, Map<String, List<HiscoreEntry>> entries)
    {
        this.recentCategoryKeys = categoryKeys;
        this.recentCategoryEntries = entries != null ? entries : new java.util.LinkedHashMap<>();
        // If currently showing "Recent", refresh the view
        SwingUtilities.invokeLater(() ->
        {
            if ("Recent".equals(hiscoreGroupCombo.getSelectedItem()))
            {
                hiscoreDropdownsUpdating = true;
                populateBossCombo();
                hiscoreDropdownsUpdating = false;
            }
        });
    }

    private void showRecentPbsOverview()
    {
        hiscoreTimesPanel.removeAll();

        if (recentCategoryEntries.isEmpty())
        {
            JLabel noData = new JLabel("No recent PBs");
            noData.setFont(READABLE_FONT_ITALIC);
            noData.setForeground(new Color(100, 100, 100));
            noData.setBorder(new EmptyBorder(12, 10, 12, 10));
            hiscoreTimesPanel.add(noData);
        }
        else
        {
            int count = 0;
            for (Map.Entry<String, List<HiscoreEntry>> entry : recentCategoryEntries.entrySet())
            {
                if (count >= 10) break; // limit to 10 recent categories
                List<HiscoreEntry> times = entry.getValue();
                if (times == null || times.isEmpty()) continue;

                BossCategory cat = BossCategory.fromKey(entry.getKey());
                String bossName = cat != null ? cat.getDisplayName() : entry.getKey();
                String sizeLabel = cat != null && cat.getMaxPlayers() > 1 ? " (" + cat.getSizeLabel() + ")" : "";
                HiscoreEntry best = times.get(0);

                JPanel row = new JPanel(new BorderLayout(4, 0));
                row.setBackground(count % 2 == 0 ? new Color(18, 18, 18) : new Color(28, 28, 28));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                row.setBorder(new EmptyBorder(4, 8, 4, 8));

                // Left: boss name
                JPanel leftPanel = new JPanel();
                leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
                leftPanel.setBackground(row.getBackground());

                JLabel bossLabel = new JLabel(bossName + sizeLabel);
                bossLabel.setFont(READABLE_FONT.deriveFont(Font.BOLD));
                bossLabel.setForeground(new Color(100, 149, 237));
                leftPanel.add(bossLabel);

                JLabel detailLabel = new JLabel(best.getFormattedTime() + " — " + best.getRsns());
                detailLabel.setFont(READABLE_FONT_SMALL);
                detailLabel.setForeground(new Color(170, 170, 170));
                leftPanel.add(detailLabel);

                row.add(leftPanel, BorderLayout.CENTER);

                // Right: date
                String date = best.getDate() != null ? best.getDate().trim() : "";
                if (!date.isEmpty())
                {
                    JLabel dateLabel = new JLabel(date);
                    dateLabel.setFont(READABLE_FONT_SMALL);
                    dateLabel.setForeground(new Color(100, 100, 100));
                    row.add(dateLabel, BorderLayout.EAST);
                }

                hiscoreTimesPanel.add(row);
                count++;
            }
        }

        hiscoreTimesPanel.revalidate();
        hiscoreTimesPanel.repaint();
    }

    private void fetchTimesForCurrentSelection()
    {
        BossCategory cat = getSelectedBossCategory();
        if (cat == null || onFetchTimes == null) return;

        // Get accent color for the current display group
        String displayGroup = cat.getDisplayGroup();
        Color accentColor = DISPLAY_GROUP_COLORS.getOrDefault(displayGroup, new Color(100, 149, 237));

        // Show loading
        hiscoreTimesPanel.removeAll();
        JLabel loading = new JLabel("Loading times...");
        loading.setFont(READABLE_FONT_ITALIC);
        loading.setForeground(new Color(80, 80, 80));
        loading.setBorder(new EmptyBorder(12, 10, 12, 10));
        hiscoreTimesPanel.add(loading);
        hiscoreTimesPanel.revalidate();
        hiscoreTimesPanel.repaint();

        onFetchTimes.accept(cat, hiscoreTimesPanel);
    }

    // ══════════════════════════════════════════
    // Drops Tab (Clan Drop Leaderboard + Recent)
    // ══════════════════════════════════════════

    private JComponent buildDropsTab()
    {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.setBorder(new EmptyBorder(6, 4, 6, 4));

        // Title row with refresh button
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel title = new JLabel("Clan Drop Log");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(255, 180, 100));
        titleRow.add(title, BorderLayout.WEST);

        JButton refreshBtn = new JButton("\u21BB");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(12f));
        refreshBtn.setMargin(new Insets(0, 4, 0, 4));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setToolTipText("Refresh drop data");
        refreshBtn.addActionListener(e -> {
            if (onRefreshDropsTab != null) onRefreshDropsTab.run();
        });
        titleRow.add(refreshBtn, BorderLayout.EAST);
        wrapper.add(titleRow);
        wrapper.add(Box.createVerticalStrut(8));

        // ── Monthly Leaderboard ──
        JLabel lbTitle = new JLabel("TOP PLAYERS — THIS MONTH");
        lbTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbTitle.setForeground(new Color(100, 180, 255));
        lbTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(lbTitle);
        wrapper.add(Box.createVerticalStrut(4));

        dropsLeaderboardPanel.setLayout(new BoxLayout(dropsLeaderboardPanel, BoxLayout.Y_AXIS));
        dropsLeaderboardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropsLeaderboardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbPlaceholder = new JLabel("Loading...");
        lbPlaceholder.setFont(READABLE_FONT_ITALIC);
        lbPlaceholder.setForeground(Color.GRAY);
        dropsLeaderboardPanel.add(lbPlaceholder);
        wrapper.add(dropsLeaderboardPanel);

        wrapper.add(Box.createVerticalStrut(10));

        // ── Separator ──
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60, 60, 60));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        wrapper.add(sep);
        wrapper.add(Box.createVerticalStrut(6));

        // ── Recent Drops ──
        JLabel recentTitle = new JLabel("RECENT DROPS");
        recentTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        recentTitle.setForeground(new Color(100, 180, 255));
        recentTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(recentTitle);
        wrapper.add(Box.createVerticalStrut(4));

        dropsRecentPanel.setLayout(new BoxLayout(dropsRecentPanel, BoxLayout.Y_AXIS));
        dropsRecentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropsRecentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel recentPlaceholder = new JLabel("Loading...");
        recentPlaceholder.setFont(READABLE_FONT_ITALIC);
        recentPlaceholder.setForeground(Color.GRAY);
        dropsRecentPanel.add(recentPlaceholder);
        wrapper.add(dropsRecentPanel);

        wrapper.add(Box.createVerticalStrut(10));

        // ── Separator ──
        JSeparator sep2 = new JSeparator();
        sep2.setForeground(new Color(60, 60, 60));
        sep2.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        wrapper.add(sep2);
        wrapper.add(Box.createVerticalStrut(6));

        // ── Whitelist Browser ──
        JLabel wlTitle = new JLabel("ALL TRACKABLE DROPS");
        wlTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        wlTitle.setForeground(new Color(100, 180, 255));
        wlTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(wlTitle);
        wrapper.add(Box.createVerticalStrut(4));

        // Search field
        whitelistSearchField.setBackground(new Color(25, 25, 25));
        whitelistSearchField.setForeground(new Color(100, 100, 100));
        whitelistSearchField.setCaretColor(Color.WHITE);
        whitelistSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50, 50, 50)),
            new EmptyBorder(4, 6, 4, 6)
        ));
        whitelistSearchField.setFont(whitelistSearchField.getFont().deriveFont(11f));
        whitelistSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        whitelistSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        whitelistSearchField.setText("Search items...");
        whitelistSearchField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (whitelistSearchField.getText().equals("Search items..."))
                {
                    whitelistSearchField.setText("");
                    whitelistSearchField.setForeground(Color.WHITE);
                }
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e)
            {
                if (whitelistSearchField.getText().isEmpty())
                {
                    whitelistSearchField.setText("Search items...");
                    whitelistSearchField.setForeground(new Color(100, 100, 100));
                }
            }
        });
        whitelistSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            private void filter()
            {
                String text = whitelistSearchField.getText();
                if (text.equals("Search items...")) text = "";
                renderWhitelistBrowser(text.toLowerCase().trim());
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });
        wrapper.add(whitelistSearchField);
        wrapper.add(Box.createVerticalStrut(4));

        // Filter/sort row
        JPanel filterRow = new JPanel(new BorderLayout(4, 0));
        filterRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        whitelistCategoryFilter.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        whitelistCategoryFilter.setMaximumSize(new Dimension(120, 22));
        whitelistCategoryFilter.addActionListener(e -> {
            String text = whitelistSearchField.getText();
            if (text.equals("Search items...")) text = "";
            renderWhitelistBrowser(text.toLowerCase().trim());
        });
        filterRow.add(whitelistCategoryFilter, BorderLayout.CENTER);

        whitelistSortCombo.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        whitelistSortCombo.setMaximumSize(new Dimension(110, 22));
        whitelistSortCombo.addActionListener(e -> {
            String text = whitelistSearchField.getText();
            if (text.equals("Search items...")) text = "";
            renderWhitelistBrowser(text.toLowerCase().trim());
        });
        filterRow.add(whitelistSortCombo, BorderLayout.EAST);
        wrapper.add(filterRow);
        wrapper.add(Box.createVerticalStrut(4));

        // Browser results panel
        whitelistBrowserPanel.setLayout(new BoxLayout(whitelistBrowserPanel, BoxLayout.Y_AXIS));
        whitelistBrowserPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        whitelistBrowserPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel wlPlaceholder = new JLabel("Loading whitelist...");
        wlPlaceholder.setFont(READABLE_FONT_ITALIC);
        wlPlaceholder.setForeground(Color.GRAY);
        whitelistBrowserPanel.add(wlPlaceholder);
        wrapper.add(whitelistBrowserPanel);

        wrapper.add(Box.createVerticalStrut(10));

        // ── Player Detail (shown when clicking a player name) ──
        playerDetailPanel.setLayout(new BoxLayout(playerDetailPanel, BoxLayout.Y_AXIS));
        playerDetailPanel.setBackground(new Color(25, 25, 30));
        playerDetailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        playerDetailPanel.setVisible(false);
        playerDetailPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 80)),
            new EmptyBorder(6, 6, 6, 6)
        ));
        wrapper.add(playerDetailPanel);

        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        return scroll;
    }

    public void updateDropsLeaderboard(List<Map<String, Object>> players, String localPlayerName)
    {
        SwingUtilities.invokeLater(() ->
        {
            dropsLeaderboardPanel.removeAll();

            if (players == null || players.isEmpty())
            {
                JLabel empty = new JLabel("No drop data yet");
                empty.setFont(READABLE_FONT_ITALIC);
                empty.setForeground(Color.GRAY);
                dropsLeaderboardPanel.add(empty);
            }
            else
            {
                // Header row
                JPanel headerRow = new JPanel(new BorderLayout(4, 0));
                headerRow.setBackground(new Color(30, 30, 50));
                headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                headerRow.setBorder(new EmptyBorder(2, 4, 2, 4));

                JLabel hdrName = new JLabel("#  Player");
                hdrName.setFont(new Font("Segoe UI", Font.BOLD, 9));
                hdrName.setForeground(new Color(150, 150, 200));
                headerRow.add(hdrName, BorderLayout.WEST);

                JLabel hdrRight = new JLabel("Pts  Drops  GP");
                hdrRight.setFont(new Font("Segoe UI", Font.BOLD, 9));
                hdrRight.setForeground(new Color(150, 150, 200));
                headerRow.add(hdrRight, BorderLayout.EAST);
                dropsLeaderboardPanel.add(headerRow);

                int limit = Math.min(players.size(), 15);
                for (int i = 0; i < limit; i++)
                {
                    Map<String, Object> p = players.get(i);
                    int rank = ((Number) p.getOrDefault("rank", i + 1)).intValue();
                    String rsn = (String) p.getOrDefault("rsn", "");
                    int points = ((Number) p.getOrDefault("points", 0)).intValue();
                    int drops = ((Number) p.getOrDefault("drops", 0)).intValue();
                    long value = ((Number) p.getOrDefault("value", 0L)).longValue();

                    boolean isMe = localPlayerName != null
                        && rsn.equalsIgnoreCase(localPlayerName);

                    JPanel row = new JPanel(new BorderLayout(4, 0));
                    row.setBackground(i % 2 == 0
                        ? ColorScheme.DARK_GRAY_COLOR
                        : new Color(35, 35, 35));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                    row.setBorder(new EmptyBorder(1, 4, 1, 4));

                    String prefix = "#" + rank + " ";

                    JLabel nameLabel = new JLabel(prefix + truncate(rsn, 13));
                    nameLabel.setFont(new Font("Segoe UI",
                        isMe ? Font.BOLD : Font.PLAIN, 10));
                    nameLabel.setForeground(isMe
                        ? new Color(76, 175, 80)
                        : new Color(220, 220, 220));
                    nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    final String playerRsn = rsn;
                    nameLabel.addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            if (onFetchPlayerDrops != null) onFetchPlayerDrops.accept(playerRsn);
                        }
                        @Override
                        public void mouseEntered(MouseEvent e)
                        {
                            nameLabel.setForeground(new Color(100, 200, 255));
                        }
                        @Override
                        public void mouseExited(MouseEvent e)
                        {
                            nameLabel.setForeground(playerRsn.equalsIgnoreCase(
                                localPlayerName != null ? localPlayerName : "")
                                ? new Color(76, 175, 80) : new Color(220, 220, 220));
                        }
                    });
                    row.add(nameLabel, BorderLayout.WEST);

                    String gpStr = value >= 1_000_000
                        ? String.format("%.1fM", value / 1_000_000.0)
                        : value >= 1_000
                            ? String.format("%.0fK", value / 1_000.0)
                            : String.valueOf(value);

                    JLabel statsLabel = new JLabel(
                        String.format("%d   %d   %s", points, drops, gpStr));
                    statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                    statsLabel.setForeground(isMe
                        ? new Color(76, 175, 80)
                        : new Color(150, 150, 150));
                    row.add(statsLabel, BorderLayout.EAST);

                    dropsLeaderboardPanel.add(row);
                }
            }

            dropsLeaderboardPanel.revalidate();
            dropsLeaderboardPanel.repaint();
        });
    }

    public void updateRecentDrops(List<Map<String, Object>> drops)
    {
        SwingUtilities.invokeLater(() ->
        {
            dropsRecentPanel.removeAll();

            if (drops == null || drops.isEmpty())
            {
                JLabel empty = new JLabel("No drops recorded yet");
                empty.setFont(READABLE_FONT_ITALIC);
                empty.setForeground(Color.GRAY);
                dropsRecentPanel.add(empty);
            }
            else
            {
                int limit = Math.min(drops.size(), 20);
                for (int i = 0; i < limit; i++)
                {
                    Map<String, Object> drop = drops.get(i);
                    String item = (String) drop.getOrDefault("item", "");
                    String player = (String) drop.getOrDefault("player", "");
                    long value = ((Number) drop.getOrDefault("value", 0L)).longValue();
                    int points = ((Number) drop.getOrDefault("points", 0)).intValue();
                    String monster = (String) drop.getOrDefault("monster", "");

                    JPanel row = new JPanel(new BorderLayout(2, 0));
                    row.setBackground(i % 2 == 0
                        ? ColorScheme.DARK_GRAY_COLOR
                        : new Color(35, 35, 35));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                    row.setBorder(new EmptyBorder(2, 4, 2, 4));

                    // Left: item name + player
                    JPanel leftPanel = new JPanel();
                    leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
                    leftPanel.setBackground(row.getBackground());

                    JLabel itemLabel = new JLabel(truncate(item, 22));
                    itemLabel.setFont(READABLE_FONT);
                    itemLabel.setForeground(points >= 25
                        ? new Color(255, 100, 100)
                        : points >= 15
                            ? new Color(255, 180, 100)
                            : new Color(220, 220, 220));

                    JLabel detailLabel = new JLabel(
                        truncate(player, 12) + " — " + truncate(monster, 12));
                    detailLabel.setFont(READABLE_FONT_SMALL);
                    detailLabel.setForeground(new Color(120, 120, 120));

                    leftPanel.add(itemLabel);
                    leftPanel.add(detailLabel);
                    row.add(leftPanel, BorderLayout.CENTER);

                    // Right: points + value
                    JPanel rightPanel = new JPanel();
                    rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
                    rightPanel.setBackground(row.getBackground());

                    if (points > 0)
                    {
                        JLabel ptLabel = new JLabel("+" + points + " pts");
                        ptLabel.setFont(READABLE_FONT_SMALL);
                        ptLabel.setForeground(new Color(76, 175, 80));
                        ptLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
                        rightPanel.add(ptLabel);
                    }

                    if (value > 0)
                    {
                        String gpStr = value >= 1_000_000
                            ? String.format("%.1fM", value / 1_000_000.0)
                            : value >= 1_000
                                ? String.format("%.0fK", value / 1_000.0)
                                : value + " gp";
                        JLabel gpLabel = new JLabel(gpStr);
                        gpLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                        gpLabel.setForeground(new Color(255, 215, 0));
                        gpLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
                        rightPanel.add(gpLabel);
                    }

                    row.add(rightPanel, BorderLayout.EAST);
                    dropsRecentPanel.add(row);
                }
            }

            dropsRecentPanel.revalidate();
            dropsRecentPanel.repaint();
        });
    }

    public void setOnRefreshDropsTab(Runnable cb)
    {
        this.onRefreshDropsTab = cb;
    }

    public void setOnFetchPlayerDrops(java.util.function.Consumer<String> cb)
    {
        this.onFetchPlayerDrops = cb;
    }

    public void showPlayerDrops(String rsn, List<Map<String, Object>> drops)
    {
        SwingUtilities.invokeLater(() ->
        {
            playerDetailPanel.removeAll();
            playerDetailPanel.setVisible(true);

            // Header with close button
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(new Color(30, 30, 55));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            header.setBorder(new EmptyBorder(3, 6, 3, 6));

            JLabel title = new JLabel(rsn + " — " + (drops != null ? drops.size() : 0) + " drops");
            title.setFont(new Font("Segoe UI", Font.BOLD, 11));
            title.setForeground(new Color(100, 180, 255));
            header.add(title, BorderLayout.WEST);

            JLabel closeBtn = new JLabel("\u2715");
            closeBtn.setFont(closeBtn.getFont().deriveFont(11f));
            closeBtn.setForeground(new Color(150, 100, 100));
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    playerDetailPanel.setVisible(false);
                    playerDetailPanel.removeAll();
                    playerDetailPanel.revalidate();
                }
                @Override
                public void mouseEntered(MouseEvent e) { closeBtn.setForeground(new Color(220, 80, 80)); }
                @Override
                public void mouseExited(MouseEvent e) { closeBtn.setForeground(new Color(150, 100, 100)); }
            });
            header.add(closeBtn, BorderLayout.EAST);
            playerDetailPanel.add(header);

            if (drops == null || drops.isEmpty())
            {
                JLabel empty = new JLabel("No drops recorded");
                empty.setFont(READABLE_FONT_ITALIC);
                empty.setForeground(Color.GRAY);
                empty.setBorder(new EmptyBorder(6, 6, 6, 6));
                playerDetailPanel.add(empty);
            }
            else
            {
                // Summary stats
                int totalPts = 0;
                long totalGp = 0;
                for (Map<String, Object> d : drops)
                {
                    totalPts += ((Number) d.getOrDefault("points", 0)).intValue();
                    totalGp += ((Number) d.getOrDefault("value", 0L)).longValue();
                }
                String gpStr = totalGp >= 1_000_000
                    ? String.format("%.1fM gp", totalGp / 1_000_000.0)
                    : String.format("%,d gp", totalGp);
                JLabel summary = new JLabel(
                    String.format("%d pts | %s | %d drops", totalPts, gpStr, drops.size()));
                summary.setFont(READABLE_FONT_SMALL);
                summary.setForeground(new Color(180, 180, 180));
                summary.setBorder(new EmptyBorder(4, 6, 4, 6));
                summary.setAlignmentX(Component.LEFT_ALIGNMENT);
                playerDetailPanel.add(summary);

                // Drop rows (most recent first — API returns newest first)
                int limit = Math.min(drops.size(), 30);
                for (int i = 0; i < limit; i++)
                {
                    Map<String, Object> drop = drops.get(i);
                    String item = (String) drop.getOrDefault("item", "");
                    String monster = (String) drop.getOrDefault("monster", "");
                    int kc = ((Number) drop.getOrDefault("kc", 0)).intValue();
                    int pts = ((Number) drop.getOrDefault("points", 0)).intValue();
                    long val = ((Number) drop.getOrDefault("value", 0L)).longValue();
                    String ts = (String) drop.getOrDefault("timestamp", "");
                    // Extract just the date portion
                    String date = ts.length() >= 10 ? ts.substring(0, 10) : ts;

                    JPanel row = new JPanel(new BorderLayout(2, 0));
                    row.setBackground(i % 2 == 0
                        ? new Color(25, 25, 30)
                        : new Color(30, 30, 38));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                    row.setBorder(new EmptyBorder(2, 6, 2, 4));

                    JPanel left = new JPanel();
                    left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
                    left.setBackground(row.getBackground());

                    JLabel itemLbl = new JLabel(truncate(item, 22));
                    itemLbl.setFont(READABLE_FONT);
                    itemLbl.setForeground(pts >= 25
                        ? new Color(255, 100, 100)
                        : pts >= 15
                            ? new Color(255, 180, 100)
                            : new Color(200, 200, 200));
                    left.add(itemLbl);

                    String detail = monster;
                    if (kc > 0) detail += " (" + kc + " kc)";
                    if (!date.isEmpty()) detail += " — " + date;
                    JLabel detLbl = new JLabel(truncate(detail, 28));
                    detLbl.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                    detLbl.setForeground(new Color(110, 110, 110));
                    left.add(detLbl);

                    row.add(left, BorderLayout.CENTER);

                    if (pts > 0)
                    {
                        JLabel ptLbl = new JLabel("+" + pts);
                        ptLbl.setFont(READABLE_FONT_SMALL);
                        ptLbl.setForeground(new Color(76, 175, 80));
                        row.add(ptLbl, BorderLayout.EAST);
                    }

                    playerDetailPanel.add(row);
                }

                if (drops.size() > limit)
                {
                    JLabel more = new JLabel("...and " + (drops.size() - limit) + " more");
                    more.setFont(READABLE_FONT_ITALIC);
                    more.setForeground(new Color(100, 100, 100));
                    more.setBorder(new EmptyBorder(4, 6, 4, 6));
                    playerDetailPanel.add(more);
                }
            }

            playerDetailPanel.revalidate();
            playerDetailPanel.repaint();

            // Scroll to the player detail panel
            playerDetailPanel.scrollRectToVisible(playerDetailPanel.getBounds());
        });
    }

    // ══════════════════════════════════════════
    // Whitelist Browser
    // ══════════════════════════════════════════

    public void setOnRefreshWhitelist(Runnable cb)
    {
        this.onRefreshWhitelist = cb;
    }

    public void updateClogSyncCount(int count)
    {
        SwingUtilities.invokeLater(() -> {
            clogCountLabel.setText("Items detected: " + count);
            clogCountLabel.setVisible(true);
        });
    }

    public void setClogSyncStatus(String status)
    {
        SwingUtilities.invokeLater(() -> clogStatusLabel.setText(status));
    }

    /** Update the Collection Log status box, e.g. "861 / 1703" */
    public void setStatusClog(int obtained, int total)
    {
        SwingUtilities.invokeLater(() -> {
            if (total > 0)
            {
                statusClogLabel.setText(obtained + " / " + total);
            }
            else if (obtained > 0)
            {
                statusClogLabel.setText(String.valueOf(obtained));
            }
            else
            {
                statusClogLabel.setText("--");
            }
        });
    }

    /** Update the Total XP status box */
    public void setStatusXp(long totalXp)
    {
        SwingUtilities.invokeLater(() -> {
            if (totalXp <= 0)
            {
                statusXpLabel.setText("--");
            }
            else if (totalXp >= 1_000_000_000)
            {
                statusXpLabel.setText(String.format("%.1fB", totalXp / 1_000_000_000.0));
            }
            else if (totalXp >= 1_000_000)
            {
                statusXpLabel.setText(String.format("%.1fM", totalXp / 1_000_000.0));
            }
            else if (totalXp >= 1_000)
            {
                statusXpLabel.setText(String.format("%.1fK", totalXp / 1_000.0));
            }
            else
            {
                statusXpLabel.setText(String.valueOf(totalXp));
            }
        });
    }

    /** Update the Hiscores status box — checkmark if any PBs exist */
    public void setStatusHiscores(boolean hasAny)
    {
        SwingUtilities.invokeLater(() -> {
            if (hasAny)
            {
                statusHiscoresLabel.setText("Yes");
                statusHiscoresLabel.setForeground(new Color(76, 175, 80));
            }
            else
            {
                statusHiscoresLabel.setText("No");
                statusHiscoresLabel.setForeground(new Color(180, 80, 80));
            }
        });
    }

    public void updateClanWhitelist(List<Map<String, String>> items)
    {
        SwingUtilities.invokeLater(() ->
        {
            cachedClanWhitelist = items != null ? items : Collections.emptyList();

            // Build category filter options
            Set<String> categories = new TreeSet<>();
            categories.add("All Categories");
            for (Map<String, String> item : cachedClanWhitelist)
            {
                String cat = item.getOrDefault("category", "");
                if (!cat.isEmpty()) categories.add(cat);
            }

            whitelistCategoryFilter.removeAllItems();
            for (String cat : categories) whitelistCategoryFilter.addItem(cat);

            renderWhitelistBrowser("");
        });
    }

    private void renderWhitelistBrowser(String searchFilter)
    {
        whitelistBrowserPanel.removeAll();

        // Hide items until user starts searching
        if (searchFilter.isEmpty())
        {
            JLabel prompt = new JLabel("Search for an item to see points");
            prompt.setFont(READABLE_FONT_ITALIC);
            prompt.setForeground(new Color(100, 100, 100));
            prompt.setBorder(new EmptyBorder(8, 4, 8, 4));
            whitelistBrowserPanel.add(prompt);
            whitelistBrowserPanel.revalidate();
            whitelistBrowserPanel.repaint();
            return;
        }

        String selectedCategory = (String) whitelistCategoryFilter.getSelectedItem();
        if (selectedCategory == null) selectedCategory = "All Categories";
        String sortMode = (String) whitelistSortCombo.getSelectedItem();
        if (sortMode == null) sortMode = "Points (High)";

        // Filter
        List<Map<String, String>> filtered = new ArrayList<>();
        for (Map<String, String> item : cachedClanWhitelist)
        {
            String name = item.getOrDefault("item", "").toLowerCase();
            String source = item.getOrDefault("source", "").toLowerCase();
            String category = item.getOrDefault("category", "");

            if (!name.contains(searchFilter) && !source.contains(searchFilter))
            {
                continue;
            }
            if (!"All Categories".equals(selectedCategory) && !category.equals(selectedCategory))
            {
                continue;
            }
            filtered.add(item);
        }

        // Sort
        final String sort = sortMode;
        filtered.sort((a, b) -> {
            if ("Points (High)".equals(sort))
            {
                return Integer.compare(
                    Integer.parseInt(b.getOrDefault("points", "0")),
                    Integer.parseInt(a.getOrDefault("points", "0")));
            }
            else if ("Points (Low)".equals(sort))
            {
                return Integer.compare(
                    Integer.parseInt(a.getOrDefault("points", "0")),
                    Integer.parseInt(b.getOrDefault("points", "0")));
            }
            else if ("Name (A-Z)".equals(sort))
            {
                return a.getOrDefault("item", "").compareToIgnoreCase(b.getOrDefault("item", ""));
            }
            else // Source (A-Z)
            {
                return a.getOrDefault("source", "").compareToIgnoreCase(b.getOrDefault("source", ""));
            }
        });

        // Count label
        JLabel countLabel = new JLabel(filtered.size() + " items");
        countLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        countLabel.setForeground(new Color(120, 120, 120));
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        countLabel.setBorder(new EmptyBorder(0, 2, 4, 0));
        whitelistBrowserPanel.add(countLabel);

        if (filtered.isEmpty())
        {
            JLabel noResults = new JLabel("No matches");
            noResults.setFont(READABLE_FONT_ITALIC);
            noResults.setForeground(new Color(100, 100, 100));
            noResults.setBorder(new EmptyBorder(8, 4, 8, 4));
            whitelistBrowserPanel.add(noResults);
        }
        else
        {
            // Show up to 50 items at a time to keep UI snappy
            int limit = Math.min(filtered.size(), 50);
            for (int i = 0; i < limit; i++)
            {
                Map<String, String> item = filtered.get(i);
                whitelistBrowserPanel.add(createWhitelistRow(item, i));
            }
            if (filtered.size() > limit)
            {
                JLabel more = new JLabel("..." + (filtered.size() - limit) + " more — narrow your search");
                more.setFont(READABLE_FONT_ITALIC);
                more.setForeground(new Color(100, 100, 100));
                more.setBorder(new EmptyBorder(4, 4, 4, 4));
                whitelistBrowserPanel.add(more);
            }
        }

        whitelistBrowserPanel.revalidate();
        whitelistBrowserPanel.repaint();
    }

    private JPanel createWhitelistRow(Map<String, String> item, int index)
    {
        String name = item.getOrDefault("item", "");
        int points = Integer.parseInt(item.getOrDefault("points", "0"));

        JPanel row = new JPanel(new BorderLayout(2, 0));
        row.setBackground(index % 2 == 0
            ? ColorScheme.DARK_GRAY_COLOR
            : new Color(35, 35, 35));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setBorder(new EmptyBorder(2, 4, 2, 4));

        // Left: item name
        JLabel nameLabel = new JLabel(truncate(name, 28));
        nameLabel.setFont(READABLE_FONT);
        // Color by point tier
        Color nameColor;
        if (points >= 2000) nameColor = new Color(198, 40, 40);       // deep red
        else if (points >= 1000) nameColor = new Color(255, 100, 100); // red
        else if (points >= 500) nameColor = new Color(255, 180, 100);  // orange
        else if (points >= 200) nameColor = new Color(76, 175, 80);    // green
        else if (points >= 100) nameColor = new Color(0, 150, 136);    // teal
        else nameColor = new Color(180, 180, 180);                      // gray
        nameLabel.setForeground(nameColor);
        row.add(nameLabel, BorderLayout.CENTER);

        // Right: points
        JLabel ptsLabel = new JLabel(String.format("%,d pts", points));
        ptsLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        ptsLabel.setForeground(nameColor);
        row.add(ptsLabel, BorderLayout.EAST);

        return row;
    }

    // ══════════════════════════════════════════
    // Admin Tab
    // ══════════════════════════════════════════

    public void showAdminTab(AdminPanel adminPanel)
    {
        SwingUtilities.invokeLater(() ->
        {
            JScrollPane adminScroll = new JScrollPane(adminPanel);
            adminScroll.setBorder(null);
            adminScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            tabbedPane.addTab("Admin", adminScroll);
            revalidate();
            repaint();
        });
    }

    // ══════════════════════════════════════════
    // Bingo Tab
    // ══════════════════════════════════════════

    public void showBingoTab(BingoPanel panel)
    {
        if (bingoTabVisible) return;
        this.bingoPanel = panel;
        SwingUtilities.invokeLater(() ->
        {
            // Insert before Admin tab if it exists, otherwise at end
            int adminIdx = tabbedPane.indexOfTab("Admin");
            if (adminIdx >= 0)
            {
                tabbedPane.insertTab("Bingo", null, panel, null, adminIdx);
            }
            else
            {
                tabbedPane.addTab("Bingo", panel);
            }
            bingoTabVisible = true;
            revalidate();
            repaint();
        });
    }

    public void hideBingoTab()
    {
        if (!bingoTabVisible) return;
        SwingUtilities.invokeLater(() ->
        {
            int idx = tabbedPane.indexOfTab("Bingo");
            if (idx >= 0) tabbedPane.removeTabAt(idx);
            bingoTabVisible = false;
            bingoPanel = null;
            revalidate();
            repaint();
        });
    }

    public BingoPanel getBingoPanel()
    {
        return bingoPanel;
    }

    // ══════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════

    public void setStatus(String text)
    {
        SwingUtilities.invokeLater(() -> homeStatusLabel.setText(text));
    }

    public void setConnected(boolean isConnected)
    {
        SwingUtilities.invokeLater(() ->
        {
            connected = isConnected;
            CardLayout cl = (CardLayout) cardContainer.getLayout();
            cl.show(cardContainer, isConnected ? CARD_CONNECTED : CARD_NOT_CONNECTED);
        });
    }

    public void setClanName(String name)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (homeTitleLabel != null) homeTitleLabel.setText(name);
            if (notConnectedTitleLabel != null) notConnectedTitleLabel.setText(name);
        });
    }

    public void setOnRefresh(Runnable onRefresh)
    {
        this.onRefresh = onRefresh;
    }

    // ══════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════

    private static String truncate(String text, int maxLen)
    {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "\u2026";
    }
}
