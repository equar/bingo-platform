package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.ActivationEmailStatus;
import com.bakaroo.bingo.backend.feature.user.entity.PasswordResetEmailStatus;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdminAccountNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountNotificationService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final UserRepository users;

    @Autowired
    public AdminAccountNotificationService(JavaMailSender mailSender,
            UserRepository users,
            @Value("${app.admin-account-email.from:no-reply@bingo.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.users = users;
        this.fromAddress = fromAddress;
    }

    public AdminAccountNotificationService(JavaMailSender mailSender, String fromAddress) {
        this(mailSender, null, fromAddress);
    }

    @Async
    public void sendActivationLink(User user, String activationLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("Complete your Bingo Admin account setup");
            message.setText(buildMessage(user, activationLink));
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("operation", "admin_activation_email")
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("userId", user.getId())
                    .addKeyValue("failureType", exception.getClass().getSimpleName())
                    .addKeyValue("causeType", causeType(exception))
                    .log("Admin activation email delivery failed");
            persistActivationStatus(user, ActivationEmailStatus.FAILED);
            return;
        }
        log.atInfo()
                .addKeyValue("operation", "admin_activation_email")
                .addKeyValue("outcome", "sent")
                .addKeyValue("userId", user.getId())
                .log("Admin activation email sent");
        persistActivationStatus(user, ActivationEmailStatus.SENT);
    }

    private void persistActivationStatus(User user, ActivationEmailStatus status) {
        if (users != null) {
            try {
                if (status == ActivationEmailStatus.SENT) {
                    users.recordActivationEmailSent(user.getId(), status, Instant.now());
                } else {
                    users.updateActivationEmailStatus(user.getId(), status, Instant.now());
                }
            } catch (RuntimeException exception) {
                log.atError()
                        .addKeyValue("operation", "activation_email_status_update")
                        .addKeyValue("outcome", "failed")
                        .addKeyValue("userId", user.getId())
                        .addKeyValue("requestedStatus", status.name())
                        .addKeyValue("failureType", exception.getClass().getSimpleName())
                        .setCause(exception)
                        .log("Activation email status update failed");
            }
        }
    }

    @Async
    public void sendPasswordResetLink(User user, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("Reset your Bingo Admin password");
            message.setText(String.join("\n",
                    "Hello " + user.getDisplayName() + ",",
                    "",
                    "An administrator requested a password reset for your account.",
                    "You cannot sign in until you choose a new password using this single-use link:",
                    resetLink,
                    "",
                    "This link expires in 24 hours. If you did not expect this email, contact your administrator."));
            mailSender.send(message);
            log.atInfo()
                    .addKeyValue("operation", "admin_password_reset_email")
                    .addKeyValue("outcome", "sent")
                    .addKeyValue("userId", user.getId())
                    .log("Admin password reset email sent");
            persistPasswordResetStatus(user, PasswordResetEmailStatus.SENT);
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("operation", "admin_password_reset_email")
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("userId", user.getId())
                    .addKeyValue("failureType", exception.getClass().getSimpleName())
                    .addKeyValue("causeType", causeType(exception))
                    .log("Admin password reset email delivery failed");
            persistPasswordResetStatus(user, PasswordResetEmailStatus.FAILED);
        }
    }

    private void persistPasswordResetStatus(User user, PasswordResetEmailStatus status) {
        if (users != null) {
            try {
                users.updatePasswordResetEmailStatus(user.getId(), status, Instant.now());
            } catch (RuntimeException exception) {
                log.atError()
                        .addKeyValue("operation", "password_reset_email_status_update")
                        .addKeyValue("outcome", "failed")
                        .addKeyValue("userId", user.getId())
                        .addKeyValue("requestedStatus", status.name())
                        .addKeyValue("failureType", exception.getClass().getSimpleName())
                        .log("Password reset email status update failed");
            }
        }
    }

    private static String causeType(Throwable exception) {
        Throwable cause = exception.getCause();
        return cause == null ? "none" : cause.getClass().getSimpleName();
    }

    private String buildMessage(User user, String activationLink) {
        return String.join("\n",
                "Hello " + user.getDisplayName() + ",",
                "",
                "An admin account has been created for you.",
                "",
                "Login email: " + user.getEmail(),
                "Set your password using this single-use link:",
                activationLink,
                "",
                "This link expires in 24 hours and can only be used once.");
    }
}
