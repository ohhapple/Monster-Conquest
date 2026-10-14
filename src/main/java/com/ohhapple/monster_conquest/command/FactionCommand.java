package com.ohhapple.monster_conquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.FactionGuiNetwork;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Monster_conquest.MODID)
public class FactionCommand {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("faction")
                .then(Commands.literal("gui")
                        .executes(FactionCommand::openGui))
        );
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        FactionGuiNetwork.sendSnapshot(player);
        PacketDistributor.sendToPlayer(player, new OpenFactionGuiPayload());
        return 1;
    }
}
