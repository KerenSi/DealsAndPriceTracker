package com.dealstracker.ingestion.portal;

import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Bina Projects portals ({chain}.binaprojects.com: Good Pharm, King Store, Maayan 2000, ...).
 * POST MainIO_Hok.aspx lists one day's files as JSON; Download.aspx?FileNm= answers with the
 * file's real location ({@code [{"SPath": ...}]}).
 */
public class BinaProjectsPortal implements ChainPortal {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String baseUrl;
    private final PortalHttp http = new PortalHttp();

    public BinaProjectsPortal(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    public List<TransparencyFileName> list(String type, String storeCode, LocalDate date) throws Exception {
        List<TransparencyFileName> files = new ArrayList<>();
        // Files for today may not be up yet early in the morning; include yesterday.
        for (LocalDate day : List.of(date, date.minusDays(1))) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("WStore", "");
            form.put("WDate", day.format(DAY));
            form.put("WFileType", "0");
            HttpResponse<String> resp = http.postForm(baseUrl + "/MainIO_Hok.aspx", form);
            PortalHttp.requireOk(baseUrl + "/MainIO_Hok.aspx", resp.statusCode());
            Matcher m = TransparencyFileName.PATTERN.matcher(resp.body());
            while (m.find()) {
                TransparencyFileName f = TransparencyFileName.of(m);
                if (f.type().equals(type)) {
                    files.add(f);
                }
            }
        }
        return files;
    }

    @Override
    public InputStream download(TransparencyFileName file) throws Exception {
        return http.download(baseUrl + "/Download.aspx?FileNm=" + URLEncoder.encode(file.name(), StandardCharsets.UTF_8));
    }
}
