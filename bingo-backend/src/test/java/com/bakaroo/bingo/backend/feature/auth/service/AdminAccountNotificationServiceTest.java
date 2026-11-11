package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.PasswordResetEmailStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AdminAccountNotificationServiceTest {

    private static final User USER = createUser();

    @Test
    void recordsSuccessfulPasswordResetEmailStatus() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = createUser();
        ReflectionTestUtils.setField(user, "id", 81L);

        new AdminAccountNotificationService(mailSender, userRepository, "no-reply@example.invalid")
                .sendPasswordResetLink(user, "https://admin.example.invalid/reset-password#token=single-use");

        verify(userRepository).updatePasswordResetEmailStatus(
                eq(81L), eq(PasswordResetEmailStatus.SENT), any(Instant.class));
    }

    @Test
    void recordsFailedPasswordResetEmailStatus() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = createUser();
        ReflectionTestUtils.setField(user, "id", 82L);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        new AdminAccountNotificationService(mailSender, userRepository, "no-reply@example.invalid")
                .sendPasswordResetLink(user, "https://admin.example.invalid/reset-password#token=single-use");

        verify(userRepository).updatePasswordResetEmailStatus(
                eq(82L), eq(PasswordResetEmailStatus.FAILED), any(Instant.class));
    }

    @Test
    void sendsSingleUseActivationLinkWithoutPassword() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SimpleMailMessage[] sentMessage = new SimpleMailMessage[1];
        doAnswer(invocation -> {
            sentMessage[0] = invocation.getArgument(0);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        new AdminAccountNotificationService(mailSender, "no-reply@example.invalid")
                .sendActivationLink(USER, "https://admin.example.invalid/activate-account?token=single-use");

        assertEquals("no-reply@example.invalid", sentMessage[0].getFrom());
        assertEquals("admin@example.invalid", sentMessage[0].getTo()[0]);
        assertEquals("Complete your Bingo Admin account setup", sentMessage[0].getSubject());
        org.junit.jupiter.api.Assertions.assertTrue(
                sentMessage[0].getText().contains("https://admin.example.invalid/activate-account?token=single-use"));
        org.junit.jupiter.api.Assertions.assertFalse(sentMessage[0].getText().contains("Password:"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsMailDeliveryFailureWithoutFailingTheAdminRequest(CapturedOutput output) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException(
                "body-sentinel password-sentinel token-sentinel otp-sentinel"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        AdminAccountNotificationService service = new AdminAccountNotificationService(
                mailSender, "no-reply@example.invalid");
        assertDoesNotThrow(() -> service.sendActivationLink(
                USER, "http://localhost:5173/activate-account#token=activation-sentinel&otp=activation-link-sentinel"));
        assertDoesNotThrow(() -> service.sendPasswordResetLink(
                USER, "http://localhost:5173/reset-password#token=reset-sentinel&password=reset-link-sentinel"));

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
        String logs = output.getOut() + output.getErr();
        org.junit.jupiter.api.Assertions.assertTrue(logs.contains("MailSendException"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("body-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("password-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("token-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("otp-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("activation-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("activation-link-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("reset-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("reset-link-sentinel"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("admin@example.invalid"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("encoded-password"));
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
