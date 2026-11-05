package com.ohhapple.monster_conquest.network;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 配置阶段的只读版本提示。该载荷是可选通道，不参与兼容性准入。
 */
public record ModVersionHandshakePayload(String version)
        implements CustomPacketPayload {

    public static final Type<ModVersionHandshakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "mod_version"));

    public static final StreamCodec<
            FriendlyByteBuf, ModVersionHandshakePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) ->
                            buffer.writeUtf(payload.version(), 128),
                    buffer -> new ModVersionHandshakePayload(
                            buffer.readUtf(128)));

    public ModVersionHandshakePayload {
        version = version == null ? "unknown" : version;
    }

    public static void handle(ModVersionHandshakePayload payload,
                              IPayloadContext context) {
        ClientboundPayloadRouter.dispatch(payload, context);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
