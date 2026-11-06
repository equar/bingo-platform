package com.bakaroo.bingo.backend.feature.auth.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DeploymentAdminBootstrapServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final DeploymentAdminBootstrapService service = new DeploymentAdminBootstrapService(
            users, encoder, mail, "sender@example.com", "https://admin.bingo.bakaroo.com/activate-account");

    @Test
    void createsAdminWithHashedReadablePasswordAndEmailsTheSamePassword() {
        when(users.findByEmailIgnoreCase("apps@bakaroo.com")).thenReturn(Optional.empty());
        assertTrue(service.bootstrap(" Apps@bakaroo.com "));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertEquals("apps@bakaroo.com", user.getEmail());
        assertTrue(user.hasRole(UserRole.ADMIN));
        assertNull(user.getNormalizedPhone());
        assertTrue(user.isMustChangePassword());
        assertNotNull(user.getTemporaryPasswordExpiresAt());
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertArrayEquals(new String[]{"apps@bakaroo.com"}, sent.getValue().getTo());
        String body = sent.getValue().getText();
        String password = body.lines().filter(line -> line.startsWith("Temporary password: "))
                .findFirst().orElseThrow().substring("Temporary password: ".length());
        assertTrue(encoder.matches(password, user.getPasswordHash()));
        assertTrue(password.matches("(?:[A-Z][a-z]{3}-){6}[2-9]{4}"));
        assertTrue(body.contains("https://admin.bingo.bakaroo.com"));
    }

    @Test
    void existingIdentityIsNeverChangedPromotedOrEmailed() {
        when(users.findByEmailIgnoreCase("equarhishe@gmail.com")).thenReturn(Optional.of(mock(User.class)));
        assertFalse(service.bootstrap("equarhishe@gmail.com"));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(mail);
    }

    @Test
    void mailFailurePropagatesWithoutLeakingCredentials() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        doThrow(new MailSendException("sensitive message body")).when(mail).send(any(SimpleMailMessage.class));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.bootstrap("apps@bakaroo.com"));
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("sensitive"));
    }

    @Test
    void invalidEmailDoesNotWriteOrSend() {
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap("bad\n@example.com"));
        verifyNoInteractions(users, mail);
    }

    @Test
    void passwordsAreIndependent() {
        assertNotEquals(service.generatePassword(), service.generatePassword());
    }
}
