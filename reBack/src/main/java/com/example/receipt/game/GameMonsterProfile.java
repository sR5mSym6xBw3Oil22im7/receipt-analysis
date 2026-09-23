package com.example.receipt.game;

public record GameMonsterProfile(
        String imageSha256, String monsterName, String species, String rarity,
        int power, int guard, int speed, String visualSeed, String visualProfileJson,
        String imagePromptVersion) { }
