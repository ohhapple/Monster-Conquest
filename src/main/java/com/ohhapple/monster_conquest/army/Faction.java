package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface Faction {
    ResourceLocation getId();
    String getDisplayName();
    List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes();
    List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes();

    default Set<ResourceLocation> getDefaultHostileFactions() { return Set.of(); }
    default Set<String> getDefaultHostileEntityTypes() { return Set.of(); }

    /** 默认单向结盟的阵营 ID 列表。 */
    default Set<ResourceLocation> getDefaultAllyFactions() { return Set.of(); }

    default boolean isPlayerFaction() { return false; }
    @Nullable default UUID getOwnerPlayer() { return null; }

    default boolean containsSoldier(EntityType<?> type) { return getSoldierTypes().contains(type); }
    default boolean containsCommander(EntityType<?> type) { return getCommanderTypes().contains(type); }
}
