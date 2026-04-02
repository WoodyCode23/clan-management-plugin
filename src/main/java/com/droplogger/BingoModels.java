package com.droplogger;

import java.awt.Color;
import java.util.List;

public class BingoModels
{
    public static class BoardData
    {
        private final List<Tile> tiles;
        private final List<Team> teams;
        private final List<BountyResult> bountyResults;
        private final List<TeamDrop> teamDrops;
        private final double teamTotalPoints;
        private final List<String> announcements;

        public BoardData(List<Tile> tiles, List<Team> teams, List<BountyResult> bountyResults,
                         List<TeamDrop> teamDrops, double teamTotalPoints, List<String> announcements)
        {
            this.tiles = tiles;
            this.teams = teams;
            this.bountyResults = bountyResults;
            this.teamDrops = teamDrops;
            this.teamTotalPoints = teamTotalPoints;
            this.announcements = announcements;
        }

        public List<Tile> getTiles() { return tiles; }
        public List<Team> getTeams() { return teams; }
        public List<BountyResult> getBountyResults() { return bountyResults; }
        public List<TeamDrop> getTeamDrops() { return teamDrops; }
        public double getTeamTotalPoints() { return teamTotalPoints; }
        public List<String> getAnnouncements() { return announcements; }
    }

    public static class Tile
    {
        private final int row;
        private final int col;
        private final String task;
        private final double completionPercent;
        private final double points;
        private final double bingoThreshold;
        private final double maxThreshold;

        public Tile(int row, int col, String task, double completionPercent,
                    double points, double bingoThreshold, double maxThreshold)
        {
            this.row = row;
            this.col = col;
            this.task = task;
            this.completionPercent = completionPercent;
            this.points = points;
            this.bingoThreshold = bingoThreshold;
            this.maxThreshold = maxThreshold;
        }

        public int getRow() { return row; }
        public int getCol() { return col; }
        public String getTask() { return task; }
        public double getCompletionPercent() { return completionPercent; }
        public double getPoints() { return points; }
        public double getBingoThreshold() { return bingoThreshold; }
        public double getMaxThreshold() { return maxThreshold; }

        public Color getTileColor()
        {
            if (completionPercent >= 100)
            {
                return Color.BLACK;
            }
            else if (completionPercent >= 50)
            {
                return new Color(76, 175, 80); // Green
            }
            else if (completionPercent >= 5)
            {
                return new Color(255, 235, 59); // Yellow
            }
            else
            {
                return Color.GRAY;
            }
        }

        public Color getTextColor()
        {
            if (completionPercent >= 100 || completionPercent >= 50)
            {
                return Color.WHITE;
            }
            return Color.BLACK;
        }
    }

    public static class Team
    {
        private final String code;
        private final String name;
        private final double points;
        private final int rank;

        public Team(String code, String name, double points, int rank)
        {
            this.code = code;
            this.name = name;
            this.points = points;
            this.rank = rank;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public double getPoints() { return points; }
        public int getRank() { return rank; }
    }

    public static class TeamDrop
    {
        private final String rsn;
        private final String dropName;
        private final String date;
        private final double points;
        private final String tileCode;
        private final String tileName;

        public TeamDrop(String rsn, String dropName, String date, double points,
                        String tileCode, String tileName)
        {
            this.rsn = rsn;
            this.dropName = dropName;
            this.date = date;
            this.points = points;
            this.tileCode = tileCode;
            this.tileName = tileName;
        }

        public String getRsn() { return rsn; }
        public String getDropName() { return dropName; }
        public String getDate() { return date; }
        public double getPoints() { return points; }
        public String getTileCode() { return tileCode; }
        public String getTileName() { return tileName; }
    }

    public static class BountyResult
    {
        private final int bountyNumber;
        private final String description;
        private final String winner;

        public BountyResult(int bountyNumber, String description, String winner)
        {
            this.bountyNumber = bountyNumber;
            this.description = description;
            this.winner = winner;
        }

        public int getBountyNumber() { return bountyNumber; }
        public String getDescription() { return description; }
        public String getWinner() { return winner; }
    }

    public static class WhitelistItem
    {
        private final String item;
        private final String tileCode;

        public WhitelistItem(String item, String tileCode)
        {
            this.item = item;
            this.tileCode = tileCode;
        }

        public String getItem() { return item; }
        public String getTileCode() { return tileCode; }
    }

    public static class RosterPlayer
    {
        private final String rsn;
        private final int dropCount;
        private final double totalPoints;

        public RosterPlayer(String rsn, int dropCount, double totalPoints)
        {
            this.rsn = rsn;
            this.dropCount = dropCount;
            this.totalPoints = totalPoints;
        }

        public String getRsn() { return rsn; }
        public int getDropCount() { return dropCount; }
        public double getTotalPoints() { return totalPoints; }
    }
}
