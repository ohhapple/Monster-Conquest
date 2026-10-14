package com.ohhapple.monster_conquest.network.serverbound.unit;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.UnitAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：单位界面里的操作。
 *   · 策略类操作只作用于"被打开界面的这一个单位"：首次改动会把它个体化（抄一份当前策略），
 *     之后它走自己的个体策略，不再跟随小队 / 全军的策略；"解除个体策略"按钮恢复跟随；
 *   · 服务端会重新校验访问权限，客户端伪造 entityId 也改不了别人的单位。
 */
public record UnitControlPayload(int entityId, int action, int value) implements CustomPacketPayload {

    public static final int ACTION_MOVEMENT = 0;
    public static final int ACTION_ATTACK = 1;
    public static final int ACTION_FORMATION = 2;
    public static final int ACTION_RIDE = 3;
    public static final int ACTION_CLEAR_EQUIPMENT = 4;
    /** 解除个体策略：这个单位重新跟随小队 / 全军指令。 */
    public static final int ACTION_FOLLOW_ARMY = 5;

    public static final Type<UnitControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "unit_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnitControlPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.entityId());
                        buf.writeVarInt(payload.action());
                        buf.writeVarInt(payload.value());
                    },
                    buf -> new UnitControlPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UnitControlPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.serverLevel().getEntity(payload.entityId())
                    instanceof ManagedUnit unit)) return;
            if (!UnitAccess.canOpen(player, unit)) return;

            if (payload.action() == ACTION_CLEAR_EQUIPMENT) {
                unit.clearManualEquipment();
                return;
            }

            // 策略只有玩家阵营士兵有（首领走自己的行军 / 战斗逻辑，不吃这套策略）
            if (!(unit instanceof AbstractArmySoldier soldier)) return;
            if (!(soldier.getCommanderPlayer() instanceof ServerPlayer owner)) return;
            PlayerCommandState state = PlayerArmyManager.getIfPresent(owner.getUUID());
            if (state == null) return;

            if (payload.action() == ACTION_FOLLOW_ARMY) {
                soldier.clearStrategyOverrides();
                state.issueOrder(player.level().getGameTime());
                return;
            }

            // 首次个体化：先把当前生效的小队策略抄进个体状态。
            //   要在写 override 之前抄 —— 标记个体化之后 stateForCommand 返回的就是个体状态了。
            if (!soldier.hasStrategyOverride()) {
                soldier.adoptStrategyFrom(PlayerCommandState.stateForCommand(soldier, state));
            }
            PlayerCommandState.SquadState mine = soldier.loneState();

            switch (payload.action()) {
                case ACTION_MOVEMENT -> {
                    soldier.setMovementOverride(PlayerCommandState.MovementMode.byId(payload.value()));
                    mine.setMovement(soldier.getMovementOverride());
                }
                case ACTION_ATTACK -> {
                    soldier.setAttackOverride(PlayerCommandState.AttackMode.byId(payload.value()));
                    mine.setAttackMode(soldier.getAttackOverride());
                }
                case ACTION_FORMATION -> {
                    soldier.setFormationOverride(
                            PlayerCommandState.FormationMode.byId(payload.value()));
                    mine.setFormation(soldier.getFormationOverride());
                }
                case ACTION_RIDE -> {
                    soldier.setRideOverride(PlayerCommandState.RideMode.byId(payload.value()));
                    mine.setRideMode(soldier.getRideOverride());
                }
                default -> { }
            }
            // 界面上改策略也是一条玩家命令：让这个单位（以及整支军队）按新策略行动 ——
            //   否则"还没接受过命令"的单位会把界面里设的策略当成没下过
            soldier.markPlayerCommanded();
            state.issueOrder(player.level().getGameTime());
        });
    }
}
