package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.arena.game.ArenaStatusFlags;
import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** 始终处于滚动形态的大犰狳；移动和远目标锁定由竞技技能运行时驱动。 */
public final class ArenaGiantArmadillo extends ArenaCreatureSoldier {

    public ArenaGiantArmadillo(EntityType<? extends PathfinderMob> type,
                               Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.MELEE_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 0;
    }

    @Override
    public double getMovementSpeed() {
        return 0.31;
    }

    @Override
    public void enterArenaMode(java.util.UUID matchId, int side,
                               int boardIndex) {
        super.enterArenaMode(matchId, side, boardIndex);
        applyRollingPresentation();
    }

    @Override
    public void enterArenaPresentationMode(int side, int boardIndex) {
        super.enterArenaPresentationMode(side, boardIndex);
        applyRollingPresentation();
    }

    private void applyRollingPresentation() {
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        setNoAi(true);
        setArenaStatusFlag(ArenaStatusFlags.ARMADILLO_ROLL, true);
        setArenaStatusFlag(ArenaStatusFlags.ARMADILLO_MOVING, false);
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(
                    ArenaSkillBalance.GIANT_ARMADILLO_BASE_SCALE);
        }
        refreshDimensions();
    }
}
