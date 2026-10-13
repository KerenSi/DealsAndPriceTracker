package com.dealstracker.deals;

import com.dealstracker.domain.PriceSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/** A single observed price, independent of persistence. */
public record PricePoint(
        BigDecimal price,
        BigDecimal referencePrice,
        boolean promotion,
        Integer promoMinQuantity,
        BigDecimal promoTotalPrice,
        OffsetDateTime observedAt) {

    public static PricePoint of(BigDecimal price, OffsetDateTime at) {
        return new PricePoint(price, null, false, null, null, at);
    }

    public static PricePoint from(PriceSnapshot s) {
        return new PricePoint(s.getPrice(), s.getOriginalReferencePrice(), s.isPromotion(),
                s.getPromoMinQuantity(), s.getPromoTotalPrice(), s.getScrapedAt());
    }

    /**
     * The price actually paid per package. For multi-buy offers ("3 for 50") this is
     * total / quantity when that is cheaper than the shelf price.
     */
    public BigDecimal effectivePrice() {
        if (promoMinQuantity != null && promoMinQuantity > 0 && promoTotalPrice != null) {
            BigDecimal perUnit = promoTotalPrice.divide(BigDecimal.valueOf(promoMinQuantity), 4, RoundingMode.HALF_UP);
            if (perUnit.compareTo(price) < 0) {
                return perUnit;
            }
        }
        return price;
    }

    public boolean isMultiBuy() {
        return promoMinQuantity != null && promoMinQuantity > 1 && promoTotalPrice != null;
    }
}
