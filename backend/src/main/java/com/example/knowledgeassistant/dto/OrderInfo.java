package com.example.knowledgeassistant.dto;

import java.util.List;

public record OrderInfo(
        String orderNo,
        String customerName,
        String status,
        String paidAmount,
        String eta,
        List<String> events
) {
}
