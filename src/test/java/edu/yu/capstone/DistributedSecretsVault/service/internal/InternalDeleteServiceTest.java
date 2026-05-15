package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;
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

    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private CommitPublisher commitPublisher;

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

        service = new InternalDeleteService(nodeClient, secretPartRepository, pendingActionsBuffer, commitPublisher,
                clusterConfig);
    }

    // ── Happy path: single-node (no peers) ─────────────────────────────

    @Test
    void testDeleteSucceedsWithNoPeers() {
        // Single-node: m=1, k=1 → required=1 (self ACK is sufficient)
        clusterConfig.setQuorumM(1);
        clusterConfig.setThresholdK(1);
        service = new InternalDeleteService(nodeClient, secretPartRepository, pendingActionsBuffer, commitPublisher,
                clusterConfig);

        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        verify(pendingActionsBuffer).bufferAction(any(), eq(key), eq(ActionType.DELETE));
        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
        verify(secretPartRepository, never()).deleteParts(key);
    }

    // ── Happy path: multi-node with sufficient ACKs ────────────────────

    @Test
    void testDeleteSucceedsWithSufficientPeerAcks() {
        // m=3, k=2 → required=2 (1 self + at least 1 peer)
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any(DeletePrepareRequest.class)))
                .thenAnswer(invocation -> PeerResponse.acknowledged(invocation.getArgument(0)));
        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        verify(nodeClient, times(2)).sendDeletePrepare(anyString(), any(DeletePrepareRequest.class));
        verify(pendingActionsBuffer).bufferAction(any(), eq(key), eq(ActionType.DELETE));
        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
        verify(secretPartRepository, never()).deleteParts(key);
    }

    @Test
    void testDeleteSucceedsWhenSomePeersFail() {
        // m=3, k=2 → required=2 (1 self + 1 peer ACK is sufficient)
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        // Only one peer ACKs, but that's enough (1 self + 1 peer = 2 >= required 2)
        when(nodeClient.sendDeletePrepare(eq("http://peer1:8080"), any()))
                .thenReturn(PeerResponse.acknowledged("http://peer1:8080"));
        when(nodeClient.sendDeletePrepare(eq("http://peer2:8080"), any()))
                .thenReturn(PeerResponse.failed("http://peer2:8080", "timeout"));
        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
        verify(secretPartRepository, never()).deleteParts(key);
    }

    // ── Error paths ────────────────────────────────────────────────────

    @Test
    void testDeleteThrowsWhenSecretNotFound() {
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(false);

        assertThrows(SecretNotFoundException.class, () -> service.deleteAcrossCluster(key));

        verify(nodeClient, never()).resolvePeerUrls();
        verify(secretPartRepository, never()).deleteParts(any());
        verify(commitPublisher, never()).broadcastCommit(any());
    }

    @Test
    void testDeleteThrowsWhenQuorumNotReached() {
        // m=3, k=2 → required=2. With 0 peer ACKs, self only provides 1 → fail
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any()))
                .thenAnswer(invocation -> PeerResponse.failed(invocation.getArgument(0), "timeout"));

        assertThrows(QuorumNotReachedException.class, () -> service.deleteAcrossCluster(key));

        verify(commitPublisher, never()).broadcastCommit(any());
        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testDeleteThrowsWhenAllPeersFailAndThresholdNotMet() {
        // m=3, k=1 → required=3. All peers fail → only self ACK (1) < 3
        clusterConfig.setThresholdK(1);
        service = new InternalDeleteService(nodeClient, secretPartRepository, pendingActionsBuffer, commitPublisher,
                clusterConfig);

        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any()))
                .thenAnswer(invocation -> PeerResponse.failed(invocation.getArgument(0), "timeout"));

        assertThrows(QuorumNotReachedException.class, () -> service.deleteAcrossCluster(key));
    }

    // ── Commit failure handling ────────────────────────────────────────

    @Test
    void testDeletePublishesCommitAfterPrepareQuorum() {
        SecretKey key = createKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any()))
                .thenAnswer(invocation -> PeerResponse.acknowledged(invocation.getArgument(0)));

        assertDoesNotThrow(() -> service.deleteAcrossCluster(key));

        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
        verify(secretPartRepository, never()).deleteParts(key);
    }

    // ── Threshold computation edge cases ───────────────────────────────

    @Test
    void testRequiredAcksMinimumIsOne() {
        // Even if m - k + 1 computes to zero or negative, require at least 1
        clusterConfig.setQuorumM(0);
        clusterConfig.setThresholdK(5);
        service = new InternalDeleteService(nodeClient, secretPartRepository, pendingActionsBuffer, commitPublisher,
                clusterConfig);

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
