package com.ohhapple.monster_conquest.gun;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.ModSounds;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.projectile.ArmyBullet;
import com.ohhapple.monster_conquest.entity.projectile.ArmyGunProjectile;
import com.ohhapple.monster_conquest.entity.projectile.ArmyRocket;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.network.clientbound.gun.GunStatusPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家枪械的射击 / 装填服务端逻辑。
 *   · 左键开火（客户端 GunFirePayload 驱动，服务端按射速节流）
 *   · 弹匣打空或停火一段时间后自动装填（备弹来自背包 + 战术装备栏中的弹药箱）
 *   · 创造模式备弹无限：弹匣照常打空、照常换弹，不消耗备弹
 *   · 腰射 / 瞄准两套散布与射速；狙击枪右键开镜
 *   · 物品模型状态切换（待机 / 开火 / 换弹）
 */
public final class GunCombat {

    private GunCombat() {}

    private static final String AMMO_KEY = "MCAmmo";

    /** 物品模型状态：0 = 待机，1 = 装填，2 = 开火。 */
    private static final int MODEL_IDLE = 0;
    private static final int MODEL_RELOAD = 1;
    private static final int MODEL_FIRE = 2;

    private record ReloadState(net.minecraft.world.item.Item gun, int total, int left) {}

    /** 停火多久后"快速补弹"（无换弹时间、无动画，只有音效）。 */
    private static final int TOP_UP_IDLE_TICKS = 25;

    private static final Map<UUID, ReloadState> RELOADS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();

    // ===================== 弹药数据 =====================

    public static int getAmmo(ItemStack stack, GunSpec spec) {
        if (!CombatConfig.gunRequireAmmo) return spec.magazineSize();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(AMMO_KEY)) return spec.magazineSize();
        return Math.max(0, Math.min(spec.magazineSize(), tag.getInt(AMMO_KEY)));
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(AMMO_KEY, Math.max(0, ammo));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 备弹是否无限（配置关闭弹药消耗 / 创造模式）。 */
    private static boolean infiniteReserve(ServerPlayer player) {
        return !CombatConfig.gunRequireAmmo || player.isCreative();
    }

    private static boolean hasReserve(ServerPlayer player, GunSpec spec) {
        return infiniteReserve(player) || TacticalEquipmentSlotsData.countReserve(player, spec.ammoType()) > 0;
    }

    // ===================== 射击 =====================

    public static boolean tryFire(ServerPlayer player, ItemStack stack) {
        return tryFire(player, stack, player.getLookAngle());
    }

    /** aim 为客户端准星方向；与服务器视角偏差过大时回退到服务器方向。 */
    public static boolean tryFire(ServerPlayer player, ItemStack stack, Vec3 aim) {
        if (!(stack.getItem() instanceof GunItem gun)) return false;
        GunSpec spec = gun.spec();

        if (player.getCooldowns().isOnCooldown(gun)) return false;
        if (RELOADS.containsKey(player.getUUID())) return false;

        if (getAmmo(stack, spec) <= 0) {
            dryFire(player, spec);
            startReload(player, stack, spec);
            player.getCooldowns().addCooldown(gun, 8);
            return false;
        }

        fire(player, stack, spec, resolveAim(player, aim));
        setAmmo(stack, getAmmo(stack, spec) - 1);
        LAST_SHOT.put(player.getUUID(), player.level().getGameTime());

        boolean ads = isAds(player, gun, stack);
        int cooldown = ads ? spec.adsCooldownTicks() : spec.cooldownTicks();
        player.getCooldowns().addCooldown(gun, Math.max(1, cooldown));
        return true;
    }

    private static Vec3 resolveAim(ServerPlayer player, Vec3 clientAim) {
        Vec3 serverLook = player.getLookAngle();
        if (clientAim == null || clientAim.lengthSqr() < 1.0E-6) return serverLook;
        Vec3 dir = clientAim.normalize();
        // 允许约 40° 的视角同步误差，超过则视为异常请求，使用服务端朝向
        return dir.dot(serverLook) < 0.75 ? serverLook : dir;
    }

    /** 是否处于瞄准状态：潜行，或狙击枪右键开镜。 */
    private static boolean isAds(ServerPlayer player, GunItem gun, ItemStack stack) {
        return player.isShiftKeyDown() || gun.isScoping(player, stack);
    }

    private static void fire(ServerPlayer player, ItemStack stack, GunSpec spec, Vec3 look) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();

        boolean ads = isAds(player, (GunItem) stack.getItem(), stack);
        double spread = ads ? spec.adsSpreadDegrees() : spec.hipSpreadDegrees();
        float headshot = CombatConfig.gunHeadshot ? spec.headshotMultiplier() : 1.0f;

        for (int i = 0; i < spec.pellets(); i++) {
            Vec3 dir = AbstractArmySoldier.perturbDirection(look, spread, player.getRandom());

            ArmyGunProjectile proj = spec.category() == GunCategory.LAUNCHER
                    ? new ArmyRocket(ModEntities.ARMY_ROCKET.get(), player, level)
                    : new ArmyBullet(ModEntities.ARMY_BULLET.get(), player, level);

            // 直接在眼睛位置出膛：贴脸目标也能命中（友军判定保证不会打到自己），
            // 近距离不渲染由实体渲染器负责，不会遮挡视野。
            proj.setPos(eye.x, eye.y, eye.z);
            proj.shoot(dir.x, dir.y, dir.z, (float) spec.projectileSpeed(), 0.0f);
            proj.setBaseDamage(spec.damage());
            proj.setHeadshotMultiplier(headshot);
            proj.setArmorPenetration(spec.armorPenetration());
            proj.setGravity(spec.projectileGravity());
            proj.setMaxLifeTicks(spec.projectileLifeTicks());
            proj.setMaxRange(spec.rangeBlocks());
            // 多发弹丸（霰弹）只让第一颗发出飞行音效
            proj.setFlightSoundEnabled(i == 0);
            // 多发弹丸只保留一条曳光（避免 7 条粒子线叠加）
            proj.setTrailEnabled(i == 0);
            if (spec.hasExplosion()) proj.setExplosionRadius(spec.explosionRadius());
            level.addFreshEntity(proj);
        }

        playShootSound(level, player, spec);
    }

    // ===================== 装填 =====================

    private static void startReload(ServerPlayer player, ItemStack stack, GunSpec spec) {
        if (!CombatConfig.gunRequireAmmo) {
            setAmmo(stack, spec.magazineSize());
            return;
        }
        if (RELOADS.containsKey(player.getUUID())) return;
        if (!hasReserve(player, spec)) return;

        RELOADS.put(player.getUUID(),
                new ReloadState(stack.getItem(), spec.reloadTicks(), spec.reloadTicks()));
        // 绑定实体的播放方式：声音在客户端跟随玩家当前位置，不会因网络延迟偏到身旁
        player.level().playSound(null, player,
                SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8f, 1.6f);
        sendStatus(player, true, spec.reloadTicks(), spec.reloadTicks());
    }

    private static void finishReload(ServerPlayer player, ItemStack stack, GunSpec spec) {
        int want = spec.magazineSize();
        int loaded = infiniteReserve(player)
                ? want
                : TacticalEquipmentSlotsData.consumeReserve(player, spec.ammoType(), want);
        if (loaded > 0) setAmmo(stack, loaded);

        player.level().playSound(null, player,
                SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8f, 1.6f);
        sendStatus(player, false, 0, 0);
    }

    private static void dryFire(ServerPlayer player, GunSpec spec) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double x = eye.x + look.x * 0.7;
        double y = eye.y + look.y * 0.7 - 0.15;
        double z = eye.z + look.z * 0.7;
        player.level().playSound(null, x, y, z, SoundEvents.STONE_BUTTON_CLICK_ON,
                SoundSource.PLAYERS, 0.7f, 1.1f);
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.02, 0.02, 0.02, 0.01);
        }
    }

    // ===================== 服务端每 tick =====================

    public static void serverTick(ServerPlayer player) {
        tickReload(player);
        tickAutoReload(player);
        tickModelState(player);
    }

    private static void tickReload(ServerPlayer player) {
        ReloadState state = RELOADS.get(player.getUUID());
        if (state == null) return;

        ItemStack held = player.getMainHandItem();
        if (!held.is(state.gun())) {
            RELOADS.remove(player.getUUID());
            sendStatus(player, false, 0, 0);
            return;
        }

        int left = state.left() - 1;
        if (left > 0) {
            RELOADS.put(player.getUUID(), new ReloadState(state.gun(), state.total(), left));
            return;
        }

        RELOADS.remove(player.getUUID());
        if (held.getItem() instanceof GunItem gun) {
            finishReload(player, held, gun.spec());
        }
    }

    /**
     * 弹匣打空 → 正常换弹（有时间 + 换弹形态 + 进度条）；
     * 弹匣没满但停火一段时间 → 快速补弹（无换弹时间 / 动画，只有音效）。
     */
    private static void tickAutoReload(ServerPlayer player) {
        if (!CombatConfig.gunRequireAmmo) return;
        if (RELOADS.containsKey(player.getUUID())) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof GunItem gun)) return;

        GunSpec spec = gun.spec();
        int ammo = getAmmo(held, spec);
        if (ammo <= 0) {
            startReload(player, held, spec);
            return;
        }
        if (ammo >= spec.magazineSize()) return;

        long now = player.level().getGameTime();
        if (now - LAST_SHOT.getOrDefault(player.getUUID(), -1000L) < TOP_UP_IDLE_TICKS) return;

        int need = spec.magazineSize() - ammo;
        int got = infiniteReserve(player)
                ? need
                : TacticalEquipmentSlotsData.consumeReserve(player, spec.ammoType(), need);
        if (got <= 0) return;

        setAmmo(held, ammo + got);
        player.level().playSound(null, player,
                SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.7f, 1.7f);
    }

    private static void tickModelState(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof GunItem gun)) return;

        int state;
        if (RELOADS.containsKey(player.getUUID())) {
            state = MODEL_RELOAD;
        } else if (player.level().getGameTime()
                - LAST_SHOT.getOrDefault(player.getUUID(), -1000L) <= 4) {
            state = MODEL_FIRE;
        } else {
            state = MODEL_IDLE;
        }

        CustomModelData current = held.get(DataComponents.CUSTOM_MODEL_DATA);
        int currentValue = current == null ? 0 : current.value();
        if (currentValue != state) {
            held.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(state));
        }
    }

    public static void clearPlayer(UUID uuid) {
        RELOADS.remove(uuid);
        LAST_SHOT.remove(uuid);
    }

    private static void sendStatus(ServerPlayer player, boolean reloading, int total, int left) {
        PacketDistributor.sendToPlayer(player, new GunStatusPayload(reloading, total, left));
    }

    // ===================== 特效 =====================

    private static void playShootSound(ServerLevel level, ServerPlayer player, GunSpec spec) {
        SoundEvent sound = switch (spec.category()) {
            case RIFLE -> ModSounds.RIFLE_SHOOT.get();
            case SHOTGUN -> ModSounds.SHOTGUN_SHOOT.get();
            case SNIPER -> ModSounds.SNIPER_SHOOT.get();
            case LAUNCHER -> ModSounds.ROCKET_LAUNCH.get();
        };
        float volume = spec.category() == GunCategory.LAUNCHER ? 1.3f : 1.0f;
        float pitch = 0.95f + player.getRandom().nextFloat() * 0.1f;
        if (spec.category() == GunCategory.SNIPER) pitch *= 0.85f;
        // 用"绑定实体"的播放方式。
        //   坐标播放用的是服务端记录的位置（有网络延迟），玩家移动时枪声会听起来
        //   从身旁/身后响；绑定实体后客户端把声音挂在玩家身上逐帧跟随，自射枪声
        //   永远居中在自己身上，别人听到的枪声也会跟着射击者移动。
        level.playSound(null, player, sound, SoundSource.PLAYERS, volume, pitch);
    }

}
