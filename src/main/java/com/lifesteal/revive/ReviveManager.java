package com.lifesteal.revive;

import com.lifesteal.config.ConfigManager;
import com.lifesteal.data.PlayerData;
import com.lifesteal.data.PlayerDataManager;
import com.lifesteal.hologram.HologramManager;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.Map;

public class ReviveManager {

    public static void tickEliminatedAndHelpers(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayers()) {
            PlayerData data = PlayerDataManager.get(player);

            if (data.eliminated) {
                enforceSpectatorLock(server, player, data);
                checkHelperCompletion(server, player, data);
            }
        }
    }

    private static void enforceSpectatorLock(MinecraftServer server, ServerPlayerEntity player, PlayerData data) {
        if (!player.isSpectator()) {
            player.changeGameMode(GameMode.SPECTATOR);
        }

        RegistryKey<World> targetDimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(data.lockDimension));
        ServerWorld targetWorld = server.getWorld(targetDimKey);
        if (targetWorld == null) {
            targetWorld = server.getOverworld();
        }

        if (player.getServerWorld() != targetWorld || player.squaredDistanceTo(data.lockX, data.lockY, data.lockZ) > 0.04) {
            player.teleport(targetWorld, data.lockX, data.lockY, data.lockZ, data.lockYaw, data.lockPitch);
        }

        if (server.getTicks() % 60 == 0) {
            String msg = data.invitedHelperUuid == null
                ? "§c§lELIMINATED! §7Use §e/invite <player> §7so they can play 1h & get a Diamond Sword to revive you!"
                : "§eWaiting for §6" + data.invitedHelperName + " §eto play 1h & hold a Diamond Sword...";
            player.sendMessage(Text.literal(msg), true);
        }
    }

    private static void checkHelperCompletion(MinecraftServer server, ServerPlayerEntity eliminatedPlayer, PlayerData eliminatedData) {
        if (eliminatedData.invitedHelperUuid == null) return;

        ServerPlayerEntity helper = server.getPlayerManager().getPlayer(eliminatedData.invitedHelperUuid);
        if (helper == null) return;

        PlayerData helperData = PlayerDataManager.get(helper);
        long elapsedTicks = helperData.playTimeTicks - eliminatedData.helperStartTicks;
        long requiredTicks = ConfigManager.get().reviveRequiredPlaytimeSeconds * 20L;

        boolean timeReached = elapsedTicks >= requiredTicks;
        boolean hasRequiredSword = hasItemInInventory(helper, ConfigManager.get().reviveRequiredItem);

        if (server.getTicks() % 200 == 0 && !timeReached) {
            long remainingMinutes = Math.max(1L, ((requiredTicks - elapsedTicks) / 20L) / 60L);
            helper.sendMessage(Text.literal("§6[Revive Quest] §e" + remainingMinutes + "m remaining + obtain a Diamond Sword to revive §f" + eliminatedPlayer.getName().getString()), true);
        }

        if (timeReached && hasRequiredSword) {
            completeRevive(server, eliminatedPlayer, eliminatedData, helper);
        }
    }

    private static boolean hasItemInInventory(ServerPlayerEntity player, String itemId) {
        Item requiredItem = Registries.ITEM.get(Identifier.of(itemId));
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(requiredItem)) {
                return true;
            }
        }
        return false;
    }

    public static void completeRevive(MinecraftServer server, ServerPlayerEntity player, PlayerData data, ServerPlayerEntity helper) {
        data.eliminated = false;
        data.invitedHelperUuid = null;
        data.invitedHelperName = "";
        data.helperStartTicks = 0L;

        double fullHealth = ConfigManager.get().defaultHearts * 2.0;
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(fullHealth);
        }
        data.currentHearts = ConfigManager.get().defaultHearts;

        player.changeGameMode(GameMode.SURVIVAL);
        player.setHealth((float) fullHealth);

        PlayerDataManager.save(player.getUuid());
        HologramManager.updatePlayerHologramState(player);

        player.sendMessage(Text.literal("§a§lYOU ARE REVIVED! §7Restored to full hearts thanks to §e" + helper.getName().getString() + "§7!"), false);
        helper.sendMessage(Text.literal("§a§lQUEST COMPLETE! §7You have revived §e" + player.getName().getString() + "§7!"), false);

        ConfigManager.triggerEvent(server, "on_revive", Map.of(
            "%player%", player.getName().getString(),
            "%helper%", helper.getName().getString()
        ));
    }
}
