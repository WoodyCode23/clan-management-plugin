package com.droplogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DropLoggerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DropLoggerPlugin.class);
        RuneLite.main(args);
    }
}
