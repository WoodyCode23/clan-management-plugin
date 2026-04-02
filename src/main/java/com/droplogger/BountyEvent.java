package com.droplogger;

import java.time.ZonedDateTime;

public class BountyEvent
{
    private final int bountyNumber;
    private final ZonedDateTime releaseTime;
    private boolean hintFired;
    private boolean releaseFired;

    public BountyEvent(int bountyNumber, ZonedDateTime releaseTime)
    {
        this.bountyNumber = bountyNumber;
        this.releaseTime = releaseTime;
        this.hintFired = false;
        this.releaseFired = false;
    }

    public int getBountyNumber() { return bountyNumber; }
    public ZonedDateTime getReleaseTime() { return releaseTime; }
    public boolean isHintFired() { return hintFired; }
    public void setHintFired(boolean hintFired) { this.hintFired = hintFired; }
    public boolean isReleaseFired() { return releaseFired; }
    public void setReleaseFired(boolean releaseFired) { this.releaseFired = releaseFired; }
}
