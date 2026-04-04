package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class BingoTeam
{
    private final String code;
    private final String name;
    @Setter private double tilePoints;
    @Setter private double bountyBonus;
    @Setter private double totalPoints;
    @Setter private int rank;
}
