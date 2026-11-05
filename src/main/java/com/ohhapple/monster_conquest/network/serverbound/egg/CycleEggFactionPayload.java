package com.ohhapple.monster_conquest.network.serverbound.egg;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.item.SoldierSpawnEggItem;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.game.ArenaDimensions;
import com.ohhapple.monster_conquest.util.FactionDisplay;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CycleEggFactionPayload(int direction) implements CustomPacketPayload {
    public static final Type<CycleEggFactionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "cycle_egg_faction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleEggFactionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CycleEggFactionPayload::direction,
                    CycleEggFactionPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 向后一个阵营（中键、Shift + 滚轮下滑）。 */
    public static CycleEggFactionPayload next() { return new CycleEggFactionPayload(1); }

    /** 向前一个阵营（Shift + 滚轮上滑）。 */
    public static CycleEggFactionPayload previous() { return new CycleEggFactionPayload(-1); }

    private static final List<ResourceLocation> CYCLE = List.of(
            FactionIds.UNDEAD, FactionIds.VILLAGER, FactionIds.ILLAGER,
            FactionIds.NETHER, FactionIds.END, FactionIds.MONSTER, FactionIds.NEUTRAL
    );

    private static final Map<UUID, Long> LAST_SWITCH = new ConcurrentHashMap<>();
    private static final int SWITCH_COOLDOWN_TICKS = 3;

    public static void handle(CycleEggFactionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (ArenaDimensions.isArena(player)) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof SoldierSpawnEggItem)) return;

            long now = player.level().getGameTime();
            Long last = LAST_SWITCH.get(player.getUUID());
            if (last != null && now - last < SWITCH_COOLDOWN_TICKS) return;
            if (LAST_SWITCH.size() > 256) {
                // 限流表按玩家 UUID 记录：人多了定期清掉长时间没动作的条目，避免越攒越大
                LAST_SWITCH.entrySet().removeIf(e -> now - e.getValue() > 2400L);
            }
            LAST_SWITCH.put(player.getUUID(), now);

            var tag0 = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            boolean fromTool = RetrievalToolItem.isFromTool(tag0);
            ResourceLocation currentFaction = SoldierSpawnEggItem.getEggFaction(stack);

            boolean changed = false;

            if (player.isCreative()) {
                // 玩家阵营始终排在第一：新拿到的蛋（无阵营数据）第一次切换
                //   就一定落到"使用者自己的玩家阵营"，多人服务器各自独立。
                List<ResourceLocation> cycle = new ArrayList<>(CYCLE.size() + 1);
                cycle.add(PlayerFactionRegistry.factionIdOf(player));
                cycle.addAll(CYCLE);

                // 新蛋（无阵营数据）默认就是使用者自己的玩家阵营：
                //   第一次切换直接跳到"下一个"，不再重复停在玩家阵营。
                int idx = currentFaction == null ? 0 : cycle.indexOf(currentFaction);
                int next;
                if (idx < 0) {
                    next = 0;
                } else {
                    int step = payload.direction() >= 0 ? 1 : -1;
                    next = Math.floorMod(idx + step, cycle.size());
                }
                ResourceLocation target = cycle.get(next);

                if (FactionIds.isPlayer(target)) {
                    FactionRegistry.getOrCreatePlayer(player.getUUID());
                }

                var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putString(RetrievalToolItem.KEY_FACTION, target.toString());
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                changed = true;

                Component display = factionDisplayName(target, player.getServer());
                player.displayClientMessage(
                        Component.translatable("gui.monster_conquest.egg.switched", display), true);
            } else {
                if (!fromTool) {
                    player.displayClientMessage(
                            Component.translatable("gui.monster_conquest.egg.creative_only"), true);
                } else if (currentFaction == null || !FactionIds.isPlayer(currentFaction)) {
                    player.displayClientMessage(
                            Component.translatable("gui.monster_conquest.egg.not_player_faction"), true);
                } else {
                    UUID currentOwner = FactionIds.parsePlayer(currentFaction);
                    if (currentOwner == null || currentOwner.equals(player.getUUID())) {
                        player.displayClientMessage(
                                Component.translatable("gui.monster_conquest.egg.already_own"), true);
                    } else {
                        ResourceLocation own = PlayerFactionRegistry.factionIdOf(player);
                        FactionRegistry.getOrCreatePlayer(player.getUUID());

                        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                        tag.putString(RetrievalToolItem.KEY_FACTION, own.toString());
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        changed = true;

                        Component display = factionDisplayName(own, player.getServer());
                        player.displayClientMessage(
                                Component.translatable("gui.monster_conquest.egg.taken_over", display), true);
                    }
                }
            }

            if (changed) {
                player.inventoryMenu.broadcastChanges();
            }
        });
    }

    /**
     * 阵营显示名（返回 Component，交给客户端按自己的语言翻译）：
     *   只在这里解析"玩家名"这类与语言无关的信息 —— 专用服务端没有语言文件，
     *   在服务端 getString() 只会拿到翻译键。
     */
    public static Component factionDisplayName(@Nullable ResourceLocation id, MinecraftServer server) {
        if (id == null) return Component.literal("?");
        UUID owner = FactionIds.parsePlayer(id);
        if (owner != null && server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(owner);
            if (online != null) {
                return Component.translatable("faction.monster_conquest.player_short",
                        online.getGameProfile().getName());
            }
            var cache = server.getProfileCache();
            if (cache != null) {
                var opt = cache.get(owner);
                if (opt.isPresent()) {
                    return Component.translatable("faction.monster_conquest.player_short",
                            opt.get().getName());
                }
            }
        }
        return FactionDisplay.displayName(id);
    }
}
