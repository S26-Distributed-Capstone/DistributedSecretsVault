package edu.yu.capstone.DistributedSecretsVault.repository;

import java.util.List;
import java.util.Optional;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;

public interface SecretPartRepository {
    Optional<SecretPart> findPart(SecretKey key, long version);

    Optional<SecretPart> findLatest(SecretKey key);

    List<Long> listVersions(SecretKey key);

    boolean exists(SecretKey key);

    void savePart(SecretPart part);

    boolean updatePart(SecretPart part);

    void deleteParts(SecretKey key);
}
