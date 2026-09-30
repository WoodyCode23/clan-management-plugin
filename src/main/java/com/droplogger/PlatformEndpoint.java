package com.droplogger;

/**
 * The only server this plugin talks to. The plugin is Solus-only and always calls this ONE hardcoded
 * URL, as the RuneLite Plugin Hub requires (no network call to a URL derived from user input or
 * fetched data). The only connection setting a user enters is their clan API key.
 */
final class PlatformEndpoint
{
    private static final String URL = "https://api.solusosrs.com";

    private PlatformEndpoint()
    {
    }

    static String url()
    {
        return URL;
    }
}
