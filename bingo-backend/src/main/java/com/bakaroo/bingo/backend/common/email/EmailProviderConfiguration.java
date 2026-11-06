package com.bakaroo.bingo.backend.common.email;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
public class EmailProviderConfiguration {
    public EmailProviderConfiguration(@Value("${app.email.provider:smtp}") String provider) {
        if (!provider.equals("smtp") && !provider.equals("graph")) {
            throw new IllegalArgumentException("APP_EMAIL_PROVIDER must be smtp or graph.");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "graph")
    JavaMailSender graphMailSender(
            @Value("${MICROSOFT_GRAPH_TENANT_ID:${GRAPH_TENANT_ID:}}") String tenant,
            @Value("${MICROSOFT_GRAPH_CLIENT_ID:${GRAPH_CLIENT_ID:}}") String clientId,
            @Value("${MICROSOFT_GRAPH_CLIENT_SECRET:${GRAPH_CLIENT_SECRET:}}") String clientSecret,
            @Value("${app.email.from:}") String sender) {
        return new GraphMailSender(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                tenant, clientId, clientSecret, sender);
    }
}
