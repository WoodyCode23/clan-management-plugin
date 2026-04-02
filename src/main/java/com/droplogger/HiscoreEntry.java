package com.droplogger;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HiscoreEntry
{
    private final int rank;
    private final double timeSeconds;
    private final String formattedTime;
    private final String rsns;
    private final String date;
    private final String categoryKey;
    private final int partySize;

    /**
     * Legacy constructor for v1 compatibility (no categoryKey/partySize).
     */
    public HiscoreEntry(int rank, double timeSeconds, String formattedTime, String rsns, String date)
    {
        this(rank, timeSeconds, formattedTime, rsns, date, null, 1);
    }
}
