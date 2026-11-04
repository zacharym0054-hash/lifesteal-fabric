package com.lifesteal.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
    public double defaultHearts = 10.0;
    public double maxHearts = 20.0;
    public double minHearts = 1.0;
    public double heartsLostOnDeath = 1.0;
    public double heartsGainedOnKill = 1.0;
    public int reviveRequiredPlaytimeSeconds = 3600;
    public String reviveRequiredItem = "minecraft:diamond_sword";

    public Map<String, List<String>> programmableEvents = new HashMap<>();

    public ModConfig() {
        List<String> onKill = new ArrayList<>();
        onKill.add("tellraw @a {\"text\":\"[Lifesteal] %killer% stole a heart from %victim%!\",\"color\":\"red\"}");
        programmableEvents.put("on_kill", onKill);

        List<String> onElimination = new ArrayList<>();
        onElimination.add("tellraw @a {\"text\":\"[Lifesteal] %player% lost their final heart and is locked in Spectator at their death location!\",\"color\":\"dark_red\"}");
        onElimination.add("playsound minecraft:entity.wither.spawn master @a ~ ~ ~ 1 0.8");
        programmableEvents.put("on_elimination", onElimination);

        List<String> onRevive = new ArrayList<>();
        onRevive.add("tellraw @a {\"text\":\"[Lifesteal] %player% has been revived to full health thanks to %helper%!\",\"color\":\"green\"}");
        onRevive.add("playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 1 1");
        programmableEvents.put("on_revive", onRevive);

        List<String> onBountyClaim = new ArrayList<>();
        onBountyClaim.add("tellraw @a {\"text\":\"[Bounty] %killer% claimed a %bounty% heart bounty on %victim%!\",\"color\":\"gold\"}");
        programmableEvents.put("on_bounty_claim", onBountyClaim);

        programmableEvents.put("on_withdraw", new ArrayList<>());
        programmableEvents.put("on_heart_consume", new ArrayList<>());
        programmableEvents.put("on_death", new ArrayList<>());
    }
}
