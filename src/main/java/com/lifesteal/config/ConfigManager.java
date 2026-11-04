package com.lifesteal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lifesteal_events.json");
    private static ModConfig config;

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            config = new ModConfig();
            save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            config = GSON.fromJson(reader, ModConfig.class);
            if (config == null) {
                config = new ModConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
            config = new ModConfig();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ModConfig get() {
        if (config == null) {
            load();
        }
        return config;
    }

    public static void triggerEvent(MinecraftServer server, String eventKey, Map<String, String> placeholders) {
        if (server == null || get().programmableEvents == null) return;
        List<String> commands = get().programmableEvents.get(eventKey);
        if (commands == null || commands.isEmpty()) return;

        for (String rawCommand : commands) {
            String cmd = rawCommand;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                cmd = cmd.replace(entry.getKey(), entry.getValue());
            }
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), cmd);
        }
    }
}
