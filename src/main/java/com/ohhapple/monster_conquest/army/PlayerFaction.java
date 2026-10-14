package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家阵营：没有 NPC 首领，玩家自己就是首领。
 * 士兵的 commanderUuid 直接指向玩家的 UUID。
 * 敌对关系从 FactionConfig.playerFactionEnemies / FactionConfig.playerFactionHostile 读取，
 * 所有玩家的玩家阵营共享同一份默认值。
 */
public final class PlayerFaction implements Faction {
    private final UUID owner;
    private final ResourceLocation id;

    public PlayerFaction(UUID owner) {
        this.owner = owner;
        this.id = FactionIds.player(owner);
    }

    @Override public ResourceLocation getId() { return id; }
    @Override public String getDisplayName() { return "玩家阵营"; }
    @Override public boolean isPlayerFaction() { return true; }
    @Override public UUID getOwnerPlayer() { return owner; }

    @Override
    public List<EntityType<? extends AbstractArmySoldier>> getSoldierTypes() {
        return List.of(
                ModEntities.ZOMBIE_SOLDIER.get(),
                ModEntities.HUSK_SOLDIER.get(),
                ModEntities.DROWNED_SOLDIER.get(),
                ModEntities.ZOMBIE_VILLAGER_SOLDIER.get(),
                ModEntities.SKELETON_SOLDIER.get(),
                ModEntities.STRAY_SOLDIER.get(),
                ModEntities.BOGGED_SOLDIER.get(),
                ModEntities.WITHER_SKELETON_SOLDIER.get(),
                ModEntities.WITCH_SOLDIER.get(),
                ModEntities.VINDICATOR_SOLDIER.get(),
                ModEntities.EVOKER_SOLDIER.get(),
                ModEntities.VILLAGER_MACE_SOLDIER.get(),
                ModEntities.VILLAGER_ARCHER_SOLDIER.get(),
                ModEntities.VILLAGER_PRIEST_SOLDIER.get(),
                ModEntities.RIFLE_GUNNER_SOLDIER.get(),
                ModEntities.SHOTGUN_GUNNER_SOLDIER.get(),
                ModEntities.ROCKET_GUNNER_SOLDIER.get(),
                ModEntities.SNIPER_GUNNER_SOLDIER.get()
        );
    }

    @Override
    public List<EntityType<? extends AbstractArmyCommander>> getCommanderTypes() {
        // 玩家阵营没有 NPC 首领
        return List.of();
    }

    @Override
    public Set<String> getDefaultHostileEntityTypes() {
        return new HashSet<>(FactionConfig.playerFactionEnemies);
    }

    @Override
    public Set<ResourceLocation> getDefaultHostileFactions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : FactionConfig.playerFactionHostile) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) out.add(rl);
        }
        return out;
    }
}
