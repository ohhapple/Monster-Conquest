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

public final class IllagerFaction implements Faction {
    public static final ResourceLocation ID = FactionIds.ILLAGER;
    public static final IllagerFaction INSTANCE = new IllagerFaction();
    private IllagerFaction() {}

    @Override public ResourceLocation getId() { return ID; }
    @Override public String getDisplayName() { return "灾厄"; }

    @Override public List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes() {
        return List.of(
                ModEntities.VINDICATOR_SOLDIER.get(),
                ModEntities.EVOKER_SOLDIER.get(),
                ModEntities.WITCH_SOLDIER.get(),
                ModEntities.RIFLE_GUNNER_SOLDIER.get(),
                ModEntities.SHOTGUN_GUNNER_SOLDIER.get(),
                ModEntities.ROCKET_GUNNER_SOLDIER.get(),
                ModEntities.SNIPER_GUNNER_SOLDIER.get()
        );
    }

    @Override public List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes() {
        return List.of(ModEntities.ILLAGER_COMMANDER.get());
    }

    @Override public Set<ResourceLocation> getDefaultHostileFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionIllagerHostile) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }

    @Override public Set<String> getDefaultHostileEntityTypes() {
        return new HashSet<>(FactionConfig.factionIllagerEnemies);
    }

    @Override public Set<ResourceLocation> getDefaultAllyFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionIllagerAlly) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }
}
