package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 火箭弹：比子弹稍慢，仍然接近平射；命中敌人或方块后爆炸。
 */
public class ArmyRocket extends ArmyGunProjectile {

    public ArmyRocket(EntityType<? extends ArmyGunProjectile> type, Level level) {
        super(type, level);
    }

    public ArmyRocket(EntityType<? extends ArmyGunProjectile> type,
                      LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    @Override
    protected double defaultGravity() { return 0.02; }

    @Override
    protected SoundEvent getFlightSound() { return SoundEvents.FIREWORK_ROCKET_SHOOT; }

    @Override protected int getFlightSoundInterval() { return 3; }
    @Override protected float getFlightSoundVolume() { return 0.35f; }
    @Override protected float getFlightSoundPitch() { return 1.0f; }

    @Override
    protected Item getDefaultItem() { return Monster_conquest.ROCKET_PROJECTILE.get(); }

    @Override
    protected void spawnTrailParticles(ServerLevel level) {
        // 更大的烟雾拖尾（营火烟），更像真正的火箭弹轨迹。
        Vec3 v = getDeltaMovement();
        if (tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    getX() - v.x * 0.3, getY() - v.y * 0.3 + 0.05, getZ() - v.z * 0.3,
                    2, 0.06, 0.06, 0.06, 0.01);
        }
        if (tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME,
                    getX() - v.x * 0.45, getY() - v.y * 0.45, getZ() - v.z * 0.45,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }
}
