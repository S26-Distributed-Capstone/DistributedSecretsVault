package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite key that uniquely identifies a secret within the vault.
 * <p>
 * A secret is scoped to an {@link #ownerId owner} (the user who created it)
 * and a human-readable {@link #name}. Together these form the Redis key
 * used by {@link edu.yu.capstone.DistributedSecretsVault.repository.impl.RedisSecretPartRepository}
 * in the format {@code ownerId:name}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretKey {
    /** Identifier of the user who owns this secret (e.g. a username or OAuth subject). */
    private String ownerId;

    /** Human-readable name chosen by the owner (e.g. {@code "DB_PASSWORD"}). */
    private String name;
}
