package com.ohhapple.monster_conquest.network.serverbound.spawn;

import com.ohhapple.monster_conquest.item.PathToolItem;
import com.ohhapple.monster_conquest.menu.SpawnPointMenu;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：修改刷怪点界面里的一项配置（动作 + 取值）。
 *   不带来源坐标：只作用于玩家当前打开的那个刷怪点界面（方块刷怪点或手持的刷怪点物品），
 *   由菜单负责校验、写配置与保存。
 */
public record SpawnPointConfigPayload(int action, int value) implements CustomPacketPayload {

    public static final Type<SpawnPointConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "spawn_point_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnPointConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpawnPointConfigPayload::action,
                    ByteBufCodecs.VAR_INT, SpawnPointConfigPayload::value,
                    SpawnPointConfigPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpawnPointConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof SpawnPointMenu menu) {
                menu.applyAction(payload.action(), payload.value());
                // 界面里的「清空」按钮会改刷怪点路径：拿着路径点工具时同步刷新世界里的预览
                PathToolItem.syncPreview(player);
            }
        });
    }
}
