package com.example.receipt.game;

import java.time.OffsetDateTime;
import java.util.List;

public record GameRoom(long roomId, String roomCode, String status, String player1Name, String player2Name, String player1TokenHash, String player2TokenHash, List<String> player1Candidates, List<String> player2Candidates, Long player1MonsterId, Long player2MonsterId, boolean player1Locked, boolean player2Locked, Long player1ScoreX2, Long player2ScoreX2, String winnerPlayer, Long winnerMonsterId, OffsetDateTime battleCompletedAt, OffsetDateTime expiresAt) {
    public boolean expired() { return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now()); }
    public boolean isPlayer1(String tokenHash) { return player1TokenHash.equals(tokenHash); }
    public boolean isPlayer2(String tokenHash) { return player2TokenHash != null && player2TokenHash.equals(tokenHash); }
}
