package com.dealstracker.api;

import com.dealstracker.api.Dtos.AlertDto;
import com.dealstracker.api.Dtos.CreateAlertRequest;
import com.dealstracker.domain.AlertRule;
import com.dealstracker.domain.Product;
import com.dealstracker.repository.AlertRuleRepository;
import com.dealstracker.repository.ProductRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Alert subscriptions. Listing and deleting require the admin token, since rules hold recipient ids. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRuleRepository rules;
    private final ProductRepository products;
    private final AdminGuard guard;

    public AlertController(AlertRuleRepository rules, ProductRepository products, AdminGuard guard) {
        this.rules = rules;
        this.products = products;
        this.guard = guard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDto create(@Valid @RequestBody CreateAlertRequest req) {
        Product product = products.findById(req.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown product"));
        AlertRule rule = new AlertRule(product, req.channel(), req.recipientId());
        if (req.targetDiscountPercentage() != null) {
            rule.setTargetDiscountPercentage(req.targetDiscountPercentage());
        }
        return dto(rules.save(rule));
    }

    @GetMapping
    public List<AlertDto> list(@RequestHeader(value = AdminGuard.HEADER, required = false) String token) {
        guard.check(token);
        return rules.findAllBy().stream().map(AlertController::dto).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
                       @RequestHeader(value = AdminGuard.HEADER, required = false) String token) {
        guard.check(token);
        rules.deleteById(id);
    }

    private static AlertDto dto(AlertRule r) {
        return new AlertDto(r.getId(), r.getProduct().getId(), r.getProduct().getTitle(),
                r.getTargetDiscountPercentage(), r.getNotificationChannel(), r.getChannelRecipientId(),
                r.isActive(), r.getLastNotifiedAt());
    }
}
