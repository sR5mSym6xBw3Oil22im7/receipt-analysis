package com.example.receipt.game;

import java.util.Base64;

public record MonsterResponse(long monsterId, String receiptTableName, String imageSha256, String monsterName, String species, String rarity, int power, int guard, int speed, String generationStatus, String imageUrl, String imageDataUrl, String visualProfileJson) {
    public static MonsterResponse from(GameMonster m, String imageUrl) { return from(m, imageUrl, false); }
    public static MonsterResponse from(GameMonster m, String imageUrl, boolean includeImage) {
        String data = includeImage && m.image() != null ? "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(m.image()) : null;
        return new MonsterResponse(m.monsterId(), m.receiptTableName(), m.imageSha256(), m.monsterName(), m.species(), m.rarity(), m.power(), m.guard(), m.speed(), m.generationStatus(), imageUrl, data, m.visualProfileJson());
    }
}
