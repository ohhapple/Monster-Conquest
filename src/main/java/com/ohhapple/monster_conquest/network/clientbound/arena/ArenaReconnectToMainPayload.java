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

/** Reconnects a LAN guest to the integrated main world after an arena match. */
public record ArenaReconnectToMainPayload(String host, int port)
        implements CustomPacketPayload {

    public static final Type<ArenaReconnectToMainPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_reconnect_to_main"));

    public static final StreamCodec<RegistryFriendlyByteBuf,
            ArenaReconnectToMainPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ArenaReconnectToMainPayload::host,
            ByteBufCodecs.VAR_INT, ArenaReconnectToMainPayload::port,
            ArenaReconnectToMainPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArenaReconnectToMainPayload payload,
                              IPayloadContext context) {
        ClientboundPayloadRouter.dispatch(payload, context);
    }
}
