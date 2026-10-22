package com.ohhapple.monster_conquest.network.serverbound.spawn;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.SpawnPointBlockItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：左键手持的刷怪点物品 → 打开配置界面（仅创造模式）。 */
public record OpenSpawnPointEditorPayload() implements CustomPacketPayload {

    public static final Type<OpenSpawnPointEditorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "open_spawn_point_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpawnPointEditorPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenSpawnPointEditorPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenSpawnPointEditorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SpawnPointBlockItem.openEditor(player, InteractionHand.MAIN_HAND);
            }
        });
    }
}
