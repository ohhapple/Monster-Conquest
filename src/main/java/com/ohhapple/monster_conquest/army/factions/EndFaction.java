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

public final class EndFaction implements Faction {
    public static final ResourceLocation ID = FactionIds.END;
    public static final EndFaction INSTANCE = new EndFaction();
    private EndFaction() {}

    @Override public ResourceLocation getId() { return ID; }
    @Override public String getDisplayName() { return "末地"; }

    @Override public List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes() {
        // 末地阵营没有可召唤的士兵
        return List.of();
    }

    @Override public List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes() {
        return List.of(ModEntities.END_COMMANDER.get());
    }

    @Override public Set<ResourceLocation> getDefaultHostileFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionEndHostile) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }

    @Override public Set<String> getDefaultHostileEntityTypes() {
        return new HashSet<>(FactionConfig.factionEndEnemies);
    }

    @Override public Set<ResourceLocation> getDefaultAllyFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionEndAlly) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }
}
