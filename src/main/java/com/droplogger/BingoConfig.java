package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class BingoConfig
{
    private final int gridRows;
    private final int gridCols;
    private final String eventName;
    private final String startDate;
    private final String endDate;
    private final int hintMinutesBefore;
    private final List<BingoTile> tiles;
    private final List<BingoTeam> teams;
    private final List<BingoBounty> bounties;
    private final Map<String, String> roster; // lowercase RSN -> team code
}
