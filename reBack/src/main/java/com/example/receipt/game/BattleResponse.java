package com.example.receipt.game;

public record BattleResponse(MonsterResponse player1, MonsterResponse player2, long player1BattleScoreX2, long player2BattleScoreX2, String winnerPlayer) {
    public double player1BattleScore() { return player1BattleScoreX2 / 2.0; }
    public double player2BattleScore() { return player2BattleScoreX2 / 2.0; }
}
