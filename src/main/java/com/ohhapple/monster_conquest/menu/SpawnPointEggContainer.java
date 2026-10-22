package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.block.SpawnPointBlockEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端：刷怪点的模板蛋槽（1 格）包装成 Container，供菜单槽位读写。
 *   蛋只作为生成模板，刷怪时不消耗；方块被拆掉时蛋会掉出来。
 */
public class SpawnPointEggContainer extends SimpleContainer {

    private final SpawnPointBlockEntity spawner;
    /** 物品来源：写入后立刻回写物品；方块来源为 null。 */
    @Nullable private final Runnable onChanged;
    private boolean loading;

    public SpawnPointEggContainer(SpawnPointBlockEntity spawner, @Nullable Runnable onChanged) {
        super(1);
        this.spawner = spawner;
        this.onChanged = onChanged;
        loading = true;
        setItem(0, spawner.getEgg());
        loading = false;
    }

    @Override
    public void setChanged() {
        if (loading) return;
        spawner.setEgg(getItem(0));
        if (onChanged != null) onChanged.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && !spawner.isRemoved()
                && player.distanceToSqr(
                        spawner.getBlockPos().getX() + 0.5,
                        spawner.getBlockPos().getY() + 0.5,
                        spawner.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
