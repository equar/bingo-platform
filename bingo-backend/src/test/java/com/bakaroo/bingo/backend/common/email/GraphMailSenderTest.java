package com.bakaroo.bingo.backend.common.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;

class GraphMailSenderTest {
    private final HttpClient http = mock(HttpClient.class);
    private final GraphMailSender sender = new GraphMailSender(http, "tenant.example", "client-id",
            "sensitive-client-secret", "apps@bakaroo.com");

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private SimpleMailMessage message() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("recipient@example.com");
        message.setSubject("Your account");
        message.setText("Temporary password: sensitive-password");
        return message;
    }

    @Test
    void authenticatesWithApplicationCredentialsSendsViaMailboxAndCachesToken() throws Exception {
        var auth = response(200, "{\"access_token\":\"access-token\",\"expires_in\":3600}");
        var accepted = response(202, "");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(auth, accepted);
        sender.send(message());
        sender.send(message());
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(requests.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        var sent = requests.getAllValues();
        assertEquals("https://login.microsoftonline.com/tenant.example/oauth2/v2.0/token", sent.get(0).uri().toString());
        assertEquals("https://graph.microsoft.com/v1.0/users/apps%40bakaroo.com/sendMail", sent.get(1).uri().toString());
        assertEquals("Bearer access-token", sent.get(1).headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void rejectsAuthenticationWithoutExposingProviderResponse() throws Exception {
        var denied = response(401, "sensitive-client-secret");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(denied);
        MailSendException error = assertThrows(MailSendException.class, () -> sender.send(message()));
        assertTrue(error.getMessage().contains("401"));
        assertFalse(error.getMessage().contains("sensitive"));
        assertNull(error.getCause());
        verify(http).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void sendFailurePropagatesForBootstrapRollbackWithoutLeakingBody() throws Exception {
        var auth = response(200, "{\"access_token\":\"access-token\",\"expires_in\":3600}");
        var denied = response(403, "sensitive-password");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(auth, denied);
        MailSendException error = assertThrows(MailSendException.class, () -> sender.send(message()));
        assertTrue(error.getMessage().contains("403"));
        assertFalse(error.getMessage().contains("sensitive"));
    }

    @Test
    void rejectsUnsupportedProviderAndMissingGraphConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new EmailProviderConfiguration("unknown"));
        assertThrows(IllegalArgumentException.class, () -> new GraphMailSender(http, "", "id", "secret", "sender@example.com"));
        assertDoesNotThrow(() -> new EmailProviderConfiguration("smtp"));
        assertDoesNotThrow(() -> new EmailProviderConfiguration("graph"));
    }
}
