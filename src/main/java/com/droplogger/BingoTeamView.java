package com.droplogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure helpers for the plugin's bingo team-view card: turning a {@link PlatformApiService.BingoCard}
 * into the things the panel renders (which team is "yours", one team's board as
 * List&lt;BingoTile&gt;, and the points/tiles/rank/gap status line). No Swing, no network, no game
 * state, so these are plain JUnit-testable, matching {@link BingoTiles}.
 */
public final class BingoTeamView
{
    private BingoTeamView()
    {
    }

    /** Same normalization as ClanManagementPlugin's private normalizeName, duplicated here so RSN
     *  matching stays testable without instantiating the (RuneLite-injected) plugin class. */
    public static String normalizeName(String s)
    {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').replaceAll(" +", " ").trim();
    }

    public static boolean isSamePlayer(String a, String b)
    {
        String na = normalizeName(a);
        return !na.isEmpty() && na.equals(normalizeName(b));
    }

    /**
     * The team whose roster contains localPlayerName (normalized match), or null when it is on no
     * team, the card has no teams, or localPlayerName is blank. Ties (a name on two rosters, which
     * should not happen server-side) resolve to the first team listed.
     */
    public static String findOwnTeamId(PlatformApiService.BingoCard card, String localPlayerName)
    {
        if (card == null || card.teams == null || localPlayerName == null) return null;
        String target = normalizeName(localPlayerName);
        if (target.isEmpty()) return null;
        for (PlatformApiService.BingoTeam team : card.teams)
        {
            if (team == null || team.members == null) continue;
            for (String member : team.members)
            {
                if (normalizeName(member).equals(target)) return team.teamId;
            }
        }
        return null;
    }

    /** This card's total tile count, from the board definition (0 when there is no board yet). */
    public static int totalTiles(PlatformApiService.BingoCard card)
    {
        if (card == null || card.board == null || card.board.tiles == null) return 0;
        return card.board.tiles.size();
    }

    /** The standings entry for one team, or null when standings haven't been computed yet / teamId
     *  doesn't match anything. */
    public static PlatformApiService.BingoStanding standingForTeam(PlatformApiService.BingoCard card, String teamId)
    {
        if (card == null || card.standings == null || teamId == null) return null;
        for (PlatformApiService.BingoStanding s : card.standings)
        {
            if (s != null && teamId.equals(s.teamId)) return s;
        }
        return null;
    }

    /** "1st", "2nd", "3rd", "4th", ... "11th"/"12th"/"13th" special-cased. rank <= 0 renders as-is. */
    public static String rankOrdinal(int rank)
    {
        if (rank <= 0) return String.valueOf(rank);
        int mod100 = rank % 100;
        if (mod100 >= 11 && mod100 <= 13) return rank + "th";
        switch (rank % 10)
        {
            case 1: return rank + "st";
            case 2: return rank + "nd";
            case 3: return rank + "rd";
            default: return rank + "th";
        }
    }

    /**
     * The points / tiles / rank / gap status line for one team, e.g.
     * "128 pts | 9/25 tiles | Rank 1 of 4 | Leading by 20 pts" (rank 1, a lead known) or
     * "108 pts | 8/25 tiles | Rank 2 of 4 | 20 pts behind 1st" (any other rank, a gap known).
     * Trailing clauses are omitted rather than guessed when the corresponding gap is null.
     */
    public static String formatTeamStatusLine(double points, int tilesComplete, int totalTiles, int rank, int teamCount,
        PlatformApiService.BingoGap gapToAbove, PlatformApiService.BingoGap leadOverBelow)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(formatNumber(points)).append(points == 1 ? " pt" : " pts");
        sb.append(" | ").append(tilesComplete).append("/").append(totalTiles).append(" tiles");
        if (rank > 0 && teamCount > 0)
        {
            sb.append(" | Rank ").append(rank).append(" of ").append(teamCount);
        }
        if (rank == 1)
        {
            if (leadOverBelow != null)
            {
                sb.append(" | Leading by ").append(formatNumber(leadOverBelow.points))
                  .append(leadOverBelow.points == 1 ? " pt" : " pts");
            }
        }
        else if (gapToAbove != null)
        {
            sb.append(" | ").append(formatNumber(gapToAbove.points)).append(gapToAbove.points == 1 ? " pt" : " pts")
              .append(" behind ").append(rank > 1 ? rankOrdinal(rank - 1) : "1st");
        }
        return sb.toString();
    }

    private static String formatNumber(double v)
    {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.1f", v);
    }

    /**
     * Resolve one static board tile plus that team's progress into the renderer's {@link BingoTile}.
     * Icon resolution order (matches the design doc's "Tiles tab Icon column"): the tile's icon
     * string first as a boss name (via {@link BingoTiles#bossSpriteId}), else as a bare item id, else
     * as an item name matched against the tile's own items, else the tile's first item, else a blank
     * icon (itemId 0, which BingoBoardPanel already renders as an iconless cell rather than throwing).
     */
    public static BingoTile resolveTile(PlatformApiService.BingoBoardTile t, PlatformApiService.BingoTileProgress progress)
    {
        double points = progress != null ? progress.points : 0;
        double threshold = t.threshold > 0 ? t.threshold : t.max;

        String icon = t.icon == null ? "" : t.icon.trim();
        if (!icon.isEmpty())
        {
            if (BingoTiles.bossSpriteId(icon) >= 0)
            {
                return BingoTile.boss(t.code, t.name, t.row, t.col, points, threshold, icon);
            }
            try
            {
                int id = Integer.parseInt(icon);
                return BingoTile.item(t.code, t.name, t.row, t.col, points, threshold, id);
            }
            catch (NumberFormatException notAnId)
            {
                if (t.items != null)
                {
                    for (PlatformApiService.BingoItem item : t.items)
                    {
                        if (item.itemName != null && item.itemName.equalsIgnoreCase(icon))
                        {
                            return BingoTile.item(t.code, t.name, t.row, t.col, points, threshold, item.itemId);
                        }
                    }
                }
            }
        }
        if (t.items != null && !t.items.isEmpty())
        {
            return BingoTile.item(t.code, t.name, t.row, t.col, points, threshold, t.items.get(0).itemId);
        }
        return BingoTile.item(t.code, t.name, t.row, t.col, points, threshold, 0);
    }

    /** The selected team's whole board as the renderer's List&lt;BingoTile&gt;. Missing progress for
     *  a tile (team hasn't touched it, or the team has no progress map at all) reads as 0 points. */
    public static List<BingoTile> toBoardTiles(PlatformApiService.BingoCard card, String teamId)
    {
        List<BingoTile> out = new ArrayList<>();
        if (card == null || card.board == null || card.board.tiles == null) return out;
        java.util.Map<String, PlatformApiService.BingoTileProgress> byTile =
            card.progress != null && teamId != null ? card.progress.get(teamId) : null;
        for (PlatformApiService.BingoBoardTile t : card.board.tiles)
        {
            if (t == null) continue;
            PlatformApiService.BingoTileProgress progress = byTile != null ? byTile.get(t.code) : null;
            out.add(resolveTile(t, progress));
        }
        return out;
    }
}
