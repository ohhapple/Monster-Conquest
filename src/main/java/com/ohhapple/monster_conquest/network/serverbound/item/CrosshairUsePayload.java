package com.ohhapple.monster_conquest.network.serverbound.item;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.item.ControlToolItem;
import com.ohhapple.monster_conquest.item.HatredWandItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.util.RaycastUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：右键使用工具。
 *  - 仇恨权杖：raycast 实体 → tryMarkHostile
 *  - 收纳工具：raycast 实体 → tryRetrieve
 *  - 控制工具：优先 raycast 敌方实体 → attackTarget；
 *              低头且无实体命中 → commandFollowHere；
 *              否则 raycast 方块 → goTo
 */
public record CrosshairUsePayload() implements CustomPacketPayload {

    public static final Type<CrosshairUsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "crosshair_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrosshairUsePayload> STREAM_CODEC =
            StreamCodec.unit(new CrosshairUsePayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CrosshairUsePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();

            if (stack.getItem() instanceof HatredWandItem wand) {
                Entity target = RaycastUtil.raycastEntity(
                        player, RaycastUtil.DEFAULT_MAX_DISTANCE,
                        HatredWandItem::isValidTarget);
                if (target != null) wand.tryMarkHostile(player, target, stack);
            } else if (stack.getItem() instanceof RetrievalToolItem tool) {
                Entity target = RaycastUtil.raycastEntity(
                        player, RaycastUtil.DEFAULT_MAX_DISTANCE,
                        e -> e instanceof AbstractArmySoldier
                                || e instanceof AbstractArmyCommander
                                || e instanceof WarHorseEntity);
                if (target != null) tool.tryRetrieve(player, target);
            } else if (stack.getItem() instanceof ControlToolItem ctrl) {
                // 1) 优先射线实体 → attackTarget
                //    排除自己正骑着的战马：骑在马上低头右键脚下时，射线会先打到自己骑的马，
                //      不排除的话会被当成"攻击命令"，永远走不到下面的"跟随"分支。
                Entity target = RaycastUtil.raycastEntity(
                        player, RaycastUtil.DEFAULT_MAX_DISTANCE,
                        e -> e instanceof LivingEntity && !(e instanceof Player)
                                && e != player.getVehicle());
                if (target instanceof LivingEntity le) {
                    ctrl.attackTarget(player, le, stack);
                    return;
                }

                // 2) 准星正对脚底 → 命令当前选中小队跟随
                var look = player.getLookAngle();
                double horizSqr = look.x * look.x + look.z * look.z;
                if (look.y < 0 && horizSqr < 0.0001) {
                    ctrl.commandFollowHere(player, stack);
                    return;
                }

                // 3) 其余 → raycast 方块 → 前往
                //    没瞄到方块（对着天空 / 远处地形之外）时用准星前方 16 格兜底，
                //    保证"前往地点"永远能下达（与轮盘的"前往准星地点"一致）
                BlockPos pos = RaycastUtil.raycastBlock(
                        player, RaycastUtil.DEFAULT_MAX_DISTANCE);
                if (pos != null) {
                    ctrl.goTo(player, pos, stack);
                } else {
                    ctrl.goTo(player,
                            player.getEyePosition().add(player.getLookAngle().scale(16.0)),
                            stack);
                }
            }
        });
    }
}
