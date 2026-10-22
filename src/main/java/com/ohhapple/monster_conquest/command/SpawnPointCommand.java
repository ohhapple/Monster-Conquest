package com.ohhapple.monster_conquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.block.SpawnPointGlobal;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 刷怪点全局开关指令：
 *   /spawnpoints            查看当前状态
 *   /spawnpoints on | off   允许 / 禁止所有刷怪点刷怪
 *   /spawnpoints toggle     切换
 * 只决定"刷不刷"，不动各刷怪点自己的配置与开关；界面里的开关会一起显示全局状态。
 */
public class SpawnPointCommand {

    /** 挂到 /monsterc 根节点下面（由 FactionCommand 统一注册根指令）。 */
    public static void attach(LiteralCommandNode<CommandSourceStack> root) {
        root.addChild(Commands.literal("spawnpoints")
                .requires(source -> source.hasPermission(2))
                .executes(SpawnPointCommand::report)
                .then(Commands.literal("on")
                        .executes(ctx -> apply(ctx, false)))
                .then(Commands.literal("off")
                        .executes(ctx -> apply(ctx, true)))
                .then(Commands.literal("toggle")
                        .executes(ctx -> apply(ctx, !SpawnPointGlobal.isDisabled())))
                .build());
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, boolean disabled) {
        SpawnPointGlobal.setDisabled(ctx.getSource().getServer(), disabled);
        ctx.getSource().sendSuccess(() -> Component.translatable(disabled
                ? "command.monster_conquest.spawnpoints.disabled"
                : "command.monster_conquest.spawnpoints.enabled"), true);
        return disabled ? 0 : 1;
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        boolean disabled = SpawnPointGlobal.isDisabled();
        ctx.getSource().sendSuccess(() -> Component.translatable(disabled
                ? "command.monster_conquest.spawnpoints.status_off"
                : "command.monster_conquest.spawnpoints.status_on"), false);
        return disabled ? 0 : 1;
    }
}
