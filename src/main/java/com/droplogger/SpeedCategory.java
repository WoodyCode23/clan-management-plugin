package com.droplogger;

/**
 * Maps game activities + team sizes to sheet rows in "3. Hiscore Tracking".
 * Each entry corresponds to one subcategory row on the hiscores sheet.
 * Titles come from column N of the sheet via the API.
 */
public enum SpeedCategory
{
    // Chambers of Xeric (rows 10-14)
    COX_SOLO(10, "cox", "Chambers of Xeric", 1, 1, false),
    COX_TRIO(11, "cox", "Chambers of Xeric", 3, 3, true),
    COX_CM_SOLO(12, "cox", "CM Chambers of Xeric", 1, 1, false),
    COX_CM_TRIO(13, "cox", "CM Chambers of Xeric", 3, 3, true),
    COX_CM_5MAN(14, "cox", "CM Chambers of Xeric", 5, 5, true),

    // Theatre of Blood (rows 15-19)
    TOB_DUO(15, "tob", "Theatre of Blood", 2, 2, true),
    TOB_TRIO(16, "tob", "Theatre of Blood", 3, 3, true),
    TOB_4MAN(17, "tob", "Theatre of Blood", 4, 4, true),
    TOB_5MAN(18, "tob", "Theatre of Blood", 5, 5, true),
    TOB_HM_5MAN(19, "tob", "HM Theatre of Blood", 5, 5, true),

    // Tombs of Amascut (rows 20-23)
    TOA_150_SOLO(20, "toa", "Tombs of Amascut", 1, 1, false),
    TOA_150_DUO(21, "toa", "Tombs of Amascut", 2, 2, true),
    TOA_300_SOLO(22, "toa", "Tombs of Amascut", 1, 1, false),
    TOA_300_DUO(23, "toa", "Tombs of Amascut", 2, 2, true),

    // Solo wave content (single-category groups)
    JAD(24, "jad", "TzTok-Jad", 1, 1, false),
    ZUK(25, "zuk", "TzKal-Zuk", 1, 1, false),
    COLO(26, "colo", "Sol Heredit", 1, 1, false),

    // Gauntlet (rows 27-28)
    GAUNTLET(27, "gaunt", "The Gauntlet", 1, 1, false),
    CORRUPTED_GAUNTLET(28, "gaunt", "The Corrupted Gauntlet", 1, 1, false),

    // Nightmare (rows 29-30)
    NIGHTMARE(29, "nightmare", "Nightmare", 2, 100, true),
    PHOSANIS(30, "nightmare", "Phosanis Nightmare", 1, 1, false),

    // DT2 bosses (rows 31-34)
    DUKE(31, "dt2", "Awakened Duke Sucellus", 1, 1, false),
    LEVIATHAN(32, "dt2", "Awakened Leviathan", 1, 1, false),
    VARDORVIS(33, "dt2", "Awakened Vardorvis", 1, 1, false),
    WHISPERER(34, "dt2", "Awakened Whisperer", 1, 1, false),

    // Barbarian Assault (row 36)
    BA(36, "ba", "Barbarian Assault", 5, 5, true),

    // Nex (rows 37-38)
    NEX_DUO(37, "nex", "Nex", 2, 2, true),
    NEX_TRIO(38, "nex", "Nex", 3, 3, true),

    // TzHaar-Ket-Rak's Challenges (rows 39-41)
    RAKS_1ST(39, "raks", "First Challenge", 1, 1, false),
    RAKS_3RD(40, "raks", "Third Challenge", 1, 1, false),
    RAKS_5TH(41, "raks", "Fifth Challenge", 1, 1, false),

    // Royal Titans (rows 42-43)
    TITANS_SOLO(42, "titans", "Royal Titans", 1, 1, false),
    TITANS_DUO(43, "titans", "Royal Titans", 2, 2, true),

    // Yama (rows 44-45)
    YAMA_SOLO(44, "yama", "Yama", 1, 1, false),
    YAMA_DUO(45, "yama", "Yama", 2, 2, true);

    private final int sheetRow;
    private final String group;
    private final String activityName;
    private final int minPlayers;
    private final int maxPlayers;
    private final boolean groupContent;

    SpeedCategory(int sheetRow, String group, String activityName,
                  int minPlayers, int maxPlayers, boolean groupContent)
    {
        this.sheetRow = sheetRow;
        this.group = group;
        this.activityName = activityName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.groupContent = groupContent;
    }

    public int getSheetRow() { return sheetRow; }
    public String getGroup() { return group; }
    public String getActivityName() { return activityName; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public boolean isGroupContent() { return groupContent; }

    /**
     * Find the matching category for an activity and party size.
     */
    public static SpeedCategory find(String group, int partySize)
    {
        SpeedCategory best = null;
        for (SpeedCategory cat : values())
        {
            if (!cat.group.equals(group))
            {
                continue;
            }
            if (partySize >= cat.minPlayers && partySize <= cat.maxPlayers)
            {
                // Prefer the most specific match (smallest range)
                if (best == null || (cat.maxPlayers - cat.minPlayers) < (best.maxPlayers - best.minPlayers))
                {
                    best = cat;
                }
            }
        }
        return best;
    }
}
