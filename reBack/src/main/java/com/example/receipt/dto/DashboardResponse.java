package com.example.receipt.dto;

import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(String period, long currentMonthTotal, long currentMonthReceiptCount,
        long averagePerDay, long totalReceiptCount, List<DashboardMonthlyTrend> monthlyTrend,
        List<DashboardBreakdown> storeBreakdown, List<DashboardBreakdown> categoryBreakdown,
        List<DashboardRecentReceipt> recentReceipts, List<DashboardTopItem> topItems) { }
