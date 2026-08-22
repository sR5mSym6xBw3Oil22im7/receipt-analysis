package com.example.receipt.dto;
public record DashboardRecentReceipt(String receiptTableName, String purchasedAt, String storeName, String storeCategory, Long totalAmount) { }
