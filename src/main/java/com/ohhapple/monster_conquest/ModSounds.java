package com.ohhapple.monster_conquest;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 枪械音效注册。
 * 音频文件位于 assets/monster_conquest/sounds/gun/。
 */
public final class ModSounds {

    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Monster_conquest.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> RIFLE_SHOOT =
            register("gun.rifle_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN_SHOOT =
            register("gun.shotgun_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_LAUNCH =
            register("gun.rocket_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNIPER_SHOOT =
            register("gun.sniper_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLET_HIT =
            register("gun.bullet_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_EXPLODE =
            register("gun.rocket_explode");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, name)));
    }
}
