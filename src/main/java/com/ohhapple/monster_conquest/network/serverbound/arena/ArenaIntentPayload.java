package com.ohhapple.monster_conquest.network.serverbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.game.ArenaMatchManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** 客户端 → 服务端：商店、经验和棋子位置操作。 */
public record ArenaIntentPayload(
        int action,
        int targetType,
        int targetIndex,
        UUID pieceId) implements CustomPacketPayload {

    public static final int OPEN = 0;
    public static final int BUY = 1;
    public static final int REFRESH = 2;
    public static final int BUY_XP = 3;
    public static final int MOVE = 4;
    public static final int CLOSE = 5;
    public static final int SELL = 6;
    public static final int SURRENDER = 7;
    public static final int EXIT_MATCH = 8;
    public static final int SPECTATE = 9;
    public static final int RETURN_TO_BOARD = 10;
    public static final int EQUIP = 11;
    public static final int COMBINE_EQUIPMENT = 12;
    public static final int DISASSEMBLE = 13;
    public static final int MOVE_EQUIPMENT = 14;
    public static final int DUPLICATE = 15;
    public static final int SWAP_EQUIPMENT = 16;

    public static final int BOARD = 0;
    public static final int BENCH = 1;

    public static final Type<ArenaIntentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_intent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaIntentPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ArenaIntentPayload decode(RegistryFriendlyByteBuf buf) {
                    return new ArenaIntentPayload(
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readUUID());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ArenaIntentPayload payload) {
                    buf.writeVarInt(payload.action());
                    buf.writeVarInt(payload.targetType());
                    buf.writeVarInt(payload.targetIndex());
                    buf.writeUUID(payload.pieceId());
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArenaIntentPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                ArenaMatchManager.handleIntent(player, payload);
            }
        });
    }
}
