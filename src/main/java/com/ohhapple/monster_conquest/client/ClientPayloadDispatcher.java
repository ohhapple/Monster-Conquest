package com.ohhapple.monster_conquest.client;

import com.ohhapple.monster_conquest.client.gui.CommandWheelScreen;
import com.ohhapple.monster_conquest.client.gui.FactionGuiClientEvents;
import com.ohhapple.monster_conquest.client.gun.GunClientState;
import com.ohhapple.monster_conquest.client.rts.RtsClientState;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionAllySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionHostilitySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.GunStatusPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.SyncEquipmentPayload;
import com.ohhapple.monster_conquest.network.clientbound.recruit.OpenRecruitGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.trade.OpenTradeGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncSquadPayload;
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
        } else if (payload instanceof GunStatusPayload p) {
            GunClientState.onStatus(p.reloading(), p.totalTicks(), p.ticksLeft());
        } else if (payload instanceof SyncEquipmentPayload p) {
            com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData.setClientTacticalSlots(p.items());
        } else if (payload instanceof OpenRecruitGuiPayload p) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.ohhapple.monster_conquest.client.gui.RecruitStationScreen(p.pos()));
        } else if (payload instanceof OpenTradeGuiPayload p) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.ohhapple.monster_conquest.client.gui.TradeStationScreen(p.pos()));
        }
    }
}
