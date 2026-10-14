package com.ohhapple.monster_conquest.network.clientbound.sync;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.ClientboundPayloadRouter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端 → 客户端：
 *   · soldierUuids：当前选中所有小队里的士兵 UUID（渲染脚下绿框）
 *   · moveMarker：右键移动的目标位置（渲染方块黄框），null = 无
 */
public record SyncRtsSelectionPayload(Set<UUID> soldierUuids,
                                      @Nullable Vec3 moveMarker)
        implements CustomPacketPayload {

    public static final Type<SyncRtsSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "sync_rts_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRtsSelectionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SyncRtsSelectionPayload decode(RegistryFriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    Set<UUID> set = new LinkedHashSet<>(n);
                    for (int i = 0; i < n; i++) set.add(buf.readUUID());
                    boolean hasMarker = buf.readBoolean();
                    Vec3 marker = hasMarker
                            ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
                            : null;
                    return new SyncRtsSelectionPayload(set, marker);
                }
                @Override
                public void encode(RegistryFriendlyByteBuf buf, SyncRtsSelectionPayload p) {
                    Set<UUID> set = p.soldierUuids();
                    buf.writeVarInt(set.size());
                    for (UUID u : set) buf.writeUUID(u);
                    Vec3 m = p.moveMarker();
                    boolean hasMarker = m != null;
                    buf.writeBoolean(hasMarker);
                    if (hasMarker) {
                        buf.writeDouble(m.x);
                        buf.writeDouble(m.y);
                        buf.writeDouble(m.z);
                    }
                }
            };

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncRtsSelectionPayload payload, IPayloadContext ctx) {
        ClientboundPayloadRouter.dispatch(payload, ctx);
    }
}