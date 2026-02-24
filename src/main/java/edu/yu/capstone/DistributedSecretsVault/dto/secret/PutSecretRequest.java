package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.Data;

@Data
public class PutSecretRequest {
    private final String secretCurrentName;
    private final String secretCurrentValue;
    private final String secretUpdatedName;
    private final String secretUpdatedValue;
}
