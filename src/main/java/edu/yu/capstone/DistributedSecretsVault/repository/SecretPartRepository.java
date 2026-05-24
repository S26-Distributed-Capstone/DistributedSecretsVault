package edu.yu.capstone.DistributedSecretsVault.repository;

import java.util.List;
import java.util.Optional;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;

/**
 * Persistence abstraction for storing and retrieving individual secret shards.
 * <p>
 * Each node in the cluster stores at most one shard per secret version.
 * Implementations are responsible for versioned storage, existence checks,
 * and bulk deletion of all versions for a given key.
 *
 * @see edu.yu.capstone.DistributedSecretsVault.repository.impl.RedisSecretPartRepository
 */
public interface SecretPartRepository {

    /**
     * Finds a specific version of a secret shard.
     *
     * @param key     the composite secret key
     * @param version the version number to retrieve
     * @return the shard, or empty if not found
     */
    Optional<SecretPart> findPart(SecretKey key, long version);

    /**
     * Finds the latest (highest version) shard for a secret.
     *
     * @param key the composite secret key
     * @return the latest shard, or empty if no versions exist
     */
    Optional<SecretPart> findLatest(SecretKey key);

    /**
     * Lists all stored version numbers for a secret, in ascending order.
     *
     * @param key the composite secret key
     * @return list of version numbers, or an empty list if none exist
     */
    List<Long> listVersions(SecretKey key);

    /**
     * Checks whether at least one version exists for the given key.
     *
     * @param key the composite secret key
     * @return {@code true} if the secret has at least one stored version
     */
    boolean exists(SecretKey key);

    /**
     * Persists a new secret shard. Used during the commit phase of a create operation.
     *
     * @param part the shard to save (must include key, version, index, and data)
     * @throws IllegalArgumentException if {@code part} or its key is null
     */
    void savePart(SecretPart part);

    /**
     * Updates an existing secret shard in place. Used during the commit phase of
     * an update operation.
     *
     * @param part the shard with updated data
     * @return {@code true} if the version existed and was updated, {@code false} otherwise
     * @throws IllegalArgumentException if {@code part} or its key is null
     */
    boolean updatePart(SecretPart part);

    /**
     * Deletes all versions of a secret shard. Used during the commit phase of
     * a delete operation.
     *
     * @param key the composite secret key
     */
    void deleteParts(SecretKey key);
}
