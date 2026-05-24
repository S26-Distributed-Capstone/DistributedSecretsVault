package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API request body for creating a new secret ({@code POST /api/v1/secrets}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostSecretRequest {
    /** Name (key) for the new secret. */
    private String secretName;

    /** Plaintext value of the secret. */
    private String secretValue;

    /** Owner (user) creating the secret. */
    private String user;
}
