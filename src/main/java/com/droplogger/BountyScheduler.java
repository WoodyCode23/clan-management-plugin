package com.droplogger;

import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Slf4j
public class BountyScheduler
{
    private static final ZoneId EST = ZoneId.of("America/New_York");
    private static final DateTimeFormatter PARSER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ScheduledExecutorService executor;
    private final BingoService bingoService;
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

    // Callbacks: (bounty, message) for chat and Discord
    private BiConsumer<BingoBounty, String> onHint;
    private BiConsumer<BingoBounty, String> onRelease;

    public BountyScheduler(ScheduledExecutorService executor, BingoService bingoService)
    {
        this.executor = executor;
        this.bingoService = bingoService;
    }

    public void setOnHint(BiConsumer<BingoBounty, String> cb) { this.onHint = cb; }
    public void setOnRelease(BiConsumer<BingoBounty, String> cb) { this.onRelease = cb; }

    public void schedule(List<BingoBounty> bounties, int hintMinutesBefore)
    {
        cancel();

        ZonedDateTime now = ZonedDateTime.now(EST);
        long sequentialDelay = 0; // For firing past-but-unfired bounties sequentially

        for (BingoBounty bounty : bounties)
        {
            if (bounty.getReleaseTime() == null || bounty.getReleaseTime().isEmpty()) continue;

            ZonedDateTime releaseTime;
            try
            {
                releaseTime = java.time.LocalDateTime.parse(bounty.getReleaseTime(), PARSER).atZone(EST);
            }
            catch (Exception e)
            {
                log.debug("Failed to parse bounty #{} release time: {}", bounty.getNumber(), bounty.getReleaseTime());
                continue;
            }

            ZonedDateTime hintTime = releaseTime.minusMinutes(hintMinutesBefore);

            // Schedule hint
            if (!bounty.isHintFired())
            {
                long delayMs = ChronoUnit.MILLIS.between(now, hintTime);
                if (delayMs < 0)
                {
                    // Past but never fired — skip, don't spam old hints
                }
                else
                {
                    scheduledTasks.add(executor.schedule(
                        () -> fireHint(bounty, hintMinutesBefore),
                        delayMs, TimeUnit.MILLISECONDS));
                }
            }

            // Schedule release
            if (!bounty.isReleaseFired())
            {
                long delayMs = ChronoUnit.MILLIS.between(now, releaseTime);
                if (delayMs < 0)
                {
                    // Past but never fired — fire with sequential delay to avoid spam
                    sequentialDelay += 5000;
                    final long delay = sequentialDelay;
                    scheduledTasks.add(executor.schedule(
                        () -> fireRelease(bounty),
                        delay, TimeUnit.MILLISECONDS));
                }
                else
                {
                    scheduledTasks.add(executor.schedule(
                        () -> fireRelease(bounty),
                        delayMs, TimeUnit.MILLISECONDS));
                }
            }
        }

        log.info("Bounty scheduler: {} tasks scheduled", scheduledTasks.size());
    }

    public void cancel()
    {
        for (ScheduledFuture<?> task : scheduledTasks)
        {
            task.cancel(false);
        }
        scheduledTasks.clear();
    }

    /**
     * Get the next upcoming bounty and time until release, for countdown display.
     */
    public String getNextBountyCountdown(List<BingoBounty> bounties)
    {
        ZonedDateTime now = ZonedDateTime.now(EST);
        BingoBounty next = null;
        long minDelay = Long.MAX_VALUE;

        for (BingoBounty bounty : bounties)
        {
            if (bounty.getReleaseTime() == null || bounty.getReleaseTime().isEmpty()) continue;
            if (!bounty.getWinner().isEmpty()) continue; // Already completed

            try
            {
                ZonedDateTime releaseTime = java.time.LocalDateTime.parse(bounty.getReleaseTime(), PARSER).atZone(EST);
                long delay = ChronoUnit.MILLIS.between(now, releaseTime);
                if (delay > 0 && delay < minDelay)
                {
                    minDelay = delay;
                    next = bounty;
                }
            }
            catch (Exception ignored) {}
        }

        if (next == null) return null;

        long hours = minDelay / (1000 * 60 * 60);
        long minutes = (minDelay / (1000 * 60)) % 60;
        return "Bounty #" + next.getNumber() + " in " + hours + "h " + minutes + "m";
    }

    private void fireHint(BingoBounty bounty, int minutesBefore)
    {
        String message = "[Bingo] Bounty #" + bounty.getNumber() +
            " hint: releasing in ~" + minutesBefore + " minutes!";
        log.info(message);

        if (onHint != null) onHint.accept(bounty, message);

        // Persist fired state
        try { bingoService.markBountyFired(bounty.getNumber(), "hintFired"); }
        catch (Exception e) { log.warn("Failed to persist hintFired for bounty #{}", bounty.getNumber(), e); }
    }

    private void fireRelease(BingoBounty bounty)
    {
        String message = "[Bingo] Bounty #" + bounty.getNumber() +
            " is NOW LIVE: " + bounty.getDescription();
        log.info(message);

        if (onRelease != null) onRelease.accept(bounty, message);

        // Persist fired state
        try { bingoService.markBountyFired(bounty.getNumber(), "releaseFired"); }
        catch (Exception e) { log.warn("Failed to persist releaseFired for bounty #{}", bounty.getNumber(), e); }
    }
}
