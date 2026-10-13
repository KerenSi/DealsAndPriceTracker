package com.dealstracker.api;

import com.dealstracker.api.Dtos.ChatRequest;
import com.dealstracker.api.Dtos.ChatResponse;
import com.dealstracker.api.Dtos.StoreDto;
import com.dealstracker.auth.PreferencesService;
import com.dealstracker.chat.ChatService;
import com.dealstracker.insights.Insight;
import com.dealstracker.insights.InsightService;
import com.dealstracker.repository.StoreRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MiscController {

    private final StoreRepository stores;
    private final InsightService insights;
    private final ChatService chat;
    private final PreferencesService preferences;

    public MiscController(StoreRepository stores, InsightService insights, ChatService chat,
                          PreferencesService preferences) {
        this.stores = stores;
        this.insights = insights;
        this.chat = chat;
        this.preferences = preferences;
    }

    @GetMapping("/stores")
    public List<StoreDto> stores() {
        return stores.findByEnabledTrueOrderByChain().stream().map(ProductController::store).toList();
    }

    @GetMapping("/insights")
    public List<Insight> insights(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return insights.insights(preferences.forRequest(authorization));
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chat.reply(request.messages()));
    }
}
