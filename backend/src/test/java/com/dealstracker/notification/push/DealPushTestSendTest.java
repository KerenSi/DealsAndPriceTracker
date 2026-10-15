package com.dealstracker.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.domain.PushSubscription;
import com.dealstracker.notification.push.DealPushService.TestOutcome;
import com.dealstracker.notification.push.WebPushSender.Delivery;
import com.dealstracker.notification.push.WebPushSender.Result;
import com.dealstracker.repository.ProductInterestRepository;
import com.dealstracker.repository.PushDealNotificationRepository;
import com.dealstracker.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealPushTestSendTest {

    private final UUID user = UUID.randomUUID();
    private final PushSubscription laptop = new PushSubscription(user, "https://fcm.googleapis.com/fcm/send/laptop", "k", "a");
    private final PushSubscription phone = new PushSubscription(user, "https://fcm.googleapis.com/fcm/send/phone", "k", "a");

    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final WebPushSender sender = mock(WebPushSender.class);
    private final DealPushService service = new DealPushService(mock(PriceAnalysisService.class), subscriptions,
            mock(ProductInterestRepository.class), mock(PushDealNotificationRepository.class), sender,
            new ObjectMapper(), Clock.systemUTC());

    @Test
    void testsOnlyTheAskingBrowser() {
        when(subscriptions.findByEndpoint(laptop.getEndpoint())).thenReturn(Optional.of(laptop));
        when(sender.send(eq(laptop), anyString())).thenReturn(new Delivery(Result.FAILED, "fcm.googleapis.com answered 403 bad key"));

        TestOutcome outcome = service.sendTest(user, laptop.getEndpoint());

        assertThat(outcome.status()).isEqualTo(TestOutcome.Status.FAILED);
        assertThat(outcome.detail()).isEqualTo("fcm.googleapis.com answered 403 bad key");
        verify(sender, never()).send(eq(phone), anyString());
    }

    @Test
    void anExpiredSubscriptionIsForgottenAndReportedAsNotRegistered() {
        when(subscriptions.findByEndpoint(laptop.getEndpoint())).thenReturn(Optional.of(laptop));
        when(sender.send(eq(laptop), anyString())).thenReturn(new Delivery(Result.GONE, "410"));

        assertThat(service.sendTest(user, laptop.getEndpoint()).status()).isEqualTo(TestOutcome.Status.NOT_REGISTERED);
        verify(subscriptions).delete(laptop);
    }

    @Test
    void anotherUsersOrUnknownBrowserIsNotRegistered() {
        PushSubscription someoneElse = new PushSubscription(UUID.randomUUID(), "https://fcm.googleapis.com/fcm/send/x", "k", "a");
        when(subscriptions.findByEndpoint(someoneElse.getEndpoint())).thenReturn(Optional.of(someoneElse));

        assertThat(service.sendTest(user, someoneElse.getEndpoint()).status()).isEqualTo(TestOutcome.Status.NOT_REGISTERED);
        assertThat(service.sendTest(user, "https://fcm.googleapis.com/fcm/send/unknown").status())
                .isEqualTo(TestOutcome.Status.NOT_REGISTERED);
        verify(sender, never()).send(any(), anyString());
    }

    @Test
    void withoutAnEndpointEveryBrowserIsTried() {
        when(subscriptions.findByUserId(user)).thenReturn(List.of(laptop, phone));
        when(sender.send(eq(laptop), anyString())).thenReturn(new Delivery(Result.FAILED, "timeout"));
        when(sender.send(eq(phone), anyString())).thenReturn(new Delivery(Result.DELIVERED, null));

        TestOutcome outcome = service.sendTest(user, null);

        assertThat(outcome.status()).isEqualTo(TestOutcome.Status.DELIVERED);
        assertThat(outcome.delivered()).isEqualTo(1);
    }
}
