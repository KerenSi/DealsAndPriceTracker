package com.dealstracker.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.dealstracker.domain.Category;
import com.dealstracker.domain.UnitType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class TransparencyFileParserTest {

    private static final String PRICES = """
            <?xml version="1.0" encoding="utf-8"?>
            <Root>
              <ChainId>7290000000000</ChainId>
              <StoreId>001</StoreId>
              <Items>
                <Item>
                  <ItemCode>7290000000001</ItemCode>
                  <ItemName>האגיס פרידום דריי מידה 4</ItemName>
                  <ManufacturerName>קימברלי קלארק</ManufacturerName>
                  <UnitQty>יחידה</UnitQty>
                  <Quantity>44</Quantity>
                  <ItemPrice>64.90</ItemPrice>
                </Item>
                <Item>
                  <ItemCode>7290000000002</ItemCode>
                  <ItemName>מטרנה שלב 1</ItemName>
                  <ManufacturerName>מטרנה</ManufacturerName>
                  <UnitQty>גרמים</UnitQty>
                  <Quantity>700</Quantity>
                  <ItemPrice>74.90</ItemPrice>
                </Item>
                <Item>
                  <ItemCode>7290000000003</ItemCode>
                  <ItemName>מגבונים לחים לתינוק</ItemName>
                  <ItemPrice>9.90</ItemPrice>
                </Item>
                <Item>
                  <ItemCode>7290000000004</ItemCode>
                  <ItemName>חלב 3%</ItemName>
                  <ItemPrice>6.90</ItemPrice>
                </Item>
              </Items>
            </Root>
            """;

    private static final String PROMOS = """
            <?xml version="1.0" encoding="utf-8"?>
            <Root>
              <Promotions>
                <Promotion>
                  <PromotionDescription>2 ב-100</PromotionDescription>
                  <MinQty>2</MinQty>
                  <DiscountedPrice>100</DiscountedPrice>
                  <PromotionItems>
                    <Item><ItemCode>7290000000001</ItemCode></Item>
                  </PromotionItems>
                </Promotion>
              </Promotions>
            </Root>
            """;

    private final TransparencyFileParser parser = new TransparencyFileParser();

    @Test
    void keepsOnlyTrackedCategoriesAndAppliesMultiBuy() throws Exception {
        Map<String, TransparencyFileParser.Promotion> promos = parser.parsePromotions(stream(PROMOS));
        List<ScrapedPrice> prices = parser.parsePrices(stream(PRICES), promos);

        assertThat(prices).hasSize(2);
        ScrapedPrice diapers = prices.get(0);
        assertThat(diapers.category()).isEqualTo(Category.DIAPERS);
        assertThat(diapers.sizeLabel()).isEqualTo("Size 4");
        assertThat(diapers.unitType()).isEqualTo(UnitType.UNITS);
        assertThat(diapers.promoMinQuantity()).isEqualTo(2);
        assertThat(diapers.promoTotalPrice()).isEqualByComparingTo("100");

        ScrapedPrice formula = prices.get(1);
        assertThat(formula.category()).isEqualTo(Category.BABY_FOOD);
        assertThat(formula.unitType()).isEqualTo(UnitType.GRAMS);
        assertThat(formula.unitQuantity()).isEqualByComparingTo("700");
        assertThat(formula.promotion()).isFalse();
    }

    @Test
    void readsGzippedFiles() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(PRICES.getBytes(StandardCharsets.UTF_8));
        }
        List<ScrapedPrice> prices = parser.parsePrices(new ByteArrayInputStream(bytes.toByteArray()), Map.of());
        assertThat(prices).hasSize(2);
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesUtf16StoresFileAndMatchesBranch() throws Exception {
        String xml = """
                <Root><ChainID>7290873255550</ChainID><SubChains><SubChain><Stores>
                  <Store><StoreID>063</StoreID><StoreName>אבן גבירול 64</StoreName><Address>אבן גבירול 64</Address><City>5000</City></Store>
                  <Store><StoreID>080</StoreID><StoreName>יגאל אלון דלי</StoreName><Address>יגאל-אלון 94</Address><City>5000</City></Store>
                </Stores></SubChain></SubChains></Root>
                """;
        // Cerberus chains publish Stores files as UTF-16 with a byte order mark and no XML declaration.
        byte[] utf16 = ("\uFEFF" + xml).getBytes(StandardCharsets.UTF_16LE);
        List<TransparencyFileParser.StoreInfo> stores = parser.parseStores(new ByteArrayInputStream(utf16));

        assertThat(stores).extracting(TransparencyFileParser.StoreInfo::storeCode).containsExactly("063", "080");
        assertThat(stores.get(1).matches("יגאל אלון")).isTrue();
        assertThat(stores.get(1).matches("דלי יגאל אלון")).isTrue();
        assertThat(stores.get(0).matches("יגאל אלון")).isFalse();
    }

    @Test
    void readsItemLevelPromotionsAndSkipsBasketCoupons() throws Exception {
        // Layout from a real Osher Ad PromoFull file: quantity and price on each PromotionItem.
        String promos = """
                <Root><Promotions>
                  <Promotion>
                    <PromotionDescription>קופון 50 מעל 249</PromotionDescription>
                    <AdditionalIsCoupon>1</AdditionalIsCoupon>
                    <Groups><Group><MinPurchaseAmount>249.00</MinPurchaseAmount><PromotionItems>
                      <PromotionItem><ItemCode>7290000000001</ItemCode><MinQty>1</MinQty><DiscountedPrice>18.50</DiscountedPrice></PromotionItem>
                      <PromotionItem><ItemCode>7290000000002</ItemCode><MinQty>1</MinQty></PromotionItem>
                    </PromotionItems></Group></Groups>
                  </Promotion>
                  <Promotion>
                    <PromotionDescription>חיתולי פרימיום פמפרס 2 ב 65</PromotionDescription>
                    <Groups><Group><PromotionItems>
                      <PromotionItem>
                        <ItemCode>7290000000001</ItemCode><MinQty>2.00</MinQty>
                        <DiscountedPrice>65.00</DiscountedPrice><DiscountedPricePerMida>1.625</DiscountedPricePerMida>
                      </PromotionItem>
                    </PromotionItems></Group></Groups>
                  </Promotion>
                </Promotions></Root>
                """;
        Map<String, TransparencyFileParser.Promotion> parsed = parser.parsePromotions(stream(promos));

        assertThat(parsed).containsOnlyKeys("7290000000001");
        TransparencyFileParser.Promotion promo = parsed.get("7290000000001");
        assertThat(promo.minQuantity()).isEqualTo(2);
        assertThat(promo.discountedPrice()).isEqualByComparingTo("65");
        assertThat(promo.totalPriceForQuantity()).isTrue();
    }

    @Test
    void pureeKeywordDoesNotMatchInsideOtherWords() {
        assertThat(CategoryClassifier.classify("קצפת צמחית השף הלבן")).isEmpty();
        assertThat(CategoryClassifier.classify("מחית תפוחי עץ מטרנה")).contains(Category.BABY_FOOD);
    }
}
