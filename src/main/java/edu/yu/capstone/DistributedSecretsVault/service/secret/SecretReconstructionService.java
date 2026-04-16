package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.encrypt.SecretReconstructor;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientPartsException;

@Service
public class SecretReconstructionService {
    private final SecretReconstructor secretReconstructor = new SecretReconstructor();

    public String reconstruct(List<SecretPart> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new InsufficientPartsException();
        }
        Map<Integer, byte[]> partMap = new HashMap<>();
        for (SecretPart part : parts) {
            if (part == null || part.getShard() == null) {
                continue;
            }
            partMap.put(part.getPartIndex(), part.getShard());
        }
        if (partMap.isEmpty()) {
            throw new InsufficientPartsException();
        }
        byte[] secret = secretReconstructor.reconstruct(partMap);
        return new String(secret, StandardCharsets.UTF_8);
    }
}
