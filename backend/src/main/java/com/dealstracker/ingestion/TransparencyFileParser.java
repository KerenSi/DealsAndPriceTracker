package com.dealstracker.ingestion;

import com.dealstracker.domain.Category;
import com.dealstracker.domain.UnitType;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses the XML files Israeli chains publish under the Food Price Transparency Law
 * ("PriceFull" and "PromoFull" files). Chains differ slightly in element names and casing,
 * so lookups are case-insensitive and accept the common variants.
 */
public class TransparencyFileParser {

    /** A promotion that applies to one or more item codes. */
    public record Promotion(String description, Integer minQuantity, BigDecimal discountedPrice,
                            boolean totalPriceForQuantity) {
    }

    /** Parses a PriceFull file and keeps only items in the tracked categories. */
    public List<ScrapedPrice> parsePrices(InputStream in, Map<String, Promotion> promosByItemCode) throws Exception {
        Document doc = parse(in);
        List<ScrapedPrice> result = new ArrayList<>();
        for (Element item : elements(doc, "Item", "Product")) {
            String name = text(item, "ItemName", "ItemNm").orElse(null);
            Optional<Category> category = CategoryClassifier.classify(name);
            Optional<String> code = text(item, "ItemCode");
            Optional<BigDecimal> price = decimal(item, "ItemPrice");
            if (category.isEmpty() || code.isEmpty() || price.isEmpty() || price.get().signum() <= 0) {
                continue;
            }
            Promotion promo = promosByItemCode.get(code.get());
            BigDecimal qty = decimal(item, "Quantity").orElse(null);
            UnitType unitType = unitType(text(item, "UnitQty", "UnitOfMeasure").orElse(""));

            boolean isPromo = promo != null;
            Integer minQty = null;
            BigDecimal total = null;
            BigDecimal shelfPrice = price.get();
            BigDecimal reference = null;
            if (isPromo && promo.discountedPrice() != null) {
                if (promo.minQuantity() != null && promo.minQuantity() > 1) {
                    minQty = promo.minQuantity();
                    total = promo.totalPriceForQuantity()
                            ? promo.discountedPrice()
                            : promo.discountedPrice().multiply(BigDecimal.valueOf(promo.minQuantity()));
                } else if (promo.discountedPrice().compareTo(shelfPrice) < 0) {
                    reference = shelfPrice;
                    shelfPrice = promo.discountedPrice();
                }
            }

            result.add(new ScrapedPrice(
                    code.get(),
                    name.trim(),
                    text(item, "ManufacturerName", "ManufactureName").map(String::trim).orElse(null),
                    category.get(),
                    CategoryClassifier.sizeLabel(name, category.get()).orElse(null),
                    qty,
                    unitType,
                    shelfPrice,
                    reference,
                    isPromo,
                    isPromo ? promo.description() : null,
                    minQty,
                    total,
                    null));
        }
        return result;
    }

    /** Parses a PromoFull file into a map of item code to promotion. */
    public Map<String, Promotion> parsePromotions(InputStream in) throws Exception {
        Document doc = parse(in);
        Map<String, Promotion> result = new HashMap<>();
        for (Element promoEl : elements(doc, "Promotion", "Sale")) {
            String description = text(promoEl, "PromotionDescription").orElse(null);
            Integer minQty = decimal(promoEl, "MinQty").map(BigDecimal::intValue).orElse(null);
            BigDecimal discounted = decimal(promoEl, "DiscountedPrice").orElse(null);
            // DiscountedPricePerMida = 1 means DiscountedPrice is per unit; otherwise it is the bundle total.
            boolean perUnit = text(promoEl, "DiscountedPricePerMida").map("1"::equals).orElse(false);
            Promotion promo = new Promotion(description, minQty, discounted, !perUnit);
            for (Element itemEl : childElements(promoEl, "Item")) {
                text(itemEl, "ItemCode").ifPresent(code -> result.put(code, promo));
            }
            // Some chains list one item per <Sale> with ItemCode directly on it.
            text(promoEl, "ItemCode").ifPresent(code -> result.putIfAbsent(code, promo));
        }
        return result;
    }

    static UnitType unitType(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        // Hebrew has final letter forms: "גרם" (gram) vs. "גרמים" (grams).
        if (s.contains("גרם") || s.contains("גרמ") || s.contains("ג'") || s.contains("ק\"ג") || s.contains("קילו")
                || s.contains("gram")) {
            return UnitType.GRAMS;
        }
        if (s.contains("מ\"ל") || s.contains("מיליליטר") || s.contains("ליטר") || s.contains("ml")) {
            return UnitType.ML;
        }
        return UnitType.UNITS;
    }

    private static Document parse(InputStream raw) throws Exception {
        InputStream in = new BufferedInputStream(raw);
        in.mark(2);
        int b1 = in.read();
        int b2 = in.read();
        in.reset();
        if (b1 == 0x1f && b2 == 0x8b) {
            in = new GZIPInputStream(in);
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder builder = f.newDocumentBuilder();
        return builder.parse(in);
    }

    private static List<Element> elements(Document doc, String... names) {
        List<Element> out = new ArrayList<>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            for (String n : names) {
                if (e.getTagName().equalsIgnoreCase(n) && hasChildElements(e)) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        NodeList all = parent.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.getTagName().equalsIgnoreCase(name) && hasChildElements(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean hasChildElements(Element e) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> text(Element parent, String... names) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            for (String name : names) {
                if (((Element) n).getTagName().equalsIgnoreCase(name)) {
                    String t = n.getTextContent();
                    if (t != null && !t.isBlank()) {
                        return Optional.of(t.trim());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> decimal(Element parent, String... names) {
        return text(parent, names).flatMap(t -> {
            try {
                return Optional.of(new BigDecimal(t));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }
}
