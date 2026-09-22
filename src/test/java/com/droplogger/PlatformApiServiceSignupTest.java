package com.droplogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pure parsing tests for the signups client model: PlatformApiService.parseSignups takes a
 * JsonObject and never touches the network, so a hand-built payload string exercises it exactly
 * like a real GET /clans/:slug/signups response would, including the new optional `you` object a
 * questionnaire-gated event adds ({@code you: { state, rsn, questionnaireUrl }}).
 *
 * The load-bearing assertion in this file is the one every test method that includes a
 * questionnaireUrl repeats: the parsed Signups has no field that could carry it, so there is no
 * getter to even assert against - the type system is the proof. The plugin must never display,
 * link, tooltip or open that URL (Hub rule); it parses `you.state` only.
 */
public class PlatformApiServiceSignupTest
{
    private static final Gson GSON = new Gson();

    private JsonObject json(String s) { return GSON.fromJson(s, JsonObject.class); }

    @Test public void youStatePendingParses()
    {
        String payload = "{\"open\":true,\"eventName\":\"Raid night\",\"signups\":[],"
            + "\"you\":{\"state\":\"pending\",\"rsn\":\"Woox\",\"questionnaireUrl\":\"https://forms.gle/abc123\"}}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertEquals("pending", s.youState);
        // No confirmed signups yet - a pending member is never added to the pool.
        assertTrue(s.rsns.isEmpty());
    }

    @Test public void youStateSignedUpParses()
    {
        String payload = "{\"open\":true,\"eventName\":\"Raid night\",\"signups\":[{\"rsn\":\"Woox\"}],"
            + "\"you\":{\"state\":\"signed_up\",\"rsn\":\"Woox\",\"questionnaireUrl\":null}}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertEquals("signed_up", s.youState);
        assertEquals(1, s.rsns.size());
        assertEquals("Woox", s.rsns.get(0));
    }

    @Test public void youStateNoneParses()
    {
        String payload = "{\"open\":true,\"eventName\":\"Raid night\",\"signups\":[],"
            + "\"you\":{\"state\":\"none\",\"rsn\":null,\"questionnaireUrl\":null}}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertEquals("none", s.youState);
    }

    @Test public void missingYouEntirelyDefaultsToNone()
    {
        // An older server that predates the `you` field - the plugin must keep working against it.
        String payload = "{\"open\":true,\"eventName\":\"Raid night\",\"signups\":[{\"rsn\":\"Zezima\"}]}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertEquals("none", s.youState);
        assertEquals(1, s.rsns.size());
    }

    @Test public void nullYouDefaultsToNone()
    {
        String payload = "{\"open\":true,\"eventName\":\"Raid night\",\"signups\":[],\"you\":null}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertEquals("none", s.youState);
    }

    @Test public void unrecognisedStateStringDefaultsToNone()
    {
        // A future/typo'd state string must never be treated as pending or signed_up.
        String payload = "{\"open\":true,\"signups\":[],\"you\":{\"state\":\"waitlisted\"}}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertEquals("none", s.youState);
    }

    @Test public void closedEventStillCarriesYouState()
    {
        // A closed/non-draft event returns open=false, but the viewer's own state (e.g. still
        // pending from before signups closed) is preserved rather than being reset to "none".
        String payload = "{\"open\":false,\"you\":{\"state\":\"pending\",\"questionnaireUrl\":\"https://forms.gle/xyz\"}}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertFalse(s.open);
        assertEquals("pending", s.youState);
        assertTrue(s.rsns.isEmpty());
    }

    @Test public void nullRootDefaultsToNone()
    {
        PlatformApiService.Signups s = PlatformApiService.parseSignups(null);
        assertNotNull(s);
        assertFalse(s.open);
        assertEquals("none", s.youState);
    }

    @Test public void malformedYouNeverThrows()
    {
        // "you" present but not an object at all - parse must skip it, not throw.
        String payload = "{\"open\":true,\"signups\":[],\"you\":\"not an object\"}";
        PlatformApiService.Signups s = PlatformApiService.parseSignups(json(payload));
        assertNotNull(s);
        assertEquals("none", s.youState);
    }
}
