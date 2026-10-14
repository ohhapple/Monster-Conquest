package com.ohhapple.monster_conquest.network.clientbound.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 服务端 → 客户端：同步战术装备栏内容（HUD 统计备弹用）。 */
public record SyncEquipmentPayload(List<ItemStack> items) implements CustomPacketPayload {

    public static final Type<SyncEquipmentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "sync_equipment"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEquipmentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC, SyncEquipmentPayload::items,
                    SyncEquipmentPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncEquipmentPayload payload, IPayloadContext ctx) {
        // 占位：实际处理见 ClientboundPayloadRouter → ClientPayloadDispatcher
    }
}
