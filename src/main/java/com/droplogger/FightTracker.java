package com.droplogger;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks all players seen in an instanced boss fight from entry to exit.
 * This prevents "fake solos" — if a second player was ever present during the fight,
 * the party size reflects that even if they left before the kill.
 */
@Slf4j
public class FightTracker
{
    private final Set<String> trackedPlayers = new LinkedHashSet<>();
    private boolean tracking = false;

    /**
     * Start tracking a new fight. Call when entering an instanced region.
     * Clears any previous tracking data and seeds with the local player.
     */
    public void startTracking(String localPlayerName)
    {
        trackedPlayers.clear();
        tracking = true;
        if (localPlayerName != null && !localPlayerName.isEmpty())
        {
            trackedPlayers.add(localPlayerName);
        }
        log.debug("Fight tracking started for {}", localPlayerName);
    }

    /**
     * Add visible players to the tracked set. Call each game tick while in an instance.
     * Only counts players on the same plane as the local player (filters spectators).
     *
     * @param visiblePlayers all players visible to the client
     * @param localPlayer    the local player (to get plane and skip self)
     */
    public void addPlayers(List<Player> visiblePlayers, Player localPlayer)
    {
        if (!tracking || localPlayer == null)
        {
            return;
        }

        int localPlane = localPlayer.getWorldLocation().getPlane();

        for (Player player : visiblePlayers)
        {
            if (player == localPlayer)
            {
                continue;
            }
            String name = player.getName();
            if (name == null || name.isEmpty())
            {
                continue;
            }
            // Only count players on the same plane (filters ToB spectators etc.)
            if (player.getWorldLocation().getPlane() != localPlane)
            {
                continue;
            }
            if (trackedPlayers.add(name))
            {
                log.debug("Fight tracker: added player {}", name);
            }
        }
    }

    /**
     * Stop tracking. Call when leaving an instanced region.
     * Does NOT clear the tracked players — they remain available for PB submission.
     */
    public void stopTracking()
    {
        if (tracking)
        {
            log.debug("Fight tracking stopped. Tracked {} players: {}",
                trackedPlayers.size(), trackedPlayers);
        }
        tracking = false;
    }

    /**
     * Get all players seen during the current (or most recent) fight.
     * Returns an unmodifiable sorted list for deterministic submitter selection.
     */
    public List<String> getTrackedMembers()
    {
        List<String> members = new ArrayList<>(trackedPlayers);
        Collections.sort(members, String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(members);
    }

    /**
     * Get the tracked party size (all players ever seen during the fight).
     */
    public int getTrackedPartySize()
    {
        return trackedPlayers.size();
    }

    /**
     * Whether we are actively tracking a fight.
     */
    public boolean isTracking()
    {
        return tracking;
    }

    /**
     * Clear all tracking state. Used on plugin shutdown.
     */
    public void reset()
    {
        trackedPlayers.clear();
        tracking = false;
    }
}
