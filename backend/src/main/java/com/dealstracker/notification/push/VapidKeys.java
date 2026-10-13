package com.dealstracker.notification.push;

import com.dealstracker.config.TrackerProperties;
import com.dealstracker.domain.WebPushKeys;
import com.dealstracker.repository.WebPushKeysRepository;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * The server's VAPID identity. Configured keys win; otherwise a pair is generated once and kept in the
 * database, so subscriptions survive restarts and every backend instance signs with the same key.
 */
@Component
public class VapidKeys {

    private static final Logger log = LoggerFactory.getLogger(VapidKeys.class);

    private final TrackerProperties.WebPush config;
    private final WebPushKeysRepository repository;
    private volatile KeyPair keyPair;
    private volatile String publicKey;

    public VapidKeys(TrackerProperties props, WebPushKeysRepository repository) {
        this.config = props.notifications().webPush();
        this.repository = repository;
    }

    /** The key browsers pass as {@code applicationServerKey} when subscribing. */
    public String publicKey() {
        load();
        return publicKey;
    }

    public KeyPair keyPair() {
        load();
        return keyPair;
    }

    public String subject() {
        return config.subject();
    }

    private synchronized void load() {
        if (keyPair != null) {
            return;
        }
        String pub = config.publicKey();
        String priv = config.privateKey();
        if (isBlank(pub) || isBlank(priv)) {
            WebPushKeys stored = repository.findById(WebPushKeys.ID).orElseGet(this::generate);
            pub = stored.getPublicKey();
            priv = stored.getPrivateKey();
        }
        keyPair = WebPushCrypto.keyPair(pub, priv);
        publicKey = WebPushCrypto.encodePublic((ECPublicKey) keyPair.getPublic());
    }

    private WebPushKeys generate() {
        KeyPair generated = WebPushCrypto.generateKeyPair();
        try {
            WebPushKeys keys = repository.saveAndFlush(new WebPushKeys(
                    WebPushCrypto.encodePublic((ECPublicKey) generated.getPublic()),
                    WebPushCrypto.encodePrivate((ECPrivateKey) generated.getPrivate())));
            log.info("Generated a VAPID key pair for web push");
            return keys;
        } catch (DataIntegrityViolationException e) {
            // Another instance generated one first; use theirs.
            return repository.findById(WebPushKeys.ID).orElseThrow(() -> e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
