package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AdminAccountNotificationServiceTest {

    private static final User USER = createUser();

    @Test
    void sendsGeneratedCredentialToAccountEmail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SimpleMailMessage[] sentMessage = new SimpleMailMessage[1];
        doAnswer(invocation -> {
            sentMessage[0] = invocation.getArgument(0);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        new AdminAccountNotificationService(mailSender, "no-reply@example.invalid")
                .sendGeneratedPassword(USER, "Temporary-Admin-42!");

        assertEquals("no-reply@example.invalid", sentMessage[0].getFrom());
        assertEquals("admin@example.invalid", sentMessage[0].getTo()[0]);
        assertEquals("Your Bingo Admin account", sentMessage[0].getSubject());
        org.junit.jupiter.api.Assertions.assertTrue(sentMessage[0].getText().contains("Temporary-Admin-42!"));
    }

    @Test
    void propagatesMailDeliveryFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class,
                () -> new AdminAccountNotificationService(mailSender, "no-reply@example.invalid")
                        .sendGeneratedPassword(USER, "Temporary-Admin-42!"));
    }

    private static User createUser() {
        User user = User.create(
                "251911111111",
                "Admin User",
                "encoded-password",
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                Instant.now());
        user.setEmail("admin@example.invalid");
        return user;
    }
}
