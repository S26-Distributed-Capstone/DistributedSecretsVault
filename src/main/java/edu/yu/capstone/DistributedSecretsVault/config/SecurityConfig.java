package edu.yu.capstone.DistributedSecretsVault.config;

import lombok.Data;

@Data
public class SecurityConfig {
    private boolean authEnabled;
    private String oauthIssuer;
    private String oauthAudience;
}
