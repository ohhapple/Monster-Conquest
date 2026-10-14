package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.entity.ManagedUnit;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端：某个单位（士兵 / 首领）的手动装备（6 格）+ 战术装备（18 格）包装成 Container，供菜单槽位读写。
 *   · 手动装备为空 = 该部位使用单位自带装备（自带装备本身不进容器，界面里看不到）；
 *   · 写入 / 清空会立刻作用到单位身上，清空时还原自带装备。
 */
public class UnitEquipmentContainer extends SimpleContainer {

    /** 装备槽 + 战术装备槽总数。 */
    public static final int TOTAL = ManagedUnit.GEAR_SLOTS + ManagedUnit.TACTICAL_SLOTS;

    private final ManagedUnit unit;
    private boolean loading;

    public UnitEquipmentContainer(ManagedUnit unit) {
        super(TOTAL);
        this.unit = unit;
        load();
    }

    public ManagedUnit unit() { return unit; }

    private void load() {
        loading = true;
        for (int i = 0; i < ManagedUnit.GEAR_SLOTS; i++) {
            setItem(i, unit.getManualGear(i));
        }
        for (int i = 0; i < ManagedUnit.TACTICAL_SLOTS; i++) {
            setItem(ManagedUnit.GEAR_SLOTS + i, unit.getManualTactical(i));
        }
        loading = false;
    }

    @Override
    public void setChanged() {
        if (loading) return;
        // 客户端只负责显示（内容由服务端槽位同步包写入）：不回写实体，避免窗口开着时把单位装备改花
        if (unit.asEntity().level().isClientSide) return;
        for (int i = 0; i < ManagedUnit.GEAR_SLOTS; i++) {
            unit.setManualGear(i, getItem(i));
        }
        for (int i = 0; i < ManagedUnit.TACTICAL_SLOTS; i++) {
            unit.setManualTactical(i, getItem(ManagedUnit.GEAR_SLOTS + i));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && unit.asEntity().isAlive()
                && player.distanceToSqr(unit.asEntity()) <= 64.0;
    }
}
