package com.bakaroo.bingo.backend.feature.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.security.DeploymentAdminBootstrapService;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.admin-account-email.from=bootstrap@example.test")
@ActiveProfiles("test")
class DeploymentAdminBootstrapIntegrationTest extends MySqlIntegrationTest {
    @Autowired DeploymentAdminBootstrapService service;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate transactions;
    @MockitoBean JavaMailSender mail;

    @Test
    void deliveryFailureRollsBackAndNextAttemptCreatesOnlyOnce() {
        String email = "bootstrap-" + UUID.randomUUID() + "@example.test";
        try {
            doThrow(new MailSendException("simulated delivery failure"))
                    .when(mail).send(any(SimpleMailMessage.class));
            assertThrows(IllegalStateException.class, () -> service.bootstrap(email));
            assertTrue(users.findByEmailIgnoreCase(email).isEmpty());

            reset(mail);
            assertTrue(service.bootstrap(email));
            assertTrue(users.findByEmailIgnoreCase(email).isPresent());
            assertFalse(service.bootstrap(email));
            verify(mail, times(1)).send(any(SimpleMailMessage.class));
        } finally {
            transactions.executeWithoutResult(status -> users.findByEmailIgnoreCase(email).ifPresent(users::delete));
        }
    }
}
