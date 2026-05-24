package edu.yu.capstone.DistributedSecretsVault.dto.response;

import lombok.Data;

/**
 * API response body containing a single secret value and its metadata.
 */
@Data
public class SecretResponse {
    /** The secret's name (key). */
    private String key;

    /** The version number of this secret value. */
    private long version;

    /** The reconstructed plaintext secret value. */
    private String value;
}
