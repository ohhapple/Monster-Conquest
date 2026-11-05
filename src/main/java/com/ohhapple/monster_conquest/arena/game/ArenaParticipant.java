package com.ohhapple.monster_conquest.arena.game;

import java.util.UUID;

/** 房间座位在转服后仍保持稳定，0..7 对应八块独立备战棋盘。 */
public record ArenaParticipant(UUID id, String name, int seatIndex) {

    public ArenaParticipant {
        seatIndex = Math.max(0, Math.min(7, seatIndex));
    }

    public ArenaParticipant(UUID id, String name) {
        this(id, name, 0);
    }
}
