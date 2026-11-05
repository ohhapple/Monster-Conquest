package com.ohhapple.monster_conquest.client;

import com.ohhapple.monster_conquest.client.gui.CommandWheelScreen;
import com.ohhapple.monster_conquest.client.gui.FactionGuiClientEvents;
import com.ohhapple.monster_conquest.client.gui.RecruitStationScreen;
import com.ohhapple.monster_conquest.client.gui.TradeStationScreen;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaLobbyClientState;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaGameClientState;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaIntegratedReturnHandler;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaUiSounds;
import com.ohhapple.monster_conquest.client.gun.GunClientState;
import com.ohhapple.monster_conquest.client.path.ClientPathPreview;
import com.ohhapple.monster_conquest.client.rts.RtsClientState;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionAllySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionHostilitySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbyNoticePayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCareerPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCombatStatsPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaGameNoticePayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLaunchProgressPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSpectatorSnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaConnectToChildPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaReconnectToMainPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaReturnToWorldPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSoundPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.GunStatusPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.SyncEquipmentPayload;
import com.ohhapple.monster_conquest.network.clientbound.path.SyncPathRoutePayload;
import com.ohhapple.monster_conquest.network.clientbound.recruit.OpenRecruitGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncSquadPayload;
import com.ohhapple.monster_conquest.network.clientbound.trade.OpenTradeGuiPayload;
import com.ohhapple.monster_conquest.network.ModVersionHandshakePayload;

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
        } else if (payload instanceof ModVersionHandshakePayload p) {
            ModVersionClientState.receiveServerVersion(p.version());
        } else if (payload instanceof ArenaLobbySnapshotPayload p) {
            ArenaLobbyClientState.setSnapshot(p);
        } else if (payload instanceof ArenaCareerPayload p) {
            ArenaLobbyClientState.setViewedCareer(p);
        } else if (payload instanceof ArenaLobbyNoticePayload p) {
            ArenaLobbyClientState.showTranslatedNotice(p.translationKey());
        } else if (payload instanceof ArenaPlayerSnapshotPayload p) {
            ArenaGameClientState.setSnapshot(p);
        } else if (payload instanceof ArenaCombatStatsPayload p) {
            ArenaGameClientState.setCombatStats(p);
        } else if (payload instanceof ArenaSpectatorSnapshotPayload p) {
            ArenaGameClientState.setSpectatorSnapshot(p);
        } else if (payload instanceof ArenaGameNoticePayload p) {
            ArenaGameClientState.showNotice(p.translationKey());
        } else if (payload instanceof ArenaSoundPayload p) {
            ArenaUiSounds.playServerCue(p.cue());
        } else if (payload instanceof ArenaLaunchProgressPayload p) {
            ArenaLobbyClientState.setLaunchProgress(p);
        } else if (payload instanceof ArenaConnectToChildPayload p) {
            ArenaIntegratedReturnHandler.connectToChild(p, ctx);
        } else if (payload instanceof ArenaReconnectToMainPayload p) {
            ArenaIntegratedReturnHandler.reconnectToMain(p, ctx);
        } else if (payload instanceof ArenaReturnToWorldPayload p) {
            ArenaIntegratedReturnHandler.handle(p, ctx);
        }
    }
}
