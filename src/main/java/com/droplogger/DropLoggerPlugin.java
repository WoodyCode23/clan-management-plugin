package com.droplogger;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
    name = "Clan Management",
    description = "Clan management plugin — drops, hiscores, and more",
    tags = {"clan", "management", "drop", "logger", "discord", "hiscores"}
)
public class DropLoggerPlugin extends Plugin
{
    private static final Pattern VALUABLE_DROP_PATTERN =
        Pattern.compile("Valuable drop: (.+?) \\(([\\d,]+) coins\\)");
    private static final Pattern COLLECTION_LOG_PATTERN =
        Pattern.compile("New item added to your collection log: (.+)");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private DropLoggerConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private GoogleSheetsService sheetsService;

    @Inject
    private BoardDataService boardDataService;

    @Inject
    private DiscordWebhookService discordService;

    @Inject
    private HiscoreService hiscoreService;

    @Inject
    private AdminService adminService;

    @Inject
    private WomService womService;

    @Inject
    private DrawManager drawManager;

    @Inject
    private Gson gson;

    private ClanPanel panel;
    private AdminPanel adminPanel;
    private NavigationButton navButton;

    private ScheduledFuture<?> refreshTask;

    // Track last killed NPC for correlating drops
    private String lastKilledNpc = "Unknown";
    private int lastKillCount = 0;

    private PbDetector pbDetector;
    private FightTracker fightTracker;
    private boolean wasInInstance = false;

    private static final String WHITELIST_CACHE_FILE = "clan-whitelist-cache.json";
    private static final String HISCORE_CACHE_FILE = "clan-hiscore-cache.json";
    private static final String DROPS_CACHE_FILE = "clan-drops-cache.json";

    // In-memory hiscore cache: categoryKey → list of entries
    private final Map<String, List<HiscoreEntry>> hiscoreCacheV2 = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile boolean hiscoreV2BatchFetched = false; // true once allTopTimes has been called this session

    // Server-side config fetched from Settings tab
    private String fetchedClanDropLogUrl;
    private String fetchedDiscordWebhookUrl;
    private String clanName = "Clan";
    private boolean serverConfigLoaded = false;

    // Cached drops tab data
    private List<Map<String, Object>> cachedLeaderboard;
    private List<Map<String, Object>> cachedRecentDrops;
    private List<Map<String, String>> cachedClanWhitelist;
    private boolean dropsTabLoaded = false;

    @Provides
    DropLoggerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DropLoggerConfig.class);
    }

    /**
     * Decode the clan code (base64 of "url|key") into a 2-element array [url, key].
     * Returns null if the code is blank or malformed.
     */
    private String[] decodeClanCode()
    {
        String code = config.boardCode();
        if (code == null || code.trim().isEmpty())
        {
            return null;
        }
        try
        {
            String cleaned = code.trim().replaceAll("^['\"]|['\"]$", "");
            String decoded = new String(java.util.Base64.getDecoder().decode(cleaned));
            int sep = decoded.indexOf('|');
            if (sep < 0)
            {
                return null;
            }
            String url = decoded.substring(0, sep);
            String key = decoded.substring(sep + 1);
            if (url.isEmpty() || key.isEmpty())
            {
                return null;
            }
            return new String[]{url, key};
        }
        catch (Exception e)
        {
            log.warn("Invalid clan code", e);
            return null;
        }
    }

    /** Get the Clan Management API URL from the clan code. */
    private String getClanApiUrl()
    {
        String[] parts = decodeClanCode();
        return parts != null ? parts[0] : "";
    }

    /** Get the API key from the clan code. */
    private String getApiKey()
    {
        String[] parts = decodeClanCode();
        return parts != null ? parts[1] : "";
    }

    /** Get the clan name from server config, defaulting to "Clan". */
    String getClanName()
    {
        return clanName != null && !clanName.isEmpty() ? clanName : "Clan";
    }

    @Override
    protected void startUp()
    {
        // Set up side panel
        panel = new ClanPanel();
        // Show tabs only if board code is configured
        panel.setConnected(decodeClanCode() != null);
        panel.setOnRefresh(() -> executor.submit(this::refreshData));
        panel.setOnFetchTimes((cat, timesPanel) -> executor.submit(() -> fetchAndDisplayTimesV2(cat, timesPanel)));
        panel.setOnClearHiscoreCache(() ->
        {
            hiscoreCacheV2.clear();
            hiscoreV2BatchFetched = false;
            File cacheFile = getHiscoreCacheFile();
            if (cacheFile.exists()) cacheFile.delete();
            log.info("Hiscore cache cleared — next view will batch-fetch");
        });
        panel.setOnRefreshDropsTab(() -> executor.submit(this::refreshDropsTab));
        panel.setOnFetchPlayerDrops((rsn) -> executor.submit(() -> fetchPlayerDrops(rsn)));
        panel.setOnRefreshWhitelist(() -> executor.submit(this::refreshClanWhitelist));
        panel.setOnFetchWomData((metric, period) -> executor.submit(() -> fetchWomData(metric, period)));
        // Load caches from disk (avoids re-fetching every startup)
        loadHiscoreCacheFromDisk();
        loadDropsCacheFromDisk();
        loadWhitelistCacheFromDisk();
        // Show cached drops data immediately if available
        if (cachedLeaderboard != null)
        {
            panel.updateDropsLeaderboard(cachedLeaderboard, null);
        }
        if (cachedRecentDrops != null)
        {
            panel.updateRecentDrops(cachedRecentDrops);
        }
        if (cachedClanWhitelist != null && !cachedClanWhitelist.isEmpty())
        {
            panel.updateClanWhitelist(cachedClanWhitelist);
        }

        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
        if (icon == null)
        {
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
        icon = ImageUtil.resizeImage(icon, 16, 16);

        navButton = NavigationButton.builder()
            .tooltip("Clan Management")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        // Set up admin panel if admin key is configured
        setupAdminPanel();

        // Set up PB detector and fight tracker
        pbDetector = new PbDetector();
        fightTracker = new FightTracker();

        // Start periodic data refresh
        startDataRefresh();

        log.info("Clan Management plugin started");
    }

    @Override
    protected void shutDown()
    {
        if (refreshTask != null) refreshTask.cancel(true);

        if (fightTracker != null) fightTracker.reset();

        clientToolbar.removeNavigation(navButton);
        log.info("Clan Management plugin stopped");
    }

    @Subscribe
    public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
    {
        if (!"droplogger".equals(event.getGroup()))
        {
            return;
        }

        if ("boardCode".equals(event.getKey()))
        {
            log.info("Clan code changed, reconnecting...");
            serverConfigLoaded = false;
            panel.setConnected(decodeClanCode() != null);
            executor.submit(this::refreshData);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (fightTracker == null)
        {
            return;
        }

        boolean inInstance = client.isInInstancedRegion();

        if (inInstance && !wasInInstance)
        {
            // Just entered an instance — start tracking
            String localName = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getName() : null;
            fightTracker.startTracking(localName);
        }
        else if (!inInstance && wasInInstance)
        {
            // Just left an instance — stop tracking (data preserved for PB check)
            fightTracker.stopTracking();
        }

        if (inInstance && fightTracker.isTracking())
        {
            // Scan for players each tick while in the instance
            fightTracker.addPlayers(client.getPlayers(), client.getLocalPlayer());
        }

        wasInInstance = inInstance;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        // Reset fight tracker on logout/hop to avoid stale data
        if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.HOPPING)
        {
            if (fightTracker != null)
            {
                fightTracker.reset();
            }
            wasInInstance = false;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        String rawMessage = event.getMessage();
        String cleanedMessage = Text.removeTags(rawMessage);

        // ── Hiscore submission (always update context, even if submission is disabled) ──
        pbDetector.processMessage(cleanedMessage);

        if (config.enablePbSubmission())
        {
            handleCompletionTime(cleanedMessage);
        }

        // ── Drop Logging (clan drop log) ──
        if (config.enableClanDropLog())
        {
            handleDropLogging(rawMessage);
        }

        // ── Collection Log Detection (for clan drop log) ──
        if (config.enableClanDropLog())
        {
            handleCollectionLogEntry(cleanedMessage);
        }
    }

    private void handleDropLogging(String message)
    {
        Matcher matcher = VALUABLE_DROP_PATTERN.matcher(message);
        if (!matcher.find())
        {
            return;
        }

        String itemName = matcher.group(1);
        int value = Integer.parseInt(matcher.group(2).replace(",", ""));

        if (value < config.clanDropMinValue())
        {
            return;
        }

        String clanLogUrl = getClanApiUrl();
        if (clanLogUrl.isEmpty())
        {
            return;
        }

        String playerName = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName()
            : "Unknown";

        WorldPoint wp = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation()
            : new WorldPoint(0, 0, 0);

        DropEntry drop = new DropEntry(
            itemName, value, lastKilledNpc, lastKillCount,
            wp.getX(), wp.getY(), wp.getPlane(), playerName
        );

        executor.submit(() -> sheetsService.logClanDrop(clanLogUrl, drop, getApiKey()));
        log.debug("Clan drop logged: {} ({} gp)", itemName, value);

        // Post to Discord
        if (config.postDrops() && fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
        {
            String webhookUrl = fetchedDiscordWebhookUrl;
            executor.submit(() -> discordService.postDrop(webhookUrl, drop));
        }

        // Chat confirmation
        if (config.chatConfirmation())
        {
            clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[" + getClanName() + "] Drop logged: " + itemName + " (" + value + " gp)", "")
            );
        }
    }

    private void handleCollectionLogEntry(String cleanedMessage)
    {
        Matcher matcher = COLLECTION_LOG_PATTERN.matcher(cleanedMessage);
        if (!matcher.find())
        {
            return;
        }

        String itemName = matcher.group(1).trim();
        String playerName = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName()
            : "Unknown";

        WorldPoint wp = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation()
            : new WorldPoint(0, 0, 0);

        // Create a drop entry with 0 value (collection log entries don't always have a value)
        DropEntry drop = new DropEntry(
            itemName, 0, lastKilledNpc, lastKillCount,
            wp.getX(), wp.getY(), wp.getPlane(), playerName
        );

        if (fetchedClanDropLogUrl != null && !fetchedClanDropLogUrl.isEmpty())
        {
            String clanLogUrl = fetchedClanDropLogUrl;
            executor.submit(() -> sheetsService.logClanDrop(clanLogUrl, drop, getApiKey(), "collection_log"));
            log.debug("Collection log entry submitted: {} for {}", itemName, playerName);
        }
    }

    /**
     * Handle any boss/raid completion time — checks against clan hiscores
     * even when it's not a personal best, since a player can set a clan
     * record without beating their own PB.
     */
    private void handleCompletionTime(String cleanedMessage)
    {
        PbDetector.CompletionResult completion = pbDetector.detectCompletion(cleanedMessage);
        if (completion == null)
        {
            return;
        }

        String group = completion.getGroup();
        if ("unknown".equals(group))
        {
            log.debug("Completion time detected but could not identify activity");
            return;
        }

        // Gather party members — use fight tracker for instanced bosses, fallback to snapshot
        List<String> partyMembers;
        if (fightTracker != null && fightTracker.getTrackedPartySize() > 0)
        {
            partyMembers = fightTracker.getTrackedMembers();
        }
        else
        {
            partyMembers = getPartyMembers();
        }
        int partySize = partyMembers.size();

        // Resolve the specific BossCategory
        BossCategory bossCategory = resolveBossCategory(group, partySize);

        if (bossCategory == null)
        {
            log.warn("Could not resolve category for group={} size={}", group, partySize);
            return;
        }

        boolean isGroupContent = bossCategory.isGroupContent();
        String categoryName = bossCategory.getDisplayName();

        // Validate clan membership for group content
        if (isGroupContent)
        {
            if (!validateClanMembership(partyMembers))
            {
                log.info("Time not submitted: not all party members in clan chat");
                if (config.pbChatConfirmation())
                {
                    clientThread.invokeLater(() ->
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                            "[" + getClanName() + "] Time not submitted — not all party members are in clan chat", "")
                    );
                }
                return;
            }
        }

        // Sort party members so all clients agree on who submits
        List<String> sortedMembers = new ArrayList<>(partyMembers);
        Collections.sort(sortedMembers, String.CASE_INSENSITIVE_ORDER);
        String rsns = String.join(", ", sortedMembers);

        // Only the alphabetically first party member submits (prevents duplicates
        // when multiple clan members have the plugin running in the same raid)
        String localName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
        boolean isSubmitter = sortedMembers.isEmpty()
            || sortedMembers.get(0).equalsIgnoreCase(localName);

        if (!isSubmitter)
        {
            log.debug("Completion detected but {} is the designated submitter, skipping",
                sortedMembers.get(0));
            return;
        }

        String date = new SimpleDateFormat("MM/dd").format(new Date());
        String formattedTime = completion.getFormattedTime();
        double timeSeconds = completion.getTimeSeconds();
        String categoryKey = bossCategory.getKey();
        String sizeLabel = bossCategory.getSizeLabel();

        log.info("Completion time: {} {} — {} (key={}, party: {})",
            formattedTime, categoryName, sizeLabel, categoryKey, rsns);

        // Capture screenshot immediately (must be done on render thread)
        final BufferedImage[] screenshotHolder = {null};
        if (config.postPbs() && fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
        {
            try
            {
                drawManager.requestNextFrameListener(image ->
                {
                    screenshotHolder[0] = new BufferedImage(
                        image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = screenshotHolder[0].createGraphics();
                    g.drawImage(image, 0, 0, null);
                    g.dispose();
                });
            }
            catch (Exception e)
            {
                log.warn("Failed to capture screenshot", e);
            }
        }

        String v2ApiUrl = fetchedClanDropLogUrl != null ? fetchedClanDropLogUrl : "";
        String apiKey = getApiKey();
        final int finalPartySize = partySize;
        final String finalCategoryName = categoryName;

        executor.submit(() ->
        {
            // Small delay to ensure screenshot capture completes
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            int placed = 0;

            if (!v2ApiUrl.isEmpty())
            {
                placed = hiscoreService.checkAndSubmitPbV2(
                    v2ApiUrl, categoryKey, formattedTime, timeSeconds,
                    rsns, date, finalPartySize, apiKey);
                log.debug("Hiscore submit result for {}: {}", categoryKey, placed);
            }

            // Only notify in chat if the time placed in the clan top 3
            if (placed > 0 && config.pbChatConfirmation())
            {
                String msg = String.format("[%s] %s placed #%d in %s!",
                    getClanName(), formattedTime, placed, finalCategoryName);
                clientThread.invokeLater(() ->
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "")
                );
            }

            // Invalidate cache for this category so next UI view fetches fresh data
            if (placed > 0)
            {
                hiscoreCacheV2.remove(categoryKey);
                saveHiscoreCacheV2ToDisk();
            }

            // Post to Discord if placed top 3
            if (placed > 0 && config.postPbs()
                && fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
            {
                discordService.postPb(fetchedDiscordWebhookUrl, formattedTime, placed,
                    finalCategoryName, rsns, screenshotHolder[0]);
            }
        });
    }

    /**
     * Resolve the BossCategory (v2) based on group key and party size.
     * Group keys from PbDetector are now specific enough that BossCategory.find() handles most cases.
     */
    private BossCategory resolveBossCategory(String group, int partySize)
    {
        // For raids, the group key maps directly
        // For most bosses, the group key is now specific (e.g. "bandos", "duke")
        // BossCategory.find() picks the best match for the given party size
        return BossCategory.find(group, partySize);
    }

    /**
     * Get all party members. In instanced areas, uses visible players
     * on the same plane as the local player (filters out spectators).
     * Otherwise returns just the local player.
     */
    private List<String> getPartyMembers()
    {
        List<String> members = new ArrayList<>();
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return members;
        }

        String localName = localPlayer.getName();
        if (localName != null)
        {
            members.add(localName);
        }

        if (client.isInInstancedRegion())
        {
            int localPlane = localPlayer.getWorldLocation().getPlane();

            for (Player player : client.getPlayers())
            {
                if (player == localPlayer)
                {
                    continue;
                }
                String name = player.getName();
                if (name == null || name.isEmpty())
                {
                    continue;
                }
                // Filter out spectators — they're on a different plane (e.g. ToB spectators)
                if (player.getWorldLocation().getPlane() != localPlane)
                {
                    continue;
                }
                members.add(name);
            }
        }

        return members;
    }

    /**
     * Validate that all party members are in the player's clan chat.
     */
    private boolean validateClanMembership(List<String> partyMembers)
    {
        ClanChannel clanChannel = client.getClanChannel();
        if (clanChannel == null)
        {
            log.warn("Cannot validate clan membership — not in a clan chat");
            return false;
        }

        Set<String> clanNames = new HashSet<>();
        for (ClanChannelMember member : clanChannel.getMembers())
        {
            clanNames.add(Text.toJagexName(member.getName()).toLowerCase());
        }

        for (String partyMember : partyMembers)
        {
            String normalized = Text.toJagexName(partyMember).toLowerCase();
            if (!clanNames.contains(normalized))
            {
                log.info("Party member {} is not in clan chat", partyMember);
                return false;
            }
        }

        return true;
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        NPC npc = event.getNpc();
        if (npc != null)
        {
            lastKilledNpc = npc.getName();
            lastKillCount = pbDetector.getLastKillCount();
        }
    }

    private void startDataRefresh()
    {
        if (refreshTask != null)
        {
            refreshTask.cancel(false);
        }

        int interval = Math.max(30, config.refreshInterval());
        refreshTask = executor.scheduleAtFixedRate(
            this::refreshData, 10, interval, TimeUnit.SECONDS);
    }

    private void refreshData()
    {
        String apiUrl = getClanApiUrl();
        if (apiUrl == null || apiUrl.isEmpty())
        {
            panel.setConnected(false);
            panel.setStatus("Enter your Clan Code in plugin settings");
            return;
        }

        // Fetch server-side config on first successful connection
        if (!serverConfigLoaded)
        {
            try
            {
                Map<String, String> serverConfig = boardDataService.fetchConfig(apiUrl, getApiKey());
                fetchedClanDropLogUrl = apiUrl; // Same URL as the clan code
                fetchedDiscordWebhookUrl = serverConfig.getOrDefault("discordWebhookUrl", "");
                String configClanName = serverConfig.getOrDefault("clanName", "");
                if (!configClanName.isEmpty()) clanName = configClanName;
                serverConfigLoaded = true;
                panel.setConnected(true);
                panel.setClanName(getClanName());
                discordService.setClanName(getClanName());

                // Set WOM group ID from server config
                String womId = serverConfig.getOrDefault("womGroupId", "");
                if (!womId.isEmpty())
                {
                    try { womService.setGroupId(Integer.parseInt(womId)); }
                    catch (NumberFormatException ignored) {}
                }

                // Show announcement on home tab
                String announcement = serverConfig.getOrDefault("announcement", "");
                if (!announcement.isEmpty())
                {
                    panel.setAnnouncements(java.util.Collections.singletonList(announcement));
                }

                log.info("Server config loaded — clanName={}, clanLog={}, discord={}",
                    getClanName(), !fetchedClanDropLogUrl.isEmpty(), !fetchedDiscordWebhookUrl.isEmpty());

                // Auto-load drops tab on first config load
                executor.submit(this::refreshDropsTab);
            }
            catch (Exception e)
            {
                log.warn("Failed to fetch server config — will retry next refresh", e);
            }
        }

        // Auto-refresh WOM data on same cycle
        refreshWomData();
        refreshClanActivity();
    }

    /**
     * Batch-fetch all hiscore times from the v2 API (one call), populate entire cache.
     * Called once per session on first hiscore tab interaction, or on manual refresh.
     */
    private void batchFetchAllHiscores()
    {
        String v2Url = fetchedClanDropLogUrl;
        String apiKey = getApiKey();

        if (v2Url == null || v2Url.isEmpty())
        {
            log.debug("Clan drop log URL not configured — skipping batch hiscore fetch");
            return;
        }

        try
        {
            Map<String, List<HiscoreEntry>> allTimes = hiscoreService.fetchAllTopTimes(v2Url, apiKey);
            hiscoreCacheV2.putAll(allTimes);
            hiscoreV2BatchFetched = true;
            saveHiscoreCacheV2ToDisk();
            log.info("Batch-fetched hiscores: {} categories", allTimes.size());
        }
        catch (Exception e)
        {
            log.warn("Failed to batch-fetch hiscores", e);
        }
    }

    private void fetchAndDisplayTimesV2(BossCategory cat, javax.swing.JPanel timesPanel)
    {
        java.awt.Color accentColor = new java.awt.Color(100, 149, 237);

        // If we haven't done a batch fetch this session and cache is empty, do it now
        if (!hiscoreV2BatchFetched && hiscoreCacheV2.isEmpty())
        {
            batchFetchAllHiscores();
        }

        // Serve from cache (may be empty list for categories with no entries — that's fine)
        List<HiscoreEntry> cached = hiscoreCacheV2.get(cat.getKey());
        if (cached != null)
        {
            panel.populateTimesPanel(timesPanel, cached, accentColor);
            return;
        }

        // Category not in cache — it might just have no entries yet
        if (hiscoreV2BatchFetched)
        {
            // We already fetched everything; this category simply has no times
            panel.populateTimesPanel(timesPanel, new ArrayList<>(), accentColor);
            return;
        }

        // Fallback: try individual fetch
        String v2Url = fetchedClanDropLogUrl;
        String apiKey = getApiKey();

        if (v2Url != null && !v2Url.isEmpty())
        {
            try
            {
                List<HiscoreEntry> entries = hiscoreService.fetchTopTimesV2(v2Url, cat.getKey(), apiKey);
                hiscoreCacheV2.put(cat.getKey(), entries);
                saveHiscoreCacheV2ToDisk();
                panel.populateTimesPanel(timesPanel, entries, accentColor);
                return;
            }
            catch (Exception e)
            {
                log.warn("Failed to fetch hiscore times for {}", cat.getKey(), e);
            }
        }

        // Failed or not configured
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            timesPanel.removeAll();
            String msg = (v2Url == null || v2Url.isEmpty())
                ? "Hiscore API not configured"
                : "Failed to load times";
            javax.swing.JLabel err = new javax.swing.JLabel(msg);
            err.setFont(err.getFont().deriveFont(java.awt.Font.ITALIC, 10f));
            err.setForeground(new java.awt.Color(120, 120, 120));
            err.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 10, 12, 10));
            timesPanel.add(err);
            timesPanel.revalidate();
            timesPanel.repaint();
        });
    }

    private void refreshDropsTab()
    {
        String clanLogUrl = fetchedClanDropLogUrl;
        if (clanLogUrl == null || clanLogUrl.isEmpty())
        {
            log.debug("Clan drop log URL not configured — skipping drops tab refresh");
            // Try to show cached data if available
            if (cachedLeaderboard != null)
            {
                String playerName = client.getLocalPlayer() != null
                    ? client.getLocalPlayer().getName() : null;
                panel.updateDropsLeaderboard(cachedLeaderboard, playerName);
            }
            if (cachedRecentDrops != null)
            {
                panel.updateRecentDrops(cachedRecentDrops);
            }
            return;
        }

        String playerName = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName()
            : null;

        try
        {
            List<Map<String, Object>> leaderboard = boardDataService.fetchLeaderboard(
                clanLogUrl, getApiKey(), "monthly");
            cachedLeaderboard = leaderboard;
            panel.updateDropsLeaderboard(leaderboard, playerName);
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch drops leaderboard", e);
            // Show cached if available
            if (cachedLeaderboard != null)
            {
                panel.updateDropsLeaderboard(cachedLeaderboard, playerName);
            }
        }

        try
        {
            List<Map<String, Object>> recent = boardDataService.fetchRecentDrops(
                clanLogUrl, getApiKey(), 20);
            cachedRecentDrops = recent;
            panel.updateRecentDrops(recent);
            saveDropsCacheToDisk();
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch recent drops", e);
            if (cachedRecentDrops != null)
            {
                panel.updateRecentDrops(cachedRecentDrops);
            }
        }

        // Also refresh the clan whitelist browser
        refreshClanWhitelist();

        dropsTabLoaded = true;
    }

    private void fetchPlayerDrops(String rsn)
    {
        String clanLogUrl = fetchedClanDropLogUrl;
        if (clanLogUrl == null || clanLogUrl.isEmpty())
        {
            return;
        }

        try
        {
            List<Map<String, Object>> drops = boardDataService.fetchPlayerDrops(
                clanLogUrl, getApiKey(), rsn);
            panel.showPlayerDrops(rsn, drops);
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch drops for {}", rsn, e);
        }
    }

    private void refreshClanWhitelist()
    {
        String clanLogUrl = fetchedClanDropLogUrl;
        if (clanLogUrl == null || clanLogUrl.isEmpty())
        {
            // Show cached if available
            if (cachedClanWhitelist != null && !cachedClanWhitelist.isEmpty())
            {
                panel.updateClanWhitelist(cachedClanWhitelist);
            }
            return;
        }

        try
        {
            List<Map<String, String>> whitelist = boardDataService.fetchClanWhitelist(
                clanLogUrl, getApiKey());
            cachedClanWhitelist = whitelist;
            panel.updateClanWhitelist(whitelist);
            saveWhitelistCacheToDisk();
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch clan whitelist", e);
            if (cachedClanWhitelist != null && !cachedClanWhitelist.isEmpty())
            {
                panel.updateClanWhitelist(cachedClanWhitelist);
            }
        }
    }

    // Track last WOM fetch settings so auto-refresh uses the same options
    private String lastWomMetric = "overall";
    private String lastWomPeriod = "week"; // null = hiscores mode

    private void fetchWomData(String metric, String period)
    {
        lastWomMetric = metric;
        lastWomPeriod = period;
        doFetchWomData(metric, period);
    }

    private void refreshWomData()
    {
        doFetchWomData(lastWomMetric, lastWomPeriod);
    }

    private void doFetchWomData(String metric, String period)
    {
        try
        {
            if (period != null)
            {
                List<WomService.WomEntry> entries = womService.fetchGained(metric, period);
                panel.updateWomLeaderboard(entries, true);
            }
            else
            {
                List<WomService.WomEntry> entries = womService.fetchHiscores(metric);
                panel.updateWomLeaderboard(entries, false);
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch WOM data: {}", e.getMessage());
            panel.updateWomLeaderboard(null, false);
        }
    }

    private void refreshClanActivity()
    {
        try
        {
            List<WomService.ActivityEntry> activity = womService.fetchActivity(15);
            panel.updateActivity(activity);
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch clan activity: {}", e.getMessage());
        }
    }

    private void saveWhitelistCacheToDisk()
    {
        try
        {
            File cacheFile = new File(
                net.runelite.client.RuneLite.RUNELITE_DIR, "clan-whitelist-cache.json");
            java.util.Map<String, Object> cacheData = new java.util.LinkedHashMap<>();
            cacheData.put("whitelist", cachedClanWhitelist);
            String json = gson.toJson(cacheData);
            java.nio.file.Files.write(cacheFile.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            log.debug("Failed to save whitelist cache", e);
        }
    }

    private void loadWhitelistCacheFromDisk()
    {
        try
        {
            File cacheFile = new File(
                net.runelite.client.RuneLite.RUNELITE_DIR, "clan-whitelist-cache.json");
            if (!cacheFile.exists()) return;

            String json = new String(
                java.nio.file.Files.readAllBytes(cacheFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();

            if (root.has("whitelist"))
            {
                com.google.gson.JsonArray arr = root.getAsJsonArray("whitelist");
                List<Map<String, String>> whitelist = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement elem : arr)
                {
                    com.google.gson.JsonObject obj = elem.getAsJsonObject();
                    Map<String, String> item = new java.util.LinkedHashMap<>();
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet())
                    {
                        item.put(entry.getKey(),
                            entry.getValue().isJsonPrimitive()
                                ? entry.getValue().getAsString() : "");
                    }
                    whitelist.add(item);
                }
                cachedClanWhitelist = whitelist;
                log.debug("Loaded {} whitelist items from disk cache", whitelist.size());
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to load whitelist cache from disk", e);
        }
    }

    // ── Hiscore cache ──

    private File getHiscoreCacheFile()
    {
        File runeliteDir = new File(System.getProperty("user.home"), ".runelite");
        return new File(runeliteDir, HISCORE_CACHE_FILE);
    }

    private void saveHiscoreCacheV2ToDisk()
    {
        try
        {
            Map<String, List<Map<String, Object>>> toSave = new LinkedHashMap<>();
            for (Map.Entry<String, List<HiscoreEntry>> entry : hiscoreCacheV2.entrySet())
            {
                List<Map<String, Object>> entryList = new ArrayList<>();
                for (HiscoreEntry he : entry.getValue())
                {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("rank", he.getRank());
                    m.put("timeSeconds", he.getTimeSeconds());
                    m.put("formattedTime", he.getFormattedTime());
                    m.put("rsns", he.getRsns());
                    m.put("date", he.getDate());
                    m.put("categoryKey", he.getCategoryKey());
                    m.put("partySize", he.getPartySize());
                    entryList.add(m);
                }
                toSave.put(entry.getKey(), entryList);
            }

            File cacheFile = getHiscoreCacheFile();
            try (FileWriter writer = new FileWriter(cacheFile))
            {
                gson.toJson(toSave, writer);
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to save hiscore cache", e);
        }
    }

    private void loadHiscoreCacheFromDisk()
    {
        try
        {
            File cacheFile = getHiscoreCacheFile();
            if (!cacheFile.exists()) return;

            Type type = new TypeToken<LinkedHashMap<String, List<Map<String, Object>>>>(){}.getType();
            try (FileReader reader = new FileReader(cacheFile))
            {
                Map<String, List<Map<String, Object>>> raw = gson.fromJson(reader, type);
                if (raw == null) return;

                for (Map.Entry<String, List<Map<String, Object>>> entry : raw.entrySet())
                {
                    List<HiscoreEntry> entries = new ArrayList<>();
                    for (Map<String, Object> m : entry.getValue())
                    {
                        entries.add(new HiscoreEntry(
                            ((Number) m.getOrDefault("rank", 0)).intValue(),
                            ((Number) m.getOrDefault("timeSeconds", 0.0)).doubleValue(),
                            (String) m.getOrDefault("formattedTime", ""),
                            (String) m.getOrDefault("rsns", ""),
                            (String) m.getOrDefault("date", ""),
                            (String) m.getOrDefault("categoryKey", null),
                            ((Number) m.getOrDefault("partySize", 1)).intValue()
                        ));
                    }
                    hiscoreCacheV2.put(entry.getKey(), entries);
                }
                log.info("Loaded hiscore cache from disk: {} categories", hiscoreCacheV2.size());
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load hiscore cache from disk", e);
        }
    }

    // ── Drops tab cache ──

    private void saveDropsCacheToDisk()
    {
        try
        {
            Map<String, Object> cache = new LinkedHashMap<>();
            cache.put("leaderboard", cachedLeaderboard);
            cache.put("recent", cachedRecentDrops);

            File cacheFile = new File(new File(System.getProperty("user.home"), ".runelite"), DROPS_CACHE_FILE);
            try (FileWriter writer = new FileWriter(cacheFile))
            {
                gson.toJson(cache, writer);
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to save drops cache", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadDropsCacheFromDisk()
    {
        try
        {
            File cacheFile = new File(new File(System.getProperty("user.home"), ".runelite"), DROPS_CACHE_FILE);
            if (!cacheFile.exists()) return;

            Type type = new TypeToken<LinkedHashMap<String, Object>>(){}.getType();
            try (FileReader reader = new FileReader(cacheFile))
            {
                Map<String, Object> cache = gson.fromJson(reader, type);
                if (cache == null) return;

                if (cache.containsKey("leaderboard"))
                {
                    cachedLeaderboard = (List<Map<String, Object>>) cache.get("leaderboard");
                }
                if (cache.containsKey("recent"))
                {
                    cachedRecentDrops = (List<Map<String, Object>>) cache.get("recent");
                }

                if (cachedLeaderboard != null || cachedRecentDrops != null)
                {
                    log.info("Loaded drops cache from disk");
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load drops cache from disk", e);
        }
    }

    private void setupAdminPanel()
    {
        String adminKey = config.adminApiKey();
        if (adminKey == null || adminKey.isEmpty())
        {
            return;
        }

        this.adminPanel = new AdminPanel();
        panel.showAdminTab(adminPanel);

        String clanApiUrl = getClanApiUrl();
        String apiKey = getApiKey();

        // Load shared settings from sheet
        adminPanel.setOnLoadSettings(() -> executor.submit(() -> {
            try
            {
                adminPanel.setStatus("Loading settings...");
                var settings = adminService.getSharedSettings(clanApiUrl, apiKey, adminKey);
                adminPanel.setClanName(settings.getOrDefault("clanName", ""));
                adminPanel.setWebhookUrl(settings.getOrDefault("discordWebhookUrl", ""));
                adminPanel.setWomGroupId(settings.getOrDefault("womGroupId", ""));
                adminPanel.setAnnouncement(settings.getOrDefault("announcement", ""));
                adminPanel.setStatus("Settings loaded");
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        // Save shared settings to sheet
        adminPanel.setOnSaveSettings(args -> executor.submit(() -> {
            try
            {
                adminPanel.setStatus("Saving...");
                adminService.saveSharedSettings(clanApiUrl, apiKey, adminKey,
                    args[0], args[1], args.length > 2 ? args[2] : "",
                    args.length > 3 ? args[3] : "");
                adminPanel.setStatus("Settings saved");
                // Refresh local cached config
                serverConfigLoaded = false;
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        // Hiscore moderation
        adminPanel.setOnRemoveHiscore(args -> executor.submit(() -> {
            try
            {
                String categoryKey = args[0];
                int rank = Integer.parseInt(args[1]);
                String v2Url = fetchedClanDropLogUrl != null ? fetchedClanDropLogUrl : "";
                if (!v2Url.isEmpty())
                {
                    adminService.removeHiscoreEntryV2(v2Url, apiKey, adminKey, categoryKey, rank);
                    adminPanel.setStatus("Removed rank #" + rank + " from " + categoryKey);
                }
                else
                {
                    adminPanel.setStatus("No hiscore API configured");
                }
                // Clear cache for this category
                hiscoreCacheV2.remove(categoryKey);
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        // Rotate API key
        adminPanel.setOnRotateApiKey(newKey -> executor.submit(() -> {
            try
            {
                adminPanel.setStatus("Rotating API key...");
                adminService.rotateApiKey(clanApiUrl, apiKey, adminKey, newKey);

                // Generate new clan code with the new key
                String newClanCode = java.util.Base64.getEncoder().encodeToString(
                    (clanApiUrl + "|" + newKey).getBytes());
                adminPanel.setNewBoardCode(newClanCode);
                adminPanel.setStatus("API key rotated — distribute new clan code to members");
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        log.info("Admin panel enabled");
    }
}
