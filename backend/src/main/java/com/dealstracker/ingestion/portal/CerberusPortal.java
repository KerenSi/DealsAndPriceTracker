package com.dealstracker.ingestion.portal;

import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cerberus file server used by many chains (https://url.publishedprices.co.il: Tiv Taam, Osher Ad,
 * Rami Levy, ...). Flow: GET /login for a CSRF token, POST /login/user, GET /file for a fresh token,
 * POST /file/json/dir to list, GET /file/d/{name} to download.
 */
public class CerberusPortal implements ChainPortal {

    private static final Logger log = LoggerFactory.getLogger(CerberusPortal.class);
    private static final Pattern CSRF = Pattern.compile("name=\"csrftoken\"\\s+content=\"([^\"]+)\"");
    private static final Pattern FNAME = Pattern.compile("\"fname\"\\s*:\\s*\"([^\"]+)\"");
    private static final int LOGIN_ATTEMPTS = 3;

    private final String baseUrl;
    private final String username;
    private final String password;
    private final PortalHttp http = new PortalHttp();
    private String csrfToken;

    public CerberusPortal(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.username = username;
        this.password = password == null ? "" : password;
    }

    @Override
    public List<TransparencyFileName> list(String type, String storeCode, LocalDate date) throws Exception {
        ensureLoggedIn();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("sEcho", "1");
        form.put("iDisplayStart", "0");
        form.put("iDisplayLength", "100000");
        form.put("sSearch", type);
        form.put("cd", "/");
        form.put("csrftoken", csrfToken);
        HttpResponse<String> resp = http.postForm(baseUrl + "/file/json/dir", form);
        PortalHttp.requireOk(baseUrl + "/file/json/dir", resp.statusCode());
        if (!resp.body().contains("aaData")) {
            csrfToken = null;
            throw new IllegalStateException("Cerberus listing at " + baseUrl + " did not return files");
        }
        List<TransparencyFileName> files = new ArrayList<>();
        Matcher m = FNAME.matcher(resp.body());
        while (m.find()) {
            TransparencyFileName.parse(m.group(1)).ifPresent(files::add);
        }
        return files;
    }

    @Override
    public InputStream download(TransparencyFileName file) throws Exception {
        ensureLoggedIn();
        return http.download(baseUrl + "/file/d/" + URLEncoder.encode(file.name(), StandardCharsets.UTF_8));
    }

    /** Logs in; the server occasionally bounces a valid login back to the login page, so retry. */
    private void ensureLoggedIn() throws Exception {
        if (csrfToken != null) {
            return;
        }
        for (int attempt = 1; attempt <= LOGIN_ATTEMPTS; attempt++) {
            HttpResponse<String> loginPage = http.get(baseUrl + "/login");
            Map<String, String> form = new LinkedHashMap<>();
            form.put("r", "");
            form.put("username", username);
            form.put("password", password);
            form.put("Submit", "Sign in");
            form.put("csrftoken", token(loginPage.body()));
            HttpResponse<String> resp = http.postForm(baseUrl + "/login/user", form);
            if (resp.uri().getPath().endsWith("/file")) {
                csrfToken = token(resp.body());
                return;
            }
            log.warn("Cerberus login to {} as {} failed (attempt {})", baseUrl, username, attempt);
            Thread.sleep(2000L * attempt);
        }
        throw new IllegalStateException("Could not log in to " + baseUrl + " as " + username);
    }

    private static String token(String html) {
        Matcher m = CSRF.matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("No csrftoken on Cerberus page");
        }
        return m.group(1);
    }
}
