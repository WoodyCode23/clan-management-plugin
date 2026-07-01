package com.droplogger;

import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.client.game.SpriteManager;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

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
    private static final Font READABLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font READABLE_FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font READABLE_FONT_ITALIC = new Font("Segoe UI", Font.ITALIC, 11);

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

    private boolean connected = false;

    private Runnable onRefresh;

    // ── Hiscores tab components ──
    private final JPanel hiscoresContentPanel = new JPanel();
    private final JPanel hiscoreTimesPanel = new JPanel();
    private final JTextField hiscoreSearchField = new JTextField();
    private final JTextField hiscorePlayerSearchField = new JTextField();
    private static final String PLAYER_FILTER_PLACEHOLDER = "Filter by player...";
    // The current boss leaderboard, cached so the player filter can re-render it client-side.
    // Null means we're not showing a boss leaderboard (e.g. the recent-PBs overview), so the
    // player filter is a no-op and won't clobber that view.
    private java.util.List<HiscoreEntry> lastTimesEntries = null;
    private Color lastTimesAccent = null;

    // ── Members tab (browse other players' collection logs) ──
    private final JTextField memberSearchField = new JTextField();
    private final JPanel membersContent = new ScrollableColumn(); // tracks viewport width so cards fit beside the scrollbar
    private final JTextField clogTabSearchField = new JTextField();
    private final JPanel clogCatListPanel = new JPanel();
    private java.util.List<PlatformApiService.RosterMember> currentMembers = new java.util.ArrayList<>();
    private Runnable onLoadRoster;
    private java.util.function.Consumer<String> onSelectMember;
    private boolean platformAdmin = false;
    private java.util.function.Consumer<Object[]> onSetRankOverride; // {rsn, mode, assignedRank}
    private java.util.function.Consumer<String> onClearRankOverride; // rsn
    private ItemManager itemManager; // for local item-icon rendering in the clog grid
    private PlatformApiService.PlayerClog currentClog = null;
    private String currentClogRsn = null;
    private String currentClogTab = null;
    private PlatformApiService.PlayerProfile currentProfile = null;
    private java.util.function.Consumer<String> onLoadClog;
    // Combat Achievements drill-down (tier → boss → task done/missing), mirrors the clog flow.
    private PlatformApiService.PlayerCa currentCa = null;
    private String currentCaTier = null;
    private final JTextField caTaskSearchField = new JTextField();
    private final JPanel caTaskListPanel = new JPanel();
    private java.util.function.Consumer<String> onLoadCa;
    private Runnable onLoadRanks;
    private final JPanel ranksContent = new ScrollableColumn();
    private boolean ranksActive = false;
    private java.util.function.Consumer<Object[]> onRequestRank; // {rankName, eligible(Boolean), missing(List)}
    private SpriteManager spriteManager; // in-game clan-rank icon sprites

    // In-game clan-rank icon sprite per rank (the SpriteID.ClanRankIcons set — the same icons shown
    // in the Solus CC). The clan's icon-per-rank choice isn't exposed by the RuneLite API, so these
    // are mapped by hand; adjust each sprite id to the icon picked in the clan rank-title settings
    // (the icon tooltip shows the current sprite id to make matching easy).
    private static final java.util.Map<String, Integer> RANK_ICON_SPRITE = new java.util.HashMap<>();
    static
    {
        // Mapped by the user against the live in-game clan-rank icons (cache sprite IDs).
        RANK_ICON_SPRITE.put("adamant_sword", 3150);
        RANK_ICON_SPRITE.put("rune_sword", 3143);
        RANK_ICON_SPRITE.put("dragon_sword", 3144);
        RANK_ICON_SPRITE.put("tzkal", 3246);
        RANK_ICON_SPRITE.put("adamant_pick", 3150);
        RANK_ICON_SPRITE.put("rune_pick", 3151);
        RANK_ICON_SPRITE.put("dragon_pick", 3152);
        RANK_ICON_SPRITE.put("maxed", 3247);
        RANK_ICON_SPRITE.put("adamant_comp", 3323);
        RANK_ICON_SPRITE.put("rune_comp", 3324);
        RANK_ICON_SPRITE.put("dragon_comp", 3320);
        RANK_ICON_SPRITE.put("beast", 3073);
        RANK_ICON_SPRITE.put("gm_beast", 3206);
        RANK_ICON_SPRITE.put("xp_beast", 3071);
        RANK_ICON_SPRITE.put("log_beast", 3217);
        RANK_ICON_SPRITE.put("heart_2", 3109);
        RANK_ICON_SPRITE.put("heart_3", 3110);
        RANK_ICON_SPRITE.put("heart_4", 3111);
    }

    /** A BoxLayout column that fills the scroll viewport's WIDTH (so children fit beside the scrollbar). */
    private static class ScrollableColumn extends JPanel implements javax.swing.Scrollable
    {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 100; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private final JComboBox<String> hiscoreGroupCombo = new JComboBox<>();
    private final JComboBox<String> hiscoreBossCombo = new JComboBox<>();
    private final JComboBox<String> hiscoreSizeCombo = new JComboBox<>();
    private final JComboBox<String> hiscoreModeCombo = new JComboBox<>();
    private java.util.function.Consumer<String> onPbModeChange;
    private final JComboBox<String> activityFilterCombo = new JComboBox<>();
    private java.util.function.Consumer<String> onActivityFilterChange;
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
    private final JComboBox<String> womPeriodCombo = new JComboBox<>(new String[]{"Day", "Week", "Month", "Year", "All-Time"});
    private final JComboBox<String> womModeCombo = new JComboBox<>(new String[]{"XP Gained"});
    private java.util.function.BiConsumer<String, String> onFetchWomData;

    // Status indicators (bottom of home tab)
    private JLabel statusClogLabel;
    private JLabel statusXpLabel;
    private JLabel statusHiscoresLabel;
    private Runnable onRefreshStatus;

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

        // "Not connected" onboarding screen — everything centered.
        notConnectedPanel.setLayout(new BorderLayout());
        notConnectedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel msgBox = new JPanel();
        msgBox.setLayout(new BoxLayout(msgBox, BoxLayout.Y_AXIS));
        msgBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        msgBox.setBorder(new EmptyBorder(36, 16, 20, 16));

        notConnectedTitleLabel = new JLabel("Solus");
        notConnectedTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        notConnectedTitleLabel.setForeground(ACCENT_GOLD);
        notConnectedTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        msgBox.add(notConnectedTitleLabel);
        msgBox.add(Box.createVerticalStrut(6));

        JLabel introLabel = new JLabel("<html><div style='text-align:center;'>Connect with your personal API key.</div></html>");
        introLabel.setFont(READABLE_FONT);
        introLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        introLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        msgBox.add(introLabel);
        msgBox.add(Box.createVerticalStrut(14));

        JLabel stepsLabel = new JLabel("<html><div style='text-align:center; line-height:160%;'>"
            + "1. Join the Solus Discord<br>"
            + "2. Run <b>/getkey</b> in Discord<br>"
            + "3. Paste your key into the<br>plugin's <b>API Key</b> setting</div></html>");
        stepsLabel.setFont(READABLE_FONT_SMALL);
        stepsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        stepsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        msgBox.add(stepsLabel);
        msgBox.add(Box.createVerticalStrut(16));

        JButton discordBtn = new JButton("Join the Solus Discord");
        discordBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        discordBtn.setForeground(Color.WHITE);
        discordBtn.setBackground(new Color(88, 101, 242)); // Discord blurple
        discordBtn.setOpaque(true);
        discordBtn.setBorderPainted(false);
        discordBtn.setFocusPainted(false);
        discordBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        discordBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        discordBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Opens the user's browser via RuneLite's sanctioned helper — the plugin makes no request.
        discordBtn.addActionListener(e -> LinkBrowser.browse("https://discord.gg/solus"));
        msgBox.add(discordBtn);

        notConnectedPanel.add(msgBox, BorderLayout.NORTH);

        tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Home tab (always present, default)
        tabbedPane.addTab("Home", buildHomeTab());

        // Speed Times tab
        tabbedPane.addTab("Speed Times", buildHiscoresTab());

        // Drops tab (leaderboard + recent)
        tabbedPane.addTab("Drops", buildDropsTab());

        // XP tab (WOM leaderboards)
        tabbedPane.addTab("XP", buildWomTab());

        // Activity tab (clan joins, leaves, rank changes)
        tabbedPane.addTab("Activity", buildActivityTab());

        // Members tab (browse other players' collection logs)
        tabbedPane.addTab("Members", buildMembersTab());

        // Ranks tab (which clan ranks YOU qualify for)
        tabbedPane.addTab("Ranks", buildRanksTab());

        // Lazy-load roster on first Members open; (re)evaluate ranks whenever the Ranks tab opens.
        tabbedPane.addChangeListener(e ->
        {
            int idx = tabbedPane.getSelectedIndex();
            String title = idx >= 0 ? tabbedPane.getTitleAt(idx) : "";
            if ("Members".equals(title) && currentMembers.isEmpty() && onLoadRoster != null)
            {
                onLoadRoster.run();
            }
            ranksActive = "Ranks".equals(title);
            if (ranksActive && onLoadRanks != null)
            {
                onLoadRanks.run();
            }
        });

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
        homeTitleLabel = new JLabel("Solus");
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
        home.add(createNavCard("Speed Times", "PB times & clan speed leaderboards", new Color(100, 149, 237), "Speed Times"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("XP", "Clan XP leaderboards from Wise Old Man", new Color(76, 175, 80), "XP"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("Drops", "Clan drop log, leaderboard & whitelist", new Color(255, 180, 100), "Drops"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("Activity", "Live feed: joins, leaves, drops, PBs & clog", new Color(100, 180, 255), "Activity"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("Members", "Browse clan members' collection logs", new Color(186, 142, 255), "Members"));
        home.add(Box.createVerticalStrut(8));
        home.add(createNavCard("My Ranks", "Check which clan ranks you qualify for", ACCENT_GOLD, "Ranks"));
        home.add(Box.createVerticalStrut(20));

        // ── Active Event card ──
        eventCardPanel.setLayout(new BoxLayout(eventCardPanel, BoxLayout.Y_AXIS));
        eventCardPanel.setBackground(new Color(40, 40, 40));
        eventCardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60)),
            new EmptyBorder(10, 10, 10, 10)));
        eventCardPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
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
        // Full-width left-aligned title that lines up with the nav cards' left edge (CENTER
        // alignmentX + MAX_VALUE width so its box spans the panel, text drawn at the left).
        JLabel announcementsTitle = new JLabel("Announcements");
        announcementsTitle.setFont(announcementsTitle.getFont().deriveFont(Font.BOLD, 13f));
        announcementsTitle.setForeground(ACCENT_GOLD);
        announcementsTitle.setHorizontalAlignment(SwingConstants.LEFT);
        announcementsTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        announcementsTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        home.add(announcementsTitle);
        home.add(Box.createVerticalStrut(6));

        // Same alignment/width treatment as the event card so announcement cards line up
        // edge-to-edge with the nav buttons above (previously LEFT-aligned → visibly offset).
        announcementsPanel.setLayout(new BoxLayout(announcementsPanel, BoxLayout.Y_AXIS));
        announcementsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        announcementsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        announcementsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

        JLabel noAnnouncements = new JLabel("No announcements");
        noAnnouncements.setFont(noAnnouncements.getFont().deriveFont(Font.ITALIC, 11f));
        noAnnouncements.setForeground(new Color(100, 100, 100));
        noAnnouncements.setAlignmentX(Component.CENTER_ALIGNMENT);
        announcementsPanel.add(noAnnouncements);

        home.add(announcementsPanel);
        home.add(Box.createVerticalStrut(12));

        // ── Tracking Status (bottom) ──
        home.add(Box.createVerticalGlue());
        home.add(Box.createVerticalStrut(12));

        JPanel statusRow = new JPanel(new GridLayout(1, 3, 6, 0));
        statusRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statusRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        statusClogLabel = new JLabel("--", SwingConstants.CENTER);
        statusXpLabel = new JLabel("--", SwingConstants.CENTER);
        statusHiscoresLabel = new JLabel("--", SwingConstants.CENTER);

        statusRow.add(buildStatusBox("C-Log", statusClogLabel));
        statusRow.add(buildStatusBox("XP", statusXpLabel));
        statusRow.add(buildStatusBox("Speed", statusHiscoresLabel));
        statusRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusRow.setToolTipText("Click to refresh");
        statusRow.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                if (onRefreshStatus != null)
                {
                    statusClogLabel.setText("...");
                    statusXpLabel.setText("...");
                    statusHiscoresLabel.setText("...");
                    onRefreshStatus.run();
                }
            }
        });
        home.add(statusRow);

        home.add(Box.createVerticalStrut(4));

        // Status
        homeStatusLabel.setFont(homeStatusLabel.getFont().deriveFont(10f));
        homeStatusLabel.setForeground(new Color(120, 120, 120));
        homeStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        home.add(homeStatusLabel);

        return home;
    }

    // ══════════════════════════════════════════
    // Activity Tab
    // ══════════════════════════════════════════

    private JComponent buildActivityTab()
    {
        // ScrollableColumn (tracks viewport width) + a scroll pane, so the feed scrolls and never
        // overflows the panel — matching the Drops tab.
        ScrollableColumn tab = new ScrollableColumn();
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        tab.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tab.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Clan Activity");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(new Color(100, 180, 255));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        tab.add(title);
        tab.add(Box.createVerticalStrut(4));

        JLabel desc = new JLabel("Drops, PBs, collection log & CAs");
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 11f));
        desc.setForeground(new Color(140, 140, 140));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        tab.add(desc);
        tab.add(Box.createVerticalStrut(8));

        // Filter dropdown — narrows the feed to a single kind of event.
        activityFilterCombo.addItem("Everything");
        activityFilterCombo.addItem("Drops");
        activityFilterCombo.addItem("Personal Bests");
        activityFilterCombo.addItem("Collection Log");
        activityFilterCombo.addItem("Combat Achievements");
        activityFilterCombo.setBackground(new Color(30, 30, 30));
        activityFilterCombo.setForeground(Color.WHITE);
        activityFilterCombo.setFont(READABLE_FONT_SMALL);
        activityFilterCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        activityFilterCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        activityFilterCombo.addActionListener(e ->
        {
            if (onActivityFilterChange != null)
            {
                onActivityFilterChange.accept(activityTypeFilter((String) activityFilterCombo.getSelectedItem()));
            }
        });
        tab.add(activityFilterCombo);
        tab.add(Box.createVerticalStrut(10));

        activityPanel.setLayout(new BoxLayout(activityPanel, BoxLayout.Y_AXIS));
        activityPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        activityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel loading = new JLabel("Loading...");
        loading.setFont(loading.getFont().deriveFont(Font.ITALIC, 11f));
        loading.setForeground(new Color(100, 100, 100));
        activityPanel.add(loading);

        tab.add(activityPanel);

        JScrollPane scroll = new JScrollPane(tab,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
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
        titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        titleLbl.setForeground(new Color(120, 120, 120));
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(titleLbl);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        valueLabel.setForeground(new Color(200, 200, 200));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(valueLabel);

        return box;
    }

    // ══════════════════════════════════════════
    // Members Tab — browse other players' collection logs
    // ══════════════════════════════════════════

    private JPanel buildMembersTab()
    {
        JPanel tab = new JPanel(new BorderLayout(0, 6));
        tab.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tab.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Members");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(new Color(186, 142, 255));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);

        JLabel desc = new JLabel("Pick a member to view their collection log");
        desc.setFont(READABLE_FONT_SMALL);
        desc.setForeground(new Color(140, 140, 140));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        desc.setBorder(new EmptyBorder(2, 0, 8, 0));
        header.add(desc);

        memberSearchField.setBackground(new Color(30, 30, 30));
        memberSearchField.setForeground(Color.WHITE);
        memberSearchField.setCaretColor(Color.WHITE);
        memberSearchField.setFont(READABLE_FONT);
        memberSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        memberSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        memberSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50, 50, 50)),
            new EmptyBorder(2, 6, 2, 6)));
        memberSearchField.setToolTipText("Search members...");
        memberSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { renderMemberList(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { renderMemberList(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { renderMemberList(); }
        });
        header.add(memberSearchField);
        tab.add(header, BorderLayout.NORTH);

        membersContent.setLayout(new BoxLayout(membersContent, BoxLayout.Y_AXIS));
        membersContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JScrollPane scroll = new JScrollPane(membersContent,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        tab.add(scroll, BorderLayout.CENTER);

        JLabel loading = new JLabel("Open this tab to load members…");
        loading.setFont(READABLE_FONT_ITALIC);
        loading.setForeground(new Color(100, 100, 100));
        membersContent.add(loading);

        // One-time setup for the clog tab's boss/category search (shown inside showClogTab).
        clogCatListPanel.setLayout(new BoxLayout(clogCatListPanel, BoxLayout.Y_AXIS));
        clogCatListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        clogCatListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        clogTabSearchField.setBackground(new Color(30, 30, 30));
        clogTabSearchField.setForeground(Color.WHITE);
        clogTabSearchField.setCaretColor(Color.WHITE);
        clogTabSearchField.setFont(READABLE_FONT_SMALL);
        clogTabSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        clogTabSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        clogTabSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50, 50, 50)),
            new EmptyBorder(2, 6, 2, 6)));
        clogTabSearchField.setToolTipText("Search…");
        clogTabSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { renderClogCategories(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { renderClogCategories(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { renderClogCategories(); }
        });

        // One-time setup for the CA tier's task search (shown inside showCaTier).
        caTaskListPanel.setLayout(new BoxLayout(caTaskListPanel, BoxLayout.Y_AXIS));
        caTaskListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        caTaskListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        caTaskSearchField.setBackground(new Color(30, 30, 30));
        caTaskSearchField.setForeground(Color.WHITE);
        caTaskSearchField.setCaretColor(Color.WHITE);
        caTaskSearchField.setFont(READABLE_FONT_SMALL);
        caTaskSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        caTaskSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        caTaskSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50, 50, 50)),
            new EmptyBorder(2, 6, 2, 6)));
        caTaskSearchField.setToolTipText("Search tasks / bosses…");
        caTaskSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { renderCaTasks(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { renderCaTasks(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { renderCaTasks(); }
        });

        return tab;
    }

    /** Populate the Members tab from the roster. */
    public void setMemberList(java.util.List<PlatformApiService.RosterMember> members)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentMembers = members != null ? members : new java.util.ArrayList<>();
            renderMemberList();
        });
    }

    private void renderMemberList()
    {
        membersContent.removeAll();
        String q = memberSearchField.getText() == null ? "" : memberSearchField.getText().trim().toLowerCase();

        if (currentMembers.isEmpty())
        {
            JLabel none = new JLabel("No members loaded");
            none.setFont(READABLE_FONT_ITALIC);
            none.setForeground(new Color(100, 100, 100));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            membersContent.add(none);
        }
        else
        {
            int shown = 0;
            for (PlatformApiService.RosterMember m : currentMembers)
            {
                if (!q.isEmpty() && !m.rsn.toLowerCase().contains(q)) continue;
                if (shown >= 80)
                {
                    JLabel more = new JLabel("…refine your search to see more");
                    more.setFont(READABLE_FONT_ITALIC);
                    more.setForeground(new Color(100, 100, 100));
                    more.setAlignmentX(Component.LEFT_ALIGNMENT);
                    membersContent.add(more);
                    break;
                }
                membersContent.add(buildMemberRow(m));
                membersContent.add(Box.createVerticalStrut(2));
                shown++;
            }
            if (shown == 0)
            {
                JLabel none = new JLabel("No members match");
                none.setFont(READABLE_FONT_ITALIC);
                none.setForeground(new Color(100, 100, 100));
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                membersContent.add(none);
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    private JPanel buildMemberRow(PlatformApiService.RosterMember m)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(new Color(40, 40, 40));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60)),
            new EmptyBorder(7, 10, 7, 10)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel name = new JLabel(m.rsn);
        name.setFont(READABLE_FONT);
        name.setForeground(Color.WHITE);
        row.add(name, BorderLayout.WEST);

        if (m.rank != null && !m.rank.isEmpty())
        {
            JLabel rank = new JLabel(m.rank);
            rank.setFont(READABLE_FONT_SMALL);
            rank.setForeground(new Color(150, 150, 150));
            row.add(rank, BorderLayout.EAST);
        }

        row.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e)
            {
                if (onSelectMember != null) onSelectMember.accept(m.rsn);
            }
        });
        return row;
    }

    public void setItemManager(ItemManager im) { this.itemManager = im; }
    public void setSpriteManager(SpriteManager sm) { this.spriteManager = sm; }

    /** Admin reference: render every clan-rank icon sprite with its ID so the right ones can be mapped. */
    private void showRankIconReference()
    {
        SwingUtilities.invokeLater(() ->
        {
            ranksContent.removeAll();
            ranksContent.add(clogBackButton("← Ranks", () -> { if (onLoadRanks != null) onLoadRanks.run(); }));
            ranksContent.add(Box.createVerticalStrut(4));
            JLabel info = new JLabel("<html>These are the in-game clan-rank icons. Find the symbol for each "
                + "rank (sword, pickaxe, infernal cape, max cape, horseshoe…) and send me its <b>#id</b>.</html>");
            info.setFont(READABLE_FONT_SMALL);
            info.setForeground(new Color(170, 170, 170));
            info.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            info.setBorder(new EmptyBorder(0, 0, 6, 0));
            ranksContent.add(info);

            JPanel grid = new JPanel(new java.awt.GridLayout(0, 4, 4, 6));
            grid.setBackground(new Color(30, 30, 30));
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            int base = SpriteID.ClanRankIcons._0; // 3062
            for (int i = 0; i < 280; i++)
            {
                final int sprite = base + i;
                JPanel cell = new JPanel();
                cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
                cell.setBackground(new Color(40, 40, 40));
                cell.setBorder(new EmptyBorder(3, 3, 3, 3));
                final JLabel icon = new JLabel();
                icon.setAlignmentX(Component.CENTER_ALIGNMENT);
                icon.setPreferredSize(new Dimension(24, 22));
                if (spriteManager != null)
                {
                    spriteManager.getSpriteAsync(sprite, 0, img -> SwingUtilities.invokeLater(() ->
                    {
                        if (img != null) { icon.setIcon(new ImageIcon(img)); icon.revalidate(); icon.repaint(); }
                    }));
                }
                JLabel id = new JLabel("#" + sprite);
                id.setFont(READABLE_FONT_SMALL.deriveFont(9f));
                id.setForeground(new Color(150, 150, 150));
                id.setAlignmentX(Component.CENTER_ALIGNMENT);
                cell.add(icon);
                cell.add(id);
                grid.add(cell);
            }
            JPanel gridWrap = new JPanel(new BorderLayout());
            gridWrap.setBackground(new Color(30, 30, 30));
            gridWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            gridWrap.add(grid, BorderLayout.NORTH);
            ranksContent.add(gridWrap);
            ranksContent.revalidate();
            ranksContent.repaint();
        });
    }

    /** A clan-rank icon (in-game sprite) for the given rank id, async-loaded. Empty if unmapped. */
    private JLabel rankSpriteIcon(String rankId)
    {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(22, 20));
        Integer sprite = RANK_ICON_SPRITE.get(rankId);
        if (sprite != null)
        {
            label.setToolTipText("clan rank icon sprite #" + sprite);
            if (spriteManager != null)
            {
                spriteManager.getSpriteAsync(sprite, 0, img -> SwingUtilities.invokeLater(() ->
                {
                    if (img != null) { label.setIcon(new ImageIcon(img)); label.revalidate(); label.repaint(); }
                }));
            }
        }
        return label;
    }

    /** Entry point from the plugin: cache the clog and show the tab overview. */
    public void showPlayerClog(String rsn, PlatformApiService.PlayerClog clog)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentClogRsn = rsn;
            currentClog = clog;
            renderClogOverview();
        });
    }

    /** Entry point from the plugin: cache the profile and show the member's landing page. */
    public void showMemberProfile(String rsn, PlatformApiService.PlayerProfile profile)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentClogRsn = rsn;
            currentProfile = profile;
            currentClog = null; // the clog is fetched lazily when its section is opened
            renderMemberProfile();
        });
    }

    /** Member landing: name + section cards (Collection Log / Speed Times / Drops). */
    private void renderMemberProfile()
    {
        membersContent.removeAll();
        membersContent.add(clogBackButton("← Members", this::renderMemberList));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle(currentClogRsn, new Color(186, 142, 255), 16f));
        membersContent.add(Box.createVerticalStrut(8));

        if (currentProfile == null)
        {
            membersContent.add(clogNote("Could not load this player's profile."));
        }
        else
        {
            String clogSub = currentProfile.clogObtained
                + (currentProfile.clogTotal > 0 ? " / " + currentProfile.clogTotal : "") + " unique items";
            membersContent.add(buildSectionCard("Collection Log", clogSub, new Color(186, 142, 255),
                () -> { if (onLoadClog != null) onLoadClog.accept(currentClogRsn); }));
            membersContent.add(Box.createVerticalStrut(6));
            membersContent.add(buildSectionCard("Speed Times",
                currentProfile.pbs.size() + " personal bests", new Color(100, 149, 237), this::showMemberPbs));
            membersContent.add(Box.createVerticalStrut(6));
            membersContent.add(buildSectionCard("Recent Drops",
                currentProfile.drops.size() + " logged", new Color(255, 180, 100), this::showMemberDrops));
            membersContent.add(Box.createVerticalStrut(6));
            String caSub = currentProfile.caTotal > 0
                ? currentProfile.caCompleted + " / " + currentProfile.caTotal + " tasks"
                : "View combat achievements";
            membersContent.add(buildSectionCard("Combat Achievements", caSub, ACCENT_CA,
                () -> { if (onLoadCa != null) onLoadCa.accept(currentClogRsn); }));

            if (platformAdmin)
            {
                membersContent.add(Box.createVerticalStrut(10));
                membersContent.add(buildRankAdminSection(currentClogRsn));
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** Admin-only: put a member into Collection-Log mode, set their rank manually, or clear the override. */
    private JPanel buildRankAdminSection(String rsn)
    {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(new Color(40, 40, 40));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(220, 120, 120)),
            new EmptyBorder(8, 10, 8, 10)));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));

        JLabel h = new JLabel("Admin — rank override");
        h.setFont(READABLE_FONT.deriveFont(Font.BOLD));
        h.setForeground(new Color(225, 140, 140));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(h);
        JLabel sub = new JLabel("<html>Sticky — auto-checks stay off for this member until you clear it.</html>");
        sub.setFont(READABLE_FONT_SMALL);
        sub.setForeground(new Color(150, 150, 150));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(sub);
        box.add(Box.createVerticalStrut(5));

        JButton clogBtn = new JButton("Collection-Log mode");
        styleAdminBtn(clogBtn);
        clogBtn.addActionListener(e ->
        {
            if (onSetRankOverride != null) onSetRankOverride.accept(new Object[]{ rsn, "clog_only", null });
            flashAdmin(clogBtn, "Set ✓");
        });
        box.add(clogBtn);
        box.add(Box.createVerticalStrut(4));

        JPanel setRow = new JPanel(new BorderLayout(4, 0));
        setRow.setBackground(box.getBackground());
        setRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        setRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        JComboBox<String> rankCombo = new JComboBox<>();
        for (RankSystem.Rank r : RankSystem.RANKS) rankCombo.addItem(r.name);
        rankCombo.setFont(READABLE_FONT_SMALL);
        JButton setBtn = new JButton("Set rank");
        styleAdminBtn(setBtn);
        setBtn.setMaximumSize(new Dimension(90, 24));
        setBtn.addActionListener(e ->
        {
            if (onSetRankOverride != null) onSetRankOverride.accept(new Object[]{ rsn, "admin_set", (String) rankCombo.getSelectedItem() });
            flashAdmin(setBtn, "Set ✓");
        });
        setRow.add(rankCombo, BorderLayout.CENTER);
        setRow.add(setBtn, BorderLayout.EAST);
        box.add(setRow);
        box.add(Box.createVerticalStrut(4));

        JButton clearBtn = new JButton("Clear override (back to auto)");
        styleAdminBtn(clearBtn);
        clearBtn.addActionListener(e ->
        {
            if (onClearRankOverride != null) onClearRankOverride.accept(rsn);
            flashAdmin(clearBtn, "Cleared ✓");
        });
        box.add(clearBtn);
        return box;
    }

    private void styleAdminBtn(JButton b)
    {
        b.setFont(READABLE_FONT_SMALL);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
    }

    private void flashAdmin(JButton b, String text)
    {
        String old = b.getText();
        b.setText(text);
        javax.swing.Timer t = new javax.swing.Timer(1500, e -> b.setText(old));
        t.setRepeats(false);
        t.start();
    }

    private JPanel buildSectionCard(String title, String subtitle, Color accent, Runnable action)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(new Color(40, 40, 40));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
            new EmptyBorder(8, 10, 8, 10)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel txt = new JPanel();
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        txt.setBackground(card.getBackground());
        JLabel t = new JLabel(title);
        t.setFont(READABLE_FONT.deriveFont(Font.BOLD));
        t.setForeground(Color.WHITE);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = new JLabel(subtitle);
        s.setFont(READABLE_FONT_SMALL);
        s.setForeground(new Color(160, 160, 160));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        txt.add(t);
        txt.add(s);
        card.add(txt, BorderLayout.CENTER);

        JLabel arrow = new JLabel(">");
        arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, 12f));
        arrow.setForeground(new Color(100, 100, 100));
        card.add(arrow, BorderLayout.EAST);

        makeCardClickable(card, action);
        return card;
    }

    private void showMemberPbs()
    {
        membersContent.removeAll();
        membersContent.add(clogBackButton("← " + currentClogRsn, this::renderMemberProfile));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle("Speed Times", new Color(100, 149, 237), 14f));
        membersContent.add(Box.createVerticalStrut(4));

        if (currentProfile == null || currentProfile.pbs.isEmpty())
        {
            membersContent.add(clogNote("No personal bests recorded."));
        }
        else
        {
            // Organize by the same display-group order used on the Speed Times tab, then by boss
            // name, then team size — so related bosses cluster instead of a flat unordered list.
            java.util.List<String> groupOrder = new java.util.ArrayList<>(DISPLAY_GROUP_COLORS.keySet());
            java.util.List<PlatformApiService.PlayerPb> sorted = new java.util.ArrayList<>(currentProfile.pbs);
            sorted.sort((a, b) ->
            {
                BossCategory ca = BossCategory.fromKey(a.bossKey);
                BossCategory cb = BossCategory.fromKey(b.bossKey);
                int gi = Integer.compare(groupRank(groupOrder, ca), groupRank(groupOrder, cb));
                if (gi != 0) return gi;
                String na = ca != null ? ca.getDisplayName() : a.bossKey;
                String nb = cb != null ? cb.getDisplayName() : b.bossKey;
                int ni = na.compareToIgnoreCase(nb);
                return ni != 0 ? ni : Integer.compare(a.teamSize, b.teamSize);
            });

            String lastGroup = null;
            for (PlatformApiService.PlayerPb pb : sorted)
            {
                BossCategory cat = BossCategory.fromKey(pb.bossKey);
                String group = cat != null ? cat.getDisplayGroup() : "Other";
                if (!group.equals(lastGroup))
                {
                    lastGroup = group;
                    JLabel header = new JLabel(group);
                    header.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
                    header.setForeground(DISPLAY_GROUP_COLORS.getOrDefault(group, new Color(150, 150, 150)));
                    header.setAlignmentX(Component.LEFT_ALIGNMENT);
                    header.setBorder(new EmptyBorder(6, 2, 2, 0));
                    membersContent.add(header);
                }
                membersContent.add(buildPbRow(pb, cat));
                membersContent.add(Box.createVerticalStrut(2));
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** Rank a boss's display group against the canonical group order (unknown groups sort last). */
    private int groupRank(java.util.List<String> order, BossCategory cat)
    {
        String g = cat != null ? cat.getDisplayGroup() : "Other";
        int i = order.indexOf(g);
        return i < 0 ? order.size() : i;
    }

    private JPanel buildPbRow(PlatformApiService.PlayerPb pb, BossCategory cat)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(new Color(35, 35, 35));
        row.setBorder(new EmptyBorder(6, 8, 6, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        String displayName = cat != null ? cat.getDisplayName() : bossName(pb.bossKey);
        // Use the category's size bucket ("Duo"/"6+"/"Group"), not the raw participant count —
        // scaled raids (cox_group etc.) collapse to one best time, so the exact size is meaningless.
        String sizeLabel = (cat != null && cat.getMaxPlayers() > 1) ? cat.getSizeLabel() : null;
        String label = displayName + (sizeLabel != null ? " (" + sizeLabel + ")" : "");
        JLabel name = new JLabel(label);
        name.setFont(READABLE_FONT_SMALL);
        name.setForeground(Color.WHITE);

        JLabel time = new JLabel(formatMs(pb.timeMs));
        time.setFont(READABLE_FONT_SMALL);
        time.setForeground(new Color(100, 149, 237));
        row.add(time, BorderLayout.EAST);

        // Boss icon (by group) on the left, like the Speed Times overview. Null-safe.
        ImageIcon icon = cat != null ? bossIcons.get(cat.getGroup()) : null;
        if (icon != null)
        {
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
            row.add(iconLabel, BorderLayout.WEST);
        }

        boolean hasTeam = pb.teamSize > 1 && pb.teamMembers != null && !pb.teamMembers.isEmpty();
        if (hasTeam)
        {
            // Stack the roster beneath the boss name (e.g. "BlG Woody, BlG Moby").
            JPanel stacked = new JPanel();
            stacked.setLayout(new BoxLayout(stacked, BoxLayout.Y_AXIS));
            stacked.setOpaque(false);
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel team = new JLabel(pb.teamMembers);
            team.setFont(READABLE_FONT_SMALL.deriveFont(Font.ITALIC));
            team.setForeground(new Color(140, 140, 140));
            team.setAlignmentX(Component.LEFT_ALIGNMENT);
            stacked.add(name);
            stacked.add(team);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            row.add(stacked, BorderLayout.CENTER);
        }
        else
        {
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            row.add(name, BorderLayout.CENTER);
        }
        return row;
    }

    private void showMemberDrops()
    {
        membersContent.removeAll();
        membersContent.add(clogBackButton("← " + currentClogRsn, this::renderMemberProfile));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle("Recent Drops", new Color(255, 180, 100), 14f));
        membersContent.add(Box.createVerticalStrut(4));

        if (currentProfile == null || currentProfile.drops.isEmpty())
        {
            membersContent.add(clogNote("No drops logged."));
        }
        else
        {
            for (PlatformApiService.PlayerDrop d : currentProfile.drops)
            {
                membersContent.add(buildMemberDropRow(d));
                membersContent.add(Box.createVerticalStrut(2));
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    private JPanel buildMemberDropRow(PlatformApiService.PlayerDrop d)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(new Color(35, 35, 35));
        row.setBorder(new EmptyBorder(5, 8, 5, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(row.getBackground());
        JLabel item = new JLabel(d.itemName);
        item.setFont(READABLE_FONT_SMALL);
        item.setForeground(Color.WHITE);
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(item);
        if (d.monsterName != null && !d.monsterName.isEmpty())
        {
            JLabel from = new JLabel("from " + d.monsterName);
            from.setFont(READABLE_FONT_SMALL);
            from.setForeground(new Color(140, 140, 140));
            from.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(from);
        }
        row.add(left, BorderLayout.CENTER);

        if (d.value > 0)
        {
            JLabel val = new JLabel(formatXp(d.value));
            val.setFont(READABLE_FONT_SMALL);
            val.setForeground(ACCENT_GOLD);
            row.add(val, BorderLayout.EAST);
        }
        return row;
    }

    private String bossName(String bossKey)
    {
        BossCategory c = BossCategory.fromKey(bossKey);
        if (c != null) return c.getDisplayName();
        String s = bossKey == null ? "" : bossKey.replace('_', ' ');
        return s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String formatMs(int ms)
    {
        long totalSec = ms / 1000;
        return String.format("%d:%02d.%02d", totalSec / 60, totalSec % 60, (ms % 1000) / 10);
    }

    public void setOnLoadClog(java.util.function.Consumer<String> cb) { this.onLoadClog = cb; }
    public void setOnLoadCa(java.util.function.Consumer<String> cb) { this.onLoadCa = cb; }
    public void setOnLoadRanks(Runnable cb) { this.onLoadRanks = cb; }
    public void setOnRequestRank(java.util.function.Consumer<Object[]> cb) { this.onRequestRank = cb; }
    public boolean isRanksActive() { return ranksActive; }

    private JComponent buildRanksTab()
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel title = new JLabel("My Ranks");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(ACCENT_GOLD);
        titleRow.add(title, BorderLayout.WEST);
        JButton refresh = new JButton("↻");
        refresh.setMargin(new Insets(0, 6, 0, 6));
        refresh.setFocusPainted(false);
        refresh.setToolTipText("Re-check (open your bank first for item requirements)");
        refresh.addActionListener(e -> { if (onLoadRanks != null) onLoadRanks.run(); });
        titleRow.add(refresh, BorderLayout.EAST);
        wrapper.add(titleRow, BorderLayout.NORTH);

        ranksContent.setLayout(new BoxLayout(ranksContent, BoxLayout.Y_AXIS));
        ranksContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        ranksContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hint = new JLabel("Open this tab (or refresh) to check your ranks.");
        hint.setFont(READABLE_FONT_ITALIC);
        hint.setForeground(new Color(120, 120, 120));
        ranksContent.add(hint);

        JScrollPane scroll = new JScrollPane(ranksContent,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private java.util.Map<String, Integer> rankSnapshotIds; // owned name->id from the plugin, for icons

    /** Render the local player's rank eligibility (built in-game by the plugin) into the Ranks tab.
     *  itemIds is the player's owned name→id map (local only) so owned items always get an icon. */
    public void showRanks(java.util.List<RankSystem.RankStatus> results, java.util.Map<String, Integer> itemIds, String mode)
    {
        SwingUtilities.invokeLater(() ->
        {
            this.rankSnapshotIds = itemIds;
            ranksContent.removeAll();
            if (results == null || results.isEmpty())
            {
                ranksContent.add(clogNote("Log in to check your ranks."));
            }
            else
            {
                boolean clogOnly = "clog_only".equals(mode);
                String noteText = clogOnly
                    ? "Collection Log mode (set by an admin): ranks are checked from your collection log — open it once so the plugin can read it, then ↻. Nothing is sent."
                    : "Checked locally — nothing about your items is sent. Open your bank, then ↻.";
                JLabel note = new JLabel("<html>" + noteText + "</html>");
                note.setFont(READABLE_FONT_SMALL);
                note.setForeground(clogOnly ? new Color(150, 175, 210) : new Color(130, 130, 130));
                note.setAlignmentX(Component.LEFT_ALIGNMENT);
                note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
                note.setBorder(new EmptyBorder(0, 0, 6, 0));
                ranksContent.add(note);

                if (platformAdmin)
                {
                    JButton iconRef = new JButton("Find clan-rank icon IDs");
                    iconRef.setFont(READABLE_FONT_SMALL);
                    iconRef.setFocusPainted(false);
                    iconRef.setAlignmentX(Component.LEFT_ALIGNMENT);
                    iconRef.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                    iconRef.addActionListener(e -> showRankIconReference());
                    ranksContent.add(iconRef);
                    ranksContent.add(Box.createVerticalStrut(6));
                }

                for (RankSystem.RankStatus rs : results)
                {
                    ranksContent.add(buildRankCard(rs));
                    ranksContent.add(Box.createVerticalStrut(6));
                }
            }
            ranksContent.revalidate();
            ranksContent.repaint();
        });
    }

    /** Admin-set mode: the member's rank is assigned by an admin — show it, no self-evaluation. */
    public void showAdminAssignedRank(String rankName, String mode)
    {
        SwingUtilities.invokeLater(() ->
        {
            ranksContent.removeAll();
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(new Color(35, 35, 35));
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(186, 142, 255)),
                new EmptyBorder(10, 12, 10, 12)));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel h = new JLabel("Rank set by an admin");
            h.setFont(READABLE_FONT.deriveFont(Font.BOLD, 13f));
            h.setForeground(new Color(186, 142, 255));
            h.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(h);
            JLabel r = new JLabel(rankName != null && !rankName.isEmpty() ? rankName : "(not assigned yet)");
            r.setFont(READABLE_FONT.deriveFont(Font.BOLD, 15f));
            r.setForeground(Color.WHITE);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            r.setBorder(new EmptyBorder(4, 0, 4, 0));
            card.add(r);
            JLabel sub = new JLabel("<html>Your rank is managed manually by clan staff, so automatic checks are turned off for you.</html>");
            sub.setFont(READABLE_FONT_SMALL);
            sub.setForeground(new Color(140, 140, 140));
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(sub);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 4));
            ranksContent.add(card);
            ranksContent.revalidate();
            ranksContent.repaint();
        });
    }

    private JPanel buildRankCard(RankSystem.RankStatus rs)
    {
        // Card height tracks its current content so collapsing/expanding doesn't clip or over-stretch.
        JPanel card = new JPanel()
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(35, 35, 35));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, rs.eligible ? new Color(76, 175, 80) : new Color(110, 110, 110)),
            new EmptyBorder(4, 6, 4, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ── Header (always visible; click to expand/collapse) ──
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(new Color(35, 35, 35));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        header.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 3));
        left.setBackground(new Color(35, 35, 35));
        final JLabel caret = new JLabel("+");
        caret.setFont(READABLE_FONT.deriveFont(Font.BOLD, 14f));
        caret.setForeground(new Color(150, 150, 150));
        caret.setPreferredSize(new Dimension(11, 16));
        left.add(caret);
        left.add(rankSpriteIcon(rs.rank.id));
        JLabel name = new JLabel(rs.rank.name);
        name.setFont(READABLE_FONT.deriveFont(Font.BOLD, 13f));
        name.setForeground(rs.eligible ? new Color(90, 200, 90) : Color.WHITE);
        left.add(name);
        header.add(left, BorderLayout.WEST);

        int groupsMet = 0;
        for (RankSystem.GroupStatus gs : rs.groups) if (gs.satisfied()) groupsMet++;
        JLabel badge = new JLabel(rs.eligible ? "QUALIFIED" : groupsMet + "/" + rs.groups.size());
        badge.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
        badge.setForeground(rs.eligible ? new Color(90, 200, 90) : new Color(190, 160, 90));
        badge.setBorder(new EmptyBorder(0, 0, 0, 8));
        header.add(badge, BorderLayout.EAST);
        card.add(header);

        // ── Details (collapsed by default) ──
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(new Color(35, 35, 35));
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.setVisible(false);
        buildRankDetails(details, rs);
        card.add(details);

        java.awt.event.MouseAdapter toggle = new java.awt.event.MouseAdapter()
        {
            @Override public void mousePressed(java.awt.event.MouseEvent e)
            {
                boolean show = !details.isVisible();
                details.setVisible(show);
                caret.setText(show ? "–" : "+"); // – open / + closed (ASCII-safe glyphs)
                card.revalidate(); card.repaint();
                ranksContent.revalidate(); ranksContent.repaint();
            }
        };
        header.addMouseListener(toggle);
        left.addMouseListener(toggle);
        caret.addMouseListener(toggle);
        name.addMouseListener(toggle);
        return card;
    }

    /** The expandable body of a rank card: prerequisites, requirement groups + checks, request button. */
    private void buildRankDetails(JPanel details, RankSystem.RankStatus rs)
    {
        if (!rs.rank.requires.isEmpty())
        {
            boolean reqMet = rs.unmetRequires.isEmpty();
            StringBuilder names = new StringBuilder();
            for (String id : rs.rank.requires)
            {
                if (names.length() > 0) names.append(", ");
                names.append(RankSystem.nameOf(id));
            }
            JLabel reqLabel = new JLabel((reqMet ? "Requires (met): " : "Requires: ") + names);
            reqLabel.setFont(READABLE_FONT_SMALL.deriveFont(Font.ITALIC));
            reqLabel.setForeground(reqMet ? new Color(90, 200, 90) : new Color(210, 140, 90));
            reqLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            reqLabel.setBorder(new EmptyBorder(4, 2, 2, 0));
            details.add(reqLabel);
        }

        for (RankSystem.GroupStatus gs : rs.groups)
        {
            JLabel g = new JLabel(gs.group.label + "   " + gs.met + " / " + gs.group.need);
            g.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
            g.setForeground(gs.satisfied() ? new Color(90, 200, 90) : new Color(210, 180, 90));
            g.setAlignmentX(Component.LEFT_ALIGNMENT);
            g.setBorder(new EmptyBorder(6, 2, 2, 0));
            details.add(g);
            for (RankSystem.Result r : gs.results)
            {
                details.add(buildRankCheckRow(r));
            }
        }

        // Claim button — opt-in. Sends only the result (eligible + what's missing), never items.
        JButton request = new JButton(rs.eligible ? "Request " + rs.rank.name : "Request anyway (not eligible)");
        request.setFont(READABLE_FONT_SMALL);
        request.setFocusPainted(false);
        request.setAlignmentX(Component.LEFT_ALIGNMENT);
        request.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        request.setBorder(new EmptyBorder(4, 8, 4, 8));
        request.addActionListener(e ->
        {
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (RankSystem.GroupStatus gs2 : rs.groups)
            {
                if (!gs2.satisfied()) missing.add(gs2.group.label + " (" + gs2.met + "/" + gs2.group.need + ")");
            }
            if (onRequestRank != null) onRequestRank.accept(new Object[]{ rs.rank.name, rs.eligible, missing });
            request.setText("Requested — staff pinged");
            request.setEnabled(false);
        });
        details.add(Box.createVerticalStrut(6));
        details.add(request);
    }

    /** One requirement row: painted status square + item icon (if applicable) + label. No glyphs.
     *  BorderLayout (not FlowLayout) so long labels ellipsize on one line instead of wrapping + clipping. */
    private JPanel buildRankCheckRow(RankSystem.Result r)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(new Color(35, 35, 35));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(1, 2, 1, 2));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        left.setBackground(row.getBackground());

        // Status square (reliable — a painted component, not a font glyph).
        JLabel dot = new JLabel();
        dot.setOpaque(true);
        dot.setBackground(r.met ? new Color(76, 175, 80) : new Color(95, 95, 95));
        dot.setPreferredSize(new Dimension(9, 9));
        left.add(dot);

        // Item icon for item checks (bright if owned, faded if not).
        if (r.check.kind == RankSystem.Kind.ITEMS && r.check.names != null && !r.check.names.isEmpty())
        {
            int id = resolveItemId(r.check.names.get(0));
            if (id > 0) left.add(rankItemIcon(id, r.met));
        }
        row.add(left, BorderLayout.WEST);

        JLabel l = new JLabel(r.label);
        l.setFont(READABLE_FONT_SMALL);
        l.setForeground(r.met ? new Color(200, 210, 200) : new Color(140, 140, 140));
        l.setToolTipText(r.label); // full text on hover, since the row may ellipsize
        row.add(l, BorderLayout.CENTER);
        return row;
    }

    private JLabel rankItemIcon(int itemId, boolean met)
    {
        final float alpha = met ? 1.0f : 0.3f;
        JLabel label = new JLabel()
        {
            @Override protected void paintComponent(Graphics gr)
            {
                Graphics2D g2 = (Graphics2D) gr.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        label.setPreferredSize(new Dimension(20, 18));
        if (itemManager != null)
        {
            AsyncBufferedImage img = itemManager.getImage(itemId);
            label.setIcon(new ImageIcon(img));
            img.onLoaded(() -> { label.setIcon(new ImageIcon(img)); label.revalidate(); label.repaint(); });
        }
        return label;
    }

    private final java.util.Map<String, Integer> rankItemIdCache = new java.util.HashMap<>();
    private java.util.Map<String, Integer> clogNameToId; // clog name->id, resolves untradeable icons
    public void setClogNameToId(java.util.Map<String, Integer> m) { this.clogNameToId = m; }

    /** Resolve an exact in-game item name to its id (for icon display), cached.
     *  Prefers the player's OWNED items (works for untradeables too), then falls back to GE search. */
    private int resolveItemId(String name)
    {
        if (name == null || itemManager == null) return -1;
        String key = name.toLowerCase();
        if (rankSnapshotIds != null)
        {
            Integer owned = rankSnapshotIds.get(key);
            if (owned != null && owned > 0) return owned;
        }
        // Collection-log name→id covers UNTRADEABLES (fire cape, void, infernal cape, fighter torso…)
        // that the GE search below can't return.
        if (clogNameToId != null)
        {
            Integer clog = clogNameToId.get(key);
            if (clog != null && clog > 0) return clog;
        }
        Integer cached = rankItemIdCache.get(key);
        if (cached != null) return cached;
        int id = -1;
        try
        {
            for (ItemPrice p : itemManager.search(name))
            {
                if (p.getName() != null && p.getName().equalsIgnoreCase(name)) { id = p.getId(); break; }
            }
        }
        catch (Exception ignored) { /* search may fail offline */ }
        rankItemIdCache.put(key, id);
        return id;
    }

    /** Entry point from the plugin: cache the CA data and show the tier overview. */
    public void showPlayerCa(String rsn, PlatformApiService.PlayerCa ca)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentClogRsn = rsn;
            currentCa = ca;
            renderCaOverview();
        });
    }

    /** CA level 1: overall points + per-tier progress cards (each clickable → tier task list). */
    private void renderCaOverview()
    {
        membersContent.removeAll();
        membersContent.add(clogBackButton("← " + currentClogRsn, this::renderMemberProfile));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle("Combat Achievements", ACCENT_CA, 14f));

        if (currentCa == null)
        {
            membersContent.add(clogNote("No combat achievements synced for this player."));
        }
        else
        {
            JLabel headline = new JLabel(currentCa.completed + " / " + currentCa.total
                + "   ·   " + currentCa.pointsEarned + " / " + currentCa.pointsTotal + " pts");
            headline.setFont(READABLE_FONT);
            headline.setForeground(ACCENT_CA);
            headline.setAlignmentX(Component.LEFT_ALIGNMENT);
            headline.setBorder(new EmptyBorder(2, 0, 8, 0));
            membersContent.add(headline);

            for (PlatformApiService.CaTier t : currentCa.tiers)
            {
                JPanel card = buildClogProgressCard(t.tier, t.completed, t.total);
                makeCardClickable(card, () -> showCaTier(t.tier));
                membersContent.add(card);
                membersContent.add(Box.createVerticalStrut(4));
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** CA level 2: a tier's tasks, grouped by boss, each marked done/missing — searchable. */
    private void showCaTier(String tier)
    {
        currentCaTier = tier;
        membersContent.removeAll();
        membersContent.add(clogBackButton("← Combat Achievements", this::renderCaOverview));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle(tier + " Tier", ACCENT_CA, 14f));
        membersContent.add(Box.createVerticalStrut(4));
        caTaskSearchField.setText("");
        membersContent.add(caTaskSearchField);
        membersContent.add(Box.createVerticalStrut(4));
        membersContent.add(caTaskListPanel);
        renderCaTasks();
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** Render the current tier's tasks (grouped by boss, done/missing), filtered by the search box. */
    private void renderCaTasks()
    {
        if (currentCa == null || currentCaTier == null) return;
        caTaskListPanel.removeAll();
        String q = caTaskSearchField.getText() == null ? "" : caTaskSearchField.getText().trim().toLowerCase();
        String lastBoss = null;
        int shown = 0;
        for (PlatformApiService.CaTaskInfo t : currentCa.tasks)
        {
            if (!currentCaTier.equals(t.tier)) continue;
            if (!q.isEmpty() && !(t.name.toLowerCase().contains(q) || t.monster.toLowerCase().contains(q))) continue;
            if (!t.monster.equals(lastBoss))
            {
                lastBoss = t.monster;
                JLabel boss = new JLabel(t.monster);
                boss.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
                boss.setForeground(new Color(150, 150, 150));
                boss.setAlignmentX(Component.LEFT_ALIGNMENT);
                boss.setBorder(new EmptyBorder(6, 2, 2, 0));
                caTaskListPanel.add(boss);
            }
            caTaskListPanel.add(buildCaTaskRow(t));
            caTaskListPanel.add(Box.createVerticalStrut(2));
            shown++;
        }
        if (shown == 0)
        {
            JLabel none = new JLabel(q.isEmpty() ? "No tasks" : "No matches");
            none.setFont(READABLE_FONT_ITALIC);
            none.setForeground(new Color(100, 100, 100));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            caTaskListPanel.add(none);
        }
        caTaskListPanel.revalidate();
        caTaskListPanel.repaint();
    }

    private JPanel buildCaTaskRow(PlatformApiService.CaTaskInfo t)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(new Color(35, 35, 35));
        row.setBorder(new EmptyBorder(5, 8, 5, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        left.setBackground(row.getBackground());
        JLabel mark = new JLabel(t.completed ? "✓" : "○");
        mark.setFont(mark.getFont().deriveFont(Font.BOLD, 12f));
        mark.setForeground(t.completed ? new Color(76, 175, 80) : new Color(90, 90, 90));
        left.add(mark);
        JLabel name = new JLabel(t.name);
        name.setFont(READABLE_FONT_SMALL);
        name.setForeground(t.completed ? Color.WHITE : new Color(130, 130, 130));
        left.add(name);
        row.add(left, BorderLayout.WEST);

        JLabel pts = new JLabel(t.points + "pt");
        pts.setFont(READABLE_FONT_SMALL);
        pts.setForeground(new Color(120, 120, 120));
        row.add(pts, BorderLayout.EAST);
        row.setToolTipText(t.type != null && !t.type.isEmpty() ? t.type : null);
        return row;
    }

    /** Level 1: per-tab progress (each tab clickable → category list). */
    private void renderClogOverview()
    {
        membersContent.removeAll();
        membersContent.add(clogBackButton("← " + currentClogRsn, this::renderMemberProfile));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle("Collection Log", new Color(186, 142, 255), 14f));

        if (currentClog == null)
        {
            membersContent.add(clogNote("No collection log synced for this player."));
        }
        else
        {
            int pct = currentClog.total > 0 ? (int) Math.round(currentClog.obtained * 100.0 / currentClog.total) : 0;
            JLabel headline = new JLabel(currentClog.obtained + " / " + currentClog.total + "  (" + pct + "%)");
            headline.setFont(READABLE_FONT);
            headline.setForeground(ACCENT_GOLD);
            headline.setAlignmentX(Component.LEFT_ALIGNMENT);
            headline.setBorder(new EmptyBorder(2, 0, 8, 0));
            membersContent.add(headline);

            for (java.util.Map.Entry<String, int[]> en : groupClog(null).entrySet())
            {
                final String tab = en.getKey();
                JPanel card = buildClogProgressCard(tab, en.getValue()[0], en.getValue()[1]);
                makeCardClickable(card, () -> showClogTab(tab));
                membersContent.add(card);
                membersContent.add(Box.createVerticalStrut(4));
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** Level 2: categories within a tab — searchable, each clickable → icon grid. */
    private void showClogTab(String tab)
    {
        currentClogTab = tab;
        membersContent.removeAll();
        membersContent.add(clogBackButton("← " + currentClogRsn, this::renderClogOverview));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle(tab, new Color(100, 149, 237), 14f));
        membersContent.add(Box.createVerticalStrut(4));
        clogTabSearchField.setText(""); // clear the search when entering a tab
        membersContent.add(clogTabSearchField);
        membersContent.add(Box.createVerticalStrut(4));
        membersContent.add(clogCatListPanel);
        renderClogCategories();
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** Render the current tab's categories into the list panel, filtered by the search box. */
    private void renderClogCategories()
    {
        if (currentClogTab == null) return;
        clogCatListPanel.removeAll();
        String q = clogTabSearchField.getText() == null ? "" : clogTabSearchField.getText().trim().toLowerCase();
        int shown = 0;
        for (java.util.Map.Entry<String, int[]> en : groupClog(currentClogTab).entrySet())
        {
            final String cat = en.getKey();
            if (!q.isEmpty() && !cat.toLowerCase().contains(q)) continue;
            JPanel card = buildClogProgressCard(cat, en.getValue()[0], en.getValue()[1]);
            makeCardClickable(card, () -> showClogCategory(currentClogTab, cat));
            clogCatListPanel.add(card);
            clogCatListPanel.add(Box.createVerticalStrut(4));
            shown++;
        }
        if (shown == 0)
        {
            JLabel none = new JLabel(q.isEmpty() ? "No entries" : "No matches");
            none.setFont(READABLE_FONT_ITALIC);
            none.setForeground(new Color(100, 100, 100));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            clogCatListPanel.add(none);
        }
        clogCatListPanel.revalidate();
        clogCatListPanel.repaint();
    }

    /** Level 3: the item icon grid for a category (owned bright, missing dimmed). */
    private void showClogCategory(String tab, String category)
    {
        membersContent.removeAll();
        membersContent.add(clogBackButton("← " + tab, () -> showClogTab(tab)));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle(category, new Color(100, 149, 237), 14f));

        int owned = 0, total = 0;
        JPanel grid = new JPanel(new GridLayout(0, 5, 3, 3));
        grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        for (PlatformApiService.ClogCatalogItem it : currentClog.items)
        {
            if (!category.equals(it.category) || !tab.equals(it.tab)) continue;
            grid.add(iconCell(it));
            total++;
            if (it.owned) owned++;
        }

        JLabel cnt = new JLabel(owned + " / " + total);
        cnt.setFont(READABLE_FONT_SMALL);
        cnt.setForeground(new Color(170, 170, 170));
        cnt.setAlignmentX(Component.LEFT_ALIGNMENT);
        cnt.setBorder(new EmptyBorder(2, 0, 8, 0));
        membersContent.add(cnt);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        holder.add(grid, BorderLayout.NORTH);
        membersContent.add(holder);

        membersContent.revalidate();
        membersContent.repaint();
    }

    /** Group the cached clog by tab (tabFilter == null) or by category within one tab. */
    private java.util.LinkedHashMap<String, int[]> groupClog(String tabFilter)
    {
        java.util.LinkedHashMap<String, int[]> out = new java.util.LinkedHashMap<>();
        if (currentClog == null) return out;
        for (PlatformApiService.ClogCatalogItem it : currentClog.items)
        {
            if (tabFilter != null && !tabFilter.equals(it.tab)) continue;
            String key = tabFilter == null ? it.tab : it.category;
            int[] c = out.computeIfAbsent(key, k -> new int[2]);
            c[1]++;
            if (it.owned) c[0]++;
        }
        return out;
    }

    private JPanel buildClogProgressCard(String label, int obtained, int total)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(35, 35, 35));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 55, 55)),
            new EmptyBorder(6, 8, 6, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JPanel rowTop = new JPanel(new BorderLayout());
        rowTop.setBackground(card.getBackground());
        JLabel name = new JLabel(label);
        name.setFont(READABLE_FONT);
        name.setForeground(Color.WHITE);
        JLabel cnt = new JLabel(obtained + " / " + total);
        cnt.setFont(READABLE_FONT_SMALL);
        cnt.setForeground(new Color(170, 170, 170));
        rowTop.add(name, BorderLayout.WEST);
        rowTop.add(cnt, BorderLayout.EAST);
        card.add(rowTop);
        card.add(Box.createVerticalStrut(3));

        double frac = total > 0 ? (double) obtained / total : 0;
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) Math.round(frac * 100));
        bar.setForeground(frac >= 1.0 ? new Color(76, 175, 80) : new Color(186, 142, 255));
        bar.setBackground(new Color(20, 20, 20));
        bar.setBorderPainted(false);
        bar.setStringPainted(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(bar);

        return card;
    }

    /** A single item cell: local game icon, bright if owned and faded if missing. */
    private JComponent iconCell(PlatformApiService.ClogCatalogItem it)
    {
        final float alpha = it.owned ? 1.0f : 0.22f;
        JLabel label = new JLabel()
        {
            @Override protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(36, 32));
        label.setToolTipText(it.name + (it.owned ? "" : " — missing"));
        if (itemManager != null && it.itemId > 0)
        {
            AsyncBufferedImage img = itemManager.getImage(it.itemId);
            label.setIcon(new ImageIcon(img));
            img.onLoaded(() -> { label.setIcon(new ImageIcon(img)); label.revalidate(); label.repaint(); });
        }
        return label;
    }

    private JButton clogBackButton(String text, Runnable action)
    {
        JButton back = new JButton(text);
        back.setFont(READABLE_FONT_SMALL);
        back.setFocusPainted(false);
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        back.addActionListener(e -> action.run());
        return back;
    }

    private JLabel clogTitle(String text, Color color, float size)
    {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel clogNote(String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(READABLE_FONT_SMALL);
        l.setForeground(new Color(150, 150, 150));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(8, 0, 0, 0));
        return l;
    }

    /** Make a whole card (incl. its child labels) clickable. */
    private void makeCardClickable(JComponent card, Runnable action)
    {
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter ma = new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
        };
        addMouseRecursive(card, ma);
    }

    private void addMouseRecursive(Component c, MouseAdapter ma)
    {
        c.addMouseListener(ma);
        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents()) addMouseRecursive(child, ma);
        }
    }

    public void setOnLoadRoster(Runnable cb) { this.onLoadRoster = cb; }
    public void setOnSelectMember(java.util.function.Consumer<String> cb) { this.onSelectMember = cb; }
    public void setPlatformAdmin(boolean a) { this.platformAdmin = a; }
    public void setOnSetRankOverride(java.util.function.Consumer<Object[]> cb) { this.onSetRankOverride = cb; }
    public void setOnClearRankOverride(java.util.function.Consumer<String> cb) { this.onClearRankOverride = cb; }

    private JPanel createNavCard(String name, String description, Color accentColor, String tabName)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(new Color(40, 40, 40));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
            new EmptyBorder(10, 12, 10, 12)
        ));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
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
     * Set the announcements shown on the home tab (pinned ones first, gold-accented cards).
     */
    public void setAnnouncements(List<PlatformApiService.Announcement> items)
    {
        SwingUtilities.invokeLater(() ->
        {
            announcementsPanel.removeAll();

            if (items == null || items.isEmpty())
            {
                JLabel none = new JLabel("No announcements");
                none.setFont(none.getFont().deriveFont(Font.ITALIC, 11f));
                none.setForeground(new Color(100, 100, 100));
                none.setAlignmentX(Component.CENTER_ALIGNMENT);
                announcementsPanel.add(none);
            }
            else
            {
                for (PlatformApiService.Announcement a : items)
                {
                    JPanel card = new JPanel(new BorderLayout(8, 0));
                    card.setBackground(new Color(30, 28, 15));
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_GOLD_DIM),
                        new EmptyBorder(8, 10, 8, 10)
                    ));
                    card.setAlignmentX(Component.CENTER_ALIGNMENT);
                    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

                    String pin = a.pinned ? "📌 " : ""; // pushpin
                    String author = a.author != null && !a.author.isEmpty()
                        ? "<br><span style='color:#8a8a6a'>— " + escapeHtml(a.author) + "</span>" : "";
                    JLabel text = new JLabel("<html>" + pin + escapeHtml(a.message) + author + "</html>");
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

    /** Minimal HTML escape so announcement text renders literally inside the JLabel HTML. */
    private static String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Update the active event card on the Home tab.
     */
    public void updateActiveEvent(String type, String displayName, String endTime,
                                  List<LeaderboardEntry> leaderboard)
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
                    LeaderboardEntry entry = leaderboard.get(i);
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
    public void updateActivity(List<PlatformApiService.ActivityItem> entries)
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
                int idx = 0;
                for (PlatformApiService.ActivityItem entry : entries)
                {
                    JPanel row = new JPanel(new BorderLayout(6, 0));
                    row.setBackground(idx++ % 2 == 0 ? ColorScheme.DARK_GRAY_COLOR : new Color(35, 35, 35));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                    row.setBorder(new EmptyBorder(3, 6, 3, 6));

                    String icon;
                    String color;
                    String desc;
                    String detail = entry.detail == null ? "" : entry.detail;
                    switch (entry.type)
                    {
                        case "join": icon = "+"; color = "#4CAF50"; desc = entry.rsn + " joined the clan"; break;
                        case "leave": icon = "\u2212"; color = "#E05B5B"; desc = entry.rsn + " left the clan"; break;
                        case "pb": icon = "\u23f1"; color = "#5B9BD5"; desc = entry.rsn + ": " + entry.title + (detail.isEmpty() ? "" : " \u2014 " + detail); break;
                        case "drop": icon = "$"; color = "#FFD700"; desc = entry.rsn + ": " + entry.title
                            + (entry.value > 0 ? " (" + formatXp(entry.value) + " gp)" : "") + (detail.isEmpty() ? "" : " " + detail); break;
                        case "clog": icon = "\u2605"; color = "#C77DFF"; desc = entry.rsn + ": " + entry.title + (detail.isEmpty() ? "" : " (" + detail + ")"); break;
                        case "ca": icon = "\u2694"; color = "#DC7A3C"; desc = entry.rsn + ": " + entry.title; break;
                        default: icon = "\u2022"; color = "#888888"; desc = entry.rsn + " " + entry.title;
                    }

                    // Colored icon on the left.
                    JLabel iconLabel = new JLabel(icon);
                    iconLabel.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
                    try { iconLabel.setForeground(Color.decode(color)); }
                    catch (Exception ignored) { iconLabel.setForeground(new Color(150, 150, 150)); }
                    row.add(iconLabel, BorderLayout.WEST);

                    // Description fills the middle (truncated so it never pushes the row wide). Full text on hover.
                    JLabel label = new JLabel(truncate(desc, 40));
                    label.setFont(READABLE_FONT_SMALL);
                    label.setForeground(new Color(200, 200, 200));
                    label.setToolTipText(desc);
                    row.add(label, BorderLayout.CENTER);

                    // Time-ago on the right.
                    JLabel timeLabel = new JLabel(formatTimeAgo(entry.createdAt));
                    timeLabel.setFont(READABLE_FONT_SMALL);
                    timeLabel.setForeground(new Color(110, 110, 110));
                    row.add(timeLabel, BorderLayout.EAST);

                    activityPanel.add(row);
                    activityPanel.add(Box.createVerticalStrut(1));
                }
            }

            activityPanel.revalidate();
            activityPanel.repaint();
        });
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
    private static final Color ACCENT_CA = new Color(220, 120, 60); // Combat Achievements accent

    // ══════════════════════════════════════════
    // WOM XP Tab
    // ══════════════════════════════════════════

    private JComponent buildWomTab()
    {
        ScrollableColumn wrapper = new ScrollableColumn();
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

    public void updateWomLeaderboard(List<LeaderboardEntry> entries, boolean isGained)
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

            for (LeaderboardEntry entry : entries)
            {
                womLeaderboardPanel.add(createWomRow(entry, isGained));
                womLeaderboardPanel.add(Box.createVerticalStrut(1));
            }

            womLeaderboardPanel.revalidate();
            womLeaderboardPanel.repaint();
        });
    }

    private JPanel createWomRow(LeaderboardEntry entry, boolean isGained)
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

    public void setOnPbModeChange(java.util.function.Consumer<String> callback)
    {
        this.onPbModeChange = callback;
    }

    public void setOnActivityFilterChange(java.util.function.Consumer<String> callback)
    {
        this.onActivityFilterChange = callback;
    }

    /** Map an activity filter dropdown label to the API's ?type= CSV ("" = everything). */
    private static String activityTypeFilter(String label)
    {
        if (label == null) return "";
        switch (label)
        {
            case "Drops": return "drop";
            case "Personal Bests": return "pb";
            case "Collection Log": return "clog";
            case "Combat Achievements": return "ca";
            default: return "";
        }
    }

    /**
     * Populate a times panel with fetched HiscoreEntry data.
     * Called by the plugin after fetching times for a category.
     */
    // Boss group-key -> small icon, supplied by the plugin (which has ItemManager).
    private java.util.Map<String, ImageIcon> bossIcons = java.util.Collections.emptyMap();

    public void setBossIcons(java.util.Map<String, ImageIcon> icons)
    {
        this.bossIcons = icons != null ? icons : java.util.Collections.emptyMap();
        SwingUtilities.invokeLater(() -> {
            // Show the boss icon beside each name in the boss selector / search dropdown too.
            hiscoreBossCombo.setRenderer(new javax.swing.DefaultListCellRenderer()
            {
                @Override
                public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus)
                {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    ImageIcon ic = null;
                    if (value instanceof String)
                    {
                        List<BossCategory> cats = BossCategory.getCategoriesForBossAnyGroup((String) value);
                        if (!cats.isEmpty()) ic = bossIcons.get(cats.get(0).getGroup());
                    }
                    lbl.setIcon(ic);
                    lbl.setIconTextGap(5);
                    return lbl;
                }
            });
            showRecentPbsOverview();
        });
    }

    public void populateTimesPanel(JPanel timesPanel, List<HiscoreEntry> entries, Color accentColor)
    {
        SwingUtilities.invokeLater(() ->
        {
            // Cache the full leaderboard so the player filter can re-render it without a re-fetch.
            lastTimesEntries = entries != null ? entries : new java.util.ArrayList<>();
            lastTimesAccent = accentColor;
            renderTimesFiltered();
        });
    }

    /** Current player-filter query (lowercased, "" when empty/placeholder). */
    private String playerQuery()
    {
        String t = hiscorePlayerSearchField.getText();
        if (t == null || t.equals(PLAYER_FILTER_PLACEHOLDER)) return "";
        return t.trim().toLowerCase();
    }

    /** Re-render the cached boss leaderboard into the times panel, filtered by the player query. */
    private void renderTimesFiltered()
    {
        if (lastTimesEntries == null) return; // not a boss-leaderboard view (e.g. recent overview)

        hiscoreTimesPanel.removeAll();
        String q = playerQuery();
        boolean any = false;
        for (HiscoreEntry entry : lastTimesEntries)
        {
            if (!q.isEmpty() && (entry.getRsns() == null || !entry.getRsns().toLowerCase().contains(q)))
            {
                continue;
            }
            hiscoreTimesPanel.add(createTimeEntry(entry, lastTimesAccent));
            any = true;
        }
        if (!any)
        {
            JLabel none = new JLabel(q.isEmpty() ? "No times recorded" : "No players match");
            none.setFont(READABLE_FONT_ITALIC);
            none.setForeground(new Color(80, 80, 80));
            none.setBorder(new EmptyBorder(6, 36, 6, 10));
            hiscoreTimesPanel.add(none);
        }
        hiscoreTimesPanel.revalidate();
        hiscoreTimesPanel.repaint();
    }

    private JPanel createTimeEntry(HiscoreEntry entry, Color accentColor)
    {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);
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
            // ── Solo layout: clean single row — rank · time · rsn · date ──
            JPanel rowSolo = new JPanel(new BorderLayout(6, 0));
            rowSolo.setBackground(ColorScheme.DARK_GRAY_COLOR);
            // Thin bottom divider only (no boxed border), light left padding
            rowSolo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR),
                new EmptyBorder(5, 10, 5, 8)
            ));
            rowSolo.setAlignmentX(Component.LEFT_ALIGNMENT);
            rowSolo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

            // Left: rank + time together
            JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            left.setBackground(rowSolo.getBackground());
            JLabel rankLabel = new JLabel(rankText);
            rankLabel.setFont(rankLabel.getFont().deriveFont(Font.BOLD, 10f));
            rankLabel.setForeground(rankColor);
            left.add(rankLabel);
            JLabel timeLabel = new JLabel(entry.getFormattedTime());
            timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 11f));
            timeLabel.setForeground(Color.WHITE);
            left.add(timeLabel);
            rowSolo.add(left, BorderLayout.WEST);

            // Center: rsn (muted)
            JLabel rsnLabel = new JLabel(rsns);
            rsnLabel.setFont(READABLE_FONT);
            rsnLabel.setForeground(new Color(170, 170, 170));
            rsnLabel.setBorder(new EmptyBorder(0, 6, 0, 0));
            rowSolo.add(rsnLabel, BorderLayout.CENTER);

            // Right: date (subtle)
            if (!date.isEmpty())
            {
                JLabel dateLabel = new JLabel(date);
                dateLabel.setFont(READABLE_FONT_SMALL);
                dateLabel.setForeground(new Color(110, 110, 110));
                rowSolo.add(dateLabel, BorderLayout.EAST);
            }

            // Hover highlight
            rowSolo.addMouseListener(new MouseAdapter()
            {
                @Override public void mouseEntered(MouseEvent e) { rowSolo.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR); left.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR); }
                @Override public void mouseExited(MouseEvent e) { rowSolo.setBackground(ColorScheme.DARK_GRAY_COLOR); left.setBackground(ColorScheme.DARK_GRAY_COLOR); }
            });

            container.add(rowSolo);
        }
        else
        {
            // ── Group layout: clickable time row with expandable player list ──

            // Detail panel (hidden until clicked)
            JPanel detailPanel = new JPanel();
            detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
            detailPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
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
            timeRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            timeRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR),
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
                public void mouseEntered(MouseEvent e) { timeRow.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR); }
                @Override
                public void mouseExited(MouseEvent e) { timeRow.setBackground(ColorScheme.DARK_GRAY_COLOR); }
            });

            container.add(timeRow);
            container.add(detailPanel);
        }

        return container;
    }

    private JPanel buildHiscoresTab()
    {
        // ScrollableColumn tracks the viewport width so the tab never overflows the panel ("too wide").
        ScrollableColumn wrapper = new ScrollableColumn();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.setBorder(new EmptyBorder(6, 4, 6, 4));

        // Title row with refresh button
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel hiscoreTitle = new JLabel("Clan Speed Times");
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

        // ── Mode toggle (All PBs vs Clan-verified only) ──
        JLabel hiscoreModeLabel = new JLabel("Mode");
        hiscoreModeLabel.setFont(READABLE_FONT);
        hiscoreModeLabel.setForeground(new Color(180, 180, 180));
        hiscoreModeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(hiscoreModeLabel);
        wrapper.add(Box.createVerticalStrut(2));

        hiscoreModeCombo.addItem("All PBs");
        hiscoreModeCombo.addItem("Clan Only");
        hiscoreModeCombo.setBackground(new Color(30, 30, 30));
        hiscoreModeCombo.setForeground(Color.WHITE);
        hiscoreModeCombo.setFont(READABLE_FONT);
        hiscoreModeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscoreModeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        hiscoreModeCombo.addActionListener(e ->
        {
            if (onPbModeChange != null)
            {
                onPbModeChange.accept("Clan Only".equals(hiscoreModeCombo.getSelectedItem()) ? "clan" : "all");
            }
        });
        wrapper.add(hiscoreModeCombo);
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

        // ── Player filter (filters the current boss leaderboard by name, client-side) ──
        hiscorePlayerSearchField.setBackground(new Color(30, 30, 30));
        hiscorePlayerSearchField.setForeground(new Color(100, 100, 100));
        hiscorePlayerSearchField.setCaretColor(Color.WHITE);
        hiscorePlayerSearchField.setFont(READABLE_FONT_SMALL);
        hiscorePlayerSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscorePlayerSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        hiscorePlayerSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50, 50, 50)),
            new EmptyBorder(2, 6, 2, 6)));
        hiscorePlayerSearchField.setText(PLAYER_FILTER_PLACEHOLDER);
        hiscorePlayerSearchField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override public void focusGained(java.awt.event.FocusEvent e)
            {
                if (hiscorePlayerSearchField.getText().equals(PLAYER_FILTER_PLACEHOLDER))
                {
                    hiscorePlayerSearchField.setText("");
                    hiscorePlayerSearchField.setForeground(Color.WHITE);
                }
            }
            @Override public void focusLost(java.awt.event.FocusEvent e)
            {
                if (hiscorePlayerSearchField.getText().isEmpty())
                {
                    hiscorePlayerSearchField.setText(PLAYER_FILTER_PLACEHOLDER);
                    hiscorePlayerSearchField.setForeground(new Color(100, 100, 100));
                }
            }
        });
        hiscorePlayerSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { renderTimesFiltered(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { renderTimesFiltered(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { renderTimesFiltered(); }
        });
        wrapper.add(hiscorePlayerSearchField);
        wrapper.add(Box.createVerticalStrut(4));

        // ── Times display panel ──
        hiscoreTimesPanel.setLayout(new BoxLayout(hiscoreTimesPanel, BoxLayout.Y_AXIS));
        hiscoreTimesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        hiscoreTimesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiscoreTimesPanel.setBorder(new EmptyBorder(2, 0, 2, 0));

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
        lastTimesEntries = null; // recent overview isn't a boss leaderboard — disable the player filter
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

                JPanel row = new JPanel(new BorderLayout(6, 0));
                row.setBackground(count % 2 == 0 ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                row.setBorder(new EmptyBorder(4, 8, 4, 8));

                // Boss icon (left of the name), if we have one for this group
                ImageIcon bossIcon = cat != null ? bossIcons.get(cat.getGroup()) : null;
                if (bossIcon != null)
                {
                    JLabel iconLabel = new JLabel(bossIcon);
                    iconLabel.setVerticalAlignment(SwingConstants.CENTER);
                    iconLabel.setBorder(new EmptyBorder(0, 0, 0, 2));
                    row.add(iconLabel, BorderLayout.WEST);
                }

                // Center: boss name
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
        lastTimesEntries = null; // avoid filtering stale entries while the new boss loads
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
        ScrollableColumn wrapper = new ScrollableColumn();
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
        whitelistSearchField.setText("Search item or boss...");
        whitelistSearchField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (whitelistSearchField.getText().equals("Search item or boss..."))
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
                    whitelistSearchField.setText("Search item or boss...");
                    whitelistSearchField.setForeground(new Color(100, 100, 100));
                }
            }
        });
        whitelistSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            private void filter()
            {
                String text = whitelistSearchField.getText();
                if (text.equals("Search item or boss...")) text = "";
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

        whitelistCategoryFilter.setFont(READABLE_FONT_SMALL);
        whitelistCategoryFilter.setMaximumSize(new Dimension(120, 22));
        whitelistCategoryFilter.addActionListener(e -> {
            String text = whitelistSearchField.getText();
            if (text.equals("Search item or boss...")) text = "";
            renderWhitelistBrowser(text.toLowerCase().trim());
        });
        filterRow.add(whitelistCategoryFilter, BorderLayout.CENTER);

        whitelistSortCombo.setFont(READABLE_FONT_SMALL);
        whitelistSortCombo.setMaximumSize(new Dimension(110, 22));
        whitelistSortCombo.addActionListener(e -> {
            String text = whitelistSearchField.getText();
            if (text.equals("Search item or boss...")) text = "";
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
                hdrName.setFont(new Font("Segoe UI", Font.BOLD, 11));
                hdrName.setForeground(new Color(150, 150, 200));
                headerRow.add(hdrName, BorderLayout.WEST);

                JLabel hdrRight = new JLabel("Pts  Drops  GP");
                hdrRight.setFont(new Font("Segoe UI", Font.BOLD, 11));
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
                    statsLabel.setFont(READABLE_FONT_SMALL);
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
                        gpLabel.setFont(READABLE_FONT_SMALL);
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
                    detLbl.setFont(READABLE_FONT_SMALL);
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

    /** Update the Collection Log status box, e.g. "875/1699" */
    public void setStatusClog(int obtained, int total)
    {
        SwingUtilities.invokeLater(() -> {
            if (total > 0)
            {
                statusClogLabel.setText(obtained + "/" + total);
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
        countLabel.setFont(READABLE_FONT_SMALL);
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
        String boss = item.getOrDefault("source", "");

        JPanel row = new JPanel(new BorderLayout(2, 0));
        row.setBackground(index % 2 == 0
            ? ColorScheme.DARK_GRAY_COLOR
            : new Color(35, 35, 35));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setBorder(new EmptyBorder(2, 4, 2, 4));
        if (!boss.isEmpty()) row.setToolTipText(name + " — " + boss + " (" + points + " pts)");

        // Left: item name
        JLabel nameLabel = new JLabel(truncate(name, 28));
        nameLabel.setFont(READABLE_FONT);
        // Color by point tier
        Color nameColor;
        if (points >= 150) nameColor = new Color(198, 40, 40);        // deep red — mega grind
        else if (points >= 80) nameColor = new Color(255, 100, 100);  // red
        else if (points >= 40) nameColor = new Color(255, 180, 100);  // orange
        else if (points >= 20) nameColor = new Color(76, 175, 80);    // green
        else if (points >= 10) nameColor = new Color(0, 150, 136);    // teal
        else nameColor = new Color(180, 180, 180);                     // gray
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

    /** @deprecated Clan name is hardcoded to Solus. */
    public void setClanName(String name)
    {
        // no-op — hardcoded to Solus
    }

    public void setOnRefresh(Runnable onRefresh)
    {
        this.onRefresh = onRefresh;
    }

    public void setOnRefreshStatus(Runnable cb)
    {
        this.onRefreshStatus = cb;
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
