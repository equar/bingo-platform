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

    public void sendGeneratedPassword(User user, String rawPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject("Your Bingo Admin account");
        message.setText(buildMessage(user, rawPassword));
        mailSender.send(message);
    }

    private String buildMessage(User user, String rawPassword) {
        return String.join("\n",
                "Hello " + user.getDisplayName() + ",",
                "",
                "Your Bingo admin account is ready.",
                "",
                "Login email: " + user.getEmail(),
                "Password: " + rawPassword,
                "",
                "Please change the password after your first login.");
    }
}
