package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class InternalDeleteServiceTest {

    @Mock
    private NodeClient nodeClient;

    @Mock
    private SecretPartRepository secretPartRepository;

    private ClusterConfig clusterConfig;
    private InternalDeleteService service;

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        clusterConfig.setQuorumM(3);
        clusterConfig.setWriteTimeoutMillis(5000L);
        clusterConfig.setLockTimeoutMillis(5000L);

        service = new InternalDeleteService(nodeClient, secretPartRepository, clusterConfig);
    }

    // ── Happy path: single-node (no peers) ─────────────────────────────

    @Test
    void testDeleteSucceedsWithNoPeers() {
        // Single-node: m=1, k=1 → required=1 (self ACK is sufficient)
        clusterConfig.setQuorumM(1);
        clusterConfig.setThresholdK(1);
        service = new InternalDeleteService(nodeClient, secretPartRepository, clusterConfig);

        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        verify(secretPartRepository).deleteParts(key);
    }

    // ── Happy path: multi-node with sufficient ACKs ────────────────────

    @Test
    void testDeleteSucceedsWithSufficientPeerAcks() {
        // m=3, k=2 → required=2 (1 self + at least 1 peer)
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any(DeletePrepareRequest.class))).thenReturn(true);
        when(nodeClient.sendDeleteCommit(anyString(), any(DeleteCommitRequest.class))).thenReturn(true);

        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        // Verify prepare was sent to both peers
        verify(nodeClient, times(2)).sendDeletePrepare(anyString(), any(DeletePrepareRequest.class));
        // Verify commit was sent to both peers
        verify(nodeClient, times(2)).sendDeleteCommit(anyString(), any(DeleteCommitRequest.class));
        // Verify local delete
        verify(secretPartRepository).deleteParts(key);
    }

    @Test
    void testDeleteSucceedsWhenSomePeersFail() {
        // m=3, k=2 → required=2 (1 self + 1 peer ACK is sufficient)
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        // Only one peer ACKs, but that's enough (1 self + 1 peer = 2 >= required 2)
        when(nodeClient.sendDeletePrepare(eq("http://peer1:8080"), any())).thenReturn(true);
        when(nodeClient.sendDeletePrepare(eq("http://peer2:8080"), any())).thenReturn(false);
        when(nodeClient.sendDeleteCommit(anyString(), any())).thenReturn(true);

        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        verify(secretPartRepository).deleteParts(key);
    }

    // ── Error paths ────────────────────────────────────────────────────

    @Test
    void testDeleteThrowsWhenSecretNotFound() {
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(false);

        assertThrows(SecretNotFoundException.class, () -> service.deleteAcrossCluster(key));

        verify(nodeClient, never()).resolvePeerUrls();
        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testDeleteThrowsWhenQuorumNotReached() {
        // m=3, k=2 → required=2. With 0 peer ACKs, self only provides 1 → fail
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any())).thenReturn(false);

        assertThrows(QuorumNotReachedException.class, () -> service.deleteAcrossCluster(key));

        // Should NOT have proceeded to commit or local delete
        verify(nodeClient, never()).sendDeleteCommit(anyString(), any());
        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testDeleteThrowsWhenAllPeersFailAndThresholdNotMet() {
        // m=3, k=1 → required=3. All peers fail → only self ACK (1) < 3
        clusterConfig.setThresholdK(1);
        service = new InternalDeleteService(nodeClient, secretPartRepository, clusterConfig);

        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any())).thenReturn(false);

        assertThrows(QuorumNotReachedException.class, () -> service.deleteAcrossCluster(key));
    }

    // ── Commit failure handling ────────────────────────────────────────

    @Test
    void testDeleteCompletesEvenWhenCommitDeliveryFails() {
        // Commit failures are logged but don't fail the operation
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any())).thenReturn(true);
        when(nodeClient.sendDeleteCommit(anyString(), any())).thenReturn(false);

        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        // Local delete should still happen
        verify(secretPartRepository).deleteParts(key);
    }

    // ── Threshold computation edge cases ───────────────────────────────

    @Test
    void testRequiredAcksMinimumIsOne() {
        // Even if m - k + 1 computes to zero or negative, require at least 1
        clusterConfig.setQuorumM(0);
        clusterConfig.setThresholdK(5);
        service = new InternalDeleteService(nodeClient, secretPartRepository, clusterConfig);

        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of());

        // Self ACK (1) >= max(0-5+1, 1) = 1 → should succeed
        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));
    }

    private SecretKey createKey(String ownerId, String name) {
        return new SecretKey(ownerId, name);
    }
}
