package com.ohhapple.monster_conquest.network.serverbound.path;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.PathToolItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：中键选中准星指着的单位 / 军队 / 小编队，或刷怪点方块。 */
public record PathToolSelectPayload() implements CustomPacketPayload {

    public static final Type<PathToolSelectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "path_tool_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PathToolSelectPayload> STREAM_CODEC =
            StreamCodec.unit(new PathToolSelectPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PathToolSelectPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.getMainHandItem().getItem() instanceof PathToolItem) {
                PathToolItem.select(player, player.getMainHandItem());
            }
        });
    }
}
