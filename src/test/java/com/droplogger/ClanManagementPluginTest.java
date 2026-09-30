package com.droplogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanManagementPluginTest
{
    public static void main(String[] args) throws Exception
    {
        // Dev only: SOLUS_DEV_API=http://127.0.0.1:3002 (through an SSH tunnel) points this dev client
        // at the private dev API, so mass cards from the dev Discord server can be tested.
        String devApi = System.getenv("SOLUS_DEV_API");
        if (devApi != null && !devApi.isEmpty()) ClanManagementPlugin.devPlatformUrlOverride = devApi;
        ExternalPluginManager.loadBuiltin(ClanManagementPlugin.class);
        RuneLite.main(args);
    }
}
