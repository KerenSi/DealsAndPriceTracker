package com.dealstracker.ingestion;

import com.dealstracker.domain.Category;
import com.dealstracker.domain.Store;
import com.dealstracker.domain.UnitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic synthetic prices for local development and demos, so the whole pipeline
 * (history, deal detection, insights, chat) works before real feeds are connected.
 * Barcodes are prefixed with "SAMPLE-" so they never collide with real items.
 */
@Component
@ConditionalOnProperty(prefix = "tracker.sources.sample", name = "enabled", havingValue = "true")
public class SamplePriceSource implements PriceSource {

    record CatalogItem(String barcode, String title, String brand, Category category, String sizeLabel,
                       int quantity, UnitType unitType, double basePrice) {
    }

    static final List<CatalogItem> CATALOG = List.of(
            new CatalogItem("SAMPLE-0001", "האגיס פרידום דריי מידה 4 (44 יח')", "Huggies", Category.DIAPERS, "Size 4", 44, UnitType.UNITS, 64.90),
            new CatalogItem("SAMPLE-0002", "האגיס פרידום דריי מידה 5 (38 יח')", "Huggies", Category.DIAPERS, "Size 5", 38, UnitType.UNITS, 64.90),
            new CatalogItem("SAMPLE-0003", "פמפרס פרימיום קר מידה 4 (52 יח')", "Pampers", Category.DIAPERS, "Size 4", 52, UnitType.UNITS, 79.90),
            new CatalogItem("SAMPLE-0004", "פמפרס אקטיב בייבי מידה 3 (60 יח')", "Pampers", Category.DIAPERS, "Size 3", 60, UnitType.UNITS, 69.90),
            new CatalogItem("SAMPLE-0005", "טיטולים מותג פרטי מידה 4 (50 יח')", "Private Label", Category.DIAPERS, "Size 4", 50, UnitType.UNITS, 44.90),
            new CatalogItem("SAMPLE-0101", "מטרנה שלב 1 700 גרם", "Materna", Category.BABY_FOOD, "Stage 1", 700, UnitType.GRAMS, 74.90),
            new CatalogItem("SAMPLE-0102", "מטרנה שלב 2 700 גרם", "Materna", Category.BABY_FOOD, "Stage 2", 700, UnitType.GRAMS, 74.90),
            new CatalogItem("SAMPLE-0103", "סימילאק שלב 1 700 גרם", "Similac", Category.BABY_FOOD, "Stage 1", 700, UnitType.GRAMS, 79.90),
            new CatalogItem("SAMPLE-0104", "נוטרילון שלב 1 800 גרם", "Nutrilon", Category.BABY_FOOD, "Stage 1", 800, UnitType.GRAMS, 89.90),
            new CatalogItem("SAMPLE-0105", "דייסת אורז מטרנה 300 גרם", "Materna", Category.BABY_FOOD, null, 300, UnitType.GRAMS, 21.90),
            new CatalogItem("SAMPLE-0106", "מחית גרבר תפוח 2x80 גרם", "Gerber", Category.BABY_FOOD, null, 160, UnitType.GRAMS, 12.90));

    @Override
    public String name() {
        return "sample";
    }

    @Override
    public boolean supports(Store store) {
        return true;
    }

    @Override
    public List<ScrapedPrice> fetch(Store store, LocalDate date) {
        List<ScrapedPrice> out = new ArrayList<>();
        double storeFactor = 0.92 + (Math.floorMod(store.getChain().hashCode(), 17) / 100.0);
        for (CatalogItem item : CATALOG) {
            // Pharmacies don't carry the private-label supermarket diapers.
            if (item.brand().equals("Private Label") && store.getChain().contains("PHARM")) {
                continue;
            }
            Random rnd = new Random((item.barcode() + store.getChain() + date).hashCode());
            double noise = 1 + (rnd.nextGaussian() * 0.02);
            BigDecimal regular = money(item.basePrice() * storeFactor * noise);

            BigDecimal price = regular;
            BigDecimal reference = null;
            boolean promo = false;
            String promoText = null;
            Integer minQty = null;
            BigDecimal total = null;

            int roll = rnd.nextInt(100);
            if (roll < 8) {
                // Genuine markdown: ~25% off.
                promo = true;
                reference = regular;
                price = money(regular.doubleValue() * 0.75);
                promoText = "מבצע: " + price + " ₪ במקום " + regular + " ₪";
            } else if (roll < 14) {
                // Anchored "sale": inflated reference, price barely changed.
                promo = true;
                reference = money(regular.doubleValue() * 1.35);
                price = money(regular.doubleValue() * 0.98);
                promoText = "מבצע! " + price + " ₪ במקום " + reference + " ₪";
            } else if (roll < 20) {
                // Multi-buy: 2 for the price of ~1.6.
                promo = true;
                minQty = 2;
                total = money(regular.doubleValue() * 1.6);
                promoText = "2 ב-" + total + " ₪";
            }

            out.add(new ScrapedPrice(item.barcode(), item.title(), item.brand(), item.category(), item.sizeLabel(),
                    BigDecimal.valueOf(item.quantity()), item.unitType(), price, reference, promo, promoText,
                    minQty, total, null));
        }
        return out;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).add(new BigDecimal("0.09"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
