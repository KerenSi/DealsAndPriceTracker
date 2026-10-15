package com.dealstracker.chat;

import com.dealstracker.auth.UserPreferences;
import com.dealstracker.config.TrackerProperties;
import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.insights.Insight;
import com.dealstracker.insights.InsightService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Answers shopper questions with Gemini or Claude (tracker.chat.provider), grounded in the current price table.
 * The MVP dataset is small, so the full latest-price table is passed as context.
 */
@Service
public class ChatService {

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
    private final ChatModel model;

    public ChatService(PriceAnalysisService analysis, InsightService insights, TrackerProperties props,
            RestClient.Builder rest) {
        this.analysis = analysis;
        this.insights = insights;
        TrackerProperties.Chat config = props.chat();
        this.model = "claude".equalsIgnoreCase(config.provider())
                ? new ClaudeChat(config)
                : new GeminiChat(config.gemini(), rest);
    }

    public String reply(List<ChatMessage> conversation) {
        return model.reply(INSTRUCTIONS + "\n\n" + priceContext(), conversation);
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
        for (Insight i : insights.insights(UserPreferences.ANONYMOUS)) {
            sb.append("- ").append(i.message()).append('\n');
        }
        return sb.toString();
    }
}
