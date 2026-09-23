package com.example.receipt.game;

import java.util.List;

public record RoomResponse(String roomCode, String status, String player, String player1Name, String player2Name, List<String> ownCandidates, boolean ownLocked, boolean opponentLocked, MonsterResponse ownMonster, MonsterResponse opponentMonster, BattleResponse battle) { }
