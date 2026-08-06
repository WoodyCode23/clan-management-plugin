package com.droplogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clan rank requirements + evaluator.
 *
 * PRIVACY — IMPORTANT: rank requirements are evaluated **entirely on the client, at runtime**.
 * The player's bank / inventory / item data is read locally ONLY to tick requirement boxes for
 * themselves and is NEVER sent anywhere. Nothing about a player's items or bank leaves the client.
 * When a member chooses to CLAIM a rank, the plugin sends only the evaluation RESULT (eligible +
 * which named requirements are met/missing for that one rank) to the clan's admin feed — not the
 * bank. See the rank-claim flow in PlatformApiService / ClanManagementPlugin.
 *
 * A rank is a set of requirement GROUPS (each "achieve N of M" checks — a single mandatory
 * requirement is just N=1, M=1) plus prerequisite ranks. Checks are either leaves (own item(s),
 * skill level, CA tier/task, total level, diary count, boss kc) or composites (ALL-of / ANY-of
 * nested checks, for "a full Moon set" or "X or Y"). Pure logic — no RuneLite deps here.
 */
public final class RankSystem
{
    public enum Kind { ITEMS, ITEMS_PREFIX, SKILL, TOTAL, CA_TIER, CA_TASK, DIARY, BOSS_KC, ALL, ANY, RANK, TOTAL_XP, CLOG, UNLOCK, COMBAT_LEVEL, CLOG_SLOT }

    /** A single requirement check (leaf or composite). */
    public static final class Check
    {
        public final Kind kind;
        public final String label;
        public final List<String> names;   // ITEMS: item names; CA_TASK: task name; key skills/tiers/boss
        public final String key;            // SKILL skill / CA_TIER tier / DIARY tier / BOSS_KC boss
        public final int value;             // SKILL level / TOTAL min / DIARY count / BOSS_KC count / ITEMS k
        public final List<Check> children;  // ALL/ANY
        public final int need;              // ANY: how many children

        private Check(Kind kind, String label, List<String> names, String key, int value, List<Check> children, int need)
        {
            this.kind = kind; this.label = label; this.names = names; this.key = key;
            this.value = value; this.children = children; this.need = need;
        }

        public static Check item(String name) { return items(name, 1, name); }
        /** Own k of the listed item names (k = names.length means "all", k = 1 means "any"). */
        public static Check items(String label, int k, String... names)
        { return new Check(Kind.ITEMS, label, Arrays.asList(names), null, k, null, 0); }
        // Match owned items by NAME PREFIX — covers league/locked/broken variants (e.g. "Void ranger
        // helm (l)") that share the base name. Keeps ranged-only for the void helm since melee/mage
        // are "Void melee/mage helm", which don't share the "Void ranger helm" prefix.
        public static Check itemPrefix(String name) { return itemsPrefix(name, 1, name); }
        public static Check itemsPrefix(String label, int k, String... prefixes)
        { return new Check(Kind.ITEMS_PREFIX, label, Arrays.asList(prefixes), null, k, null, 0); }
        public static Check skill(String label, String skill, int level)
        { return new Check(Kind.SKILL, label, null, skill, level, null, 0); }
        public static Check total(int min) { return new Check(Kind.TOTAL, min + " total level", null, null, min, null, 0); }
        public static Check caTier(String tier) { return new Check(Kind.CA_TIER, tier + " Combat Achievements", null, tier, 0, null, 0); }
        public static Check caTask(String task) { return new Check(Kind.CA_TASK, task, Arrays.asList(task), null, 0, null, 0); }
        public static Check diary(String tier, int count) { return new Check(Kind.DIARY, count + " " + tier + " Diaries", null, tier, count, null, 0); }
        public static Check kc(String boss, int count) { return new Check(Kind.BOSS_KC, boss + " " + count + " kc", null, boss, count, null, 0); }
        /** KC check with an explicit WiseOldMan/snapshot key (separate from the display label). */
        public static Check kc(String label, int count, String key) { return new Check(Kind.BOSS_KC, label, null, key, count, null, 0); }
        public static Check all(String label, Check... cs) { return new Check(Kind.ALL, label, null, null, 0, Arrays.asList(cs), cs.length); }
        public static Check any(String label, int need, Check... cs) { return new Check(Kind.ANY, label, null, null, 0, Arrays.asList(cs), need); }
        /** Hold another rank (for tiered prerequisites shown inline + Heart-of-Solus "N Beast ranks"). */
        public static Check rank(String rankId, String name) { return new Check(Kind.RANK, name, null, rankId, 0, null, 0); }
        public static Check totalXp(int xp, String label) { return new Check(Kind.TOTAL_XP, label, null, null, xp, null, 0); }
        public static Check clog(int count) { return new Check(Kind.CLOG, count + " Collection Log slots", null, null, count, null, 0); }
        /** A persistent unlock (e.g. a prayer learned from a scroll — the scroll itself is consumed). */
        public static Check unlock(String label, String key) { return new Check(Kind.UNLOCK, label, null, key, 0, null, 0); }
        /** Minimum combat level. */
        public static Check combat(int level) { return new Check(Kind.COMBAT_LEVEL, level + " Combat", null, null, level, null, 0); }
        /** Any of the named collection-log slots has been obtained. Read from the synced clog set (a
         *  permanent unlock/proof), NOT current bank possession, so it is exempt from the clog-tightening. */
        public static Check clogSlot(String label, String... names) { return new Check(Kind.CLOG_SLOT, label, Arrays.asList(names), null, 0, null, 0); }
    }

    /** A requirement group: need N of the option checks satisfied. */
    public static final class Group
    {
        public final String label;
        public final int need;
        public final List<Check> options;
        public Group(String label, int need, List<Check> options) { this.label = label; this.need = need; this.options = options; }
        public static Group of(String label, int need, Check... options) { return new Group(label, need, Arrays.asList(options)); }
    }

    public static final class Rank
    {
        public final String id, name, path, desc;
        public final List<String> requires;
        public final List<Group> groups;
        public Rank(String id, String name, String path, String desc, List<String> requires, List<Group> groups)
        { this.id = id; this.name = name; this.path = path; this.desc = desc; this.requires = requires; this.groups = groups; }
    }

    /** Live in-game snapshot the checks evaluate against. */
    public static final class PlayerSnapshot
    {
        public final Set<String> ownedItems = new HashSet<>();      // lowercased names owned (equip+inv+bank+clog)
        public final Map<String, Integer> itemIds = new HashMap<>(); // lowercased name -> item id (owned, for icons)
        public final Map<String, Integer> skills = new HashMap<>(); // lowercased skill -> level
        public int totalLevel;
        public long totalXp;                                        // overall XP (for XP Beast)
        public int clogSlots;                                       // collection log slots obtained (Log Beast)
        public int combatLevel;                                     // computed combat level (COMBAT_LEVEL checks)
        public final Set<String> clogObtained = new HashSet<>();    // lowercased clog-slot names ever obtained (CLOG_SLOT checks)
        public final Set<String> unlocks = new HashSet<>();         // persistent unlocks (prayers, etc.) lowercased
        public final Set<String> caDone = new HashSet<>();          // lowercased completed CA task names
        public final Set<String> caTiersComplete = new HashSet<>(); // lowercased tiers fully complete
        public final Map<String, Integer> diaryComplete = new HashMap<>(); // tier(lower) -> # regions complete
        public final Map<String, Integer> kc = new HashMap<>();     // lowercased boss -> kc
        public final Set<String> ranksHeld = new HashSet<>();       // rank ids already earned/qualified
    }

    public static final class Result
    {
        public final Check check; public final boolean met; public final String label;
        public Result(Check c, boolean met) { this.check = c; this.met = met; this.label = c.label; }
    }

    public static final class RankStatus
    {
        public final Rank rank;
        public boolean eligible;
        public boolean granted; // held via the member's Discord rank (auto-checked, not evaluated)
        public final List<String> unmetRequires = new ArrayList<>();
        public final List<GroupStatus> groups = new ArrayList<>();
        public RankStatus(Rank r) { this.rank = r; }
    }

    public static final class GroupStatus
    {
        public final Group group; public final int met; public final List<Result> results;
        public GroupStatus(Group g, int met, List<Result> results) { this.group = g; this.met = met; this.results = results; }
        public boolean satisfied() { return met >= group.need; }
    }

    // ── Evaluation ──

    public static boolean evalCheck(Check c, PlayerSnapshot s)
    {
        switch (c.kind)
        {
            case ITEMS:
            {
                int have = 0;
                for (String n : c.names) if (s.ownedItems.contains(n.toLowerCase())) have++;
                return have >= c.value;
            }
            case ITEMS_PREFIX:
            {
                int have = 0;
                for (String p : c.names)
                {
                    String pl = p.toLowerCase();
                    for (String owned : s.ownedItems) if (owned.startsWith(pl)) { have++; break; }
                }
                return have >= c.value;
            }
            case SKILL:
                return s.skills.getOrDefault(c.key.toLowerCase(), 0) >= c.value;
            case TOTAL:
                return s.totalLevel >= c.value;
            case CA_TIER:
                return s.caTiersComplete.contains(c.key.toLowerCase());
            case CA_TASK:
                return s.caDone.contains(c.names.get(0).toLowerCase());
            case DIARY:
                return s.diaryComplete.getOrDefault(c.key.toLowerCase(), 0) >= c.value;
            case BOSS_KC:
                return s.kc.getOrDefault(c.key.toLowerCase(), 0) >= c.value;
            case RANK:
                return s.ranksHeld.contains(c.key.toLowerCase());
            case TOTAL_XP:
                return s.totalXp >= c.value;
            case CLOG:
                return s.clogSlots >= c.value;
            case UNLOCK:
                return s.unlocks.contains(c.key.toLowerCase());
            case COMBAT_LEVEL:
                return s.combatLevel >= c.value;
            case CLOG_SLOT:
                for (String n : c.names) if (s.clogObtained.contains(n.toLowerCase())) return true;
                return false;
            case ALL:
                for (Check ch : c.children) if (!evalCheck(ch, s)) return false;
                return true;
            case ANY:
            {
                int n = 0;
                for (Check ch : c.children) if (evalCheck(ch, s)) n++;
                return n >= c.need;
            }
            default:
                return false;
        }
    }

    public static RankStatus evaluate(Rank r, PlayerSnapshot s)
    {
        RankStatus rs = new RankStatus(r);
        boolean ok = true;
        for (String req : r.requires)
        {
            if (!s.ranksHeld.contains(req)) { rs.unmetRequires.add(nameOf(req)); ok = false; }
        }
        for (Group g : r.groups)
        {
            List<Result> results = new ArrayList<>();
            int met = 0;
            for (Check c : g.options)
            {
                boolean m = evalCheck(c, s);
                if (m) met++;
                results.add(new Result(c, m));
            }
            GroupStatus gs = new GroupStatus(g, met, results);
            rs.groups.add(gs);
            if (!gs.satisfied()) ok = false;
        }
        rs.eligible = ok;
        return rs;
    }

    public static String nameOf(String id)
    {
        for (Rank r : RANKS) if (r.id.equals(id)) return r.name;
        return id;
    }

    /** First concrete item / clog-slot name inside a check (recursing composites), for icon display. */
    public static String firstItemName(Check c)
    {
        if ((c.kind == Kind.ITEMS || c.kind == Kind.ITEMS_PREFIX || c.kind == Kind.CLOG_SLOT) && c.names != null && !c.names.isEmpty()) return c.names.get(0);
        if (c.children != null) for (Check ch : c.children) { String n = firstItemName(ch); if (n != null) return n; }
        return null;
    }

    /**
     * Evaluate every rank with prerequisite tiering resolved. A rank counts as "held" once its own
     * groups AND its required lower ranks are satisfied; we iterate to a fixpoint so a Rune rank can
     * see its Adamant prerequisite, and Heart-of-Solus can see the Beast ranks it counts. RANK checks
     * (Heart tiers) re-evaluate each pass as ranksHeld grows.
     */
    public static List<RankStatus> evaluateAll(PlayerSnapshot s)
    {
        return evaluateAll(s, null);
    }

    /**
     * Evaluate with a set of ranks the member already holds via their Discord rank. Those (and
     * everything below them) are marked "granted" — auto-checked without needing their requirements,
     * and they satisfy the prerequisites of higher ranks so the member only sees what's left to earn.
     */
    public static List<RankStatus> evaluateAll(PlayerSnapshot s, Set<String> granted)
    {
        // Expand granted ranks through their OWN prerequisite chains (Dragon Comp → Dragon
        // Sword/Pick → Rune → Adamant; Beast → TzKal + Maxed). Discord role order is not used —
        // the Sword/Pick paths and the four Beast ranks are parallel, so only the requires DAG
        // says what a held rank implies.
        if (granted != null && !granted.isEmpty())
        {
            granted = new HashSet<>(granted);
            boolean grew = true;
            while (grew)
            {
                grew = false;
                for (Rank r : RANKS)
                {
                    if (!granted.contains(r.id)) continue;
                    for (String req : r.requires) if (granted.add(req)) grew = true;
                }
            }
        }
        s.ranksHeld.clear();
        if (granted != null) s.ranksHeld.addAll(granted);
        List<RankStatus> out = new ArrayList<>();
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 25)
        {
            changed = false;
            out = new ArrayList<>();
            for (Rank r : RANKS)
            {
                RankStatus rs = evaluate(r, s);
                if (granted != null && granted.contains(r.id))
                {
                    rs.granted = true;
                    rs.eligible = true;
                    rs.unmetRequires.clear();
                }
                out.add(rs);
                if (rs.eligible && s.ranksHeld.add(r.id)) changed = true;
            }
        }
        return out;
    }

    // ── Upgrade implications: owning the key item satisfies the base item's requirement ──
    // (e.g. own Ultor ring → Berserker ring (i) ticks; own Divine rune pouch → Rune pouch ticks).
    // Names lowercased. Expansion is transitive (chains resolve fully). Audit/extend as needed.
    public static final Map<String, String[]> IMPLIES = new HashMap<>();

    static
    {
        // Vestige rings → imbued ring → base ring
        IMPLIES.put("ultor ring", new String[]{"berserker ring (i)"});
        IMPLIES.put("magus ring", new String[]{"seers ring (i)"});
        IMPLIES.put("bellator ring", new String[]{"warrior ring (i)"});
        IMPLIES.put("venator ring", new String[]{"archers ring (i)"});
        IMPLIES.put("berserker ring (i)", new String[]{"berserker ring"});
        IMPLIES.put("seers ring (i)", new String[]{"seers ring"});
        IMPLIES.put("warrior ring (i)", new String[]{"warrior ring"});
        IMPLIES.put("archers ring (i)", new String[]{"archers ring"});
        // Rune pouch
        IMPLIES.put("divine rune pouch", new String[]{"rune pouch"});
        // Skilling storage bags rename when opened (and some have combined upgrades) — every
        // variant counts as owning the base bag. Fullness doesn't rename items, so name matching
        // already covers full/empty.
        IMPLIES.put("open coal bag", new String[]{"coal bag"});
        IMPLIES.put("open herb sack", new String[]{"herb sack"});
        IMPLIES.put("open seed box", new String[]{"seed box"});
        IMPLIES.put("open fish barrel", new String[]{"fish barrel"});
        IMPLIES.put("fish sack barrel", new String[]{"fish barrel"});      // fish sack + barrel combo
        IMPLIES.put("open fish sack barrel", new String[]{"fish barrel"});
        IMPLIES.put("open log basket", new String[]{"log basket"});
        IMPLIES.put("forestry basket", new String[]{"log basket"});        // forestry kit + log basket
        IMPLIES.put("open forestry basket", new String[]{"log basket"});
        IMPLIES.put("open plank sack", new String[]{"plank sack"});
        IMPLIES.put("open gem bag", new String[]{"gem bag"});
        // Ava's
        IMPLIES.put("ava's assembler", new String[]{"ava's accumulator"});
        IMPLIES.put("ava's accumulator", new String[]{"ava's attractor"});
        // Defenders
        IMPLIES.put("avernic defender", new String[]{"dragon defender"});
        // Imbued max capes → imbued capes
        IMPLIES.put("imbued saradomin max cape", new String[]{"imbued saradomin cape"});
        IMPLIES.put("imbued guthix max cape", new String[]{"imbued guthix cape"});
        IMPLIES.put("imbued zamorak max cape", new String[]{"imbued zamorak cape"});
        // Black mask
        IMPLIES.put("slayer helmet (i)", new String[]{"black mask (i)"});
        IMPLIES.put("slayer helmet", new String[]{"black mask"});
        IMPLIES.put("black mask (i)", new String[]{"black mask"});
        // Void: elite implies normal
        IMPLIES.put("elite void top", new String[]{"void knight top"});
        IMPLIES.put("elite void robe", new String[]{"void knight robe"});
        // Masori fortified → base
        IMPLIES.put("masori mask (f)", new String[]{"masori mask"});
        IMPLIES.put("masori body (f)", new String[]{"masori body"});
        IMPLIES.put("masori chaps (f)", new String[]{"masori chaps"});
        // Zenyte ornament kits → base
        IMPLIES.put("amulet of torture (or)", new String[]{"amulet of torture"});
        IMPLIES.put("necklace of anguish (or)", new String[]{"necklace of anguish"});
        IMPLIES.put("tormented bracelet (or)", new String[]{"tormented bracelet"});
        IMPLIES.put("ring of suffering (ri)", new String[]{"ring of suffering (i)"});
        IMPLIES.put("ring of suffering (i)", new String[]{"ring of suffering"});
        // Osmumten's fang ornament (Cursed phalanx) → base fang
        IMPLIES.put("osmumten's fang (or)", new String[]{"osmumten's fang"});
        // Godsword ornament kits → base (TzKal wants a BGS; the kitted one counts)
        IMPLIES.put("bandos godsword (or)", new String[]{"bandos godsword"});
        IMPLIES.put("armadyl godsword (or)", new String[]{"armadyl godsword"});
        IMPLIES.put("saradomin godsword (or)", new String[]{"saradomin godsword"});
        IMPLIES.put("zamorak godsword (or)", new String[]{"zamorak godsword"});
        // Dragon pickaxe cosmetic variants → base (kit, Zalcano, Trailblazer)
        IMPLIES.put("dragon pickaxe (or)", new String[]{"dragon pickaxe"});
        IMPLIES.put("dragon pickaxe (upgraded)", new String[]{"dragon pickaxe"});
        IMPLIES.put("dragon pickaxe (or) (zalcano)", new String[]{"dragon pickaxe"});
        IMPLIES.put("dragon pickaxe (or) (trailblazer)", new String[]{"dragon pickaxe"});
        // Crystal + infernal pickaxe/axe/harpoon are built FROM the dragon tool
        IMPLIES.put("crystal pickaxe", new String[]{"dragon pickaxe"});
        IMPLIES.put("crystal axe", new String[]{"dragon axe"});
        IMPLIES.put("crystal harpoon", new String[]{"dragon harpoon"});
        // Infernal tool = dragon tool + a CONSUMED Smouldering stone, so owning one proves both.
        IMPLIES.put("infernal pickaxe", new String[]{"dragon pickaxe", "smouldering stone"});
        IMPLIES.put("infernal axe", new String[]{"dragon axe", "smouldering stone"});
        IMPLIES.put("infernal harpoon", new String[]{"dragon harpoon", "smouldering stone"});
        IMPLIES.put("dragon axe (or)", new String[]{"dragon axe"});
        IMPLIES.put("dragon harpoon (or)", new String[]{"dragon harpoon"});
        // Weapon upgrades count for the base they're built from
        IMPLIES.put("emberlight", new String[]{"arclight"});                 // Emberlight is the Arclight upgrade
        IMPLIES.put("trident of the swamp", new String[]{"trident of the seas", "magic fang"}); // swamp = seas + magic fang
        IMPLIES.put("trident of the swamp (e)", new String[]{"trident of the swamp"});
        IMPLIES.put("toxic trident", new String[]{"trident of the swamp"});      // alt name for the swamp trident
        IMPLIES.put("toxic trident (e)", new String[]{"trident of the swamp"});
        IMPLIES.put("trident of the swamp (o)", new String[]{"trident of the swamp"});      // ornament kit
        IMPLIES.put("trident of the swamp (e) (o)", new String[]{"trident of the swamp"});
        // Uncharged tridents still count as having obtained the trident
        IMPLIES.put("uncharged trident", new String[]{"trident of the seas"});
        IMPLIES.put("uncharged trident (e)", new String[]{"trident of the seas"});
        IMPLIES.put("uncharged toxic trident", new String[]{"trident of the swamp"});
        IMPLIES.put("uncharged toxic trident (e)", new String[]{"trident of the swamp"});
        // Ancestral + Torva ornament-kit variants count for the base piece (TzKal armour group)
        IMPLIES.put("ancestral hat (or)", new String[]{"ancestral hat"});
        IMPLIES.put("ancestral robe top (or)", new String[]{"ancestral robe top"});
        IMPLIES.put("ancestral robe bottom (or)", new String[]{"ancestral robe bottom"});
        IMPLIES.put("torva full helm (or)", new String[]{"torva full helm"});
        IMPLIES.put("torva platebody (or)", new String[]{"torva platebody"});
        IMPLIES.put("torva platelegs (or)", new String[]{"torva platelegs"});
        IMPLIES.put("dizana's blessed quiver", new String[]{"dizana's quiver"}); // correct in-game name
        IMPLIES.put("dizana's max cape", new String[]{"dizana's quiver"});  // quiver sunk into the max cape
        IMPLIES.put("dizana's max hood", new String[]{"dizana's quiver"});
        IMPLIES.put("dizana's quiver (broken)", new String[]{"dizana's quiver"});
        IMPLIES.put("amulet of rancour", new String[]{"amulet of torture", "araxyte fang"}); // rancour = torture + Araxyte fang
        IMPLIES.put("amulet of rancour (s)", new String[]{"amulet of rancour"}); // ornament variant
        IMPLIES.put("confliction gauntlets", new String[]{"tormented bracelet"}); // tormented upgrade (wiki-confirmed)
        IMPLIES.put("ferocious gloves", new String[]{"hydra leather"}); // made FROM the leather
        IMPLIES.put("dragon hunter lance", new String[]{"hydra's claw"}); // lance is built FROM the Hydra's claw
        // Corrupted Gauntlet: crystal gear is built FROM the seeds (owning the gear proves the seed)
        IMPLIES.put("blade of saeldor", new String[]{"enhanced crystal weapon seed"});
        IMPLIES.put("blade of saeldor (c)", new String[]{"enhanced crystal weapon seed"});
        IMPLIES.put("bow of faerdhinen", new String[]{"enhanced crystal weapon seed"});
        IMPLIES.put("bow of faerdhinen (c)", new String[]{"enhanced crystal weapon seed"});
        IMPLIES.put("crystal helm", new String[]{"crystal armour seed"});
        IMPLIES.put("crystal body", new String[]{"crystal armour seed"});
        IMPLIES.put("crystal legs", new String[]{"crystal armour seed"});
        // Maggot King necklace chain: rupture = anguish + etched fang (proves owning both).
        IMPLIES.put("necklace of rupture", new String[]{"necklace of anguish", "etched elder venator fang"});
        IMPLIES.put("etched elder venator fang", new String[]{"elder venator fang"});
        // Infernal / Fire / Ava's cape variants count for the base
        IMPLIES.put("infernal max cape", new String[]{"infernal cape"});
        IMPLIES.put("infernal cape", new String[]{"fire cape"}); // a fire cape is sacrificed to enter the Inferno
        IMPLIES.put("fire max cape", new String[]{"fire cape"});
        IMPLIES.put("assembler max cape", new String[]{"ava's assembler"});
        IMPLIES.put("accumulator max cape", new String[]{"ava's accumulator"});
        IMPLIES.put("masori assembler", new String[]{"ava's assembler"}); // assembler + masori body
        IMPLIES.put("masori assembler max cape", new String[]{"ava's assembler"});
        // Cerberus boots are made FROM the crystals — owning the boot satisfies the crystal check.
        IMPLIES.put("primordial boots", new String[]{"primordial crystal"});
        IMPLIES.put("pegasian boots", new String[]{"pegasian crystal"});
        IMPLIES.put("eternal boots", new String[]{"eternal crystal"});
        // Avernic treads upgrades are modular — each variant counts as the base treads PLUS the
        // boots that were sunk into it (which in turn imply their crystals, transitively).
        IMPLIES.put("avernic treads (pr)", new String[]{"avernic treads", "primordial boots"});
        IMPLIES.put("avernic treads (pe)", new String[]{"avernic treads", "pegasian boots"});
        IMPLIES.put("avernic treads (et)", new String[]{"avernic treads", "eternal boots"});
        IMPLIES.put("avernic treads (pr)(pe)", new String[]{"avernic treads", "primordial boots", "pegasian boots"});
        IMPLIES.put("avernic treads (pe)(pr)", new String[]{"avernic treads", "primordial boots", "pegasian boots"});
        IMPLIES.put("avernic treads (pr)(et)", new String[]{"avernic treads", "primordial boots", "eternal boots"});
        IMPLIES.put("avernic treads (et)(pr)", new String[]{"avernic treads", "primordial boots", "eternal boots"});
        IMPLIES.put("avernic treads (pe)(et)", new String[]{"avernic treads", "pegasian boots", "eternal boots"});
        IMPLIES.put("avernic treads (et)(pe)", new String[]{"avernic treads", "pegasian boots", "eternal boots"});
        IMPLIES.put("avernic treads (max)", new String[]{"avernic treads", "primordial boots", "pegasian boots", "eternal boots"});
        // Cosmetic recolour/ornament sets count for the base pieces (TzKal armour group)
        IMPLIES.put("radiant oathplate helm", new String[]{"oathplate helm"});
        IMPLIES.put("radiant oathplate chest", new String[]{"oathplate chest"});
        IMPLIES.put("radiant oathplate legs", new String[]{"oathplate legs"});
        IMPLIES.put("sanguine torva full helm", new String[]{"torva full helm"});
        IMPLIES.put("sanguine torva platebody", new String[]{"torva platebody"});
        IMPLIES.put("sanguine torva platelegs", new String[]{"torva platelegs"});
        IMPLIES.put("twisted ancestral hat", new String[]{"ancestral hat"});
        IMPLIES.put("twisted ancestral robe top", new String[]{"ancestral robe top"});
        IMPLIES.put("twisted ancestral robe bottom", new String[]{"ancestral robe bottom"});
        // Zulrah: serpentine helm + mutagen helms imply the Serpentine visage; blowpipe implies the fang
        IMPLIES.put("serpentine helm", new String[]{"serpentine visage"});
        IMPLIES.put("tanzanite helm", new String[]{"serpentine helm"});
        IMPLIES.put("magma helm", new String[]{"serpentine helm"});
        IMPLIES.put("toxic blowpipe", new String[]{"tanzanite fang"});
        IMPLIES.put("blazing blowpipe", new String[]{"toxic blowpipe"}); // ornamented blowpipe (chains to tanzanite fang)
        // Occult necklace ornament kit → base (Dragon sword unique)
        IMPLIES.put("occult necklace (or)", new String[]{"occult necklace"});
        // Avernic defender CA-hilt variants count for the base defender. Ghommal's hilt (CA reward)
        // applied to it renames the item "Ghommal's avernic defender <tier>" (5 = Master, 6 = GM).
        IMPLIES.put("ghommal's avernic defender 5", new String[]{"avernic defender"});
        IMPLIES.put("ghommal's avernic defender 6", new String[]{"avernic defender"});
        IMPLIES.put("avernic defender (l)", new String[]{"avernic defender"});
        IMPLIES.put("avernic defender (or)", new String[]{"avernic defender"});
        // Dragon defender already covered by avernic defender above.
    }

    /** Add every item implied by what's owned (transitively). Mutates the set. */
    public static void expandOwned(Set<String> owned)
    {
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (String item : new ArrayList<>(owned))
            {
                String[] imp = IMPLIES.get(item);
                if (imp == null) continue;
                for (String i : imp) if (owned.add(i.toLowerCase())) changed = true;
            }
        }
    }

    // ── Rank definitions — the full Solus ladder ──
    // Sourced from solusosrs.com/ranking-system + the user's filled-in item pools. Item names are
    // best-effort in-game names; lines marked VERIFY need confirming. Achieve gates are 6 of 9
    // (user-confirmed, not the website's 5/9). Tiering via `requires` (resolved by evaluateAll).
    // Every check kind evaluates live: skills/total/XP/items in-game; diaries via varbits; boss KC
    // via WiseOldMan (through the clan API); CA tiers via varbits and named CA tasks via the synced
    // completion list (members must have opened their CA list once for task data to exist).

    public static final List<Rank> RANKS = new ArrayList<>();

    private static Check moonSet()
    {
        return Check.any("Complete one Perilous Moon set", 1,
            Check.all("Blood Moon set", Check.item("Blood moon helm"), Check.item("Blood moon chestplate"), Check.item("Blood moon tassets")),
            Check.all("Blue Moon set", Check.item("Blue moon helm"), Check.item("Blue moon chestplate"), Check.item("Blue moon tassets")),
            Check.all("Eclipse Moon set", Check.item("Eclipse moon helm"), Check.item("Eclipse moon chestplate"), Check.item("Eclipse moon tassets")));
    }

    private static Check fullVoid()
    {
        return Check.all("Full Void", Check.items("Void helm", 1, "Void melee helm", "Void mage helm", "Void ranger helm"),
            Check.item("Void knight top"), Check.item("Void knight robe"), Check.item("Void knight gloves"));
    }

    private static Check dragonTools(String label, int n)
    {
        return Check.items(label, n, "Dragon pickaxe", "Dragon axe", "Dragon harpoon");
    }

    private static Check zenyte(String label, int n)
    {
        return Check.items(label, n, "Amulet of torture", "Necklace of anguish", "Tormented bracelet", "Ring of suffering");
    }

    // "Bigger and Badder" = the superior-slayer unlock. No clean varbit is exposed, so we accept any
    // superior-monster unique clog slot as proof you unlocked (and fought) superiors. NOTE: this is
    // slightly stricter than the raw unlock (needs a superior UNIQUE drop); swap to a varbit if found.
    private static Check biggerAndBadder()
    {
        return Check.clogSlot("Bigger and Badder (superior slayer)",
            "Imbued heart", "Eternal gem", "Dust battlestaff", "Mist battlestaff");
    }

    // "1 of 2 Titan prayer scrolls" (Royal Titans) — own a scroll OR have the prayer unlocked.
    private static Check titanScrolls()
    {
        return Check.any("1 of 2 Titan prayer scrolls", 1,
            Check.unlock("Deadeye unlocked", "deadeye"), Check.item("Deadeye prayer scroll"),
            Check.unlock("Mystic Vigour unlocked", "mystic vigour"), Check.item("Mystic vigour prayer scroll"));
    }

    private static Check endGameMeleeHelm()
    {
        return Check.items("Any end-game melee helm", 1,
            "Torva full helm", "Neitiznot faceguard", "Justiciar faceguard", "Oathplate helm");
    }

    // A Combat Achievement TIER requirement, met by the tier varbit OR by owning a Ghommal's hilt of
    // that tier or higher (the CA reward: hilt 2 = Medium, 3 = Hard, 4 = Elite, 5 = Master, 6 = GM).
    // The hilt is a bank-checkable proof when CA data has not been synced this session.
    private static Check caTierOrHilt(String tier, String... hilts)
    {
        java.util.List<Check> opts = new java.util.ArrayList<>();
        opts.add(Check.caTier(tier));
        for (String h : hilts) opts.add(Check.item(h));
        return Check.any(tier + " Combat Achievements", 1, opts.toArray(new Check[0]));
    }

    static
    {
        // ===================== PvM PATH (Sword) =====================

        // — Adamant Sword — Pre-mid-game PvM (no prerequisite).
        Group amSwordUniques = Group.of("Obtain any 9 of 12 early-game uniques", 9,
            Check.item("Fire cape"),
            Check.itemsPrefix("Imbued god cape (MA2)", 1, "Imbued saradomin cape", "Imbued guthix cape", "Imbued zamorak cape"),
            Check.item("Dragon defender"),
            Check.item("Berserker ring (i)"),
            Check.item("Book of the dead"),
            Check.item("Ava's accumulator"),
            Check.item("Rune pouch"),
            Check.item("Zombie axe"),
            Check.item("Twinflame staff"),
            Check.item("Arclight"),
            Check.item("Sulphur blades"),
            Check.item("Glacial temotli"));
        Group amSwordPrayer = Group.of("70 Prayer", 1, Check.skill("70 Prayer", "Prayer", 70));
        Group amSwordMedCa = Group.of("Medium Combat Achievements", 1,
            caTierOrHilt("Medium", "Ghommal's hilt 2", "Ghommal's hilt 3", "Ghommal's hilt 4", "Ghommal's hilt 5", "Ghommal's hilt 6"));
        Group amSwordBigger = Group.of("Bigger and Badder (superior slayer)", 1, biggerAndBadder());
        Group amSwordAchieve = Group.of("Achieve 5 of 9", 5,
            Check.items("Black mask", 1, "Black mask", "Black mask (i)"),
            Check.item("Barrows gloves"),
            moonSet(),
            Check.item("Fighter torso"),
            fullVoid(),
            Check.items("Mix-n-Match Barrows", 1, "Dharok's helm", "Ahrim's hood", "Karil's coif", "Guthan's helm", "Torag's helm", "Verac's helm"),
            // Pegasian boots (and pegasian-containing Avernic treads via IMPLIES) surpass Ranger boots,
            // so they satisfy this too; primordial/eternal-only treads correctly do not (not ranged).
            Check.items("Ranger boots / Spiked manacles / Pegasian", 1, "Ranger boots", "Spiked manacles", "Pegasian boots"),
            Check.combat(100),
            titanScrolls());
        RANKS.add(new Rank("adamant_sword", "Adamant Sword", "PvM", "Pre-mid-game PvM",
            new ArrayList<>(), Arrays.asList(amSwordUniques, amSwordPrayer, amSwordMedCa, amSwordBigger, amSwordAchieve)));

        // — Rune Sword — Mid-game PvM (requires Adamant Sword).
        Group rnSwordUniques = Group.of("Obtain any 9 of 12 mid-game uniques", 9,
            Check.item("Fire cape"),
            Check.itemsPrefix("Imbued god cape (MA2)", 1, "Imbued saradomin cape", "Imbued guthix cape", "Imbued zamorak cape"),
            Check.item("Dragon defender"),
            Check.item("Berserker ring (i)"),
            Check.item("Book of the dead"),
            Check.item("Divine rune pouch"),
            Check.item("Crystal halberd"),
            Check.item("Trident of the seas"),
            Check.item("Abyssal whip"),
            Check.any("Deadeye prayer (Royal Titans)", 1,
                Check.unlock("Deadeye unlocked", "deadeye"), Check.item("Deadeye prayer scroll")),
            Check.any("Mystic Vigour prayer (Royal Titans)", 1,
                Check.unlock("Mystic Vigour unlocked", "mystic vigour"), Check.item("Mystic vigour prayer scroll")),
            Check.item("Ava's assembler"));
        Group rnSwordZenyte = Group.of("Obtain any 1 of 4 Zenyte jewellery", 1, zenyte("Zenyte jewellery", 1));
        Group rnSword78Herb = Group.of("78 Herblore", 1, Check.skill("78 Herblore", "Herblore", 78));
        Group rnSwordHardCa = Group.of("Hard Combat Achievements", 1,
            caTierOrHilt("Hard", "Ghommal's hilt 3", "Ghommal's hilt 4", "Ghommal's hilt 5", "Ghommal's hilt 6"));
        Group rnSwordAchieve = Group.of("Achieve 5 of 9", 5,
            Check.any("Zulrah — 512 KC or 2/3 uniques", 1, Check.kc("Zulrah 512 KC", 512, "zulrah"),
                Check.items("2 Zulrah uniques", 2, "Tanzanite fang", "Magic fang", "Serpentine visage")),
            Check.any("God Wars — 508 KC or 4 uniques", 1, Check.kc("God Wars 508 KC", 508, "god_wars_dungeon"),
                Check.items("4 GWD uniques", 4, "Bandos chestplate", "Bandos tassets", "Bandos boots",
                    "Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt", "Saradomin sword",
                    "Armadyl crossbow", "Staff of the dead", "Zamorakian spear", "Saradomin's light")),
            Check.any("Cerberus — 512 KC or 2 crystals", 1, Check.kc("Cerberus 512 KC", 512, "cerberus"),
                Check.items("2 Cerberus crystals", 2, "Primordial crystal", "Pegasian crystal", "Eternal crystal")),
            Check.any("Abyssal Sire — 620 KC or Bludgeon", 1, Check.kc("Abyssal Sire 620 KC", 620, "abyssal_sire"),
                Check.items("Abyssal bludgeon", 1, "Abyssal bludgeon", "Bludgeon axon", "Bludgeon claw", "Bludgeon spine")),
            Check.any("Tormented Demons — 500 KC or Emberlight", 1, Check.kc("Tormented Demons 500 KC", 500, "tormented_demons"),
                Check.items("Emberlight", 1, "Emberlight", "Burning claws", "Smouldering gland")),
            endGameMeleeHelm(),
            Check.kc("100 combined raid KC", 100, "raids_combined"),
            Check.any("Corrupted Gauntlet: 100 KC or Enhanced seed", 1,
                Check.kc("Corrupted Gauntlet 100 KC", 100, "the_corrupted_gauntlet"),
                Check.item("Enhanced crystal weapon seed")),
            Check.items("Voidwaker — full or any piece", 1,
                "Voidwaker", "Voidwaker blade", "Voidwaker gem", "Voidwaker hilt"));
        RANKS.add(new Rank("rune_sword", "Rune Sword", "PvM", "Mid-game PvM",
            req("adamant_sword"), Arrays.asList(rnSwordUniques, rnSwordZenyte, rnSword78Herb, rnSwordHardCa, rnSwordAchieve)));

        // — Dragon Sword — Pre-late-game PvM (requires Rune Sword). ALL late uniques + 3/4 Zenyte.
        Group drSwordUniques = Group.of("Obtain ALL 17 late-game uniques", 17,
            Check.item("Infernal cape"),
            Check.itemsPrefix("Imbued god cape (MA2)", 1, "Imbued saradomin cape", "Imbued guthix cape", "Imbued zamorak cape"),
            Check.item("Avernic defender"),
            Check.item("Berserker ring (i)"),
            Check.item("Book of the dead"),
            Check.item("Dizana's quiver"),
            Check.item("Divine rune pouch"),
            Check.item("Crystal halberd"),
            Check.item("Trident of the swamp"),
            Check.item("Noxious halberd"),
            Check.any("Augury (Arcane scroll)", 1,
                Check.unlock("Augury unlocked", "augury"), Check.item("Arcane prayer scroll")),
            Check.any("Rigour (Dexterous scroll)", 1,
                Check.unlock("Rigour unlocked", "rigour"), Check.item("Dexterous prayer scroll")),
            Check.item("Toxic blowpipe"),
            Check.item("Osmumten's fang"),
            Check.item("Occult necklace"),
            Check.item("Emberlight"),
            Check.clogSlot("Rite of vile transference", "Rite of vile transference"));
        Group drSwordZenyte = Group.of("Obtain any 3 of 4 Zenyte jewellery", 1, zenyte("3 of 4 Zenyte jewellery", 3));
        Group drSwordEliteCa = Group.of("Elite Combat Achievements", 1,
            caTierOrHilt("Elite", "Ghommal's hilt 4", "Ghommal's hilt 5", "Ghommal's hilt 6"));
        Group drSwordDamageSpec = Group.of("Damage special weapon (1 of 3)", 1,
            Check.items("Zaryte crossbow / Dragon claws / Voidwaker", 1, "Zaryte crossbow", "Dragon claws", "Voidwaker"));
        Group drSwordDebuffSpec = Group.of("Debuff special weapon (1 of 3)", 1,
            Check.items("Bandos godsword / Dragon warhammer / Elder maul", 1, "Bandos godsword", "Dragon warhammer", "Elder maul"));
        Group drSwordAchieve = Group.of("Achieve 5 of 8", 5,
            Check.any("Corrupted Gauntlet — 400 KC or weapon+armour seeds", 1, Check.kc("Corrupted Gauntlet 400 KC", 400, "the_corrupted_gauntlet"),
                // Spec is enhanced weapon seed + 5 armour seeds; ownership is name-based so the
                // armour-seed COUNT can't be verified — presence of both is the closest readable check.
                Check.all("Enhanced weapon seed + armour seed", Check.item("Enhanced crystal weapon seed"), Check.item("Crystal armour seed"))),
            Check.any("God Wars — 1,014 KC or 8 uniques", 1, Check.kc("God Wars 1,014 KC", 1014, "god_wars_dungeon"),
                Check.items("8 GWD uniques", 8, "Bandos chestplate", "Bandos tassets", "Bandos boots",
                    "Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt", "Saradomin sword",
                    "Armadyl crossbow", "Staff of the dead", "Zamorakian spear", "Saradomin's light", "Zamorakian hasta")),
            Check.any("Araxxor: 600 KC or Araxyte fang", 1, Check.kc("Araxxor 600 KC", 600, "araxxor"),
                Check.item("Araxyte fang")),
            Check.any("Alchemical Hydra — 1,001 KC or Claw + Leather", 1, Check.kc("Alchemical Hydra 1,001 KC", 1001, "alchemical_hydra"),
                Check.all("Hydra uniques", Check.item("Hydra's claw"), Check.item("Hydra leather"))),
            Check.items("Any DT2 Vestige", 1, "Ultor vestige", "Magus vestige", "Bellator vestige", "Venator vestige",
                "Ultor ring", "Magus ring", "Bellator ring", "Venator ring"),
            Check.kc("Chambers of Xeric — 200 KC", 200, "cox_total"),
            Check.kc("Theatre of Blood — 200 KC", 200, "tob_total"),
            Check.kc("Tombs of Amascut — 200 KC", 200, "toa_total"));
        RANKS.add(new Rank("dragon_sword", "Dragon Sword", "PvM", "Pre-late-game PvM",
            req("rune_sword"), Arrays.asList(drSwordUniques, drSwordZenyte, drSwordEliteCa, drSwordDamageSpec, drSwordDebuffSpec, drSwordAchieve)));

        // — TzKal — Late-game PvM, approaching Grandmaster (requires Dragon Sword).
        Group tzUniques = Group.of("Obtain ALL TzKal uniques", 14,
            Check.item("Osmumten's fang (or)"),
            Check.item("Lightbearer"),
            Check.item("Ultor ring"),
            Check.item("Ferocious gloves"),
            Check.item("Bandos godsword"),
            Check.item("Dragon pickaxe"),
            Check.item("Amulet of rancour"),
            Check.item("Avernic treads"),
            Check.itemPrefix("Void ranger helm"),
            Check.itemPrefix("Elite void top"),
            Check.itemPrefix("Elite void robe"),
            Check.itemPrefix("Void knight gloves"),
            Check.item("Keris partisan of the sun"),
            Check.item("Slepey tablet"));
        Group tzArmour = Group.of("Obtain any 5 pieces of Masori / Ancestral / Torva / Oathplate", 5,
            Check.item("Masori mask"), Check.item("Masori body"), Check.item("Masori chaps"),
            Check.item("Ancestral hat"), Check.item("Ancestral robe top"), Check.item("Ancestral robe bottom"),
            Check.item("Torva full helm"), Check.item("Torva platebody"), Check.item("Torva platelegs"),
            Check.item("Oathplate helm"), Check.item("Oathplate chest"), Check.item("Oathplate legs"));
        Group tzExtras = Group.of("Approaching Grandmaster - all of", 12,
            Check.any("TzKal-Zuk — 5 KC or 4/10 Zuk GM tasks", 1, Check.kc("TzKal-Zuk 5 KC", 5, "tzkalzuk"),
                Check.any("4 of 10 Inferno GM tasks", 4,
                    Check.caTask("Budget Setup"), Check.caTask("Facing Jad Head-on II"),
                    Check.caTask("Inferno Grandmaster"), Check.caTask("Inferno Speed-Runner"),
                    Check.caTask("Jad? What Are You Doing Here?"), Check.caTask("Nibbler Chaser"),
                    Check.caTask("No Luck Required"), Check.caTask("Playing with Jads"),
                    Check.caTask("The Floor Is Lava"), Check.caTask("Wasn't Even Close"))),
            Check.any("Fortis Colosseum — 10 KC or Perfect Footwork", 1, Check.kc("Fortis Colosseum 10 KC", 10, "sol_heredit"), Check.caTask("Perfect Footwork")),
            // Owning Torva/an Awakener's orb proves nothing (Torva comes from Nex; the orb just
            // STARTS the fight) — the awakened-kill GM tasks are the real proof of a kill.
            Check.any("Defeat an Awakened Boss", 1,
                Check.caTask("Duke Sucellus Sleeper"), Check.caTask("Leviathan Sleeper"),
                Check.caTask("Vardorvis Sleeper"), Check.caTask("Whispered")),
            Check.caTask("Perfect Phosani's Nightmare"),
            Check.items("Damage special weapon (1 of 3)", 1, "Zaryte crossbow", "Dragon claws", "Voidwaker"),
            Check.kc("Chambers of Xeric — 200 KC", 200, "cox_total"),
            Check.kc("Theatre of Blood — 200 KC", 200, "tob_total"),
            Check.kc("Tombs of Amascut — 200 KC", 200, "toa_total"),
            Check.caTask("It's Dark Down Here"),
            Check.items("Purifying sigil (Yama)", 1, "Purifying sigil",
                "Purifying sigil (top)", "Purifying sigil (middle)", "Purifying sigil (bottom)",
                "Purifying sigil (left)", "Purifying sigil (right)",
                "Radiant oathplate helm", "Radiant oathplate chest", "Radiant oathplate legs"),
            Check.any("Stat restoration (Ornate pool or Elite diary)", 1,
                Check.unlock("Ornate pool", "ornate pool"), Check.diary("Elite", 1)),
            caTierOrHilt("Master", "Ghommal's hilt 5", "Ghommal's hilt 6"));
        RANKS.add(new Rank("tzkal", "TzKal", "PvM", "Late-game PvM (approaching GM)",
            req("dragon_sword"), Arrays.asList(tzUniques, tzArmour, tzExtras)));

        // ===================== SKILLER PATH (Pickaxe) =====================
        Check[] skiller8 = {
            Check.item("Coal bag"), Check.item("Fish barrel"), Check.item("Bottomless compost bucket"),
            Check.item("Herb sack"), Check.item("Rune pouch"), Check.item("Plank sack"),
            Check.item("Log basket"), Check.item("Seed box"),
        };
        Check[] skiller12 = {
            Check.item("Coal bag"), Check.item("Fish barrel"), Check.item("Bottomless compost bucket"),
            Check.item("Herb sack"), Check.item("Rune pouch"), Check.item("Plank sack"),
            Check.item("Log basket"), Check.item("Seed box"),
            Check.item("Colossal pouch"), Check.item("Smouldering stone"),
            Check.item("Crystal tool seed"), Check.item("Pharaoh's sceptre"),
        };

        // — Adamant Pick — Early-game skiller.
        Group amPickReq = Group.of("1,800 total level + 6 Medium Diaries", 2,
            Check.total(1800), Check.diary("Medium", 6));
        Group amPickItems = Group.of("Obtain any 2 of 8 early-game skiller items", 2, skiller8);
        Group amPickAchieve = Group.of("Achieve 5 of 9 skills", 5,
            Check.skill("70 Construction", "Construction", 70), Check.skill("75 Crafting", "Crafting", 75),
            Check.skill("72 Herblore", "Herblore", 72), Check.skill("74 Farming", "Farming", 74),
            Check.skill("74 Mining", "Mining", 74), Check.skill("74 Fletching", "Fletching", 74),
            Check.skill("70 Prayer", "Prayer", 70), Check.skill("80 Cooking", "Cooking", 80),
            Check.skill("80 Hunter", "Hunter", 80));
        RANKS.add(new Rank("adamant_pick", "Adamant Pick", "Skiller", "Early-game skiller",
            new ArrayList<>(), Arrays.asList(amPickReq, amPickItems, amPickAchieve)));

        // — Rune Pick — Mid-game skiller (requires Adamant Pick).
        Group rnPickReq = Group.of("2,050 total + 6 Hard Diaries + 1 of 3 dragon tools", 3,
            Check.total(2050), Check.diary("Hard", 6), dragonTools("1 of 3 dragon tools", 1));
        Group rnPickItems = Group.of("Obtain any 4 of 8 mid-game skiller items", 4, skiller8);
        Group rnPickAchieve = Group.of("Achieve 5 of 9 skills", 5,
            Check.skill("82 Construction", "Construction", 82), Check.skill("85 Crafting", "Crafting", 85),
            Check.skill("81 Herblore", "Herblore", 81), Check.skill("85 Farming", "Farming", 85),
            Check.skill("82 Fishing", "Fishing", 82), Check.skill("85 Agility", "Agility", 85),
            Check.skill("87 Slayer", "Slayer", 87), Check.skill("77 Runecraft", "Runecraft", 77),
            Check.skill("77 Smithing", "Smithing", 77));
        RANKS.add(new Rank("rune_pick", "Rune Pick", "Skiller", "Mid-game skiller",
            req("adamant_pick"), Arrays.asList(rnPickReq, rnPickItems, rnPickAchieve)));

        // — Dragon Pick — Late-game skiller (requires Rune Pick).
        Group drPickReq = Group.of("2,300 total + 6 Elite Diaries + 2 of 3 dragon tools", 3,
            Check.total(2300), Check.diary("Elite", 6), dragonTools("2 of 3 dragon tools", 2));
        Group drPickItems = Group.of("Obtain any 8 of 12 late-game skiller items", 8, skiller12);
        Group drPickAchieve = Group.of("Achieve 5 of 9 skills", 5,
            Check.skill("90 Construction", "Construction", 90), Check.skill("93 Crafting", "Crafting", 93),
            Check.skill("90 Herblore", "Herblore", 90), Check.skill("94 Thieving", "Thieving", 94),
            Check.skill("89 Mining", "Mining", 89), Check.skill("92 Agility", "Agility", 92),
            Check.skill("95 Slayer", "Slayer", 95), Check.skill("95 Runecraft", "Runecraft", 95),
            Check.skill("91 Hunter", "Hunter", 91));
        RANKS.add(new Rank("dragon_pick", "Dragon Pick", "Skiller", "Late-game skiller",
            req("rune_pick"), Arrays.asList(drPickReq, drPickItems, drPickAchieve)));

        // — Maxed — Skiller completion.
        // 2,376 = 24 skills x 99 since Sailing (2026). Bump again if Jagex adds another skill.
        Group maxedReq = Group.of("2,376 total level + All Achievement Diaries", 2,
            Check.total(2376), Check.diary("Elite", 12));
        RANKS.add(new Rank("maxed", "Maxed", "Skiller", "Skiller completion",
            new ArrayList<>(), Arrays.asList(maxedReq)));

        // ===================== COMPLETIONIST =====================
        RANKS.add(new Rank("adamant_comp", "Adamant Completionist", "Completionist", "Both Adamant ranks",
            req("adamant_sword", "adamant_pick"), new ArrayList<>()));
        RANKS.add(new Rank("rune_comp", "Rune Completionist", "Completionist", "Both Rune ranks",
            req("rune_sword", "rune_pick"), new ArrayList<>()));
        RANKS.add(new Rank("dragon_comp", "Dragon Completionist", "Completionist", "Both Dragon ranks",
            req("dragon_sword", "dragon_pick"), new ArrayList<>()));

        // ===================== BEAST / META =====================
        RANKS.add(new Rank("beast", "Beast", "Beast", "Conquered TzKal and Maxed",
            req("tzkal", "maxed"), new ArrayList<>()));
        RANKS.add(new Rank("gm_beast", "GM Beast", "Beast", "All Grandmaster Combat Achievements",
            new ArrayList<>(), Arrays.asList(Group.of("All Grandmaster Combat Achievements", 1, Check.caTier("Grandmaster")))));
        RANKS.add(new Rank("xp_beast", "XP Beast", "Beast", "1,000,000,000 total XP",
            new ArrayList<>(), Arrays.asList(Group.of("1B total XP", 1, Check.totalXp(1_000_000_000, "1,000,000,000 total XP")))));
        RANKS.add(new Rank("log_beast", "Log Beast", "Beast", "1,200 Collection Log slots",
            new ArrayList<>(), Arrays.asList(Group.of("1,200 Collection Log slots", 1, Check.clog(1200)))));

        // Heart of Solus — qualify for N Beast ranks (Beast / GM / XP / Log).
        Check[] beastRanks = {
            Check.rank("beast", "Beast"), Check.rank("gm_beast", "GM Beast"),
            Check.rank("xp_beast", "XP Beast"), Check.rank("log_beast", "Log Beast"),
        };
        RANKS.add(new Rank("heart_2", "Heart of Solus ♦♦", "Beast", "Qualify for 2 Beast ranks",
            new ArrayList<>(), Arrays.asList(Group.of("Qualify for any 2 Beast ranks", 2, beastRanks))));
        RANKS.add(new Rank("heart_3", "Heart of Solus ♦♦♦", "Beast", "Qualify for 3 Beast ranks",
            new ArrayList<>(), Arrays.asList(Group.of("Qualify for any 3 Beast ranks", 3, beastRanks))));
        RANKS.add(new Rank("heart_4", "Heart of Solus ♦♦♦♦", "Beast", "Qualify for all 4 Beast ranks",
            new ArrayList<>(), Arrays.asList(Group.of("Qualify for all 4 Beast ranks", 4, beastRanks))));
    }

    private static List<String> req(String... ids) { return Arrays.asList(ids); }
}
