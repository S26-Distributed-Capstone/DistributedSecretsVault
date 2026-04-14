package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Validated
@ConfigurationProperties(prefix = "security")
@Data
public class SecurityConfig {
    private boolean authEnabled;
    private String oauthIssuer;
    private String oauthAudience;
}
