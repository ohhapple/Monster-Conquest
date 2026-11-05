package com.ohhapple.monster_conquest.network;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.function.Consumer;

/**
 * 发送版本后立即结束，不等待客户端回应，因此版本提示不会阻塞连接。
 */
public record ModVersionConfigurationTask(
        ServerConfigurationPacketListener listener)
        implements ICustomConfigurationTask {

    public static final Type TYPE = new Type(
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "mod_version"));

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        sender.accept(new ModVersionHandshakePayload(
                ModVersionInfo.currentVersion()));
        listener.finishCurrentTask(TYPE);
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
