package com.ohhapple.monster_conquest.network.serverbound.egg;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.CommanderSpawnEggItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.item.SoldierSpawnEggItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record ToggleEggCavalryPayload() implements CustomPacketPayload {
    public static final Type<ToggleEggCavalryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "toggle_egg_cavalry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleEggCavalryPayload> STREAM_CODEC =
            StreamCodec.unit(new ToggleEggCavalryPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static final Map<UUID, Long> LAST_SWITCH = new ConcurrentHashMap<>();
    private static final int SWITCH_COOLDOWN_TICKS = 3;

    public static void handle(ToggleEggCavalryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();

            boolean isSoldier = stack.getItem() instanceof SoldierSpawnEggItem;
            boolean isCommander = stack.getItem() instanceof CommanderSpawnEggItem;
            if (!isSoldier && !isCommander) return;

            if (!player.isCreative()) {
                player.displayClientMessage(
                        Component.translatable("gui.monster_conquest.egg.creative_only"), true);
                return;
            }

            long now = player.level().getGameTime();
            Long last = LAST_SWITCH.get(player.getUUID());
            if (last != null && now - last < SWITCH_COOLDOWN_TICKS) return;
            if (LAST_SWITCH.size() > 256) {
                // 限流表按玩家 UUID 记录：人多了定期清掉长时间没动作的条目，避免越攒越大
                LAST_SWITCH.entrySet().removeIf(e -> now - e.getValue() > 2400L);
            }
            LAST_SWITCH.put(player.getUUID(), now);

            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            boolean cavalry = tag.getBoolean(RetrievalToolItem.KEY_CAVALRY);
            boolean nowCav = !cavalry;
            tag.putBoolean(RetrievalToolItem.KEY_CAVALRY, nowCav);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            player.displayClientMessage(Component.translatable(
                    "gui.monster_conquest.egg.cavalry_toggled",
                    Component.translatable(nowCav
                            ? "tooltip.monster_conquest.form.cavalry"
                            : "tooltip.monster_conquest.form.infantry")), true);

            player.inventoryMenu.broadcastChanges();
        });
    }
}