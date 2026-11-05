package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** 只发送给操作者的非空间化竞技界面音效，不广播给附近玩家。 */
public record ArenaSoundPayload(int cue) implements CustomPacketPayload {

    public static final int PURCHASE_SUCCESS = 0;
    public static final int EQUIPMENT_SUCCESS = 1;
    public static final int UPGRADE_SUCCESS = 2;
    public static final int SELL_SUCCESS = 3;
    public static final int COUNTDOWN_BELL_THREE = 4;
    public static final int COUNTDOWN_BELL_TWO = 5;
    public static final int COUNTDOWN_BELL_ONE = 6;
    public static final int BATTLE_VICTORY = 7;
    public static final int BATTLE_DEFEAT = 8;
    public static final int UNIT_MOVE_SUCCESS = 9;
    public static final int EQUIPMENT_MOVE_SUCCESS = 10;
    public static final int EQUIPMENT_COMBINE_SUCCESS = 11;

    public static final Type<ArenaSoundPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf,
            ArenaSoundPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArenaSoundPayload::cue,
            ArenaSoundPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
