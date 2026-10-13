package com.dealstracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushControllerTest {

    @Test
    void acceptsOnlyBrowserPushServices() {
        assertThat(PushController.isPushService("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(PushController.isPushService("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(PushController.isPushService("https://web.push.apple.com/abc")).isTrue();
        assertThat(PushController.isPushService("https://wns2-par02p.notify.windows.com/w/?token=abc")).isTrue();

        assertThat(PushController.isPushService("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(PushController.isPushService("https://evilfcm.googleapis.com.attacker.io/x")).isFalse();
        assertThat(PushController.isPushService("https://169.254.169.254/latest")).isFalse();
        assertThat(PushController.isPushService("https://notfcm.googleapis.com.evil/x")).isFalse();
    }
}
