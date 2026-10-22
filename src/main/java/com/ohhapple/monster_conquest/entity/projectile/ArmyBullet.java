package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 步枪 / 霰弹枪的高速弹丸：重力极小，几乎直线飞行。
 * 与火箭弹一致：由实体模型表现（中心对称弹体），不使用粒子渲染弹道。
 */
public class ArmyBullet extends ArmyGunProjectile {

    /** 子弹线段离本地玩家眼睛的最大距离；超过后不播放掠过声。 */
    private static final double FLYBY_RANGE = 6.0;

    private boolean flyByPlayed;

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
    protected void playClientFlightSound(Vec3 from, Vec3 to) {
        if (flyByPlayed || !isFlightSoundEnabled()) return;

        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6) return;

        AABB search = new AABB(from, to).inflate(FLYBY_RANGE);
        Player localPlayer = null;
        for (Player candidate : level().getEntitiesOfClass(
                Player.class, search,
                p -> p.isLocalPlayer() && p != getOwner())) {
            localPlayer = candidate;
            break;
        }
        if (localPlayer == null) return;

        Vec3 eye = localPlayer.getEyePosition();
        double t = eye.subtract(from).dot(segment) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec3 closest = from.add(segment.scale(t));
        double distance = Math.sqrt(closest.distanceToSqr(eye));
        if (distance > FLYBY_RANGE) return;

        float proximity = (float) (1.0 - distance / FLYBY_RANGE);
        float energy = Math.max(0.25f, flightEnergyFraction());
        float volume = (0.10f + 0.34f * proximity) * (0.65f + 0.35f * energy);
        float pitch = 1.03f + this.random.nextFloat() * 0.03f;
        level().playLocalSound(closest.x, closest.y, closest.z,
                ModSounds.BULLET_FLYBY.get(), SoundSource.PLAYERS,
                volume, pitch, false);
        flyByPlayed = true;
    }

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
