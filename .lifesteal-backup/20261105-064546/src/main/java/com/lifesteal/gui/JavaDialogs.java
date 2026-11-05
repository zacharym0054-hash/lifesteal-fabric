package com.lifesteal.gui;

import com.lifesteal.data.PlayerData;
import com.lifesteal.data.PlayerDataManager;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;

public class JavaDialogs {

    public static void openMainMenu(ServerPlayerEntity player) {
        PlayerData data = PlayerDataManager.get(player);
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        double hearts = attr != null ? attr.getBaseValue() / 2.0 : 10.0;

        MutableText dialog = Text.literal("\n§8§m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n")
            .append(Text.literal("       §c§l❤ LIFESTEAL DIALOG UI ❤\n\n"))
            .append(Text.literal("  §7Player: §f" + player.getName().getString() + "\n"))
            .append(Text.literal("  §7Heart Balance: §c" + String.format("%.1f ❤", hearts) + "\n"))
            .append(Text.literal("  §7Active Bounty: §6" + String.format("%.1f ❤", data.bountyHalfHearts / 2.0) + "\n"))
            .append(Text.literal("  §7Kills: §a" + data.kills + " §8| §7Playtime: §b" + data.getFormattedPlaytime() + "\n"))
            .append(Text.literal("  §7Head Hologram: " + (data.showHologram ? "§a✔ Enabled" : "§c✖ Disabled") + "\n\n  "))
            .append(actionBtn("§c[Withdraw 1❤]", "/lf withdraw 1", "§7Convert 1 heart into an item"))
            .append(Text.literal(" "))
            .append(actionBtn("§c[Withdraw 2❤]", "/lf withdraw 2", "§7Convert 2 hearts into items"))
            .append(Text.literal(" "))
            .append(suggestBtn("§6[Add Bounty]", "/lf bounty <player> <halfHearts>", "§7Click to prefill bounty command"))
            .append(Text.literal("\n\n  "))
            .append(actionBtn("§b[★ Leaderboard]", "/lf leaderboard", "§7Open stylized player rankings"))
            .append(Text.literal(" "))
            .append(actionBtn("§e[⚙ Settings: Toggle Hologram]", "/lf settings hologram", "§7Enable or disable overhead hearts"))
            .append(Text.literal("\n§8§m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n"));

        player.sendMessage(dialog, false);
    }

    public static void sendLeaderboard(ServerPlayerEntity player) {
        MutableText board = Text.literal("\n§8§m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯§r §6§l★ LIFESTEAL LEADERBOARD ★ §8§m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n\n");

        List<PlayerData> sorted = PlayerDataManager.getAll().stream()
            .sorted(Comparator.comparingInt((PlayerData d) -> d.kills).reversed()
                .thenComparing(Comparator.comparingDouble((PlayerData d) -> d.currentHearts).reversed()))
            .limit(10)
            .toList();

        int rank = 1;
        for (PlayerData d : sorted) {
            String killers = d.killedByHistory.isEmpty() ? "None" : String.join(", ", d.killedByHistory);
            MutableText line = Text.literal(" §6#" + rank++ + " §f§l" + d.username + "\n")
                .append(Text.literal("    §7Hearts: §c" + String.format("%.1f❤", d.currentHearts) +
                    " §8| §7Kills: §a" + d.kills +
                    " §8| §7Bounty: §6" + String.format("%.1f❤", d.bountyHalfHearts / 2.0) +
                    " §8| §7Playtime: §b" + d.getFormattedPlaytime() + "\n"))
                .append(Text.literal("    §7Killed By: §c" + killers + "\n\n"));
            board.append(line);
        }

        board.append(Text.literal("§8§m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n"));
        player.sendMessage(board, false);
    }

    private static MutableText actionBtn(String title, String cmd, String hover) {
        return Text.literal(title).styled(style -> style
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover)))
        );
    }

    private static MutableText suggestBtn(String title, String cmd, String hover) {
        return Text.literal(title).styled(style -> style
            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover)))
        );
    }
}
