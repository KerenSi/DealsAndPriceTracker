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

    /** One branch from a chain's Stores file. */
    public record StoreInfo(String storeCode, String name, String address, String city) {

        /** True when every word of {@code match} appears in the name, address or city. */
        public boolean matches(String match) {
            String haystack = normalize(name + " " + address + " " + city);
            for (String word : normalize(match).split(" ")) {
                if (!word.isEmpty() && !haystack.contains(word)) {
                    return false;
                }
            }
            return true;
        }

        private static String normalize(String s) {
            return s.replaceAll("[\\-'\"׳״.,()]", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        }
    }

    /** Parses a Stores file (one chain, all branches). */
    public List<StoreInfo> parseStores(InputStream in) throws Exception {
        Document doc = parse(in);
        List<StoreInfo> result = new ArrayList<>();
        for (Element store : elements(doc, "Store", "Branch")) {
            text(store, "StoreId", "StoreID").ifPresent(code -> result.add(new StoreInfo(
                    code,
                    text(store, "StoreName").orElse(""),
                    text(store, "Address").orElse(""),
                    text(store, "City").orElse(""))));
        }
        return result;
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

            Integer minQty = null;
            BigDecimal total = null;
            BigDecimal shelfPrice = price.get();
            BigDecimal reference = null;
            if (promo != null && promo.discountedPrice() != null) {
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
            // A "promotion" that neither lowers the price nor is a multi-buy is not a deal.
            boolean isPromo = minQty != null || reference != null;

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

    /**
     * Parses a PromoFull file into a map of item code to promotion. Quantity and price sit either on
     * the promotion (Shufersal style: Promotion/MinQty + PromotionItems/Item/ItemCode) or on each item
     * (Cerberus style: Promotion/Groups/Group/PromotionItems/PromotionItem/{ItemCode, MinQty, DiscountedPrice}).
     */
    public Map<String, Promotion> parsePromotions(InputStream in) throws Exception {
        Document doc = parse(in);
        Map<String, Promotion> result = new HashMap<>();
        for (Element promoEl : elements(doc, "Promotion", "Sale")) {
            if (isBasketCoupon(promoEl)) {
                continue;
            }
            String description = text(promoEl, "PromotionDescription").orElse(null);
            for (Element itemEl : childElements(promoEl, "Item", "PromotionItem")) {
                text(itemEl, "ItemCode").ifPresent(code -> add(result, code, promotion(description, itemEl, promoEl)));
            }
            // Some chains list one item per <Sale> with ItemCode directly on it.
            text(promoEl, "ItemCode").ifPresent(code -> add(result, code, promotion(description, promoEl, promoEl)));
        }
        return result;
    }

    /**
     * Keeps only promotions with a price. Basket-wide coupons ("50 off over 249") list every item
     * without one and would otherwise mark the whole store as on sale or hide a real item deal.
     */
    private static void add(Map<String, Promotion> result, String code, Promotion promo) {
        if (promo.discountedPrice() != null && promo.discountedPrice().signum() > 0) {
            result.putIfAbsent(code, promo);
        }
    }

    /** "50 off a basket over 249" style coupons: not a price for any one item. */
    private static boolean isBasketCoupon(Element promo) {
        if (text(promo, "AdditionalIsCoupon").map("1"::equals).orElse(false)) {
            return true;
        }
        NodeList amounts = promo.getElementsByTagName("MinPurchaseAmount");
        for (int i = 0; i < amounts.getLength(); i++) {
            try {
                if (new BigDecimal(amounts.item(i).getTextContent().trim()).signum() > 0) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // empty or malformed: no threshold
            }
        }
        return false;
    }

    private static Promotion promotion(String description, Element item, Element promo) {
        Integer minQty = decimal(item, "MinQty").or(() -> decimal(promo, "MinQty"))
                .map(BigDecimal::intValue).orElse(null);
        BigDecimal discounted = decimal(item, "DiscountedPrice").or(() -> decimal(promo, "DiscountedPrice"))
                .orElse(null);
        // DiscountedPricePerMida = 1 means DiscountedPrice is per unit; otherwise it is the bundle total.
        boolean perUnit = text(item, "DiscountedPricePerMida").or(() -> text(promo, "DiscountedPricePerMida"))
                .map("1"::equals).orElse(false);
        return new Promotion(description, minQty, discounted, !perUnit);
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

    private static List<Element> childElements(Element parent, String... names) {
        List<Element> out = new ArrayList<>();
        NodeList all = parent.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            for (String name : names) {
                if (e.getTagName().equalsIgnoreCase(name) && hasChildElements(e)) {
                    out.add(e);
                    break;
                }
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
