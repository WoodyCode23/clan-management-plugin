package com.droplogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanManagementPluginTest
{
    public static void main(String[] args) throws Exception
    {
        // runClient also puts the dev-only PlatformEndpoint (src/dev) first on the classpath, so with
        // SOLUS_DEV_API set this client talks to the private dev API instead of the live one.
        ExternalPluginManager.loadBuiltin(ClanManagementPlugin.class);
        RuneLite.main(args);
    }
}
