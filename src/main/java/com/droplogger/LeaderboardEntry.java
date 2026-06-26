package com.droplogger;

import javax.swing.ImageIcon;

/**
 * A single row in a clan leaderboard (XP totals or event scores). Populated entirely
 * from the platform API ({@link PlatformApiService}); no third-party data source.
 */
public class LeaderboardEntry
{
    public final int rank;
    public final String username;
    public final String role;
    public final long experience;
    public final int level;
    public final long gained;
    public ImageIcon roleIcon;

    public LeaderboardEntry(int rank, String username, String role, long experience, int level, long gained)
    {
        this.rank = rank;
        this.username = username;
        this.role = role;
        this.experience = experience;
        this.level = level;
        this.gained = gained;
    }
}
