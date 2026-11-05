package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.ClientboundPayloadRouter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 集成单人客户端无法使用原版 transfer，因此主动连接本地子服。 */
public record ArenaConnectToChildPayload(String host, int port)
        implements CustomPacketPayload {

    public static final Type<ArenaConnectToChildPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_connect_to_child"));

    public static final StreamCodec<RegistryFriendlyByteBuf,
            ArenaConnectToChildPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ArenaConnectToChildPayload::host,
            ByteBufCodecs.VAR_INT, ArenaConnectToChildPayload::port,
            ArenaConnectToChildPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArenaConnectToChildPayload payload,
                              IPayloadContext context) {
        ClientboundPayloadRouter.dispatch(payload, context);
    }
}
