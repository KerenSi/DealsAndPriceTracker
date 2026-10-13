package com.dealstracker.ingestion.portal;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A chain that publishes an HTML (or JSON) page listing its files (Shufersal, Super-Pharm, Carrefour).
 * The listing URL is a template; placeholders: {type} (PriceFull...), {catId} (Shufersal's category
 * number), {storeCode}, {date} (yyyy-MM-dd), {dateCompact} (yyyyMMdd), {page}.
 * Files are downloaded from the link on the page when there is one, otherwise from downloadUrl,
 * a template with {name} and {fileDate} (yyyyMMdd from the file name).
 */
public class WebIndexPortal implements ChainPortal {

    private static final Pattern LINK = Pattern.compile("(?:href|src)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    /** Shufersal's FileObject categories. */
    private static final Map<String, String> SHUFERSAL_CATEGORY = Map.of(
            "Price", "1", TransparencyFileName.PRICE_FULL, "2", "Promo", "3",
            TransparencyFileName.PROMO_FULL, "4", TransparencyFileName.STORES, "5");

    private final String listUrl;
    private final String downloadUrl;
    private final int maxPages;
    private final PortalHttp http = new PortalHttp();
    private final Map<String, String> links = new HashMap<>();

    public WebIndexPortal(String listUrl, String downloadUrl, Integer maxPages) {
        this.listUrl = listUrl;
        this.downloadUrl = downloadUrl;
        this.maxPages = maxPages == null || maxPages < 1 ? 1 : maxPages;
    }

    @Override
    public List<TransparencyFileName> list(String type, String storeCode, LocalDate date) throws Exception {
        Map<String, TransparencyFileName> found = new LinkedHashMap<>();
        for (LocalDate day : List.of(date, date.minusDays(1))) {
            for (int page = 1; page <= maxPages; page++) {
                String url = listUrl
                        .replace("{type}", type)
                        .replace("{catId}", SHUFERSAL_CATEGORY.getOrDefault(type, "0"))
                        .replace("{storeCode}", storeCode == null || type.equals(TransparencyFileName.STORES)
                                ? "0" : storeCode)
                        .replace("{date}", day.toString())
                        .replace("{dateCompact}", day.format(DateTimeFormatter.BASIC_ISO_DATE))
                        .replace("{page}", Integer.toString(page));
                HttpResponse<String> resp = http.get(url);
                PortalHttp.requireOk(url, resp.statusCode());
                int before = found.size();
                collect(url, resp.body(), type, found);
                if (found.size() == before || !listUrl.contains("{page}")) {
                    break;
                }
            }
            if (!listUrl.contains("{date")) {
                break;
            }
        }
        return new ArrayList<>(found.values());
    }

    private void collect(String pageUrl, String body, String type, Map<String, TransparencyFileName> found) {
        Matcher link = LINK.matcher(body);
        while (link.find()) {
            String href = link.group(1).replace("&amp;", "&");
            TransparencyFileName.parse(href).filter(f -> f.type().equals(type)).ifPresent(f -> {
                found.putIfAbsent(f.name(), f);
                links.put(f.name(), URI.create(pageUrl).resolve(href.replace(" ", "%20")).toString());
            });
        }
        // Pages that build their table in JavaScript only carry the bare names.
        Matcher name = TransparencyFileName.PATTERN.matcher(body);
        while (name.find()) {
            TransparencyFileName f = TransparencyFileName.of(name);
            if (f.type().equals(type)) {
                found.putIfAbsent(f.name(), f);
            }
        }
    }

    @Override
    public InputStream download(TransparencyFileName file) throws Exception {
        String url = links.get(file.name());
        if (url == null) {
            if (downloadUrl == null || downloadUrl.isBlank()) {
                throw new IllegalStateException("No download link for " + file.name());
            }
            url = downloadUrl.replace("{name}", file.name()).replace("{fileDate}", file.date());
        }
        return http.download(url);
    }
}
