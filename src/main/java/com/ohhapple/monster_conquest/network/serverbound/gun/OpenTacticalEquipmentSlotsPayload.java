package com.ohhapple.monster_conquest.network.serverbound.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.menu.TacticalEquipmentSlotsMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：打开战术装备栏界面。 */
public record OpenTacticalEquipmentSlotsPayload() implements CustomPacketPayload {

    public static final Type<OpenTacticalEquipmentSlotsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "open_tactical_equipment_slots"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTacticalEquipmentSlotsPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenTacticalEquipmentSlotsPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenTacticalEquipmentSlotsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new TacticalEquipmentSlotsMenu(id, inv),
                            Component.translatable("gui.monster_conquest.tactical_equipment_slots")),
                    buf -> buf.writeVarInt(0));
        });
    }
}
