package com.dealstracker.deals;

import com.dealstracker.domain.PriceSnapshot;
import com.dealstracker.domain.Product;
import com.dealstracker.domain.Store;
import java.math.BigDecimal;

/**
 * Latest price of one product in one store, with its deal evaluation.
 *
 * @param unitPrice price per diaper, or per 100 g / 100 ml; null if the pack size is unknown
 * @param unitLabel "diaper", "100g", "100ml" or "unit"
 */
public record ListingAnalysis(
        Product product,
        Store store,
        PriceSnapshot latest,
        DealEvaluation evaluation,
        BigDecimal unitPrice,
        String unitLabel) {
}
