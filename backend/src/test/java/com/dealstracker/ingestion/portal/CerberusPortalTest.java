package com.dealstracker.ingestion.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the client against a local imitation of the publishedprices.co.il flow. */
class CerberusPortalTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger logins = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", ex -> {
            if (ex.getRequestURI().getPath().equals("/login/user")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                // First attempt bounces back to the login page, as the real server sometimes does.
                boolean ok = logins.incrementAndGet() > 1 && body.contains("username=TivTaam")
                        && body.contains("csrftoken=login-token");
                ex.getResponseHeaders().add("Set-Cookie", "cftpSID=abc; Path=/");
                ex.getResponseHeaders().add("Location", ok ? "/file" : "/login");
                send(ex, 302, "");
            } else {
                send(ex, 200, "<meta name=\"csrftoken\" content=\"login-token\"/>");
            }
        });
        server.createContext("/file", ex -> {
            String path = ex.getRequestURI().getPath();
            String cookie = ex.getRequestHeaders().getFirst("Cookie");
            if (cookie == null || !cookie.contains("cftpSID=abc")) {
                send(ex, 403, "");
            } else if (path.equals("/file/json/dir")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                send(ex, 200, body.contains("csrftoken=file-token") && body.contains("sSearch=PriceFull")
                        ? "{\"aaData\":[{\"fname\":\"PriceFull7290873255550-000-080-20261013-004004.gz\"},"
                          + "{\"fname\":\"PriceFull7290873255550-000-081-20261013-004004.gz\"}]}"
                        : "{}");
            } else if (path.startsWith("/file/d/")) {
                send(ex, 200, "<Root>" + path.substring("/file/d/".length()) + "</Root>");
            } else {
                send(ex, 200, "<meta name=\"csrftoken\" content=\"file-token\"/>");
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void logsInListsAndDownloads() throws Exception {
        CerberusPortal portal = new CerberusPortal(baseUrl, "TivTaam", "");
        List<TransparencyFileName> files = portal.list("PriceFull", "080", LocalDate.of(2026, 10, 13));
        assertThat(files).extracting(TransparencyFileName::storeCode).containsExactly("080", "081");
        assertThat(logins.get()).isEqualTo(2);

        try (InputStream in = portal.download(files.get(0))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("<Root>PriceFull7290873255550-000-080-20261013-004004.gz</Root>");
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }
}
