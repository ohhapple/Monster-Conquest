package com.ohhapple.monster_conquest.client;

import com.ohhapple.monster_conquest.client.gui.CommandWheelScreen;
import com.ohhapple.monster_conquest.client.gui.FactionGuiClientEvents;
import com.ohhapple.monster_conquest.client.gui.RecruitStationScreen;
import com.ohhapple.monster_conquest.client.gui.TradeStationScreen;
import com.ohhapple.monster_conquest.client.gun.GunClientState;
import com.ohhapple.monster_conquest.client.path.ClientPathPreview;
import com.ohhapple.monster_conquest.client.rts.RtsClientState;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionAllySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionHostilitySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.GunStatusPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.SyncEquipmentPayload;
import com.ohhapple.monster_conquest.network.clientbound.path.SyncPathRoutePayload;
import com.ohhapple.monster_conquest.network.clientbound.recruit.OpenRecruitGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncSquadPayload;
import com.ohhapple.monster_conquest.network.clientbound.trade.OpenTradeGuiPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadDispatcher {

    private ClientPayloadDispatcher() {}

    public static void dispatch(CustomPacketPayload payload, IPayloadContext ctx) {
        if (payload instanceof FactionHostilitySnapshotPayload p) {
            FactionGuiClientEvents.handleSnapshot(p, ctx);
        } else if (payload instanceof FactionAllySnapshotPayload p) {
            FactionGuiClientEvents.handleAllySnapshot(p, ctx);
        } else if (payload instanceof OpenFactionGuiPayload p) {
            FactionGuiClientEvents.handleOpen(p, ctx);
        } else if (payload instanceof SyncSquadPayload p) {
            CommandWheelScreen.onServerSyncSquad(p.squad());
        } else if (payload instanceof SyncRtsSelectionPayload p) {
            RtsClientState.setSelected(p.soldierUuids());
            RtsClientState.setMoveMarker(p.moveMarker());
            RtsClientState.setFeedback(p.feedback());
        } else if (payload instanceof GunStatusPayload p) {
            GunClientState.onStatus(p.reloading(), p.totalTicks(), p.ticksLeft());
        } else if (payload instanceof SyncEquipmentPayload p) {
            TacticalEquipmentSlotsData.setClientTacticalSlots(p.items());
        } else if (payload instanceof OpenRecruitGuiPayload p) {
            Minecraft.getInstance().setScreen(
                    new RecruitStationScreen(p.pos()));
        } else if (payload instanceof OpenTradeGuiPayload p) {
            Minecraft.getInstance().setScreen(
                    new TradeStationScreen(p.pos()));
        } else if (payload instanceof SyncPathRoutePayload p) {
            ClientPathPreview.set(p.mode(), p.points());
        }
    }
}
