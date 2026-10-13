package com.dealstracker.api;

import com.dealstracker.ingestion.IngestionService;
import com.dealstracker.notification.DealAlertService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IngestionService ingestion;
    private final DealAlertService alerts;
    private final AdminGuard guard;
    private final Clock clock;

    public AdminController(IngestionService ingestion, DealAlertService alerts, AdminGuard guard, Clock clock) {
        this.ingestion = ingestion;
        this.alerts = alerts;
        this.guard = guard;
        this.clock = clock;
    }

    /** Runs ingestion now, then sends alerts. */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestHeader(value = AdminGuard.HEADER, required = false) String token) {
        guard.check(token);
        IngestionService.Result result = ingestion.ingestAll();
        int sent = alerts.evaluateAndNotify();
        return Map.of("ingestion", result, "alertsSent", sent);
    }

    /**
     * Backfills the past N days of history (one snapshot per day). Intended for the sample source,
     * so a fresh environment has enough history for deal detection.
     */
    @PostMapping("/backfill")
    public Map<String, Object> backfill(@RequestHeader(value = AdminGuard.HEADER, required = false) String token,
                                        @RequestParam(defaultValue = "45") int days) {
        guard.check(token);
        int snapshots = 0;
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (int d = Math.min(days, 120); d >= 1; d--) {
            OffsetDateTime at = now.minusDays(d);
            snapshots += ingestion.ingest(LocalDate.from(at), at).snapshots();
        }
        return Map.of("days", days, "snapshots", snapshots);
    }
}
