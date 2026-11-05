package com.ohhapple.monster_conquest.network.serverbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.ArenaLobbyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** 客户端 → 服务端：大厅生命周期与房间操作。 */
public record ArenaActionPayload(
        int action, int roomId, UUID targetId, int targetIndex)
        implements CustomPacketPayload {

    public static final int OPEN = 0;
    public static final int CLOSE = 1;
    public static final int CREATE = 2;
    public static final int JOIN = 3;
    public static final int LEAVE = 4;
    public static final int SET_READY = 5;
    public static final int TOGGLE_AUTO_FILL = 6;
    public static final int KICK = 7;
    public static final int START = 8;
    public static final int CHANGE_SEAT = 9;
    public static final int VIEW_CAREER = 10;
    public static final int SPECTATE = 11;

    public static final UUID NO_TARGET = new UUID(0L, 0L);

    public ArenaActionPayload(int action, int roomId, UUID targetId) {
        this(action, roomId, targetId, -1);
    }

    public static final Type<ArenaActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaActionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ArenaActionPayload decode(RegistryFriendlyByteBuf buf) {
                    return new ArenaActionPayload(
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readUUID(),
                            buf.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ArenaActionPayload payload) {
                    buf.writeVarInt(payload.action());
                    buf.writeVarInt(payload.roomId());
                    buf.writeUUID(payload.targetId());
                    buf.writeVarInt(payload.targetIndex());
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArenaActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                ArenaLobbyManager.handleAction(
                        player, payload.action(), payload.roomId(),
                        payload.targetId(), payload.targetIndex());
            }
        });
    }
}
