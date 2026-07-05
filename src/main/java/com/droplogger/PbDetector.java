package com.droplogger;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects completion times from OSRS game chat messages.
 * Tracks activity context to determine which boss/raid the time is for.
 * Supports both personal best detection and general completion time detection
 * so clan hiscores can be checked on every completion, not just PBs.
 */
@Slf4j
public class PbDetector
{
    // ── Completion time patterns (match ANY completion, with or without PB) ──

    // "Challenge duration: 22:15.00" or "Challenge duration: 22:15.00 (new personal best)" — CoX or Gauntlet
    private static final Pattern CHALLENGE_TIME = Pattern.compile(
        "Challenge duration: ((\\d+:)?\\d+:\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    // "Theatre of Blood completion time: 23:45.60"
    private static final Pattern TOB_TIME = Pattern.compile(
        "Theatre of Blood.*?completion time: ((\\d+:)?\\d+:\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    // "Tombs of Amascut...completion time: 23:45.60"
    private static final Pattern TOA_TIME = Pattern.compile(
        "Tombs of Amascut.*?completion time: ((\\d+:)?\\d+:\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    // "Hallowed Sepulchre completion time: 5:30.00"
    private static final Pattern SEP_TIME = Pattern.compile(
        "Hallowed Sepulchre.*?completion time: ((\\d+:)?\\d+:\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    // "Fight duration: 1:23.40" — boss kills
    private static final Pattern FIGHT_TIME = Pattern.compile(
        "Fight duration: ((\\d+:)?\\d+:\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    // "Duration: 32:15.60" — wave content (Jad, Zuk, Colo) and general
    private static final Pattern DURATION_TIME = Pattern.compile(
        "Duration: ((\\d+:)?\\d+:\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    // ── Activity identification patterns ──
    // Kill count messages identify which boss was just killed

    // "Your Duke Sucellus kill count is: 50."
    private static final Pattern KC_PATTERN = Pattern.compile(
        "Your (.+?) kill count is: ([\\d,]+)", Pattern.CASE_INSENSITIVE);

    // "Your completed Chambers of Xeric count is: 470." / "…Theatre of Blood count is: 12." —
    // raids use "completed … count", not "kill count", so KC_PATTERN misses them.
    private static final Pattern RAID_COUNT_PATTERN = Pattern.compile(
        "Your completed (.+?) count is: ([\\d,]+)", Pattern.CASE_INSENSITIVE);

    // "Your Barrows chest count is: 120."
    private static final Pattern CHEST_COUNT_PATTERN = Pattern.compile(
        "Your (.+?) chest count is: ([\\d,]+)", Pattern.CASE_INSENSITIVE);

    // "Your Chambers of Xeric challenge/raid completion count is: 50."
    private static final Pattern COX_COMPLETION = Pattern.compile(
        "Chambers of Xeric", Pattern.CASE_INSENSITIVE);

    // "Congratulations - your team completed the Chambers of Xeric!"
    private static final Pattern COX_COMPLETE_MSG = Pattern.compile(
        "completed the Chambers of Xeric", Pattern.CASE_INSENSITIVE);

    // CoX Challenge Mode detection
    private static final Pattern COX_CM_PATTERN = Pattern.compile(
        "Challenge Mode", Pattern.CASE_INSENSITIVE);

    // "Congratulations - your team completed the Theatre of Blood!"
    private static final Pattern TOB_COMPLETE_MSG = Pattern.compile(
        "completed the Theatre of Blood", Pattern.CASE_INSENSITIVE);

    // ── State ──
    private String lastActivity = null;
    private String lastBossName = null;
    private int lastKillCount = 0;
    private boolean isCoxCm = false;
    private long contextAtMs = 0; // when lastActivity was last set from a KC/count message

    // ── Pending duration (Inferno / Fight Caves / Colosseum ordering) ──
    // In TzHaar content and the Colosseum the "Duration: x" line arrives BEFORE the
    // "Your TzKal-Zuk kill count is:" line, so there is no activity context yet when the
    // time appears. The time is parked here and the KC message that follows claims it.
    private String pendingTime = null;
    private boolean pendingPb = false;
    private long pendingAtMs = 0;
    private CompletionResult pendingCompletion = null;

    // Context older than this cannot claim a bare "Duration:" line — after an hour in the
    // Inferno, the last KC message was some unrelated boss (or nothing at all).
    private static final long CONTEXT_FRESH_MS = 30_000;
    // A parked duration must be claimed by a KC message within this window (the game sends
    // both lines in the same tick; the window is generous for lag).
    private static final long PENDING_CLAIM_MS = 10_000;

    /**
     * Process a chat message to update activity context.
     * Call this for every GAMEMESSAGE before checking for PBs.
     */
    public void processMessage(String cleanedMessage)
    {
        // Track kill counts to identify bosses
        Matcher kcMatcher = KC_PATTERN.matcher(cleanedMessage);
        if (kcMatcher.find())
        {
            lastBossName = kcMatcher.group(1).trim();
            lastKillCount = Integer.parseInt(kcMatcher.group(2).replace(",", ""));
            lastActivity = mapBossToGroup(lastBossName);
            contextAtMs = System.currentTimeMillis();
            claimPendingDuration();
            log.debug("Activity context set: {} ({}) KC={}", lastBossName, lastActivity, lastKillCount);
            return;
        }

        // Raid completion counts ("Your completed Chambers of Xeric count is: 470") and chest
        // counts ("Your Barrows chest count is: 120") — set the counter context so raid/chest
        // DROPS get the right KC attached, exactly like boss kill counts.
        Matcher raidCount = RAID_COUNT_PATTERN.matcher(cleanedMessage);
        if (raidCount.find())
        {
            lastBossName = raidCount.group(1).trim();
            lastKillCount = Integer.parseInt(raidCount.group(2).replace(",", ""));
            lastActivity = mapBossToGroup(lastBossName);
            contextAtMs = System.currentTimeMillis();
            log.debug("Raid count context set: {} ({}) KC={}", lastBossName, lastActivity, lastKillCount);
            return;
        }
        Matcher chestCount = CHEST_COUNT_PATTERN.matcher(cleanedMessage);
        if (chestCount.find())
        {
            lastBossName = chestCount.group(1).trim();
            lastKillCount = Integer.parseInt(chestCount.group(2).replace(",", ""));
            lastActivity = mapBossToGroup(lastBossName);
            contextAtMs = System.currentTimeMillis();
            return;
        }

        // Track raid completions
        if (COX_COMPLETE_MSG.matcher(cleanedMessage).find() ||
            COX_COMPLETION.matcher(cleanedMessage).find())
        {
            isCoxCm = COX_CM_PATTERN.matcher(cleanedMessage).find();
            lastActivity = isCoxCm ? "cox_cm" : "cox";
            lastBossName = isCoxCm ? "CM Chambers of Xeric" : "Chambers of Xeric";
            contextAtMs = System.currentTimeMillis();
        }
        else if (TOB_COMPLETE_MSG.matcher(cleanedMessage).find())
        {
            lastActivity = "tob";
            lastBossName = "Theatre of Blood";
            contextAtMs = System.currentTimeMillis();
        }
    }

    /**
     * A KC message just set fresh activity context — if a bare "Duration:" line was parked
     * moments ago (Inferno/Fight Caves/Colosseum send the time BEFORE the kill count), turn
     * it into a completion the plugin can drain via {@link #drainPendingCompletion()}.
     */
    private void claimPendingDuration()
    {
        if (pendingTime == null)
        {
            return;
        }
        String time = pendingTime;
        boolean pb = pendingPb;
        pendingTime = null;
        if (System.currentTimeMillis() - pendingAtMs > PENDING_CLAIM_MS
            || lastActivity == null || "unknown".equals(lastActivity))
        {
            return;
        }
        pendingCompletion = new CompletionResult(lastActivity, time, lastBossName, pb);
        log.debug("Pending duration claimed by {}: {}", lastActivity, time);
    }

    /**
     * Return (and clear) a completion whose duration line arrived before its kill-count
     * line. Call after {@link #processMessage} on every game message.
     */
    public CompletionResult drainPendingCompletion()
    {
        CompletionResult result = pendingCompletion;
        pendingCompletion = null;
        return result;
    }

    /**
     * Check if a chat message contains a completion time (PB or not).
     * Returns a CompletionResult if detected, null otherwise.
     * This fires on every boss kill / raid completion so the clan
     * hiscores can be checked even when it's not a personal best.
     */
    public CompletionResult detectCompletion(String cleanedMessage)
    {
        // Intermediate progress lines also say "Duration:" but are NOT completions:
        //  - third-party phase timers ("phase"/"split"/"section") — produced a 6s "Phosani kill"
        //  - vanilla CoX floor times ("Upper/Middle/Lower level complete! Duration: 4:31")
        //  - Olm phase lines ("Olm duration: 5:20") — produced a 36s "CoX solo"
        //  - ToB room times ("Wave 'The Maiden of Sugadinti' complete! Duration: 2:45")
        // Only the whole-kill / whole-raid message may count.
        String lowerAll = cleanedMessage.toLowerCase();
        if (lowerAll.contains("phase") || lowerAll.contains("split") || lowerAll.contains("section")
            || lowerAll.contains("level complete") || lowerAll.contains("olm duration")
            || lowerAll.contains("wave"))
        {
            return null;
        }

        boolean isPb = cleanedMessage.contains("(new personal best)");
        Matcher matcher;

        // Check ToB first (specific pattern) — distinguish entry/normal/hard
        matcher = TOB_TIME.matcher(cleanedMessage);
        if (matcher.find())
        {
            String lower = cleanedMessage.toLowerCase();
            String tobGroup;
            if (lower.contains("entry")) tobGroup = "tob_entry";
            else if (lower.contains("hard")) tobGroup = "tob_hm";
            else tobGroup = "tob";
            return new CompletionResult(tobGroup, matcher.group(1), null, isPb);
        }

        // Check ToA — entry/normal/expert
        matcher = TOA_TIME.matcher(cleanedMessage);
        if (matcher.find())
        {
            String lower = cleanedMessage.toLowerCase();
            String toaGroup;
            if (lower.contains("entry")) toaGroup = "toa_entry";
            else if (lower.contains("expert")) toaGroup = "toa_expert";
            else toaGroup = "toa";
            return new CompletionResult(toaGroup, matcher.group(1), null, isPb);
        }

        // Check Sepulchre
        matcher = SEP_TIME.matcher(cleanedMessage);
        if (matcher.find())
        {
            return new CompletionResult("sep", matcher.group(1), null, isPb);
        }

        // Check Challenge duration (CoX or Gauntlet — disambiguate by context)
        matcher = CHALLENGE_TIME.matcher(cleanedMessage);
        if (matcher.find())
        {
            String group = resolveChallengeDuration();
            return new CompletionResult(group, matcher.group(1), null, isPb);
        }

        // Check Fight duration (bosses — use lastActivity for boss identity)
        matcher = FIGHT_TIME.matcher(cleanedMessage);
        if (matcher.find())
        {
            String group = lastActivity != null ? lastActivity : "unknown";
            return new CompletionResult(group, matcher.group(1), lastBossName, isPb);
        }

        // Check generic Duration (Jad, Zuk, Colo — use lastActivity).
        // These content types send "Duration:" BEFORE "Your TzKal-Zuk kill count is:", so
        // fresh context means a same-tick multi-line message ordered KC-first; stale or
        // missing context means the KC line is still coming — park the time and let
        // claimPendingDuration() attribute it when the KC message arrives.
        matcher = DURATION_TIME.matcher(cleanedMessage);
        if (matcher.find())
        {
            boolean contextFresh = lastActivity != null && !"unknown".equals(lastActivity)
                && System.currentTimeMillis() - contextAtMs <= CONTEXT_FRESH_MS;
            if (!contextFresh)
            {
                pendingTime = matcher.group(1);
                pendingPb = isPb;
                pendingAtMs = System.currentTimeMillis();
                log.debug("Duration with no fresh context parked: {}", pendingTime);
                return null;
            }
            return new CompletionResult(lastActivity, matcher.group(1), lastBossName, isPb);
        }

        return null;
    }

    /**
     * Resolve "Challenge duration" to either CoX or Gauntlet based on recent context.
     */
    private String resolveChallengeDuration()
    {
        if ("cox_cm".equals(lastActivity))
        {
            return "cox_cm";
        }
        if ("cox".equals(lastActivity))
        {
            return "cox";
        }
        // If lastActivity was already resolved to a gauntlet variant, use it directly
        if (lastActivity != null && lastActivity.startsWith("gaunt"))
        {
            return lastActivity;
        }
        if (lastBossName != null)
        {
            String lower = lastBossName.toLowerCase();
            if (lower.contains("hunllef"))
            {
                return "gaunt_corrupted";
            }
            if (lower.contains("gauntlet") || lower.contains("crystalline"))
            {
                return "gaunt";
            }
        }
        // Default to cox if ambiguous (more common)
        return "cox";
    }

    /**
     * Map a boss name from kill count messages to a group key.
     */
    private String mapBossToGroup(String bossName)
    {
        if (bossName == null) return "unknown";
        String lower = bossName.toLowerCase();

        // DT2 bosses
        if (lower.contains("duke sucellus")) return "duke";
        if (lower.contains("leviathan")) return "leviathan";
        if (lower.contains("whisperer")) return "whisperer";
        if (lower.contains("vardorvis")) return "vardorvis";

        // Nightmare
        if (lower.contains("phosani")) return "phosanis";
        if (lower.contains("nightmare")) return "nightmare";

        // Nex
        if (lower.contains("nex")) return "nex";

        // Araxxor
        if (lower.contains("araxxor")) return "araxxor";

        // Maggot King (Blood Moon Rises, 2026)
        if (lower.contains("maggot king")) return "maggot_king";

        // Gauntlet (hunllef = corrupted, crystalline = normal)
        if (lower.contains("hunllef")) return "gaunt_corrupted";
        if (lower.contains("crystalline")) return "gaunt";

        // Wave content
        if (lower.contains("tztok-jad") || lower.contains("jad")) return "jad";
        if (lower.contains("tzkal-zuk") || lower.contains("zuk")) return "zuk";
        if (lower.contains("sol heredit") || lower.contains("colosseum")) return "colo";

        // BA
        if (lower.contains("barbarian assault") || lower.contains("penance queen")) return "ba";

        // Yama
        if (lower.contains("yama")) return "yama";

        // Titans
        if (lower.contains("titan")) return "titans";

        // Hueycoatl / Amoxliatl
        if (lower.contains("hueycoatl")) return "hueycoatl";
        if (lower.contains("amoxliatl")) return "amoxliatl";

        // GWD bosses
        if (lower.contains("general graardor") || lower.contains("graardor")) return "bandos";
        if (lower.contains("commander zilyana") || lower.contains("zilyana")) return "sara";
        if (lower.contains("k'ril") || lower.contains("tsutsaroth")) return "zammy";
        if (lower.contains("kree") || lower.contains("kree'arra")) return "arma";

        // Slayer bosses
        if (lower.contains("cerberus")) return "cerberus";
        if (lower.contains("alchemical hydra") || lower.contains("hydra")) return "hydra";
        if (lower.contains("thermonuclear") || lower.contains("smoke devil")) return "thermy";
        if (lower.contains("kraken")) return "kraken";
        if (lower.contains("abyssal sire") || lower.contains("sire")) return "sire";
        if (lower.contains("grotesque")) return "grotesque";
        if (lower.contains("skotizo")) return "skotizo";

        // Other bosses
        if (lower.contains("zulrah")) return "zulrah";
        if (lower.contains("vorkath")) return "vorkath";
        if (lower.contains("kalphite queen")) return "kq";
        if (lower.contains("corporeal beast") || lower.contains("corp")) return "corp";
        if (lower.contains("giant mole")) return "mole";
        if (lower.contains("sarachnis")) return "sarachnis";
        if (lower.contains("king black dragon")) return "kbd";
        if (lower.contains("dagannoth")) return "dks";
        if (lower.contains("barrows")) return "barrows";
        if (lower.contains("bryophyta")) return "bryophyta";
        if (lower.contains("obor")) return "obor";
        if (lower.contains("hespori")) return "hespori";

        // Wilderness bosses (includes de-wildy variants)
        if (lower.contains("callisto") || lower.contains("artio")) return "callisto";
        if (lower.contains("vet'ion") || lower.contains("calvar")) return "vetion";
        if (lower.contains("venenatis") || lower.contains("spindel")) return "venenatis";
        if (lower.contains("chaos elemental")) return "chaos_ele";
        if (lower.contains("scorpia")) return "scorpia";
        if (lower.contains("crazy archaeologist")) return "crazy_arch";

        // Sepulchre
        if (lower.contains("sepulchre")) return "sep";

        // TzHaar-Ket-Rak challenges
        if (lower.contains("ket-rak") || lower.contains("ket rak")) return "raks";

        // Raids (usually identified by completion messages, not KC). The CM count message is
        // "Your completed Chambers of Xeric: Challenge Mode count is: X" — check CM first or
        // solo/team CM completions land on the regular CoX board.
        if (lower.contains("chambers") || lower.contains("xeric"))
        {
            return lower.contains("challenge mode") ? "cox_cm" : "cox";
        }
        if (lower.contains("theatre") || lower.contains("verzik")) return "tob";
        if (lower.contains("tombs") || lower.contains("amascut")) return "toa";

        return "unknown";
    }

    /**
     * Return the last parsed kill count from chat (e.g. "Your X kill count is: 50").
     */
    public int getLastKillCount()
    {
        return lastKillCount;
    }

    /**
     * Get the last tracked boss name (for logging/debugging).
     */
    public String getLastBossName()
    {
        return lastBossName;
    }

    public void reset()
    {
        lastActivity = null;
        lastBossName = null;
        isCoxCm = false;
        contextAtMs = 0;
        pendingTime = null;
        pendingCompletion = null;
    }

    /**
     * Result of a completion time detection.
     */
    @Getter
    public static class CompletionResult
    {
        private final String group;
        private final String formattedTime;
        private final double timeSeconds;
        private final String bossName;
        private final boolean personalBest;

        public CompletionResult(String group, String formattedTime, String bossName, boolean personalBest)
        {
            this.group = group;
            this.formattedTime = formattedTime;
            this.timeSeconds = parseTimeToSeconds(formattedTime);
            this.bossName = bossName;
            this.personalBest = personalBest;
        }
    }

    /** Parse a "h:mm:ss.ss" / "mm:ss.ss" / "ss.ss" completion time into seconds. Pure utility. */
    static double parseTimeToSeconds(String timeStr)
    {
        if (timeStr == null || timeStr.isEmpty())
        {
            return 0;
        }

        String[] parts = timeStr.split(":");
        double seconds = 0;

        try
        {
            if (parts.length == 3)
            {
                seconds = Double.parseDouble(parts[0]) * 3600
                    + Double.parseDouble(parts[1]) * 60
                    + Double.parseDouble(parts[2]);
            }
            else if (parts.length == 2)
            {
                seconds = Double.parseDouble(parts[0]) * 60
                    + Double.parseDouble(parts[1]);
            }
            else if (parts.length == 1)
            {
                seconds = Double.parseDouble(parts[0]);
            }
        }
        catch (NumberFormatException e)
        {
            return 0;
        }

        return seconds;
    }
}
