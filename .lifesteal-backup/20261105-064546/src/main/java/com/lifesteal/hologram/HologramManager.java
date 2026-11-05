package com.lifesteal.hologram;

import com.lifesteal.data.PlayerData;
import com.lifesteal.data.PlayerDataManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {
    private static final Map<UUID, DisplayEntity.TextDisplayEntity> ACTIVE_HOLOGRAMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> HIDDEN_PAIRS = new ConcurrentHashMap<>();

    public static void tickServerHolograms(MinecraftServer server) {
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayers()) {
            updatePlayerHologramState(target);
        }

        for (ServerPlayerEntity observer : server.getPlayerManager().getPlayers()) {
            Set<UUID> hiddenForObserver = HIDDEN_PAIRS.computeIfAbsent(observer.getUuid(), k -> ConcurrentHashMap.newKeySet());

            for (ServerPlayerEntity target : server.getPlayerManager().getPlayers()) {
                if (observer.getUuid().equals(target.getUuid())) continue;

                DisplayEntity.TextDisplayEntity hologram = ACTIVE_HOLOGRAMS.get(target.getUuid());
                if (hologram == null || !hologram.isAlive()) continue;

                if (observer.getWorld() != target.getWorld()) continue;

                boolean blockedByWall = !hasLineOfSight(observer, target);

                if (blockedByWall) {
                    if (!hiddenForObserver.contains(target.getUuid())) {
                        hiddenForObserver.add(target.getUuid());
                        observer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(hologram.getId()));
                    }
                } else {
                    if (hiddenForObserver.contains(target.getUuid())) {
                        hiddenForObserver.remove(target.getUuid());
                        removeHologram(target.getUuid());
                        updatePlayerHologramState(target);
                    }
                }
            }
        }
    }

    public static void updatePlayerHologramState(ServerPlayerEntity player) {
        PlayerData data = PlayerDataManager.get(player);
        double hearts = player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH) / 2.0;
        data.currentHearts = hearts;

        boolean shouldHide = !player.isAlive()
            || player.isSneaking()
            || player.isSpectator()
            || player.isInvisible()
            || !data.showHologram
            || data.eliminated;

        if (shouldHide) {
            removeHologram(player.getUuid());
            return;
        }

        DisplayEntity.TextDisplayEntity display = ACTIVE_HOLOGRAMS.get(player.getUuid());
        if (display == null || !display.isAlive() || display.getWorld() != player.getWorld()) {
            removeHologram(player.getUuid());
            display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, player.getWorld());
            display.refreshPositionAndAngles(player.getX(), player.getY() + 2.25, player.getZ(), 0, 0);
            display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
            display.setViewRange(0.6f);
            player.getWorld().spawnEntity(display);
            display.startRiding(player, true);
            ACTIVE_HOLOGRAMS.put(player.getUuid(), display);
        }

        String bountySuffix = data.bountyHalfHearts > 0
            ? String.format(" §6[Bounty: %.1f❤]", data.bountyHalfHearts / 2.0)
            : "";

        display.setText(Text.literal(String.format("§c❤ %.1f Hearts%s", hearts, bountySuffix)));
    }

    private static boolean hasLineOfSight(ServerPlayerEntity observer, ServerPlayerEntity target) {
        if (observer.squaredDistanceTo(target) > 4096.0) {
            return false;
        }
        Vec3d start = observer.getEyePos();
        Vec3d end = target.getEyePos().add(0, 0.35, 0);

        BlockHitResult hitResult = observer.getWorld().raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.VISUAL,
            RaycastContext.FluidHandling.NONE,
            observer
        ));

        return hitResult.getType() == HitResult.Type.MISS;
    }

    public static void removeHologram(UUID playerUuid) {
        DisplayEntity.TextDisplayEntity existing = ACTIVE_HOLOGRAMS.remove(playerUuid);
        if (existing != null && existing.isAlive()) {
            existing.discard();
        }
        for (Set<UUID> set : HIDDEN_PAIRS.values()) {
            set.remove(playerUuid);
        }
    }
}
