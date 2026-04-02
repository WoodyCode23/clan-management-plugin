package com.droplogger;

import java.time.Instant;

public class DropEntry
{
    private final String itemName;
    private final int value;
    private final String monsterName;
    private final int killCount;
    private final int worldX;
    private final int worldY;
    private final int plane;
    private final Instant timestamp;
    private final String playerName;

    public DropEntry(String itemName, int value, String monsterName, int killCount,
                     int worldX, int worldY, int plane, String playerName)
    {
        this.itemName = itemName;
        this.value = value;
        this.monsterName = monsterName;
        this.killCount = killCount;
        this.worldX = worldX;
        this.worldY = worldY;
        this.plane = plane;
        this.timestamp = Instant.now();
        this.playerName = playerName;
    }

    public String getItemName() { return itemName; }
    public int getValue() { return value; }
    public String getMonsterName() { return monsterName; }
    public int getKillCount() { return killCount; }
    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }
    public int getPlane() { return plane; }
    public Instant getTimestamp() { return timestamp; }
    public String getPlayerName() { return playerName; }
}
