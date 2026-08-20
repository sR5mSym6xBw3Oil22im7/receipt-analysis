package com.example.receipt.service;

import com.example.receipt.dto.ReceiptText;
import com.example.receipt.exception.ReceiptException;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GeminiReceiptAnalyzer implements ReceiptAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiReceiptAnalyzer.class);
    private static final String PROMPT = """
            この画像はレシートです。印字されている文字列を上から下へ読み取り、
            1行ごとに lines 配列へ格納してください。
            推測で存在しない文字を追加しないでください。
            バーコード画像そのものは文字列化しなくて構いません。
            出力は指定されたJSONスキーマだけにしてください。
            """;

    private final String model;
    private final Gson gson = new Gson();

    public GeminiReceiptAnalyzer(
            @Value("${gemini.model:gemini-3.5-flash-lite}") String model) {
        this.model = model;
    }

    @Override
    public ReceiptText analyze(byte[] imageBytes, String mimeType, String geminiApiKey) {
        final String activeApiKey;
        try {
            activeApiKey = GeminiApiKeyPolicy.requireValid(geminiApiKey);
        } catch (IllegalArgumentException e) {
            String normalized = geminiApiKey == null ? "" : geminiApiKey.trim();
            String code = normalized.isBlank() ? "GEMINI_API_KEY_MISSING" : "INVALID_GEMINI_API_KEY";
            String message = normalized.isBlank()
                    ? "Gemini APIキーをWeb画面で入力してください。"
                    : "Gemini APIキーの形式を確認してください。";
            throw new ReceiptException(HttpStatus.BAD_REQUEST, code, message);
        }

        try (Client client = Client.builder().apiKey(activeApiKey).build()) {
            Map<String, Schema> properties = new LinkedHashMap<>();
            properties.put(
                    "lines",
                    Schema.builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING).build())
                            .build()
            );

            Schema responseSchema = Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(properties)
                    .required("lines")
                    .build();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema)
                    .candidateCount(1)
                    .build();

            Content content = Content.fromParts(
                    Part.fromBytes(imageBytes, mimeType),
                    Part.fromText(PROMPT)
            );

            GenerateContentResponse response = client.models.generateContent(model, content, config);
            String responseText = response.text();
            if (responseText == null || responseText.isBlank()) {
                throw new ReceiptException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "EMPTY_ANALYSIS_RESULT",
                        "Geminiから解析結果を取得できませんでした。"
                );
            }

            GeminiResponse parsed = gson.fromJson(responseText, GeminiResponse.class);
            if (parsed == null || parsed.lines() == null) {
                throw new ReceiptException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "INVALID_ANALYSIS_RESULT",
                        "Geminiの解析結果が想定形式ではありません。"
                );
            }

            List<String> normalizedLines = parsed.lines().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .limit(1000)
                    .toList();

            if (normalizedLines.isEmpty()) {
                throw new ReceiptException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "NO_TEXT_FOUND",
                        "レシートから文字を読み取れませんでした。"
                );
            }

            return new ReceiptText(normalizedLines);
        } catch (ReceiptException e) {
            throw e;
        } catch (ApiException e) {
            throw mapApiException(e);
        } catch (Exception e) {
            LOGGER.error("Gemini receipt analysis failed: model={}, mimeType={}, imageBytes={}, exception={}, message={}",
                    model, mimeType, imageBytes.length, e.getClass().getName(), e.getMessage());
            throw new ReceiptException(
                    HttpStatus.BAD_GATEWAY,
                    "GEMINI_API_ERROR",
                    "Gemini APIでレシート解析に失敗しました。"
            );
        }
    }

    static ReceiptException mapApiException(ApiException e) {
        String apiStatus = e.status() == null ? "" : e.status();
        String apiMessage = e.message() == null ? "" : e.message();
        String normalizedMessage = apiMessage.toLowerCase(java.util.Locale.ROOT);
        LOGGER.warn("Gemini API rejected request: code={}, status={}, message={}",
                e.code(), apiStatus, apiMessage);

        if (e.code() == 429 || "RESOURCE_EXHAUSTED".equalsIgnoreCase(e.status())) {
            return new ReceiptException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "GEMINI_QUOTA_EXCEEDED",
                    "Gemini APIの利用上限に達しました。少し待って再試行するか、別の利用可能なAPIキーを入力して再試行してください。"
            );
        }

        if (e.code() == 401 || e.code() == 403
                || normalizedMessage.contains("api key")
                || normalizedMessage.contains("apikey")
                || normalizedMessage.contains("api_key")) {
            return new ReceiptException(
                    HttpStatus.UNAUTHORIZED,
                    "GEMINI_API_KEY_REJECTED",
                    "Gemini APIキーが無効、ブロック済み、または権限不足です。別のAPIキーを入力してください。"
            );
        }

        return new ReceiptException(
                e.code() >= 400 && e.code() < 500 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY,
                "GEMINI_API_ERROR",
                e.code() >= 400 && e.code() < 500
                        ? "Gemini APIへのリクエストが拒否されました。APIキー、モデル、画像形式を確認してください。"
                        : "Gemini APIでレシート解析に失敗しました。"
        );
    }

    private record GeminiResponse(List<String> lines) {
    }
}
