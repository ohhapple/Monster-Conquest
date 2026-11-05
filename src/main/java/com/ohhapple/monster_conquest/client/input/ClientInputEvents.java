package com.ohhapple.monster_conquest.client.input;

import com.ohhapple.monster_conquest.client.gui.CommandWheelScreen;
import com.ohhapple.monster_conquest.client.gui.GuideBookScreen;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaLobbyScreen;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaCompendiumScreen;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaPlayerScreen;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaGameClientState;
import com.ohhapple.monster_conquest.arena.game.ArenaDimensions;
import com.ohhapple.monster_conquest.config.ArenaConfig;
import com.ohhapple.monster_conquest.client.rts.RtsClientState;
import com.ohhapple.monster_conquest.client.rts.RtsClientTicker;
import com.ohhapple.monster_conquest.client.rts.RtsScreen;
import com.ohhapple.monster_conquest.item.CommanderSpawnEggItem;
import com.ohhapple.monster_conquest.item.ControlToolItem;
import com.ohhapple.monster_conquest.item.GuideBookItem;
import com.ohhapple.monster_conquest.item.HatredWandItem;
import com.ohhapple.monster_conquest.item.PathToolItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.item.SoldierSpawnEggItem;
import com.ohhapple.monster_conquest.item.SpawnPointBlockItem;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.serverbound.backpack.OpenEquippedBackpackPayload;
import com.ohhapple.monster_conquest.network.serverbound.egg.CycleEggFactionPayload;
import com.ohhapple.monster_conquest.network.serverbound.egg.ToggleEggCavalryPayload;
import com.ohhapple.monster_conquest.network.serverbound.faction.RequestOpenFactionGuiPayload;
import com.ohhapple.monster_conquest.network.serverbound.gun.OpenTacticalEquipmentSlotsPayload;
import com.ohhapple.monster_conquest.network.serverbound.item.ControlToolModePayload;
import com.ohhapple.monster_conquest.network.serverbound.item.CrosshairSelectPayload;
import com.ohhapple.monster_conquest.network.serverbound.item.HatredWandModePayload;
import com.ohhapple.monster_conquest.network.serverbound.item.RetrievalToolModePayload;
import com.ohhapple.monster_conquest.network.serverbound.path.PathToolModePayload;
import com.ohhapple.monster_conquest.network.serverbound.path.PathToolSelectPayload;
import com.ohhapple.monster_conquest.network.serverbound.spawn.OpenSpawnPointEditorPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class ClientInputEvents {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        boolean arenaContext = ArenaDimensions.isArena(
                minecraft.player.level(), minecraft.player.blockPosition())
                || isArenaServerConnection(minecraft);
        if (!arenaContext) return;
        if (isAllowedArenaScreen(event.getNewScreen())) return;
        event.setCanceled(true);
    }

    static boolean isAllowedArenaScreen(Screen screen) {
        return screen == null
                || screen instanceof ArenaPlayerScreen
                || screen instanceof ArenaCompendiumScreen
                || screen instanceof ChatScreen
                || screen instanceof ConnectScreen
                || screen instanceof DisconnectedScreen
                || screen instanceof PauseScreen
                || screen instanceof ReceivingLevelScreen
                || screen instanceof TitleScreen;
    }

    /** 手持说明书右键 → 打开说明书界面（纯客户端）。 */
    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (ArenaDimensions.isArena(player.level(), player.blockPosition())) return;
        if (!(player.getMainHandItem().getItem() instanceof GuideBookItem)) return;
        GuideBookScreen.open();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean arenaContext = ArenaDimensions.isArena(
                mc.player.level(), mc.player.blockPosition())
                || ArenaGameClientState.isArenaSessionContext()
                || isArenaServerConnection(mc);

        while (ModKeyMappings.OPEN_ARENA_LOBBY.consumeClick()) {
            if (mc.screen != null) continue;
            if (arenaContext) {
                ArenaPlayerScreen.open();
            } else {
                ArenaLobbyScreen.open();
            }
        }

        while (ModKeyMappings.OPEN_FACTION_GUI.consumeClick()) {
            if (arenaContext || mc.screen != null) continue;
            PacketDistributor.sendToServer(new RequestOpenFactionGuiPayload());
        }

        while (ModKeyMappings.OPEN_EQUIPMENT.consumeClick()) {
            if (arenaContext || mc.screen != null) continue;
            PacketDistributor.sendToServer(new OpenTacticalEquipmentSlotsPayload());
        }

        // 打开战术装备栏里的背包（默认 B，可在按键设置里改）
        while (ModKeyMappings.OPEN_BACKPACK.consumeClick()) {
            if (arenaContext || mc.screen != null) continue;
            PacketDistributor.sendToServer(
                    new OpenEquippedBackpackPayload());
        }

        if (arenaContext) {
            if (RtsClientState.isRtsMode()) {
                RtsClientTicker.exitRts(mc);
                if (mc.screen instanceof RtsScreen) mc.setScreen(null);
            }
            return;
        }

        int wheelKey = ModKeyMappings.COMMAND_WHEEL.getKey().getValue();
        if (event.getKey() == wheelKey && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null) {
                mc.setScreen(new CommandWheelScreen());
            }
        }

        // RTS 模式开关（可用控制设置改键）
        if (ModKeyMappings.RTS_TOGGLE.matches(event.getKey(), event.getScanCode())
                && event.getAction() == GLFW.GLFW_PRESS) {
            if (RtsClientState.isRtsMode()) {
                mc.setScreen(null);  // 触发 removed → exitRts
                return;
            }
            if (mc.screen != null) return;
            RtsClientTicker.enterRts(mc);
        }
    }

    private static boolean isArenaServerConnection(Minecraft minecraft) {
        if (minecraft.getCurrentServer() == null) return false;
        try {
            return ServerAddress.parseString(
                    minecraft.getCurrentServer().ip).getPort()
                    == ArenaConfig.arenaServerPort;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (ArenaDimensions.isArena(mc.player.level(), mc.player.blockPosition())) return;
        if (event.getAction() != 1) return;
        if (event.getButton() != 2) return;

        ItemStack stack = mc.player.getMainHandItem();

        // 手持路径点工具中键 → 选中准星指着的单位 / 军队 / 刷怪点
        if (stack.getItem() instanceof PathToolItem) {
            PacketDistributor.sendToServer(
                    new PathToolSelectPayload());
            event.setCanceled(true);
            return;
        }

        if (stack.getItem() instanceof SoldierSpawnEggItem) {
            PacketDistributor.sendToServer(CycleEggFactionPayload.next());
            event.setCanceled(true);
            return;
        }

        if (stack.getItem() instanceof HatredWandItem
                || stack.getItem() instanceof ControlToolItem) {
            PacketDistributor.sendToServer(new CrosshairSelectPayload());
            event.setCanceled(true);
        }
    }

    /** Shift + 滚轮：切换手中刷怪蛋的阵营（上滚向前、下滚向后）。 */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (ArenaDimensions.isArena(mc.player.level(), mc.player.blockPosition())) return;
        if (!mc.options.keyShift.isDown()) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof SoldierSpawnEggItem)) return;

        double delta = event.getScrollDeltaY();
        if (delta == 0.0) return;

        PacketDistributor.sendToServer(delta > 0.0
                ? CycleEggFactionPayload.previous()
                : CycleEggFactionPayload.next());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (ArenaDimensions.isArena(mc.player.level(), mc.player.blockPosition())) return;

        if (mc.crosshairPickEntity != null) return;

        ItemStack stack = mc.player.getMainHandItem();

        if (stack.getItem() instanceof HatredWandItem) {
            event.setCanceled(true);
            boolean reset = mc.player.isShiftKeyDown();
            PacketDistributor.sendToServer(new HatredWandModePayload(reset));
            return;
        }

        if (stack.getItem() instanceof ControlToolItem) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new ControlToolModePayload());
            return;
        }

        if (stack.getItem() instanceof RetrievalToolItem) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new RetrievalToolModePayload());
            return;
        }

        if (stack.getItem() instanceof SoldierSpawnEggItem
                || stack.getItem() instanceof CommanderSpawnEggItem) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new ToggleEggCavalryPayload());
            return;
        }

        // 左键手持的刷怪点方块：打开配置界面（仅创造模式，服务端校验）
        if (stack.getItem() instanceof SpawnPointBlockItem) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(
                    new OpenSpawnPointEditorPayload());
            return;
        }

        // 左键手持的路径点工具：切换巡逻 / 路线；蹲下左键：切换军队 / 单个
        if (stack.getItem() instanceof PathToolItem) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(
                    new PathToolModePayload(
                            mc.player.isShiftKeyDown()
                                    ? PathToolModePayload.KIND_SELECT
                                    : PathToolModePayload.KIND_PATH));
        }
    }
}
