package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.encrypt.SecretReconstructor;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;

/**
 * Reassembles plaintext secret values from collected Shamir shards.
 * <p>
 * Internal read services provide the available {@link SecretPart} records, and
 * this adapter converts them into the share map expected by
 * {@link SecretReconstructor}.
 */
@Service
public class SecretReconstructionService {
    /** Stateless wrapper around the Shamir reconstruction implementation. */
    private final SecretReconstructor secretReconstructor = new SecretReconstructor();

    /**
     * Reconstructs a plaintext secret from its available shard records.
     *
     * @param parts shard records gathered from local storage and peers
     * @return reconstructed plaintext secret value
     * @throws InsufficientShardsException if no usable shards are provided
     */
    public String reconstruct(List<SecretPart> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new InsufficientShardsException();
        }
        Map<Integer, byte[]> partMap = new HashMap<>();
        for (SecretPart part : parts) {
            // Ignore null records so callers can pass partially populated peer responses.
            if (part == null || part.getShard() == null) {
                continue;
            }
            partMap.put(part.getPartIndex(), part.getShard());
        }
        if (partMap.isEmpty()) {
            throw new InsufficientShardsException();
        }
        byte[] secret = secretReconstructor.reconstruct(partMap);
        return new String(secret, StandardCharsets.UTF_8);
    }
}
