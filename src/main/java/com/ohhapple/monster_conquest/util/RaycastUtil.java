package com.ohhapple.monster_conquest.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

public final class RaycastUtil {

    private RaycastUtil() {}

    public static final double DEFAULT_MAX_DISTANCE = 256.0;

    private static final double ENTITY_INFLATE = 0.4;
    private static final double SEARCH_INFLATE = 1.5;

    @Nullable
    public static Entity raycastEntity(Player player, double maxDist, Predicate<Entity> filter) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        Vec3 end = eye.add(dir.scale(maxDist));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockLimit = blockHit.getType() == HitResult.Type.MISS
                ? maxDist
                : eye.distanceTo(blockHit.getLocation());

        AABB search = new AABB(eye, end).inflate(SEARCH_INFLATE);

        Entity best = null;
        double bestDist = blockLimit + 0.001;

        for (Entity e : player.level().getEntities(player, search, filter)) {
            AABB box = e.getBoundingBox().inflate(ENTITY_INFLATE);
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceTo(hit.get());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    @Nullable
    public static BlockPos raycastBlock(Player player, double maxDist) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        Vec3 end = eye.add(dir.scale(maxDist));
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getBlockPos();
    }
}