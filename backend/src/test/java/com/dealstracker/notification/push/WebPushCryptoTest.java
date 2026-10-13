package com.dealstracker.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    /** The worked example from RFC 8291 section 5. */
    @Test
    void matchesRfc8291Example() {
        KeyPair asKeys = WebPushCrypto.keyPair(
                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
                "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] body = WebPushCrypto.encrypt(
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                WebPushCrypto.decode("BTBZMqHH6r4Tts7J_aSIgg"),
                asKeys,
                WebPushCrypto.decode("DGv6ra1nlYgDCS1FRnbzlw"));

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzA"
                        + "C8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void generatedKeysRoundTripThroughTheirEncoding() {
        KeyPair generated = WebPushCrypto.generateKeyPair();
        String pub = WebPushCrypto.encodePublic((ECPublicKey) generated.getPublic());
        String priv = WebPushCrypto.encodePrivate((ECPrivateKey) generated.getPrivate());

        KeyPair restored = WebPushCrypto.keyPair(pub, priv);

        assertThat(WebPushCrypto.decode(pub)).hasSize(65);
        assertThat(WebPushCrypto.decode(priv)).hasSize(32);
        assertThat(restored.getPublic()).isEqualTo(generated.getPublic());
        assertThat(((ECPrivateKey) restored.getPrivate()).getS())
                .isEqualTo(((ECPrivateKey) generated.getPrivate()).getS());
    }

    @Test
    void vapidHeaderCarriesAVerifiableEs256Jwt() throws Exception {
        KeyPair vapid = WebPushCrypto.generateKeyPair();

        String header = WebPushCrypto.vapidAuthorization(vapid, "https://fcm.googleapis.com",
                "mailto:test@example.com", 1_900_000_000L);

        assertThat(header).startsWith("vapid t=")
                .endsWith(", k=" + WebPushCrypto.encodePublic((ECPublicKey) vapid.getPublic()));
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).isEqualTo(
                "{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1900000000,\"sub\":\"mailto:test@example.com\"}");

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(vapid.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }
}
