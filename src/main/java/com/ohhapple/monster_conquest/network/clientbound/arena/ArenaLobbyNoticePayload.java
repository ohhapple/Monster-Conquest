package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** 服务端只向操作者或受影响玩家发送的大厅提示，不进入共享房间快照。 */
public record ArenaLobbyNoticePayload(String translationKey)
        implements CustomPacketPayload {

    public static final Type<ArenaLobbyNoticePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_lobby_notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaLobbyNoticePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ArenaLobbyNoticePayload::translationKey,
                    ArenaLobbyNoticePayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
