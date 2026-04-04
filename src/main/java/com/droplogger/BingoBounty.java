package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BingoBounty
{
    private final int number;
    private final String description;
    private final String releaseTime; // ISO format EST
    private final double points;
    private final String winner;
    private final boolean hintFired;
    private final boolean releaseFired;
}
