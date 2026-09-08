package io.github.klisman99.collectorsauctionplatform.notifications;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("platform.mail")
record MailProperties(@Email String from, @NotNull URI webBaseUrl) {}
