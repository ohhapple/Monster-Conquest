package com.ohhapple.monster_conquest.event;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.item.ControlToolItem;
import com.ohhapple.monster_conquest.item.HatredWandItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.menu.UnitMenu;
import com.ohhapple.monster_conquest.util.UnitAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 蹲下右键单位：打开单位界面（士兵与首领 / 巡逻队长都支持）。
 *   · 手里拿着仇恨权杖 / 收纳工具 / 控制工具时保持这些工具自己的右键行为，不抢操作；
 *   · 生存玩家只能打开自己玩家阵营（自己的）单位，创造模式 / OP 不限；
 *   · 蹲下右键骑兵的战马 = 打开骑它那个单位的界面。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public final class UnitInteractionEvents {

    /** 打开界面的最大距离，避免隔墙点远处单位。 */
    private static final double MAX_DISTANCE_SQ = 64.0;

    private UnitInteractionEvents() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        Player player = event.getEntity();
        if (!player.isSecondaryUseActive()) return;           // 要求蹲下
        if (isTool(event.getItemStack())) return;              // 工具优先

        ManagedUnit unit = resolveUnit(event.getTarget());
        if (unit == null) return;
        if (player.distanceToSqr(unit.asEntity()) > MAX_DISTANCE_SQ) return;

        if (!UnitAccess.canOpen(player, unit)) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("msg.monster_conquest.unit.not_yours"), true);
            }
            cancel(event);
            return;
        }

        if (player instanceof ServerPlayer sp) openUnit(sp, unit);
        cancel(event);
    }

    /** 打开某个单位的界面（服务端）。 */
    public static void openUnit(ServerPlayer player, ManagedUnit unit) {
        player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new UnitMenu(id, inv, unit),
                        Component.translatable("gui.monster_conquest.unit")),
                buf -> buf.writeVarInt(unit.asEntity().getId()));
    }

    private static void cancel(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof HatredWandItem
                || stack.getItem() instanceof RetrievalToolItem
                || stack.getItem() instanceof ControlToolItem;
    }

    /** 右键目标解析：单位本身（士兵 / 首领）；骑兵战马则取骑它的那个单位。 */
    @Nullable
    private static ManagedUnit resolveUnit(Entity target) {
        if (target instanceof ManagedUnit unit) return unit;
        if (target instanceof WarHorseEntity horse
                && horse.getFirstPassenger() instanceof ManagedUnit rider) return rider;
        return null;
    }
}
