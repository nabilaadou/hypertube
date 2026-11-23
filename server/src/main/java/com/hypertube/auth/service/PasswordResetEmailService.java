package com.hypertube.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {
    private final JavaMailSender mail;
    private final String baseUrl;
    private final String from;

    public PasswordResetEmailService(JavaMailSender mail,
            @Value("${hypertube.password-reset.base-url}") String baseUrl,
            @Value("${hypertube.password-reset.from}") String from) {
        this.mail = mail;
        this.baseUrl = baseUrl;
        this.from = from;
    }

    public void send(String email, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your Hypertube password");
        message.setText("Use this link within 30 minutes to reset your password:\n\n" + baseUrl + "?token=" + token + "\n");
        mail.send(message);
    }
}
