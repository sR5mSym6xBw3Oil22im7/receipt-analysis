package com.example.receipt.dto;
import java.time.LocalDateTime;
public record DashboardRecentReceipt(String receiptTableName, LocalDateTime purchasedAt, String storeName, String storeCategory, Long totalAmount) { }
