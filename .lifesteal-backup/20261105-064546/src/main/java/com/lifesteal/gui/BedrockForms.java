package com.lifesteal.gui;

import com.lifesteal.LifestealMod;
import com.lifesteal.data.PlayerData;
import com.lifesteal.data.PlayerDataManager;
import com.lifesteal.hologram.HologramManager;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.Comparator;
import java.util.List;

public class BedrockForms {

    public static boolean tryOpenMainMenu(ServerPlayerEntity player) {
        try {
            FloodgatePlayer fgPlayer = FloodgateApi.getInstance().getPlayer(player.getUuid());
            if (fgPlayer == null) {
                return false;
            }
            openDashboardForm(player, fgPlayer);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void openDashboardForm(ServerPlayerEntity player, FloodgatePlayer fgPlayer) {
        PlayerData data = PlayerDataManager.get(player);
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        double hearts = attr != null ? attr.getBaseValue() / 2.0 : 10.0;

        SimpleForm form = SimpleForm.builder()
            .title("§c❤ Lifesteal Control Panel")
            .content(
                "§7Account: §f" + player.getName().getString() + "\n" +
                "§7Balance: §c" + String.format("%.1f", hearts) + " ❤\n" +
                "§7Bounty on You: §6" + String.format("%.1f", data.bountyHalfHearts / 2.0) + " ❤\n" +
                "§7Total Kills: §a" + data.kills + " §8| §7Playtime: §b" + data.getFormattedPlaytime()
            )
            .button("§cWithdraw Hearts")
            .button("§6Place Half-Heart Bounty")
            .button("§bLeaderboard & Stats")
            .button("§eSettings (Hologram)")
            .button("§8Close")
            .validResultHandler(response -> {
                switch (response.clickedButtonId()) {
                    case 0 -> openWithdrawForm(player, fgPlayer);
                    case 1 -> openBountyForm(player, fgPlayer);
                    case 2 -> openLeaderboardForm(player, fgPlayer);
                    case 3 -> openSettingsForm(player, fgPlayer);
                }
            })
            .build();

        fgPlayer.sendForm(form);
    }

    private static void openWithdrawForm(ServerPlayerEntity player, FloodgatePlayer fgPlayer) {
        CustomForm form = CustomForm.builder()
            .title("§cWithdraw Hearts")
            .slider("Full Hearts to Withdraw", 1, 15, 1, 1)
            .validResultHandler(response -> {
                int amount = Math.round(response.asSlider(0));
                LifestealMod.withdrawHearts(player, amount);
            })
            .build();

        fgPlayer.sendForm(form);
    }

    private static void openBountyForm(ServerPlayerEntity player, FloodgatePlayer fgPlayer) {
        CustomForm form = CustomForm.builder()
            .title("§6Place Bounty (Half-Hearts)")
            .input("Target Player Username", "Enter exact player name")
            .slider("Half-Hearts to Sacrifice (1 = 0.5❤)", 1, 20, 1, 1)
            .validResultHandler(response -> {
                String targetName = response.asInput(0);
                int halfHearts = Math.round(response.asSlider(1));
                LifestealMod.placeBountyHalfHearts(player, targetName, halfHearts);
            })
            .build();

        fgPlayer.sendForm(form);
    }

    private static void openLeaderboardForm(ServerPlayerEntity player, FloodgatePlayer fgPlayer) {
        StringBuilder sb = new StringBuilder("§6§lTOP LIFESTEAL PLAYERS\n\n");
        List<PlayerData> top = PlayerDataManager.getAll().stream()
            .sorted(Comparator.comparingInt((PlayerData d) -> d.kills).reversed())
            .limit(10)
            .toList();

        int rank = 1;
        for (PlayerData d : top) {
            String killers = d.killedByHistory.isEmpty() ? "Nobody" : String.join(", ", d.killedByHistory);
            sb.append("§e#").append(rank++).append(" §f").append(d.username)
              .append("\n  §7Hearts: §c").append(String.format("%.1f❤", d.currentHearts))
              .append(" §8| §7Kills: §a").append(d.kills)
              .append("\n  §7Bounty: §6").append(String.format("%.1f❤", d.bountyHalfHearts / 2.0))
              .append(" §8| §7Time: §b").append(d.getFormattedPlaytime())
              .append("\n  §7Killed By: §c").append(killers).append("\n\n");
        }

        SimpleForm form = SimpleForm.builder()
            .title("§6★ Lifesteal Leaderboard ★")
            .content(sb.toString())
            .button("§8Back to Menu")
            .validResultHandler(r -> openDashboardForm(player, fgPlayer))
            .build();

        fgPlayer.sendForm(form);
    }

    private static void openSettingsForm(ServerPlayerEntity player, FloodgatePlayer fgPlayer) {
        PlayerData data = PlayerDataManager.get(player);

        CustomForm form = CustomForm.builder()
            .title("§ePlayer Settings")
            .toggle("Display Heart Hologram Above Head", data.showHologram)
            .validResultHandler(response -> {
                data.showHologram = response.asToggle(0);
                PlayerDataManager.save(player.getUuid());
                HologramManager.updatePlayerHologramState(player);
                player.sendMessage(Text.literal("§aSettings updated! Overhead Hologram: " + (data.showHologram ? "§2ON" : "§cOFF")), false);
            })
            .build();

        fgPlayer.sendForm(form);
    }
}
