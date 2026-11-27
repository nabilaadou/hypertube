package com.hypertube.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailChangeEmailService {
    private final JavaMailSender mail;
    private final String baseUrl;
    private final String from;

    public EmailChangeEmailService(JavaMailSender mail,
            @Value("${hypertube.email-change.base-url}") String baseUrl,
            @Value("${hypertube.email-change.from}") String from) {
        this.mail = mail;
        this.baseUrl = baseUrl;
        this.from = from;
    }

    public void send(String email, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Confirm your Hypertube email");
        message.setText("Use this link within 30 minutes to confirm your email:\n\n" + baseUrl + "?token=" + token + "\n");
        mail.send(message);
    }
}
