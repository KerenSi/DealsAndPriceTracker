package com.dealstracker.insights;

import java.math.BigDecimal;
import java.util.UUID;

public record Insight(
        String type,
        String message,
        UUID productId,
        String storeName,
        BigDecimal price,
        BigDecimal unitPrice,
        String unitLabel) {
}
