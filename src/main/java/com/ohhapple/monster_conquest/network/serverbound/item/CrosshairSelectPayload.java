package com.ohhapple.monster_conquest.network.serverbound.item;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.ControlToolItem;
import com.ohhapple.monster_conquest.item.HatredWandItem;
import com.ohhapple.monster_conquest.util.RaycastUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record CrosshairSelectPayload() implements CustomPacketPayload {

    public static final Type<CrosshairSelectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "crosshair_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrosshairSelectPayload> STREAM_CODEC =
            StreamCodec.unit(new CrosshairSelectPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CrosshairSelectPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();

            // 控制工具 + 蹲下 + 中键 → 循环"目标小队号"
            if (stack.getItem() instanceof ControlToolItem && player.isShiftKeyDown()) {
                var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .get(player.getUUID());
                int max = Math.max(1, com.ohhapple.monster_conquest.config.PlayerConfig.maxPlayerSquads);
                int cur = state.getControlToolSelectedSquad();
                // -1（单兵选择）→ 从 1 分队开始循环；0（全选）在队号之后
                int next = (cur < 0) ? 1 : cur + 1;
                if (next > max) next = 0;

                state.setControlToolSelectedSquad(next);

                net.minecraft.network.chat.Component msg = (next == 0)
                        ? net.minecraft.network.chat.Component.translatable(
                        "msg.monster_conquest.control_tool.squad.default")
                        : net.minecraft.network.chat.Component.translatable(
                        "msg.monster_conquest.control_tool.squad.set", next);
                player.displayClientMessage(msg, true);
                return;
            }

            if (stack.getItem() instanceof HatredWandItem wand) {
                Entity target = RaycastUtil.raycastEntity(
                        player, RaycastUtil.DEFAULT_MAX_DISTANCE,
                        HatredWandItem::isValidTarget);
                if (target != null) {
                    wand.trySelect(player, target, stack);
                    // 同步给客户端
                    player.inventoryMenu.broadcastChanges();
                }
            } else if (stack.getItem() instanceof ControlToolItem ctrl) {
                Entity target = RaycastUtil.raycastEntity(
                        player, RaycastUtil.DEFAULT_MAX_DISTANCE,
                        e -> e instanceof LivingEntity);
                if (target != null) {
                    ctrl.trySelect(player, target, stack);
                    // 同步给客户端
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
    }
}
