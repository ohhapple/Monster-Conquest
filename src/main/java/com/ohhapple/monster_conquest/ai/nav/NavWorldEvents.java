package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 方块几何变化时让路线缓存失效。
 *   单位不能继续沿一条经过新墙的旧路线走；缓存失效只按维度递增版本，
 *   不在方块事件里做逐条路线扫描。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public final class NavWorldEvents {

    private NavWorldEvents() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RoutePlanner.noteWorldChanged(level.dimension().location());
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RoutePlanner.noteWorldChanged(level.dimension().location());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && !event.getAffectedBlocks().isEmpty()) {
            RoutePlanner.noteWorldChanged(level.dimension().location());
        }
    }
}
