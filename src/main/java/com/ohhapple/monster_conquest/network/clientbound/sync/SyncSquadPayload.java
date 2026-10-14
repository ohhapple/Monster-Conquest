package com.ohhapple.monster_conquest.network.clientbound.sync;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端 → 客户端：通知"当前选中的小分队已变更"，用于轮盘同步显示。
 *
 * 本类的 handle 方法体不引用任何 net.minecraft.client.* 类。
 *   实际客户端逻辑由 ClientPayloadDispatcher → CommandWheelScreen.onServerSyncSquad 处理。
 */
public record SyncSquadPayload(int squad) implements CustomPacketPayload {

    public static final Type<SyncSquadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "sync_squad"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSquadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncSquadPayload::squad,
                    SyncSquadPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    /**
     * 占位方法。实际处理见：
     *   ClientboundPayloadRouter.dispatch → ClientPayloadDispatcher.dispatch
     *   → CommandWheelScreen.onServerSyncSquad
     */
    public static void handle(SyncSquadPayload payload, IPayloadContext ctx) {
        // 空实现，仅供签名占位。
    }
}
