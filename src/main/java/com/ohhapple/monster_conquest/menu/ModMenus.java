package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 容器菜单注册。 */
public final class ModMenus {

    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Monster_conquest.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AmmoBoxMenu>> AMMO_BOX =
            MENUS.register("ammo_box", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new AmmoBoxMenu(id, inv, buf.readVarInt(), buf.readVarInt())));

    public static final DeferredHolder<MenuType<?>, MenuType<TacticalEquipmentSlotsMenu>> TACTICAL_EQUIPMENT_SLOTS =
            MENUS.register("tactical_equipment_slots", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new TacticalEquipmentSlotsMenu(id, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<BackpackMenu>> BACKPACK =
            MENUS.register("backpack", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new BackpackMenu(id, inv, buf.readVarInt(), buf.readVarInt())));

    public static final DeferredHolder<MenuType<?>, MenuType<UnitMenu>> UNIT =
            MENUS.register("unit", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new UnitMenu(id, inv, buf.readVarInt())));
}
