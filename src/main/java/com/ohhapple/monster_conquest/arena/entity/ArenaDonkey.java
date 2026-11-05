package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class ArenaDonkey extends ArenaAnimalSoldier {

    public ArenaDonkey(EntityType<? extends PathfinderMob> type,
                       Level level) {
        super(type, level);
    }

    @Override
    public int getRenderIndex() {
        return 39;
    }

    @Override
    public double getMovementSpeed() {
        return 0.32;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && isArenaUnit()) {
            double ratio = ArenaSkillBalance
                    .DONKEY_DEATH_SHIELD_RATIO
                    * ArenaSkillBalance.abilityScale(
                    getArenaStar(), getArenaMagicPower());
            arenaSkills().grantNearestAllyShield(
                    ratio, false);
        }
        super.die(source);
    }
}
