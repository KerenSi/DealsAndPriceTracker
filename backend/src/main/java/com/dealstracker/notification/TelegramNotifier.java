package com.dealstracker.notification;

import com.dealstracker.config.TrackerProperties;
import com.dealstracker.domain.NotificationChannel;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sends messages via the Telegram Bot API (sendMessage). */
@Component
public class TelegramNotifier implements Notifier {

    private final String botToken;
    private final RestClient rest = RestClient.create("https://api.telegram.org");

    public TelegramNotifier(TrackerProperties props) {
        this.botToken = props.notifications().telegram().botToken();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public void send(String chatId, String message) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is not configured");
        }
        rest.post()
                .uri("/bot{token}/sendMessage", botToken)
                .body(Map.of("chat_id", chatId, "text", message, "disable_web_page_preview", true))
                .retrieve()
                .toBodilessEntity();
    }
}
