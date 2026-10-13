package com.dealstracker.ingestion;

import com.dealstracker.domain.Store;
import java.time.LocalDate;
import java.util.List;

/** Fetches current prices for the tracked categories from one data source. */
public interface PriceSource {

    String name();

    /** Whether this source can produce prices for the given store branch. */
    boolean supports(Store store);

    List<ScrapedPrice> fetch(Store store, LocalDate date) throws Exception;
}
