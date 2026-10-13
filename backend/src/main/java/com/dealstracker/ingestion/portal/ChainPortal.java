package com.dealstracker.ingestion.portal;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * One chain's Price Transparency Law publishing site. Chains publish through a handful of
 * portal products (Cerberus at publishedprices.co.il, Bina Projects, plain download pages),
 * and the file names change with every upload, so the latest file is found by listing.
 */
public interface ChainPortal {

    /**
     * Lists the files of the given type ({@link TransparencyFileName#PRICE_FULL} etc.).
     * storeCode narrows the listing on portals that filter by store; it may be null.
     */
    List<TransparencyFileName> list(String type, String storeCode, LocalDate date) throws Exception;

    /** Downloads one file from the last listing. The stream may be gzipped. */
    InputStream download(TransparencyFileName file) throws Exception;
}
