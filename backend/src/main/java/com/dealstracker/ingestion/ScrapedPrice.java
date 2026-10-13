package com.dealstracker.ingestion;

import com.dealstracker.domain.Category;
import com.dealstracker.domain.UnitType;
import java.math.BigDecimal;

/** One normalized price observation produced by a {@link PriceSource}. */
public record ScrapedPrice(
        String barcode,
        String title,
        String brand,
        Category category,
        String sizeLabel,
        BigDecimal unitQuantity,
        UnitType unitType,
        BigDecimal price,
        BigDecimal referencePrice,
        boolean promotion,
        String promotionDescription,
        Integer promoMinQuantity,
        BigDecimal promoTotalPrice,
        String productUrl) {
}
