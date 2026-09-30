package com.droplogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanManagementPluginTest
{
    public static void main(String[] args) throws Exception
    {
        // Dev only: with SOLUS_DEV_API set (for example http://127.0.0.1:3002 through an SSH tunnel)
        // the dev build talks to the private dev API, so mass cards from the dev Discord server can be
        // tested. Without it, the normal plugin loads and talks to the live API as usual.
        String devApi = System.getenv("SOLUS_DEV_API");
        if (devApi != null && !devApi.isEmpty())
        {
            ExternalPluginManager.loadBuiltin(DevClanManagementPlugin.class);
        }
        else
        {
            ExternalPluginManager.loadBuiltin(ClanManagementPlugin.class);
        }
        RuneLite.main(args);
    }
}
