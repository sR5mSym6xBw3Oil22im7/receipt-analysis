package com.example.receipt.game;

import com.example.receipt.dto.ReceiptItemData;
import com.example.receipt.dto.ReceiptStructuredData;
import com.google.gson.Gson;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

public final class GameRules {
    public static final String VISUAL_VERSION = "monster-visual:v1";
    public static final String PROMPT_VERSION = "monster-prompt:v1";
    private static final Gson GSON = new Gson();
    private static final String[] COLORS = {"IVORY", "CRIMSON", "AZURE", "EMERALD", "VIOLET", "GOLD", "SILVER", "OBSIDIAN"};
    private static final String[] SHAPES = {"SLENDER", "COMPACT", "ARMORED", "TALL", "MUSCULAR", "MISTLIKE"};
    private static final String[] HORNS = {"NONE", "CURVED", "CROWNED", "SHORT", "CRYSTAL", "ANTLERED"};
    private static final String[] WINGS = {"NONE", "SMALL", "FEATHERED", "MEMBRANE", "CRYSTAL"};
    private static final String[] ARMOR = {"LIGHT", "PLATED", "SCALED", "CRYSTAL", "HEAVY", "NONE"};
    private static final String[] AURAS = {"SPARKS", "SMOKE", "AQUA", "LEAVES", "EMBER", "MOONLIGHT"};
    private static final String[] PERSONALITIES = {"BRAVE", "TRICKSTER", "CALM", "FEROCIOUS", "CURIOUS", "GUARDIAN"};
    private static final String[] EPITHETS = {"烈風の", "鋼鉄の", "紅蓮の", "蒼天の", "幻影の", "星詠みの", "深緑の", "銀河の"};
    private static final Map<String, String> SPECIES = Map.of("スーパー", "BEAST", "コンビニ", "WOLF", "ドラッグストア", "GOLEM", "飲食店", "DRAGON");
    private static final Map<String, String> SPECIES_JA = Map.of("BEAST", "ビースト", "WOLF", "ウルフ", "GOLEM", "ゴーレム", "DRAGON", "ドラゴン", "PHANTOM", "ファントム");

    private GameRules() { }

    public static GameMonsterProfile profile(String sha, ReceiptStructuredData data) {
        String normalizedSha = sha.toLowerCase(Locale.ROOT);
        String species = SPECIES.getOrDefault(safe(data == null ? null : data.storeCategory()), "PHANTOM");
        String rarity = rarity(normalizedSha);
        int[] stats = stats(normalizedSha, data);
        List<String> tags = visualTags(data == null ? List.of() : data.safeItems());
        String tagString = String.join(",", tags);
        String visualSeed = sha256(VISUAL_VERSION + "|" + normalizedSha + "|" + tagString);
        Map<String, Object> visual = new LinkedHashMap<>();
        visual.put("visualProfileVersion", VISUAL_VERSION);
        visual.put("species", species);
        visual.put("visualTags", tags);
        visual.put("mainColor", pick(COLORS, visualSeed, 0));
        visual.put("bodyShape", pick(SHAPES, visualSeed, 8));
        visual.put("hornType", pick(HORNS, visualSeed, 16));
        visual.put("wingType", pick(WINGS, visualSeed, 24));
        visual.put("armorType", pick(ARMOR, visualSeed, 32));
        visual.put("auraType", pick(AURAS, visualSeed, 40));
        visual.put("personality", pick(PERSONALITIES, visualSeed, 48));
        visual.put("sourceConfidenceSummary", tags.isEmpty() ? "UNKNOWN" : "MEDIUM");
        String name = EPITHETS[index(normalizedSha, 8, EPITHETS.length)] + SPECIES_JA.get(species)
                + "・" + Integer.toHexString((int) unsigned(normalizedSha, 56)).toUpperCase(Locale.ROOT);
        return new GameMonsterProfile(normalizedSha, name, species, rarity, stats[0], stats[1], stats[2], visualSeed, GSON.toJson(visual), PROMPT_VERSION);
    }

    public static String rarity(String sha) {
        long value = unsigned(sha, 0) % 100;
        if (value < 60) return "NORMAL";
        if (value < 85) return "RARE";
        if (value < 95) return "SUPER_RARE";
        if (value < 99) return "ULTRA_RARE";
        return "LEGEND";
    }

    public static int rarityBonus(String rarity) {
        return switch (rarity) { case "RARE" -> 10; case "SUPER_RARE" -> 20; case "ULTRA_RARE" -> 30; case "LEGEND" -> 40; default -> 0; };
    }

    public static long battleScoreX2(GameMonster self, GameMonster opponent) {
        return (long) (self.power() + self.guard() + self.speed()) * 6
                + Math.max(self.power() - opponent.guard(), 0) * 4L
                + Math.max(self.speed() - opponent.speed(), 0) * 2L
                + Math.max(self.guard() - opponent.power(), 0)
                + rarityBonus(self.rarity()) * 2L;
    }

    public static String winner(GameMonster first, GameMonster second, long firstScore, long secondScore) {
        if (firstScore != secondScore) return firstScore > secondScore ? "PLAYER1" : "PLAYER2";
        return first.imageSha256().compareTo(second.imageSha256()) > 0 ? "PLAYER1" : "PLAYER2";
    }

    public static List<String> visualTags(List<ReceiptItemData> items) {
        Map<String, Double> weights = new HashMap<>();
        for (ReceiptItemData item : items) {
            if (item == null) continue;
            double weight = item.amount() != null && item.amount() >= 0 ? item.amount() :
                    item.quantity() != null && item.unitPrice() != null && item.quantity().signum() >= 0 && item.unitPrice() >= 0
                            ? item.quantity().doubleValue() * item.unitPrice() : 1d;
            if (weight <= 0) weight = 1d;
            String name = Normalizer.normalize(safe(item.name()), Normalizer.Form.NFKC).replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
            List<String> tags = new ArrayList<>();
            if (name.contains("カルパス")) tags.addAll(List.of("MEAT", "SMOKED", "STICK"));
            else {
                if (name.matches(".*(肉|チキン|鶏|牛|豚|ハム|ソーセージ|カルビ|プロテイン).*")) tags.add("PROTEIN");
                if (name.matches(".*(揚|フライ|コロッケ|天ぷら|唐揚).*")) tags.add("FRIED");
                if (name.matches(".*(チョコ|カカオ).*")) tags.add("CACAO");
                if (name.matches(".*(ナッツ|ピーナッツ|アーモンド).*")) tags.add("NUT");
                if (name.matches(".*(水|ドリンク|ジュース|飲料|スポーツ|ピルクル|牛乳|お茶).*")) tags.add("LIQUID");
                if (name.matches(".*(オレンジ|みかん|レモン|柑橘).*")) tags.add("CITRUS");
                if (name.matches(".*(魚|鮭|明太|海鮮|刺身).*")) tags.add("SEAFOOD");
                if (name.matches(".*(辛|明太|キムチ|唐辛子).*")) tags.add("SPICY");
                if (name.matches(".*(きのこ|しいたけ|まいたけ).*")) tags.add("MUSHROOM");
                if (name.matches(".*(パスタ|麺|うどん|そば|ラーメン).*")) tags.add("NOODLE");
                if (name.matches(".*(おにぎり|ご飯|米|弁当).*")) tags.add("RICE_GRAIN");
                if (name.matches(".*(ヨーグルト|発酵|乳酸菌|ピルクル).*")) tags.add("FERMENTED");
                if (name.matches(".*(乳|ミルク|牛乳).*")) tags.add("DAIRY_DRINK");
                if (name.matches(".*(野菜|サラダ|キャベツ|トマト).*")) tags.add("VEGETABLE");
                if (name.matches(".*(菓子|ケーキ|プリン|飴|スイーツ).*")) tags.add("SWEET");
            }
            if (tags.isEmpty()) tags.add(categoryTag(item.category()));
            for (String tag : new LinkedHashSet<>(tags)) weights.merge(tag, weight, Double::sum);
        }
        return weights.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).limit(3).map(Map.Entry::getKey).toList();
    }

    private static String categoryTag(String category) {
        return switch (safe(category)) { case "飲食" -> "LIQUID"; case "食料品" -> "PROTEIN"; case "日用品" -> "ARMOR"; case "交通・移動" -> "AURA"; default -> "MYSTERY"; };
    }

    private static int[] stats(String sha, ReceiptStructuredData data) {
        double[] base = {0, 0, 0};
        List<ReceiptItemData> items = data == null ? List.of() : data.safeItems();
        double total = 0;
        for (ReceiptItemData item : items) {
            if (item == null) continue;
            double amount = item.amount() != null && item.amount() >= 0 ? item.amount() : item.quantity() != null && item.unitPrice() != null && item.quantity().signum() >= 0 && item.unitPrice() >= 0 ? item.quantity().doubleValue() * item.unitPrice() : 0;
            if (amount > 0) total += amount;
        }
        if (total > 0) {
            for (ReceiptItemData item : items) addCategory(base, item == null ? null : item.category(), item == null ? 0 : Math.max(0, amount(item)), total);
        } else if (!items.isEmpty()) {
            for (ReceiptItemData item : items) addCategory(base, item == null ? null : item.category(), 1, items.size());
        } else base[0] = base[1] = base[2] = 100d / 3d;
        int[] result = {(int)Math.round(base[0]), (int)Math.round(base[1]), (int)Math.round(base[2])};
        String store = data == null ? "" : safe(data.storeCategory());
        switch (store) { case "スーパー" -> { result[0]+=2; result[1]+=2; result[2]+=2; } case "コンビニ" -> result[2]+=6; case "ドラッグストア" -> result[1]+=6; case "飲食店" -> result[0]+=6; default -> result[index(sha, 8, 3)] += 6; }
        long amount = data == null || data.totalAmount() == null || data.totalAmount() <= 0 ? 0 : data.totalAmount();
        int bonus = (int)Math.min(12, Math.round(3d * Math.log1p(amount / 500d)));
        int each = bonus / 3, remainder = bonus % 3;
        result[0]+=each; result[1]+=each; result[2]+=each;
        int start = index(sha, 16, 3); for (int i=0;i<remainder;i++) result[(start+i)%3]++;
        for (int i=0;i<3;i++) result[i] = Math.max(0, Math.min(120, result[i]));
        return result;
    }

    private static double amount(ReceiptItemData i) { return i.amount() != null && i.amount() >= 0 ? i.amount() : i.quantity() != null && i.unitPrice() != null && i.quantity().signum() >= 0 && i.unitPrice() >= 0 ? i.quantity().doubleValue()*i.unitPrice() : 0; }
    private static void addCategory(double[] out, String category, double amount, double total) { double w=amount/total; switch(safe(category)) { case "食料品" -> {out[0]+=w*60;out[1]+=w*40;} case "日用品" -> {out[1]+=w*80;out[2]+=w*20;} case "飲食" -> {out[0]+=w*80;out[2]+=w*20;} case "交通・移動" -> {out[1]+=w*20;out[2]+=w*80;} default -> {out[0]+=w*34;out[1]+=w*33;out[2]+=w*33;} } }
    private static String pick(String[] values, String seed, int offset) { return values[index(seed, offset, values.length)]; }
    private static int index(String hex, int offset, int modulo) { return (int)(unsigned(hex, offset) % modulo); }
    private static long unsigned(String hex, int offset) { int end=Math.min(offset+8, hex.length()); return Long.parseUnsignedLong(hex.substring(offset,end),16); }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    public static String sha256(String value) { try { byte[] d=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); for(byte x:d)b.append(String.format("%02x",x)); return b.toString(); } catch(Exception e){throw new IllegalStateException(e);} }
}
