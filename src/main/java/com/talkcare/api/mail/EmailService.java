package com.talkcare.api.mail;

import com.talkcare.api.config.TalkCareProperties;
import java.util.Properties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * Plain-text email over SMTP, configured by the {@code TALKCARE_SMTP_*} variables. Port 465 uses
 * implicit TLS; any other port requires STARTTLS.
 */
@Service
public class EmailService {

    private static final String TIMEOUT_MILLIS = "15000";

    private final TalkCareProperties.Smtp smtp;
    private final JavaMailSenderImpl sender;

    public EmailService(TalkCareProperties properties) {
        this.smtp = properties.smtp();
        this.sender = smtp.isConfigured() ? createSender(smtp) : null;
    }

    public boolean isConfigured() {
        return sender != null;
    }

    public void send(String to, String subject, String text) {
        if (sender == null) {
            throw new IllegalStateException("SMTP is not configured.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtp.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }

    private static JavaMailSenderImpl createSender(TalkCareProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        sender.setUsername(smtp.username());
        sender.setPassword(smtp.password());
        boolean implicitTls = smtp.port() == 465;
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", Boolean.toString(implicitTls));
        props.put("mail.smtp.starttls.enable", Boolean.toString(!implicitTls));
        props.put("mail.smtp.starttls.required", Boolean.toString(!implicitTls));
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
        props.put("mail.smtp.timeout", TIMEOUT_MILLIS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MILLIS);
        return sender;
    }
}
