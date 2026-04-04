package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BingoStandings
{
    private final List<BingoTeam> teamStandings;
    private final List<PlayerStanding> individualStandings;

    @Getter
    @AllArgsConstructor
    public static class PlayerStanding
    {
        private final int rank;
        private final String rsn;
        private final String team;
        private final double points;
    }
}
