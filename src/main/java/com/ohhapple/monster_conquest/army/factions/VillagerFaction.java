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

public final class VillagerFaction implements Faction {
    public static final ResourceLocation ID = FactionIds.VILLAGER;
    public static final VillagerFaction INSTANCE = new VillagerFaction();
    private VillagerFaction() {}

    @Override public ResourceLocation getId() { return ID; }
    @Override public String getDisplayName() { return "村民"; }

    @Override public List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes() {
        return List.of(
                ModEntities.VILLAGER_MACE_SOLDIER.get(),
                ModEntities.VILLAGER_PRIEST_SOLDIER.get(),
                ModEntities.VILLAGER_ARCHER_SOLDIER.get(),
                // 四种枪手：村民首领 / 巡逻队长也可以召唤（与灾厄阵营一致）
                ModEntities.RIFLE_GUNNER_SOLDIER.get(),
                ModEntities.SHOTGUN_GUNNER_SOLDIER.get(),
                ModEntities.ROCKET_GUNNER_SOLDIER.get(),
                ModEntities.SNIPER_GUNNER_SOLDIER.get());
    }

    @Override public List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes() {
        return List.of(ModEntities.VILLAGER_COMMANDER.get());
    }

    @Override public Set<ResourceLocation> getDefaultHostileFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionVillagerHostile) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }

    @Override public Set<String> getDefaultHostileEntityTypes() {
        return new HashSet<>(FactionConfig.factionVillagerEnemies);
    }

    @Override public Set<ResourceLocation> getDefaultAllyFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionVillagerAlly) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }
}
