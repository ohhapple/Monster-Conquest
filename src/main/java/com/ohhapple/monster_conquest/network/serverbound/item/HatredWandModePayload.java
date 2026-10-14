package com.ohhapple.monster_conquest.network.serverbound.item;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.HatredWandItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record HatredWandModePayload(boolean reset) implements CustomPacketPayload {
    public static final Type<HatredWandModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "hatred_wand_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HatredWandModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, HatredWandModePayload::reset,
                    HatredWandModePayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 每玩家模式切换冷却。防止客户端一次点击发 2 个 payload 导致"回弹"。 */
    private static final Map<UUID, Long> LAST_SWITCH = new ConcurrentHashMap<>();
    private static final int SWITCH_COOLDOWN_TICKS = 3;

    public static void handle(HatredWandModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof HatredWandItem wand)) return;

            long now = player.level().getGameTime();
            Long last = LAST_SWITCH.get(player.getUUID());
            if (last != null && now - last < SWITCH_COOLDOWN_TICKS) return;
            if (LAST_SWITCH.size() > 256) {
                // 限流表按玩家 UUID 记录：人多了定期清掉长时间没动作的条目，避免越攒越大
                LAST_SWITCH.entrySet().removeIf(e -> now - e.getValue() > 2400L);
            }
            LAST_SWITCH.put(player.getUUID(), now);

            if (payload.reset()) {
                if (player.level() instanceof ServerLevel sl) wand.resetHostility(player, sl);
            } else {
                HatredWandItem.toggleMode(player, stack);
            }
            player.inventoryMenu.broadcastChanges();
        });
    }
}