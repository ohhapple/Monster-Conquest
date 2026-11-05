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

/** 集成主世界不能接收 transfer，结束后由客户端重开原世界。 */
public record ArenaReturnToWorldPayload(String levelName)
        implements CustomPacketPayload {

    public static final Type<ArenaReturnToWorldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_return_to_world"));

    public static final StreamCodec<RegistryFriendlyByteBuf,
            ArenaReturnToWorldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ArenaReturnToWorldPayload::levelName,
            ArenaReturnToWorldPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArenaReturnToWorldPayload payload,
                              IPayloadContext context) {
        ClientboundPayloadRouter.dispatch(payload, context);
    }
}
