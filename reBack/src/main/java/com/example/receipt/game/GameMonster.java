package com.example.receipt.game;

import java.time.OffsetDateTime;

public record GameMonster(
        long monsterId, String receiptTableName, String imageSha256, String monsterName,
        String species, String rarity, int power, int guard, int speed,
        String generationStatus, OffsetDateTime generationStartedAt, byte[] image,
        String imageMimeType, String visualSeed, String visualProfileJson,
        String imagePromptVersion) {
    public boolean ready() { return "READY".equals(generationStatus) && image != null; }
}
