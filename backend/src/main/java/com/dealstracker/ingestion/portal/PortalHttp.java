package com.dealstracker.ingestion.portal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A small HTTP client with its own cookie jar (portal logins are cookie sessions). */
public class PortalHttp {

    private static final int ATTEMPTS = 4;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(3);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; DealsTracker/1.0)";
    private static final Pattern JSON_URL = Pattern.compile(
            "\"(?:SPath|href|url|path|link)\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager())
            .proxy(ProxySelector.getDefault())
            .build();

    public HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return send(request(url).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> postForm(String url, Map<String, String> form)
            throws IOException, InterruptedException {
        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest req = request(url)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Downloads a file. Some portals answer the download link with a small JSON document pointing at
     * the real file (Bina's {@code [{"SPath": ...}]}, Super-Pharm's {@code {"href": ...}}); that link
     * is followed once.
     */
    public InputStream download(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> resp = send(request(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        requireOk(url, resp.statusCode());
        byte[] body = resp.body();
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("json") || startsWithJson(body)) {
            Matcher m = JSON_URL.matcher(new String(body, StandardCharsets.UTF_8));
            if (m.find()) {
                String next = URI.create(url).resolve(m.group(1).replace("\\/", "/")).toString();
                HttpResponse<byte[]> file = send(request(next).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                requireOk(next, file.statusCode());
                return new ByteArrayInputStream(file.body());
            }
        }
        return new ByteArrayInputStream(body);
    }

    /** The portals drop connections now and then (resets, TLS handshake aborts); retry those. */
    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return http.send(req, handler);
            } catch (IOException e) {
                if (attempt >= ATTEMPTS) {
                    throw e;
                }
                Thread.sleep(RETRY_DELAY.toMillis() * attempt);
            }
        }
    }

    public static void requireOk(String url, int status) {
        if (status / 100 != 2) {
            throw new IllegalStateException("GET " + url + " returned " + status);
        }
    }

    private static boolean startsWithJson(byte[] body) {
        for (byte b : body) {
            if (!Character.isWhitespace(b)) {
                return b == '[' || b == '{';
            }
        }
        return false;
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", USER_AGENT);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
