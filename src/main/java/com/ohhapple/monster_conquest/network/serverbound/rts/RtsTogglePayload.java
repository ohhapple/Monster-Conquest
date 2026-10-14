package com.ohhapple.monster_conquest.network.serverbound.rts;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：进入 / 退出 RTS 模式。 */
public record RtsTogglePayload(boolean active) implements CustomPacketPayload {

    public static final Type<RtsTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "rts_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsTogglePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, RtsTogglePayload::active,
                    RtsTogglePayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RtsTogglePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var state = PlayerArmyManager.get(player.getUUID());
            state.setRtsActive(payload.active());
            // 选中状态跨"退出 / 再进入"保留：进出时都同步一次，
            //   让客户端的绿框 / 黄框与服务端完全一致（顺带清掉已阵亡单位的框），
            //   否则退出后界面还显示旧框，但再下令已经作用不到那些单位了。
            PacketDistributor.sendToPlayer(player, new SyncRtsSelectionPayload(
                    state.collectRtsSelectedSoldierUuids(player),
                    state.getRtsMoveMarker()));
        });
    }
}
