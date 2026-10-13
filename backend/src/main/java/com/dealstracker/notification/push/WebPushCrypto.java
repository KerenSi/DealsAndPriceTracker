package com.dealstracker.notification.push;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The cryptography of standard Web Push, using only the JDK: payload encryption (RFC 8291, aes128gcm)
 * and VAPID authorization (RFC 8292, an ES256 JWT). Keys use the usual Web Push encoding: base64url of
 * the raw private scalar and of the uncompressed public point.
 */
public final class WebPushCrypto {

    /** Record size advertised in the aes128gcm header; payloads always fit in a single record. */
    private static final int RECORD_SIZE = 4096;

    private static final ECParameterSpec P256 = p256();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private WebPushCrypto() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(P256, RANDOM);
            return gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static KeyPair keyPair(String publicKey, String privateKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            ECPrivateKey priv = (ECPrivateKey) kf.generatePrivate(
                    new ECPrivateKeySpec(new BigInteger(1, decode(privateKey)), P256));
            return new KeyPair(publicKey(decode(publicKey)), priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid VAPID key pair", e);
        }
    }

    public static String encodePublic(ECPublicKey key) {
        return B64.encodeToString(uncompressed(key));
    }

    public static String encodePrivate(ECPrivateKey key) {
        return B64.encodeToString(fixed32(key.getS().toByteArray()));
    }

    /** Whether a browser's subscription keys are usable: a P-256 point and a 16-byte auth secret. */
    public static boolean validSubscriptionKeys(String p256dh, String auth) {
        try {
            publicKey(decode(p256dh));
            return decode(auth).length == 16;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Encrypts a push message body for a subscription's {@code p256dh} key and {@code auth} secret. */
    public static byte[] encrypt(byte[] plaintext, String p256dh, String auth) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, decode(p256dh), decode(auth), generateKeyPair(), salt);
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(asKeys.getPrivate());
            ka.doPhase(publicKey(uaPublic), true);
            byte[] ecdhSecret = ka.generateSecret();
            byte[] asPublic = uncompressed((ECPublicKey) asKeys.getPublic());

            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // 0x02 marks the last (and only) record.
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The {@code Authorization} header value for a push to {@code audience} (the endpoint's origin). */
    public static String vapidAuthorization(KeyPair vapid, String audience, String subject, long expiresAtEpochSec) {
        try {
            String header = b64Json("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
            String claims = b64Json("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}"
                    .formatted(audience, expiresAtEpochSec, subject));
            String signingInput = header + "." + claims;
            // JWS wants the raw R||S signature, which the JDK calls P1363 format.
            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
            sig.initSign(vapid.getPrivate());
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String jwt = signingInput + "." + B64.encodeToString(sig.sign());
            return "vapid t=" + jwt + ", k=" + encodePublic((ECPublicKey) vapid.getPublic());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] decode(String base64url) {
        // Browsers send unpadded base64url; tolerate standard base64 too.
        return B64D.decode(base64url.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("Expected an uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    private static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[] {4}, fixed32(key.getW().getAffineX().toByteArray()),
                fixed32(key.getW().getAffineY().toByteArray()));
    }

    /** HKDF-SHA256 with a single expand block, which covers every length Web Push needs. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[] {1})), length);
    }

    private static byte[] fixed32(byte[] unsigned) {
        byte[] out = new byte[32];
        int n = Math.min(unsigned.length, 32);
        System.arraycopy(unsigned, unsigned.length - n, out, 32 - n, n);
        return out;
    }

    private static String b64Json(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
