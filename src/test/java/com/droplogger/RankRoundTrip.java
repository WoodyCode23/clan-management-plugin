package com.droplogger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * Verifies RankSystem.parseRanks is the exact inverse of RankExport: serialize the bundled
 * RANKS -> A, parse A back into Rank objects, re-serialize -> B, assert A equals B.
 * Run: gradlew rankRoundTrip
 */
public final class RankRoundTrip {
    private static JsonArray serialize(List<RankSystem.Rank> ranks) {
        JsonArray arr = new JsonArray();
        for (RankSystem.Rank r : ranks) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id); ro.addProperty("name", r.name);
            ro.addProperty("path", r.path); ro.addProperty("desc", r.desc);
            JsonArray req = new JsonArray(); for (String s : r.requires) req.add(s); ro.add("requires", req);
            JsonArray groups = new JsonArray();
            for (RankSystem.Group g : r.groups) {
                JsonObject go = new JsonObject();
                go.addProperty("label", g.label); go.addProperty("need", g.need);
                JsonArray opts = new JsonArray(); for (RankSystem.Check c : g.options) opts.add(RankExport.check(c));
                go.add("options", opts); groups.add(go);
            }
            ro.add("groups", groups); arr.add(ro);
        }
        return arr;
    }

    public static void main(String[] args) throws Exception {
        JsonArray a = serialize(RankSystem.RANKS);
        String aStr = new GsonBuilder().create().toJson(a);
        List<RankSystem.Rank> parsed = RankSystem.parseRanks(aStr);
        JsonArray b = serialize(parsed);
        String bStr = new GsonBuilder().create().toJson(b);
        if (!aStr.equals(bStr)) {
            System.out.println("ROUNDTRIP_FAIL");
            // find first divergence
            int n = Math.min(aStr.length(), bStr.length());
            int i = 0; while (i < n && aStr.charAt(i) == bStr.charAt(i)) i++;
            int from = Math.max(0, i - 60);
            System.out.println("A: ..." + aStr.substring(from, Math.min(aStr.length(), i + 60)));
            System.out.println("B: ..." + bStr.substring(from, Math.min(bStr.length(), i + 60)));
            System.exit(1);
        }
        System.out.println("ROUNDTRIP_OK: " + parsed.size() + " ranks, " + aStr.length() + " chars identical");

        // Alternates: owning only a hidden alt (an upgrade) must satisfy the check, and the alt must
        // survive a serialize->parse round-trip.
        String altJson = "[{\"id\":\"t\",\"name\":\"T\",\"path\":\"\",\"desc\":\"\",\"requires\":[],"
            + "\"groups\":[{\"label\":\"ring\",\"need\":1,\"options\":["
            + "{\"kind\":\"ITEMS\",\"label\":\"A ring\",\"names\":[\"berserker ring (i)\"],\"k\":1,\"alts\":[\"ultor ring\"]}]}]}]";
        RankSystem.Rank altRank = RankSystem.parseRanks(altJson).get(0);
        if (altRank.groups.get(0).options.get(0).alts.size() != 1) { System.out.println("ALTS_PARSE_FAIL"); System.exit(1); }
        RankSystem.PlayerSnapshot snap = new RankSystem.PlayerSnapshot();
        snap.ownedItems.add("ultor ring"); // owns only the alt, not the named item
        RankSystem.RankStatus st = RankSystem.evaluate(altRank, snap);
        if (!st.eligible) { System.out.println("ALTS_EVAL_FAIL: owning the alt did not satisfy the check"); System.exit(1); }
        // And re-serializing keeps the alt.
        if (!new GsonBuilder().create().toJson(serialize(RankSystem.parseRanks(altJson))).contains("ultor ring")) {
            System.out.println("ALTS_REEXPORT_FAIL"); System.exit(1);
        }
        System.out.println("ALTS_OK: owned alt satisfied the check + round-tripped");
    }
}
