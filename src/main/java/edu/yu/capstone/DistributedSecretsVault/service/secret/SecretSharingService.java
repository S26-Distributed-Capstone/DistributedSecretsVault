package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.List;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;

@Service
public class SecretSharingService {
    public List<SecretPart> split(SecretKey key, String value, int threshold, int totalParts) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
