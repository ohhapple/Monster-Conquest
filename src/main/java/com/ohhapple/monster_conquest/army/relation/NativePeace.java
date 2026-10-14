package com.ohhapple.monster_conquest.army.relation;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡
 * 互不索敌、互不伤害；战马按主人阵营判定。
 */
public final class NativePeace {

    private NativePeace() {}

    /** 灾厄阵营认作同族的原版生物。 */
    public static final Set<EntityType<?>> ILLAGER_KIN = Set.of(
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.WITCH,
            EntityType.EVOKER,
            EntityType.RAVAGER,
            EntityType.VEX);

    /** 村民阵营认作同族的原版生物。 */
    public static final Set<EntityType<?>> VILLAGER_KIN = Set.of(
            EntityType.VILLAGER,
            EntityType.IRON_GOLEM);

    /** 两个实体之间是否存在"同族和平"关系（双向判定）。 */
    public static boolean isPeacefulPair(@Nullable Entity a, @Nullable Entity b) {
        if (a == null || b == null || a == b) return false;

        ResourceLocation facA = factionOf(a);
        if (facA != null) {
            if (FactionIds.ILLAGER.equals(facA) && isKin(b, ILLAGER_KIN)) return true;
            if (FactionIds.VILLAGER.equals(facA) && isKin(b, VILLAGER_KIN)) return true;
        }

        ResourceLocation facB = factionOf(b);
        if (facB != null) {
            if (FactionIds.ILLAGER.equals(facB) && isKin(a, ILLAGER_KIN)) return true;
            if (FactionIds.VILLAGER.equals(facB) && isKin(a, VILLAGER_KIN)) return true;
        }
        return false;
    }

    /** 实体是否属于该和平集合（战马按其主人判定）。 */
    public static boolean isKin(Entity entity, Set<EntityType<?>> kin) {
        if (entity instanceof WarHorseEntity horse) {
            Entity owner = horseOwner(horse);
            return owner != null && isKin(owner, kin);
        }
        return kin.contains(entity.getType());
    }

    /** 该阵营对应的"同族和平"原版生物集合；没有对应关系时返回空集。 */
    public static Set<EntityType<?>> kinOf(@Nullable ResourceLocation faction) {
        if (FactionIds.ILLAGER.equals(faction)) return ILLAGER_KIN;
        if (FactionIds.VILLAGER.equals(faction)) return VILLAGER_KIN;
        return Set.of();
    }

    /** 实体所属阵营（士兵 / 首领 / 战马主人）。 */
    @Nullable
    public static ResourceLocation factionOf(@Nullable Entity entity) {
        if (entity instanceof AbstractArmySoldier s) return s.getFactionId();
        if (entity instanceof AbstractArmyCommander c) return c.getFactionId();
        if (entity instanceof WarHorseEntity horse) {
            Entity owner = horseOwner(horse);
            return owner == null ? null : factionOf(owner);
        }
        return null;
    }

    @Nullable
    private static Entity horseOwner(WarHorseEntity horse) {
        var ownerUuid = horse.getOwnerUuid();
        if (ownerUuid == null || !(horse.level() instanceof ServerLevel sl)) return null;
        return sl.getEntities().get(ownerUuid);
    }
}
