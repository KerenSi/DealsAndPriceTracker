package com.dealstracker.ingestion.portal;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Price Transparency Law file name, e.g. {@code PriceFull7290873255550-000-080-20261013-004004.gz}
 * (type, ChainId, optional sub-chain, StoreId, timestamp) or {@code PriceFull7290027600007-413-202610130300.gz}.
 */
public record TransparencyFileName(String name, String type, String chainCode, String storeCode, String timestamp) {

    /** Matches a file name anywhere in a page, JSON listing or URL. */
    public static final Pattern PATTERN = Pattern.compile(
            "(PriceFull|PromoFull|Stores|Price|Promo)(\\d{13})((?:-\\d+)+)(?:\\.xml)?(?:\\.gz|\\.xml)?",
            Pattern.CASE_INSENSITIVE);

    public static final String PRICE_FULL = "PriceFull";
    public static final String PROMO_FULL = "PromoFull";
    public static final String STORES = "Stores";

    public static Optional<TransparencyFileName> parse(String text) {
        Matcher m = PATTERN.matcher(text);
        return m.find() ? Optional.of(of(m)) : Optional.empty();
    }

    static TransparencyFileName of(Matcher m) {
        String[] parts = m.group(3).substring(1).split("-");
        int end = parts.length;
        String timestamp;
        // "20261013-004004" is split in two; "202610130300" is one part.
        if (end >= 2 && parts[end - 2].length() == 8 && parts[end - 1].length() <= 6) {
            timestamp = parts[end - 2] + parts[end - 1];
            end -= 2;
        } else {
            timestamp = parts[end - 1];
            end -= 1;
        }
        String store = end > 0 ? parts[end - 1] : null;
        return new TransparencyFileName(m.group(0), canonicalType(m.group(1)), m.group(2), store, timestamp);
    }

    private static String canonicalType(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "pricefull" -> PRICE_FULL;
            case "promofull" -> PROMO_FULL;
            case "stores" -> STORES;
            case "price" -> "Price";
            default -> "Promo";
        };
    }

    public boolean isForStore(String code) {
        return storeCode != null && code != null && sameCode(storeCode, code);
    }

    /** Store codes are zero-padded differently across files ("80" vs "080"). */
    public static boolean sameCode(String a, String b) {
        return stripZeros(a).equals(stripZeros(b));
    }

    private static String stripZeros(String code) {
        String s = code.trim().replaceFirst("^0+(?=.)", "");
        return s;
    }

    /** Sorts by timestamp; a timestamp padded to 14 digits compares correctly as a string. */
    public static final Comparator<TransparencyFileName> BY_TIME =
            Comparator.comparing(f -> (f.timestamp() + "00000000000000").substring(0, 14));

    /** The newest file of the given type, for the given store (or any store when storeCode is null). */
    public static Optional<TransparencyFileName> latest(List<TransparencyFileName> files, String type,
                                                        String chainCode, String storeCode) {
        return files.stream()
                .filter(f -> f.type().equals(type))
                .filter(f -> chainCode == null || f.chainCode().equals(chainCode))
                .filter(f -> storeCode == null || f.isForStore(storeCode))
                .max(BY_TIME);
    }

    /** The date part of the timestamp, yyyyMMdd. */
    public String date() {
        return timestamp.length() >= 8 ? timestamp.substring(0, 8) : timestamp;
    }
}
