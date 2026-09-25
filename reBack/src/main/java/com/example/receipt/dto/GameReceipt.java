package com.example.receipt.dto;

public record GameReceipt(String id, String label, String purchasedAt, Long totalAmount, boolean cardReady) {}
