package com.ohhapple.monster_conquest.client;

import com.ohhapple.monster_conquest.network.ModVersionInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 保存配置阶段收到的服务端版本，并在进入世界后提示一次。 */
public final class ModVersionClientState {

    @Nullable private static String serverVersion;

    private ModVersionClientState() {
    }

    public static void receiveServerVersion(String version) {
        serverVersion = version == null ? "unknown" : version.trim();
    }

    public static void showWarningIfNeeded(LocalPlayer player) {
        String received = serverVersion;
        serverVersion = null;
        if (received == null || received.isBlank()) return;
        String clientVersion = ModVersionInfo.currentVersion();
        if (received.equals(clientVersion)) return;
        player.displayClientMessage(Component.translatable(
                "message.monster_conquest.mod_version_mismatch",
                clientVersion, received), false);
    }

    public static void clear() {
        serverVersion = null;
    }
}
