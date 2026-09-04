package io.github.klisman99.collectorsauctionplatform.notifications;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountRegistered;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class VerificationEmailListener {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    VerificationEmailListener(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @ApplicationModuleListener
    void sendVerificationEmail(RegularAccountRegistered event) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(event.email());
        helper.setSubject("Verify your Collectors Auction Platform account");
        helper.setText("""
                Welcome to Collectors Auction Platform, %s.

                Verify your email address to activate trading access:
                %s

                This link expires in 24 hours and can only be used once.
                """.formatted(event.publicHandle(), verificationUrl(event.verificationToken())));
        mailSender.send(message);
    }

    private String verificationUrl(String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return properties.webBaseUrl().resolve("/?verificationToken=" + encodedToken).toString();
    }
}
