package com.dealstracker.notification;

import com.dealstracker.domain.NotificationChannel;

public interface Notifier {

    NotificationChannel channel();

    void send(String recipientId, String message) throws Exception;
}
