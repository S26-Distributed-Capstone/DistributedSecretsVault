package edu.yu.capstone.DistributedSecretsVault.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.NodeStateResponse;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.RecoveryHealthResponse;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.StateSummary;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

/**
 * Internal peer-recovery RPC endpoints.
 * These endpoints are always available to other nodes in the cluster.
 */
@RestController
@RequestMapping("/internal/recovery")
public class PeerRecoveryController {
    private static final Logger log = LoggerFactory.getLogger(PeerRecoveryController.class);

    private final SecretPartRepository secretPartRepository;
    private final StringRedisTemplate redisTemplate;

    public PeerRecoveryController(SecretPartRepository secretPartRepository, StringRedisTemplate redisTemplate) {
        this.secretPartRepository = secretPartRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/state")
    public ResponseEntity<NodeStateResponse> exportNodeState() {
        try {
            Set<StateSummary> state = scanLocalState();
            NodeStateResponse response = new NodeStateResponse();
            response.setNodeState(new ArrayList<>(state));
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Error exporting peer recovery state", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/shard/{user}/{key}/{version}")
    public ResponseEntity<SecretPart> getShard(
            @PathVariable String user,
            @PathVariable String key,
            @PathVariable long version) {
        try {
            Optional<SecretPart> part = secretPartRepository.findPart(new SecretKey(user, key), version);
            return part.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception ex) {
            log.error("Error retrieving recovery shard for {}:{}:{}", user, key, version, ex);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<RecoveryHealthResponse> recoveryHealth() {
        return ResponseEntity.ok(new RecoveryHealthResponse(true, "Ready to serve peer recovery requests"));
    }

    private Set<StateSummary> scanLocalState() {
        Set<StateSummary> state = new HashSet<>();
        Set<String> redisKeys = redisTemplate.keys("*:*");
        if (redisKeys == null || redisKeys.isEmpty()) {
            return state;
        }

        for (String redisKey : redisKeys) {
            if (redisKey == null) {
                continue;
            }
            String[] parts = redisKey.split(":", 2);
            if (parts.length != 2) {
                continue;
            }

            List<Long> versions = secretPartRepository.listVersions(new SecretKey(parts[0], parts[1]));
            for (Long version : versions) {
                state.add(new StateSummary(parts[0], parts[1], version, "local"));
            }
        }
        return state;
    }
}
