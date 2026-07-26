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
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.Skill;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
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
import java.util.Arrays;
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
    private static final Pattern COLLECTION_LOG_PATTERN =
        Pattern.compile("New item added to your collection log: (.+)");

    // Pets aren't loot-table items (they arrive via a clog unlock, not LootReceived), so they're
    // matched by name here and posted as drops too. Add new pets to this set as they release.
    private static final Set<String> PET_NAMES = new HashSet<>(Arrays.asList(
        "abyssal orphan", "abyssal protector", "baby chinchompa", "baby mole", "baron",
        "beaver", "bloodhound", "butch", "callisto cub", "chompy chick", "giant squirrel",
        "great blue heron", "hellpuppy", "heron", "herbi", "huberte", "ikkle hydra",
        "jal-nib-rek", "kalphite princess", "lil' creator", "lil' zik", "lil'viathan",
        "little nightmare", "muphin", "nexling", "nid", "noon", "olmlet",
        "pet chaos elemental", "pet dagannoth prime", "pet dagannoth rex", "pet dagannoth supreme",
        "pet dark core", "pet general graardor", "pet k'ril tsutsaroth", "pet kraken",
        "pet kree'arra", "pet penance queen", "pet smoke devil", "pet snakeling", "pet zilyana",
        "phoenix", "prince black dragon", "quetzin", "rift guardian", "rock golem", "rocky",
        "scorpia's offspring", "scurry", "skotos", "smol heredit", "smolcano", "sraracha",
        "tangleroot", "tiny tempor", "tumeken's guardian", "tzrek-jad", "venenatis spiderling",
        "vet'ion jr.", "vorki", "wisp", "youngllef",
        // 2025-2026 pets
        "yami", "bran", "dom", "moxi"
    ));

    // A DUPLICATE pet ("You have a funny feeling like you would have been followed") fires no
    // clog unlock and no loot event — the only identification is the boss context from the
    // kill-count line. Keys must match the boss name as it appears in KC chat messages.
    private static final Map<String, String> BOSS_PET = new HashMap<>();
    static
    {
        BOSS_PET.put("yama", "Yami");
        BOSS_PET.put("kraken", "Pet kraken");
        BOSS_PET.put("cerberus", "Hellpuppy");
        BOSS_PET.put("vorkath", "Vorki");
        BOSS_PET.put("zulrah", "Pet snakeling");
        BOSS_PET.put("grotesque guardians", "Noon");
        BOSS_PET.put("abyssal sire", "Abyssal orphan");
        BOSS_PET.put("alchemical hydra", "Ikkle hydra");
        BOSS_PET.put("sarachnis", "Sraracha");
        BOSS_PET.put("kalphite queen", "Kalphite princess");
        BOSS_PET.put("general graardor", "Pet general graardor");
        BOSS_PET.put("k'ril tsutsaroth", "Pet k'ril tsutsaroth");
        BOSS_PET.put("commander zilyana", "Pet zilyana");
        BOSS_PET.put("kree'arra", "Pet kree'arra");
        BOSS_PET.put("nex", "Nexling");
        BOSS_PET.put("giant mole", "Baby mole");
        BOSS_PET.put("dagannoth rex", "Pet dagannoth rex");
        BOSS_PET.put("dagannoth prime", "Pet dagannoth prime");
        BOSS_PET.put("dagannoth supreme", "Pet dagannoth supreme");
        BOSS_PET.put("corporeal beast", "Pet dark core");
        BOSS_PET.put("king black dragon", "Prince black dragon");
        BOSS_PET.put("thermonuclear smoke devil", "Pet smoke devil");
        BOSS_PET.put("scorpia", "Scorpia's offspring");
        BOSS_PET.put("callisto", "Callisto cub");
        BOSS_PET.put("artio", "Callisto cub");
        BOSS_PET.put("venenatis", "Venenatis spiderling");
        BOSS_PET.put("spindel", "Venenatis spiderling");
        BOSS_PET.put("vet'ion", "Vet'ion jr.");
        BOSS_PET.put("calvar'ion", "Vet'ion jr.");
        BOSS_PET.put("chaos elemental", "Pet chaos elemental");
        BOSS_PET.put("skotizo", "Skotos");
        BOSS_PET.put("araxxor", "Nid");
        BOSS_PET.put("phantom muspah", "Muphin");
        BOSS_PET.put("the nightmare", "Little nightmare");
        BOSS_PET.put("phosani's nightmare", "Little nightmare");
        BOSS_PET.put("duke sucellus", "Baron");
        BOSS_PET.put("vardorvis", "Butch");
        BOSS_PET.put("the leviathan", "Lil'viathan");
        BOSS_PET.put("the whisperer", "Wisp");
        BOSS_PET.put("the hueycoatl", "Huberte");
        BOSS_PET.put("amoxliatl", "Moxi");
        BOSS_PET.put("the royal titans", "Bran");
        BOSS_PET.put("doom of mokhaiotl", "Dom");
        BOSS_PET.put("sol heredit", "Smol heredit");
        BOSS_PET.put("zalcano", "Smolcano");
        BOSS_PET.put("scurrius", "Scurry");
        BOSS_PET.put("tztok-jad", "Tzrek-jad");
        BOSS_PET.put("tzkal-zuk", "Jal-nib-rek");
        BOSS_PET.put("chambers of xeric", "Olmlet");
        BOSS_PET.put("chambers of xeric challenge mode", "Olmlet");
        BOSS_PET.put("theatre of blood", "Lil' zik");
        BOSS_PET.put("tombs of amascut", "Tumeken's guardian");
    }

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
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private DrawManager drawManager;

    @Inject
    private BoardDataService boardDataService;

    @Inject
    private AdminService adminService;

    @Inject
    private Gson gson;

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

    // Fixed platform endpoint. The plugin is Solus-only and always talks to ONE hardcoded URL
    // — required by the RuneLite Plugin Hub (no network call to a URL derived from user input
    // or fetched data). The only connection config a user enters is their clan API key.
    private static final String PLATFORM_URL = "https://api.solusosrs.com";
    private static final String PLATFORM_SLUG = "solus";


    // In-memory hiscore cache: categoryKey → list of entries
    private final Map<String, List<HiscoreEntry>> hiscoreCacheV2 = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile boolean hiscoreV2BatchFetched = false; // true once allTopTimes has been called this session
    // Speed-times mode: "clan" (clan-verified live only — the DEFAULT, so imports stay off the
    // board) or "all" (each player's best across sources, via the Mode dropdown). The Recent
    // overview is newest-first in both modes.
    private volatile String pbMode = "clan";
    private volatile String activityFilter = ""; // activity feed type filter: "" = all, else CSV e.g. "drop,pb"
    private volatile boolean platformIsAdmin = false; // caller's key owner has admin/manage_announcements (from bootstrap permissions)

    // Server-side config fetched from Settings tab
    private int fetchedMinDropValue = 100000;
    private String clanName = "Solus";
    private boolean serverConfigLoaded = false;
    private boolean achievementsSyncedThisSession = false; // diary/quest sync fires once per login

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

    // Collection log sync state (automatic — like WikiSync/RuneProfile)
    private final Map<Integer, ClogItem> clogSyncItems = Collections.synchronizedMap(new LinkedHashMap<>());
    private Map<Integer, String[]> clogItemCategoryMap = null; // itemId -> [tab, category]
    private Map<String, Integer> clogNameToId = null; // lowercase item name -> itemId (for pet icons)
    private int clogDebounceTicksRemaining = -1;
    private boolean clogSearchPending = false;
    private int clogRawEventCount = 0;
    private static final int CLOG_DEBOUNCE_TICKS = 30;
    private static final int SCRIPT_CLOG_ITEM = 4100;
    private static final int SEARCH_TOGGLE_PACKED = 40697932; // InterfaceID.Collection.SEARCH_TOGGLE
    private static final int CLOG_TABS_ENUM = 2102;
    private static final int CLOG_DUPE_REMAP_ENUM = 3721; // game enum: bad itemId -> canonical itemId
    // Cross-category collection-log slots that share a single log slot in-game but carry distinct
    // item ids that enum 3721 does NOT cover. The Volcanic Mine prospector pieces are listed under
    // their own category with new item ids, yet the game counts them as the same slots as the
    // Motherlode Mine set (varp 2943/2944 counts them once). Mapped variant -> canonical so our
    // catalog and unique counts match the game's totals. Enum 3721 still takes precedence and
    // covers any future Jagex-declared dupes; this map only fills the gaps 3721 leaves.
    private static final Map<Integer, Integer> CLOG_DUPE_REMAP_GAPS = Map.of(
        29472, 12013, // Prospector helmet (Volcanic Mine -> Motherlode Mine)
        29474, 12014, // Prospector jacket
        29476, 12015, // Prospector legs
        29478, 12016  // Prospector boots
    );
    private static final int VARP_CLOG_OBTAINED = 2943;   // VarPlayer.CLOG_LOGGED — authoritative unique obtained
    private static final int VARP_CLOG_TOTAL = 2944;      // VarPlayer.CLOG_TOTAL — authoritative unique total
    private static final int PARAM_TAB_NAME = 682;
    private static final int PARAM_TAB_CATEGORIES_ENUM = 683;
    private static final int PARAM_CATEGORY_NAME = 689;
    private static final int PARAM_CATEGORY_ITEMS_ENUM = 690;

    // Boss group-key -> representative item id, for the small icon beside each boss name.
    private static final Map<String, Integer> BOSS_GROUP_ICONS = new HashMap<>();
    static
    {
        // Raids
        BOSS_GROUP_ICONS.put("cox", 20851);      BOSS_GROUP_ICONS.put("cox_cm", 22386);
        BOSS_GROUP_ICONS.put("tob", 22473);      BOSS_GROUP_ICONS.put("tob_entry", 22473);
        BOSS_GROUP_ICONS.put("tob_hm", 22473);
        BOSS_GROUP_ICONS.put("toa", 27352);      BOSS_GROUP_ICONS.put("toa_entry", 27352);
        BOSS_GROUP_ICONS.put("toa_expert", 27352);
        // GWD
        BOSS_GROUP_ICONS.put("bandos", 12650);   BOSS_GROUP_ICONS.put("sara", 12651);
        BOSS_GROUP_ICONS.put("zammy", 12652);    BOSS_GROUP_ICONS.put("arma", 12649);
        // DT2
        BOSS_GROUP_ICONS.put("duke", 28250);     BOSS_GROUP_ICONS.put("leviathan", 28252);
        BOSS_GROUP_ICONS.put("whisperer", 28246); BOSS_GROUP_ICONS.put("vardorvis", 28248);
        // Wave / capes
        BOSS_GROUP_ICONS.put("jad", 13225);      BOSS_GROUP_ICONS.put("zuk", 21291);
        BOSS_GROUP_ICONS.put("colo", 28960);
        // Gauntlet
        BOSS_GROUP_ICONS.put("gaunt", 23757);    BOSS_GROUP_ICONS.put("gaunt_corrupted", 23759);
        // Nightmare
        BOSS_GROUP_ICONS.put("nightmare", 24491); BOSS_GROUP_ICONS.put("phosanis", 24491);
        // Slayer / misc bosses
        BOSS_GROUP_ICONS.put("nex", 26348);      BOSS_GROUP_ICONS.put("araxxor", 29836);
        BOSS_GROUP_ICONS.put("cerberus", 13247); BOSS_GROUP_ICONS.put("hydra", 22746);
        BOSS_GROUP_ICONS.put("thermy", 12648);   BOSS_GROUP_ICONS.put("kraken", 12655);
        BOSS_GROUP_ICONS.put("sire", 13262);     BOSS_GROUP_ICONS.put("grotesque", 21748);
        BOSS_GROUP_ICONS.put("skotizo", 21273);  BOSS_GROUP_ICONS.put("zulrah", 12921);
        BOSS_GROUP_ICONS.put("vorkath", 21992);  BOSS_GROUP_ICONS.put("kq", 12647);
        BOSS_GROUP_ICONS.put("corp", 12816);     BOSS_GROUP_ICONS.put("mole", 12646);
        BOSS_GROUP_ICONS.put("sarachnis", 23495); BOSS_GROUP_ICONS.put("kbd", 12653);
        BOSS_GROUP_ICONS.put("dks", 12644);
        // Newer bosses
        BOSS_GROUP_ICONS.put("hueycoatl", 30152); BOSS_GROUP_ICONS.put("amoxliatl", 30154);
        BOSS_GROUP_ICONS.put("yama", 29622);
        BOSS_GROUP_ICONS.put("maggot_king", 33634); // Elder venator fang
        // Wilderness
        BOSS_GROUP_ICONS.put("callisto", 13178); BOSS_GROUP_ICONS.put("vetion", 13179);
        BOSS_GROUP_ICONS.put("venenatis", 13177); BOSS_GROUP_ICONS.put("chaos_ele", 11995);
        BOSS_GROUP_ICONS.put("scorpia", 13181);  BOSS_GROUP_ICONS.put("crazy_arch", 11990);
        // Low/other
        BOSS_GROUP_ICONS.put("barrows", 4708);   BOSS_GROUP_ICONS.put("bryophyta", 22372);
        BOSS_GROUP_ICONS.put("obor", 20756);
        BOSS_GROUP_ICONS.put("hespori", 22997);  // filled bottomless compost bucket — its signature unique (no item depicts the boss)
        BOSS_GROUP_ICONS.put("titans", 30638);   // Giantsoul amulet (Royal Titans)
        BOSS_GROUP_ICONS.put("sep", 20659);      BOSS_GROUP_ICONS.put("ba", 12703);
    }
    // Built from enum 3721 on clog open: maps a slot's "bad" item id to its canonical id.
    // Replaces the old hand-maintained skip list (which dropped real slots → undercount).
    private Map<Integer, Integer> clogDupeRemap = Collections.emptyMap();
    // Authoritative game counts captured on clog open (varp 2943/2944), reused at upload time.
    private int clogObtainedCount = 0;
    private int clogTotalCount = 0;

    // Adventure log PB sync state
    private int adventureLogPbTicksRemaining = -1;
    private int caReadTicksRemaining = -1; // ticks until we read the CA task interface after it opens
    // Task-name text color in the CA interface: bright green = completed, grey = incomplete.
    private static final int CA_COMPLETE_COLOR = 0x0DC10D;
    private static final int CA_TASK_NAME_COMPONENT = 10; // component 715,10 holds the task-name column
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

    /** @deprecated Legacy — returns empty string; still referenced by the dead GAS fallbacks. */
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
        return config.apiKey() != null && !config.apiKey().trim().isEmpty();
    }

    private String getPlatformUrl()
    {
        return PLATFORM_URL;
    }

    private String getPlatformKey()
    {
        return config.apiKey() == null ? "" : config.apiKey().trim();
    }

    private String getPlatformSlug()
    {
        return PLATFORM_SLUG;
    }

    /**
     * Fetch shared config from the platform bootstrap endpoint.
     * Updates cached values for min drop value, active event, etc. (no URLs are ever
     * read from this response — the plugin only calls the hardcoded platform endpoint.)
     */
    private void fetchBootstrapConfig()
    {
        if (!isPlatformConfigured())
        {
            return;
        }

        String url = getPlatformUrl() + "/clans/" + getPlatformSlug() + "/bootstrap";
        JsonObject response = platformApiService.getSync(url, getPlatformKey());
        clogCatalogIds = platformApiService.fetchClogCatalogIds(getPlatformUrl(), getPlatformKey(), getPlatformSlug());
        log.debug("clog catalog ids loaded: {}", clogCatalogIds.size());

        // Pop the Event tab once when a race is live, or a scheduled one starts within 7 days.
        JsonObject activeEvent = platformApiService.fetchActiveEvent(getPlatformUrl(), getPlatformKey(), getPlatformSlug());
        if (activeEvent != null)
        {
            boolean live = activeEvent.has("event") && !activeEvent.get("event").isJsonNull();
            boolean soon = false;
            if (activeEvent.has("upcoming") && !activeEvent.get("upcoming").isJsonNull())
            {
                try
                {
                    long startMs = java.time.Instant.parse(
                        activeEvent.getAsJsonObject("upcoming").get("startTime").getAsString()).toEpochMilli();
                    soon = startMs - System.currentTimeMillis() <= 7L * 24 * 3600 * 1000;
                }
                catch (Exception ignored) { }
            }
            if (live || soon)
            {
                panel.showEventTabOnce();
            }
        }
        if (response == null)
        {
            log.warn("Failed to fetch bootstrap config from platform");
            return;
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

        // Permissions — the caller key owner's clan permissions drive admin-section unlock.
        platformIsAdmin = false;
        if (response.has("permissions") && response.get("permissions").isJsonArray())
        {
            JsonArray perms = response.getAsJsonArray("permissions");
            for (int i = 0; i < perms.size(); i++)
            {
                String perm = perms.get(i).getAsString();
                if ("admin".equals(perm) || "manage_announcements".equals(perm))
                {
                    platformIsAdmin = true;
                    break;
                }
            }
        }

        // Announcements — render on the home tab.
        List<PlatformApiService.Announcement> anns = new ArrayList<>();
        if (response.has("announcements") && response.get("announcements").isJsonArray())
        {
            JsonArray arr = response.getAsJsonArray("announcements");
            for (int i = 0; i < arr.size(); i++)
            {
                JsonObject o = arr.get(i).getAsJsonObject();
                anns.add(new PlatformApiService.Announcement(
                    o.has("id") ? o.get("id").getAsString() : "",
                    o.has("message") && !o.get("message").isJsonNull() ? o.get("message").getAsString() : "",
                    o.has("author") && !o.get("author").isJsonNull() ? o.get("author").getAsString() : null,
                    o.has("pinned") && o.get("pinned").getAsBoolean()));
            }
        }
        panel.setAnnouncements(anns);

        // Now that permissions are known, unlock the admin tab if this key's owner is an admin.
        if (platformIsAdmin)
        {
            javax.swing.SwingUtilities.invokeLater(this::setupAdminPanel);
        }

        log.info("Bootstrap config loaded from platform");
    }

    /** Re-fetch announcements and refresh both the home display and the admin management list. */
    private void refreshAnnouncements()
    {
        if (!isPlatformConfigured()) return;
        List<PlatformApiService.Announcement> anns = platformApiService.fetchAnnouncements(
            getPlatformUrl(), getPlatformKey(), getPlatformSlug());
        panel.setAnnouncements(anns);
        if (adminPanel != null) adminPanel.setAnnouncementsList(anns);
    }

    /** Get the clan name — hardcoded to Solus. */
    String getClanName()
    {
        return "Solus";
    }

    /**
     * True only when the LOGGED-IN account is actually a member of the clan. A member's API key
     * configured on a non-clan alt must not submit that alt's drops/times to the clan feed —
     * the key authenticates the Discord user, not the account being played.
     */
    private boolean localPlayerInClan()
    {
        ClanChannel clan = client.getClanChannel();
        return clan != null && clan.getName() != null
            && clan.getName().equalsIgnoreCase(getClanName());
    }

    @Override
    protected void startUp()
    {
        // Set up side panel
        panel = new ClanPanel();
        panel.setItemManager(itemManager); // for local item-icon rendering in the Members clog grid
        panel.setSpriteManager(spriteManager); // for in-game clan-rank icons on the Ranks tab
        panel.exportRankIcons(new File(pluginDataDir(), "rank-icons")); // inline rank icons beside names
        // Show tabs only if board code is configured
        panel.setConnected(isPlatformConfigured());
        panel.setOnRefresh(() -> executor.submit(this::refreshData));
        panel.setOnFetchTimes((cat, timesPanel) -> executor.submit(() -> fetchAndDisplayTimesV2(cat, timesPanel)));
        panel.setOnPbModeChange(mode -> executor.submit(() ->
        {
            pbMode = mode;
            batchFetchAllHiscores(); // re-fetch in the new mode (cache is replaced, not merged)
        }));
        panel.setOnActivityFilterChange(filter -> executor.submit(() ->
        {
            activityFilter = filter;
            refreshClanActivity();
        }));
        // Members tab: load the roster on first open, fetch a player's clog on select.
        panel.setOnLoadRoster(() -> executor.submit(() ->
        {
            if (!isPlatformConfigured()) return;
            panel.setMemberList(platformApiService.fetchRoster(getPlatformUrl(), getPlatformKey(), getPlatformSlug()));
        }));
        // Event tab: live draft state (public endpoint) — your team, captains, roster.
        panel.setOnLoadEvent(() -> executor.submit(() ->
        {
            if (!isPlatformConfigured()) return;
            try
            {
                String localName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
                panel.updateEvent(
                    boardDataService.fetchDraft(getPlatformUrl(), getPlatformSlug(), getPlatformKey()),
                    platformApiService.fetchActiveEvent(getPlatformUrl(), getPlatformKey(), getPlatformSlug()),
                    localName);
            }
            catch (Exception ex)
            {
                log.debug("event tab load failed", ex);
                panel.updateEvent(null, null, null);
            }
        }));
        panel.setOnSelectMember(rsn -> executor.submit(() ->
        {
            if (!isPlatformConfigured()) return;
            PlatformApiService.PlayerProfile prof = platformApiService.fetchPlayerProfile(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn);
            PlatformApiService.MemberAbout about = platformApiService.fetchMemberAbout(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn);
            panel.showMemberProfile(rsn, prof, about);
        }));
        panel.setOnLoadClog(rsn -> executor.submit(() ->
        {
            if (!isPlatformConfigured()) return;
            panel.showPlayerClog(rsn, platformApiService.fetchPlayerClog(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn));
        }));
        panel.setOnLoadCa(rsn -> executor.submit(() ->
        {
            if (!isPlatformConfigured()) return;
            panel.showPlayerCa(rsn, platformApiService.fetchPlayerCa(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn));
        }));
        // Loud auth-failure warning: a bad/mispasted API key otherwise fails silently while the
        // panel looks fine (e.g. a member's kills never syncing). At most one warning per 5 min.
        platformApiService.setOnAuthFailure(() ->
        {
            long now = System.currentTimeMillis();
            if (now - lastAuthWarnAt < 5 * 60_000) return;
            lastAuthWarnAt = now;
            clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[" + getClanName() + "] Your API key was REJECTED. Drops/times are NOT syncing. "
                        + "Run /getkey in Discord and paste the new key into the plugin settings.", ""));
        });

        panel.setOnLoadRanks(this::loadRanksWithMode);
        panel.setOnRequestRank(args ->
        {
            String rankName = (String) args[0];
            boolean eligible = (Boolean) args[1];
            @SuppressWarnings("unchecked")
            java.util.List<String> missing = (java.util.List<String>) args[2];
            String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
            if (!isPlatformConfigured() || rsn.isEmpty()) return;
            executor.submit(() -> platformApiService.requestRank(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn, rankName, eligible, missing));
        });
        panel.setOnSetRankOverride(args ->
        {
            String rsn = (String) args[0];
            String mode = (String) args[1];
            String assigned = (String) args[2];
            if (!isPlatformConfigured()) return;
            String setBy = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
            executor.submit(() -> platformApiService.setRankOverride(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn, mode, assigned, setBy));
        });
        panel.setOnClearRankOverride(rsn ->
        {
            if (!isPlatformConfigured()) return;
            executor.submit(() -> platformApiService.clearRankOverride(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn));
        });
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

        // Build boss icons for the Speed Times list once item images are cached (client thread).
        executor.schedule(() -> clientThread.invokeLater(this::buildAndSetBossIcons), 6, TimeUnit.SECONDS);

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

        hiscoreTracker.reset();

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

        if ("apiKey".equals(event.getKey()))
        {
            log.info("API key changed, refreshing platform data...");
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

        // Read the Combat Achievements interface a few ticks after it opens (deferred so the
        // task list has populated its widgets).
        if (caReadTicksRemaining > 0)
        {
            caReadTicksRemaining--;
        }
        else if (caReadTicksRemaining == 0)
        {
            caReadTicksRemaining = -1;
            readCombatAchievements();
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
        // Capture the immutable account hash so player data is keyed on the account, not the RSN.
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            long hash = client.getAccountHash();
            platformApiService.setAccountHash(hash != -1 ? String.valueOf(hash) : null);

            // Sync Achievement Diary + Quest standing once per login. Triggered here (not off the
            // bootstrap fetch, which runs at the LOGIN screen before the player exists) and delayed
            // a few seconds so the diary varbits / quest states are populated. The flag stops a
            // world-hop's LOGGED_IN from re-firing it; it resets on a real logout below.
            if (isPlatformConfigured() && !achievementsSyncedThisSession)
            {
                executor.schedule(() -> clientThread.invokeLater(this::readAchievements), 8, TimeUnit.SECONDS);
            }
        }

        // Reset fight tracker on logout/hop to avoid stale data
        if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.HOPPING)
        {
            if (fightTracker != null)
            {
                fightTracker.reset();
            }
            wasInInstance = false;
            platformApiService.setAccountHash(null);
            rankOwnedCache.clear();   // don't carry one account's items to the next login
            rankOwnedIds.clear();
            rankBankRefreshed = false; // next login's first bank open refreshes ranks again
        }
        // Only a real logout (login screen) re-arms the diary/quest sync; a world hop must not.
        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            achievementsSyncedThisSession = false;
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

        // Drops are logged from the LootReceived event (onLootReceived) instead of the in-game
        // "Valuable drop" chat line — that gives the real monster (no more "Unknown") + item IDs,
        // so we can post only collection-log / whitelisted items rather than every valuable drop.

        // ── Collection Log Detection (for clan drop log) ──
        if (config.enableDrops())
        {
            handleCollectionLogEntry(cleanedMessage);
            handleDuplicatePet(cleanedMessage);
        }

        // ── Live diary/quest sync ──
        // Quest completions ("Congratulations, you've completed a quest: X") and diary tier
        // completions ("Congratulations! You have completed all of the hard tasks in ...") both
        // announce in chat, so re-read + sync right then — same live model as drops/PBs. A short
        // delay lets the varbits/quest states settle; the signature check inside readAchievements
        // makes a false positive a no-op.
        String lowerMsg = cleanedMessage.toLowerCase();
        if (lowerMsg.contains("you've completed a quest")
            || (lowerMsg.contains("congratulations") && lowerMsg.contains("tasks in")))
        {
            executor.schedule(() -> clientThread.invokeLater(this::readAchievements), 3, TimeUnit.SECONDS);
        }
    }

    // Duplicate pets fire ONLY this chat line — no clog unlock, no loot event — so the boss
    // context from the kill-count line is the sole way to know which pet it was.
    private void handleDuplicatePet(String cleanedMessage)
    {
        // First person only — the third-person broadcast ("X has a funny feeling...") is other
        // players' pets. Prefix + fragment instead of the full sentence to tolerate wording drift.
        String lower = cleanedMessage.toLowerCase();
        if (!lower.startsWith("you have a funny feeling") || !lower.contains("would have been followed")) return;
        if (!isPlatformConfigured() || !localPlayerInClan()) return;

        String boss = pbDetector.getLastBossName();
        String petName = boss != null ? BOSS_PET.get(boss.toLowerCase()) : null;
        if (petName == null)
        {
            log.debug("Duplicate pet with no mapped boss context (boss={})", boss);
            return;
        }

        String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";
        WorldPoint wp = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation() : new WorldPoint(0, 0, 0);
        DropEntry drop = new DropEntry(petName, 0, boss, pbDetector.getLastKillCount(),
            wp.getX(), wp.getY(), wp.getPlane(), playerName, -1);
        withScreenshot(true, screenshot ->
            platformApiService.submitDrop(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), drop, screenshot));
        log.debug("Duplicate pet logged: {} from {}", petName, boss);
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

        // Sync Combat Achievements whenever the player opens the CA task list.
        if (event.getGroupId() == InterfaceID.CA_TASKS && isPlatformConfigured() && config.enableClogSync())
        {
            caReadTicksRemaining = 4;
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
            // Seed with the known gaps, then let enum 3721 overlay/win where it has an entry.
            Map<Integer, Integer> m = new HashMap<>(CLOG_DUPE_REMAP_GAPS);
            for (int i = 0; i < badIds.length && i < goodIds.length; i++)
            {
                m.put(badIds[i], goodIds[i]);
            }
            clogDupeRemap = m;
        }
        catch (Exception e)
        {
            // Enum read failed — still apply the known gap remaps so prospector dupes don't leak.
            clogDupeRemap = CLOG_DUPE_REMAP_GAPS;
            log.warn("Failed to build clog dupe remap (enum {})", CLOG_DUPE_REMAP_ENUM, e);
        }
    }

    /** Normalise a collection-log item id to its canonical id via the game's dupe-remap enum. */
    private int remapClogId(int itemId)
    {
        return clogDupeRemap.getOrDefault(itemId, itemId);
    }

    /** Build small boss icons via ItemManager and hand them to the panel (run on the client thread). */
    private void buildAndSetBossIcons()
    {
        if (panel == null) return;
        java.util.Map<String, javax.swing.ImageIcon> icons = new HashMap<>();
        for (Map.Entry<String, Integer> e : BOSS_GROUP_ICONS.entrySet())
        {
            try
            {
                java.awt.image.BufferedImage img = itemManager.getImage(e.getValue());
                if (img != null)
                {
                    icons.put(e.getKey(), new javax.swing.ImageIcon(img));
                }
            }
            catch (Exception ignored) { /* skip icons that fail to load */ }
        }
        panel.setBossIcons(icons);
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
                        // Items can appear in MULTIPLE clog categories (Nexling is in "Nex" AND
                        // "All Pets"). Tabs iterate Bosses-first, so keep the FIRST category seen -
                        // the boss page - instead of letting Other/"All Pets" overwrite it.
                        map.putIfAbsent(itemId, new String[]{tabName, categoryName});

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
            "adventure_log",
            null // solo — no roster
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
            "[" + getClanName() + "] Syncing " + pbCount + " personal bests...", "");

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
                    "adventure_log",
                    null // imported from a single player's log — no roster
                );
                submitted++;
            }
            log.info("Submitted {} PBs to platform for {}", submitted, playerName);
            final int finalSubmitted = submitted;
            clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[" + getClanName() + "] Synced " + finalSubmitted + " personal bests to platform", "")
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
                        clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                            "[" + getClanName() + "] Collection log synced: " + clogObtainedCount + "/" + clogTotalCount + " unlocked", ""));
                    }
                    else
                    {
                        panel.setClogSyncStatus("Sync failed: HTTP " + response.code());
                    }
                }
            }
        ));
    }

    /**
     * Capture the current game frame (when capture=true) and run the callback with a scaled PNG
     * as base64 — or null when disabled / capture fails. Event-driven (no sleep): the frame
     * listener fires on the next render, then encoding + the callback run off the client thread.
     * The screenshot is uploaded to OUR API only; the plugin never sends it to Discord.
     */
    private void withScreenshot(boolean capture, java.util.function.Consumer<String> callback)
    {
        if (!capture || drawManager == null)
        {
            executor.submit(() -> callback.accept(null));
            return;
        }
        drawManager.requestNextFrameListener(image ->
        {
            BufferedImage copy;
            try
            {
                copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = copy.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
            }
            catch (Exception e)
            {
                log.warn("Screenshot capture failed", e);
                executor.submit(() -> callback.accept(null));
                return;
            }
            final BufferedImage captured = copy;
            executor.submit(() ->
            {
                String b64 = null;
                try { b64 = encodeScaledPng(captured, 800); }
                catch (Exception e) { log.warn("Screenshot encode failed", e); }
                callback.accept(b64);
            });
        });
    }

    private String encodeScaledPng(BufferedImage src, int maxWidth) throws java.io.IOException
    {
        BufferedImage img = src;
        if (src.getWidth() > maxWidth)
        {
            int h = (int) ((double) src.getHeight() * maxWidth / src.getWidth());
            BufferedImage scaled = new BufferedImage(maxWidth, h, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, maxWidth, h, null);
            g.dispose();
            img = scaled;
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private void handleCollectionLogEntry(String cleanedMessage)
    {
        if (!localPlayerInClan()) return; // non-clan alt on a member client - do not post
        Matcher matcher = COLLECTION_LOG_PATTERN.matcher(cleanedMessage);
        if (!matcher.find())
        {
            return;
        }

        String itemName = matcher.group(1).trim();
        String playerName = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName()
            : "Unknown";

        if (!isPlatformConfigured())
        {
            return;
        }

        final String pRsn = playerName;
        final String pItem = itemName;
        executor.submit(() -> platformApiService.submitCollectionLogEntry(
            getPlatformUrl(), getPlatformKey(), getPlatformSlug(), pRsn, pItem
        ));

        // A new unique can flip a rank requirement — this is one of the few moments the
        // Ranks tab re-renders (with tab open/manual refresh and first bank open).
        if (panel != null && panel.isRanksActive()) evaluateAndShowRanks();

        // A new collection-log unlock is a one-time notable event, so post it to the drop feed
        // exactly once here. Repeat drops of the same item never re-fire this message, which is
        // how we avoid per-kill spam (e.g. araxyte sacks). This also covers pets, which arrive
        // as a clog unlock rather than loot and never appear in the LootReceived item list.
        if (config.enableDrops())
        {
            ensureClogCategoryMap();
            WorldPoint wp = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getWorldLocation() : new WorldPoint(0, 0, 0);
            int unlockItemId = clogNameToId != null ? clogNameToId.getOrDefault(itemName.toLowerCase(), -1) : -1;
            // Only attribute to the last boss if it was killed recently; otherwise it's a skilling
            // unlock (no associated kill) — avoid mislabeling it with a stale boss name.
            boolean recentKill = System.currentTimeMillis() - lastKillTime < 60_000
                && lastKilledNpc != null && !lastKilledNpc.isEmpty();
            String unlockSource = recentKill ? lastKilledNpc : null;
            // No recent kill: the item's collection-log CATEGORY names its boss (Eternal
            // crystal -> "Cerberus", Crimson kisten -> "Maggot King") - far better than
            // defaulting to "Skilling" on stale kill context.
            if (unlockSource == null && unlockItemId > 0 && clogItemCategoryMap != null)
            {
                String[] meta = clogItemCategoryMap.get(unlockItemId);
                if (meta != null && meta[1] != null && !meta[1].isEmpty())
                {
                    unlockSource = meta[1];
                }
            }
            if (unlockSource == null) unlockSource = "Skilling";

            int unlockValue = unlockItemId > 0 ? itemManager.getItemPrice(unlockItemId) : 0;

            // Only post NOTABLE unlocks: pets, clan-whitelisted items, or anything worth at least
            // the clan's min drop value. This keeps trash secondaries/currency (araxyte venom sack,
            // hallowed mark) out of the feed even on first unlock.
            boolean isPet = PET_NAMES.contains(itemName.toLowerCase());
            boolean notable = isPet
                || (unlockItemId > 0 && isPostableDrop(unlockItemId))
                || unlockValue >= fetchedMinDropValue;
            if (!notable)
            {
                log.debug("Skipping low-value clog unlock {} (value {})", itemName, unlockValue);
            }
            else
            {
                // Only stamp a KC if this unlock came from the boss the KC counter belongs to.
                // Pets often arrive with NO loot event (recentKill=false, e.g. Nexling) — fall
                // back to the chat KC counter when its boss matches the resolved source.
                int unlockKc = 0;
                if (recentKill && kcAppliesTo(unlockSource, pbDetector.getLastBossName()))
                {
                    unlockKc = lastKillCount;
                }
                else if (kcAppliesTo(unlockSource, pbDetector.getLastBossName()))
                {
                    unlockKc = pbDetector.getLastKillCount();
                }
                DropEntry unlockDrop = new DropEntry(
                    itemName, unlockValue, unlockSource, unlockKc,
                    wp.getX(), wp.getY(), wp.getPlane(), playerName, unlockItemId
                );
                withScreenshot(true, screenshot ->
                    platformApiService.submitDrop(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), unlockDrop, screenshot));
                log.debug("Clog-unlock drop logged: {} from {}", itemName, unlockSource);
            }
        }
    }

    /**
     * Handle any boss/raid completion time — checks against clan hiscores
     * even when it's not a personal best, since a player can set a clan
     * record without beating their own PB.
     */
    private void handleCompletionTime(String cleanedMessage)
    {
        if (!localPlayerInClan()) return; // non-clan alt on a member client - do not submit times
        PbDetector.CompletionResult completion = pbDetector.detectCompletion(cleanedMessage);
        if (completion == null)
        {
            // Inferno/Fight Caves/Colosseum: the duration line arrives BEFORE the kill-count
            // line, so the detector parks it — the KC message that just went through
            // processMessage() may have claimed it into a full completion.
            completion = pbDetector.drainPendingCompletion();
        }
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

        // Sanity floor: no real boss/raid completion is under 15 seconds. Sub-floor times are
        // phase/split lines from other plugins that slipped past the message filter.
        if (completion.getTimeSeconds() < 15)
        {
            log.info("Ignoring implausible completion time {}s for {} (phase/split line?)",
                completion.getTimeSeconds(), group);
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

        // Validate clan membership for group content. Solo is ALWAYS clan-verified — the
        // only participant is this clan member; requiring them to idle in clan chat made
        // solo times land as "unverified" and drop off the Clan Only boards.
        boolean allClanMembers = partySize == 1 || !isGroupContent || validateClanMembership(partyMembers);
        if (!allClanMembers)
        {
            log.info("Not all party members in clan chat — PB will be submitted as unverified");
            if (config.chatConfirmation())
            {
                clientThread.invokeLater(() ->
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "[" + getClanName() + "] Time recorded (unverified: not all party members in clan chat)", "")
                );
            }
        }

        // Sort party members for a stable roster string. EVERY plugin-running member submits the
        // full roster (no designated-submitter election — that silently lost the time whenever the
        // alphabetically-first member didn't run the plugin). Duplicates are safe: the server
        // upserts per member keeping the fastest time, and Discord posts fire only when a row
        // actually improves (isNewRecord), so the first submission to arrive posts and the rest no-op.
        List<String> sortedMembers = new ArrayList<>(partyMembers);
        Collections.sort(sortedMembers, String.CASE_INSENSITIVE_ORDER);
        String rsns = String.join(", ", sortedMembers);

        String date = new SimpleDateFormat("MM/dd").format(new Date());

        // On a non-PB kill the game prints the player's TRUE personal best right in the message
        // ("Fight duration: 2:41.40. Personal best: 2:02.40") — submit THAT as the quiet baseline,
        // not the slower kill time. Solo only: for team content the historical PB was set with a
        // different roster, so the current party names would be stored against a time they didn't do.
        final boolean useGamePb = !completion.isPersonalBest()
            && completion.getPersonalBestSeconds() > 0 && partySize == 1;
        final String formattedTime = useGamePb ? completion.getPersonalBestTime() : completion.getFormattedTime();
        final double timeSeconds = useGamePb ? completion.getPersonalBestSeconds() : completion.getTimeSeconds();
        String categoryKey = bossCategory.getKey();
        String sizeLabel = bossCategory.getSizeLabel();

        log.info("Completion time: {} {} — {} (key={}, party: {})",
            formattedTime, categoryName, sizeLabel, categoryKey, rsns);

        final int finalPartySize = partySize;
        final String finalCategoryName = categoryName;
        final boolean finalAllClan = allClanMembers;
        final boolean isNewPb = completion.isPersonalBest();

        // Capture a screenshot only on a genuine new personal best (avoids encoding on every kill).
        withScreenshot(isNewPb, screenshot ->
        {
            // Submit PB to platform API — one entry per party member
            // "live" = all party members in clan chat (clan-verified)
            // "unverified" = not all members in clan chat
            if (isPlatformConfigured())
            {
                int timeMs = (int) (timeSeconds * 1000);
                String source = finalAllClan ? "live" : "unverified";

                // Build everyone's PBs NATURALLY: the first completion each session sets a
                // baseline and only improvements submit after that (the server keeps the fastest
                // per player/boss/size, and Discord posts fire only on genuine records). This
                // matters for bosses the adventure log cannot import - without it, a member whose
                // real PB predates the plugin never gets a time on the board at all.
                String sessionKey = categoryKey + "|" + finalPartySize;
                Integer sessionBest = sessionBestTimes.get(sessionKey);
                if (!isNewPb && sessionBest != null && timeMs >= sessionBest)
                {
                    log.debug("{} {} is not an improvement this session - skipping submit", categoryKey, formattedTime);
                    return;
                }
                sessionBestTimes.merge(sessionKey, timeMs, Math::min);

                // Submit each party member's time. The first submit is synchronous so we learn
                // the clan placement (clanRank 1 = new clan record); the rest are fire-and-forget.
                // All members share the same time, so the rank is stable regardless of order.
                // Roster string stored on every member's row so a team PB can show all names
                // (e.g. a duo best renders "BlG Woody, BlG Moby"). Null for solo content.
                final String teamMembers = finalPartySize > 1 ? rsns : null;

                int clanRank = 0;
                boolean firstMember = true;
                for (String member : sortedMembers)
                {
                    if (firstMember)
                    {
                        clanRank = platformApiService.submitPbSync(getPlatformUrl(), getPlatformKey(), getPlatformSlug(),
                            member.trim(), categoryKey, finalPartySize, timeMs, source, teamMembers, screenshot, isNewPb);
                        firstMember = false;
                    }
                    else
                    {
                        platformApiService.submitPb(getPlatformUrl(), getPlatformKey(), getPlatformSlug(),
                            member.trim(), categoryKey, finalPartySize, timeMs, source, teamMembers, isNewPb);
                    }
                }
                log.debug("Speed time submitted for {}: {} (clanRank {})", categoryKey, formattedTime, clanRank);

                // Invalidate cache so next UI view fetches fresh data
                hiscoreCacheV2.remove(categoryKey);
                saveHiscoreCacheV2ToDisk();

                // Chat notification — highlight when it's a clan-verified placement (top 3),
                // and especially a new clan record (#1). Quiet natural baselines stay silent:
                // "Speed time recorded" on an ordinary kill reads like a bogus PB submission,
                // and the server no-ops anything that isn't actually faster anyway.
                if (config.chatConfirmation() && isNewPb)
                {
                    final int rank = clanRank;
                    String msg;
                    if (finalAllClan && rank == 1)
                    {
                        msg = String.format("[%s] 🏆 NEW CLAN PB! %s in %s", getClanName(), formattedTime, finalCategoryName);
                    }
                    else if (finalAllClan && rank >= 2 && rank <= 3)
                    {
                        msg = String.format("[%s] Clan #%d — %s in %s", getClanName(), rank, formattedTime, finalCategoryName);
                    }
                    else
                    {
                        msg = String.format("[%s] Speed time recorded: %s in %s", getClanName(), formattedTime, finalCategoryName);
                    }
                    clientThread.invokeLater(() ->
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "")
                    );
                }
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
        if (isNonStandardWorld()) return;

        NPC npc = event.getNpc();
        if (npc != null)
        {
            lastKilledNpc = npc.getName();
            lastKillCount = pbDetector.getLastKillCount();
            lastKillTime = System.currentTimeMillis();
        }
    }

    /**
     * True when a loot source is the same boss/counter the latest KC message belongs to, so its
     * kill count can be trusted. Handles bosses whose loot NPC name differs from the KC name
     * (Araxxor→Araxyte, Grotesque Guardians→Dusk/Dawn). Returns false for non-counter sources.
     */
    private boolean kcAppliesTo(String source, String kcBoss)
    {
        if (source == null || kcBoss == null) return false;
        String s = source.toLowerCase().trim();
        String b = kcBoss.toLowerCase().trim();
        if (s.isEmpty() || b.isEmpty()) return false;
        if (s.equals(b) || s.contains(b) || b.contains(s)) return true;
        if (s.contains("araxyte") && b.contains("araxxor")) return true;
        if ((s.equals("dusk") || s.equals("dawn")) && b.contains("grotesque")) return true;
        return false;
    }

    /**
     * Log clan drops from the actual loot event. Using LootReceived (vs the "Valuable drop" chat
     * line) gives the real source name and exact item IDs, so we attribute the monster correctly
     * AND post only the rare/unique items — collection-log entries or curated whitelist items —
     * instead of every valuable drop (e.g. a bulk green d'hide stack from Corp).
     */
    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        if (isNonStandardWorld()) return;
        if (!config.enableDrops() || !isPlatformConfigured()) return;
        if (!localPlayerInClan()) return; // this account isn't in the clan — don't post its drops
        if (event.getItems() == null || event.getItems().isEmpty()) return;

        String source = event.getName();
        // Only attach a KC when the loot source is the boss/counter the KC actually belongs to.
        // Otherwise a stale "last boss" KC gets stamped onto unrelated NPC drops (clue NPCs, etc.).
        // Non-counter sources post with no KC at all.
        // NPC kills AND event loot (raid chests, Barrows) can carry a KC — but only when the loot
        // source matches the boss/counter the latest count message belongs to.
        boolean kcCapable = event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.EVENT;
        int killCount = (kcCapable && kcAppliesTo(source, pbDetector.getLastBossName()))
            ? pbDetector.getLastKillCount() : 0;
        String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";
        WorldPoint wp = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation() : new WorldPoint(0, 0, 0);

        // Live clog quantities: any looted clog-catalog item bumps its count server-side
        // immediately (already-unlocked rows only; the clog-open bulk sync stays authoritative).
        java.util.Map<Integer, Integer> clogBumps = new java.util.HashMap<>();
        for (ItemStack stack : event.getItems())
        {
            if (clogCatalogIds.contains(stack.getId()))
            {
                clogBumps.merge(stack.getId(), Math.max(1, stack.getQuantity()), Integer::sum);
            }
        }
        if (!clogBumps.isEmpty())
        {
            platformApiService.submitClogIncrements(getPlatformUrl(), getPlatformKey(), getPlatformSlug(),
                playerName, clogBumps);
        }

        for (ItemStack stack : event.getItems())
        {
            int itemId = stack.getId();
            ItemComposition comp = itemManager.getItemComposition(itemId);
            String itemName = comp.getName();
            long total = (long) itemManager.getItemPrice(itemId) * Math.max(1, stack.getQuantity());
            int value = (int) Math.min(total, Integer.MAX_VALUE);

            // Post when whitelisted OR notable by value. Value matters for REPEAT uniques the
            // member already has clogged (a second Nightmare staff fires no clog message, and
            // if the item isn't whitelisted it used to vanish entirely). The value path only
            // applies to unstackable, unnoted items, and at 10x the normal drop floor —
            // 100k-class commons (dragon metal sheets and the like) drop far too often to be
            // feed-worthy; a true repeat unique clears the higher bar.
            if (!isPostableDrop(itemId))
            {
                boolean stackLike = comp.isStackable() || comp.getNote() != -1;
                if (stackLike || value < fetchedMinDropValue * 10L) continue;
            }

            DropEntry drop = new DropEntry(itemName, value, source, killCount,
                wp.getX(), wp.getY(), wp.getPlane(), playerName, itemId);

            withScreenshot(true, screenshot ->
                platformApiService.submitDrop(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), drop, screenshot));
            log.debug("Rare drop logged: {} x{} from {}", itemName, stack.getQuantity(), source);
        }
    }

    /**
     * A loot drop is postable only if it's on the clan's curated whitelist. Collection-log
     * uniques are NOT posted from loot — they'd spam the feed on every kill (e.g. araxyte sacks
     * from Araxxor). Instead a clog unlock posts once via handleCollectionLogEntry the first
     * time it's obtained.
     */
    private boolean isPostableDrop(int itemId)
    {
        if (cachedClanWhitelist != null && !cachedClanWhitelist.isEmpty())
        {
            String name = itemManager.getItemComposition(itemId).getName();
            for (Map<String, String> entry : cachedClanWhitelist)
            {
                String wl = entry.get("item");
                if (wl != null && wl.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    /**
     * Build the collection-log item-id → [tab, category] map for drop filtering, without the
     * server catalog sync that buildClogCategoryMap does (that one runs when the clog is opened).
     * Cheap and cached after the first build; reads game enums so must run on the client thread.
     */
    /**
     * Read the open Combat Achievements task list and sync each task's completion to the platform.
     * Each rendered row in the task-name column (component 715,10) is a task whose text is the
     * exact task name (matches the wiki catalog) and whose text color encodes completion
     * (green = done, grey = not done). The list isn't virtualized, so every row matching the
     * player's current filter is present at once. Sync is per-task upsert, so any filter is safe —
     * an "All" filter syncs everything in one open, a narrower filter just updates a subset.
     */
    private void readCombatAchievements()
    {
        if (!isPlatformConfigured()) return;
        String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
        if (rsn == null || rsn.isEmpty()) return;

        Widget list = client.getWidget(InterfaceID.CA_TASKS, CA_TASK_NAME_COMPONENT);
        if (list == null) return;
        Widget[] rows = list.getDynamicChildren();
        if (rows == null || rows.length == 0) return;

        List<PlatformApiService.CaTask> tasks = new ArrayList<>();
        int completed = 0;
        for (Widget row : rows)
        {
            String name = row.getText();
            if (name == null || name.isEmpty()) continue;
            boolean done = row.getTextColor() == CA_COMPLETE_COLOR;
            if (done) completed++;
            tasks.add(new PlatformApiService.CaTask(name.trim(), done));
        }
        if (tasks.isEmpty()) return;

        final String fRsn = rsn;
        final List<PlatformApiService.CaTask> fTasks = tasks;
        final int fCompleted = completed;
        executor.submit(() -> platformApiService.syncCombatAchievements(
            getPlatformUrl(), getPlatformKey(), getPlatformSlug(), fRsn, fTasks));
        log.info("Synced {} combat achievements ({} complete) for {}", tasks.size(), fCompleted, fRsn);
        // readCombatAchievements runs on the client thread (it reads the CA interface widgets), so
        // the confirmation can post directly.
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[" + getClanName() + "] Combat achievements synced (" + fCompleted + "/" + tasks.size() + " complete)", "");
    }

    // PRIVACY: these item caches are IN-MEMORY ONLY and are NEVER sent anywhere. They exist solely
    // to tick rank requirement boxes locally. Accumulated across the session (so items stay checked
    // after the bank closes / you switch tabs) and cleared on logout. See RankSystem for the full note.
    private final java.util.Set<String> rankOwnedCache = new java.util.HashSet<>();   // lowercased item names seen
    private final java.util.Map<String, Integer> rankOwnedIds = new java.util.HashMap<>(); // name -> id (for icons)

    // Admin-controlled eval mode for THIS player, fetched from the server (sticky override).
    // "default" = bank/equipment auto-eval; "clog_only" = evaluate from the local collection log;
    // "admin_set" = rank is assigned manually by an admin (no auto-eval).
    private volatile long lastAuthWarnAt = 0; // debounce for the key-rejected chat warning
    // Fastest time seen THIS session per "categoryKey|partySize" - gates natural PB submissions.
    private final java.util.Map<String, Integer> sessionBestTimes = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile String rankMode = "default";
    private volatile String rankAssigned = null;
    private volatile java.util.Set<String> rankHeld = new java.util.HashSet<>(); // ranks held via Discord
    // Clog item ids from the server catalog — filters loot events for live quantity bumps.
    private volatile java.util.Set<Integer> clogCatalogIds = java.util.Collections.emptySet();
    private volatile java.util.Map<String, Integer> rankKc = new java.util.HashMap<>(); // WOM boss key -> KC
    private volatile java.util.Set<String> rankCaDone = new java.util.HashSet<>(); // completed CA task names (lowercased)

    // Achievement Diary completion varbits per region {varbitId, completeThreshold}. Standard regions
    // use the boolean _COMPLETE varbit (>=1); Karamja easy/medium/hard use the legacy varbit (>=2).
    private static final int[][] DIARY_EASY = {
        {4458,1},{4462,1},{4466,1},{4471,1},{4475,1},{4479,1},{4483,1},{4487,1},{4491,1},{4495,1},{7925,1},{3578,2}};
    private static final int[][] DIARY_MEDIUM = {
        {4459,1},{4463,1},{4467,1},{4472,1},{4476,1},{4480,1},{4484,1},{4488,1},{4492,1},{4496,1},{7926,1},{3599,2}};
    private static final int[][] DIARY_HARD = {
        {4460,1},{4464,1},{4468,1},{4473,1},{4477,1},{4481,1},{4485,1},{4489,1},{4493,1},{4497,1},{7927,1},{3611,2}};
    private static final int[][] DIARY_ELITE = {
        {4461,1},{4465,1},{4469,1},{4474,1},{4478,1},{4482,1},{4486,1},{4490,1},{4494,1},{4498,1},{7928,1},{4566,1}};

    /** Tab-open / refresh entry point: fetch this player's eval mode from the server, then evaluate. */
    private void loadRanksWithMode()
    {
        if (!isPlatformConfigured() || client.getLocalPlayer() == null) { panel.showRanks(null, null, "default"); return; }
        String rsn = client.getLocalPlayer().getName();
        if (rsn == null || rsn.isEmpty()) { panel.showRanks(null, null, "default"); return; }
        executor.submit(() ->
        {
            PlatformApiService.RankMode rm = platformApiService.fetchRankMode(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn);
            rankMode = rm.mode != null ? rm.mode : "default";
            rankAssigned = rm.assignedRank;
            rankHeld = rm.heldRanks != null ? new java.util.HashSet<>(rm.heldRanks) : new java.util.HashSet<>();
            // Boss KCs from WiseOldMan (via our server) — public hiscore data, drives KC requirements.
            rankKc = platformApiService.fetchPlayerKc(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn);
            // Completed Combat Achievement tasks (we already sync these) — drives named-CA requirements.
            PlatformApiService.PlayerCa ca = platformApiService.fetchPlayerCa(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn);
            java.util.Set<String> done = new java.util.HashSet<>();
            if (ca != null && ca.tasks != null)
            {
                for (PlatformApiService.CaTaskInfo t : ca.tasks)
                {
                    if (t.completed && t.name != null) done.add(t.name.toLowerCase());
                }
            }
            rankCaDone = done;
            evaluateAndShowRanks();
        });
    }

    /** (Re)evaluate the local player's ranks per the cached mode and push to the panel. Nothing is sent out. */
    private void evaluateAndShowRanks()
    {
        if (client.getLocalPlayer() == null) { panel.showRanks(null, null, "default"); return; }
        if ("admin_set".equals(rankMode))
        {
            panel.showAdminAssignedRank(rankAssigned, rankMode);
            return;
        }
        final boolean clogOnly = "clog_only".equals(rankMode);
        clientThread.invokeLater(() ->
        {
            RankSystem.PlayerSnapshot snap = buildRankSnapshot(clogOnly);
            java.util.List<RankSystem.RankStatus> results = RankSystem.evaluateAll(snap, rankHeld);
            panel.showRanks(results, snap.itemIds, rankMode);
        });
    }

    // The first bank open each session is the one container event worth a re-render: the bank
    // flooding the item cache is the big accuracy jump for item requirements.
    private boolean rankBankRefreshed = false;

    /** Cache items from worn/inventory/bank as they change so checks stay accurate after the bank closes. */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        int id = event.getContainerId();
        if (id != InventoryID.BANK && id != InventoryID.INV && id != InventoryID.WORN) return;
        cacheContainerItems(id);
        // Do NOT re-render the Ranks tab here on every event — inventory/equipment events fire
        // constantly during play and rebuilding the tab per event made it flicker. The cache
        // above always stays fresh; the tab re-renders on: tab open, the manual ↻ button,
        // first bank open of the session, or a new collection-log unlock.
        if (id == InventoryID.BANK && !rankBankRefreshed)
        {
            rankBankRefreshed = true;
            if (panel != null && panel.isRanksActive()) evaluateAndShowRanks();
        }
    }

    /** Build a snapshot of the local player's state for clan-rank validation. Client thread only.
     *  Reads are LOCAL; nothing here is transmitted. */
    private RankSystem.PlayerSnapshot buildRankSnapshot(boolean clogOnly)
    {
        RankSystem.PlayerSnapshot s = new RankSystem.PlayerSnapshot();
        // The clog name→id map resolves UNTRADEABLE item icons (fire cape, void, infernal cape…) that
        // itemManager.search can't find. Build it once and hand it to the panel for icon rendering.
        ensureClogCategoryMap();
        if (clogNameToId != null) panel.setClogNameToId(clogNameToId);
        for (Skill sk : Skill.values())
        {
            if (sk == Skill.OVERALL) continue;
            s.skills.put(sk.getName().toLowerCase(), client.getRealSkillLevel(sk));
        }
        s.totalLevel = client.getTotalLevel();
        s.totalXp = client.getOverallExperience();
        addCaTier(s, "easy", VarbitID.CA_TIER_STATUS_EASY);
        addCaTier(s, "medium", VarbitID.CA_TIER_STATUS_MEDIUM);
        addCaTier(s, "hard", VarbitID.CA_TIER_STATUS_HARD);
        addCaTier(s, "elite", VarbitID.CA_TIER_STATUS_ELITE);
        addCaTier(s, "master", VarbitID.CA_TIER_STATUS_MASTER);
        addCaTier(s, "grandmaster", VarbitID.CA_TIER_STATUS_GRANDMASTER);
        // Prayer-scroll unlocks read from the prayer book (the scroll itself is consumed on use).
        addUnlock(s, "rigour", VarbitID.PRAYER_RIGOUR_UNLOCKED);
        addUnlock(s, "augury", VarbitID.PRAYER_AUGURY_UNLOCKED);
        addUnlock(s, "preserve", VarbitID.PRAYER_PRESERVE_UNLOCKED);
        addUnlock(s, "deadeye", VarbitID.PRAYER_DEADEYE_UNLOCKED);
        addUnlock(s, "mystic vigour", VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED);

        // Collection log slots obtained (varp 2943, server-synced) — drives Log Beast.
        try { s.clogSlots = Math.max(client.getVarpValue(VARP_CLOG_OBTAINED), clogObtainedCount); }
        catch (Exception ignored) { s.clogSlots = clogObtainedCount; }
        // Achievement Diary completion (in-game varbits) → count complete per tier.
        s.diaryComplete.put("easy", countDiaries(DIARY_EASY));
        s.diaryComplete.put("medium", countDiaries(DIARY_MEDIUM));
        s.diaryComplete.put("hard", countDiaries(DIARY_HARD));
        s.diaryComplete.put("elite", countDiaries(DIARY_ELITE));
        // Completed CA tasks (fetched from our server in loadRanksWithMode).
        s.caDone.addAll(rankCaDone);

        if (clogOnly)
        {
            // Collection-log-only mode (admin-assigned): treat items the player has OBTAINED per their
            // collection log as owned, instead of their current bank/equipment. Read locally from the
            // clog synced this session (open the collection log to populate it); never sent anywhere.
            synchronized (clogSyncItems)
            {
                for (ClogItem ci : clogSyncItems.values())
                {
                    String key = ci.name.toLowerCase();
                    s.ownedItems.add(key);
                    s.itemIds.putIfAbsent(key, ci.itemId);
                }
            }
        }
        else
        {
            // Default: refresh the cache from whatever's readable now, then use the session cache.
            cacheContainerItems(InventoryID.WORN);
            cacheContainerItems(InventoryID.INV);
            cacheContainerItems(InventoryID.BANK);
            s.ownedItems.addAll(rankOwnedCache);
            s.itemIds.putAll(rankOwnedIds);
            // ALSO count collection-log obtained items (read locally when the clog is opened).
            // Consumed/combined uniques — Slepey tablet into the necklace, used prayer scrolls,
            // Cursed phalanx — vanish from the bank but stay logged forever.
            synchronized (clogSyncItems)
            {
                for (ClogItem ci : clogSyncItems.values())
                {
                    String key = ci.name.toLowerCase();
                    s.ownedItems.add(key);
                    s.itemIds.putIfAbsent(key, ci.itemId);
                }
            }
        }
        RankSystem.expandOwned(s.ownedItems); // own Ultor → Berserker ring (i) ticks, etc.

        // Boss KCs (WiseOldMan via our server) + synthetic aggregates the rank checks reference.
        java.util.Map<String, Integer> kc = rankKc;
        s.kc.putAll(kc);
        int cox = kcSum(kc, "chambers_of_xeric", "chambers_of_xeric:_challenge_mode");
        int tob = kcSum(kc, "theatre_of_blood", "theatre_of_blood:_hard_mode");
        int toa = kcSum(kc, "tombs_of_amascut", "tombs_of_amascut:_expert_mode");
        s.kc.put("cox_total", cox);
        s.kc.put("tob_total", tob);
        s.kc.put("toa_total", toa);
        s.kc.put("raids_combined", cox + tob + toa);
        s.kc.put("god_wars_dungeon", kcSum(kc, "general_graardor", "commander_zilyana", "kreearra", "kril_tsutsaroth"));
        return s;
    }

    /** Count completed diaries in a tier (client thread). Each entry is {varbitId, completeThreshold}. */
    private int countDiaries(int[][] varbits)
    {
        int n = 0;
        for (int[] vt : varbits)
        {
            try { if (client.getVarbitValue(vt[0]) >= vt[1]) n++; }
            catch (Exception ignored) { /* varbit id may differ across versions */ }
        }
        return n;
    }

    // Quest points live in a long-standing player varp. Read best-effort; a wrong value just shows
    // an odd QP count, it never blocks the diary/quest sync.
    private static final int VARP_QUEST_POINTS = 101;

    // RuneLite's Quest enum mixes in the 18 miniquests and the 10 Recipe for Disaster SUBquests
    // (209 entries total). The clan count should match the in-game quest list (181 = 209 - 28),
    // so these are excluded; RECIPE_FOR_DISASTER itself stays (it's the real quest).
    private static final java.util.Set<net.runelite.api.Quest> NON_QUEST_ENTRIES = java.util.EnumSet.of(
        net.runelite.api.Quest.ALFRED_GRIMHANDS_BARCRAWL,
        net.runelite.api.Quest.BARBARIAN_TRAINING,
        net.runelite.api.Quest.BEAR_YOUR_SOUL,
        net.runelite.api.Quest.CURSE_OF_THE_EMPTY_LORD,
        net.runelite.api.Quest.DADDYS_HOME,
        net.runelite.api.Quest.THE_ENCHANTED_KEY,
        net.runelite.api.Quest.ENTER_THE_ABYSS,
        net.runelite.api.Quest.FAMILY_PEST,
        net.runelite.api.Quest.THE_FROZEN_DOOR,
        net.runelite.api.Quest.THE_GENERALS_SHADOW,
        net.runelite.api.Quest.HIS_FAITHFUL_SERVANTS,
        net.runelite.api.Quest.HOPESPEARS_WILL,
        net.runelite.api.Quest.IN_SEARCH_OF_KNOWLEDGE,
        net.runelite.api.Quest.INTO_THE_TOMBS,
        net.runelite.api.Quest.LAIR_OF_TARN_RAZORLOR,
        net.runelite.api.Quest.MAGE_ARENA_I,
        net.runelite.api.Quest.MAGE_ARENA_II,
        net.runelite.api.Quest.SKIPPY_AND_THE_MOGRES,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__MOUNTAIN_DWARF,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__WARTFACE__BENTNOZE,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__PIRATE_PETE,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__LUMBRIDGE_GUIDE,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__EVIL_DAVE,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__SKRACH_UGLOGWEE,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__SIR_AMIK_VARZE,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI,
        net.runelite.api.Quest.RECIPE_FOR_DISASTER__CULINAROMANCER);

    /**
     * Read this player's Achievement Diary + Quest standing (client thread) and sync it to the
     * platform, then confirm in chat. Diaries: completed regions per tier (out of 12). Quests:
     * how many of the game's quests are FINISHED, plus quest points. Runs once per session.
     */
    private void readAchievements()
    {
        if (!isPlatformConfigured()) return;
        net.runelite.api.Player lp = client.getLocalPlayer();
        if (lp == null || lp.getName() == null || lp.getName().isEmpty()) return;
        final String rsn = lp.getName();
        achievementsSyncedThisSession = true; // player is present; mark done so it fires once per login

        final int diaryEasy = countDiaries(DIARY_EASY);
        final int diaryMedium = countDiaries(DIARY_MEDIUM);
        final int diaryHard = countDiaries(DIARY_HARD);
        final int diaryElite = countDiaries(DIARY_ELITE);

        int complete = 0, total = 0;
        for (net.runelite.api.Quest q : net.runelite.api.Quest.values())
        {
            if (NON_QUEST_ENTRIES.contains(q)) continue; // miniquests / RFD subquests aren't quests
            total++;
            try { if (q.getState(client) == net.runelite.api.QuestState.FINISHED) complete++; }
            catch (Exception ignored) { /* a quest's varbit may be unavailable this version */ }
        }
        int qp = 0;
        try { qp = client.getVarpValue(VARP_QUEST_POINTS); }
        catch (Exception ignored) { /* best-effort */ }

        final int fQp = qp, fComplete = complete, fTotal = total;

        // Diaries and quests barely change, so this is a one-time import and then a no-op on every
        // routine login. We only POST + announce when the reading actually differs from the last
        // sync for THIS character (signature persisted in config, so it survives client restarts).
        final String sig = fQp + ":" + fComplete + ":" + fTotal + ":"
            + diaryEasy + ":" + diaryMedium + ":" + diaryHard + ":" + diaryElite;
        final String sigKey = "achSig." + rsn.toLowerCase();
        final String prev = configManager.getConfiguration("droplogger", sigKey);
        if (sig.equals(prev))
        {
            log.debug("Achievements unchanged for {} - skipping sync", rsn);
            return;
        }
        configManager.setConfiguration("droplogger", sigKey, sig);

        executor.submit(() -> platformApiService.syncAchievementSummary(
            getPlatformUrl(), getPlatformKey(), getPlatformSlug(), rsn,
            fQp, fComplete, fTotal, diaryEasy, diaryMedium, diaryHard, diaryElite));

        final int diaryTotal = diaryEasy + diaryMedium + diaryHard + diaryElite;
        final String verb = prev == null ? "synced" : "updated"; // first import vs a later change
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[" + getClanName() + "] Diaries & quests " + verb + " (" + diaryTotal + "/48 diaries, "
                + complete + " quests, " + qp + " QP)", "");
        log.info("Achievements {} for {}: {} diaries, {} quests, {} QP", verb, rsn, diaryTotal, complete, qp);
    }

    /** Sum the KC of several WiseOldMan boss keys (for GWD / combined-raid aggregates). */
    private int kcSum(java.util.Map<String, Integer> kc, String... keys)
    {
        int total = 0;
        for (String k : keys) total += kc.getOrDefault(k, 0);
        return total;
    }

    private void addCaTier(RankSystem.PlayerSnapshot s, String tier, int varbit)
    {
        try { if (client.getVarbitValue(varbit) >= 2) s.caTiersComplete.add(tier); }
        catch (Exception ignored) { /* varbit id may differ across versions */ }
    }

    /** Record a persistent unlock (e.g. a learned prayer) when its unlock varbit is set. */
    private void addUnlock(RankSystem.PlayerSnapshot s, String key, int varbit)
    {
        try { if (client.getVarbitValue(varbit) >= 1) s.unlocks.add(key); }
        catch (Exception ignored) { /* varbit id may differ across versions */ }
    }

    /** Read a container and add its item names/ids to the in-memory session cache (never transmitted). */
    private void cacheContainerItems(int containerId)
    {
        ItemContainer c = client.getItemContainer(containerId);
        if (c == null) return;
        for (Item it : c.getItems())
        {
            if (it == null || it.getId() <= 0) continue;
            try
            {
                String name = itemManager.getItemComposition(it.getId()).getName();
                if (name != null && !name.isEmpty())
                {
                    String key = name.toLowerCase();
                    rankOwnedCache.add(key);
                    rankOwnedIds.putIfAbsent(key, it.getId());
                }
            }
            catch (Exception ignored) { /* skip unresolvable item */ }
        }
    }

    private void ensureClogCategoryMap()
    {
        if (clogItemCategoryMap != null) return;
        try
        {
            buildClogDupeRemap();
            Map<Integer, String[]> map = new HashMap<>();
            Map<String, Integer> nameToId = new HashMap<>();
            EnumComposition tabsEnum = client.getEnum(CLOG_TABS_ENUM);
            for (int tabStructId : tabsEnum.getIntVals())
            {
                StructComposition tabStruct = client.getStructComposition(tabStructId);
                String tabName = tabStruct.getStringValue(PARAM_TAB_NAME);
                EnumComposition categoriesEnum = client.getEnum(tabStruct.getIntValue(PARAM_TAB_CATEGORIES_ENUM));
                for (int catStructId : categoriesEnum.getIntVals())
                {
                    StructComposition catStruct = client.getStructComposition(catStructId);
                    String categoryName = catStruct.getStringValue(PARAM_CATEGORY_NAME);
                    EnumComposition itemsEnum = client.getEnum(catStruct.getIntValue(PARAM_CATEGORY_ITEMS_ENUM));
                    for (int rawItemId : itemsEnum.getIntVals())
                    {
                        int itemId = remapClogId(rawItemId);
                        // Items can appear in MULTIPLE clog categories (Nexling is in "Nex" AND
                        // "All Pets"). Tabs iterate Bosses-first, so keep the FIRST category seen -
                        // the boss page - instead of letting Other/"All Pets" overwrite it.
                        map.putIfAbsent(itemId, new String[]{tabName, categoryName});
                        String nm = itemManager.getItemComposition(itemId).getName();
                        if (nm != null && !nm.equals("null")) nameToId.put(nm.toLowerCase(), itemId);
                    }
                }
            }
            clogItemCategoryMap = map;
            clogNameToId = nameToId;
            log.debug("Built clog item map for drop filtering: {} items", map.size());
        }
        catch (Exception e)
        {
            log.warn("Failed to build clog item map for drop filtering", e);
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
            panel.setStatus("Enter your API key in plugin settings");
            return;
        }

        // Fetch bootstrap config from platform (min drop value, active event)
        fetchBootstrapConfig();

        // Roster ranks (rsn -> Discord-derived LADDER rank id) — the rank ICON shown beside names.
        // Discord is authoritative: the in-game CC title caps below the Heart-of-Solus tiers
        // (e.g. a heart_3 member shows only "Beast" in-game).
        try
        {
            java.util.Map<String, String> ranks = new java.util.HashMap<>();
            for (PlatformApiService.RosterMember m : platformApiService.fetchRoster(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug()))
            {
                // Discord ladder rank first; members without one (staff, unlinked) fall back to
                // their in-game STAR title (Master/Major/Proselyte) so they still get an icon.
                String rankKey = m.ladderRank;
                if (rankKey == null && m.rank != null)
                {
                    switch (m.rank.toLowerCase())
                    {
                        case "master": rankKey = "title_master"; break;
                        case "major": rankKey = "title_major"; break;
                        case "proselyte": rankKey = "title_proselyte"; break;
                        default: break;
                    }
                }
                if (m.rsn != null && rankKey != null)
                {
                    ranks.put(m.rsn.replace(' ', ' ').trim().toLowerCase(), rankKey);
                }
            }
            panel.setRosterRanks(ranks);
        }
        catch (Exception e)
        {
            log.debug("Roster rank fetch failed", e);
        }

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

                // Auto-sync roster on login if admin. Role-based: the member's own personal key
                // carries their admin permission (the shared admin key is gone).
                if (platformIsAdmin)
                {
                    clientThread.invokeLater(() -> hiscoreTracker.onLoginIfAdmin(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), getPlatformKey()));
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

        // Re-fetch speed times each cycle so the recent list updates live as other players sync
        // their PBs (previously only fetched once per session, so new times never appeared).
        batchFetchAllHiscores();
        refreshClanActivity();
        refreshEventLeaderboard();
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

        try
        {
            List<LeaderboardEntry> entries = platformApiService.fetchActiveEventLeaderboard(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug());
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

    private String getLocalPlayerName()
    {
        if (client.getLocalPlayer() != null)
        {
            return client.getLocalPlayer().getName();
        }
        return null;
    }

    /**
     * Batch-fetch all speed times from the platform API, populate entire cache.
     */
    private void batchFetchAllHiscores()
    {
        if (!isPlatformConfigured())
        {
            return;
        }

        try
        {
            Map<String, List<HiscoreEntry>> allTimes = platformApiService.fetchAllPbs(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug(), pbMode);
            if (allTimes != null)
            {
                // Replace, don't merge — a merge left stale categories behind (e.g. imported lists
                // lingering after a mode change), and the fetch order carries the Recent sorting.
                hiscoreCacheV2.clear();
                hiscoreCacheV2.putAll(allTimes);
                hiscoreV2BatchFetched = true;
                saveHiscoreCacheV2ToDisk();
                panel.setRecentCategories(new java.util.LinkedHashSet<>(hiscoreCacheV2.keySet()), new java.util.LinkedHashMap<>(hiscoreCacheV2));
                log.info("Batch-fetched speed times from platform API: {} categories", allTimes.size());
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to batch-fetch speed times from platform API", e);
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

        // Category not in cache — we've batch-fetched everything from the platform API,
        // so this category simply has no times yet.
        if (hiscoreV2BatchFetched)
        {
            panel.populateTimesPanel(timesPanel, new ArrayList<>(), accentColor);
            return;
        }

        // Batch fetch hasn't succeeded (platform not configured or fetch failed)
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            timesPanel.removeAll();
            String msg = !isPlatformConfigured()
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
        if (!isPlatformConfigured())
        {
            log.debug("Platform not configured — skipping drops tab refresh");
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
                getPlatformUrl(), getPlatformSlug(), getPlatformKey(), "monthly");
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
                getPlatformUrl(), getPlatformSlug(), getPlatformKey(), 20);
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
        if (!isPlatformConfigured())
        {
            return;
        }

        try
        {
            List<Map<String, Object>> drops = boardDataService.fetchPlayerDrops(
                getPlatformUrl(), getPlatformSlug(), getPlatformKey(), rsn);
            panel.showPlayerDrops(rsn, drops);
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch drops for {}", rsn, e);
        }
    }

    private void refreshClanWhitelist()
    {
        if (!isPlatformConfigured())
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
                getPlatformUrl(), getPlatformSlug(), getPlatformKey());
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
        if (!isPlatformConfigured())
        {
            panel.updateWomLeaderboard(null, false);
            return;
        }
        try
        {
            // All-Time ranks by current total XP (no "+"); other periods rank by gain.
            String apiPeriod = "all-time".equals(period) ? "all" : period;
            boolean isGained = !"all".equals(apiPeriod);
            List<LeaderboardEntry> entries = metric.startsWith("boss:")
                ? platformApiService.fetchKcLeaderboard(
                    getPlatformUrl(), getPlatformKey(), getPlatformSlug(), metric.substring(5), apiPeriod)
                : platformApiService.fetchXpLeaderboard(
                    getPlatformUrl(), getPlatformKey(), getPlatformSlug(), metric, apiPeriod);
            panel.updateWomLeaderboard(entries, isGained);
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch XP leaderboard: {}", e.getMessage());
            panel.updateWomLeaderboard(null, false);
        }
    }

    private void refreshClanActivity()
    {
        if (!isPlatformConfigured()) return;
        try
        {
            List<PlatformApiService.ActivityItem> activity = platformApiService.fetchActivity(
                getPlatformUrl(), getPlatformKey(), getPlatformSlug(), 25, activityFilter);
            panel.updateActivity(activity);
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch clan activity: {}", e.getMessage());
        }
    }

    /** Plugin-specific data dir under .runelite — Hub rule: don't write loose files in the .runelite root. */
    private static File pluginDataDir()
    {
        File dir = new File(net.runelite.client.RuneLite.RUNELITE_DIR, "clan-management");
        if (!dir.exists())
        {
            dir.mkdirs();
        }
        return dir;
    }

    private void saveWhitelistCacheToDisk()
    {
        try
        {
            File cacheFile = new File(pluginDataDir(), "whitelist-cache.json");
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
            File cacheFile = new File(pluginDataDir(), "whitelist-cache.json");
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
        // v2 name retired the old file on purpose: caches written by the "All PBs" era contain
        // imported lists that must not resurface on the live-only board.
        return new File(pluginDataDir(), "hiscore-cache-live.json");
    }

    private void saveHiscoreCacheV2ToDisk()
    {
        // Only persist the default (clan-verified) view. Persisting whatever mode was last
        // browsed meant an "All PBs" session wrote imports to disk, and the next startup loaded
        // them straight into the Clan Only recent view.
        if (!"clan".equals(pbMode)) return;
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

            File cacheFile = new File(pluginDataDir(), "drops-cache.json");
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
            File cacheFile = new File(pluginDataDir(), "drops-cache.json");
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

    /** "yyyy-MM-dd HH:mm" ET wall clock -> ISO instant; null when blank/invalid. */
    private static String etToIso(String text)
    {
        if (text == null || text.trim().isEmpty()) return null;
        try
        {
            java.time.LocalDateTime local = java.time.LocalDateTime.parse(text.trim(),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return local.atZone(java.time.ZoneId.of("America/New_York")).toInstant().toString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** Fetch running + scheduled events and hand formatted rows to the admin calendar. */
    private void loadAdminEventsList()
    {
        if (adminPanel == null || !isPlatformConfigured()) return;
        try
        {
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d");
            for (com.google.gson.JsonObject ev : adminService.fetchEventsList(getPlatformUrl(), getPlatformKey(), getPlatformSlug()))
            {
                String status = ev.get("status").getAsString();
                if (!"active".equals(status) && !"scheduled".equals(status)) continue;
                String window;
                try
                {
                    java.time.ZonedDateTime st = java.time.Instant.parse(ev.get("startTime").getAsString())
                        .atZone(java.time.ZoneId.of("America/New_York"));
                    java.time.ZonedDateTime en = java.time.Instant.parse(ev.get("endTime").getAsString())
                        .atZone(java.time.ZoneId.of("America/New_York"));
                    long daysUntil = java.time.Duration.between(java.time.ZonedDateTime.now(st.getZone()), st).toDays();
                    window = st.format(fmt) + " \u2192 " + en.format(fmt)
                        + ("scheduled".equals(status) && daysUntil >= 0 ? " (in " + (daysUntil + 1) + "d)" : "");
                }
                catch (Exception ex)
                {
                    window = "";
                }
                String evType = ev.has("type") ? ev.get("type").getAsString() : "";
                String glyph = "boss".equals(evType) ? "\u2694 " : "skill".equals(evType) ? "\u2692 "
                    : "clue".equals(evType) ? "\ud83d\udcdc " : "gamer".equals(evType) ? "\ud83c\udfae " : "";
                rows.add(new String[]{ev.get("id").getAsString(), status, ev.get("displayName").getAsString(), window, glyph});
            }
            adminPanel.setEventsList(rows);
        }
        catch (Exception e)
        {
            log.debug("admin events list load failed", e);
        }
    }

    private void setupAdminPanel()
    {
        if (adminPanel != null)
        {
            return; // already shown (e.g. set up once from the legacy key, then again post-bootstrap)
        }

        // Admin unlocks purely by role: the personal key's Discord user must have an admin
        // permission (bootstrap `permissions`). The legacy shared admin key is retired.
        if (!platformIsAdmin)
        {
            return;
        }

        panel.setPlatformAdmin(true); // unlock the per-member rank-override controls in the Members tab

        this.adminPanel = new AdminPanel();
        panel.showAdminTab(adminPanel);

        // Announcements — create/edit/pin/delete via the platform (uses the caller's key).
        adminPanel.setOnCreateAnnouncement(a -> executor.submit(() -> {
            if (platformApiService.createAnnouncement(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), (String) a[0], (Boolean) a[1]))
                refreshAnnouncements();
            else adminPanel.setStatus("Failed to post announcement");
        }));
        adminPanel.setOnEditAnnouncement(a -> executor.submit(() -> {
            if (platformApiService.updateAnnouncement(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), a[0], a[1], null))
                refreshAnnouncements();
            else adminPanel.setStatus("Failed to edit announcement");
        }));
        adminPanel.setOnTogglePinAnnouncement(a -> executor.submit(() -> {
            if (platformApiService.updateAnnouncement(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), (String) a[0], null, (Boolean) a[1]))
                refreshAnnouncements();
        }));
        adminPanel.setOnDeleteAnnouncement(id -> executor.submit(() -> {
            if (platformApiService.deleteAnnouncement(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), id))
                refreshAnnouncements();
            else adminPanel.setStatus("Failed to delete announcement");
        }));
        executor.submit(this::refreshAnnouncements);

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

        // Shared settings have no plugin-side save — they're edited on the web dashboard. Don't
        // report a fake "saved"; just refresh the local cache so dashboard edits show up.
        adminPanel.setOnSaveSettings(args -> executor.submit(() -> {
            adminPanel.setStatus("Settings are managed from the web dashboard");
            serverConfigLoaded = false;
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
                String startIso = etToIso(args[3]);
                String endIso = etToIso(args[4]);
                if ((args[3] != null && !args[3].isEmpty() && startIso == null)
                    || (args[4] != null && !args[4].isEmpty() && endIso == null))
                {
                    adminPanel.setStatus("Bad date format \u2014 use yyyy-MM-dd HH:mm (ET)");
                    return;
                }
                boolean startsNow = startIso == null;
                adminPanel.setStatus(startsNow ? "Starting event..." : "Scheduling event...");
                adminService.startEventPlatform(getPlatformUrl(), getPlatformKey(), getPlatformSlug(),
                    type, metric, displayName, startIso, endIso);
                adminPanel.setStatus((startsNow ? "Event started: " : "Event scheduled: ") + displayName);
                loadAdminEventsList();

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

                // Immediately refresh event display
                refreshEventLeaderboard();
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        // End a live event / cancel a scheduled one (from the calendar rows)
        adminPanel.setOnCancelEvent(eventId -> executor.submit(() -> {
            try
            {
                adminPanel.setStatus("Ending event...");
                adminService.endEventPlatform(getPlatformUrl(), getPlatformKey(), getPlatformSlug(), eventId);
                adminPanel.setStatus("Event ended");
                serverConfigLoaded = false;
                refreshEventLeaderboard();
                loadAdminEventsList();
            }
            catch (Exception e)
            {
                adminPanel.setStatus("Error: " + e.getMessage());
            }
        }));

        adminPanel.setOnLoadEvents(() -> executor.submit(this::loadAdminEventsList));
        executor.submit(this::loadAdminEventsList);

        adminPanel.setOnSyncRoster(() -> {
            // ClanSettings is only readable on the client thread. Called straight from the Swing
            // EDT (as this used to be) getClanSettings() returns null, so the sync bailed out
            // before posting anything and the roster never pruned leavers. The auto-sync path
            // already hops threads the same way.
            adminPanel.setStatus("Syncing roster…");
            clientThread.invokeLater(() -> {
                int count = hiscoreTracker.syncRoster(getPlatformUrl(), getPlatformKey(), getPlatformSlug());
                javax.swing.SwingUtilities.invokeLater(() -> adminPanel.setStatus(count > 0
                    ? "Synced " + count + " members"
                    : "Roster sync failed — join a clan first"));
            });
        });

        // Show active event state in admin panel on load
        if (!activeEventType.isEmpty())
        {
            adminPanel.setActiveEvent(activeEventType, activeEventDisplayName, activeEventEndTime);
        }

        log.info("Admin panel enabled");
    }
}
