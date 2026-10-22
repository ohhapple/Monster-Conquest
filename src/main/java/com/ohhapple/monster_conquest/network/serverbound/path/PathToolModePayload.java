package com.ohhapple.monster_conquest.network.serverbound.path;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.PathToolItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：切换路径点工具的模式（0 = 巡逻 / 路线，1 = 军队 / 单个）。 */
public record PathToolModePayload(int kind) implements CustomPacketPayload {

    public static final int KIND_PATH = 0;
    public static final int KIND_SELECT = 1;

    public static final Type<PathToolModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "path_tool_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PathToolModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PathToolModePayload::kind,
                    PathToolModePayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PathToolModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof PathToolItem)) return;
            if (payload.kind() == KIND_SELECT) {
                PathToolItem.cycleSelectMode(player, stack);
            } else {
                PathToolItem.cyclePathMode(player, stack);
            }
            player.inventoryMenu.broadcastChanges();
        });
    }
}
