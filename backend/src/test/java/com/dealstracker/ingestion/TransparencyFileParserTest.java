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
}
