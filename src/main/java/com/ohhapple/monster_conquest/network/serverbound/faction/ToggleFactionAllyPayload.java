package com.ohhapple.monster_conquest.network.serverbound.faction;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
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
 * 客户端 → 服务端：A 单向加入/移除结盟 B。
 *
 * 权限与敌对切换一致：普通玩家只能修改与自己玩家阵营相关的关系；
 *   NPC 阵营页面里点"所有玩家阵营"行会落成"该阵营 ↔ 自己"。
 */
public record ToggleFactionAllyPayload(String a, String b) implements CustomPacketPayload {

    public static final Type<ToggleFactionAllyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "toggle_faction_ally"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFactionAllyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ToggleFactionAllyPayload::a,
                    ByteBufCodecs.STRING_UTF8, ToggleFactionAllyPayload::b,
                    ToggleFactionAllyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleFactionAllyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            try {
                ResourceLocation a = ResourceLocation.tryParse(payload.a());
                ResourceLocation b = ResourceLocation.tryParse(payload.b());
                if (a == null || b == null || a.equals(b)) return;

                boolean isOpCreative = player.hasPermissions(2) && player.isCreative();
                if (!isOpCreative) {
                    ResourceLocation own = PlayerFactionRegistry.factionIdOf(player);
                    // 与敌对关系同一套权限语义：NPC 阵营页面点"所有玩家阵营"行 →
                    //   落成"该阵营不攻击我自己"（单向结盟），普通玩家点了才有反应。
                    if (FactionIds.isBigPlayer(a) && !FactionIds.isAnyPlayerFaction(b)) {
                        a = own;
                    } else if (FactionIds.isBigPlayer(b) && !FactionIds.isAnyPlayerFaction(a)) {
                        b = own;
                    }
                    if (FactionIds.isBigPlayer(a) || FactionIds.isBigPlayer(b)
                            || (!a.equals(own) && !b.equals(own))) {
                        player.displayClientMessage(
                                Component.translatable("gui.monster_conquest.faction.no_permission"), true);
                        return;
                    }
                }

                ResourceLocation first = a;
                ResourceLocation second = b;
                boolean removed = FactionAlly.isAlly(first, second);
                if (removed) FactionAlly.clearAlly(first, second);
                else FactionAlly.setAlly(first, second);

                player.displayClientMessage(Component.translatable(
                        removed
                                ? "msg.monster_conquest.faction.ally_removed"
                                : "msg.monster_conquest.faction.ally_set",
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
