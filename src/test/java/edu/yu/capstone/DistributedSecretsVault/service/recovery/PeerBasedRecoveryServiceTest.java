package edu.yu.capstone.DistributedSecretsVault.service.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.scalecube.services.Microservices;
import edu.yu.capstone.DistributedSecretsVault.config.RecoveryConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.NodeStateResponse;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.StateSummary;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PeerBasedRecoveryServiceTest {

    @Mock
    private Microservices microservices;

    @Mock
    private SecretPartRepository secretPartRepository;

    @Mock
    private NodeClient nodeClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    private PeerBasedRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        RecoveryConfig recoveryConfig = new RecoveryConfig();
        recoveryConfig.setDelaySeconds(0);
        recoveryConfig.setPeerConnectivityTimeoutSeconds(1);
        recoveryConfig.setMinRequiredPeers(1);

        recoveryService = new PeerBasedRecoveryService(
                microservices,
                secretPartRepository,
                nodeClient,
                redisTemplate,
                recoveryConfig);
    }

    @Test
    void firstClusterStartupNoPeersShouldNoOp() {
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of());

        recoveryService.onNodeStartup();

        assertEquals(PeerBasedRecoveryService.RecoveryState.COMPLETE, recoveryService.getRecoveryState());
        verify(nodeClient, never()).getNodeState(any());
        verify(nodeClient, never()).requestShard(any(), any(), any(), anyLong());
        verify(secretPartRepository, never()).savePart(any());
    }

    @Test
    void joiningNodeShouldCatchUpFromPeersAndStoreMissingShard() {
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.getNodeState("http://peer1:8080"))
                .thenReturn(new NodeStateResponse(List.of(
                        new StateSummary("alice", "db-password", 1L, "http://peer1:8080"))));

        SecretPart shard = new SecretPart(new SecretKey("alice", "db-password"), 1L, 1, new byte[] { 1, 2, 3 });
        when(nodeClient.requestShard("http://peer1:8080", "alice", "db-password", 1L)).thenReturn(shard);

        recoveryService.onNodeStartup();

        assertSame(PeerBasedRecoveryService.RecoveryState.COMPLETE, recoveryService.getRecoveryState());
        verify(secretPartRepository).savePart(shard);
        verify(nodeClient).getNodeState("http://peer1:8080");
        verify(nodeClient).requestShard("http://peer1:8080", "alice", "db-password", 1L);
    }
}
