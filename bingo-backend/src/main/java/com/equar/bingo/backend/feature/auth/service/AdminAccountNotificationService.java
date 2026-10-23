package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.feature.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AdminAccountNotificationService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public AdminAccountNotificationService(JavaMailSender mailSender,
            @Value("${app.admin-account-email.from:no-reply@bingo.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendActivationLink(User user, String activationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject("Complete your Bingo Admin account setup");
        message.setText(buildMessage(user, activationLink));
        mailSender.send(message);
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
