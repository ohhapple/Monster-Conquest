package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/**
 * 募兵站可招募的兵种表。
 *   兵种名直接用实体的翻译键：entity.monster_conquest.&lt;id&gt;；
 *   图标复用兵种蛋贴图：textures/item/egg/soldier/&lt;id&gt;_tier_&lt;n&gt;.png。
 */
public final class RecruitableSoldiers {

    private RecruitableSoldiers() {}

    public record Entry(String id,
                        DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
                        SoldierType form) {}

    public static final List<Entry> ALL = List.of(
            new Entry("soldier_zombie", ModEntities.ZOMBIE_SOLDIER, SoldierType.MELEE_INFANTRY),
            new Entry("soldier_husk", ModEntities.HUSK_SOLDIER, SoldierType.MELEE_INFANTRY),
            new Entry("soldier_zombie_villager", ModEntities.ZOMBIE_VILLAGER_SOLDIER, SoldierType.MELEE_INFANTRY),
            new Entry("soldier_drowned", ModEntities.DROWNED_SOLDIER, SoldierType.DUAL_INFANTRY),
            new Entry("soldier_skeleton", ModEntities.SKELETON_SOLDIER, SoldierType.ARCHER_INFANTRY),
            new Entry("soldier_stray", ModEntities.STRAY_SOLDIER, SoldierType.ARCHER_INFANTRY),
            new Entry("soldier_bogged", ModEntities.BOGGED_SOLDIER, SoldierType.ARCHER_INFANTRY),
            new Entry("soldier_villager_archer", ModEntities.VILLAGER_ARCHER_SOLDIER, SoldierType.ARCHER_INFANTRY),
            new Entry("soldier_witch", ModEntities.WITCH_SOLDIER, SoldierType.THROWER_INFANTRY),
            new Entry("soldier_villager_priest", ModEntities.VILLAGER_PRIEST_SOLDIER, SoldierType.THROWER_INFANTRY),
            new Entry("soldier_vindicator", ModEntities.VINDICATOR_SOLDIER, SoldierType.MELEE_INFANTRY),
            new Entry("soldier_gunner_rifle", ModEntities.RIFLE_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY),
            new Entry("soldier_wither_skeleton", ModEntities.WITHER_SKELETON_SOLDIER, SoldierType.DUAL_INFANTRY),
            new Entry("soldier_gunner_shotgun", ModEntities.SHOTGUN_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY),
            new Entry("soldier_gunner_rocket", ModEntities.ROCKET_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY),
            new Entry("soldier_gunner_sniper", ModEntities.SNIPER_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY),
            new Entry("soldier_mace", ModEntities.VILLAGER_MACE_SOLDIER, SoldierType.MELEE_INFANTRY),
            new Entry("soldier_evoker", ModEntities.EVOKER_SOLDIER, SoldierType.MAGE_INFANTRY)
    );
}
