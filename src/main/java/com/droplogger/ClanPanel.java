package com.droplogger;

import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
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
    private Runnable onLoadEvent;
    private boolean heldRanksExpanded = false;
    private boolean eventTabAutoShown = false;
    private javax.swing.Timer eventRefreshTimer = null;
    private javax.swing.Timer countdownTicker = null;
    private boolean eventLoaded = false;
    private final JPanel eventContent = new JPanel();
    private java.util.function.Consumer<String> onSelectMember;
    private boolean platformAdmin = false;
    private java.util.function.Consumer<Object[]> onSetRankOverride; // {rsn, mode, assignedRank}
    private java.util.function.Consumer<String> onClearRankOverride; // rsn
    private ItemManager itemManager; // for local item-icon rendering in the clog grid
    private PlatformApiService.PlayerClog currentClog = null;
    private String currentClogRsn = null;
    private String currentClogTab = null;
    private PlatformApiService.PlayerProfile currentProfile = null;
    private PlatformApiService.MemberAbout currentAbout = null;
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
        RANK_ICON_SPRITE.put("adamant_sword", 3142);
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
        // In-game STAR titles (staff designations) — the fallback icon for members without a
        // Discord ladder rank (e.g. the owner's Proselyte, Boomerclicks' Master).
        RANK_ICON_SPRITE.put("title_master", 3105);
        RANK_ICON_SPRITE.put("title_major", 3103);
        RANK_ICON_SPRITE.put("title_proselyte", 3101);
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
    // Leaderboards hub sub-view selector — home nav cards drive it to jump straight to a sub-view.
    private JComboBox<String> leaderboardsSelector;
    private final JComboBox<String> hiscoreModeCombo = new JComboBox<>();
    private java.util.function.Consumer<String> onPbModeChange;
    private final JComboBox<String> hiscoreGroupCombo = new JComboBox<>();
    private final JComboBox<String> hiscoreBossCombo = new JComboBox<>();
    private final JComboBox<String> hiscoreSizeCombo = new JComboBox<>();
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
    // Gained = the current rolling window; Records = best-ever gain in that window (WOM records)
    private final JComboBox<String> womViewCombo = new JComboBox<>(new String[]{"Gained", "Records"});
    private final JComboBox<String> womModeCombo = new JComboBox<>(new String[]{"Skills", "Boss KC"});
    private java.util.function.BiConsumer<String, String> onFetchWomData;

    // Boss label -> our snapshot-style key for Boss KC leaderboards (the clan API resolves these
    // against WiseOldMan bulk group data - one cached call serves every board).
    private static final java.util.LinkedHashMap<String, String> WOM_BOSS_METRICS = new java.util.LinkedHashMap<>();
    static
    {
        WOM_BOSS_METRICS.put("Chambers of Xeric", "chambers_of_xeric");
        WOM_BOSS_METRICS.put("CM Chambers of Xeric", "chambers_of_xeric:_challenge_mode");
        WOM_BOSS_METRICS.put("Theatre of Blood", "theatre_of_blood");
        WOM_BOSS_METRICS.put("ToB Hard Mode", "theatre_of_blood:_hard_mode");
        WOM_BOSS_METRICS.put("Tombs of Amascut", "tombs_of_amascut");
        WOM_BOSS_METRICS.put("ToA Expert", "tombs_of_amascut:_expert_mode");
        WOM_BOSS_METRICS.put("Zulrah", "zulrah");
        WOM_BOSS_METRICS.put("Vorkath", "vorkath");
        WOM_BOSS_METRICS.put("Araxxor", "araxxor");
        WOM_BOSS_METRICS.put("Cerberus", "cerberus");
        WOM_BOSS_METRICS.put("Alchemical Hydra", "alchemical_hydra");
        WOM_BOSS_METRICS.put("Abyssal Sire", "abyssal_sire");
        WOM_BOSS_METRICS.put("Phantom Muspah", "phantom_muspah");
        WOM_BOSS_METRICS.put("General Graardor", "general_graardor");
        WOM_BOSS_METRICS.put("Commander Zilyana", "commander_zilyana");
        WOM_BOSS_METRICS.put("Kree'arra", "kreearra");
        WOM_BOSS_METRICS.put("K'ril Tsutsaroth", "kril_tsutsaroth");
        WOM_BOSS_METRICS.put("Nex", "nex");
        WOM_BOSS_METRICS.put("Corporeal Beast", "corporeal_beast");
        WOM_BOSS_METRICS.put("The Gauntlet", "the_gauntlet");
        WOM_BOSS_METRICS.put("Corrupted Gauntlet", "the_corrupted_gauntlet");
        WOM_BOSS_METRICS.put("The Nightmare", "nightmare");
        WOM_BOSS_METRICS.put("Phosani's Nightmare", "phosanis_nightmare");
        WOM_BOSS_METRICS.put("TzKal-Zuk", "tzkalzuk");
        WOM_BOSS_METRICS.put("TzTok-Jad", "tztokjad");
        WOM_BOSS_METRICS.put("Sol Heredit", "sol_heredit");
        WOM_BOSS_METRICS.put("Vardorvis", "vardorvis");
        WOM_BOSS_METRICS.put("Duke Sucellus", "duke_sucellus");
        WOM_BOSS_METRICS.put("The Leviathan", "the_leviathan");
        WOM_BOSS_METRICS.put("The Whisperer", "the_whisperer");
        WOM_BOSS_METRICS.put("Callisto", "callisto");
        WOM_BOSS_METRICS.put("Vet'ion", "vetion");
        WOM_BOSS_METRICS.put("Venenatis", "venenatis");
        WOM_BOSS_METRICS.put("Wintertodt", "wintertodt");
        WOM_BOSS_METRICS.put("Tempoross", "tempoross");
        WOM_BOSS_METRICS.put("Zalcano", "zalcano");
    }

    /** Repopulate the metric combo for the selected mode (Skills or Boss KC). */
    private void populateWomMetricCombo()
    {
        womMetricCombo.removeAllItems();
        if ("Boss KC".equals(womModeCombo.getSelectedItem()))
        {
            for (String label : WOM_BOSS_METRICS.keySet()) womMetricCombo.addItem(label);
        }
        else
        {
            womMetricCombo.addItem("Overall");
            for (Skill skill : Skill.values())
            {
                if (skill == Skill.OVERALL) continue;
                womMetricCombo.addItem(skill.getName());
            }
        }
    }


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

    /**
     * super(false) opts out of PluginPanel's fixed-width wrapper, so the sidebar takes whatever
     * width our content computes — long names/rows pushed it past the standard panel width, and
     * RuneLite's keep-game-size behavior then grew members' client WINDOWS whenever the panel
     * opened (worst alongside resizable-hybrid). Pin the standard width; height stays computed.
     */
    private static final int PINNED_WIDTH = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
    // Wrap width for event standings rows: panel width minus the card border, insets and gold rule.
    private static final int STANDINGS_TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 40;

    // The plugin must NEVER resize the client — in either axis.
    //
    // WIDTH: pinned to the standard panel width. RuneLite's PluginPanel already fixes width, but
    // super(false) opts out of its wrapper, so we re-pin here.
    //
    // HEIGHT: this is the one that grew members' game WINDOWS. Base PluginPanel.getPreferredSize()
    // returns `super.getPreferredSize().height` — i.e. CONTENT height — and super(false) skips the
    // scroll-pane wrapper that would otherwise absorb it. So a tall tab (the Event race + standings)
    // made the panel's preferred height huge, and RuneLite's keep-game-size behavior grew the client
    // window to fit. Fix: report the PARENT viewport's height, never the content's. The panel then
    // fills the sidebar exactly (never taller), and each tab's own JScrollPane handles overflow.
    @Override
    public Dimension getPreferredSize()
    {
        java.awt.Container p = getParent();
        int h = (p != null && p.getHeight() > 0) ? p.getHeight() : 0;
        return new Dimension(PINNED_WIDTH, h);
    }

    @Override
    public Dimension getMinimumSize()
    {
        // Height 0 so the panel can shrink with the window instead of forcing it taller.
        return new Dimension(PINNED_WIDTH, 0);
    }

    @Override
    public Dimension getMaximumSize()
    {
        // Width capped (never wider); height unbounded so the layout can stretch it to fill.
        return new Dimension(PINNED_WIDTH, Integer.MAX_VALUE);
    }

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

        // Leaderboards tab — Speed Times / Drops / XP condensed behind one selector (mirrors the
        // website nav) so the panel isn't nine tabs wide.
        tabbedPane.addTab("Leaderboards", buildLeaderboardsTab());

        // Activity tab (clan joins, leaves, rank changes)
        tabbedPane.addTab("Activity", buildActivityTab());

        // Members tab (browse other players' collection logs)
        tabbedPane.addTab("Members", buildMembersTab());

        // Ranks tab (which clan ranks YOU qualify for)
        tabbedPane.addTab("Ranks", buildRanksTab());

        // Event tab intentionally NOT registered — the clan event feature isn't ready for public
        // release. buildEventTab()/renderRace()/etc. are kept (unused) for the WOM-backed rebuild.
        // Do NOT re-add addTab("Event", ...) without Ryan's go-ahead.

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

        JLabel desc = new JLabel("Pick a member to view their profile");
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

    /** Short colored account-type tag ("IM"/"HC"/"UIM"/"GIM"…); null for regular/unknown. */
    private JLabel accountTypeBadge(String type)
    {
        if (type == null) return null;
        String label;
        Color color;
        switch (type)
        {
            case "ironman": label = "IM"; color = new Color(160, 160, 160); break;
            case "hardcore": label = "HC"; color = new Color(220, 90, 90); break;
            case "ultimate": label = "UIM"; color = new Color(200, 200, 185); break;
            case "gim": label = "GIM"; color = new Color(90, 190, 120); break;
            case "hcgim": label = "HCGIM"; color = new Color(220, 90, 90); break;
            case "unranked_gim": label = "UGIM"; color = new Color(140, 150, 165); break;
            default: return null; // regular needs no badge
        }
        JLabel l = new JLabel(label);
        l.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD, 9f));
        l.setForeground(color);
        return l;
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
        JLabel typeBadge = accountTypeBadge(m.accountType);
        if (typeBadge != null)
        {
            JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            west.setOpaque(false);
            west.add(name);
            west.add(typeBadge);
            row.add(west, BorderLayout.WEST);
        }
        else
        {
            row.add(name, BorderLayout.WEST);
        }

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
    public void showMemberProfile(String rsn, PlatformApiService.PlayerProfile profile,
                                  PlatformApiService.MemberAbout about)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentClogRsn = rsn;
            currentProfile = profile;
            currentAbout = about;
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
        // Account type + EHB subtitle (either may be unknown; show what we have)
        if (currentProfile != null && (currentProfile.accountType != null || currentProfile.ehb != null))
        {
            String typeText;
            switch (currentProfile.accountType == null ? "" : currentProfile.accountType)
            {
                case "ironman": typeText = "Ironman"; break;
                case "hardcore": typeText = "Hardcore Ironman"; break;
                case "ultimate": typeText = "Ultimate Ironman"; break;
                case "gim": typeText = "Group Ironman"; break;
                case "hcgim": typeText = "Hardcore Group Ironman"; break;
                case "unranked_gim": typeText = "Unranked Group Ironman"; break;
                case "regular": typeText = "Main"; break;
                default: typeText = null;
            }
            StringBuilder sub = new StringBuilder();
            if (typeText != null) sub.append(typeText);
            if (currentProfile.ehb != null)
            {
                if (sub.length() > 0) sub.append("  ·  ");
                sub.append(String.format("%,.0f EHB", currentProfile.ehb));
            }
            JLabel subLabel = new JLabel(sub.toString());
            subLabel.setFont(READABLE_FONT_SMALL);
            subLabel.setForeground(new Color(150, 150, 150));
            subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            membersContent.add(subLabel);
        }
        membersContent.add(Box.createVerticalStrut(8));

        if (currentAbout != null && !currentAbout.isEmpty())
        {
            membersContent.add(buildAboutCard(currentAbout));
            membersContent.add(Box.createVerticalStrut(8));
        }

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
            membersContent.add(Box.createVerticalStrut(6));
            PlatformApiService.DiariesAndQuests dq = currentProfile.diariesAndQuests;
            String dqSub = dq != null
                ? dq.questsComplete + "/" + dq.questsTotal + " quests · "
                    + (dq.diaryEasy + dq.diaryMedium + dq.diaryHard + dq.diaryElite) + "/48 diaries"
                : "Not synced yet";
            membersContent.add(buildSectionCard("Diaries & Quests", dqSub, new Color(212, 175, 55),
                this::showMemberDiariesQuests));

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
        rankCombo.setBackground(new Color(30, 30, 30));
        rankCombo.setForeground(Color.WHITE);
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
        JPanel card = new JPanel(new BorderLayout(8, 0))
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        card.setBackground(new Color(40, 40, 40));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
            new EmptyBorder(8, 10, 8, 10)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel txt = new JPanel();
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        txt.setBackground(card.getBackground());
        JLabel t = new JLabel(title);
        t.setFont(READABLE_FONT.deriveFont(Font.BOLD));
        t.setForeground(Color.WHITE);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = new JLabel("<html>" + escapeHtml(subtitle) + "</html>");
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

    /** The member's customizable "about": bio + favorite boss/skill + goal (all optional). */
    private JComponent buildAboutCard(PlatformApiService.MemberAbout about)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(186, 142, 255)),
            new EmptyBorder(6, 8, 6, 8)));

        if (about.bio != null && !about.bio.isEmpty())
        {
            JLabel bio = new JLabel("<html><div style='width:" + STANDINGS_TEXT_WIDTH + "px'><i>“"
                + escapeHtml(about.bio) + "”</i></div></html>");
            bio.setForeground(Color.WHITE);
            bio.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(bio);
        }
        addAboutRow(card, "Favorite boss", about.favoriteBoss);
        addAboutRow(card, "Favorite skill", about.favoriteSkill);
        addAboutRow(card, "Current goal", about.goal);
        return card;
    }

    private void addAboutRow(JPanel card, String label, String value)
    {
        if (value == null || value.isEmpty()) return;
        JLabel row = new JLabel("<html><span style='color:#8a8a8a'>" + label + "</span> <b>"
            + escapeHtml(value) + "</b></html>");
        row.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.setFont(READABLE_FONT_SMALL);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(3, 0, 0, 0));
        card.add(row);
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

            // Bucket into collapsible sections. Raids each get their OWN section (CoX, CM CoX,
            // ToB, ToB: Hard Mode, ToA: Expert Mode…) instead of one merged "Raids" pile;
            // everything else sections by display group (GWD, Slayer, Wilderness…).
            java.util.Map<String, java.util.List<PlatformApiService.PlayerPb>> sections = new java.util.LinkedHashMap<>();
            java.util.Map<String, Color> sectionColors = new java.util.HashMap<>();
            for (PlatformApiService.PlayerPb pb : sorted)
            {
                BossCategory cat = BossCategory.fromKey(pb.bossKey);
                String group = cat != null ? cat.getDisplayGroup() : "Other";
                String section = "Raids".equals(group) && cat != null ? cat.getDisplayName() : group;
                sections.computeIfAbsent(section, k -> new java.util.ArrayList<>()).add(pb);
                sectionColors.putIfAbsent(section, DISPLAY_GROUP_COLORS.getOrDefault(group, new Color(150, 150, 150)));
            }

            for (java.util.Map.Entry<String, java.util.List<PlatformApiService.PlayerPb>> e : sections.entrySet())
            {
                membersContent.add(buildCollapsiblePbSection(e.getKey(), sectionColors.get(e.getKey()), e.getValue()));
                membersContent.add(Box.createVerticalStrut(4));
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** Member drill-down: Achievement Diary regions with per-tier ticks + quest standing. */
    private void showMemberDiariesQuests()
    {
        final Color GOLD = new Color(212, 175, 55);
        membersContent.removeAll();
        membersContent.add(clogBackButton("← " + currentClogRsn, this::renderMemberProfile));
        membersContent.add(Box.createVerticalStrut(6));
        membersContent.add(clogTitle("Diaries & Quests", GOLD, 14f));
        membersContent.add(Box.createVerticalStrut(4));

        PlatformApiService.DiariesAndQuests dq = currentProfile != null ? currentProfile.diariesAndQuests : null;
        if (dq == null)
        {
            membersContent.add(clogNote("Nothing synced yet — it syncs automatically when they log in with the plugin."));
            membersContent.revalidate();
            membersContent.repaint();
            return;
        }

        String capes = (dq.questCape ? "  Quest Cape" : "") + (dq.diaryCape ? "  Diary Cape" : "");
        membersContent.add(clogNote("Quests: " + dq.questsComplete + "/" + dq.questsTotal
            + "  ·  " + dq.questPoints + " QP" + capes));
        membersContent.add(Box.createVerticalStrut(6));

        // Achievement Diaries — one row per region, a tick per tier
        membersContent.add(clogTitle("Achievement Diaries", GOLD, 12f));
        membersContent.add(Box.createVerticalStrut(2));
        if (dq.diaries.isEmpty())
        {
            membersContent.add(clogNote(dq.diaryEasy + "/12 easy · " + dq.diaryMedium + "/12 medium · "
                + dq.diaryHard + "/12 hard · " + dq.diaryElite + "/12 elite"));
        }
        else
        {
            boolean alt = false;
            for (PlatformApiService.DiaryRegion r : dq.diaries)
            {
                membersContent.add(buildDiaryRegionRow(r, alt));
                alt = !alt;
            }
        }

        // Quests — the short interesting list is what's LEFT
        membersContent.add(Box.createVerticalStrut(8));
        membersContent.add(clogTitle("Quests", GOLD, 12f));
        membersContent.add(Box.createVerticalStrut(2));
        if (dq.questsMissing.isEmpty())
        {
            membersContent.add(clogNote(dq.questsComplete >= dq.questsTotal && dq.questsTotal > 0
                ? "All " + dq.questsTotal + " quests complete!"
                : dq.questsComplete + "/" + dq.questsTotal + " complete"));
        }
        else
        {
            membersContent.add(clogNote("Missing " + dq.questsMissing.size() + ":"));
            for (String q : dq.questsMissing)
            {
                JLabel l = new JLabel("• " + q);
                l.setFont(READABLE_FONT_SMALL);
                l.setForeground(new Color(190, 175, 130));
                l.setBorder(new EmptyBorder(1, 12, 1, 4));
                l.setAlignmentX(Component.LEFT_ALIGNMENT);
                membersContent.add(l);
            }
        }
        membersContent.revalidate();
        membersContent.repaint();
    }

    /** One diary region row: name + E/M/H/El tier ticks (green done, grey not). */
    private JPanel buildDiaryRegionRow(PlatformApiService.DiaryRegion r, boolean alt)
    {
        JPanel row = new JPanel(new BorderLayout())
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        row.setBackground(alt ? new Color(38, 38, 38) : new Color(33, 33, 33));
        row.setBorder(new EmptyBorder(3, 8, 3, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel name = new JLabel(r.name);
        name.setFont(READABLE_FONT_SMALL);
        name.setForeground(Color.WHITE);
        row.add(name, BorderLayout.WEST);

        JPanel ticks = new JPanel(new GridLayout(1, 4, 6, 0));
        ticks.setOpaque(false);
        String[] tiers = {"E", "M", "H", "El"};
        boolean[] done = {r.easy, r.medium, r.hard, r.elite};
        for (int i = 0; i < 4; i++)
        {
            JLabel t = new JLabel(tiers[i], SwingConstants.CENTER);
            t.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
            t.setForeground(done[i] ? new Color(105, 200, 105) : new Color(90, 90, 90));
            ticks.add(t);
        }
        row.add(ticks, BorderLayout.EAST);
        return row;
    }

    /** A collapsible section of PB rows: caret + colored title + count header, rows toggle below. */
    private JPanel buildCollapsiblePbSection(String title, Color accent, java.util.List<PlatformApiService.PlayerPb> pbs)
    {
        JPanel card = new JPanel()
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(35, 35, 35));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent != null ? accent : new Color(110, 110, 110)),
            new EmptyBorder(3, 6, 3, 6)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(card.getBackground());
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        header.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        final JLabel caret = new JLabel("+");
        caret.setFont(READABLE_FONT.deriveFont(Font.BOLD, 14f));
        caret.setForeground(new Color(150, 150, 150));
        caret.setPreferredSize(new Dimension(11, 16));
        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 1));
        left.setBackground(card.getBackground());
        left.add(caret);
        JLabel name = new JLabel(title);
        name.setFont(READABLE_FONT.deriveFont(Font.BOLD, 12f));
        name.setForeground(accent != null ? accent : Color.WHITE);
        left.add(name);
        header.add(left, BorderLayout.WEST);

        JLabel count = new JLabel(pbs.size() + (pbs.size() == 1 ? " time" : " times"));
        count.setFont(READABLE_FONT_SMALL);
        count.setForeground(new Color(130, 130, 130));
        count.setBorder(new EmptyBorder(0, 0, 0, 4));
        header.add(count, BorderLayout.EAST);
        card.add(header);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(card.getBackground());
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setVisible(false);
        for (PlatformApiService.PlayerPb pb : pbs)
        {
            body.add(buildPbRow(pb, BossCategory.fromKey(pb.bossKey)));
            body.add(Box.createVerticalStrut(2));
        }
        card.add(body);

        java.awt.event.MouseAdapter toggle = new java.awt.event.MouseAdapter()
        {
            @Override public void mousePressed(java.awt.event.MouseEvent ev)
            {
                boolean show = !body.isVisible();
                body.setVisible(show);
                caret.setText(show ? "-" : "+");
                card.revalidate(); card.repaint();
                membersContent.revalidate(); membersContent.repaint();
            }
        };
        header.addMouseListener(toggle);
        left.addMouseListener(toggle);
        name.addMouseListener(toggle);
        return card;
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
        // Height tracks content - the team-roster line wraps and must never clip.
        JPanel row = new JPanel(new BorderLayout(6, 0))
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
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
            JLabel team = new JLabel("<html>" + escapeHtml(pb.teamMembers) + "</html>");
            team.setFont(READABLE_FONT_SMALL.deriveFont(Font.ITALIC));
            team.setForeground(new Color(140, 140, 140));
            team.setAlignmentX(Component.LEFT_ALIGNMENT);
            stacked.add(name);
            stacked.add(team);
            row.add(stacked, BorderLayout.CENTER);
        }
        else
        {
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
        JPanel row = new JPanel(new BorderLayout(6, 0))
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        row.setBackground(new Color(35, 35, 35));
        row.setBorder(new EmptyBorder(5, 8, 5, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Item icon (by id from the server, or resolved from the name).
        int iconId = d.itemId > 0 ? d.itemId : resolveItemId(d.itemName);
        if (iconId > 0 && itemManager != null)
        {
            JLabel icon = new JLabel();
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setPreferredSize(new Dimension(36, 32));
            AsyncBufferedImage img = itemManager.getImage(iconId);
            icon.setIcon(new ImageIcon(img));
            img.onLoaded(() -> { icon.setIcon(new ImageIcon(img)); icon.revalidate(); icon.repaint(); });
            row.add(icon, BorderLayout.WEST);
        }

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
            JLabel from = new JLabel("from " + d.monsterName
                + (d.killCount > 0 ? " (" + String.format("%,d", d.killCount) + " KC)" : ""));
            from.setFont(READABLE_FONT_SMALL);
            from.setForeground(new Color(140, 140, 140));
            from.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(from);
        }
        row.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(row.getBackground());
        if (d.value > 0)
        {
            JLabel val = new JLabel(formatXp(d.value));
            val.setFont(READABLE_FONT_SMALL);
            val.setForeground(ACCENT_GOLD);
            val.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(val);
        }
        if (d.points > 0)
        {
            JLabel pts = new JLabel("+" + d.points + " pts");
            pts.setFont(READABLE_FONT_SMALL);
            pts.setForeground(new Color(76, 175, 80));
            pts.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(pts);
        }
        row.add(right, BorderLayout.EAST);
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
                    : "Checked locally — nothing about your items is sent. Open your bank and collection log once, then ↻.";
                JLabel note = new JLabel("<html>" + noteText + "</html>");
                note.setFont(READABLE_FONT_SMALL);
                note.setForeground(clogOnly ? new Color(190, 175, 130) : new Color(130, 130, 130));
                note.setAlignmentX(Component.LEFT_ALIGNMENT);
                note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
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

                // Ranks already held (via Discord rank + prerequisite expansion) collapse
                // into one section so the list leads with what's still earnable.
                java.util.List<RankSystem.RankStatus> activeRanks = new java.util.ArrayList<>();
                java.util.List<RankSystem.RankStatus> heldRanks = new java.util.ArrayList<>();
                for (RankSystem.RankStatus rs : results)
                {
                    (rs.granted ? heldRanks : activeRanks).add(rs);
                }
                for (RankSystem.RankStatus rs : activeRanks)
                {
                    ranksContent.add(buildRankCard(rs));
                    ranksContent.add(Box.createVerticalStrut(6));
                }
                if (!heldRanks.isEmpty())
                {
                    ranksContent.add(buildHeldSection(heldRanks));
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

    /** One collapsed card for every rank the member already holds. */
    private JPanel buildHeldSection(java.util.List<RankSystem.RankStatus> heldRanks)
    {
        JPanel card = new JPanel()
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(35, 35, 35));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_GOLD),
            new EmptyBorder(4, 6, 4, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(new Color(35, 35, 35));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        header.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        final JLabel caret = new JLabel(heldRanksExpanded ? "-" : "+");
        caret.setFont(READABLE_FONT.deriveFont(Font.BOLD, 14f));
        caret.setForeground(new Color(150, 150, 150));
        caret.setPreferredSize(new Dimension(11, 16));
        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 3));
        left.setBackground(new Color(35, 35, 35));
        left.add(caret);
        JLabel name = new JLabel("Held ranks (" + heldRanks.size() + ")");
        name.setFont(READABLE_FONT.deriveFont(Font.BOLD, 13f));
        name.setForeground(ACCENT_GOLD);
        left.add(name);
        header.add(left, BorderLayout.WEST);
        card.add(header);

        final JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(new Color(35, 35, 35));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setBorder(new EmptyBorder(2, 16, 2, 0));
        for (RankSystem.RankStatus rs : heldRanks)
        {
            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 1));
            row.setBackground(new Color(35, 35, 35));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(rankSpriteIcon(rs.rank.id));
            JLabel rn = new JLabel(rs.rank.name);
            rn.setFont(READABLE_FONT_SMALL);
            rn.setForeground(new Color(190, 175, 130));
            row.add(rn);
            body.add(row);
        }
        body.setVisible(heldRanksExpanded);
        card.add(body);

        header.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override public void mouseClicked(java.awt.event.MouseEvent e)
            {
                heldRanksExpanded = !heldRanksExpanded;
                caret.setText(heldRanksExpanded ? "-" : "+");
                body.setVisible(heldRanksExpanded);
                card.revalidate();
                ranksContent.revalidate();
                ranksContent.repaint();
            }
        });
        return card;
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
        name.setForeground(rs.granted ? ACCENT_GOLD : (rs.eligible ? new Color(90, 200, 90) : Color.WHITE));
        left.add(name);
        header.add(left, BorderLayout.WEST);

        int groupsMet = 0;
        for (RankSystem.GroupStatus gs : rs.groups) if (gs.satisfied()) groupsMet++;
        String badgeText = rs.granted ? "Held" : (rs.eligible ? "QUALIFIED" : groupsMet + "/" + rs.groups.size());
        Color badgeColor = rs.granted ? ACCENT_GOLD
            : (rs.eligible ? new Color(90, 200, 90) : new Color(190, 160, 90));
        JLabel badge = new JLabel(badgeText);
        badge.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
        badge.setForeground(badgeColor);
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
        // Held via the member's Discord rank — no need to show requirements or a request button.
        if (rs.granted)
        {
            JLabel held = new JLabel("<html>You already hold this rank (from your Discord rank).</html>");
            held.setFont(READABLE_FONT_SMALL);
            held.setForeground(new Color(190, 175, 130));
            held.setAlignmentX(Component.LEFT_ALIGNMENT);
            held.setBorder(new EmptyBorder(4, 2, 2, 0));
            details.add(held);
            return;
        }

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
            JLabel g = new JLabel("<html>" + escapeHtml(gs.group.label) + "   <b>" + gs.met + " / " + gs.group.need + "</b></html>");
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
        JPanel row = new JPanel(new BorderLayout(5, 0))
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        row.setBackground(new Color(35, 35, 35));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(1, 2, 1, 2));

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

        JLabel l = new JLabel("<html>" + escapeHtml(r.label) + "</html>");
        l.setFont(READABLE_FONT_SMALL);
        l.setForeground(r.met ? new Color(200, 210, 200) : new Color(140, 140, 140));
        l.setToolTipText(r.label);
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
        label.setToolTipText(it.name + (it.owned
            ? (it.quantity > 1 ? " ×" + it.quantity : "")
            : " — missing"));
        if (itemManager != null && it.itemId > 0)
        {
            // Render the obtained COUNT on the icon (in-game stack-number style) when > 1,
            // so "10 abyssal whips" reads straight off the grid like the real collection log.
            AsyncBufferedImage img = itemManager.getImage(it.itemId, it.quantity, it.quantity > 1);
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
    public void setOnLoadEvent(Runnable cb) { this.onLoadEvent = cb; }

    // ══════════════════════════════════════════
    // Event Tab (live draft)
    // ══════════════════════════════════════════

    private JComponent buildEventTab()
    {
        ScrollableColumn wrapper = new ScrollableColumn();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.setBorder(new EmptyBorder(6, 4, 6, 4));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel title = new JLabel("Clan Event");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        titleRow.add(title, BorderLayout.WEST);
        JButton refresh = new JButton("\u21bb");
        refresh.setMargin(new Insets(0, 6, 0, 6));
        refresh.setFocusPainted(false);
        refresh.addActionListener(e -> { if (onLoadEvent != null) onLoadEvent.run(); });
        titleRow.add(refresh, BorderLayout.EAST);
        wrapper.add(titleRow);
        wrapper.add(Box.createVerticalStrut(6));

        eventContent.setLayout(new BoxLayout(eventContent, BoxLayout.Y_AXIS));
        eventContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        eventContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventContent.add(eventNote("Open this tab during a clan event to see your team."));
        wrapper.add(eventContent);

        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return scroll;
    }

    // Skill metric -> RuneLite sprite (compile-time constants; no magic numbers).
    private static final java.util.Map<String, Integer> SKILL_SPRITES = new java.util.HashMap<>();
    static
    {
        SKILL_SPRITES.put("attack", net.runelite.api.SpriteID.SKILL_ATTACK);
        SKILL_SPRITES.put("strength", net.runelite.api.SpriteID.SKILL_STRENGTH);
        SKILL_SPRITES.put("defence", net.runelite.api.SpriteID.SKILL_DEFENCE);
        SKILL_SPRITES.put("ranged", net.runelite.api.SpriteID.SKILL_RANGED);
        SKILL_SPRITES.put("prayer", net.runelite.api.SpriteID.SKILL_PRAYER);
        SKILL_SPRITES.put("magic", net.runelite.api.SpriteID.SKILL_MAGIC);
        SKILL_SPRITES.put("hitpoints", net.runelite.api.SpriteID.SKILL_HITPOINTS);
        SKILL_SPRITES.put("agility", net.runelite.api.SpriteID.SKILL_AGILITY);
        SKILL_SPRITES.put("herblore", net.runelite.api.SpriteID.SKILL_HERBLORE);
        SKILL_SPRITES.put("thieving", net.runelite.api.SpriteID.SKILL_THIEVING);
        SKILL_SPRITES.put("crafting", net.runelite.api.SpriteID.SKILL_CRAFTING);
        SKILL_SPRITES.put("fletching", net.runelite.api.SpriteID.SKILL_FLETCHING);
        SKILL_SPRITES.put("mining", net.runelite.api.SpriteID.SKILL_MINING);
        SKILL_SPRITES.put("smithing", net.runelite.api.SpriteID.SKILL_SMITHING);
        SKILL_SPRITES.put("fishing", net.runelite.api.SpriteID.SKILL_FISHING);
        SKILL_SPRITES.put("cooking", net.runelite.api.SpriteID.SKILL_COOKING);
        SKILL_SPRITES.put("firemaking", net.runelite.api.SpriteID.SKILL_FIREMAKING);
        SKILL_SPRITES.put("woodcutting", net.runelite.api.SpriteID.SKILL_WOODCUTTING);
        SKILL_SPRITES.put("runecrafting", net.runelite.api.SpriteID.SKILL_RUNECRAFT);
        SKILL_SPRITES.put("runecraft", net.runelite.api.SpriteID.SKILL_RUNECRAFT);
        SKILL_SPRITES.put("slayer", net.runelite.api.SpriteID.SKILL_SLAYER);
        SKILL_SPRITES.put("farming", net.runelite.api.SpriteID.SKILL_FARMING);
        SKILL_SPRITES.put("construction", net.runelite.api.SpriteID.SKILL_CONSTRUCTION);
        SKILL_SPRITES.put("hunter", net.runelite.api.SpriteID.SKILL_HUNTER);
    }

    /** Icon for an event's metric: skill sprite, boss/clue item icon, or empty when unknown. */
    private JLabel metricIcon(String type, String metric, int size)
    {
        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(size, size));
        String m = metric != null ? metric.toLowerCase() : "";
        if (("skill".equals(type) || "gamer".equals(type)) && SKILL_SPRITES.containsKey(m))
        {
            if (spriteManager != null)
            {
                spriteManager.getSpriteAsync(SKILL_SPRITES.get(m), 0, img -> SwingUtilities.invokeLater(() ->
                    icon.setIcon(new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH)))));
            }
            return icon;
        }
        Integer itemId = EventMetrics.iconItemId(m);
        if (itemId != null && itemManager != null)
        {
            itemManager.getImage(itemId).addTo(icon);
        }
        return icon;
    }

    /** Every pending (scheduled) event as a simple dated list — the full calendar. */
    private void renderPendingList(com.google.gson.JsonArray pending)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(new EmptyBorder(4, 8, 6, 8));

        JLabel head = new JLabel("Upcoming events");
        head.setFont(FontManager.getRunescapeBoldFont());
        head.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(head);

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a");
        for (int i = 0; i < pending.size(); i++)
        {
            com.google.gson.JsonObject ev = pending.get(i).getAsJsonObject();
            String when;
            try
            {
                when = java.time.Instant.parse(ev.get("startTime").getAsString())
                    .atZone(java.time.ZoneId.of("America/New_York")).format(fmt) + " ET";
            }
            catch (Exception ex)
            {
                when = "?";
            }
            JPanel rowPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 1));
            rowPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            rowPanel.add(metricIcon(
                ev.has("type") ? ev.get("type").getAsString() : null,
                ev.has("metric") ? ev.get("metric").getAsString() : null, 16));
            JLabel row = new JLabel("<html><b>" + ev.get("displayName").getAsString() + "</b> \u2014 " + when + "</html>");
            row.setFont(READABLE_FONT_SMALL);
            row.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            rowPanel.add(row);
            card.add(rowPanel);
        }
        eventContent.add(card);
        eventContent.add(Box.createVerticalStrut(8));
    }

    /** Countdown to a scheduled event — a live ticking timer, shown from 7 days out. */
    private void renderCountdown(com.google.gson.JsonObject upcoming)
    {
        long startMs;
        try
        {
            startMs = java.time.Instant.parse(upcoming.get("startTime").getAsString()).toEpochMilli();
        }
        catch (Exception ex)
        {
            return;
        }
        // Only surface the countdown inside the final 7 days.
        if (startMs - System.currentTimeMillis() > 7L * 24 * 3600 * 1000)
        {
            return;
        }

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_GOLD),
            new EmptyBorder(8, 8, 8, 8)));

        JLabel tag = new JLabel("SCHEDULED");
        tag.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD));
        tag.setForeground(ACCENT_GOLD);
        tag.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(tag);

        JPanel titleRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(metricIcon(
            upcoming.has("type") ? upcoming.get("type").getAsString() : null,
            upcoming.has("metric") ? upcoming.get("metric").getAsString() : null, 20));
        JLabel title = new JLabel("<html><b>" + upcoming.get("displayName").getAsString() + "</b></html>");
        title.setForeground(Color.WHITE);
        titleRow.add(title);
        card.add(titleRow);

        JLabel timer = new JLabel();
        timer.setFont(READABLE_FONT.deriveFont(Font.BOLD, 15f));
        timer.setForeground(Color.WHITE);
        timer.setAlignmentX(Component.LEFT_ALIGNMENT);
        timer.setBorder(new EmptyBorder(4, 2, 0, 0));
        card.add(timer);

        Runnable tick = () ->
        {
            long left = startMs - System.currentTimeMillis();
            if (left <= 0)
            {
                timer.setText("Starting\u2026");
                if (countdownTicker != null) countdownTicker.stop();
                if (onLoadEvent != null) onLoadEvent.run(); // flip to the live race view
                return;
            }
            long d = left / 86_400_000L;
            long h = (left % 86_400_000L) / 3_600_000L;
            long m = (left % 3_600_000L) / 60_000L;
            long sec = (left % 60_000L) / 1000L;
            timer.setText(d > 0
                ? String.format("Starts in %dd %02d:%02d:%02d", d, h, m, sec)
                : String.format("Starts in %02d:%02d:%02d", h, m, sec));
        };
        tick.run();
        countdownTicker = new javax.swing.Timer(1000, ev -> tick.run());
        countdownTicker.start();

        eventContent.add(card);
        eventContent.add(Box.createVerticalStrut(8));
    }

    /** Boss/Skill-of-the-Week race card: standings with the local player highlighted. */
    private void renderRace(com.google.gson.JsonObject root, String localPlayerName)
    {
        com.google.gson.JsonObject event = root.getAsJsonObject("event");
        com.google.gson.JsonArray board = root.getAsJsonArray("leaderboard");
        String type = event.get("type").getAsString();
        String me = localPlayerName != null ? localPlayerName.toLowerCase() : "";

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_GOLD),
            new EmptyBorder(6, 8, 6, 8)));

        JPanel titleRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(metricIcon(type, event.has("metric") ? event.get("metric").getAsString() : null, 20));
        JLabel title = new JLabel("<html><b>" + event.get("displayName").getAsString() + "</b></html>");
        title.setForeground(ACCENT_GOLD);
        titleRow.add(title);
        card.add(titleRow);

        try
        {
            long endMs = java.time.Instant.parse(event.get("endTime").getAsString()).toEpochMilli();
            long left = endMs - System.currentTimeMillis();
            String when = left <= 0 ? "Ended"
                : left > 86_400_000L ? "Ends in " + (left / 86_400_000L) + "d " + (left % 86_400_000L) / 3_600_000L + "h"
                : "Ends in " + (left / 3_600_000L) + "h " + (left % 3_600_000L) / 60_000L + "m";
            JLabel ends = new JLabel(when);
            ends.setFont(READABLE_FONT_SMALL);
            ends.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            ends.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(ends);
        }
        catch (Exception ignored) { }
        card.add(Box.createVerticalStrut(4));

        int shown = 0;
        int myRank = -1;
        String myScore = null;
        for (int i = 0; i < board.size(); i++)
        {
            com.google.gson.JsonObject row = board.get(i).getAsJsonObject();
            String rsn = row.get("rsn").getAsString();
            long score = row.get("score").getAsLong();
            boolean isMe = rsn.toLowerCase().equals(me);
            if (isMe) { myRank = i + 1; myScore = raceScore(type, score); }
            if (i < 10)
            {
                // HTML with a fixed width so a long RSN wraps to a second line instead of forcing
                // the panel (and the client) wider. STANDINGS_TEXT_WIDTH \u2248 panel minus borders/pad.
                JLabel line = new JLabel("<html><div style='width:" + STANDINGS_TEXT_WIDTH + "px'>"
                    + (i + 1) + ". " + rsn + " \u2014 " + raceScore(type, score) + "</div></html>");
                line.setFont(isMe ? READABLE_FONT.deriveFont(Font.BOLD, 12f) : READABLE_FONT_SMALL);
                line.setForeground(isMe ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                line.setBorder(new EmptyBorder(1, 4, 0, 0));
                card.add(line);
                shown++;
            }
        }
        if (shown == 0)
        {
            card.add(eventNote("No scores yet \u2014 get out there!"));
        }
        if (myRank > 10)
        {
            JLabel mine = new JLabel("<html><div style='width:" + STANDINGS_TEXT_WIDTH + "px'>\u2026 "
                + myRank + ". " + localPlayerName + " \u2014 " + myScore + "</div></html>");
            mine.setFont(READABLE_FONT.deriveFont(Font.BOLD, 12f));
            mine.setForeground(Color.WHITE);
            mine.setAlignmentX(Component.LEFT_ALIGNMENT);
            mine.setBorder(new EmptyBorder(1, 4, 0, 0));
            card.add(mine);
        }

        eventContent.add(card);
        eventContent.add(Box.createVerticalStrut(8));
    }

    private String raceScore(String type, long score)
    {
        if ("skill".equals(type))
        {
            return score >= 1_000_000 ? String.format("%.1fM xp", score / 1_000_000.0)
                : String.format("%,dk xp", score / 1000);
        }
        return String.format("%,d kc", score);
    }

    private JLabel eventNote(String text)
    {
        JLabel l = new JLabel("<html><i>" + text + "</i></html>");
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setBorder(new EmptyBorder(4, 2, 4, 2));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Select the Event tab once per session (active race auto-show on login). */
    public void showEventTabOnce()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (eventTabAutoShown) return;
            eventTabAutoShown = true;
            for (int i = 0; i < tabbedPane.getTabCount(); i++)
            {
                if ("Event".equals(tabbedPane.getTitleAt(i)))
                {
                    tabbedPane.setSelectedIndex(i);
                    break;
                }
            }
        });
    }

    /** Re-render the Event tab: active race first (if any), then draft state. */
    public void updateEvent(com.google.gson.JsonObject draft, com.google.gson.JsonObject race, String localPlayerName)
    {
        SwingUtilities.invokeLater(() ->
        {
            eventContent.removeAll();
            if (countdownTicker != null)
            {
                countdownTicker.stop();
                countdownTicker = null;
            }
            boolean renderedRace = false;
            if (race != null)
            {
                if (race.has("event") && !race.get("event").isJsonNull())
                {
                    renderRace(race, localPlayerName);
                    renderedRace = true;
                }
                boolean hasLive = race.has("event") && !race.get("event").isJsonNull();
                if (race.has("upcomingList") && race.get("upcomingList").isJsonArray()
                    && race.getAsJsonArray("upcomingList").size() > 0)
                {
                    com.google.gson.JsonArray pending = race.getAsJsonArray("upcomingList");
                    // Soonest event gets the ticking countdown; with no live race, ALL
                    // pending events list below it so members see the full calendar.
                    renderCountdown(pending.get(0).getAsJsonObject());
                    if (!hasLive && pending.size() > 0)
                    {
                        renderPendingList(pending);
                    }
                    renderedRace = true;
                }
                else if (race.has("upcoming") && !race.get("upcoming").isJsonNull())
                {
                    renderCountdown(race.getAsJsonObject("upcoming"));
                    renderedRace = true;
                }
            }
            // A COMPLETED draft is history, not an event — hide it entirely. The section
            // comes back on its own the moment a new draft is created (setup/live).
            boolean draftDone = draft != null
                && "complete".equals(draft.getAsJsonObject("event").get("status").getAsString());
            if (draft == null || draftDone)
            {
                if (!renderedRace)
                {
                    eventContent.add(eventNote("No clan event is running right now."));
                }
                eventContent.revalidate();
                eventContent.repaint();
                return;
            }

            com.google.gson.JsonObject event = draft.getAsJsonObject("event");
            com.google.gson.JsonArray teams = draft.getAsJsonArray("teams");
            com.google.gson.JsonArray pool = draft.getAsJsonArray("pool");
            com.google.gson.JsonArray picks = draft.getAsJsonArray("picks");

            java.util.Map<String, String> rsnByPool = new java.util.HashMap<>();
            java.util.Map<String, Double> ehbByPool = new java.util.HashMap<>();
            for (com.google.gson.JsonElement el : pool)
            {
                com.google.gson.JsonObject p = el.getAsJsonObject();
                String pid = p.get("id").getAsString();
                rsnByPool.put(pid, p.get("rsn").getAsString());
                if (p.has("ehb") && !p.get("ehb").isJsonNull())
                {
                    try { ehbByPool.put(pid, Double.parseDouble(p.get("ehb").getAsString())); }
                    catch (NumberFormatException ignored) { }
                }
            }
            java.util.Map<String, java.util.List<String>> rosterByTeam = new java.util.LinkedHashMap<>();
            java.util.Map<String, String> teamOfPlayer = new java.util.HashMap<>();
            for (com.google.gson.JsonElement el : picks)
            {
                com.google.gson.JsonObject pk = el.getAsJsonObject();
                String rsn = rsnByPool.get(pk.get("poolId").getAsString());
                if (rsn == null) continue;
                rosterByTeam.computeIfAbsent(pk.get("teamId").getAsString(), k -> new java.util.ArrayList<>()).add(rsn);
                teamOfPlayer.put(rsn.toLowerCase(), pk.get("teamId").getAsString());
            }

            String me = localPlayerName != null ? localPlayerName.toLowerCase() : "";
            String myTeamId = teamOfPlayer.get(me);
            // Captains are on a team without being drafted
            if (myTeamId == null)
            {
                for (com.google.gson.JsonElement el : teams)
                {
                    com.google.gson.JsonObject t = el.getAsJsonObject();
                    String c1 = t.get("captain1").getAsString().toLowerCase();
                    String c2 = t.has("captain2") && !t.get("captain2").isJsonNull() ? t.get("captain2").getAsString().toLowerCase() : "";
                    if (me.equals(c1) || me.equals(c2)) { myTeamId = t.get("id").getAsString(); break; }
                }
            }

            String status = event.get("status").getAsString();
            JLabel name = new JLabel("<html><b>" + event.get("name").getAsString() + "</b></html>");
            name.setForeground(Color.WHITE);
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            name.setBorder(new EmptyBorder(0, 2, 2, 2));
            eventContent.add(name);
            eventContent.add(eventNote("live".equals(status) ? "Draft in progress \u2014 " + picks.size() + " picks made"
                : "setup".equals(status) ? "Draft has not started yet" : "Draft complete"));

            if ("live".equals(status) && teams.size() > 0 && picks.size() < pool.size())
            {
                // Snake order: who is on the clock right now
                int n = teams.size();
                int round = picks.size() / n;
                int idx = picks.size() % n;
                com.google.gson.JsonObject clockTeam = (round % 2 == 0 ? teams.get(idx) : teams.get(n - 1 - idx)).getAsJsonObject();
                JLabel clock = new JLabel("<html><b>\u23f1 On the clock: " + clockTeam.get("name").getAsString() + "</b></html>");
                try { clock.setForeground(Color.decode(clockTeam.get("color").getAsString())); }
                catch (NumberFormatException ex) { clock.setForeground(Color.WHITE); }
                clock.setAlignmentX(Component.LEFT_ALIGNMENT);
                clock.setBorder(new EmptyBorder(2, 2, 2, 2));
                eventContent.add(clock);

                // Last few picks, most recent first
                java.util.Map<String, String> codeByTeam = new java.util.HashMap<>();
                for (com.google.gson.JsonElement el : teams)
                {
                    com.google.gson.JsonObject t = el.getAsJsonObject();
                    codeByTeam.put(t.get("id").getAsString(),
                        t.has("code") && !t.get("code").isJsonNull() ? t.get("code").getAsString() : t.get("name").getAsString());
                }
                for (int i = picks.size() - 1; i >= Math.max(0, picks.size() - 3); i--)
                {
                    com.google.gson.JsonObject pk = picks.get(i).getAsJsonObject();
                    String rsn = rsnByPool.get(pk.get("poolId").getAsString());
                    if (rsn == null) continue;
                    eventContent.add(eventNote("#" + pk.get("pickNumber").getAsInt() + " " + rsn
                        + " \u2192 " + codeByTeam.get(pk.get("teamId").getAsString())));
                }
            }
            eventContent.add(Box.createVerticalStrut(6));

            for (com.google.gson.JsonElement el : teams)
            {
                com.google.gson.JsonObject t = el.getAsJsonObject();
                String tid = t.get("id").getAsString();
                boolean mine = tid.equals(myTeamId);
                // During a live draft show only YOUR team in detail; show all once complete.
                if (!mine && myTeamId != null && !"complete".equals(status)) continue;

                Color teamColor;
                try { teamColor = Color.decode(t.get("color").getAsString()); }
                catch (NumberFormatException ex) { teamColor = ColorScheme.BRAND_ORANGE; }

                JPanel card = new JPanel();
                card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
                card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, teamColor),
                    new EmptyBorder(6, 8, 6, 8)));

                JLabel tn = new JLabel("<html><b>" + t.get("name").getAsString() + (mine ? " \u2b50" : "") + "</b></html>");
                tn.setForeground(teamColor);
                tn.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(tn);
                String caps = t.get("captain1").getAsString()
                    + (t.has("captain2") && !t.get("captain2").isJsonNull() ? " \u00b7 " + t.get("captain2").getAsString() : "");
                JLabel cl = new JLabel("Captains: " + caps);
                cl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                cl.setFont(FontManager.getRunescapeSmallFont());
                cl.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(cl);

                double teamEhb = 0;
                for (com.google.gson.JsonElement pel : picks)
                {
                    com.google.gson.JsonObject pk = pel.getAsJsonObject();
                    if (tid.equals(pk.get("teamId").getAsString()))
                    {
                        Double v = ehbByPool.get(pk.get("poolId").getAsString());
                        if (v != null) teamEhb += v;
                    }
                }
                if (teamEhb > 0)
                {
                    JLabel ehbLabel = new JLabel(String.format("Team EHB: %.0f", teamEhb));
                    ehbLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    ehbLabel.setFont(FontManager.getRunescapeSmallFont());
                    ehbLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    card.add(ehbLabel);
                }

                java.util.List<String> roster = rosterByTeam.getOrDefault(tid, java.util.Collections.emptyList());
                int i = 1;
                for (String rsn : roster)
                {
                    JLabel r = new JLabel(i++ + ". " + rsn);
                    r.setForeground(rsn.toLowerCase().equals(me) ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                    if (rsn.toLowerCase().equals(me)) r.setFont(FontManager.getRunescapeBoldFont());
                    r.setAlignmentX(Component.LEFT_ALIGNMENT);
                    r.setBorder(new EmptyBorder(1, 6, 0, 0));
                    card.add(r);
                }
                if (roster.isEmpty()) card.add(eventNote("No picks yet."));

                eventContent.add(card);
                eventContent.add(Box.createVerticalStrut(6));
            }

            if (myTeamId == null)
            {
                int remaining = pool.size() - picks.size();
                eventContent.add(eventNote(rsnInPool(pool, me)
                    ? "You haven't been drafted yet \u2014 " + remaining + " players still in the jar."
                    : "You aren't in this event's player pool."));
            }

            eventContent.revalidate();
            eventContent.repaint();
        });
    }

    private boolean rsnInPool(com.google.gson.JsonArray pool, String me)
    {
        for (com.google.gson.JsonElement el : pool)
        {
            if (el.getAsJsonObject().get("rsn").getAsString().toLowerCase().equals(me)) return true;
        }
        return false;
    }

    public void setOnSelectMember(java.util.function.Consumer<String> cb) { this.onSelectMember = cb; }
    public void setPlatformAdmin(boolean a) { this.platformAdmin = a; }
    public void setOnSetRankOverride(java.util.function.Consumer<Object[]> cb) { this.onSetRankOverride = cb; }
    public void setOnClearRankOverride(java.util.function.Consumer<String> cb) { this.onClearRankOverride = cb; }

    private JPanel createNavCard(String name, String description, Color accentColor, String tabName)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0))
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        card.setBackground(new Color(40, 40, 40));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
            new EmptyBorder(10, 12, 10, 12)
        ));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
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
                // Speed Times / Drops / XP now live inside the Leaderboards tab — open it and
                // switch the sub-view instead of looking for a top-level tab that no longer exists.
                if (leaderboardsSelector != null
                    && ("Speed Times".equals(tabName) || "Drops".equals(tabName) || "XP".equals(tabName)))
                {
                    int lb = tabbedPane.indexOfTab("Leaderboards");
                    if (lb >= 0) tabbedPane.setSelectedIndex(lb);
                    leaderboardsSelector.setSelectedItem(tabName);
                    return;
                }
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
                    JPanel card = new JPanel(new BorderLayout(8, 0))
                    {
                        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                    };
                    card.setBackground(new Color(30, 28, 15));
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_GOLD_DIM),
                        new EmptyBorder(8, 10, 8, 10)
                    ));
                    card.setAlignmentX(Component.CENTER_ALIGNMENT);

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
                    // Height follows content \u2014 the description wraps to two lines when needed.
                    JPanel row = new JPanel(new BorderLayout(6, 0))
                    {
                        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                    };
                    row.setBackground(idx++ % 2 == 0 ? ColorScheme.DARK_GRAY_COLOR : new Color(35, 35, 35));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setBorder(new EmptyBorder(3, 6, 3, 6));

                    String badge;   // small text badge (NO unicode glyphs \u2014 the RL panel font renders them as boxes)
                    String color;
                    String desc;
                    String detail = entry.detail == null ? "" : entry.detail;
                    boolean clanBest = "pb".equals(entry.type) && detail.contains("new clan record");
                    switch (entry.type)
                    {
                        case "join": badge = "+"; color = "#4CAF50"; desc = entry.rsn + " joined the clan"; break;
                        case "leave": badge = "-"; color = "#E05B5B"; desc = entry.rsn + " left the clan"; break;
                        case "pb":
                            badge = "PB"; color = "#5B9BD5";
                            // Lead with the time; flag records loudly (the row also goes gold).
                            String time = detail.replace("\u00b7 new clan record", "").replace("new clan record", "").trim();
                            desc = entry.rsn + ": " + entry.title + (time.isEmpty() ? "" : " - " + time) + (clanBest ? "  CLAN BEST" : "");
                            break;
                        case "drop": badge = "$"; color = "#FFD700"; desc = entry.rsn + ": " + entry.title
                            + (entry.value > 0 ? " (" + formatXp(entry.value) + " gp)" : "") + (detail.isEmpty() ? "" : " " + detail); break;
                        case "clog": badge = "LOG"; color = "#C77DFF"; desc = entry.rsn + ": " + entry.title + (detail.isEmpty() ? "" : " (" + detail + ")"); break;
                        case "ca": badge = "CA"; color = "#DC7A3C"; desc = entry.rsn + ": " + entry.title; break;
                        default: badge = "*"; color = "#888888"; desc = entry.rsn + " " + entry.title;
                    }

                    // Left: the item's icon for drops/clogs, the boss icon for PBs;
                    // a small colored text badge otherwise.
                    JComponent leftCol;
                    ImageIcon pbBossIcon = "pb".equals(entry.type) ? bossIconForLabel(entry.title) : null;
                    if (("drop".equals(entry.type) || "clog".equals(entry.type)) && entry.itemId > 0 && itemManager != null)
                    {
                        JLabel iconLabel = new JLabel();
                        iconLabel.setPreferredSize(new Dimension(26, 24));
                        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
                        AsyncBufferedImage img = itemManager.getImage(entry.itemId);
                        iconLabel.setIcon(new ImageIcon(img));
                        img.onLoaded(() -> { iconLabel.setIcon(new ImageIcon(img)); iconLabel.revalidate(); iconLabel.repaint(); });
                        leftCol = iconLabel;
                    }
                    else if (pbBossIcon != null)
                    {
                        JLabel iconLabel = new JLabel(pbBossIcon);
                        iconLabel.setPreferredSize(new Dimension(26, 24));
                        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
                        leftCol = iconLabel;
                    }
                    else
                    {
                        JLabel badgeLabel = new JLabel(badge);
                        badgeLabel.setFont(READABLE_FONT_SMALL.deriveFont(Font.BOLD, 10f));
                        badgeLabel.setPreferredSize(new Dimension(26, 24));
                        badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
                        try { badgeLabel.setForeground(Color.decode(color)); }
                        catch (Exception ignored) { badgeLabel.setForeground(new Color(150, 150, 150)); }
                        leftCol = badgeLabel;
                    }
                    row.add(leftCol, BorderLayout.WEST);

                    // Description wraps (html) instead of hard-truncating names. Clan bests go gold.
                    // The leading RSN gets the member's clan rank appended in dim gold.
                    String descHtml = desc.startsWith(entry.rsn)
                        ? nameWithRankHtml(entry.rsn) + escapeHtml(desc.substring(entry.rsn.length()))
                        : escapeHtml(desc);
                    JLabel label = new JLabel("<html>" + descHtml + "</html>");
                    label.setFont(clanBest ? READABLE_FONT_SMALL.deriveFont(Font.BOLD) : READABLE_FONT_SMALL);
                    label.setForeground(clanBest ? ACCENT_GOLD : new Color(200, 200, 200));
                    row.add(label, BorderLayout.CENTER);
                    if (clanBest)
                    {
                        row.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 2, 0, 0, ACCENT_GOLD),
                            new EmptyBorder(3, 4, 3, 6)));
                    }

                    // Time-ago on the right, top-aligned beside wrapped text.
                    JLabel timeLabel = new JLabel(formatTimeAgo(entry.createdAt));
                    timeLabel.setFont(READABLE_FONT_SMALL);
                    timeLabel.setForeground(new Color(110, 110, 110));
                    timeLabel.setVerticalAlignment(SwingConstants.TOP);
                    row.add(timeLabel, BorderLayout.EAST);

                    activityPanel.add(row);
                    activityPanel.add(Box.createVerticalStrut(1));
                }
            }

            activityPanel.revalidate();
            activityPanel.repaint();
        });
    }


    /**
     * Resolve a PB activity title like "Zulrah (Solo)" or "The Nightmare (5-Man)" to its boss
     * group's icon. Matches the base name against BossCategory display names (ignoring a leading
     * "The "), since activity events carry the display label, not the boss key.
     */
    // Server activity labels that abbreviate the boss name — mapped straight to the icon group.
    private static final java.util.Map<String, String> LABEL_GROUP_ALIASES = new java.util.HashMap<>();
    static
    {
        LABEL_GROUP_ALIASES.put("tob: entry mode", "tob_entry");
        LABEL_GROUP_ALIASES.put("tob: hard mode", "tob_hm");
        LABEL_GROUP_ALIASES.put("toa: entry mode", "toa_entry");
        LABEL_GROUP_ALIASES.put("toa: expert mode", "toa_expert");
        LABEL_GROUP_ALIASES.put("cm chambers of xeric", "cox_cm");
        LABEL_GROUP_ALIASES.put("phosani's nightmare", "phosanis");
        LABEL_GROUP_ALIASES.put("corrupted gauntlet", "gaunt_corrupted");
    }

    private ImageIcon bossIconForLabel(String title)
    {
        if (title == null || bossIcons.isEmpty()) return null;
        String base = title;
        int paren = base.lastIndexOf(" (");
        if (paren > 0) base = base.substring(0, paren);
        base = base.toLowerCase().replaceFirst("^the ", "").trim();
        // Older activity rows carry an unstripped size suffix ("Maggot King Solo") - drop it.
        base = base.replaceFirst(" (solo|duo|trio|group|mass)$", "").trim();
        String alias = LABEL_GROUP_ALIASES.get(base);
        if (alias != null)
        {
            ImageIcon aliased = bossIcons.get(alias);
            if (aliased != null) return aliased;
        }
        for (BossCategory cat : BossCategory.getAll())
        {
            String name = cat.getDisplayName().toLowerCase().replaceFirst("^the ", "").trim();
            if (name.equals(base))
            {
                ImageIcon icon = bossIcons.get(cat.getGroup());
                if (icon != null) return icon;
            }
        }
        return null;
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
        womModeCombo.addActionListener(e -> populateWomMetricCombo());
        populateWomMetricCombo();
        womMetricCombo.setFont(READABLE_FONT_SMALL);
        womMetricCombo.setRenderer(new SkillComboRenderer());

        row1.add(womModeCombo);
        row1.add(womMetricCombo);
        wrapper.add(row1);
        wrapper.add(Box.createVerticalStrut(4));

        // Row 2: Period + view (Gained / Records)
        JPanel row2 = new JPanel(new GridLayout(1, 2, 4, 0));
        row2.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        womPeriodCombo.setFont(READABLE_FONT_SMALL);
        womViewCombo.setFont(READABLE_FONT_SMALL);
        womViewCombo.setToolTipText("Gained = this period so far. Records = the most anyone has ever done in a single period.");
        row2.add(womPeriodCombo);
        row2.add(womViewCombo);

        // Only trigger fetch on explicit refresh click, not on every combo change
        wrapper.add(row2);
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

        String selected = (String) womMetricCombo.getSelectedItem();
        if (selected == null) return;
        String metric = "Boss KC".equals(womModeCombo.getSelectedItem())
            ? "boss:" + WOM_BOSS_METRICS.getOrDefault(selected, selected.toLowerCase())
            : selected.toLowerCase();
        String period = ((String) womPeriodCombo.getSelectedItem()).toLowerCase();
        // Records view rides the period string (":records" suffix) so the callback signature
        // stays a BiConsumer; the plugin splits it back apart. All-Time has no record window —
        // the server maps it to week — so pass week explicitly to keep the UI honest.
        if ("Records".equals(womViewCombo.getSelectedItem()))
        {
            if ("all-time".equals(period)) period = "week";
            period = period + ":records";
        }
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

    // rsn (lowercased) -> in-game clan rank title (Xerician, Senator, Maxed…), from the roster.
    // Shown beside player names across the panel feeds.
    private java.util.Map<String, String> rosterRanks = java.util.Collections.emptyMap();

    public void setRosterRanks(java.util.Map<String, String> ranks)
    {
        this.rosterRanks = ranks != null ? ranks : java.util.Collections.emptyMap();
    }

    /** The member's clan rank title, or null. Handles nbsp-vs-space RSN variants. */
    private String rankOf(String rsn)
    {
        if (rsn == null || rosterRanks.isEmpty()) return null;
        String key = rsn.replace(' ', ' ').trim().toLowerCase();
        String rank = rosterRanks.get(key);
        return rank != null && !rank.isEmpty() ? rank : null;
    }

    // Rank icon sprites exported to small PNGs so they can render INLINE beside names in html
    // labels (<img> needs a URL; Swing html can't reference in-memory images).
    private final java.util.Map<String, java.io.File> rankIconFiles = new java.util.concurrent.ConcurrentHashMap<>();

    /** file: URL for a rank's exported icon, or null — re-checked so a deleted file can never
     *  render as Swing's broken-image placeholder. */
    private String rankIconUrl(String rankId)
    {
        if (rankId == null) return null;
        java.io.File f = rankIconFiles.get(rankId);
        return f != null && f.exists() ? f.toURI().toString() : null;
    }

    /** Export every mapped clan-rank icon sprite to dir once and remember the file URLs. */
    public void exportRankIcons(java.io.File dir)
    {
        if (spriteManager == null) return;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        for (java.util.Map.Entry<String, Integer> e : RANK_ICON_SPRITE.entrySet())
        {
            String rankId = e.getKey();
            java.io.File f = new java.io.File(dir, rankId + ".png");
            if (f.exists()) { rankIconFiles.put(rankId, f); continue; }
            spriteManager.getSpriteAsync(e.getValue(), 0, img ->
            {
                try
                {
                    if (img != null && javax.imageio.ImageIO.write(img, "png", f))
                    {
                        rankIconFiles.put(rankId, f);
                    }
                }
                catch (Exception ignored) { /* icon just won't show */ }
            });
        }
    }

    /** HTML fragment "Name <rank icon>" — the member's clan-rank SYMBOL beside their name. */
    private String nameWithRankHtml(String rsn)
    {
        String esc = escapeHtml(rsn);
        String rank = rankOf(rsn);
        String url = rankIconUrl(rank);
        return url == null ? esc : esc + " <img src='" + url + "'>";
    }

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
            hiscoreTimesPage = 0; // new boss selection starts at page 1
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

    private int hiscoreTimesPage = 0;
    private static final int TIMES_PAGE_SIZE = 10;

    /** Re-render the cached boss leaderboard, filtered by the player query, in pages of 10 —
     *  so members can flip through and find where they place. */
    private void renderTimesFiltered()
    {
        if (lastTimesEntries == null) return; // not a boss-leaderboard view (e.g. recent overview)

        hiscoreTimesPanel.removeAll();
        String q = playerQuery();
        List<HiscoreEntry> filtered = new java.util.ArrayList<>();
        for (HiscoreEntry entry : lastTimesEntries)
        {
            if (!q.isEmpty() && (entry.getRsns() == null || !entry.getRsns().toLowerCase().contains(q)))
            {
                continue;
            }
            filtered.add(entry);
        }

        if (filtered.isEmpty())
        {
            JLabel none = new JLabel(q.isEmpty() ? "No times recorded" : "No players match");
            none.setFont(READABLE_FONT_ITALIC);
            none.setForeground(new Color(80, 80, 80));
            none.setBorder(new EmptyBorder(6, 36, 6, 10));
            hiscoreTimesPanel.add(none);
        }
        else
        {
            int pages = (filtered.size() + TIMES_PAGE_SIZE - 1) / TIMES_PAGE_SIZE;
            if (hiscoreTimesPage >= pages) hiscoreTimesPage = pages - 1;
            if (hiscoreTimesPage < 0) hiscoreTimesPage = 0;
            int from = hiscoreTimesPage * TIMES_PAGE_SIZE;
            int to = Math.min(from + TIMES_PAGE_SIZE, filtered.size());
            for (int i = from; i < to; i++)
            {
                hiscoreTimesPanel.add(createTimeEntry(filtered.get(i), lastTimesAccent));
            }

            if (pages > 1)
            {
                JPanel nav = new JPanel(new BorderLayout());
                nav.setBackground(ColorScheme.DARK_GRAY_COLOR);
                nav.setAlignmentX(Component.LEFT_ALIGNMENT);
                nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
                nav.setBorder(new EmptyBorder(4, 8, 2, 8));

                JButton prev = new JButton("< Prev");
                prev.setFont(READABLE_FONT_SMALL);
                prev.setFocusPainted(false);
                prev.setMargin(new Insets(1, 6, 1, 6));
                prev.setEnabled(hiscoreTimesPage > 0);
                prev.addActionListener(e -> { hiscoreTimesPage--; renderTimesFiltered(); });
                nav.add(prev, BorderLayout.WEST);

                JLabel pageLbl = new JLabel("Page " + (hiscoreTimesPage + 1) + " / " + pages, SwingConstants.CENTER);
                pageLbl.setFont(READABLE_FONT_SMALL);
                pageLbl.setForeground(new Color(150, 150, 150));
                nav.add(pageLbl, BorderLayout.CENTER);

                JButton next = new JButton("Next >");
                next.setFont(READABLE_FONT_SMALL);
                next.setFocusPainted(false);
                next.setMargin(new Insets(1, 6, 1, 6));
                next.setEnabled(hiscoreTimesPage < pages - 1);
                next.addActionListener(e -> { hiscoreTimesPage++; renderTimesFiltered(); });
                nav.add(next, BorderLayout.EAST);

                hiscoreTimesPanel.add(nav);
            }
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

            // Center: rsn (muted) + clan rank in dim gold
            JLabel rsnLabel = new JLabel("<html>" + nameWithRankHtml(rsns) + "</html>");
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

    /**
     * Leaderboards hub: a selector at the top swaps between the Speed Times, Drops and XP views
     * (each unchanged) via a CardLayout — three former top-level tabs folded into one, matching the
     * website's condensed nav.
     */
    private JComponent buildLeaderboardsTab()
    {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel cards = new JPanel(new CardLayout());
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cards.add(buildHiscoresTab(), "Speed Times");
        cards.add(buildDropsTab(), "Drops");
        cards.add(buildWomTab(), "XP");

        JComboBox<String> selector = new JComboBox<>(new String[]{ "Drops", "Speed Times", "XP" });
        selector.setFocusable(false);
        selector.addActionListener(e ->
            ((CardLayout) cards.getLayout()).show(cards, (String) selector.getSelectedItem()));
        leaderboardsSelector = selector;
        // Default the hub to Drops (selecting it also shows the matching card via the listener).
        selector.setSelectedItem("Drops");

        JPanel selectorRow = new JPanel(new BorderLayout());
        selectorRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        selectorRow.setBorder(new EmptyBorder(6, 6, 4, 6));
        selectorRow.add(selector, BorderLayout.CENTER);

        container.add(selectorRow, BorderLayout.NORTH);
        container.add(cards, BorderLayout.CENTER);
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

        hiscoreModeCombo.addItem("Clan Only");
        hiscoreModeCombo.addItem("All PBs");
        hiscoreModeCombo.setSelectedItem("Clan Only"); // default: live times only; imports via All PBs
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

                JPanel row = new JPanel(new BorderLayout(6, 0))
                {
                    // Two text lines + padding — cap at the real preferred height so the
                    // second line (time — names) never clips.
                    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                };
                row.setBackground(count % 2 == 0 ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
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

                String bestRsns = best.getRsns() != null ? best.getRsns() : "";
                JLabel detailLabel = new JLabel("<html>" + escapeHtml(best.getFormattedTime()) + " — "
                    + (bestRsns.contains(",") ? escapeHtml(bestRsns) : nameWithRankHtml(bestRsns)) + "</html>");
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

        // Search-only browser: no category/sort combos, no default listing — results appear
        // below the search bar as you type (sorted by points, high first).

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
        playerDetailPanel.setBackground(new Color(30, 30, 30));
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

    private java.util.List<Map<String, Object>> lastDropsLbPlayers;
    private String lastDropsLbLocalName;
    private int dropsLbPage = 0;
    private static final int DROPS_LB_PAGE_SIZE = 10;

    public void updateDropsLeaderboard(List<Map<String, Object>> players, String localPlayerName)
    {
        SwingUtilities.invokeLater(() ->
        {
            // Cache for the page nav; keep the current page across periodic refreshes (clamped).
            lastDropsLbPlayers = players;
            lastDropsLbLocalName = localPlayerName;
            renderDropsLeaderboard();
        });
    }

    private void renderDropsLeaderboard()
    {
        List<Map<String, Object>> players = lastDropsLbPlayers;
        String localPlayerName = lastDropsLbLocalName;
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
                hdrName.setForeground(new Color(170, 170, 170));
                headerRow.add(hdrName, BorderLayout.WEST);

                headerRow.add(dropsStatsColumns("Pts", "Drops", "GP",
                    new Color(170, 170, 170), true, headerRow.getBackground()), BorderLayout.EAST);
                dropsLeaderboardPanel.add(headerRow);

                int pages = (players.size() + DROPS_LB_PAGE_SIZE - 1) / DROPS_LB_PAGE_SIZE;
                if (dropsLbPage >= pages) dropsLbPage = pages - 1;
                if (dropsLbPage < 0) dropsLbPage = 0;
                int from = dropsLbPage * DROPS_LB_PAGE_SIZE;
                int limit = Math.min(from + DROPS_LB_PAGE_SIZE, players.size());
                for (int i = from; i < limit; i++)
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

                    String rankUrl = rankIconUrl(rankOf(rsn));
                    JLabel nameLabel = new JLabel("<html>" + escapeHtml(prefix + truncate(rsn, 13))
                        + (rankUrl != null ? " <img src='" + rankUrl + "'>" : "") + "</html>");
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

                    // Fixed-width columns so Pts/Drops/GP line up on every row — a 0 in any
                    // column must not shift its neighbours.
                    row.add(dropsStatsColumns(String.valueOf(points), String.valueOf(drops), gpStr,
                        isMe ? new Color(76, 175, 80) : new Color(150, 150, 150), false,
                        row.getBackground()), BorderLayout.EAST);

                    dropsLeaderboardPanel.add(row);
                }

                int totalPages = (players.size() + DROPS_LB_PAGE_SIZE - 1) / DROPS_LB_PAGE_SIZE;
                if (totalPages > 1)
                {
                    JPanel nav = new JPanel(new BorderLayout());
                    nav.setBackground(ColorScheme.DARK_GRAY_COLOR);
                    nav.setAlignmentX(Component.LEFT_ALIGNMENT);
                    nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                    nav.setBorder(new EmptyBorder(3, 4, 1, 4));

                    JButton prev = new JButton("< Prev");
                    prev.setFont(READABLE_FONT_SMALL);
                    prev.setFocusPainted(false);
                    prev.setMargin(new Insets(1, 6, 1, 6));
                    prev.setEnabled(dropsLbPage > 0);
                    prev.addActionListener(e -> { dropsLbPage--; renderDropsLeaderboard(); });
                    nav.add(prev, BorderLayout.WEST);

                    JLabel pageLbl = new JLabel("Page " + (dropsLbPage + 1) + " / " + totalPages, SwingConstants.CENTER);
                    pageLbl.setFont(READABLE_FONT_SMALL);
                    pageLbl.setForeground(new Color(150, 150, 150));
                    nav.add(pageLbl, BorderLayout.CENTER);

                    JButton next = new JButton("Next >");
                    next.setFont(READABLE_FONT_SMALL);
                    next.setFocusPainted(false);
                    next.setMargin(new Insets(1, 6, 1, 6));
                    next.setEnabled(dropsLbPage < totalPages - 1);
                    next.addActionListener(e -> { dropsLbPage++; renderDropsLeaderboard(); });
                    nav.add(next, BorderLayout.EAST);

                    dropsLeaderboardPanel.add(nav);
                }
            }

            dropsLeaderboardPanel.revalidate();
            dropsLeaderboardPanel.repaint();
        }
    }

    /**
     * Three fixed-width right-aligned columns (Pts / Drops / GP) for the monthly drops
     * leaderboard. The SAME widths are used for the header and every row, so values always
     * line up regardless of digit count (a 0 must not collapse its column).
     */
    private JPanel dropsStatsColumns(String pts, String drops, String gp, Color fg, boolean bold, Color bg)
    {
        JPanel cols = new JPanel(new java.awt.GridBagLayout());
        cols.setBackground(bg);
        int[] widths = {32, 40, 46};
        String[] vals = {pts, drops, gp};
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.gridy = 0;
        for (int c = 0; c < 3; c++)
        {
            JLabel l = new JLabel(vals[c], SwingConstants.RIGHT);
            l.setFont(bold ? new Font("Segoe UI", Font.BOLD, 11) : READABLE_FONT_SMALL);
            l.setForeground(fg);
            java.awt.Dimension d = new java.awt.Dimension(widths[c], 14);
            l.setPreferredSize(d);
            l.setMinimumSize(d);
            gc.gridx = c;
            cols.add(l, gc);
        }
        return cols;
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
                    int dropItemId = ((Number) drop.getOrDefault("itemId", 0)).intValue();

                    JPanel row = new JPanel(new BorderLayout(4, 0))
                    {
                        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                    };
                    row.setBackground(i % 2 == 0
                        ? ColorScheme.DARK_GRAY_COLOR
                        : new Color(35, 35, 35));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setBorder(new EmptyBorder(2, 4, 2, 4));

                    // Item icon on the left (id from the server, name-resolve fallback).
                    int iconId = dropItemId > 0 ? dropItemId : resolveItemId(item);
                    if (iconId > 0 && itemManager != null)
                    {
                        JLabel icon = new JLabel();
                        icon.setPreferredSize(new Dimension(30, 28));
                        icon.setHorizontalAlignment(SwingConstants.CENTER);
                        AsyncBufferedImage img = itemManager.getImage(iconId);
                        icon.setIcon(new ImageIcon(img));
                        img.onLoaded(() -> { icon.setIcon(new ImageIcon(img)); icon.revalidate(); icon.repaint(); });
                        row.add(icon, BorderLayout.WEST);
                    }

                    // Center: item name + "player Rank — boss"
                    JPanel leftPanel = new JPanel();
                    leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
                    leftPanel.setBackground(row.getBackground());

                    JLabel itemLabel = new JLabel(truncate(item, 24));
                    itemLabel.setFont(READABLE_FONT);
                    itemLabel.setForeground(points >= 25
                        ? new Color(255, 100, 100)
                        : points >= 15
                            ? new Color(255, 180, 100)
                            : new Color(220, 220, 220));
                    itemLabel.setToolTipText(item);

                    int kc = ((Number) drop.getOrDefault("kc", 0)).intValue();
                    JLabel detailLabel = new JLabel("<html>" + nameWithRankHtml(player)
                        + (monster.isEmpty() ? "" : " — " + escapeHtml(monster))
                        + (kc > 0 ? " <font color='#8a8a8a'>(" + String.format("%,d", kc) + " KC)</font>" : "")
                        + "</html>");
                    detailLabel.setFont(READABLE_FONT_SMALL);
                    detailLabel.setForeground(new Color(120, 120, 120));

                    leftPanel.add(itemLabel);
                    leftPanel.add(detailLabel);
                    row.add(leftPanel, BorderLayout.CENTER);

                    // Right: gp value on top, clan points under it
                    JPanel rightPanel = new JPanel();
                    rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
                    rightPanel.setBackground(row.getBackground());

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

                    if (points > 0)
                    {
                        JLabel ptLabel = new JLabel("+" + points + " pts");
                        ptLabel.setFont(READABLE_FONT_SMALL);
                        ptLabel.setForeground(new Color(76, 175, 80));
                        ptLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
                        rightPanel.add(ptLabel);
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
            header.setBackground(new Color(45, 42, 30)); // gold-tinted header band
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            header.setBorder(new EmptyBorder(4, 8, 4, 6));

            JLabel title = new JLabel(rsn + " — " + (drops != null ? drops.size() : 0) + " drops");
            title.setFont(new Font("Segoe UI", Font.BOLD, 12));
            title.setForeground(ACCENT_GOLD);
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

                    // Height follows the two text lines — a fixed cap was clipping the detail line.
                    JPanel row = new JPanel(new BorderLayout(4, 0))
                    {
                        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                    };
                    row.setBackground(i % 2 == 0
                        ? ColorScheme.DARK_GRAY_COLOR
                        : new Color(35, 35, 35));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setBorder(new EmptyBorder(2, 6, 2, 4));

                    // Item icon (resolved by name — these rows don't carry an id)
                    int rowIconId = resolveItemId(item);
                    if (rowIconId > 0 && itemManager != null)
                    {
                        JLabel icon = new JLabel();
                        icon.setPreferredSize(new Dimension(28, 26));
                        icon.setHorizontalAlignment(SwingConstants.CENTER);
                        AsyncBufferedImage img = itemManager.getImage(rowIconId);
                        icon.setIcon(new ImageIcon(img));
                        img.onLoaded(() -> { icon.setIcon(new ImageIcon(img)); icon.revalidate(); icon.repaint(); });
                        row.add(icon, BorderLayout.WEST);
                    }

                    JPanel left = new JPanel();
                    left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
                    left.setBackground(row.getBackground());

                    JLabel itemLbl = new JLabel(truncate(item, 26));
                    itemLbl.setFont(READABLE_FONT);
                    itemLbl.setForeground(pts >= 25
                        ? new Color(255, 100, 100)
                        : pts >= 15
                            ? new Color(255, 180, 100)
                            : new Color(220, 220, 220));
                    itemLbl.setToolTipText(item);
                    left.add(itemLbl);

                    String detail = monster;
                    if (kc > 0) detail += " (" + kc + " kc)";
                    if (!date.isEmpty()) detail += " — " + date;
                    JLabel detLbl = new JLabel(detail);
                    detLbl.setFont(READABLE_FONT_SMALL);
                    detLbl.setForeground(new Color(140, 140, 140));
                    detLbl.setToolTipText(detail);
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

        // Search-only: the full list stays hidden until the member types something.
        if (searchFilter == null || searchFilter.isEmpty())
        {
            JLabel hint = new JLabel("Type an item or boss name to search the trackable list");
            hint.setFont(READABLE_FONT_ITALIC);
            hint.setForeground(new Color(110, 110, 110));
            hint.setBorder(new EmptyBorder(4, 4, 4, 4));
            whitelistBrowserPanel.add(hint);
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
