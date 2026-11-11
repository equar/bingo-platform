package com.bakaroo.bingo.backend.common.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.slf4j.MDC;
import com.bakaroo.bingo.backend.common.api.CorrelationIdFilter;

/** Graph transport for the application's plain-text notifications. Never logs message bodies or tokens. */
public class GraphMailSender extends JavaMailSenderImpl {
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String tenant;
    private final String clientId;
    private final String clientSecret;
    private final String sender;
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public GraphMailSender(HttpClient http, String tenant, String clientId, String clientSecret, String sender) {
        if (tenant.isBlank() || !tenant.matches("[a-zA-Z0-9.-]+") || clientId.isBlank()
                || clientSecret.isBlank() || !sender.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Graph email requires tenant ID, client ID, client secret, and a sender mailbox.");
        }
        this.http = http;
        this.tenant = tenant;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.sender = sender;
    }

    @Override
    public void send(SimpleMailMessage message) {
        send(new SimpleMailMessage[]{message});
    }

    @Override
    public void send(SimpleMailMessage... messages) {
        for (SimpleMailMessage message : messages) {
            try {
                if (message.getTo() == null || message.getTo().length == 0) {
                    throw new MailSendException("Graph email requires a recipient.");
                }
                var recipients = Arrays.stream(message.getTo())
                        .map(address -> Map.of("emailAddress", Map.of("address", address))).toList();
                String body = json.writeValueAsString(Map.of("message", Map.of(
                        "subject", message.getSubject() == null ? "" : message.getSubject(),
                        "body", Map.of("contentType", "Text", "content", message.getText() == null ? "" : message.getText()),
                        "toRecipients", recipients), "saveToSentItems", true));
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(
                        "https://graph.microsoft.com/v1.0/users/" + encode(sender) + "/sendMail"))
                        .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + accessToken())
                        .header("Content-Type", "application/json");
                addCorrelationHeader(requestBuilder);
                HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 202) {
                    if (response.statusCode() == 401) invalidateToken();
                    throw new MailSendException("Graph sendMail failed with HTTP " + response.statusCode() + ".");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new MailSendException("Graph email request interrupted.", exception);
            } catch (MailSendException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new MailSendException("Graph email request failed. Check connectivity and provider configuration.",
                        exception);
            }
        }
    }

    private synchronized void invalidateToken() {
        token = null;
        tokenExpires = Instant.EPOCH;
    }

    private synchronized String accessToken() throws Exception {
        if (token != null && Instant.now().isBefore(tokenExpires)) return token;
        String form = "grant_type=client_credentials&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&scope=" + encode("https://graph.microsoft.com/.default");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(
                "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded");
        addCorrelationHeader(requestBuilder);
        HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new MailSendException("Graph authentication failed with HTTP " + response.statusCode() + ".");
        }
        var payload = json.readTree(response.body());
        String value = payload.path("access_token").asText("");
        if (value.isBlank()) throw new MailSendException("Graph authentication returned no access token.");
        token = value;
        tokenExpires = Instant.now().plusSeconds(Math.max(0, payload.path("expires_in").asLong(0) - 60));
        return token;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void addCorrelationHeader(HttpRequest.Builder requestBuilder) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (CorrelationIdFilter.isValidCorrelationId(correlationId)) {
            requestBuilder.header("X-Correlation-Id", correlationId);
        }
    }
}
