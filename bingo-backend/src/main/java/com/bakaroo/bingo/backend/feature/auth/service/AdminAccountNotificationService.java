package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.feature.user.entity.User;
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

    public AdminAccountNotificationService(JavaMailSender mailSender,
            @Value("${app.admin-account-email.from:no-reply@bingo.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
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
            log.error("Unable to send admin account activation email for user id {}", user.getId(), exception);
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
        } catch (RuntimeException exception) {
            log.error("Unable to send admin password reset email for user id {}", user.getId(), exception);
        }
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
