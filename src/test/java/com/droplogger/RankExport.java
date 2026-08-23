package com.droplogger;

import com.google.gson.*;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Migration tool: serialize the hardcoded RankSystem.RANKS to the server JSON contract
 * (docs/superpowers/specs/2026-08-23-rank-editor-design.md). Run: gradlew exportRanks.
 * Writes build/ranks-export.json, which is then seeded into the API via PUT /clans/solus/ranks.
 */
public final class RankExport {
    private static JsonObject check(RankSystem.Check c) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", c.kind.name());
        if (c.label != null) o.addProperty("label", c.label);
        switch (c.kind) {
            case ITEMS: o.add("names", arr(c.names)); o.addProperty("k", c.value); break;
            case ITEMS_PREFIX: o.add("prefixes", arr(c.names)); o.addProperty("k", c.value); break;
            case SKILL: o.addProperty("skill", c.key); o.addProperty("level", c.value); break;
            case TOTAL: o.addProperty("min", c.value); break;
            case TOTAL_XP: o.addProperty("xp", c.value); break;
            case COMBAT_LEVEL: o.addProperty("level", c.value); break;
            case CA_TIER: o.addProperty("tier", c.key); break;
            case CA_TASK: o.addProperty("task", c.names.get(0)); break;
            case DIARY: o.addProperty("tier", c.key); o.addProperty("count", c.value); break;
            case BOSS_KC: o.addProperty("boss", c.key); o.addProperty("count", c.value); if (c.key != null) o.addProperty("key", c.key); break;
            case CLOG: o.addProperty("count", c.value); break;
            case CLOG_SLOT: o.add("names", arr(c.names)); break;
            case UNLOCK: o.addProperty("key", c.key); break;
            case RANK: o.addProperty("rankId", c.key); if (c.label != null) o.addProperty("name", c.label); break;
            case ALL: o.add("children", children(c.children)); break;
            case ANY: o.addProperty("need", c.need); o.add("children", children(c.children)); break;
        }
        return o;
    }
    private static JsonArray arr(List<String> xs) { JsonArray a = new JsonArray(); if (xs != null) for (String x : xs) a.add(x); return a; }
    private static JsonArray children(List<RankSystem.Check> cs) { JsonArray a = new JsonArray(); for (RankSystem.Check c : cs) a.add(check(c)); return a; }

    public static void main(String[] args) throws Exception {
        JsonArray ranks = new JsonArray();
        for (RankSystem.Rank r : RankSystem.RANKS) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id); ro.addProperty("name", r.name);
            ro.addProperty("path", r.path); ro.addProperty("desc", r.desc);
            JsonArray req = new JsonArray(); for (String s : r.requires) req.add(s); ro.add("requires", req);
            JsonArray groups = new JsonArray();
            for (RankSystem.Group g : r.groups) {
                JsonObject go = new JsonObject();
                go.addProperty("label", g.label); go.addProperty("need", g.need);
                JsonArray opts = new JsonArray(); for (RankSystem.Check c : g.options) opts.add(check(c));
                go.add("options", opts); groups.add(go);
            }
            ro.add("groups", groups); ranks.add(ro);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream("build/ranks-export.json"), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(ranks, w);
        }
        System.out.println("RANK_EXPORT: " + ranks.size() + " ranks -> build/ranks-export.json");
    }
}
