package com.dealstracker.chat;

import com.dealstracker.config.TrackerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini through Google's Gemini API (GEMINI_API_KEY from Google AI Studio). The free tier costs
 * nothing within its rate limits; Google may use free-tier prompts to improve its products.
 */
class GeminiChat implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(GeminiChat.class);

    private final TrackerProperties.Gemini config;
    private final RestClient rest;

    GeminiChat(TrackerProperties.Gemini config, RestClient.Builder rest) {
        this.config = config;
        this.rest = rest.baseUrl("https://generativelanguage.googleapis.com").build();
    }

    @Override
    public String reply(String instructions, List<ChatMessage> conversation) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            return "Chat is not configured yet (missing GEMINI_API_KEY).";
        }
        List<Map<String, Object>> contents = conversation.stream()
                .map(m -> Map.<String, Object>of(
                        "role", "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content()))))
                .toList();
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", contents);

        try {
            JsonNode response = rest.post()
                    .uri("/v1beta/models/{model}:generateContent", config.model())
                    .header("x-goog-api-key", config.apiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return answerText(response);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                log.warn("Gemini rate limit reached: {}", e.getResponseBodyAsString());
                return "The assistant is busy right now, please try again in a minute.";
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
                    || e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                log.error("Gemini rejected the request ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
                return "Chat is not configured correctly (check GEMINI_API_KEY and GEMINI_MODEL).";
            }
            log.error("Gemini request failed with status {}", e.getStatusCode(), e);
            return "The assistant is unavailable right now.";
        } catch (RestClientException e) {
            log.error("Gemini request failed", e);
            return "The assistant is unavailable right now.";
        }
    }

    /** Joins the answer's text parts, skipping thought summaries. */
    static String answerText(JsonNode response) {
        JsonNode candidate = response == null ? null : response.path("candidates").path(0);
        if (candidate == null || candidate.isMissingNode()) {
            return "Sorry, I can't help with that request.";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        if (!text.isEmpty()) {
            return text.toString();
        }
        return "SAFETY".equals(candidate.path("finishReason").asText())
                ? "Sorry, I can't help with that request."
                : "Sorry, I couldn't come up with an answer.";
    }
}
