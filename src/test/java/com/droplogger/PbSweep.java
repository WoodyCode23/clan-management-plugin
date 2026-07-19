package com.droplogger;

/**
 * Parsing sweep: real chat-message sequences through PbDetector, asserting which board
 * each time lands on. Run via `gradlew sweep`. Exits non-zero on any failure so it can
 * gate future detector changes.
 */
public class PbSweep
{
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args)
    {
        // ── Gauntlet ──
        // count-first ordering (context set before the time line)
        seq("CG count-first", "gaunt_corrupted", "6:33.60",
            "Your Corrupted Gauntlet completion count is: 45.",
            "Challenge duration: 6:33.60. Personal best: 5:57.00.");
        // time-first ordering (cold client: park + claim)
        seq("CG time-first (parked)", "gaunt_corrupted", "6:33.60",
            "Challenge duration: 6:33.60. Personal best: 5:57.00.",
            "Your Corrupted Gauntlet completion count is: 45.");
        seq("Gauntlet normal", "gaunt", "7:39.00",
            "Your Gauntlet completion count is: 12.",
            "Challenge duration: 7:39.00 (new personal best)");

        // ── Chambers of Xeric ──
        seq("CoX duration-first (parked)", "cox", "41:40.80",
            "Team size: Solo Duration: 41:40.80 Personal best: 39:53.40",
            "Your completed Chambers of Xeric count is: 470.");
        seq("CoX CM count-first", "cox_cm", "28:11.40",
            "Your completed Chambers of Xeric: Challenge Mode count is: 30.",
            "Team size: Solo Duration: 28:11.40 Personal best: 27:02.00");
        reject("CoX floor line", "Upper level complete! Duration: 4:31.00");
        reject("Olm phase line", "Olm duration: 5:20.00");

        // ── Theatre of Blood ──
        one("ToB room time", "tob", "15:07.20",
            "Theatre of Blood completion time: 15:07.20. Personal best: 13:56.40");
        reject("ToB total time", "Theatre of Blood total completion time: 17:37.80. Personal best: 16:10.20");
        one("ToB entry", "tob_entry", "18:00.00",
            "Theatre of Blood: Entry Mode completion time: 18:00.00. Personal best: 17:00.00");
        one("ToB hard", "tob_hm", "20:30.00",
            "Theatre of Blood: Hard Mode completion time: 20:30.00. Personal best: 19:00.00");
        reject("ToB room wave line", "Wave 'The Maiden of Sugadinti' complete! Duration: 2:45.00");
        reject("ToB final wave line", "Wave 'The Final Challenge' (Normal Mode) complete! Duration: 4:04.60");

        // ── Tombs of Amascut ──
        one("ToA expert challenge time", "toa_expert", "25:33.00",
            "Tombs of Amascut: Expert Mode challenge completion time: 25:33.00. Personal best: 23:12.60");
        reject("ToA total time", "Tombs of Amascut: Expert Mode total completion time: 28:33.60. Personal best: 26:09.00");
        one("ToA normal", "toa", "26:22.00",
            "Tombs of Amascut challenge completion time: 26:22.00. Personal best: 25:08.00");

        // ── Wave content (duration before KC) ──
        seq("Inferno parked", "zuk", "68:33.00",
            "Duration: 68:33.00. Personal best: 65:00.00",
            "Your TzKal-Zuk kill count is: 5.");
        seq("Fight Caves parked", "jad", "32:15.60",
            "Duration: 32:15.60 (new personal best)",
            "Your TzTok-Jad kill count is: 8.");
        seq("Colosseum parked", "colo", "26:56.40",
            "Colosseum duration: 26:56.40. Personal best: 25:00.00",
            "Your Sol Heredit kill count is: 3.");
        reject("Colosseum wave line", "Wave: 11 Wave duration: 2:15.00");

        // ── Bosses ──
        seq("GG kc-first", "grotesque", "1:05.40",
            "Your Grotesque Guardians kill count is: 100.",
            "Fight duration: 1:05.40. Personal best: 1:02.00");
        seq("GG fight-first (parked)", "grotesque", "1:05.40",
            "Fight duration: 1:05.40. Personal best: 1:02.00",
            "Your Grotesque Guardians kill count is: 100.");
        seq("Phosani solo", "phosanis", "5:03.00",
            "Your Phosani's Nightmare kill count is: 714.",
            "Team size: Solo Fight duration: 5:03.00. Personal best: 4:25.20");
        reject("Nightmare plugin phase line",
            "Phosani's Nightmare P4 boss complete! Duration: 0:08.40 Total: 5:03.00");
        seq("Yama", "yama", "3:21.00",
            "Your Yama kill count is: 1090.",
            "Fight duration: 3:21.00 (new personal best)");

        System.out.println("──────────────────");
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** Feed messages in order to a FRESH detector; assert the final detected completion. */
    private static void seq(String name, String expectGroup, String expectTime, String... messages)
    {
        PbDetector d = new PbDetector();
        PbDetector.CompletionResult result = null;
        for (String m : messages)
        {
            d.processMessage(m);
            PbDetector.CompletionResult r = d.detectCompletion(m);
            if (r != null) result = r;
            PbDetector.CompletionResult drained = d.drainPendingCompletion();
            if (drained != null) result = drained;
        }
        check(name, result, expectGroup, expectTime);
    }

    /** Single message with no context must resolve on its own. */
    private static void one(String name, String expectGroup, String expectTime, String message)
    {
        seq(name, expectGroup, expectTime, message);
    }

    /** Message must NOT produce a completion (phase/progress/total lines). */
    private static void reject(String name, String message)
    {
        PbDetector d = new PbDetector();
        d.processMessage(message);
        PbDetector.CompletionResult r = d.detectCompletion(message);
        PbDetector.CompletionResult drained = d.drainPendingCompletion();
        if (r == null && drained == null)
        {
            passed++;
            System.out.println("PASS  " + name);
        }
        else
        {
            failed++;
            PbDetector.CompletionResult bad = r != null ? r : drained;
            System.out.println("FAIL  " + name + " — unexpectedly detected " + bad.getGroup() + " " + bad.getFormattedTime());
        }
    }

    private static void check(String name, PbDetector.CompletionResult r, String expectGroup, String expectTime)
    {
        if (r != null && expectGroup.equals(r.getGroup()) && expectTime.equals(r.getFormattedTime()))
        {
            passed++;
            System.out.println("PASS  " + name);
        }
        else
        {
            failed++;
            System.out.println("FAIL  " + name + " — expected " + expectGroup + " " + expectTime
                + ", got " + (r == null ? "null" : r.getGroup() + " " + r.getFormattedTime()));
        }
    }
}
