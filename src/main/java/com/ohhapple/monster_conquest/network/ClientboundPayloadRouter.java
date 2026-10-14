package com.ohhapple.monster_conquest.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

/**
 * 客户端 payload 的安全路由。
 *
 * 本类不 import / 引用任何 net.minecraft.client.* 或 client 子包。
 *   → 专用服务端加载本类时不会触发 RuntimeDistCleaner。
 *
 * 客户端实际逻辑通过反射延迟加载 ClientPayloadDispatcher。
 *   首次成功后缓存 Method，后续调用零反射开销。
 */
public final class ClientboundPayloadRouter {

    private ClientboundPayloadRouter() {}

    private static final String DISPATCHER_CLASS =
            "com.ohhapple.monster_conquest.client.ClientPayloadDispatcher";

    /** 缓存反射 Method：首次成功后不再走 Class.forName + getMethod。 */
    private static volatile Method cachedDispatch;
    /** 反射失败标记：一旦失败就不重复尝试。 */
    private static volatile boolean reflectionFailed = false;

    public static void dispatch(CustomPacketPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        if (reflectionFailed) return;

        Method m = cachedDispatch;
        if (m == null) {
            synchronized (ClientboundPayloadRouter.class) {
                m = cachedDispatch;
                if (m == null) {
                    try {
                        Class<?> cls = Class.forName(DISPATCHER_CLASS);
                        m = cls.getMethod("dispatch",
                                CustomPacketPayload.class, IPayloadContext.class);
                        cachedDispatch = m;
                    } catch (Throwable t) {
                        reflectionFailed = true;
                        return;
                    }
                }
            }
        }

        try {
            m.invoke(null, payload, ctx);
        } catch (Throwable ignored) {
            // 单次调用失败不影响后续（例如内部异常）
        }
    }
}
