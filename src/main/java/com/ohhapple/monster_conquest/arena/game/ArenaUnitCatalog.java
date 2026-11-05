package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.ArmadilloArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.BlazeArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.BoggedArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.BreezeArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.CaveSpiderArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.CowArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.CreeperArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.DonkeyArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.DrownedArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.EndermanArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.EndermiteArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.EnderDragonArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.ElderGuardianArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.EvokerArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.FoxArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.FrogArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.GoatArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.GiantArmadilloArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.HoglinArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.HuskArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.IronGolemArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.MaceArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.MagmaCubeArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.PiglinShieldArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.PigArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.PandaArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.PolarBearArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.RabbitArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.RavagerArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.RifleGunnerArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SkeletonArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SnowGolemArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SheepArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SniperGunnerArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SnifferArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SlimeArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.ShotgunGunnerArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SkeletonHorseArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.SpiderArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.StrayArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.VillagerArcherArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.VindicatorArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.WitherSkeletonArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.WanderingTraderArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.WitchSoldierArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.WardenArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.WitherArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.WolfArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.ZombieArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.ZombieVillagerArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.ZombifiedPiglinArenaUnit;
import com.ohhapple.monster_conquest.arena.unit.TurtleArenaUnit;

import java.util.List;

/**
 * 自走棋弈子注册表。这里只决定收录哪些弈子；卡费和基础属性由各个
 * {@link ArenaUnit} 子类自己声明。
 */
public final class ArenaUnitCatalog {

    private static final int[][] COST_WEIGHTS = {
            {100, 0, 0, 0, 0},
            {100, 0, 0, 0, 0},
            {75, 25, 0, 0, 0},
            {55, 30, 15, 0, 0},
            {45, 33, 20, 2, 0},
            {30, 40, 25, 5, 0},
            {16, 30, 43, 10, 1},
            {15, 20, 32, 30, 3},
            {10, 17, 25, 33, 15},
            {5, 10, 20, 40, 25}
    };

    public static final ArenaUnit ENDER_DRAGON =
            new EnderDragonArenaUnit();

    public static final List<ArenaUnit> ALL = List.of(
            new ZombieArenaUnit(),
            new SkeletonArenaUnit(),
            new StrayArenaUnit(),
            new DrownedArenaUnit(),
            new VillagerArcherArenaUnit(),
            new VindicatorArenaUnit(),
            new MaceArenaUnit(),
            new RifleGunnerArenaUnit(),
            new PiglinShieldArenaUnit(),
            new WitherSkeletonArenaUnit(),
            new SniperGunnerArenaUnit(),
            new ZombieVillagerArenaUnit(),
            new HuskArenaUnit(),
            new BoggedArenaUnit(),
            new EvokerArenaUnit(),
            new SnowGolemArenaUnit(),
            new SpiderArenaUnit(),
            new WolfArenaUnit(),
            new GoatArenaUnit(),
            new WitchSoldierArenaUnit(),
            new BreezeArenaUnit(),
            new RavagerArenaUnit(),
            new PolarBearArenaUnit(),
            new HoglinArenaUnit(),
            new IronGolemArenaUnit(),
            new CaveSpiderArenaUnit(),
            new ArmadilloArenaUnit(),
            new BlazeArenaUnit(),
            new SnifferArenaUnit(),
            new EndermiteArenaUnit(),
            new SlimeArenaUnit(),
            new ElderGuardianArenaUnit(),
            new RabbitArenaUnit(),
            new DonkeyArenaUnit(),
            new SheepArenaUnit(),
            new CowArenaUnit(),
            new PigArenaUnit(),
            new ZombifiedPiglinArenaUnit(),
            new FrogArenaUnit(),
            new FoxArenaUnit(),
            new CreeperArenaUnit(),
            new PandaArenaUnit(),
            new EndermanArenaUnit(),
            new WardenArenaUnit(),
            new WanderingTraderArenaUnit(),
            new MagmaCubeArenaUnit(),
            new TurtleArenaUnit(),
            new ShotgunGunnerArenaUnit(),
            new WitherArenaUnit(),
            new SkeletonHorseArenaUnit(),
            new GiantArmadilloArenaUnit(),
            ENDER_DRAGON
    );

    private ArenaUnitCatalog() {
    }

    public static ArenaUnit get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    public static int enderDragonIndex() {
        return ALL.indexOf(ENDER_DRAGON);
    }

    public static int[] costWeights(int playerLevel) {
        return COST_WEIGHTS[
                Math.max(1, Math.min(10, playerLevel)) - 1].clone();
    }
}
