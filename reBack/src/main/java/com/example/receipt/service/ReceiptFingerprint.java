package com.example.receipt.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Canonical UTF-8 representation used for receipt identity. */
public final class ReceiptFingerprint {
    private ReceiptFingerprint() {}

    public static List<String> normalizeLines(List<String> lines) {
        if (lines == null) return List.of();
        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            if (line == null) continue;
            String value = Normalizer.normalize(line, Normalizer.Form.NFKC)
                    .replace('\u00a0', ' ')
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!value.isEmpty()) normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    public static String canonicalText(List<String> lines) {
        // レシートの表示上の空白・改行位置はOCR結果で揺れるため、比較対象から除外する。
        return String.join("\n", normalizeLines(lines))
                .replaceAll("(?U)\\s+", "");
    }

    public static String sha256(String canonicalText) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalText.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256が利用できません。", e);
        }
    }
}
