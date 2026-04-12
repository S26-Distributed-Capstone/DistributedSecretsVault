package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.Map;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.cluster.ClusterManager;

@Service
public class SecretServiceImpl implements SecretService {
    private final SecretPartRepository secretPartRepository;
    private final ClusterManager clusterManager;
    private final SecretSharingService secretSharingService;
    private final SecretReconstructionService secretReconstructionService;

    public SecretServiceImpl(SecretPartRepository secretPartRepository,
            ClusterManager clusterManager,
            SecretSharingService secretSharingService,
            SecretReconstructionService secretReconstructionService) {
        this.secretPartRepository = secretPartRepository;
        this.clusterManager = clusterManager;
        this.secretSharingService = secretSharingService;
        this.secretReconstructionService = secretReconstructionService;
    }

    @Override
    public SecretVersion storeSecret(SecretKey key, String value) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public SecretVersion updateSecret(SecretKey key, String value) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getSecret(SecretKey key, Long version) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Map<Long, String> getAllVersions(SecretKey key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void deleteSecret(SecretKey key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
