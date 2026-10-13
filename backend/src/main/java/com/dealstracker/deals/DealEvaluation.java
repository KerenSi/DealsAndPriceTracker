package com.dealstracker.deals;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of evaluating the current price of one listing against its history.
 *
 * @param genuineDeal            true when the drop vs. the historical baseline exceeds the threshold
 *                               and no price-anchoring was detected
 * @param effectivePrice         price per package actually paid (multi-buy aware)
 * @param baselinePrice          moving average over the short window (default 30 days); null without history
 * @param longBaselinePrice      moving average over the long window (default 60 days); null without history
 * @param dropPercent            real drop vs. baseline, in percent; null without history
 * @param claimedDiscountPercent discount the store advertises vs. its reference price; null if none
 * @param suspectedFakeDiscount  reference price looks inflated relative to observed history
 * @param reasons                human-readable explanation lines
 */
public record DealEvaluation(
        boolean genuineDeal,
        BigDecimal effectivePrice,
        BigDecimal baselinePrice,
        BigDecimal longBaselinePrice,
        BigDecimal dropPercent,
        BigDecimal claimedDiscountPercent,
        boolean suspectedFakeDiscount,
        List<String> reasons) {
}
