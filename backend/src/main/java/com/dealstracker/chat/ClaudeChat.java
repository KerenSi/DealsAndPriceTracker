package com.dealstracker.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.dealstracker.config.TrackerProperties;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Claude through the Anthropic API (ANTHROPIC_API_KEY). */
class ClaudeChat implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeChat.class);

    private final TrackerProperties.Chat config;
    private final AnthropicClient client;

    ClaudeChat(TrackerProperties.Chat config) {
        this.config = config;
        this.client = createClient();
    }

    private static AnthropicClient createClient() {
        try {
            return AnthropicOkHttpClient.fromEnv();
        } catch (Exception e) {
            log.warn("Anthropic client not configured; chat is disabled ({})", e.getMessage());
            return null;
        }
    }

    @Override
    public String reply(String instructions, List<ChatMessage> conversation) {
        if (client == null) {
            return "Chat is not configured yet (missing ANTHROPIC_API_KEY).";
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .system(instructions)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        if (!config.model().startsWith("claude-haiku")) {
            // If a safety classifier declines, let the API retry on a fallback model (Haiku has no fallback).
            params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        for (ChatMessage m : conversation) {
            if ("assistant".equals(m.role())) {
                params.addAssistantMessage(m.content());
            } else {
                params.addUserMessage(m.content());
            }
        }

        try {
            Message response = client.messages().create(params.build());
            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                return "Sorry, I can't help with that request.";
            }
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining());
            return text.isBlank() ? "Sorry, I couldn't come up with an answer." : text;
        } catch (UnauthorizedException e) {
            log.error("Chat is misconfigured: Anthropic API rejected the credentials");
            return "Chat is not configured correctly (check ANTHROPIC_API_KEY).";
        } catch (RateLimitException e) {
            log.warn("Chat rate limited", e);
            return "The assistant is busy right now, please try again in a minute.";
        } catch (AnthropicServiceException e) {
            log.error("Chat request failed with status {}", e.statusCode(), e);
            return "The assistant is unavailable right now.";
        }
    }
}
