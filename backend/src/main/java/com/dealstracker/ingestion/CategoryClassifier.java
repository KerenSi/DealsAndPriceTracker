package com.dealstracker.ingestion;

import com.dealstracker.domain.Category;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keyword-based classification of raw item names into the MVP categories. */
public final class CategoryClassifier {

    private static final List<String> DIAPER_KEYWORDS = List.of(
            "חיתול", "טיטול", "האגיס", "פמפרס", "huggies", "pampers", "diaper");

    private static final List<String> BABY_FOOD_KEYWORDS = List.of(
            "מטרנה", "סימילאק", "נוטרילון", "אפטמיל", "תמ\"ל", "תמל ", "דייסה", "דייסת", "מחית", "גרבר",
            "materna", "similac", "nutrilon", "aptamil", "gerber", "infant formula", "baby cereal");

    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "מגבונ", "wipes", "קרם", "cream", "שקיות", "bags");

    private static final Pattern DIAPER_SIZE = Pattern.compile("(?:מידה|size|שלב)\\s*(\\d{1,2}(?:\\+)?)",
            Pattern.CASE_INSENSITIVE);

    private CategoryClassifier() {
    }

    public static Optional<Category> classify(String itemName) {
        if (itemName == null) {
            return Optional.empty();
        }
        String name = itemName.toLowerCase(Locale.ROOT);
        if (EXCLUDE_KEYWORDS.stream().anyMatch(name::contains)) {
            return Optional.empty();
        }
        if (DIAPER_KEYWORDS.stream().anyMatch(name::contains)) {
            return Optional.of(Category.DIAPERS);
        }
        if (BABY_FOOD_KEYWORDS.stream().anyMatch(name::contains)) {
            return Optional.of(Category.BABY_FOOD);
        }
        return Optional.empty();
    }

    /** Extracts "Size 4" / "Stage 2" style labels from an item name. */
    public static Optional<String> sizeLabel(String itemName, Category category) {
        if (itemName == null) {
            return Optional.empty();
        }
        Matcher m = DIAPER_SIZE.matcher(itemName);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of((category == Category.DIAPERS ? "Size " : "Stage ") + m.group(1));
    }
}
