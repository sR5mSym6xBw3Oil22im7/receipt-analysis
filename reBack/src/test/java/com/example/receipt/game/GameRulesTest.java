package com.example.receipt.game;

import com.example.receipt.dto.ReceiptItemData;
import com.example.receipt.dto.ReceiptStructuredData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameRulesTest {
    private static String sha(String prefix) { return prefix + "0".repeat(64-prefix.length()); }

    @Test
    void rarityBoundariesAreDeterministic() {
        assertThat(GameRules.rarity(sha("0000003b"))).isEqualTo("NORMAL");
        assertThat(GameRules.rarity(sha("0000003c"))).isEqualTo("RARE");
        assertThat(GameRules.rarity(sha("00000054"))).isEqualTo("RARE");
        assertThat(GameRules.rarity(sha("00000055"))).isEqualTo("SUPER_RARE");
        assertThat(GameRules.rarity(sha("0000005e"))).isEqualTo("SUPER_RARE");
        assertThat(GameRules.rarity(sha("0000005f"))).isEqualTo("ULTRA_RARE");
        assertThat(GameRules.rarity(sha("00000062"))).isEqualTo("ULTRA_RARE");
        assertThat(GameRules.rarity(sha("00000063"))).isEqualTo("LEGEND");
    }

    @Test
    void profileAndVisualTagsAreStableAndUseCompoundProductMeaning() {
        ReceiptStructuredData data = new ReceiptStructuredData("店", null, "スーパー", null, 126L, null, null, List.of(new ReceiptItemData("ペンシルカルパス", "食料品", BigDecimal.ONE, 126L, 126L)));
        GameMonsterProfile first = GameRules.profile(sha("01234567"), data);
        GameMonsterProfile second = GameRules.profile(sha("01234567"), data);
        assertThat(first).isEqualTo(second);
        assertThat(first.visualProfileJson()).contains("MEAT", "SMOKED", "STICK");
        assertThat(first.visualProfileJson()).doesNotContain("PENCIL");
    }

    @Test
    void battleUsesIntegerScoreAndShaTieBreak() {
        GameMonster first = monster(sha("aaaaaaaa"), "NORMAL", 40, 40, 40);
        GameMonster second = monster(sha("bbbbbbbb"), "NORMAL", 40, 40, 40);
        long firstScore = GameRules.battleScoreX2(first, second);
        long secondScore = GameRules.battleScoreX2(second, first);
        assertThat(firstScore).isEqualTo(secondScore);
        assertThat(GameRules.winner(first, second, firstScore, secondScore)).isEqualTo("PLAYER2");
        assertThat(firstScore % 1).isZero();
    }

    private static GameMonster monster(String sha, String rarity, int power, int guard, int speed) {
        return new GameMonster(1, "receipt_0123456789abcdef0123456789abcdef", sha, "test", "BEAST", rarity, power, guard, speed, "READY", null, new byte[]{1}, "image/jpeg", null, "{}", "v1");
    }
}
