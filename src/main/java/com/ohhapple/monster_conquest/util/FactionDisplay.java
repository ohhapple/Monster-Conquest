package com.ohhapple.monster_conquest.util;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 阵营显示名工具：把阵营 ID 转成可翻译的 Component。
 *  - 已知阵营（monster_conquest:undead 等）→ 走 faction.monster_conquest.<name> 翻译键
 *  - 玩家阵营 → "玩家 xxxxxxxx" 短 ID
 */
public final class FactionDisplay {

    private FactionDisplay() {}

    public static Component displayName(@Nullable ResourceLocation id) {
        if (id == null) return Component.literal("-");

        if (FactionIds.isPlayer(id)) {
            UUID owner = FactionIds.parsePlayer(id);
            String shortId = owner != null
                    ? owner.toString().substring(0, 8)
                    : id.getPath();
            return Component.translatable("faction.monster_conquest.player_short", shortId);
        }
        // 大玩家阵营（monster_conquest:player）：没有 UUID，单独走 player_all 文案
        if (FactionIds.isBigPlayer(id)) {
            return Component.translatable("faction.monster_conquest.player_all");
        }
        return Component.translatable("faction.monster_conquest." + id.getPath());
    }

    /**
     * 显示名（带兜底）：没注册过的阵营（例如没有语言条目的数据包阵营）
     *   直接显示 id 原文，避免界面上出现翻译键。
     */
    public static Component displayNameOrId(@Nullable ResourceLocation id) {
        if (id == null) return Component.literal("-");
        if (!FactionIds.isAnyPlayerFaction(id) && FactionRegistry.get(id) == null) {
            return Component.literal(id.toString());
        }
        return displayName(id);
    }
}
