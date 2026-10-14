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

public final class NeutralFaction implements Faction {
    public static final ResourceLocation ID = FactionIds.NEUTRAL;
    public static final NeutralFaction INSTANCE = new NeutralFaction();
    private NeutralFaction() {}

    @Override public ResourceLocation getId() { return ID; }
    @Override public String getDisplayName() { return "中立"; }

    @Override public List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes() {
        return List.of(
                ModEntities.RIFLE_GUNNER_SOLDIER.get(),
                ModEntities.SHOTGUN_GUNNER_SOLDIER.get(),
                ModEntities.ROCKET_GUNNER_SOLDIER.get(),
                ModEntities.SNIPER_GUNNER_SOLDIER.get()
        );
    }

    @Override public List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes() {
        return List.of(ModEntities.NEUTRAL_COMMANDER.get());
    }

    @Override public Set<ResourceLocation> getDefaultHostileFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionNeutralHostile) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }

    @Override public Set<String> getDefaultHostileEntityTypes() {
        return new HashSet<>(FactionConfig.factionNeutralEnemies);
    }

    @Override public Set<ResourceLocation> getDefaultAllyFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionNeutralAlly) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }
}
