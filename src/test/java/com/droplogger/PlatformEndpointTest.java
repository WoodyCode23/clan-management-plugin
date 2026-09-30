package com.droplogger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlatformEndpointTest
{
    @Test
    public void shippedPluginOnlyTalksToTheLiveApi()
    {
        // The test classpath has only the shipped PlatformEndpoint (the dev copy is runClient-only).
        assertEquals("https://api.solusosrs.com", PlatformEndpoint.url());
    }
}
