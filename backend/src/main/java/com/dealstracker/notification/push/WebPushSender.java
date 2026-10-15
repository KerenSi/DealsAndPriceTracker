package com.dealstracker.notification.push;

import com.dealstracker.domain.PushSubscription;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Delivers one encrypted message to one browser's push service (FCM, Mozilla, Apple, ...). */
@Component
public class WebPushSender {

    public enum Result {
        DELIVERED,
        /** The browser unsubscribed or the subscription expired; it should be forgotten. */
        GONE,
        FAILED
    }

    /** What happened, with the push service's reply (or the error) when it wasn't delivered. */
    public record Delivery(Result result, String detail) {
        static Delivery of(Result result) {
            return new Delivery(result, null);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    /** How long a push service keeps an undelivered message, e.g. while the computer is off. */
    private static final Duration TTL = Duration.ofDays(1);

    private final VapidKeys vapid;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WebPushSender(VapidKeys vapid, Clock clock) {
        this.vapid = vapid;
        this.clock = clock;
    }

    public Delivery send(PushSubscription subscription, String json) {
        try {
            URI endpoint = URI.create(subscription.getEndpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
            long expires = clock.instant().plus(Duration.ofHours(12)).getEpochSecond();
            byte[] body = WebPushCrypto.encrypt(json.getBytes(StandardCharsets.UTF_8),
                    subscription.getP256dh(), subscription.getAuth());
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization",
                            WebPushCrypto.vapidAuthorization(vapid.keyPair(), audience, vapid.subject(), expires))
                    .header("TTL", String.valueOf(TTL.toSeconds()))
                    .header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Delivery.of(Result.DELIVERED);
            }
            String detail = ("%s answered %d %s".formatted(endpoint.getHost(), status, response.body())).strip();
            if (status == 404 || status == 410) {
                return new Delivery(Result.GONE, detail);
            }
            log.warn("Push failed: {}", detail);
            return new Delivery(Result.FAILED, detail);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Delivery(Result.FAILED, "interrupted");
        } catch (Exception e) {
            log.warn("Push to subscription {} failed", subscription.getId(), e);
            return new Delivery(Result.FAILED, "couldn't reach the push service: " + e);
        }
    }
}
