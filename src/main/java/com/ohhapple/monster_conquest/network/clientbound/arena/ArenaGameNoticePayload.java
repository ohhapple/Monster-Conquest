package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** 自走棋操作界面的一次性提示。 */
public record ArenaGameNoticePayload(String translationKey)
        implements CustomPacketPayload {

    public static final Type<ArenaGameNoticePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_game_notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaGameNoticePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ArenaGameNoticePayload::translationKey,
                    ArenaGameNoticePayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
