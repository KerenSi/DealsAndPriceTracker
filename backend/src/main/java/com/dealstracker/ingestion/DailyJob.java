package com.dealstracker.ingestion;

import com.dealstracker.config.TrackerProperties;
import com.dealstracker.notification.DealAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily price collection followed by deal alerts. */
@Component
public class DailyJob {

    private static final Logger log = LoggerFactory.getLogger(DailyJob.class);

    private final IngestionService ingestion;
    private final DealAlertService alerts;
    private final TrackerProperties props;

    public DailyJob(IngestionService ingestion, DealAlertService alerts, TrackerProperties props) {
        this.ingestion = ingestion;
        this.alerts = alerts;
        this.props = props;
    }

    @Scheduled(cron = "${tracker.ingestion.cron}", zone = "${tracker.timezone}")
    public void run() {
        IngestionService.Result result = ingestion.ingestAll();
        log.info("Daily ingestion finished: {}", result);
        int sent = alerts.evaluateAndNotify();
        log.info("Sent {} deal alerts", sent);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (props.ingestion().runOnStartup()) {
            run();
        }
    }
}
