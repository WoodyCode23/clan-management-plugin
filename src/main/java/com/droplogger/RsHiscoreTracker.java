package com.droplogger;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanMember;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class RsHiscoreTracker
{
    private final Client client;
    private final PlatformApiService platformApiService;
    private final ScheduledExecutorService executor;

    private final Set<String> previousOnlineMembers = new HashSet<>();
    private boolean initialized = false;
    private boolean rosterSyncedThisSession = false;

    @Inject
    public RsHiscoreTracker(Client client, PlatformApiService platformApiService,
                            ScheduledExecutorService executor)
    {
        this.client = client;
        this.platformApiService = platformApiService;
        this.executor = executor;
    }

    /**
     * Called every game tick. Tracks online clan members and detects logoffs.
     */
    public void onGameTick(ClanManagementConfig config)
    {
        if (!config.enableStatTracking())
        {
            return;
        }

        String baseUrl = config.platformApiUrl();
        String apiKey = config.platformApiKey();
        String slug = config.platformClanSlug();
        if (baseUrl.isEmpty() || apiKey.isEmpty() || slug.isEmpty())
        {
            return;
        }

        ClanChannel clanChannel = client.getClanChannel();
        if (clanChannel == null)
        {
            return;
        }

        // Build current online set
        Set<String> currentOnline = new HashSet<>();
        for (ClanChannelMember member : clanChannel.getMembers())
        {
            String name = Text.toJagexName(member.getName());
            currentOnline.add(name);
        }

        if (!initialized)
        {
            // First tick — just record who is online, don't trigger snapshots
            previousOnlineMembers.addAll(currentOnline);
            initialized = true;
            return;
        }

        // Detect logoffs: was in previous set but not in current
        for (String name : previousOnlineMembers)
        {
            if (!currentOnline.contains(name))
            {
                log.debug("Clan member logged off: {}, triggering snapshot", name);
                executor.submit(() -> platformApiService.triggerSnapshot(baseUrl, apiKey, slug, name));
            }
        }

        previousOnlineMembers.clear();
        previousOnlineMembers.addAll(currentOnline);
    }

    /**
     * Sync the full clan roster from ClanSettings. Admin only.
     * Returns the number of members synced, or -1 if ClanSettings unavailable.
     */
    public int syncRoster(ClanManagementConfig config)
    {
        String baseUrl = config.platformApiUrl();
        String apiKey = config.platformApiKey();
        String slug = config.platformClanSlug();
        if (baseUrl.isEmpty() || apiKey.isEmpty() || slug.isEmpty())
        {
            return -1;
        }

        ClanSettings clanSettings = client.getClanSettings();
        if (clanSettings == null)
        {
            log.warn("ClanSettings not available — is the player in a clan?");
            return -1;
        }

        List<ClanMember> members = clanSettings.getMembers();
        if (members == null || members.isEmpty())
        {
            return 0;
        }

        List<String[]> memberList = new ArrayList<>();
        for (ClanMember member : members)
        {
            String name = Text.toJagexName(member.getName());
            String rank = member.getRank() != null ? member.getRank().toString() : null;
            memberList.add(new String[]{name, rank});
        }

        log.info("Syncing roster: {} members", memberList.size());
        executor.submit(() -> platformApiService.syncRoster(baseUrl, apiKey, slug, memberList));
        return memberList.size();
    }

    /**
     * Auto-sync roster on login if admin key is configured. Called once per session.
     */
    public void onLoginIfAdmin(ClanManagementConfig config, String adminKey)
    {
        if (rosterSyncedThisSession || adminKey == null || adminKey.isEmpty())
        {
            return;
        }

        rosterSyncedThisSession = true;
        int count = syncRoster(config);
        if (count > 0)
        {
            log.info("Auto-synced roster on login: {} members", count);
        }
    }

    public void reset()
    {
        previousOnlineMembers.clear();
        initialized = false;
        rosterSyncedThisSession = false;
    }
}
