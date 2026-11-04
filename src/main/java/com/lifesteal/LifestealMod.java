package com.lifesteal;

import com.lifesteal.config.ConfigManager;
import com.lifesteal.data.PlayerData;
import com.lifesteal.data.PlayerDataManager;
import com.lifesteal.gui.BedrockForms;
import com.lifesteal.gui.JavaDialogs;
import com.lifesteal.hologram.HologramManager;
import com.lifesteal.revive.ReviveManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.GameMode;

import java.util.Map;

public class LifestealMod implements ModInitializer {
    public static final String MOD_ID = "lifesteal";

    public static final Item HEART_ITEM = new Item(
        new Item.Settings()
            .maxCount(64)
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.CUSTOM_NAME, Text.literal("Heart of Vitality").formatted(Formatting.RED, Formatting.BOLD))
    );

    @Override
    public void onInitialize() {
        ConfigManager.load();
        PlayerDataManager.init();

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "heart"), HEART_ITEM);

        registerEvents();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerAllCommands(dispatcher);
        });
    }

    private void registerEvents() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PlayerDataManager.saveAll());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerData data = PlayerDataManager.get(player);
            HologramManager.updatePlayerHologramState(player);
            if (data.eliminated) {
                player.changeGameMode(GameMode.SPECTATOR);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            HologramManager.removeHologram(player.getUuid());
            PlayerDataManager.save(player.getUuid());
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient() && stack.isOf(HEART_ITEM) && player instanceof ServerPlayerEntity serverPlayer) {
                PlayerData data = PlayerDataManager.get(serverPlayer);
                if (data.eliminated) {
                    serverPlayer.sendMessage(Text.literal("§cEliminated players cannot consume hearts directly!"), true);
                    return TypedActionResult.fail(stack);
                }

                EntityAttributeInstance attr = serverPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (attr != null) {
                    double maxAllowed = ConfigManager.get().maxHearts * 2.0;
                    if (attr.getBaseValue() >= maxAllowed) {
                        serverPlayer.sendMessage(Text.literal("§cYou have reached the maximum of " + ConfigManager.get().maxHearts + " hearts!"), true);
                        return TypedActionResult.fail(stack);
                    }

                    attr.setBaseValue(Math.min(maxAllowed, attr.getBaseValue() + 2.0));
                    serverPlayer.heal(2.0f);
                    stack.decrement(1);
                    HologramManager.updatePlayerHologramState(serverPlayer);
                    PlayerDataManager.save(serverPlayer.getUuid());

                    serverPlayer.sendMessage(Text.literal("§a§l+1 ❤ Heart Consumed!"), true);
                    ConfigManager.triggerEvent(serverPlayer.getServer(), "on_heart_consume", Map.of(
                        "%player%", serverPlayer.getName().getString()
                    ));
                    return TypedActionResult.success(stack);
                }
            }
            return TypedActionResult.pass(stack);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return;

            PlayerData victimData = PlayerDataManager.get(victim);
            if (victimData.eliminated) return;

            victimData.deaths++;
            EntityAttributeInstance victimAttr = victim.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            double currentMaxHealth = victimAttr != null ? victimAttr.getBaseValue() : 20.0;
            double healthPenalty = ConfigManager.get().heartsLostOnDeath * 2.0;
            double updatedVictimHealth = currentMaxHealth - healthPenalty;

            ConfigManager.triggerEvent(victim.getServer(), "on_death", Map.of(
                "%player%", victim.getName().getString()
            ));

            if (damageSource.getAttacker() instanceof ServerPlayerEntity killer && !killer.getUuid().equals(victim.getUuid())) {
                PlayerData killerData = PlayerDataManager.get(killer);
                killerData.kills++;

                if (!victimData.killedByHistory.contains(killer.getName().getString())) {
                    victimData.killedByHistory.add(killer.getName().getString());
                }

                double baseRewardHealth = ConfigManager.get().heartsGainedOnKill * 2.0;
                double bountyRewardHealth = victimData.bountyHalfHearts;
                double totalHealthGain = baseRewardHealth + bountyRewardHealth;

                EntityAttributeInstance killerAttr = killer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (killerAttr != null) {
                    double maxCap = ConfigManager.get().maxHearts * 2.0;
                    killerAttr.setBaseValue(Math.min(maxCap, killerAttr.getBaseValue() + totalHealthGain));
                    killer.heal((float) totalHealthGain);
                }

                if (bountyRewardHealth > 0) {
                    ConfigManager.triggerEvent(victim.getServer(), "on_bounty_claim", Map.of(
                        "%killer%", killer.getName().getString(),
                        "%victim%", victim.getName().getString(),
                        "%bounty%", String.format("%.1f", bountyRewardHealth / 2.0)
                    ));
                    victimData.bountyHalfHearts = 0.0;
                }

                ConfigManager.triggerEvent(victim.getServer(), "on_kill", Map.of(
                    "%killer%", killer.getName().getString(),
                    "%victim%", victim.getName().getString()
                ));

                killer.sendMessage(Text.literal("§aYou stole §c" + String.format("%.1f ❤", totalHealthGain / 2.0) + " §afrom §f" + victim.getName().getString() + "§a!"), false);
                HologramManager.updatePlayerHologramState(killer);
                PlayerDataManager.save(killer.getUuid());
            }

            if (updatedVictimHealth < ConfigManager.get().minHearts * 2.0) {
                victimData.eliminated = true;
                victimData.lockX = victim.getX();
                victimData.lockY = victim.getY();
                victimData.lockZ = victim.getZ();
                victimData.lockYaw = victim.getYaw();
                victimData.lockPitch = victim.getPitch();
                victimData.lockDimension = victim.getWorld().getRegistryKey().getValue().toString();

                if (victimAttr != null) {
                    victimAttr.setBaseValue(2.0);
                }
                victimData.currentHearts = 0.0;
                victim.changeGameMode(GameMode.SPECTATOR);

                victim.sendMessage(Text.literal("§4§lYOU LOST YOUR FINAL HEART!\n§cYou are locked at your death coordinates in Spectator mode.\n§eUse §6/invite <player> §eso they can play 1 hour and get a Diamond Sword to revive you!"), false);

                ConfigManager.triggerEvent(victim.getServer(), "on_elimination", Map.of(
                    "%player%", victim.getName().getString()
                ));
            } else {
                if (victimAttr != null) {
                    victimAttr.setBaseValue(updatedVictimHealth);
                }
                victimData.currentHearts = updatedVictimHealth / 2.0;
            }

            HologramManager.updatePlayerHologramState(victim);
            PlayerDataManager.save(victim.getUuid());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayers()) {
                PlayerData data = PlayerDataManager.get(player);
                data.playTimeTicks++;
            }
            HologramManager.tickServerHolograms(server);
            ReviveManager.tickEliminatedAndHelpers(server);
        });
    }

    private void registerAllCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(buildMainCommand("lifesteal"));
        dispatcher.register(buildMainCommand("lf"));

        dispatcher.register(CommandManager.literal("invite")
            .then(CommandManager.argument("helper", EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    PlayerData senderData = PlayerDataManager.get(sender);

                    if (!senderData.eliminated) {
                        sender.sendMessage(Text.literal("§cOnly eliminated players locked in spectator can use /invite!"), false);
                        return 0;
                    }

                    ServerPlayerEntity helper = EntityArgumentType.getPlayer(ctx, "helper");
                    if (helper.getUuid().equals(sender.getUuid())) {
                        sender.sendMessage(Text.literal("§cYou cannot invite yourself!"), false);
                        return 0;
                    }

                    PlayerData helperData = PlayerDataManager.get(helper);
                    if (helperData.eliminated) {
                        sender.sendMessage(Text.literal("§cThat player is also eliminated!"), false);
                        return 0;
                    }

                    senderData.invitedHelperUuid = helper.getUuid();
                    senderData.invitedHelperName = helper.getName().getString();
                    senderData.helperStartTicks = helperData.playTimeTicks;
                    PlayerDataManager.save(sender.getUuid());

                    sender.sendMessage(Text.literal("§aYou invited §e" + helper.getName().getString() + "§a! Once they play for 1 hour and have a Diamond Sword in their inventory, you will be revived at full health!"), false);
                    helper.sendMessage(Text.literal("§6§l[REVIVE QUEST] §e" + sender.getName().getString() + " §7has chosen you as their savior! Play for §f1 hour §7and obtain a §bDiamond Sword §7to revive them!"), false);
                    return 1;
                }))
        );
    }

    private LiteralArgumentBuilder<ServerCommandSource> buildMainCommand(String rootLiteral) {
        return CommandManager.literal(rootLiteral)
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                openGuiForPlayer(player);
                return 1;
            })
            .then(CommandManager.literal("balance")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    PlayerData data = PlayerDataManager.get(player);
                    double hearts = player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH) / 2.0;
                    player.sendMessage(Text.literal("§7Heart Balance: §c" + String.format("%.1f ❤", hearts) + " §8| §7Active Bounty: §6" + String.format("%.1f ❤", data.bountyHalfHearts / 2.0)), false);
                    return 1;
                }))
            .then(CommandManager.literal("withdraw")
                .then(CommandManager.argument("hearts", IntegerArgumentType.integer(1, 20))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        int amount = IntegerArgumentType.getInteger(ctx, "hearts");
                        withdrawHearts(player, amount);
                        return 1;
                    })))
            .then(CommandManager.literal("bounty")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .then(CommandManager.argument("halfHearts", IntegerArgumentType.integer(1, 40))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String target = StringArgumentType.getString(ctx, "target");
                            int halfHearts = IntegerArgumentType.getInteger(ctx, "halfHearts");
                            placeBountyHalfHearts(player, target, halfHearts);
                            return 1;
                        }))))
            .then(CommandManager.literal("leaderboard")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    JavaDialogs.sendLeaderboard(player);
                    return 1;
                }))
            .then(CommandManager.literal("settings")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    openGuiForPlayer(player);
                    return 1;
                })
                .then(CommandManager.literal("hologram")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        PlayerData data = PlayerDataManager.get(player);
                        data.showHologram = !data.showHologram;
                        PlayerDataManager.save(player.getUuid());
                        HologramManager.updatePlayerHologramState(player);
                        player.sendMessage(Text.literal("§7Overhead Heart Hologram is now " + (data.showHologram ? "§aENABLED" : "§cDISABLED")), false);
                        return 1;
                    })))
            .then(CommandManager.literal("reload")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ConfigManager.load();
                    ctx.getSource().sendMessage(Text.literal("§aReloaded programmable events from config/lifesteal_events.json without recompiling!"));
                    return 1;
                }))
            .then(CommandManager.literal("admin")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("give")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                        .then(CommandManager.argument("hearts", DoubleArgumentType.doubleArg(0.5))
                            .executes(ctx -> adminModifyHearts(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "target"),
                                DoubleArgumentType.getDouble(ctx, "hearts"),
                                "ADD"
                            )))))
                .then(CommandManager.literal("take")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                        .then(CommandManager.argument("hearts", DoubleArgumentType.doubleArg(0.5))
                            .executes(ctx -> adminModifyHearts(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "target"),
                                -DoubleArgumentType.getDouble(ctx, "hearts"),
                                "ADD"
                            )))))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                        .then(CommandManager.argument("hearts", DoubleArgumentType.doubleArg(0.5))
                            .executes(ctx -> adminModifyHearts(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "target"),
                                DoubleArgumentType.getDouble(ctx, "hearts"),
                                "SET"
                            )))))
                .then(CommandManager.literal("revive")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                            PlayerData data = PlayerDataManager.get(target);
                            ReviveManager.completeRevive(ctx.getSource().getServer(), target, data, target);
                            ctx.getSource().sendMessage(Text.literal("§aForce-revived " + target.getName().getString()));
                            return 1;
                        }))));
    }

    public static void openGuiForPlayer(ServerPlayerEntity player) {
        if (player.getName().getString().startsWith(".")) {
            if (BedrockForms.tryOpenMainMenu(player)) {
                return;
            }
        }
        JavaDialogs.openMainMenu(player);
    }

    public static void withdrawHearts(ServerPlayerEntity player, int fullHeartsToWithdraw) {
        PlayerData data = PlayerDataManager.get(player);
        if (data.eliminated) {
            player.sendMessage(Text.literal("§cYou are eliminated and cannot withdraw hearts!"), false);
            return;
        }

        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return;

        double currentHealth = attr.getBaseValue();
        double healthToRemove = fullHeartsToWithdraw * 2.0;
        double minRemainingHealth = ConfigManager.get().minHearts * 2.0;

        if (currentHealth - healthToRemove < minRemainingHealth) {
            player.sendMessage(Text.literal("§cInsufficient hearts! You must keep at least " + ConfigManager.get().minHearts + " ❤."), false);
            return;
        }

        attr.setBaseValue(currentHealth - healthToRemove);
        if (player.getHealth() > attr.getBaseValue()) {
            player.setHealth((float) attr.getBaseValue());
        }

        player.getInventory().offerOrDrop(new ItemStack(HEART_ITEM, fullHeartsToWithdraw));
        HologramManager.updatePlayerHologramState(player);
        PlayerDataManager.save(player.getUuid());

        player.sendMessage(Text.literal("§aWithdrew §c" + fullHeartsToWithdraw + " ❤ §ainto your inventory!"), false);
        ConfigManager.triggerEvent(player.getServer(), "on_withdraw", Map.of(
            "%player%", player.getName().getString(),
            "%amount%", String.valueOf(fullHeartsToWithdraw)
        ));
    }

    public static void placeBountyHalfHearts(ServerPlayerEntity sender, String targetUsername, int halfHearts) {
        PlayerData targetData = PlayerDataManager.findByName(targetUsername);
        if (targetData == null) {
            sender.sendMessage(Text.literal("§cPlayer '" + targetUsername + "' was not found!"), false);
            return;
        }

        EntityAttributeInstance attr = sender.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return;

        double currentHealth = attr.getBaseValue();
        double minHealth = ConfigManager.get().minHearts * 2.0;

        if (currentHealth - halfHearts < minHealth) {
            sender.sendMessage(Text.literal("§cYou do not have enough health to sacrifice " + (halfHearts / 2.0) + " ❤ for a bounty!"), false);
            return;
        }

        attr.setBaseValue(currentHealth - halfHearts);
        if (sender.getHealth() > attr.getBaseValue()) {
            sender.setHealth((float) attr.getBaseValue());
        }

        targetData.bountyHalfHearts += halfHearts;
        PlayerDataManager.save(targetData.uuid);
        PlayerDataManager.save(sender.getUuid());
        HologramManager.updatePlayerHologramState(sender);

        sender.sendMessage(Text.literal("§6Placed §e" + String.format("%.1f ❤", halfHearts / 2.0) + " §6bounty on §f" + targetData.username + "§6!"), false);
        sender.getServer().getPlayerManager().broadcast(
            Text.literal("§6§l[BOUNTY] §e" + sender.getName().getString() + " §7added §c" + String.format("%.1f ❤", halfHearts / 2.0) + " §7to §f" + targetData.username + "§7's bounty! (Total: §6" + String.format("%.1f ❤", targetData.bountyHalfHearts / 2.0) + "§7)"),
            false
        );
    }

    private static int adminModifyHearts(ServerCommandSource source, ServerPlayerEntity target, double heartsAmount, String mode) {
        EntityAttributeInstance attr = target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return 0;

        double newBaseHealth;
        if ("SET".equals(mode)) {
            newBaseHealth = Math.max(1.0, heartsAmount * 2.0);
        } else {
            newBaseHealth = Math.max(1.0, attr.getBaseValue() + (heartsAmount * 2.0));
        }

        attr.setBaseValue(newBaseHealth);
        if (target.getHealth() > newBaseHealth) {
            target.setHealth((float) newBaseHealth);
        } else {
            target.heal((float) newBaseHealth);
        }

        HologramManager.updatePlayerHologramState(target);
        PlayerDataManager.save(target.getUuid());

        source.sendMessage(Text.literal("§aUpdated " + target.getName().getString() + "'s hearts to §c" + String.format("%.1f ❤", newBaseHealth / 2.0)));
        return 1;
    }
}
