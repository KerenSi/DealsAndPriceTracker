package com.dealstracker.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dealstracker.config.TrackerProperties;
import com.dealstracker.domain.Store;
import com.dealstracker.ingestion.portal.ChainPortal;
import com.dealstracker.ingestion.portal.TransparencyFileName;
import com.dealstracker.repository.StoreRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TransparencyPriceSourceTest {

    private static final String STORES = """
            <Root><Stores>
              <Store><StoreID>001</StoreID><StoreName>סופר-פארם יגאל אלון</StoreName><Address>יגאל אלון 65</Address><City>תל אביב</City></Store>
              <Store><StoreID>002</StoreID><StoreName>סופר-פארם דיזנגוף</StoreName><Address>דיזנגוף 50</Address><City>תל אביב</City></Store>
              <Store><StoreID>003</StoreID><StoreName>סופר-פארם מגדלי אלון</StoreName><Address>יגאל אלון 94</Address><City>תל אביב</City></Store>
            </Stores></Root>
            """;

    /** A portal with one Stores file. */
    private static final ChainPortal PORTAL = new ChainPortal() {
        @Override
        public List<TransparencyFileName> list(String type, String storeCode, LocalDate date) {
            return List.of(TransparencyFileName.parse("Stores7290172900007-000-202610130500.xml").orElseThrow());
        }

        @Override
        public InputStream download(TransparencyFileName file) {
            return new ByteArrayInputStream(STORES.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Test
    void resolvesTwoBranchesWithTheSameMatchToDifferentStores() throws Exception {
        StoreRepository repo = mock(StoreRepository.class);
        TransparencyPriceSource source = new TransparencyPriceSource(props(), repo);
        Store first = store();
        Store second = store();
        when(repo.findByChainAndEnabledTrue("SUPER_PHARM")).thenReturn(List.of(first, second));

        assertThat(source.resolveStoreCode(PORTAL, first, LocalDate.of(2026, 10, 13))).isEqualTo("001");
        assertThat(first.getChainCode()).isEqualTo("7290172900007");
        verify(repo).save(first);

        assertThat(source.resolveStoreCode(PORTAL, second, LocalDate.of(2026, 10, 13))).isEqualTo("003");
    }

    private static Store store() {
        Store s = new Store("SUPER_PHARM", "Super-Pharm Yigal Alon", "Tel Aviv");
        ReflectionTestUtils.setField(s, "storeMatch", "יגאל אלון");
        return s;
    }

    private static TrackerProperties props() {
        return new TrackerProperties(null, List.of(), null, null, null,
                new TrackerProperties.Sources(null, new TrackerProperties.Transparency(true, List.of(), Map.of())),
                null, null);
    }
}
