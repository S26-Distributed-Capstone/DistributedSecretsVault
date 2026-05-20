package edu.yu.capstone.DistributedSecretsVault.service.recovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.RecoveryConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.NodeStateResponse;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.StateSummary;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient;
import jakarta.annotation.PostConstruct;
import io.scalecube.services.Microservices;

/**
 * Peer-only recovery: every node catches up from peers when it joins a running cluster.
 * If there are no peers yet, startup is treated as the first cluster boot and recovery no-ops.
 */
@Service
public class PeerBasedRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PeerBasedRecoveryService.class);
    private static final int SHARD_REQUEST_BATCH_SIZE = 50;

    private final Microservices microservices;
    private final SecretPartRepository secretPartRepository;
    private final NodeClient nodeClient;
    private final StringRedisTemplate redisTemplate;
    private final RecoveryConfig recoveryConfig;

    @Getter
    private volatile RecoveryState recoveryState = RecoveryState.READY;

    public PeerBasedRecoveryService(Microservices microservices,
            SecretPartRepository secretPartRepository,
            NodeClient nodeClient,
            StringRedisTemplate redisTemplate,
            RecoveryConfig recoveryConfig) {
        this.microservices = microservices;
        this.secretPartRepository = secretPartRepository;
        this.nodeClient = nodeClient;
        this.redisTemplate = redisTemplate;
        this.recoveryConfig = recoveryConfig;
    }

    @PostConstruct
    public void onNodeStartup() {
        if (microservices == null || nodeClient == null || redisTemplate == null) {
            log.debug("Recovery prerequisites are unavailable; skipping peer catch-up");
            return;
        }

        try {
            if (recoveryConfig.getDelaySeconds() > 0) {
                Thread.sleep(recoveryConfig.getDelaySeconds() * 1000L);
            }
            performRecovery();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            recoveryState = RecoveryState.FAILED;
            log.error("Peer recovery interrupted", interruptedException);
        } catch (Exception ex) {
            recoveryState = RecoveryState.FAILED;
            log.error("Peer recovery failed", ex);
        }
    }

    private void performRecovery() {
        recoveryState = RecoveryState.IN_PROGRESS;

        List<String> peerUrls = waitForPeerUrls();
        if (peerUrls.isEmpty()) {
            log.info("No peers discovered during startup; treating this as the first cluster boot and skipping catch-up");
            recoveryState = RecoveryState.COMPLETE;
            return;
        }

        Map<String, Set<StateSummary>> peerState = discoverPeerState(peerUrls);
        if (peerState.isEmpty()) {
            log.info("Peers were discovered but none returned recoverable state; nothing to catch up");
            recoveryState = RecoveryState.COMPLETE;
            return;
        }

        Set<StateSummary> missingShards = determineMissingShards(peerState);
        if (missingShards.isEmpty()) {
            log.info("Local node is already caught up with the cluster");
            recoveryState = RecoveryState.COMPLETE;
            return;
        }

        int recovered = requestAndStoreMissingShards(missingShards, peerUrls);
        if (recovered == missingShards.size()) {
            recoveryState = RecoveryState.COMPLETE;
            log.info("Peer recovery complete: {} shard(s) synchronized", recovered);
        } else {
            recoveryState = RecoveryState.PARTIAL;
            log.warn("Peer recovery completed partially: {}/{} shard(s) synchronized", recovered,
                    missingShards.size());
        }
    }

    private List<String> waitForPeerUrls() {
        long deadline = System.currentTimeMillis() + (recoveryConfig.getPeerConnectivityTimeoutSeconds() * 1000L);
        long sleepMillis = 500L;
        int minRequiredPeers = Math.max(1, recoveryConfig.getMinRequiredPeers());

        while (System.currentTimeMillis() < deadline) {
            List<String> peerUrls = nodeClient.resolvePeerUrls();
            if (peerUrls.size() >= minRequiredPeers) {
                log.info("Discovered {} peer(s) for recovery", peerUrls.size());
                return peerUrls;
            }

            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return List.of();
            }
        }

        return List.of();
    }

    private Map<String, Set<StateSummary>> discoverPeerState(List<String> peerUrls) {
        Map<String, Set<StateSummary>> peerState = new HashMap<>();

        for (String peerUrl : peerUrls) {
            try {
                NodeStateResponse response = nodeClient.getNodeState(peerUrl);
                Set<StateSummary> summaries = response.getNodeState() == null
                        ? Set.of()
                        : response.getNodeState().stream()
                                .map(summary -> new StateSummary(summary.ownerId(), summary.keyName(), summary.version(),
                                        peerUrl))
                                .collect(Collectors.toSet());
                peerState.put(peerUrl, summaries);
                log.debug("Peer {} exported {} secret entry/entries", peerUrl, summaries.size());
            } catch (Exception ex) {
                log.warn("Failed to query recovery state from peer {}: {}", peerUrl, ex.getMessage());
            }
        }

        return peerState;
    }

    private Set<StateSummary> determineMissingShards(Map<String, Set<StateSummary>> peerState) {
        Set<StateSummary> allRemoteShards = peerState.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Set<String> localKeys = getLocalKeys();

        return allRemoteShards.stream()
                .filter(summary -> !localKeys.contains(summary.toRedisKey()))
                .collect(Collectors.toSet());
    }

    private Set<String> getLocalKeys() {
        Set<String> redisKeys = redisTemplate.keys("*:*" );
        if (redisKeys == null || redisKeys.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String rawKey : redisKeys) {
            if (rawKey != null && rawKey.indexOf(':') > 0) {
                String[] parts = rawKey.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                List<Long> versions = secretPartRepository.listVersions(new edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey(parts[0], parts[1]));
                for (Long version : versions) {
                    normalized.add(parts[0] + ":" + parts[1] + ":" + version);
                }
            }
        }
        return normalized;
    }

    private int requestAndStoreMissingShards(Set<StateSummary> missingShards, List<String> peerUrls) {
        int recovered = 0;
        List<StateSummary> shards = new ArrayList<>(missingShards);

        for (int i = 0; i < shards.size(); i += SHARD_REQUEST_BATCH_SIZE) {
            int end = Math.min(i + SHARD_REQUEST_BATCH_SIZE, shards.size());
            List<StateSummary> batch = shards.subList(i, end);

            for (StateSummary summary : batch) {
                Optional<SecretPart> shard = requestShardFromPeers(summary, peerUrls);
                if (shard.isPresent()) {
                    secretPartRepository.savePart(shard.get());
                    recovered++;
                    log.debug("Recovered shard {}", summary.toRedisKey());
                } else {
                    log.warn("Could not recover shard {} from any peer", summary.toRedisKey());
                }
            }
        }

        return recovered;
    }

    private Optional<SecretPart> requestShardFromPeers(StateSummary summary, List<String> peerUrls) {
        for (String peerUrl : peerUrls) {
            try {
                SecretPart shard = nodeClient.requestShard(peerUrl, summary.ownerId(), summary.keyName(), summary.version());
                if (shard != null) {
                    return Optional.of(shard);
                }
            } catch (Exception ex) {
                log.debug("Peer {} could not provide shard {}: {}", peerUrl, summary.toRedisKey(), ex.getMessage());
            }
        }
        return Optional.empty();
    }

    @Getter
    public enum RecoveryState {
        READY("Ready to recover"),
        IN_PROGRESS("Recovery in progress"),
        COMPLETE("Recovery complete"),
        PARTIAL("Recovery partially complete"),
        FAILED("Recovery failed");

        private final String description;

        RecoveryState(String description) {
            this.description = description;
        }

    }
}
