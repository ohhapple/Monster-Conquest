package com.ohhapple.monster_conquest.network;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.neoforged.fml.ModList;

/** 双向版本提示共用的当前模组版本读取入口。 */
public final class ModVersionInfo {

    private ModVersionInfo() {
    }

    public static String currentVersion() {
        return ModList.get()
                .getModContainerById(Monster_conquest.MODID)
                .map(container -> container.getModInfo()
                        .getVersion().toString())
                .orElse("unknown");
    }
}
