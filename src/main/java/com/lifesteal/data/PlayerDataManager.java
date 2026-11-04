package com.lifesteal.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lifesteal.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DATA_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lifesteal_userdata");
    private static final Map<UUID, PlayerData> CACHE = new ConcurrentHashMap<>();

    public static void init() {
        if (!DATA_DIR.exists()) {
            DATA_DIR.mkdirs();
        }
        File[] files = DATA_DIR.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try (FileReader reader = new FileReader(f)) {
                    PlayerData data = GSON.fromJson(reader, PlayerData.class);
                    if (data != null && data.uuid != null) {
                        CACHE.put(data.uuid, data);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static PlayerData get(ServerPlayerEntity player) {
        return get(player.getUuid(), player.getName().getString());
    }

    public static PlayerData get(UUID uuid, String name) {
        PlayerData data = CACHE.computeIfAbsent(uuid, id -> new PlayerData(id, name, ConfigManager.get().defaultHearts));
        if (name != null && !name.isEmpty()) {
            data.username = name;
        }
        return data;
    }

    public static PlayerData getByUuid(UUID uuid) {
        return CACHE.get(uuid);
    }

    public static PlayerData findByName(String name) {
        for (PlayerData d : CACHE.values()) {
            if (d.username.equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    public static Collection<PlayerData> getAll() {
        return CACHE.values();
    }

    public static void save(UUID uuid) {
        PlayerData data = CACHE.get(uuid);
        if (data == null) return;
        File file = new File(DATA_DIR, uuid.toString() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        for (UUID id : CACHE.keySet()) {
            save(id);
        }
    }
}
