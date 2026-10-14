package com.ohhapple.monster_conquest.network.serverbound.faction;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.relation.FactionHostility;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import com.ohhapple.monster_conquest.network.FactionGuiNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：切换两个阵营的敌对状态。
 *
 * 权限：
 *  - OP + 创造模式：可切换任意阵营（含大玩家阵营）
 *  - 其余情况：只能修改与"自己小玩家阵营"相关的关系；
 *    NPC 阵营页面里点"所有玩家阵营"行会落成"该阵营 ↔ 自己"
 */
public record ToggleFactionHostilityPayload(String a, String b) implements CustomPacketPayload {

    public static final Type<ToggleFactionHostilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "toggle_faction_hostility"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFactionHostilityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ToggleFactionHostilityPayload::a,
                    ByteBufCodecs.STRING_UTF8, ToggleFactionHostilityPayload::b,
                    ToggleFactionHostilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleFactionHostilityPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            try {
                ResourceLocation a = ResourceLocation.tryParse(payload.a());
                ResourceLocation b = ResourceLocation.tryParse(payload.b());
                if (a == null || b == null || a.equals(b)) return;

                boolean isOpCreative = player.hasPermissions(2) && player.isCreative();
                if (!isOpCreative) {
                    ResourceLocation ownFaction = PlayerFactionRegistry.factionIdOf(player);
                    // 普通玩家点 NPC 阵营页面里的"所有玩家阵营"行：落成"该阵营 ↔ 自己"。
                    //   语义就是"这个阵营敌对我的玩家阵营"，也是玩家唯一有权修改的范围；
                    //   不转换的话普通玩家点了不会有任何反应，只会以为设置没生效。
                    if (FactionIds.isBigPlayer(a) && !FactionIds.isAnyPlayerFaction(b)) {
                        a = ownFaction;
                    } else if (FactionIds.isBigPlayer(b) && !FactionIds.isAnyPlayerFaction(a)) {
                        b = ownFaction;
                    }
                    // 其余涉及大玩家阵营的组合（例如"我 ↔ 所有玩家阵营"）没有意义，仍不允许；
                    //   不涉及自己的关系（如"亡灵 ↔ 村民"）同样不允许普通玩家改。
                    if (FactionIds.isBigPlayer(a) || FactionIds.isBigPlayer(b)
                            || (!a.equals(ownFaction) && !b.equals(ownFaction))) {
                        player.displayClientMessage(
                                Component.translatable("gui.monster_conquest.faction.no_permission"), true);
                        return;
                    }
                }

                // 以"显式存过的这一对"决定加 / 减：只靠配置或大玩家阵营继承实际已经敌对的组合，
                //   第一次点击应该把它显式写下来（否则会被当成"取消"而毫无反应，GUI 里永远设不上）。
                ResourceLocation first = a;
                ResourceLocation second = b;
                boolean removed = FactionHostility.isExplicitHostile(first, second);
                if (removed) {
                    FactionHostility.clearHostile(first, second);
                } else {
                    FactionHostility.setHostile(first, second);
                }

                // 每次切换都回执：玩家能立刻确认这次点击是否落库、落成了哪一对关系。
                player.displayClientMessage(Component.translatable(
                        removed
                                ? "msg.monster_conquest.faction.hostility_removed"
                                : "msg.monster_conquest.faction.hostility_set",
                        FactionGuiNetwork.describeFaction(first, player),
                        FactionGuiNetwork.describeFaction(second, player)), true);
            } finally {
                if (ctx.player() instanceof ServerPlayer sp) {
                    FactionGuiNetwork.sendSnapshot(sp);
                }
            }
        });
    }
}
