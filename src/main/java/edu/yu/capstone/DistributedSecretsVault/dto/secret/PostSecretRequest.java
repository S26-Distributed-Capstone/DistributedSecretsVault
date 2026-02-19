package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.Data;

@Data
public class PostSecretRequest {
    private final String secretName;
    private final String value;
}
