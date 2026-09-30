package com.droplogger;

/**
 * DEV ONLY, never shipped: the "dev" source set is not part of the plugin jar. runClient puts this
 * class ahead of the real PlatformEndpoint on its classpath, so a local dev client can talk to the
 * private dev API (for example http://127.0.0.1:3002 through an SSH tunnel) when SOLUS_DEV_API is
 * set. Without it, this returns the same live URL as the shipped class.
 */
final class PlatformEndpoint
{
    private static final String LIVE_URL = "https://api.solusosrs.com";

    private PlatformEndpoint()
    {
    }

    static String url()
    {
        String dev = System.getenv("SOLUS_DEV_API");
        return dev != null && !dev.isEmpty() ? dev : LIVE_URL;
    }
}
