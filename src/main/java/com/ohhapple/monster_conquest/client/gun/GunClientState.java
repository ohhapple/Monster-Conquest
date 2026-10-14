package com.ohhapple.monster_conquest.client.gun;

import com.ohhapple.monster_conquest.client.rts.RtsClientState;
import com.ohhapple.monster_conquest.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** 客户端枪械状态：装填进度倒数、开镜变焦动画。 */
public final class GunClientState {

    private GunClientState() {}

    private static boolean reloading;
    private static int reloadTotal;
    private static int reloadLeft;

    /** 开镜变焦动画进度（0 = 未开镜，1 = 完全放大）。 */
    private static float scopeZoom;

    /** 开镜时降低鼠标灵敏度的原始值（-1 = 未修改）。 */
    private static double savedSensitivity = -1.0;

    public static void onStatus(boolean reloadingIn, int total, int left) {
        reloading = reloadingIn;
        reloadTotal = total;
        reloadLeft = left;
    }

    public static void reset() {
        reloading = false;
        reloadTotal = reloadLeft = 0;
        scopeZoom = 0.0f;
    }

    public static void tick() {
        // 开镜状态下做"换视角"操作（进入 RTS / 切第三人称 / 打开界面）→ 自动退出狙击镜：
        //   主动松开"使用中"状态（会同步给服务端），否则 RTS 里会一直带着开镜的
        //   放大倍率和降低过的鼠标灵敏度。
        Minecraft mc0 = Minecraft.getInstance();
        LocalPlayer p0 = mc0.player;
        if (p0 != null && p0.isUsingItem() && viewChanged(mc0)) {
            ItemStack main = p0.getMainHandItem();
            if (main.getItem() instanceof GunItem gun && gun.isScoping(p0, main)) {
                forceExitScope(mc0);
            }
        }

        tickScopeSensitivity();

        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) { reset(); return; }

        if (!reloading) return;
        reloadLeft--;
        if (reloadLeft <= 0) {
            reloading = false;
            reloadLeft = 0;
        }
    }

    /** 开镜变焦动画：按帧插值（和原版望远镜一样短时间快速放大）。 */
    public static void updateScopeZoom(float deltaTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { scopeZoom = 0.0f; return; }

        boolean scoping = isScopingNow(mc, p);

        float target = scoping ? 1.0f : 0.0f;
        float factor = Mth.clamp(0.5f * deltaTicks, 0.0f, 1.0f);
        scopeZoom = Mth.lerp(factor, scopeZoom, target);
        if (target == 0.0f && scopeZoom < 0.02f) scopeZoom = 0.0f;
        if (target == 1.0f && scopeZoom > 0.98f) scopeZoom = 1.0f;
    }

    public static boolean isReloading() { return reloading; }

    public static float reloadProgress() {
        if (!reloading || reloadTotal <= 0) return 0.0f;
        return Mth.clamp(1.0f - (float) reloadLeft / reloadTotal, 0.0f, 1.0f);
    }

    public static float scopeZoom() { return scopeZoom; }

    /** 当前是否真的处于"开镜"状态：狙击枪在手中、正在右键开镜，且处于普通第一人称视角
     *  （RTS 视角 / 第三人称 / 打开任何界面时都不算开镜）。 */
    private static boolean isScopingNow(Minecraft mc, LocalPlayer p) {
        if (viewChanged(mc)) return false;
        ItemStack main = p.getMainHandItem();
        return main.getItem() instanceof GunItem gun && gun.isScoping(p, main);
    }

    /** 是否处于"开镜不该生效"的视角：RTS 模式 / 第三人称 / 有界面打开。 */
    private static boolean viewChanged(Minecraft mc) {
        return RtsClientState.isRtsMode()
                || mc.screen != null
                || !mc.options.getCameraType().isFirstPerson();
    }

    /**
     * 强制退出狙击镜（进入 RTS、切换视角等"换视角"操作时调用）：
     *   松开"使用物品"（客户端 + 服务端一起退出举镜），并把变焦动画和
     *   鼠标灵敏度立刻还原到正常值。
     */
    public static void forceExitScope(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p != null) {
            ItemStack main = p.getMainHandItem();
            if (main.getItem() instanceof GunItem gun && gun.isScoping(p, main)) {
                if (mc.gameMode != null) {
                    // 和松开右键走同一条路：本地停止使用 + 发包告诉服务端。
                    mc.gameMode.releaseUsingItem(p);
                } else {
                    p.stopUsingItem();
                }
            }
        }
        scopeZoom = 0.0f;
        if (savedSensitivity >= 0.0) {
            mc.options.sensitivity().set(savedSensitivity);
            savedSensitivity = -1.0;
        }
    }

    /** 开镜时把鼠标灵敏度降到 35%，避免高倍镜下太难瞄准。 */
    private static void tickScopeSensitivity() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean scoping = p != null && isScopingNow(mc, p);

        if (scoping) {
            if (savedSensitivity < 0.0) {
                savedSensitivity = mc.options.sensitivity().get();
                mc.options.sensitivity().set(savedSensitivity * 0.35);
            }
        } else if (savedSensitivity >= 0.0) {
            mc.options.sensitivity().set(savedSensitivity);
            savedSensitivity = -1.0;
        }
    }
}
