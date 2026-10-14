package com.ohhapple.monster_conquest.network.clientbound.faction;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 服务端 → 客户端：发送当前所有阵营的敌对关系快照。
 */
public record FactionHostilitySnapshotPayload(Map<String, Set<String>> hostility)
        implements CustomPacketPayload {

    public static final Type<FactionHostilitySnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "faction_hostility_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionHostilitySnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FactionHostilitySnapshotPayload decode(RegistryFriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    Map<String, Set<String>> map = new HashMap<>();
                    for (int i = 0; i < n; i++) {
                        String k = ByteBufCodecs.STRING_UTF8.decode(buf);
                        int m = buf.readVarInt();
                        Set<String> set = new HashSet<>();
                        for (int j = 0; j < m; j++) set.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                        map.put(k, set);
                    }
                    return new FactionHostilitySnapshotPayload(map);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FactionHostilitySnapshotPayload v) {
                    buf.writeVarInt(v.hostility().size());
                    for (var e : v.hostility().entrySet()) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, e.getKey());
                        buf.writeVarInt(e.getValue().size());
                        for (String s : e.getValue()) ByteBufCodecs.STRING_UTF8.encode(buf, s);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
