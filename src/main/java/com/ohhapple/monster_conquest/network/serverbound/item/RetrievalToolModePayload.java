package com.ohhapple.monster_conquest.network.serverbound.item;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record RetrievalToolModePayload() implements CustomPacketPayload {

    public static final Type<RetrievalToolModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "retrieval_tool_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetrievalToolModePayload> STREAM_CODEC =
            StreamCodec.unit(new RetrievalToolModePayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static final Map<UUID, Long> LAST_SWITCH = new ConcurrentHashMap<>();
    private static final int SWITCH_COOLDOWN_TICKS = 3;

    public static void handle(RetrievalToolModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof RetrievalToolItem)) return;

            long now = player.level().getGameTime();
            Long last = LAST_SWITCH.get(player.getUUID());
            if (last != null && now - last < SWITCH_COOLDOWN_TICKS) return;
            if (LAST_SWITCH.size() > 256) {
                // 限流表按玩家 UUID 记录：人多了定期清掉长时间没动作的条目，避免越攒越大
                LAST_SWITCH.entrySet().removeIf(e -> now - e.getValue() > 2400L);
            }
            LAST_SWITCH.put(player.getUUID(), now);

            RetrievalToolItem.cycleMode(player, stack);
            player.inventoryMenu.broadcastChanges();
        });
    }
}