package com.droplogger;

import net.runelite.client.plugins.PluginDescriptor;

/**
 * TEST SOURCES ONLY, never shipped. The dev launcher loads this instead of ClanManagementPlugin when
 * SOLUS_DEV_API is set (for example http://127.0.0.1:3002 through an SSH tunnel), so a local build
 * can be tested against the private dev API and the dev Discord server. The shipped plugin only
 * ever calls the hardcoded PLATFORM_URL.
 */
@PluginDescriptor(
    name = "Solus",
    description = "Solus clan plugin (dev build, dev API)",
    tags = {"solus", "clan"}
)
public class DevClanManagementPlugin extends ClanManagementPlugin
{
    @Override
    protected String getPlatformUrl()
    {
        String dev = System.getenv("SOLUS_DEV_API");
        return dev != null && !dev.isEmpty() ? dev : super.getPlatformUrl();
    }
}
