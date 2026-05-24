package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API request body for deleting a secret ({@code DELETE /api/v1/secrets}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteSecretRequest {
    /** Name (key) of the secret to delete. */
    private String deleteName;

    /** Owner (user) requesting the deletion. */
    private String user;
}
