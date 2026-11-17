package com.shashwat.hackcomms.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the OTP by email. If no SMTP host is configured (the dev default),
 * the code is logged to the console so you can log in without real mail.
 */
@Slf4j
@Component
public class OtpMailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public OtpMailer(ObjectProvider<JavaMailSender> mailSender,
                     @Value("${hackcomms.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String email, String code, String workspaceName, long ttlMinutes) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("No SMTP configured. OTP for {} is {}", email, code);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(email);
        msg.setSubject(code + " is your HackComms login code");
        msg.setText("""
                Your HackComms code for the %s workspace is:

                    %s

                It expires in %d minutes. If you didn't request it, ignore this email.
                """.formatted(workspaceName, code, ttlMinutes));
        sender.send(msg);
    }
}
