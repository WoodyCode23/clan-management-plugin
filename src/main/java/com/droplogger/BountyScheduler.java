package com.droplogger;

import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BountyScheduler
{
    private static final ZoneId EST = ZoneId.of("America/New_York");
    private static final DateTimeFormatter PARSE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // SWB26 default bounty schedule
    private static final String DEFAULT_SCHEDULE =
        "1,2026-03-06T12:00;2,2026-03-07T21:00;3,2026-03-08T12:00;" +
        "4,2026-03-09T21:00;5,2026-03-10T12:00;6,2026-03-11T21:00;" +
        "7,2026-03-12T12:00;8,2026-03-13T21:00;9,2026-03-14T12:00";

    private final List<BountyEvent> events = new ArrayList<>();

    public void loadSchedule(String customSchedule)
    {
        events.clear();
        String schedule = (customSchedule != null && !customSchedule.isEmpty())
            ? customSchedule
            : DEFAULT_SCHEDULE;

        for (String entry : schedule.split(";"))
        {
            entry = entry.trim();
            if (entry.isEmpty())
            {
                continue;
            }

            String[] parts = entry.split(",", 2);
            if (parts.length != 2)
            {
                log.warn("Invalid bounty schedule entry: {}", entry);
                continue;
            }

            try
            {
                int bountyNumber = Integer.parseInt(parts[0].trim());
                ZonedDateTime releaseTime = java.time.LocalDateTime.parse(parts[1].trim(), PARSE_FORMAT)
                    .atZone(EST);
                events.add(new BountyEvent(bountyNumber, releaseTime));
            }
            catch (Exception e)
            {
                log.warn("Failed to parse bounty schedule entry: {}", entry, e);
            }
        }

        log.info("Loaded {} bounty events", events.size());
    }

    public List<BountyAlert> checkAlerts(int hintMinutesBefore)
    {
        List<BountyAlert> alerts = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(EST);

        for (BountyEvent event : events)
        {
            // Skip already-elapsed bounties that have already been handled
            if (event.isReleaseFired())
            {
                continue;
            }

            ZonedDateTime hintTime = event.getReleaseTime().minus(hintMinutesBefore, ChronoUnit.MINUTES);

            // Hint alert: fire once when we've passed the hint time but not yet the release
            if (!event.isHintFired() && now.isAfter(hintTime) && now.isBefore(event.getReleaseTime()))
            {
                event.setHintFired(true);
                long minutesLeft = ChronoUnit.MINUTES.between(now, event.getReleaseTime());
                alerts.add(new BountyAlert(
                    event.getBountyNumber(),
                    BountyAlert.Type.HINT,
                    "Bounty #" + event.getBountyNumber() + " hint: releasing in ~" + minutesLeft + " minutes!"
                ));
            }

            // Release alert: fire once when the bounty goes live
            if (!event.isReleaseFired() && now.isAfter(event.getReleaseTime()))
            {
                event.setReleaseFired(true);
                event.setHintFired(true); // in case we missed the hint
                alerts.add(new BountyAlert(
                    event.getBountyNumber(),
                    BountyAlert.Type.RELEASE,
                    "Bounty #" + event.getBountyNumber() + " is NOW LIVE! Go go go!"
                ));
            }
        }

        return alerts;
    }

    public String getNextBountyCountdown()
    {
        ZonedDateTime now = ZonedDateTime.now(EST);

        for (BountyEvent event : events)
        {
            if (now.isBefore(event.getReleaseTime()))
            {
                long totalSeconds = ChronoUnit.SECONDS.between(now, event.getReleaseTime());
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long seconds = totalSeconds % 60;
                return String.format("Bounty #%d in %02d:%02d:%02d",
                    event.getBountyNumber(), hours, minutes, seconds);
            }
        }

        return "No upcoming bounties";
    }

    public static class BountyAlert
    {
        public enum Type { HINT, RELEASE }

        private final int bountyNumber;
        private final Type type;
        private final String message;

        public BountyAlert(int bountyNumber, Type type, String message)
        {
            this.bountyNumber = bountyNumber;
            this.type = type;
            this.message = message;
        }

        public int getBountyNumber() { return bountyNumber; }
        public Type getType() { return type; }
        public String getMessage() { return message; }
    }
}
