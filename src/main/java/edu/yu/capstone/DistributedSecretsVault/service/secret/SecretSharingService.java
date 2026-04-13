package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.encrypt.SecretSplitter;

@Service
public class SecretSharingService {
    private final SecretSplitter secretSplitter = new SecretSplitter();

    public List<SecretPart> split(SecretKey key, String value, int threshold, int totalParts) {
        if (key == null || key.getName() == null || key.getName().isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
        Map<Integer, byte[]> parts = secretSplitter.split(value.getBytes(StandardCharsets.UTF_8), totalParts, threshold);
        List<SecretPart> secretParts = new ArrayList<>(parts.size());
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            SecretPart part = new SecretPart();
            part.setKey(key);
            part.setPartIndex(entry.getKey());
            part.setShard(entry.getValue());
            secretParts.add(part);
        }
        return secretParts;
    }
}
