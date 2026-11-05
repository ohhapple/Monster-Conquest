package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.arena.unit.ArenaSkillType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 竞技弈子与对应原版生物之间的受击声映射。 */
public final class ArenaUnitSounds {

    private ArenaUnitSounds() {
    }

    public static SoundEvent hurtSound(ArenaSkillType type) {
        return switch (type) {
            case ZOMBIE_SHIELD -> SoundEvents.ZOMBIE_HURT;
            case SKELETON_VOLLEY -> SoundEvents.SKELETON_HURT;
            case STRAY_ARCANE_SHOTS -> SoundEvents.STRAY_HURT;
            case DROWNED_TRIDENT_STUN -> SoundEvents.DROWNED_HURT;
            case ZOMBIE_VILLAGER_STUN ->
                    SoundEvents.ZOMBIE_VILLAGER_HURT;
            case HUSK_BATTLE_GROWTH -> SoundEvents.HUSK_HURT;
            case SNOW_GOLEM_SNOWBALL_FAN ->
                    SoundEvents.SNOW_GOLEM_HURT;
            case SPIDER_SLOW_FIELD -> SoundEvents.SPIDER_HURT;
            case BREEZE_GALE_CHARGE -> SoundEvents.BREEZE_HURT;
            case ARCHER_STRENGTH, MACE_LEAP_SMASH ->
                    SoundEvents.VILLAGER_HURT;
            case VINDICATOR_EXECUTE -> SoundEvents.VINDICATOR_HURT;
            case WOLF_CALL -> SoundEvents.WOLF_HURT;
            case GOAT_CHARGE -> SoundEvents.GOAT_HURT;
            case RAVAGER_KNOCKUP_HEAL -> SoundEvents.RAVAGER_HURT;
            case POLAR_BEAR_FORM -> SoundEvents.POLAR_BEAR_HURT;
            case HOGLIN_CHARGE -> SoundEvents.HOGLIN_HURT;
            case BOGGED_POISON_VOLLEY -> SoundEvents.BOGGED_HURT;
            case RIFLE_RAPID_FIRE, SNIPER_EXECUTE_SHOT,
                    SHOTGUN_BARRAGE -> SoundEvents.ARMOR_STAND_HIT;
            case PIGLIN_TAUNT_SHIELD -> SoundEvents.PIGLIN_HURT;
            case WITHER_DRAIN ->
                    SoundEvents.WITHER_SKELETON_HURT;
            case EVOKER_FANG_STUN -> SoundEvents.EVOKER_HURT;
            case WITCH_LINGERING_POTION -> SoundEvents.WITCH_HURT;
            case IRON_GOLEM_LEAP_SMASH ->
                    SoundEvents.IRON_GOLEM_HURT;
            case CAVE_SPIDER_VENOM -> SoundEvents.SPIDER_HURT;
            case ARMADILLO_ROLL, GIANT_ARMADILLO_MOMENTUM ->
                    SoundEvents.ARMADILLO_HURT;
            case BLAZE_FIREBALL_BARRAGE -> SoundEvents.BLAZE_HURT;
            case SNIFFER_DIG -> SoundEvents.SNIFFER_HURT;
            case ENDERMITE_SILENCE_STRIKE ->
                    SoundEvents.ENDERMITE_HURT;
            case SLIME_LEAP_SMASH -> SoundEvents.SLIME_HURT;
            case ELDER_GUARDIAN_LASER ->
                    SoundEvents.ELDER_GUARDIAN_HURT;
            case RABBIT_LEAP -> SoundEvents.RABBIT_HURT;
            case DONKEY_GIFT -> SoundEvents.DONKEY_HURT;
            case SHEEP_HEAL -> SoundEvents.SHEEP_HURT;
            case COW_CLEANSE -> SoundEvents.COW_HURT;
            case PIG_TRANSFORM -> SoundEvents.PIG_HURT;
            case ZOMBIFIED_PIGLIN_FRENZY ->
                    SoundEvents.ZOMBIFIED_PIGLIN_HURT;
            case FROG_PULL -> SoundEvents.FROG_HURT;
            case FOX_STEAL -> SoundEvents.FOX_HURT;
            case CREEPER_EXPLODE -> SoundEvents.CREEPER_HURT;
            case PANDA_ROLL -> SoundEvents.PANDA_HURT;
            case ENDERMAN_LOCK -> SoundEvents.ENDERMAN_HURT;
            case WARDEN_SONIC_BOOM -> SoundEvents.WARDEN_HURT;
            case WANDERING_TRADER_SUPPLY ->
                    SoundEvents.WANDERING_TRADER_HURT;
            case MAGMA_CUBE_BLESSING -> SoundEvents.MAGMA_CUBE_HURT;
            case TURTLE_RESILIENCE -> SoundEvents.TURTLE_HURT;
            case WITHER_SKULL_BARRAGE -> SoundEvents.WITHER_HURT;
            case SKELETON_HORSE_CLEANSE ->
                    SoundEvents.SKELETON_HORSE_HURT;
            case ENDER_DRAGON_BOARD_SLAM ->
                    SoundEvents.ENDER_DRAGON_HURT;
        };
    }
}
