package com.ohhapple.monster_conquest.army;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class PlayerFactionRegistry {
    private PlayerFactionRegistry() {}

    public static ResourceLocation factionIdOf(UUID player) { return FactionIds.player(player); }
    public static ResourceLocation factionIdOf(Player player) { return factionIdOf(player.getUUID()); }
}
