package com.dealstracker.ingestion.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TransparencyFileNameTest {

    @Test
    void parsesCerberusNameWithSubChainAndSplitTimestamp() {
        TransparencyFileName f = TransparencyFileName.parse("PriceFull7290873255550-000-080-20261013-004004.gz").orElseThrow();
        assertThat(f.type()).isEqualTo("PriceFull");
        assertThat(f.chainCode()).isEqualTo("7290873255550");
        assertThat(f.storeCode()).isEqualTo("080");
        assertThat(f.timestamp()).isEqualTo("20261013004004");
        assertThat(f.date()).isEqualTo("20261013");
    }

    @Test
    void parsesShortNameAndNameInsideUrl() {
        TransparencyFileName f = TransparencyFileName.parse(
                "https://pricesprodpublic.blob.core.windows.net/pricefull/PriceFull7290027600007-413-202610130300.gz?sv=2014")
                .orElseThrow();
        assertThat(f.name()).isEqualTo("PriceFull7290027600007-413-202610130300.gz");
        assertThat(f.storeCode()).isEqualTo("413");
        assertThat(f.timestamp()).isEqualTo("202610130300");
    }

    @Test
    void lowercaseTypeIsCanonicalised() {
        assertThat(TransparencyFileName.parse("promofull7290172900007-001-202610130100.xml").orElseThrow().type())
                .isEqualTo("PromoFull");
    }

    @Test
    void storesFileHasTimestamp() {
        TransparencyFileName f = TransparencyFileName.parse("Stores7290103152017-000-20261013-080500.xml").orElseThrow();
        assertThat(f.type()).isEqualTo("Stores");
        assertThat(f.timestamp()).isEqualTo("20261013080500");
    }

    @Test
    void latestPicksNewestFileOfTypeForStoreIgnoringZeroPadding() {
        List<TransparencyFileName> files = List.of(
                "PriceFull7290103152017-001-024-20261013-080003.gz",
                "PriceFull7290103152017-001-024-20261013-110602.gz",
                "PriceFull7290103152017-001-025-20261013-120000.gz",
                "Price7290103152017-001-024-20261013-130000.gz",
                "PromoFull7290103152017-001-024-20261013-140000.gz").stream()
                .map(n -> TransparencyFileName.parse(n).orElseThrow())
                .toList();
        assertThat(TransparencyFileName.latest(files, "PriceFull", null, "24").orElseThrow().name())
                .isEqualTo("PriceFull7290103152017-001-024-20261013-110602.gz");
    }
}
