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
import net.runelite.api.WorldType;
import net.runelite.api.EnumComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.HashMap;
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
    name = "Solus",
    description = "Solus clan plugin — drops, speed times, and more",
    tags = {"solus", "clan", "drop", "logger", "discord", "speed", "times"}
)
public class ClanManagementPlugin extends Plugin
{
    private static final Pattern VALUABLE_DROP_PATTERN =
        Pattern.compile("Valuable drop: (.+?) \\(([\\d,]+) coins\\)");
    private static final Pattern COLLECTION_LOG_PATTERN =
        Pattern.compile("New item added to your collection log: (.+)");
    private static final Pattern CLUE_COMPLETION_PATTERN =
        Pattern.compile("You have completed (\\d+) (easy|medium|hard|elite|master|beginner) Treasure Trails\\.");
    private static final Pattern CLOG_PB_PATTERN =
        Pattern.compile("Fastest (?:kill|time|completion)[:\\s]+([\\d]+:[\\d.]+)");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClanManagementConfig config;

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

    @Inject
    private BingoService bingoService;

    @Inject
    private PlatformApiService platformApiService;

    @Inject
    private RsHiscoreTracker hiscoreTracker;

    private ClanPanel panel;
    private AdminPanel adminPanel;
    private NavigationButton navButton;

    private ScheduledFuture<?> refreshTask;

    // Track last killed NPC for correlating drops
    private String lastKilledNpc = "Unknown";
    private int lastKillCount = 0;
    private long lastKillTime = 0;

    private PbDetector pbDetector;
    private FightTracker fightTracker;
    private boolean wasInInstance = false;

    // Decoded platform values from clanCode
    private String decodedPlatformUrl = "";
    private String decodedPlatformSlug = "";
    private String decodedPlatformKey = "";

    private static final String WHITELIST_CACHE_FILE = "clan-whitelist-cache.json";
    private static final String HISCORE_CACHE_FILE = "clan-hiscore-cache.json";
    private static final String DROPS_CACHE_FILE = "clan-drops-cache.json";

    // In-memory hiscore cache: categoryKey → list of entries
    private final Map<String, List<HiscoreEntry>> hiscoreCacheV2 = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile boolean hiscoreV2BatchFetched = false; // true once allTopTimes has been called this session

    // Server-side config fetched from Settings tab
    private String fetchedClanDropLogUrl;
    private String fetchedDiscordWebhookUrl;
    private int fetchedMinDropValue = 100000;
    private String clanName = "Solus";
    private boolean serverConfigLoaded = false;

    // Cached drops tab data
    private List<Map<String, Object>> cachedLeaderboard;
    private List<Map<String, Object>> cachedRecentDrops;
    private List<Map<String, String>> cachedClanWhitelist;
    private boolean dropsTabLoaded = false;

    // Active event state
    private String activeEventType = "";
    private String activeEventMetric = "";
    private String activeEventDisplayName = "";
    private String activeEventEndTime = "";
    private String activeEventId = "";

    // Bingo state
    private BingoPanel bingoPanel;
    private BountyScheduler bountyScheduler;
    private BingoConfig bingoConfig;
    private boolean bingoActive = false;
    private String playerBingoTeam = "";

    // Collection log sync state (automatic — like WikiSync/RuneProfile)
    private final Map<Integer, ClogItem> clogSyncItems = Collections.synchronizedMap(new LinkedHashMap<>());
    private Map<Integer, String[]> clogItemCategoryMap = null; // itemId -> [tab, category]
    private int clogDebounceTicksRemaining = -1;
    private boolean clogSearchPending = false;
    private int clogRawEventCount = 0;
    private static final int CLOG_DEBOUNCE_TICKS = 30;
    private static final int SCRIPT_CLOG_ITEM = 4100;
    private static final int SEARCH_TOGGLE_PACKED = 40697932; // InterfaceID.Collection.SEARCH_TOGGLE
    private static final int CLOG_TABS_ENUM = 2102;
    private static final int CLOG_DUPE_REMAP_ENUM = 3721; // game enum: bad itemId -> canonical itemId
    private static final int VARP_CLOG_OBTAINED = 2943;   // VarPlayer.CLOG_LOGGED — authoritative unique obtained
    private static final int VARP_CLOG_TOTAL = 2944;      // VarPlayer.CLOG_TOTAL — authoritative unique total
    private static final int PARAM_TAB_NAME = 682;
    private static final int PARAM_TAB_CATEGORIES_ENUM = 683;
    private static final int PARAM_CATEGORY_NAME = 689;
    private static final int PARAM_CATEGORY_ITEMS_ENUM = 690;
    // Built from enum 3721 on clog open: maps a slot's "bad" item id to its canonical id.
    // Replaces the old hand-maintained skip list (which dropped real slots → undercount).
    private Map<Integer, Integer> clogDupeRemap = Collections.emptyMap();
    // Authoritative game counts captured on clog open (varp 2943/2944), reused at upload time.
    private int clogObtainedCount = 0;
    private int clogTotalCount = 0;

    // Adventure log PB sync state
    private int adventureLogPbTicksRemaining = -1;
    private static final int JOURNALSCROLL_GROUP = 741;
    private static final int ADVENTURE_LOG_PB_DELAY_TICKS = 3;
    // Matches: "Fastest kill: 0:46.80", "Fastest run - (Team size: Solo): 13:52.80",
    //          "Fastest Overall time - (Team size: 2 player): 25:40.80",
    //          "Fastest Room time - (Team size: 1 player entry mode):" (time on next line)
    private static final Pattern ADVENTURE_PB_PATTERN =
        Pattern.compile("Fastest (?:Overall time|Room time|kill|time|run|completion)(?:\\s*-\\s*\\(Team size:\\s*(.+?)\\))?[:\\s]+((?:\\d+:)?\\d+:\\d+\\.\\d+)?");
    // Standalone time on its own line (for ToB/ToA where time wraps)
    private static final Pattern STANDALONE_TIME = Pattern.compile("^((?:\\d+:)?\\d+:\\d+\\.\\d+)$");

    @Provides
    ClanManagementConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ClanManagementConfig.class);
    }

    /**
     * Decode the clan code (base64 of "url|key") into a 2-element array [url, key].
     * Returns null if the code is blank or malformed.
     */
    /** @deprecated Legacy Google Sheet code — always returns null now. */
    private String[] decodeClanCode()
    {
        return null;
    }

    /** @deprecated Legacy — returns empty string. Use getPlatformUrl() instead. */
    private String getClanApiUrl()
    {
        return "";
    }

    /** @deprecated Legacy — returns empty string. Use getPlatformKey() instead. */
    private String getApiKey()
    {
        return "";
    }

    /**
     * Check if the player is on a non-standard world (leagues, deadman, tournament, etc.).
     * Drops and PBs from these worlds should not be tracked.
     */
    private boolean isNonStandardWorld()
    {
        java.util.EnumSet<WorldType> worldTypes = client.getWorldType();
        if (worldTypes == null) return false;
        return worldTypes.contains(WorldType.SEASONAL)
            || worldTypes.contains(WorldType.DEADMAN)
            || worldTypes.contains(WorldType.TOURNAMENT_WORLD)
            || worldTypes.contains(WorldType.FRESH_START_WORLD);
    }

    private boolean isPlatformConfigured()
    {
        return !decodedPlatformUrl.isEmpty()
            && !decodedPlatformKey.isEmpty()
            && !decodedPlatformSlug.isEmpty();
    }

    private String getPlatformUrl()
    {
        return decodedPlatformUrl;
    }

    private String getPlatformKey()
    {
        return decodedPlatformKey;
    }

    private String getPlatformSlug()
    {
        return decodedPlatformSlug;
    }

    private void decodePlatformClanCode()
    {
        String code = config.clanCode();
        String[] parts = ClanCodeUtil.decode(code);
        if (parts != null)
        {
            decodedPlatformUrl = parts[0];
            decodedPlatformSlug = parts[1];
            decodedPlatformKey = parts[2];
        }
        else
        {
            decodedPlatformUrl = "";
            decodedPlatformSlug = "";
            decodedPlatformKey = "";
        }
    }

    /**
     * Fetch shared config from the platform bootstrap endpoint.
     * Updates cached values for Discord webhook URL, min drop value, active event, etc.
     */
    private void fetchBootstrapConfig()
    {
        if (!isPlatformConfigured())
        {
            return;
        }

        String url = getPlatformUrl() + "/clans/" + getPlatformSlug() + "/bootstrap";
        JsonObject response = platformApiService.getSync(url, getPlatformKey());
        if (response == null)
        {
            log.warn("Failed to fetch bootstrap config from platform");
            return;
        }

        // Discord webhook
        if (response.has("discordWebhooks"))
        {
            JsonObject webhooks = response.getAsJsonObject("discordWebhooks");
            if (webhooks.has("drops"))
            {
                fetchedDiscordWebhookUrl = webhooks.get("drops").getAsString();
            }
        }

        // Settings
        if (response.has("settings"))
        {
            JsonObject settings = response.getAsJsonObject("settings");
            if (settings.has("minDropValue"))
            {
                fetchedMinDropValue = settings.get("minDropValue").getAsInt();
            }
        }

        // Active event
        if (response.has("activeEvent") && !response.get("activeEvent").isJsonNull())
        {
            JsonObject event = response.getAsJsonObject("activeEvent");
            activeEventType = event.has("type") ? event.get("type").getAsString() : "";
            activeEventMetric = event.has("metric") ? event.get("metric").getAsString() : "";
            activeEventDisplayName = event.has("displayName") ? event.get("displayName").getAsString() : "";
            activeEventEndTime = event.has("endTime") ? event.get("endTime").getAsString() : "";
            activeEventId = event.has("id") ? event.get("id").getAsString() : "";
        }
        else
        {
            activeEventType = "";
            activeEventMetric = "";
            activeEventDisplayName = "";
            activeEventEndTime = "";
            activeEventId = "";
        }

        log.info("Bootstrap config loaded from platform");
    }

    /** Get the clan name — hardcoded to Solus. */
    String getClanName()
    {
        return "Solus";
    }

    @Override
    protected void startUp()
    {
        // Decode platform clan code
        decodePlatformClanCode();

        // Set up side panel
        panel = new ClanPanel();
        // Show tabs only if board code is configured
        panel.setConnected(isPlatformConfigured());
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
        panel.setOnRefreshStatus(() -> executor.submit(this::refreshStatusBoxes));
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
            .tooltip("Solus")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        // Set up admin panel if admin key is configured
        setupAdminPanel();

        // Initialize bingo panel (hidden until config says active)
        bingoPanel = new BingoPanel();

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

        hiscoreTracker.reset();

        if (bountyScheduler != null)
        {
            bountyScheduler.cancel();
            bountyScheduler = null;
        }
        panel.hideBingoTab();
        bingoActive = false;

        clogSyncItems.clear();
        // clog dedup handled by Set keys + enum-3721 canonical remap
        clogDebounceTicksRemaining = -1;
        clogSearchPending = false;
        pbReadPending = false;
        adventureLogPbTicksRemaining = -1;

        clientToolbar.removeNavigation(navButton);
        log.info("Solus plugin stopped");
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
            log.info("Board code changed, reconnecting...");
            serverConfigLoaded = false;
            panel.setConnected(isPlatformConfigured());
            executor.submit(this::refreshData);
        }

        if ("clanCode".equals(event.getKey()))
        {
            log.info("Clan code changed, decoding platform config...");
            decodePlatformClanCode();
            serverConfigLoaded = false;
            executor.submit(this::refreshData);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Stat tracking: detect clan member logoffs
        if (isPlatformConfigured())
        {
            hiscoreTracker.onGameTick(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), config.enableStatTracking());
        }

        // Collection log: trigger search after clog opens to enumerate all items
        if (clogSearchPending)
        {
            clogSearchPending = false;
            triggerClogSearch();
        }

        // Read PB from collection log header (deferred by 1 tick)
        if (pbReadPending)
        {
            pbReadPending = false;
            readClogPb();
        }

        // Adventure log Counters page — bulk PB parse (deferred by several ticks)
        if (adventureLogPbTicksRemaining > 0)
        {
            adventureLogPbTicksRemaining--;
        }
        else if (adventureLogPbTicksRemaining == 0)
        {
            adventureLogPbTicksRemaining = -1;
            parseAdventureLogPbs();
        }

        // Collection log auto-sync debounce
        if (clogDebounceTicksRemaining > 0)
        {
            clogDebounceTicksRemaining--;
        }
        else if (clogDebounceTicksRemaining == 0)
        {
            clogDebounceTicksRemaining = -1;
            log.info("Clog debounce fired: {} raw events, {} unique items collected", clogRawEventCount, clogSyncItems.size());
            uploadCollectionLog();
        }

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

        // ── Skip non-standard worlds (leagues, deadman, tournaments) ──
        if (isNonStandardWorld())
        {
            return;
        }

        // ── Hiscore submission (always update context, even if submission is disabled) ──
        pbDetector.processMessage(cleanedMessage);

        if (config.enableSpeedTimes())
        {
            handleCompletionTime(cleanedMessage);
        }

        // ── Clue completion detection (set source for upcoming drop message) ──
        Matcher clueMatcher = CLUE_COMPLETION_PATTERN.matcher(cleanedMessage);
        if (clueMatcher.find())
        {
            String tier = clueMatcher.group(2);
            lastKilledNpc = tier.substring(0, 1).toUpperCase() + tier.substring(1) + " Clue Scroll";
            lastKillTime = System.currentTimeMillis();
        }

        // ── Drop Logging (clan drop log) ──
        if (config.enableDrops())
        {
            handleDropLogging(rawMessage);
        }

        // ── Collection Log Detection (for clan drop log) ──
        if (config.enableDrops())
        {
            handleCollectionLogEntry(cleanedMessage);
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        // Adventure log Counters page (group 741) — bulk PB sync
        if (event.getGroupId() == JOURNALSCROLL_GROUP && isPlatformConfigured() && config.enableSpeedTimes())
        {
            log.info("Adventure log Counters page detected (group 741), scheduling PB parse");
            // Defer by several ticks so widget text has time to populate
            adventureLogPbTicksRemaining = ADVENTURE_LOG_PB_DELAY_TICKS;
        }

        if (event.getGroupId() == InterfaceID.COLLECTION && isPlatformConfigured() && config.enableClogSync())
        {
            // Show the game's authoritative unique counts immediately (varp 2943/2944) so the
            // panel matches the in-game "X/Y" exactly, independent of what's been synced.
            clogObtainedCount = client.getVarpValue(VARP_CLOG_OBTAINED);
            clogTotalCount = client.getVarpValue(VARP_CLOG_TOTAL);
            if (clogTotalCount > 0)
            {
                panel.setStatusClog(clogObtainedCount, clogTotalCount);
            }
            // Build category mapping and sync catalog every time clog opens
            buildClogCategoryMap();
            // Collection log opened — trigger search on next tick to enumerate all items
            clogSyncItems.clear();
            // clog dedup handled by Set keys + enum-3721 canonical remap
            clogRawEventCount = 0;
            clogSearchPending = true;
            panel.setClogSyncStatus("Scanning collection log...");
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() != SCRIPT_CLOG_ITEM || !isPlatformConfigured() || !config.enableClogSync())
        {
            return;
        }

        // Script 4100 fires per obtained item: args[1] = itemId, args[2] = quantity
        Object[] args = event.getScriptEvent().getArguments();
        if (args == null || args.length < 2)
        {
            return;
        }

        int itemId = remapClogId((int) args[1]);
        int quantity = args.length >= 3 ? (int) args[2] : 1;
        clogRawEventCount++;
        String itemName = itemManager.getItemComposition(itemId).getName();

        if (itemName == null || itemName.isEmpty() || itemName.equals("null"))
        {
            return;
        }
        if (!clogSyncItems.containsKey(itemId))
        {
            String tab = null;
            String category = null;
            if (clogItemCategoryMap != null)
            {
                String[] meta = clogItemCategoryMap.get(itemId);
                if (meta != null)
                {
                    tab = meta[0];
                    category = meta[1];
                }
            }
            clogSyncItems.put(itemId, new ClogItem(itemName, itemId, tab, category, quantity));
            panel.updateClogSyncCount(clogSyncItems.size());
        }
        // Reset debounce — upload after CLOG_DEBOUNCE_TICKS with no new items
        clogDebounceTicksRemaining = CLOG_DEBOUNCE_TICKS;
    }

    /**
     * Build the bad->canonical item-id remap from game enum 3721. Some collection-log slots
     * have two item ids (an old one carrying save data + a newer "good" one introduced to fix
     * item-dupe bugs). The game ships this enum so clients can normalise; using it (instead of a
     * hand-maintained skip list) makes our unique counts match the game's varp 2943/2944 exactly.
     */
    private void buildClogDupeRemap()
    {
        try
        {
            EnumComposition remap = client.getEnum(CLOG_DUPE_REMAP_ENUM);
            int[] badIds = remap.getKeys();
            int[] goodIds = remap.getIntVals();
            Map<Integer, Integer> m = new HashMap<>();
            for (int i = 0; i < badIds.length && i < goodIds.length; i++)
            {
                m.put(badIds[i], goodIds[i]);
            }
            clogDupeRemap = m;
        }
        catch (Exception e)
        {
            log.warn("Failed to build clog dupe remap (enum {})", CLOG_DUPE_REMAP_ENUM, e);
        }
    }

    /** Normalise a collection-log item id to its canonical id via the game's dupe-remap enum. */
    private int remapClogId(int itemId)
    {
        return clogDupeRemap.getOrDefault(itemId, itemId);
    }

    private void buildClogCategoryMap()
    {
        try
        {
            buildClogDupeRemap();
            Map<Integer, String[]> map = new HashMap<>();
            // Catalog entries: each (itemId, category) pair is a separate entry
            // so items like Dragon pickaxe appear under every boss that drops them
            JsonArray catalogItems = new JsonArray();
            Set<String> catalogSeen = new HashSet<>(); // "itemId::category" dedup
            int sortOrder = 0;

            EnumComposition tabsEnum = client.getEnum(CLOG_TABS_ENUM);
            int[] tabStructIds = tabsEnum.getIntVals();

            for (int tabStructId : tabStructIds)
            {
                StructComposition tabStruct = client.getStructComposition(tabStructId);
                String tabName = tabStruct.getStringValue(PARAM_TAB_NAME);
                int categoriesEnumId = tabStruct.getIntValue(PARAM_TAB_CATEGORIES_ENUM);

                EnumComposition categoriesEnum = client.getEnum(categoriesEnumId);
                int[] categoryStructIds = categoriesEnum.getIntVals();

                for (int catStructId : categoryStructIds)
                {
                    StructComposition catStruct = client.getStructComposition(catStructId);
                    String categoryName = catStruct.getStringValue(PARAM_CATEGORY_NAME);
                    int itemsEnumId = catStruct.getIntValue(PARAM_CATEGORY_ITEMS_ENUM);

                    EnumComposition itemsEnum = client.getEnum(itemsEnumId);
                    int[] itemIds = itemsEnum.getIntVals();

                    for (int rawItemId : itemIds)
                    {
                        int itemId = remapClogId(rawItemId);
                        map.put(itemId, new String[]{tabName, categoryName});

                        String catItemName = itemManager.getItemComposition(itemId).getName();
                        if (catItemName == null || catItemName.equals("null")) continue;

                        String dedupKey = itemId + "::" + categoryName;
                        if (!catalogSeen.add(dedupKey)) continue;

                        JsonObject item = new JsonObject();
                        item.addProperty("itemId", itemId);
                        item.addProperty("itemName", catItemName);
                        item.addProperty("tab", tabName);
                        item.addProperty("category", categoryName);
                        item.addProperty("sortOrder", sortOrder++);
                        catalogItems.add(item);
                    }
                }
            }

            clogItemCategoryMap = map;
            log.info("Built collection log category map: {} unique item IDs, {} catalog entries",
                map.size(), catalogItems.size());

            String catBaseUrl = getPlatformUrl();
            String catApiKey = getPlatformKey();
            String catSlug = getPlatformSlug();
            executor.submit(() -> platformApiService.syncCatalogResolved(
                catBaseUrl, catApiKey, catSlug, catalogItems
            ));
        }
        catch (Exception e)
        {
            log.warn("Failed to build collection log category map", e);
        }
    }

    private boolean pbReadPending = false;

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        // Script 2731 = COLLECTION_DRAW_LIST — fires when a category page is loaded in the clog
        if (event.getScriptId() != 2731 || !isPlatformConfigured() || !config.enableSpeedTimes())
        {
            return;
        }
        // Defer reading by 1 tick so the header text has time to populate
        pbReadPending = true;
    }

    private void readClogPb()
    {
        // The collection log is open and a category was just selected.
        // Read the header text and the selected category name.

        // Header text: group 621, child 20
        Widget headerWidget = client.getWidget(621, 20);
        if (headerWidget == null)
        {
            return;
        }

        // The header text is in dynamic children, not getText() on the parent
        // Try reading from children first, fall back to parent text
        StringBuilder headerBuilder = new StringBuilder();
        Widget[] headerChildren = headerWidget.getDynamicChildren();
        if (headerChildren != null && headerChildren.length > 0)
        {
            for (Widget child : headerChildren)
            {
                String t = child.getText();
                if (t != null && !t.isEmpty())
                {
                    headerBuilder.append(t).append(" ");
                }
            }
        }
        if (headerBuilder.length() == 0)
        {
            String t = headerWidget.getText();
            if (t != null) headerBuilder.append(t);
        }

        String headerText = Text.removeTags(headerBuilder.toString().trim());
        if (headerText.isEmpty())
        {
            return;
        }

        log.debug("Collection log header text: {}", headerText);

        // Parse fastest time
        java.util.regex.Matcher pbMatcher = CLOG_PB_PATTERN.matcher(headerText);
        if (!pbMatcher.find())
        {
            return;
        }

        String timeStr = pbMatcher.group(1);
        int timeMs = parsePbTime(timeStr);
        if (timeMs <= 0)
        {
            return;
        }

        // Get the page/boss name from the MAIN widget title area (group 621, child 17 = MAIN)
        // or from the HEADER widget itself — the first line is typically the boss name
        // Try getting the category from the header: first line before "Kill Count:"
        String bossName = null;
        java.util.regex.Matcher nameMatcher = Pattern.compile("^(.+?)(?:\\s*Kill Count|\\s*Completions|\\s*Fastest)").matcher(headerText);
        if (nameMatcher.find())
        {
            bossName = nameMatcher.group(1).trim();
        }

        if (bossName == null || bossName.isEmpty())
        {
            // Fallback: try reading from the category list
            Widget listWidget = client.getWidget(621, 9);
            if (listWidget != null)
            {
                Widget[] listChildren = listWidget.getDynamicChildren();
                if (listChildren != null)
                {
                    for (Widget child : listChildren)
                    {
                        String text = Text.removeTags(child.getText()).trim();
                        if (!text.isEmpty() && (child.getTextColor() == 0xff981f || child.getTextColor() == 0xffffff))
                        {
                            bossName = text;
                            break;
                        }
                    }
                }
            }
        }

        if (bossName == null || bossName.isEmpty())
        {
            return;
        }

        String rsn = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName() : null;
        if (rsn == null)
        {
            return;
        }

        String rawKey = bossName.toLowerCase().replace(" ", "_")
            .replace("'", "").replaceAll("[^a-z0-9_]", "");
        String group = BossCategory.mapAdventureLogName(rawKey);
        BossCategory cat = BossCategory.find(group, 1);
        String bossKey = cat != null ? cat.getKey() : group;

        log.info("Collection log PB detected: {} — {} ({}ms, key={})", bossName, timeStr, timeMs, bossKey);

        executor.submit(() -> platformApiService.submitPb(
            getPlatformUrl(),
            getPlatformKey(),
            getPlatformSlug(),
            rsn,
            bossKey,
            1, // solo
            timeMs,
            "adventure_log"
        ));
    }

    private static int parsePbTime(String timeStr)
    {
        // Formats: "1:23.40", "12:34.50", "1:23", "0:45.60", "1:23:45.60" (h:mm:ss.cc)
        try
        {
            String[] parts = timeStr.split(":");
            if (parts.length == 3)
            {
                // H:MM:SS.cc
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                return (int) (hours * 3600000 + minutes * 60000 + seconds * 1000);
            }
            else if (parts.length == 2)
            {
                // MM:SS.cc or MM:SS
                int minutes = Integer.parseInt(parts[0]);
                double seconds;
                if (parts[1].contains("."))
                {
                    seconds = Double.parseDouble(parts[1]);
                }
                else
                {
                    seconds = Integer.parseInt(parts[1]);
                }
                return (int) (minutes * 60000 + seconds * 1000);
            }
            return -1;
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    /**
     * Parse ALL personal bests from the adventure log Counters page (group 741).
     * The widget contains dynamic children with sequential boss names and time entries.
     */
    private void parseAdventureLogPbs()
    {
        String rsn = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName() : null;
        if (rsn == null)
        {
            return;
        }

        // Find the container widget with dynamic children (scroll content)
        // Try dynamic children first, then static children
        Widget[] children = null;
        for (int child = 0; child < 30; child++)
        {
            Widget w = client.getWidget(JOURNALSCROLL_GROUP, child);
            if (w == null) continue;

            Widget[] dynChildren = w.getDynamicChildren();
            if (dynChildren != null && dynChildren.length > 10)
            {
                log.info("Adventure log: found {} dynamic children in widget 741.{}", dynChildren.length, child);
                children = dynChildren;
                break;
            }

            Widget[] statChildren = w.getStaticChildren();
            if (statChildren != null && statChildren.length > 10)
            {
                log.info("Adventure log: found {} static children in widget 741.{}", statChildren.length, child);
                children = statChildren;
                break;
            }
        }

        if (children == null)
        {
            log.warn("Adventure log Counters: no content widget found in group 741");
            return;
        }
        if (children == null || children.length == 0)
        {
            return;
        }

        // Section headers to skip (not boss names)
        Set<String> sectionHeaders = new HashSet<>();
        sectionHeaders.add("Minigames");
        sectionHeaders.add("Bosses");
        sectionHeaders.add("Skilling Bosses");
        sectionHeaders.add("Raids");

        // Log ALL widget children for debugging
        StringBuilder allText = new StringBuilder("Adventure log ALL children:\n");
        for (int i = 0; i < children.length; i++)
        {
            String t = children[i].getText();
            if (t != null && !t.isEmpty())
            {
                allText.append("[").append(i).append("] '").append(Text.removeTags(t).trim()).append("'\n");
            }
        }
        log.info(allText.toString());

        List<PbEntry> parsedPbs = new ArrayList<>();
        String currentBoss = null;
        // For ToB/ToA: pending team size from a "Fastest Room time" line where time is on the next line
        String pendingTeamSize = null;
        boolean pendingIsRoom = false;
        // Track which boss+teamSize combos we've added as "Room time" so we skip "Overall time" dupes
        Set<String> roomTimeKeys = new HashSet<>();

        for (Widget child : children)
        {
            String text = child.getText();
            if (text == null || text.isEmpty()) continue;
            String clean = Text.removeTags(text).trim();
            if (clean.isEmpty() || clean.length() <= 2) continue;

            // Skip section headers
            if (sectionHeaders.contains(clean)) {
                currentBoss = null;
                pendingTeamSize = null;
                continue;
            }

            // Check if this is a standalone time on its own line (continuation from previous)
            Matcher standaloneMatcher = STANDALONE_TIME.matcher(clean);
            if (standaloneMatcher.find() && pendingTeamSize != null && currentBoss != null)
            {
                if (pendingIsRoom)
                {
                    String timeStr = standaloneMatcher.group(1);
                    int timeMs = parsePbTime(timeStr);
                    if (timeMs > 0)
                    {
                        int teamSize = parseTeamSize(pendingTeamSize);
                        parsedPbs.add(new PbEntry(currentBoss, teamSize, timeMs));
                        roomTimeKeys.add(currentBoss + "::" + teamSize);
                    }
                }
                pendingTeamSize = null;
                pendingIsRoom = false;
                continue;
            }

            // Try to parse as a PB time entry
            Matcher pbMatcher = ADVENTURE_PB_PATTERN.matcher(clean);
            if (pbMatcher.find())
            {
                if (currentBoss == null) continue;

                boolean isOverall = clean.contains("Overall time");
                boolean isRoom = clean.contains("Room time");

                String teamSizeStr = pbMatcher.group(1);
                String timeStr = pbMatcher.group(2); // may be null if time is on next line

                if (timeStr == null)
                {
                    // Time is on the next widget child line — save context and continue
                    pendingTeamSize = teamSizeStr;
                    pendingIsRoom = isRoom;
                    continue;
                }

                // For ToB/ToA: use "Room time" (challenge time), skip "Overall time"
                if (isOverall)
                {
                    continue;
                }

                // Skip legacy "former" entries
                if (clean.contains("(former)"))
                {
                    continue;
                }

                int timeMs = parsePbTime(timeStr);
                if (timeMs <= 0) continue;

                int teamSize = parseTeamSize(teamSizeStr);

                // Don't duplicate if we already have a Room time for this combo
                String comboKey = currentBoss + "::" + teamSize;
                if (isRoom)
                {
                    roomTimeKeys.add(comboKey);
                }
                // Always add room times; skip non-room if we already have room time
                if (isRoom || !roomTimeKeys.contains(comboKey))
                {
                    parsedPbs.add(new PbEntry(currentBoss, teamSize, timeMs));
                }

                pendingTeamSize = null;
                pendingIsRoom = false;
            }
            else if (!clean.contains("Kill Count") && !clean.contains("Completions")
                      && !clean.contains("Personal Best") && !clean.contains("Kills")
                      && !clean.contains("(former)"))
            {
                // This line is a boss/activity name
                currentBoss = clean;
                pendingTeamSize = null;
                pendingIsRoom = false;
            }
        }

        if (parsedPbs.isEmpty())
        {
            log.debug("Adventure log Counters: no PBs found");
            return;
        }

        log.info("Adventure log PBs parsed: {} entries for {}", parsedPbs.size(), rsn);

        // Show chat confirmation
        final int pbCount = parsedPbs.size();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[Clan Management] Syncing " + pbCount + " personal bests...", "");

        // Submit all PBs to the platform API
        final String playerName = rsn;
        executor.submit(() ->
        {
            int submitted = 0;
            for (PbEntry pb : parsedPbs)
            {
                String rawKey = pb.bossName.toLowerCase().replace(" ", "_")
                    .replace("'", "").replaceAll("[^a-z0-9_]", "");
                String group = BossCategory.mapAdventureLogName(rawKey);

                // Try to resolve to a proper BossCategory key
                BossCategory cat = BossCategory.find(group, pb.teamSize);
                String bossKey = cat != null ? cat.getKey() : group;

                log.info("Adventure log PB: '{}' → raw='{}' → group='{}' → key='{}' (size={}, time={}ms)",
                    pb.bossName, rawKey, group, bossKey, pb.teamSize, pb.timeMs);

                platformApiService.submitPb(
                    getPlatformUrl(),
                    getPlatformKey(),
                    getPlatformSlug(),
                    playerName,
                    bossKey,
                    pb.teamSize,
                    pb.timeMs,
                    "adventure_log"
                );
                submitted++;
            }
            log.info("Submitted {} PBs to platform for {}", submitted, playerName);
            final int finalSubmitted = submitted;
            clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[Clan Management] Synced " + finalSubmitted + " personal bests to platform", "")
            );
        });
    }

    private static int parseTeamSize(String teamSizeStr)
    {
        if (teamSizeStr == null) return 1; // No team size specified = solo
        String s = teamSizeStr.trim();
        // Strip leading "(" from ToA's malformed "(2 player)" format
        if (s.startsWith("(")) s = s.substring(1).trim();
        if (s.equalsIgnoreCase("Solo")) return 1;
        // "2 players", "3 players", "1 player entry mode", "5 player hard mode", etc.
        Matcher m = Pattern.compile("(\\d+)\\s*(?:\\+\\s*)?players?").matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1));
        // "11-15 players" range — use max
        Matcher rangeMatcher = Pattern.compile("(\\d+)-(\\d+)\\s*players?").matcher(s);
        if (rangeMatcher.find()) return Integer.parseInt(rangeMatcher.group(2));
        // "24+" or "6+" etc.
        Matcher plusMatcher = Pattern.compile("(\\d+)\\+").matcher(s);
        if (plusMatcher.find()) return Integer.parseInt(plusMatcher.group(1));
        // Just a number
        Matcher numMatcher = Pattern.compile("(\\d+)").matcher(s);
        if (numMatcher.find()) return Integer.parseInt(numMatcher.group(1));
        return 1;
    }

    private static class PbEntry
    {
        final String bossName;
        final int teamSize;
        final int timeMs;

        PbEntry(String bossName, int teamSize, int timeMs)
        {
            this.bossName = bossName;
            this.teamSize = teamSize;
            this.timeMs = timeMs;
        }
    }

    private void triggerClogSearch()
    {
        // Auto-trigger the search toggle in the collection log to enumerate ALL obtained items
        // This causes script 4100 to fire for every obtained item
        try
        {
            client.menuAction(-1, SEARCH_TOGGLE_PACKED, MenuAction.CC_OP, 1, -1, "Search", null);
            client.runScript(2240);
            log.info("Collection log auto-search triggered");
        }
        catch (Exception e)
        {
            log.warn("Failed to trigger collection log search", e);
        }
    }

    private void uploadCollectionLog()
    {
        if (clogSyncItems.isEmpty())
        {
            return;
        }

        String rsn = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName() : "Unknown";
        List<ClogItem> items = new ArrayList<>(clogSyncItems.values());
        int count = items.size();

        panel.setClogSyncStatus("Uploading " + count + " items...");

        executor.submit(() -> platformApiService.bulkSyncCollectionLog(
            getPlatformUrl(),
            getPlatformKey(),
            getPlatformSlug(),
            rsn,
            items,
            clogObtainedCount,
            clogTotalCount,
            new okhttp3.Callback()
            {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e)
                {
                    log.error("Collection log auto-sync failed", e);
                    panel.setClogSyncStatus("Sync failed: " + e.getMessage());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response)
                {
                    response.close();
                    if (response.isSuccessful())
                    {
                        log.info("Collection log synced: {} items for {}", count, rsn);
                        panel.setClogSyncStatus("Synced " + count + " items");
                    }
                    else
                    {
                        panel.setClogSyncStatus("Sync failed: HTTP " + response.code());
                    }
                }
            }
        ));
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

        if (value < fetchedMinDropValue)
        {
            return;
        }

        // Only log items that are on the clan whitelist (if whitelist is loaded)
        if (cachedClanWhitelist != null && !cachedClanWhitelist.isEmpty())
        {
            boolean onWhitelist = false;
            for (Map<String, String> entry : cachedClanWhitelist)
            {
                String whitelistName = entry.get("item");
                if (whitelistName != null && whitelistName.equalsIgnoreCase(itemName))
                {
                    onWhitelist = true;
                    break;
                }
            }
            if (!onWhitelist)
            {
                log.debug("Drop '{}' not on whitelist, skipping", itemName);
                return;
            }
        }

        if (!isPlatformConfigured())
        {
            return;
        }

        String playerName = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName()
            : "Unknown";

        WorldPoint wp = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation()
            : new WorldPoint(0, 0, 0);

        // Only attribute to last NPC if killed recently (within 30s) — avoids
        // clue casket drops being attributed to a stale NPC like "Brassican Mage"
        String npcSource = (System.currentTimeMillis() - lastKillTime < 30_000)
            ? lastKilledNpc : "Unknown";

        DropEntry drop = new DropEntry(
            itemName, value, npcSource, lastKillCount,
            wp.getX(), wp.getY(), wp.getPlane(), playerName
        );

        // Capture screenshot for Discord
        final BufferedImage[] screenshotHolder = {null};
        if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
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
                log.warn("Failed to capture drop screenshot", e);
            }
        }

        // Delay slightly so the requested screenshot frame lands before we submit/post.
        // Uses the scheduler's delay instead of Thread.sleep (disallowed in Plugin Hub plugins).
        executor.schedule(() ->
        {
            platformApiService.submitDrop(
                getPlatformUrl(),
                getPlatformKey(),
                getPlatformSlug(),
                drop
            );
            log.debug("Drop logged: {} ({} gp)", itemName, value);

            // Post to Discord with screenshot
            if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
            {
                discordService.postDrop(fetchedDiscordWebhookUrl, drop, screenshotHolder[0]);
            }
        }, 500, TimeUnit.MILLISECONDS);

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

        if (isPlatformConfigured())
        {
            final String pRsn = playerName;
            final String pItem = itemName;
            executor.submit(() -> platformApiService.submitCollectionLogEntry(
                getPlatformUrl(),
                getPlatformKey(),
                getPlatformSlug(),
                pRsn,
                pItem
            ));
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
        boolean allClanMembers = !isGroupContent || validateClanMembership(partyMembers);
        if (!allClanMembers)
        {
            log.info("Not all party members in clan chat — PB will be submitted as unverified");
            if (config.chatConfirmation())
            {
                clientThread.invokeLater(() ->
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "[" + getClanName() + "] Time recorded (unverified — not all party members in clan chat)", "")
                );
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
        if (config.enableSpeedTimes() && fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
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

        final int finalPartySize = partySize;
        final String finalCategoryName = categoryName;
        final boolean finalAllClan = allClanMembers;

        // Delay slightly so the requested screenshot frame lands before we submit/post.
        // Uses the scheduler's delay instead of Thread.sleep (disallowed in Plugin Hub plugins).
        executor.schedule(() ->
        {
            // Submit PB to platform API — one entry per party member
            // "live" = all party members in clan chat (clan-verified)
            // "unverified" = not all members in clan chat
            if (isPlatformConfigured())
            {
                int timeMs = (int) (timeSeconds * 1000);
                String source = finalAllClan ? "live" : "unverified";
                for (String member : sortedMembers)
                {
                    platformApiService.submitPb(
                        getPlatformUrl(),
                        getPlatformKey(),
                        getPlatformSlug(),
                        member.trim(),
                        categoryKey,
                        finalPartySize,
                        timeMs,
                        source
                    );
                }
                log.debug("Speed time submitted for {}: {}", categoryKey, formattedTime);

                // Invalidate cache so next UI view fetches fresh data
                hiscoreCacheV2.remove(categoryKey);
                saveHiscoreCacheV2ToDisk();

                // Chat notification
                if (config.chatConfirmation())
                {
                    String msg = String.format("[%s] Speed time recorded: %s in %s",
                        getClanName(), formattedTime, finalCategoryName);
                    clientThread.invokeLater(() ->
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "")
                    );
                }

                // Post to Discord (clan-verified only)
                if (finalAllClan && fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
                {
                    discordService.postPb(fetchedDiscordWebhookUrl, formattedTime, 0,
                        finalCategoryName, rsns, screenshotHolder[0]);
                }
            }
        }, 500, TimeUnit.MILLISECONDS);
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
        if (isNonStandardWorld()) return;

        NPC npc = event.getNpc();
        if (npc != null)
        {
            lastKilledNpc = npc.getName();
            lastKillCount = pbDetector.getLastKillCount();
            lastKillTime = System.currentTimeMillis();
        }
    }

    private void startDataRefresh()
    {
        if (refreshTask != null)
        {
            refreshTask.cancel(false);
        }

        int interval = 60;
        refreshTask = executor.scheduleAtFixedRate(
            this::refreshData, 10, interval, TimeUnit.SECONDS);
    }

    private void refreshData()
    {
        if (!isPlatformConfigured())
        {
            panel.setConnected(false);
            panel.setStatus("Enter your Clan Code in plugin settings");
            return;
        }

        // Fetch bootstrap config from platform (discord webhook, min drop value, active event)
        fetchBootstrapConfig();

        // Load platform config on first successful connection
        if (!serverConfigLoaded)
        {
            try
            {
                serverConfigLoaded = true;
                panel.setConnected(true);
                log.info("Platform connected — clan={}", getClanName());

                // Auto-load drops tab on first config load
                executor.submit(this::refreshDropsTab);

                // Auto-sync roster on login if admin
                String adminApiKey = config.adminApiKey();
                if (!adminApiKey.isEmpty())
                {
                    clientThread.invokeLater(() -> hiscoreTracker.onLoginIfAdmin(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), adminApiKey));
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to initialize platform connection — will retry next refresh", e);
                serverConfigLoaded = false;
            }
        }

        // Auto-refresh WOM data on same cycle
        refreshWomData();
        refreshClanActivity();
        refreshEventLeaderboard();
        refreshBingo();
        refreshStatusBoxes();
    }

    private void refreshEventLeaderboard()
    {
        if (activeEventType.isEmpty() || activeEventMetric.isEmpty())
        {
            panel.updateActiveEvent(null, null, null, null);
            if (adminPanel != null) adminPanel.setActiveEvent(null, null, null);
            return;
        }

        if (!womService.isConfigured())
        {
            panel.updateActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime, null);
            if (adminPanel != null) adminPanel.setActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime);
            return;
        }

        try
        {
            List<WomService.WomEntry> entries = womService.fetchGained(activeEventMetric, "week");
            panel.updateActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime, entries);
            if (adminPanel != null) adminPanel.setActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime);
        }
        catch (Exception e)
        {
            log.debug("Failed to fetch event leaderboard", e);
            panel.updateActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime, null);
            if (adminPanel != null) adminPanel.setActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime);
        }
    }

    private void refreshBingo()
    {
        // Bingo is archived — always return early
        if (true) {
            if (bingoActive) {
                panel.hideBingoTab();
                bingoActive = false;
                if (bountyScheduler != null) bountyScheduler.cancel();
            }
            return;
        }

        try
        {
            // Fetch config (cached for 5 min)
            bingoConfig = bingoService.fetchBingoConfig();

            // Check if event is active based on dates
            boolean shouldBeActive = isBingoEventActive(bingoConfig);

            if (shouldBeActive && !bingoActive)
            {
                // Show bingo tab
                panel.showBingoTab(bingoPanel);
                bingoActive = true;

                // Set up bounty scheduler
                bountyScheduler = new BountyScheduler(executor, bingoService);
                bountyScheduler.setOnHint((bounty, message) -> {
                    clientThread.invokeLater(() ->
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, ""));
                    if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
                    {
                        discordService.postBountyHint(fetchedDiscordWebhookUrl, bounty,
                            bingoConfig.getHintMinutesBefore());
                    }
                });
                bountyScheduler.setOnRelease((bounty, message) -> {
                    clientThread.invokeLater(() ->
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, ""));
                    if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
                    {
                        discordService.postBountyLive(fetchedDiscordWebhookUrl, bounty);
                    }
                });
                bountyScheduler.schedule(bingoConfig.getBounties(), bingoConfig.getHintMinutesBefore());
            }
            else if (!shouldBeActive && bingoActive)
            {
                panel.hideBingoTab();
                bingoActive = false;
                if (bountyScheduler != null) bountyScheduler.cancel();
            }

            if (!bingoActive) return;

            // Resolve player's team
            String playerName = getLocalPlayerName();
            if (playerName != null)
            {
                playerBingoTeam = bingoConfig.getRoster().getOrDefault(playerName.toLowerCase(), "");
            }

            // Find team name
            String playerTeamName = null;
            for (BingoTeam team : bingoConfig.getTeams())
            {
                if (team.getCode().equals(playerBingoTeam))
                {
                    playerTeamName = team.getName();
                    break;
                }
            }

            bingoPanel.updateConfig(bingoConfig, playerBingoTeam, playerTeamName);

            // Fetch team progress for player's team
            if (!playerBingoTeam.isEmpty())
            {
                Map<String, Double> progress = bingoService.fetchTeamProgress(playerBingoTeam);
                bingoPanel.updateTeamProgress(progress);
            }

            // Fetch standings
            BingoStandings standings = bingoService.fetchAllStandings();
            bingoPanel.updateStandings(standings, playerName, playerBingoTeam);

            // Fetch drops
            List<Map<String, Object>> drops = bingoService.fetchDroplog(playerBingoTeam, 20);
            bingoPanel.updateDropLog(drops);

            // Update bounty display
            String nextBounty = bountyScheduler != null ?
                bountyScheduler.getNextBountyCountdown(bingoConfig.getBounties()) : null;
            bingoPanel.updateBounties(bingoConfig.getBounties(), nextBounty);

            // Update countdown
            bingoPanel.updateCountdown(getBingoCountdown(bingoConfig));

            // Push WOM data for KC/XP tiles
            pushWomBingoProgress();
        }
        catch (Exception e)
        {
            log.debug("Failed to refresh bingo data", e);
        }
    }

    private boolean isBingoEventActive(BingoConfig cfg)
    {
        try
        {
            java.time.ZoneId est = java.time.ZoneId.of("America/New_York");
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(est);

            if (!cfg.getStartDate().isEmpty())
            {
                java.time.ZonedDateTime start = java.time.LocalDateTime.parse(cfg.getStartDate()).atZone(est);
                if (now.isBefore(start)) return false;
            }
            if (!cfg.getEndDate().isEmpty())
            {
                java.time.ZonedDateTime end = java.time.LocalDateTime.parse(cfg.getEndDate()).atZone(est);
                if (now.isAfter(end)) return false;
            }
            return true;
        }
        catch (Exception e)
        {
            return true; // If dates can't be parsed, show anyway
        }
    }

    private String getBingoCountdown(BingoConfig cfg)
    {
        try
        {
            java.time.ZoneId est = java.time.ZoneId.of("America/New_York");
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(est);

            if (!cfg.getEndDate().isEmpty())
            {
                java.time.ZonedDateTime end = java.time.LocalDateTime.parse(cfg.getEndDate()).atZone(est);
                java.time.Duration remaining = java.time.Duration.between(now, end);
                if (!remaining.isNegative())
                {
                    long days = remaining.toDays();
                    long hours = remaining.toHours() % 24;
                    long minutes = remaining.toMinutes() % 60;
                    return "Ends in " + days + "d " + hours + "h " + minutes + "m";
                }
            }
        }
        catch (Exception ignored) {}
        return "";
    }

    private void pushWomBingoProgress()
    {
        if (bingoConfig == null || !womService.isConfigured()) return;

        String playerName = getLocalPlayerName();
        if (playerName == null) return;

        // Only push if player is on a team
        if (playerBingoTeam.isEmpty()) return;

        // Find KC/XP tiles and group by metric
        Map<String, List<BingoTile>> metricTiles = new LinkedHashMap<>();
        for (BingoTile tile : bingoConfig.getTiles())
        {
            if (("kc".equals(tile.getType()) || "xp".equals(tile.getType())) && !tile.getMetric().isEmpty())
            {
                metricTiles.computeIfAbsent(tile.getMetric(), k -> new ArrayList<>()).add(tile);
            }
        }

        if (metricTiles.isEmpty()) return;

        for (Map.Entry<String, List<BingoTile>> entry : metricTiles.entrySet())
        {
            String metric = entry.getKey();
            try
            {
                List<WomService.WomEntry> gained = womService.fetchGained(metric, "week");
                // Sum per team
                Map<String, Double> teamGains = new LinkedHashMap<>();
                for (WomService.WomEntry we : gained)
                {
                    String team = bingoConfig.getRoster().getOrDefault(we.username.toLowerCase(), "");
                    if (!team.isEmpty())
                    {
                        teamGains.merge(team, (double) we.gained, Double::sum);
                    }
                }

                // Push to sheet for each team/tile
                for (BingoTile tile : entry.getValue())
                {
                    for (Map.Entry<String, Double> tg : teamGains.entrySet())
                    {
                        bingoService.updateTileProgress(tg.getKey(), tile.getCode(), tg.getValue());
                    }
                }
            }
            catch (Exception e)
            {
                log.debug("Failed to push WOM progress for metric: {}", metric, e);
            }
        }
    }

    private String getLocalPlayerName()
    {
        if (client.getLocalPlayer() != null)
        {
            return client.getLocalPlayer().getName();
        }
        return null;
    }

    /**
     * Batch-fetch all speed times, populate entire cache.
     * Prefers platform API; falls back to Google Sheet v2 API.
     */
    private void batchFetchAllHiscores()
    {
        // Try platform API first
        if (isPlatformConfigured())
        {
            try
            {
                Map<String, List<HiscoreEntry>> allTimes = platformApiService.fetchAllPbs(
                    getPlatformUrl(), getPlatformKey(), getPlatformSlug());
                if (allTimes != null)
                {
                    hiscoreCacheV2.putAll(allTimes);
                    hiscoreV2BatchFetched = true;
                    saveHiscoreCacheV2ToDisk();
                    panel.setRecentCategories(new java.util.LinkedHashSet<>(hiscoreCacheV2.keySet()), new java.util.LinkedHashMap<>(hiscoreCacheV2));
                    log.info("Batch-fetched speed times from platform API: {} categories", allTimes.size());
                    return;
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to batch-fetch speed times from platform API", e);
            }
        }

        // Fallback to Google Sheet v2 API
        String v2Url = fetchedClanDropLogUrl;
        String apiKey = getApiKey();

        if (v2Url == null || v2Url.isEmpty())
        {
            log.debug("No speed times API configured — skipping batch fetch");
            return;
        }

        try
        {
            Map<String, List<HiscoreEntry>> allTimes = hiscoreService.fetchAllTopTimes(v2Url, apiKey);
            hiscoreCacheV2.putAll(allTimes);
            hiscoreV2BatchFetched = true;
            saveHiscoreCacheV2ToDisk();
            panel.setRecentCategories(new java.util.LinkedHashSet<>(hiscoreCacheV2.keySet()), new java.util.LinkedHashMap<>(hiscoreCacheV2));
            log.info("Batch-fetched speed times: {} categories", allTimes.size());
        }
        catch (Exception e)
        {
            log.warn("Failed to batch-fetch speed times", e);
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
                ? "Speed Times API not configured"
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

    private void refreshStatusBoxes()
    {
        if (!isPlatformConfigured()) return;

        String baseUrl = getPlatformUrl();
        String apiKey = getPlatformKey();
        String slug = getPlatformSlug();
        String rsn = getLocalPlayerName();
        if (rsn == null || rsn.isEmpty()) return;

        String encodedRsn = rsn.replace(" ", "%20");

        // Collection log count
        try
        {
            JsonObject clogData = platformApiService.getSync(
                baseUrl + "/clans/" + slug + "/collection-log/" + encodedRsn, apiKey);
            if (clogData != null)
            {
                // Prefer the authoritative game counts (varp 2943/2944) the plugin synced;
                // fall back to reconstructed counts only if the backend hasn't got them yet.
                int obtained = clogData.has("obtained") ? clogData.get("obtained").getAsInt()
                    : (clogData.has("total") ? clogData.get("total").getAsInt() : 0);
                int totalSlots = 0;
                if (clogData.has("totalSlots") && !clogData.get("totalSlots").isJsonNull())
                {
                    totalSlots = clogData.get("totalSlots").getAsInt();
                }
                else if (clogData.has("catalog") && clogData.get("catalog").isJsonArray())
                {
                    Set<Integer> catalogIds = new HashSet<>();
                    for (var el : clogData.getAsJsonArray("catalog"))
                    {
                        JsonObject ci = el.getAsJsonObject();
                        if (ci.has("itemId")) catalogIds.add(ci.get("itemId").getAsInt());
                    }
                    totalSlots = catalogIds.size();
                }
                panel.setStatusClog(obtained, totalSlots);
            }
        }
        catch (Exception e)
        {
            log.debug("Status box clog fetch failed", e);
        }

        // Stats (total XP)
        try
        {
            JsonObject statsData = platformApiService.getSync(
                baseUrl + "/clans/" + slug + "/stats/" + encodedRsn, apiKey);
            if (statsData != null && statsData.has("skills"))
            {
                long totalXp = 0;
                for (var entry : statsData.getAsJsonObject("skills").entrySet())
                {
                    JsonObject skill = entry.getValue().getAsJsonObject();
                    if (skill.has("xp"))
                    {
                        totalXp += skill.get("xp").getAsLong();
                    }
                }
                panel.setStatusXp(totalXp);
            }
        }
        catch (Exception e)
        {
            log.debug("Status box stats fetch failed", e);
        }

        // Hiscores (any PBs?)
        panel.setStatusHiscores(!panel.getRecentCategoryKeys().isEmpty());
    }

    private void refreshWomData()
    {
        doFetchWomData(lastWomMetric, lastWomPeriod);
    }

    private void doFetchWomData(String metric, String period)
    {
        try
        {
            List<WomService.WomEntry> entries = womService.fetchGained(metric, period);
            panel.updateWomLeaderboard(entries, true);
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
                panel.setRecentCategories(new java.util.LinkedHashSet<>(hiscoreCacheV2.keySet()), new java.util.LinkedHashMap<>(hiscoreCacheV2));
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

        // Load shared settings — uses platform bootstrap data
        adminPanel.setOnLoadSettings(() -> executor.submit(() -> {
            try
            {
                adminPanel.setStatus("Loading settings...");
                adminPanel.setClanName(getClanName());
                adminPanel.setStatus("Settings loaded from platform");
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        // Save shared settings — placeholder for future platform admin API
        adminPanel.setOnSaveSettings(args -> executor.submit(() -> {
            try
            {
                adminPanel.setStatus("Settings are managed from the web dashboard");
                adminPanel.setStatus("Settings saved");
                // Refresh local cached config
                serverConfigLoaded = false;
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        // Speed times moderation — managed via web dashboard
        adminPanel.setOnRemoveHiscore(args -> executor.submit(() -> {
            adminPanel.setStatus("Speed times are managed from the web dashboard");
            hiscoreCacheV2.remove(args[0]);
        }));

        // Rotate API key — managed via web dashboard
        adminPanel.setOnRotateApiKey(newKey -> executor.submit(() -> {
            adminPanel.setStatus("API keys are managed from the web dashboard");
        }));

        // Start weekly event
        adminPanel.setOnStartEvent(args -> executor.submit(() -> {
            try
            {
                String type = args[0];
                String metric = args[1];
                String displayName = args[2];
                adminPanel.setStatus("Starting event...");
                adminService.startEventPlatform(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), type, metric, displayName);
                adminPanel.setStatus("Event started: " + displayName);

                // Update local state
                activeEventType = type;
                activeEventMetric = metric;
                activeEventDisplayName = displayName;
                // End time will be fetched on next config refresh, but estimate for immediate display
                java.time.ZonedDateTime endZoned = java.time.ZonedDateTime.now(
                    java.time.ZoneId.of("America/New_York")).plusDays(7);
                activeEventEndTime = endZoned.toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));

                serverConfigLoaded = false; // Force config re-fetch

                // Post to Discord
                if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
                {
                    discordService.postEventStart(fetchedDiscordWebhookUrl, type, displayName, activeEventEndTime);
                }

                // Immediately refresh event display
                refreshEventLeaderboard();
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        // End weekly event
        adminPanel.setOnEndEvent(() -> executor.submit(() -> {
            try
            {
                adminPanel.setStatus("Ending event...");

                // Fetch final leaderboard for Discord before clearing
                List<WomService.WomEntry> finalLeaderboard = null;
                if (womService.isConfigured() && !activeEventMetric.isEmpty())
                {
                    try { finalLeaderboard = womService.fetchGained(activeEventMetric, "week"); }
                    catch (Exception ignored) {}
                }

                // Post results to Discord
                if (fetchedDiscordWebhookUrl != null && !fetchedDiscordWebhookUrl.isEmpty())
                {
                    discordService.postEventEnd(fetchedDiscordWebhookUrl, activeEventType,
                        activeEventDisplayName, finalLeaderboard);
                }

                adminService.endEventPlatform(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), activeEventId);
                adminPanel.setStatus("Event ended");

                // Clear local state
                activeEventType = "";
                activeEventMetric = "";
                activeEventDisplayName = "";
                activeEventEndTime = "";
                activeEventId = "";
                serverConfigLoaded = false;

                refreshEventLeaderboard();
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        adminPanel.setOnSyncRoster(() -> {
            int count = hiscoreTracker.syncRoster(getPlatformUrl(), getPlatformKey(), getPlatformSlug());
            if (count > 0)
            {
                adminPanel.setStatus("Synced " + count + " members");
            }
            else
            {
                adminPanel.setStatus("Roster sync failed — join a clan first");
            }
        });

        // Show active event state in admin panel on load
        if (!activeEventType.isEmpty())
        {
            adminPanel.setActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime);
        }

        log.info("Admin panel enabled");
    }
}
