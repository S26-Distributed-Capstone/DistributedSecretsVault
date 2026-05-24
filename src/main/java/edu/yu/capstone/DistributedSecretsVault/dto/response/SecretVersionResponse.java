package edu.yu.capstone.DistributedSecretsVault.dto.response;

import lombok.Data;

/**
 * API response body for a single version of a secret.
 */
@Data
public class SecretVersionResponse {
    /** The version number. */
    private long version;

    /** The reconstructed plaintext value for this version. */
    private String value;
}
