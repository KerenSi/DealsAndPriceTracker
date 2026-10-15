package com.dealstracker.api;

import com.dealstracker.api.Dtos.PushPublicKey;
import com.dealstracker.api.Dtos.PushSubscriptionRequest;
import com.dealstracker.api.Dtos.PushTestRequest;
import com.dealstracker.api.Dtos.PushTestResult;
import com.dealstracker.api.Dtos.PushUnsubscribeRequest;
import com.dealstracker.auth.AuthService;
import com.dealstracker.domain.PushSubscription;
import com.dealstracker.domain.User;
import com.dealstracker.notification.push.DealPushService;
import com.dealstracker.notification.push.VapidKeys;
import com.dealstracker.notification.push.WebPushCrypto;
import com.dealstracker.repository.PushSubscriptionRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Browser push: the server's public key, and the logged-in user's subscribed browsers. */
@RestController
public class PushController {

    /**
     * Push services browsers use (Chrome/Edge/Android, Firefox, Safari, legacy Edge). The server POSTs to
     * whatever endpoint is stored, so anything else is refused.
     */
    private static final List<String> PUSH_SERVICE_DOMAINS = List.of("fcm.googleapis.com", "android.googleapis.com",
            "push.services.mozilla.com", "push.apple.com", "notify.windows.com");

    private final AuthService auth;
    private final VapidKeys vapid;
    private final PushSubscriptionRepository subscriptions;
    private final DealPushService push;

    public PushController(AuthService auth, VapidKeys vapid, PushSubscriptionRepository subscriptions,
                          DealPushService push) {
        this.auth = auth;
        this.vapid = vapid;
        this.subscriptions = subscriptions;
        this.push = push;
    }

    @GetMapping("/api/push/public-key")
    public PushPublicKey publicKey() {
        return new PushPublicKey(vapid.publicKey());
    }

    @PutMapping("/api/me/push-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushSubscriptionRequest req,
                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        User user = auth.requireUser(authorization);
        if (!isPushService(req.endpoint())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported push service");
        }
        if (!WebPushCrypto.validSubscriptionKeys(req.keys().p256dh(), req.keys().auth())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription keys");
        }
        PushSubscription subscription = subscriptions.findByEndpoint(req.endpoint())
                .orElseGet(() -> new PushSubscription(user.getId(), req.endpoint(), req.keys().p256dh(),
                        req.keys().auth()));
        subscription.update(user.getId(), req.keys().p256dh(), req.keys().auth());
        subscriptions.save(subscription);
    }

    static boolean isPushService(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && PUSH_SERVICE_DOMAINS.stream()
                    .anyMatch(d -> host.equals(d) || host.endsWith("." + d));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @DeleteMapping("/api/me/push-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody PushUnsubscribeRequest req,
                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        User user = auth.requireUser(authorization);
        subscriptions.deleteByUserIdAndEndpoint(user.getId(), req.endpoint());
    }

    @PostMapping("/api/me/push-subscription/test")
    public PushTestResult test(@Valid @RequestBody(required = false) PushTestRequest req,
                               @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        User user = auth.requireUser(authorization);
        DealPushService.TestOutcome outcome = push.sendTest(user.getId(), req == null ? null : req.endpoint());
        return new PushTestResult(outcome.status().name(), outcome.delivered(), outcome.detail());
    }
}
