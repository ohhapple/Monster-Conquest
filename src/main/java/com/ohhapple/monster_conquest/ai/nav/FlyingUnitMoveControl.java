package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * 飞行单位共用移动控制器。
 *   导航参数只使用 1.0；这里只写方向，最终速度统一由实体的 getFlyingSpeed() 提供。
 */
public class FlyingUnitMoveControl extends MoveControl {

    private static final double VERTICAL_DEADBAND = 0.08;

    private final int maxTurn;

    public FlyingUnitMoveControl(Mob mob, int maxTurn) {
        super(mob);
        this.maxTurn = maxTurn;
    }

    @Override
    public void tick() {
        if (operation != Operation.MOVE_TO) {
            mob.setYya(0.0F);
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            return;
        }

        operation = Operation.WAIT;
        mob.setNoGravity(true);
        double dx = wantedX - mob.getX();
        double dy = wantedY - mob.getY();
        double dz = wantedZ - mob.getZ();
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        if (distanceSqr < 2.5E-7) {
            mob.setYya(0.0F);
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            return;
        }

        float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 90.0F));
        // MoveControl 只写入归一化方向；最终速度由实体 getFlyingSpeed() 统一提供。
        // 若这里再次写入 MOVEMENT_SPEED，会与 getFlyingSpeed() 形成重复速度来源。
        mob.setSpeed(1.0F);
        mob.setZza(1.0F);
        mob.setXxa(0.0F);

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(dy) > VERTICAL_DEADBAND) {
            float pitch = (float) (-(Mth.atan2(dy, horizontal) * 180.0F / (float) Math.PI));
            mob.setXRot(rotlerp(mob.getXRot(), pitch, maxTurn));
            mob.setYya(dy > 0.0 ? 1.0F : -1.0F);
        } else {
            mob.setXRot(rotlerp(mob.getXRot(), 0.0F, maxTurn));
            mob.setYya(0.0F);
        }
    }
}
