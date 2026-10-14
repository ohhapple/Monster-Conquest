package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 步枪 / 霰弹枪的高速弹丸：重力极小，几乎直线飞行。
 * 与火箭弹一致：由实体模型表现（中心对称弹体），不使用粒子渲染弹道。
 */
public class ArmyBullet extends ArmyGunProjectile {

    public ArmyBullet(EntityType<? extends ArmyGunProjectile> type, Level level) {
        super(type, level);
    }

    public ArmyBullet(EntityType<? extends ArmyGunProjectile> type,
                      LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    @Override
    protected double defaultGravity() { return 0.008; }

    @Override
    protected SoundEvent getFlightSound() { return SoundEvents.PLAYER_ATTACK_SWEEP; }

    @Override protected int getFlightSoundInterval() { return 4; }
    @Override protected float getFlightSoundVolume() { return 0.22f; }
    @Override protected float getFlightSoundPitch() { return 1.8f; }

    @Override
    protected Item getDefaultItem() { return Monster_conquest.BULLET_PROJECTILE.get(); }

    @Override
    protected void spawnTrailParticles(ServerLevel level) {
        // 曳光：每 3 tick 沿飞行方向铺 3 颗暗色 dust 形成短光条（服务端权威位置）
        if (tickCount % 3 != 0) return;
        Vec3 v = getDeltaMovement();
        double len = v.length();
        if (len < 0.001) return;
        double dx = v.x / len;
        double dy = v.y / len;
        double dz = v.z / len;

        for (int i = -1; i <= 1; i++) {
            double t = i * 0.8;
            level.sendParticles(new DustParticleOptions(new Vector3f(0.10f, 0.10f, 0.13f), 0.8f),
                    getX() + dx * t, getY() + dy * t, getZ() + dz * t,
                    1, 0, 0, 0, 0);
        }
        if (tickCount % 9 == 0) {
            level.sendParticles(ParticleTypes.SMOKE,
                    getX() - dx * 0.5, getY() - dy * 0.5, getZ() - dz * 0.5,
                    1, 0, 0, 0, 0);
        }
    }
}
