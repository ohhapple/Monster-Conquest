package com.ohhapple.monster_conquest.network.serverbound.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.item.AmmoBoxItem;
import com.ohhapple.monster_conquest.menu.AmmoBoxMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：在背包 / 战术装备栏界面里右键弹药箱，打开它的容器。 */
public record OpenAmmoBoxFromSlotPayload(int source, int index) implements CustomPacketPayload {

    public static final Type<OpenAmmoBoxFromSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "open_ammo_box_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAmmoBoxFromSlotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenAmmoBoxFromSlotPayload::source,
                    ByteBufCodecs.VAR_INT, OpenAmmoBoxFromSlotPayload::index,
                    OpenAmmoBoxFromSlotPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenAmmoBoxFromSlotPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack box = TacticalEquipmentSlotsData.resolveBox(player, payload.source(), payload.index());
            if (!(box.getItem() instanceof AmmoBoxItem)) return;

            int source = payload.source();
            int index = payload.index();
            player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new AmmoBoxMenu(id, inv, source, index),
                            Component.translatable("item.monster_conquest.ammo_box")),
                    buf -> {
                        buf.writeVarInt(source);
                        buf.writeVarInt(index);
                    });
        });
    }
}
