package com.lifesteal.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerData {
    public UUID uuid;
    public String username = "Unknown";
    public int kills = 0;
    public int deaths = 0;
    public double currentHearts = 10.0;
    public double bountyHalfHearts = 0.0;
    public long playTimeTicks = 0L;
    public boolean showHologram = true;

    public boolean eliminated = false;
    public double lockX = 0.0;
    public double lockY = 64.0;
    public double lockZ = 0.0;
    public float lockYaw = 0.0f;
    public float lockPitch = 0.0f;
    public String lockDimension = "minecraft:overworld";

    public UUID invitedHelperUuid = null;
    public String invitedHelperName = "";
    public long helperStartTicks = 0L;

    public List<String> killedByHistory = new ArrayList<>();

    public PlayerData() {}

    public PlayerData(UUID uuid, String username, double defaultHearts) {
        this.uuid = uuid;
        this.username = username;
        this.currentHearts = defaultHearts;
    }

    public String getFormattedPlaytime() {
        long totalSeconds = this.playTimeTicks / 20L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        return hours + "h " + minutes + "m";
    }
}
