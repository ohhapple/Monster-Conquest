package com.ohhapple.monster_conquest.player;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家战马（马哨）：右键或 R 轮盘召唤并骑乘；骑乘期间战马无敌、无伤害传导，
 *   但被打时照常传播仇恨；下马后战马立即销毁。马哨放在战术装备栏可用轮盘控制上下马。
 */
public final class PlayerMountUtil {

    private PlayerMountUtil() {}

    /** 战术装备栏里是否有马哨。 */
    public static boolean hasWhistle(ServerPlayer player) {
        for (ItemStack stack : TacticalEquipmentSlotsData.get(player)) {
            if (stack.is(Monster_conquest.HORSE_WHISTLE.get())) return true;
        }
        return false;
    }

    /** 玩家当前骑乘的玩家战马（没骑 / 骑的不是战马时返回 null）。 */
    @Nullable
    public static WarHorseEntity mountedHorse(ServerPlayer player) {
        return player.getVehicle() instanceof WarHorseEntity horse ? horse : null;
    }

    /**
     * 召唤并骑乘战马。
     *
     * @param requireWhistle R 轮盘指令要求"马哨在战术装备栏"；手持右键不要求
     */
    public static boolean mount(ServerPlayer player, boolean requireWhistle) {
        if (player.isPassenger()) return false;
        if (requireWhistle && !hasWhistle(player)) return false;
        if (!(player.level() instanceof ServerLevel sl)) return false;

        WarHorseEntity horse = new WarHorseEntity(ModEntities.WAR_HORSE.get(), sl);
        horse.setPos(player.getX(), player.getY(), player.getZ());
        horse.setOwner(player);
        horse.setPlayerMount(true);
        // 给马装上鞍：原版马的"可否操控"由鞍槽（inventory slot 0）决定，
        //   没有鞍时玩家坐上去也无法控制（只会原地被甩下 / 不动）。
        horse.getInventory().setItem(AbstractHorse.INV_SLOT_SADDLE,
                new ItemStack(Items.SADDLE));
        horse.setYRot(player.getYRot());
        horse.yRotO = player.getYRot();
        horse.yBodyRot = player.getYRot();
        horse.yBodyRotO = player.getYRot();
        horse.setYHeadRot(player.getYRot());
        horse.yHeadRotO = player.getYRot();
        sl.addFreshEntity(horse);
        player.startRiding(horse, true);
        return true;
    }

    /** 下马并立即销毁战马。 */
    public static boolean dismount(ServerPlayer player) {
        WarHorseEntity horse = mountedHorse(player);
        if (horse == null) return false;
        player.stopRiding();
        horse.discard();
        return true;
    }
}
