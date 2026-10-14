package com.ohhapple.monster_conquest.network.serverbound.backpack;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.menu.BackpackMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：按 B 键（按键可改）打开战术装备栏里的背包。 */
public record OpenEquippedBackpackPayload() implements CustomPacketPayload {

    public static final Type<OpenEquippedBackpackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "open_equipped_backpack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEquippedBackpackPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenEquippedBackpackPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenEquippedBackpackPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            BackpackMenu.openEquipped(player);
        });
    }
}
