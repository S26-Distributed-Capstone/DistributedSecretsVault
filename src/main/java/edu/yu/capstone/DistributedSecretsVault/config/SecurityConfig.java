package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Configuration properties for authentication and authorization.
 * <p>
 * Bound from the {@code security.*} prefix in {@code application.yml}.
 * When {@link #authEnabled} is {@code true}, incoming requests must include
 * a valid OAuth token issued by {@link #oauthIssuer} for the configured
 * {@link #oauthAudience}.
 */
@Validated
@ConfigurationProperties(prefix = "security")
@Data
public class SecurityConfig {
    /** Whether authentication is enforced for incoming API requests. */
    private boolean authEnabled;

    /** OAuth 2.0 issuer URL used to validate JWT tokens (e.g. {@code https://accounts.google.com}). */
    private String oauthIssuer;

    /** Expected OAuth audience claim in the JWT. */
    private String oauthAudience;
}
