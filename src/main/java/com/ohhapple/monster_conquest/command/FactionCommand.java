package com.ohhapple.monster_conquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.authlib.GameProfile;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.ArenaStatsData;
import com.ohhapple.monster_conquest.arena.ArenaLobbyManager;
import com.ohhapple.monster_conquest.arena.external.ArenaRuntime;
import com.ohhapple.monster_conquest.arena.external.ExternalArenaMainCoordinator;
import com.ohhapple.monster_conquest.network.FactionGuiNetwork;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;

@EventBusSubscriber(modid = Monster_conquest.MODID)
public class FactionCommand {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                event.getDispatcher();
        var rootBuilder = Commands.literal("monsterc")
                .then(Commands.literal("faction")
                        .then(Commands.literal("gui")
                                .executes(FactionCommand::openGui)))
                .then(Commands.literal("arena")
                        .then(Commands.literal("shutdown")
                                .requires(source ->
                                        source.hasPermission(4))
                                .executes(FactionCommand::
                                        shutdownArenaChild))
                        .then(Commands.literal("history")
                                .requires(source ->
                                        source.hasPermission(2))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("player",
                                                        GameProfileArgument.gameProfile())
                                                .executes(FactionCommand::
                                                        clearArenaHistory)))));
        LiteralCommandNode<CommandSourceStack> root =
                dispatcher.register(rootBuilder);
        SpawnPointCommand.attach(root);
    }

    private static int openGui(
            CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity()
                instanceof ServerPlayer player)) {
            return 0;
        }
        FactionGuiNetwork.sendSnapshot(player);
        PacketDistributor.sendToPlayer(
                player, new OpenFactionGuiPayload());
        return 1;
    }

    private static int shutdownArenaChild(
            CommandContext<CommandSourceStack> context) {
        if (ArenaRuntime.isArenaServer()) {
            context.getSource().sendFailure(
                    Component.translatable(
                            "command.monster_conquest.arena.shutdown.main_only"));
            return 0;
        }
        boolean running =
                ExternalArenaMainCoordinator.forceStopChildServer();
        context.getSource().sendSuccess(
                () -> Component.translatable(running
                        ? "command.monster_conquest.arena.shutdown.stopped"
                        : "command.monster_conquest.arena.shutdown.not_running"),
                true);
        return 1;
    }

    private static int clearArenaHistory(
            CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<GameProfile> profiles =
                GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            context.getSource().sendFailure(Component.translatable(
                    "command.monster_conquest.arena.history.not_found"));
            return 0;
        }
        GameProfile profile = profiles.iterator().next();
        ArenaStatsData data = ArenaStatsData.get(
                context.getSource().getServer());
        boolean cleared = data.clearCareer(profile.getId());
        ServerPlayer online = context.getSource().getServer()
                .getPlayerList().getPlayer(profile.getId());
        if (online != null) ArenaLobbyManager.sendSnapshot(online);
        context.getSource().sendSuccess(
                () -> Component.translatable(cleared
                                ? "command.monster_conquest.arena.history.cleared"
                                : "command.monster_conquest.arena.history.empty",
                        profile.getName()), true);
        return cleared ? 1 : 0;
    }

}
