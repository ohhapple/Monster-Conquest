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

public record FactionAllySnapshotPayload(Map<String, Set<String>> ally)
        implements CustomPacketPayload {

    public static final Type<FactionAllySnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "faction_ally_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionAllySnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FactionAllySnapshotPayload decode(RegistryFriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    Map<String, Set<String>> map = new HashMap<>();
                    for (int i = 0; i < n; i++) {
                        String k = ByteBufCodecs.STRING_UTF8.decode(buf);
                        int m = buf.readVarInt();
                        Set<String> set = new HashSet<>();
                        for (int j = 0; j < m; j++) set.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                        map.put(k, set);
                    }
                    return new FactionAllySnapshotPayload(map);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FactionAllySnapshotPayload v) {
                    buf.writeVarInt(v.ally().size());
                    for (var e : v.ally().entrySet()) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, e.getKey());
                        buf.writeVarInt(e.getValue().size());
                        for (String s : e.getValue()) ByteBufCodecs.STRING_UTF8.encode(buf, s);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
