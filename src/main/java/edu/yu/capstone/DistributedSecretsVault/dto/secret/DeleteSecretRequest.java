package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.Data;

@Data
public class DeleteSecretRequest {
    private final String deleteName;
    private final String deleteValue;
}
