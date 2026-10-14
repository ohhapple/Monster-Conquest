package com.ohhapple.monster_conquest.army.factions;

import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.army.Faction;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MonsterFaction implements Faction {
    public static final ResourceLocation ID = FactionIds.MONSTER;
    public static final MonsterFaction INSTANCE = new MonsterFaction();
    private MonsterFaction() {}

    @Override public ResourceLocation getId() { return ID; }
    @Override public String getDisplayName() { return "怪物"; }

    @Override public List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes() {
        return List.of(
                ModEntities.DROWNED_SOLDIER.get(),
                ModEntities.STRAY_SOLDIER.get(),
                ModEntities.ZOMBIE_VILLAGER_SOLDIER.get(),
                ModEntities.WITCH_SOLDIER.get(),
                ModEntities.BOGGED_SOLDIER.get()
        );
    }

    @Override public List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes() {
        return List.of(ModEntities.MONSTER_COMMANDER.get());
    }

    @Override public Set<ResourceLocation> getDefaultHostileFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionMonsterHostile) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }

    @Override public Set<String> getDefaultHostileEntityTypes() {
        return new HashSet<>(FactionConfig.factionMonsterEnemies);
    }

    @Override public Set<ResourceLocation> getDefaultAllyFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionMonsterAlly) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }
}
