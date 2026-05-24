package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API request body for updating an existing secret ({@code PUT /api/v1/secrets}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PutSecretRequest {
    /** Current name (key) of the secret to update. */
    private String secretCurrentName;

    /** New plaintext value to replace the existing secret. */
    private String secretUpdatedValue;

    /** Owner (user) requesting the update. */
    private String user;
}
