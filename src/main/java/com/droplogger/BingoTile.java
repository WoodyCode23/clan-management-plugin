package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BingoTile
{
    private final String code;
    private final String name;
    private final String type; // "drop", "kc", "xp"
    private final String metric; // WOM metric for kc/xp tiles
    private final double threshold;
    private final double max;
    private final int row;
    private final int col;
}
