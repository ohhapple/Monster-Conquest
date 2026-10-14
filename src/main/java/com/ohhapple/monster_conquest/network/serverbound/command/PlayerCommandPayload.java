package com.ohhapple.monster_conquest.network.serverbound.command;

import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.RaycastUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerCommandPayload(int action, int value) implements CustomPacketPayload {

    public static final Type<PlayerCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "player_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PlayerCommandPayload::action,
                    ByteBufCodecs.VAR_INT, PlayerCommandPayload::value,
                    PlayerCommandPayload::new);

    public static final int ACT_SET_FORMATION   = 0;
    public static final int ACT_FOLLOW_ME       = 3;
    public static final int ACT_MOVE_TO_LOOK    = 4;
    public static final int ACT_ATTACK_LOOK     = 5;
    public static final int ACT_HOLD            = 6;
    public static final int ACT_AUTONOMOUS      = 7;
    public static final int ACT_FREE_AGGRESSIVE = 8;
    public static final int ACT_CLEAR           = 9;
    public static final int ACT_VOLLEY_START    = 10;
    public static final int ACT_VOLLEY_STOP     = 11;
    public static final int ACT_STOP_ATTACK     = 12;
    public static final int ACT_RIDE_CAVALRY    = 13;
    public static final int ACT_RIDE_INFANTRY   = 14;
    public static final int ACT_RIDE_DEFAULT    = 15;
    public static final int ACT_SOFT_FOLLOW     = 16;
    /** 把准星指着的小队"并入"当前选中的 1-5 分队（或互换）。 */
    public static final int ACT_PULL_SQUAD      = 17;
    /** 玩家策略：召唤 / 骑乘玩家战马（要求马哨在战术装备栏）。 */
    public static final int ACT_PLAYER_MOUNT    = 18;
    /** 玩家策略：下马并销毁玩家战马。 */
    public static final int ACT_PLAYER_DISMOUNT = 19;
    /** 行动策略：把附近的"无小队"士兵编入当前选中的小队。 */
    public static final int ACT_ENLIST_LONE     = 20;
    /** 行动策略：解散当前选中的小队（士兵变为无小队）。 */
    public static final int ACT_DISSOLVE_SQUAD  = 21;
    /** 行动策略：自由移动（不跟随 / 不驻守 / 不列阵，像原版生物一样自由游走）。 */
    public static final int ACT_FREE_MOVE       = 22;

    public static final int VOLLEY_DURATION_TICKS = 400;

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerCommandPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PlayerCommandState state = PlayerArmyManager.get(player.getUUID());
            long nowGameTime = player.level().getGameTime();

            // 下发指令前刷新"当前有士兵的小队"。
            if (state.getSelectedSquad() == 0) {
                PlayerArmyManager.forceRefreshOccupiedSquads(player);
            } else {
                PlayerArmyManager.refreshOccupiedSquads(player);
            }

            boolean isVolleyAction = payload.action() == ACT_VOLLEY_START
                    || payload.action() == ACT_VOLLEY_STOP;
            boolean isRideAction = payload.action() == ACT_RIDE_CAVALRY
                    || payload.action() == ACT_RIDE_INFANTRY
                    || payload.action() == ACT_RIDE_DEFAULT;
            boolean isPullAction = payload.action() == ACT_PULL_SQUAD;
            boolean isPlayerMountAction = payload.action() == ACT_PLAYER_MOUNT
                    || payload.action() == ACT_PLAYER_DISMOUNT;
            boolean isSquadAdminAction = payload.action() == ACT_ENLIST_LONE
                    || payload.action() == ACT_DISSOLVE_SQUAD;
            if (!isVolleyAction && !isRideAction && !isPullAction
                    && !isPlayerMountAction && !isSquadAdminAction) {
                state.issueOrder(nowGameTime);
            }

            switch (payload.action()) {
                case ACT_SET_FORMATION ->
                        state.setFormation(PlayerCommandState.FormationMode.byId(payload.value()));

                case ACT_FOLLOW_ME ->
                        state.setMovement(PlayerCommandState.MovementMode.FOLLOW);

                case ACT_SOFT_FOLLOW -> {
                    state.setMovement(PlayerCommandState.MovementMode.SOFT_FOLLOW);
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.soft_follow"),
                            true);
                }

                case ACT_FREE_MOVE -> {
                    state.setMovement(PlayerCommandState.MovementMode.FREE);
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.free_move"),
                            true);
                }

                case ACT_MOVE_TO_LOOK -> {
                    BlockPos pos = RaycastUtil.raycastBlock(player, RaycastUtil.DEFAULT_MAX_DISTANCE);
                    Vec3 target = pos != null
                            ? new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5)
                            : player.getEyePosition().add(player.getLookAngle().scale(16.0));

                    Vec3 origin = resolveCurrentAnchor(state);
                    if (origin == null) origin = player.position();

                    double dx = target.x - origin.x;
                    double dz = target.z - origin.z;
                    float yaw = player.getYRot();
                    if (dx * dx + dz * dz > 0.01) {
                        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    }

                    // 与其它"前往"命令共用同一个下发入口
                    state.orderMove(state.getSelectedSquad(), target, yaw, nowGameTime);
                }

                case ACT_ATTACK_LOOK -> {
                    Entity t = RaycastUtil.raycastEntity(player,
                            RaycastUtil.DEFAULT_MAX_DISTANCE,
                            e -> e instanceof LivingEntity);
                    if (t instanceof net.minecraft.world.entity.LivingEntity le) {
                        // 目标是军队 / 巡逻小队成员时，整支军队一起锁定
                        state.setForcedTarget(le.getUUID(),
                                PlayerCommandState.expandArmyTargets(player.level(), le));
                        state.setAttack(PlayerCommandState.AttackMode.COMMANDED);
                    }
                }

                case ACT_HOLD -> {
                    // 原地驻守：以各小队当前所在地为锚点立即硬驻守（不再走到玩家脚下）
                    state.orderHoldInPlace(player, (ServerLevel) player.level(), nowGameTime);
                }

                case ACT_AUTONOMOUS ->
                        state.setAttack(PlayerCommandState.AttackMode.AUTONOMOUS);

                case ACT_STOP_ATTACK ->
                        state.setAttack(PlayerCommandState.AttackMode.STOP);

                case ACT_FREE_AGGRESSIVE ->
                        state.setAttack(PlayerCommandState.AttackMode.FREE_AGGRESSIVE);

                case ACT_CLEAR -> state.clear();

                case ACT_VOLLEY_START -> {
                    BlockPos pos = RaycastUtil.raycastBlock(
                            player, RaycastUtil.DEFAULT_MAX_DISTANCE);
                    Vec3 target = pos != null
                            ? new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5)
                            : player.getEyePosition().add(player.getLookAngle().scale(16.0));
                    state.startVolley(target, nowGameTime, VOLLEY_DURATION_TICKS);
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.volley.start"),
                            true);
                }

                case ACT_VOLLEY_STOP -> {
                    state.stopVolley();
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.volley.stop"),
                            true);
                }

                case ACT_RIDE_CAVALRY -> {
                    state.setRideMode(PlayerCommandState.RideMode.FORCED_CAVALRY);
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.ride.cavalry"),
                            true);
                }

                case ACT_RIDE_INFANTRY -> {
                    state.setRideMode(PlayerCommandState.RideMode.FORCED_INFANTRY);
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.ride.infantry"),
                            true);
                }

                case ACT_RIDE_DEFAULT -> {
                    state.setRideMode(PlayerCommandState.RideMode.AUTO);
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.ride.auto"),
                            true);
                }

                // 把准星指着的小队并入 / 互换到当前选中的 1-5 分队
                case ACT_PULL_SQUAD -> {
                    if (!(player.level() instanceof ServerLevel sl)) return;

                    Entity t = RaycastUtil.raycastEntity(player,
                            RaycastUtil.DEFAULT_MAX_DISTANCE,
                            e -> e instanceof AbstractArmySoldier
                                    || e instanceof WarHorseEntity);
                    if (t == null) {
                        player.displayClientMessage(
                                Component.translatable(
                                        "msg.monster_conquest.squad.pull_no_target"),
                                true);
                        return;
                    }

                    int moved = state.pullSquadIntoSelected(player, t, sl);
                    if (moved < 0) {
                        player.displayClientMessage(
                                Component.translatable(
                                        "msg.monster_conquest.squad.pull_invalid"),
                                true);
                    } else if (moved == 0) {
                        player.displayClientMessage(
                                Component.translatable(
                                        "msg.monster_conquest.squad.pull_same"),
                                true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable(
                                        "msg.monster_conquest.squad.pull_done",
                                        moved),
                                true);
                    }
                }

                // 玩家策略：上马 / 下马（马哨必须放在战术装备栏）
                case ACT_PLAYER_MOUNT -> {
                    if (!player.isPassenger()
                            && !com.ohhapple.monster_conquest.player.PlayerMountUtil
                            .mount(player, true)) {
                        player.displayClientMessage(
                                Component.translatable(
                                        "msg.monster_conquest.player_mount.need_whistle"),
                                true);
                    }
                }

                case ACT_PLAYER_DISMOUNT -> {
                    if (!com.ohhapple.monster_conquest.player.PlayerMountUtil
                            .dismount(player)) {
                        player.displayClientMessage(
                                Component.translatable(
                                        "msg.monster_conquest.player_mount.not_mounted"),
                                true);
                    }
                }

                // 行动策略：编入附近单兵 / 解散当前小队
                case ACT_ENLIST_LONE -> {
                    int n = state.enlistNearbyLoneSoldiers(
                            player, com.ohhapple.monster_conquest.config.PlayerConfig.enlistNearbyRadius);
                    player.displayClientMessage(
                            Component.translatable(
                                    "msg.monster_conquest.squad.enlist_done", n),
                            true);
                }

                case ACT_DISSOLVE_SQUAD -> {
                    if (!(player.level() instanceof ServerLevel sl)) return;
                    int n = state.dissolveCurrentSquad(player, sl);
                    player.displayClientMessage(
                            Component.translatable(n < 0
                                    ? "msg.monster_conquest.squad.dissolve_need_select"
                                    : "msg.monster_conquest.squad.dissolve_done",
                                    n < 0 ? 0 : n),
                            true);
                }
            }
        });
    }

    @Nullable
    private static Vec3 resolveCurrentAnchor(PlayerCommandState state) {
        int sel = state.getSelectedSquad();
        if (sel > 0) {
            return state.stateForSoldier(sel).getFormationAnchor();
        }
        int max = Math.max(1, PlayerConfig.maxPlayerSquads);
        for (int id = 1; id <= max; id++) {
            if (!state.isSquadOccupied(id)) continue;
            Vec3 a = state.stateForSoldier(id).getFormationAnchor();
            if (a != null) return a;
        }
        return null;
    }
}
