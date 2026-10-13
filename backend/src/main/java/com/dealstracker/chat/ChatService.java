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
import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.insights.Insight;
import com.dealstracker.insights.InsightService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Answers shopper questions using Claude, grounded in the current price table.
 * The MVP dataset is small, so the full latest-price table is passed as context.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String INSTRUCTIONS = """
            You are the shopping assistant of a price tracker for baby products (diapers and baby food)
            in local stores in Yad Eliyahu, Tel Aviv. Answer using only the price data below; if the data
            doesn't cover the question, say so. Prices are in ILS. "Effective price" already accounts for
            multi-buy offers, and "unit price" is per diaper or per 100 g, which is the fair way to compare
            pack sizes. Only compare diapers of the same size. Treat deals flagged as suspected fake
            discounts with skepticism and explain why. Reply in the language the user writes in
            (Hebrew or English), and keep answers short and concrete.
            """;

    private final PriceAnalysisService analysis;
    private final InsightService insights;
    private final TrackerProperties.Chat config;
    private final AnthropicClient client;

    public ChatService(PriceAnalysisService analysis, InsightService insights, TrackerProperties props) {
        this.analysis = analysis;
        this.insights = insights;
        this.config = props.chat();
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

    public String reply(List<ChatMessage> conversation) {
        if (client == null) {
            return "Chat is not configured yet (missing ANTHROPIC_API_KEY).";
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .system(INSTRUCTIONS + "\n\n" + priceContext())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                // If a safety classifier declines, let the API retry on a fallback model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
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

    private String priceContext() {
        StringBuilder sb = new StringBuilder("## Latest prices\n");
        sb.append("product | brand | size | store | shelf price | effective price | unit price | promotion | 30-day avg | deal\n");
        for (ListingAnalysis a : analysis.analyzeAll()) {
            var e = a.evaluation();
            String deal = e.genuineDeal() ? "GENUINE (" + e.dropPercent() + "% below avg)"
                    : e.suspectedFakeDiscount() ? "SUSPECTED FAKE DISCOUNT" : "-";
            sb.append(String.join(" | ",
                    a.product().getTitle(),
                    Objects.toString(a.product().getBrand(), "-"),
                    Objects.toString(a.product().getSizeLabel(), "-"),
                    a.store().getBranchName(),
                    a.latest().getPrice().toPlainString(),
                    e.effectivePrice().toPlainString(),
                    a.unitPrice() == null ? "-" : a.unitPrice().toPlainString() + "/" + a.unitLabel(),
                    Objects.toString(a.latest().getPromotionDescription(), "-"),
                    e.baselinePrice() == null ? "-" : e.baselinePrice().toPlainString(),
                    deal)).append('\n');
        }
        sb.append("\n## Insights\n");
        for (Insight i : insights.insights()) {
            sb.append("- ").append(i.message()).append('\n');
        }
        return sb.toString();
    }
}
