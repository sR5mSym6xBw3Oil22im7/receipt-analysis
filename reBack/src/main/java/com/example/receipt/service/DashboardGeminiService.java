package com.example.receipt.service;

import com.example.receipt.dto.DashboardResponse;
import com.example.receipt.dto.DashboardDailyTrend;
import com.example.receipt.exception.ReceiptException;
import com.example.receipt.repository.ReceiptAnalyticsRepository;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
public class DashboardGeminiService {
    private final ReceiptAnalyticsRepository repository;
    private final String model;
    private final Gson gson = new Gson();

    public DashboardGeminiService(ReceiptAnalyticsRepository repository,
            @Value("${gemini.dashboard-model:gemini-3.7-flash}") String model) {
        this.repository = repository;
        this.model = model;
    }

    public DashboardResponse analyze(String key) {
        final String apiKey;
        try { apiKey = GeminiApiKeyPolicy.requireValid(key); }
        catch (IllegalArgumentException e) { throw new ReceiptException(HttpStatus.BAD_REQUEST, "INVALID_GEMINI_API_KEY", "Gemini APIキーを確認してください。"); }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        List<Map<String, Object>> source = repository.findSavedStructuredData();
        String prompt = """
                以下はPostgreSQLに保存されたレシートデータです。このデータだけを使って支出分析をしてください。
                rawLinesは保存されたレシート本文です。金額・購入日時・店舗名が構造化値と矛盾する場合はrawLinesを確認してください。
                入力にない値を推測・補完・創作してはいけません。JSONのみ返してください。
                今日の日付は %s、対象月は %s です。
                currentMonthTotalは対象月のtotal_amount合計、currentMonthReceiptCountは対象月の購入日時の件数、
                averagePerDayはcurrentMonthTotalを今日の日付の日数で割った整数です。
                dailyTrendは対象月の1日から今日までを日付ごとに出し、購入日時がその日のtotal_amountだけを合計してください。
                recentReceiptsは全期間の購入日時降順で5件、topItemsは対象月のitemsをitem_name完全一致で集計した金額上位5件です。各行のreceiptTableNameは入力データの値をそのまま返してください。
                storeBreakdownは対象月のstore_categoryごとのtotal_amount合計、categoryBreakdownは対象月のitemsのcategoryごとのamount合計です。
                保存済みデータが空の場合は数値0、配列空で返してください。
                形式: {"period":"YYYY-MM","currentMonthTotal":0,"currentMonthReceiptCount":0,"averagePerDay":0,"totalReceiptCount":0,"dailyTrend":[{"date":"YYYY-MM-DD","amount":0}],"storeBreakdown":[{"label":"","amount":0,"percentage":0}],"categoryBreakdown":[{"label":"","amount":0,"percentage":0}],"recentReceipts":[{"receiptTableName":"","purchasedAt":"","storeName":"","storeCategory":"","totalAmount":0}],"topItems":[{"itemName":"","category":"","purchaseCount":0,"totalAmount":0]}
                保存済みデータ:
                """.formatted(today, today.toString().substring(0, 7)) + gson.toJson(source);
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            var config = GenerateContentConfig.builder().responseMimeType("application/json").candidateCount(1).build();
            var response = client.models.generateContent(model, Content.fromParts(Part.fromText(prompt)), config);
            DashboardResponse result = gson.fromJson(response.text(), DashboardResponse.class);
            if (result == null) throw new ReceiptException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_DASHBOARD_RESULT", "Geminiの分析結果が不正です。");
            List<DashboardDailyTrend> dailyTrend = analyzeDailyTrend(apiKey, source, today);
            return new DashboardResponse(result.period(), result.currentMonthTotal(), result.currentMonthReceiptCount(),
                    result.averagePerDay(), result.totalReceiptCount(), dailyTrend, result.storeBreakdown(),
                    result.categoryBreakdown(), result.recentReceipts(), result.topItems());
        } catch (ReceiptException e) { throw e; }
        catch (ApiException e) { throw GeminiReceiptAnalyzer.mapApiException(e); }
        catch (Exception e) { throw new ReceiptException(HttpStatus.BAD_GATEWAY, "GEMINI_DASHBOARD_ERROR", "Geminiによる支出分析に失敗しました。"); }
    }

    private List<DashboardDailyTrend> analyzeDailyTrend(String apiKey, List<Map<String, Object>> source, LocalDate today) {
        List<Map<String, Object>> dailySource = new ArrayList<>();
        for (Map<String, Object> receipt : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("purchasedAt", receipt.get("purchased_at"));
            row.put("totalAmount", receipt.get("total_amount"));
            row.put("rawLines", receipt.get("rawLines"));
            dailySource.add(row);
        }
        String prompt = """
                PostgreSQLに保存された以下のレシートデータだけを使い、日別支出を計算してください。rawLinesは保存されたレシート本文です。
                今日の日付は %s、対象月は %s です。入力にないデータを作らないでください。
                rawLinesとpurchasedAtから購入日を確認し、対象月の日付と一致するレシートだけを使ってください。同じ日付の最終支払額totalAmountを合計してください。
                totalAmountがnullのレシートは除外してください。対象月の1日から今日まで全日付を1回ずつ返し、データがない日はamountを0にしてください。
                dateはYYYY-MM-DD、amountは整数です。JSONのみ返してください。
                形式: {"dailyTrend":[{"date":"YYYY-MM-DD","amount":0}]}
                入力データ:
                """.formatted(today, today.toString().substring(0, 7)) + gson.toJson(dailySource);
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            var config = GenerateContentConfig.builder().responseMimeType("application/json").candidateCount(1).build();
            var response = client.models.generateContent(model, Content.fromParts(Part.fromText(prompt)), config);
            DailyTrendResult result = gson.fromJson(response.text(), DailyTrendResult.class);
            if (result == null || result.dailyTrend() == null) throw new IllegalStateException("daily trend is missing");
            return result.dailyTrend();
        } catch (ApiException e) {
            throw GeminiReceiptAnalyzer.mapApiException(e);
        } catch (Exception e) {
            throw new ReceiptException(HttpStatus.BAD_GATEWAY, "GEMINI_DAILY_TREND_ERROR", "Geminiによる日別支出分析に失敗しました。");
        }
    }

    private record DailyTrendResult(List<DashboardDailyTrend> dailyTrend) { }
}
