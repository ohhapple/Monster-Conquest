package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/** 自走棋界面的操作反馈音；不包含生物叫声。 */
public final class ArenaUiSounds {

    private static final int RESULT_NOTE_INTERVAL_TICKS = 8;
    private static List<SequencedNote> resultNotes =
            List.of();
    private static int resultNoteIndex;
    private static long nextResultNoteTick;

    private ArenaUiSounds() {
    }

    public static void playServerCue(int cue) {
        switch (cue) {
            case ArenaSoundPayload.PURCHASE_SUCCESS ->
                    play(SoundEvents.NOTE_BLOCK_BELL.value(),
                            1.45f, 0.65f);
            case ArenaSoundPayload.EQUIPMENT_SUCCESS ->
                    play(SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                            1.05f, 0.80f);
            case ArenaSoundPayload.UPGRADE_SUCCESS ->
                    play(SoundEvents.NOTE_BLOCK_CHIME.value(),
                            1.90f, 0.65f);
            case ArenaSoundPayload.SELL_SUCCESS ->
                    play(SoundEvents.ITEM_PICKUP, 0.85f, 0.65f);
            case ArenaSoundPayload.UNIT_MOVE_SUCCESS ->
                    play(SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                            1.35f, 0.62f);
            case ArenaSoundPayload.EQUIPMENT_MOVE_SUCCESS ->
                    play(SoundEvents.ARMOR_EQUIP_CHAIN.value(),
                            1.55f, 0.48f);
            case ArenaSoundPayload.EQUIPMENT_COMBINE_SUCCESS ->
                    play(SoundEvents.ANVIL_USE, 1.18f, 0.62f);
            case ArenaSoundPayload.COUNTDOWN_BELL_THREE ->
                    play(SoundEvents.NOTE_BLOCK_HARP.value(),
                            0.90f, 0.85f);
            case ArenaSoundPayload.COUNTDOWN_BELL_TWO ->
                    play(SoundEvents.NOTE_BLOCK_HARP.value(),
                            1.00f, 0.88f);
            case ArenaSoundPayload.COUNTDOWN_BELL_ONE ->
                    play(SoundEvents.NOTE_BLOCK_HARP.value(),
                            1.12f, 0.92f);
            case ArenaSoundPayload.BATTLE_VICTORY -> {
                startResultSequence(List.of(
                        new SequencedNote(
                                SoundEvents.NOTE_BLOCK_CHIME.value(),
                                1.20f, 0.52f),
                        new SequencedNote(
                                SoundEvents.NOTE_BLOCK_BELL.value(),
                                1.55f, 0.55f),
                        new SequencedNote(
                                SoundEvents.NOTE_BLOCK_PLING.value(),
                                1.90f, 0.50f)));
            }
            case ArenaSoundPayload.BATTLE_DEFEAT -> {
                startResultSequence(List.of(
                        new SequencedNote(
                                SoundEvents.NOTE_BLOCK_BASS.value(),
                                0.65f, 0.58f),
                        new SequencedNote(
                                SoundEvents.NOTE_BLOCK_GUITAR.value(),
                                0.80f, 0.52f),
                        new SequencedNote(
                                SoundEvents.NOTE_BLOCK_HARP.value(),
                                0.90f, 0.48f)));
            }
            default -> {
            }
        }
    }

    public static void playButtonClick() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.55f);
    }

    public static void playUnitClick() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.24f, 0.36f);
    }

    public static void playEquipmentClick() {
        play(SoundEvents.ARMOR_EQUIP_CHAIN.value(),
                1.72f, 0.30f);
    }

    public static void tick() {
        if (resultNoteIndex >= resultNotes.size()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long now = minecraft.level.getGameTime();
        if (now < nextResultNoteTick) return;
        SequencedNote note = resultNotes.get(resultNoteIndex++);
        play(note.sound(), note.pitch(), note.volume());
        nextResultNoteTick = now + RESULT_NOTE_INTERVAL_TICKS;
        if (resultNoteIndex >= resultNotes.size()) {
            resultNotes = List.of();
            resultNoteIndex = 0;
        }
    }

    private static void startResultSequence(
            List<SequencedNote> notes) {
        resultNotes = List.copyOf(notes);
        resultNoteIndex = 0;
        Minecraft minecraft = Minecraft.getInstance();
        nextResultNoteTick = minecraft.level == null
                ? 0L : minecraft.level.getGameTime();
    }

    private static void play(
            SoundEvent sound, float pitch, float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) return;
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(
                        sound, pitch, volume));
    }

    private record SequencedNote(
            SoundEvent sound, float pitch, float volume) {
    }
}
