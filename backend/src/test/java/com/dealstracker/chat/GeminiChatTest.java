package com.dealstracker.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dealstracker.config.TrackerProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiChatTest {

    private MockRestServiceServer server;
    private GeminiChat chat;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        chat = new GeminiChat(new TrackerProperties.Gemini("test-key", "gemini-test"), builder);
    }

    @Test
    void sendsConversationAndReturnsAnswerWithoutThoughts() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("instructions"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[2].parts[0].text").value("איפה הכי זול?"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[
                          {"text":"thinking...","thought":true},
                          {"text":"באושר עד, "},{"text":"52.90 ₪"}]},"finishReason":"STOP"}]}
                        """, MediaType.APPLICATION_JSON));

        String answer = chat.reply("instructions", List.of(
                new ChatMessage("user", "hi"),
                new ChatMessage("assistant", "hello"),
                new ChatMessage("user", "איפה הכי זול?")));

        assertThat(answer).isEqualTo("באושר עד, 52.90 ₪");
        server.verify();
    }

    @Test
    void rateLimitIsReportedAsBusy() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(chat.reply("instructions", List.of(new ChatMessage("user", "hi"))))
                .contains("busy");
    }

    @Test
    void blockedPromptHasNoCandidates() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", MediaType.APPLICATION_JSON));

        assertThat(chat.reply("instructions", List.of(new ChatMessage("user", "hi"))))
                .isEqualTo("Sorry, I can't help with that request.");
    }

    @Test
    void missingKeyExplainsWhatToSet() {
        GeminiChat unconfigured = new GeminiChat(new TrackerProperties.Gemini("", "gemini-test"), RestClient.builder());

        assertThat(unconfigured.reply("instructions", List.of(new ChatMessage("user", "hi"))))
                .contains("GEMINI_API_KEY");
    }
}
