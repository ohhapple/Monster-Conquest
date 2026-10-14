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

public final class UndeadFaction implements Faction {

    public static final ResourceLocation ID = FactionIds.UNDEAD;
    public static final UndeadFaction INSTANCE = new UndeadFaction();

    private UndeadFaction() {}

    @Override public ResourceLocation getId() { return ID; }
    @Override public String getDisplayName() { return "亡灵"; }

    @Override
    public List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes() {
        return List.of(
                ModEntities.ZOMBIE_SOLDIER.get(),
                ModEntities.DROWNED_SOLDIER.get(),
                ModEntities.HUSK_SOLDIER.get(),
                ModEntities.ZOMBIE_VILLAGER_SOLDIER.get(),
                ModEntities.WITHER_SKELETON_SOLDIER.get(),
                ModEntities.SKELETON_SOLDIER.get(),
                ModEntities.BOGGED_SOLDIER.get(),
                ModEntities.STRAY_SOLDIER.get(),
                ModEntities.WITCH_SOLDIER.get()
        );
    }

    @Override
    public List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes() {
        return List.of(ModEntities.UNDEAD_COMMANDER.get());
    }

    @Override
    public Set<ResourceLocation> getDefaultHostileFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionUndeadHostile) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }

    @Override
    public Set<String> getDefaultHostileEntityTypes() {
        return new HashSet<>(FactionConfig.factionUndeadEnemies);
    }

    @Override public Set<ResourceLocation> getDefaultAllyFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.factionUndeadAlly) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }
}
