package com.ohhapple.monster_conquest.network.clientbound.path;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：把当前选中目标的巡逻路径同步给客户端（手持路径点工具时在世界上画出来）。
 *   本类不引用任何 net.minecraft.client.* 类；实际绘制由客户端渲染器处理。
 */
public record SyncPathRoutePayload(int mode, List<Vec3> points) implements CustomPacketPayload {

    public static final Type<SyncPathRoutePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "sync_path_route"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPathRoutePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SyncPathRoutePayload decode(RegistryFriendlyByteBuf buf) {
                    int mode = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<Vec3> points = new ArrayList<>(Math.max(0, count));
                    for (int i = 0; i < count; i++) {
                        points.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                    }
                    return new SyncPathRoutePayload(mode, points);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SyncPathRoutePayload payload) {
                    buf.writeVarInt(payload.mode());
                    buf.writeVarInt(payload.points().size());
                    for (Vec3 point : payload.points()) {
                        buf.writeDouble(point.x);
                        buf.writeDouble(point.y);
                        buf.writeDouble(point.z);
                    }
                }
            };

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 占位：实际处理见 ClientboundPayloadRouter → ClientPayloadDispatcher。 */
    public static void handle(SyncPathRoutePayload payload, IPayloadContext ctx) {
        // 空实现，仅供签名占位。
    }
}
