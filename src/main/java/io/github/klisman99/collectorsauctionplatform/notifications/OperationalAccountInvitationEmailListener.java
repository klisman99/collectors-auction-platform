package io.github.klisman99.collectorsauctionplatform.notifications;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import io.github.klisman99.collectorsauctionplatform.identity.OperationalAccountInvited;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class OperationalAccountInvitationEmailListener {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    OperationalAccountInvitationEmailListener(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @ApplicationModuleListener
    void sendInvitationEmail(OperationalAccountInvited event) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(event.email());
        helper.setSubject("You are invited to Collectors Auction Platform operations");
        helper.setText("""
                You have been invited to a dedicated %s account for Collectors Auction Platform.

                Activate the operational account with this single-use link:
                %s

                This link expires in 24 hours. Operational accounts cannot sell or bid.
                """.formatted(event.role().toLowerCase(java.util.Locale.ROOT), activationUrl(event.activationToken())));
        mailSender.send(message);
    }

    private String activationUrl(String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return properties.webBaseUrl().resolve("/?operationalActivationToken=" + encodedToken).toString();
    }
}
