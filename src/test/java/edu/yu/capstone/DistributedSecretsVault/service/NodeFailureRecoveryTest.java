package edu.yu.capstone.DistributedSecretsVault.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.ServiceUnavailableException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import edu.yu.capstone.DistributedSecretsVault.service.internal.ActionType;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalDeleteService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalPostService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalPutService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

/**
 * Tests simulating node failures during distributed operations.
 * Verifies that the system rolls back gracefully when peers fail at
 * different phases (prepare, commit publish) and that buffer entries
 * are properly cleaned up.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class NodeFailureRecoveryTest {

    @Mock
    private NodeClient nodeClient;

    @Mock
    private SecretPartRepository secretPartRepository;

    @Mock
    private SecretSharingService secretSharingService;

    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private CommitPublisher commitPublisher;

    private ClusterConfig clusterConfig;

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        clusterConfig.setQuorumM(3);
        clusterConfig.setLockTimeoutMillis(5000L);
        clusterConfig.setWriteTimeoutMillis(5000L);
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST — Node Failure Scenarios
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void testPostAllPeersFailDuringPrepare() {
        InternalPostService postService = new InternalPostService(
                nodeClient, secretPartRepository, secretSharingService,
                pendingActionsBuffer, commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(eq(key), eq("value"), anyInt(), anyInt()))
                .thenReturn(parts(key));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendPostPrepare(anyString(), any(PostPrepareRequest.class)))
                .thenAnswer(inv -> PeerResponse.failed(inv.getArgument(0), "connection refused"));

        assertThrows(QuorumNotReachedException.class,
                () -> postService.postAcrossCluster(key, "value"));

        verify(pendingActionsBuffer).discard(any(UUID.class));
        verify(commitPublisher, never()).broadcastCommit(any());
    }

    @Test
    void testPostPartialAcksBelowQuorum() {
        // quorumM=3 → required ACKs = 3. With 1 self + 0 peers = 1 < 3
        InternalPostService postService = new InternalPostService(
                nodeClient, secretPartRepository, secretSharingService,
                pendingActionsBuffer, commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(eq(key), eq("value"), anyInt(), anyInt()))
                .thenReturn(parts(key));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        // One peer ACKs, one fails → total = 2 (self + 1) < required 3
        when(nodeClient.sendPostPrepare(eq("http://peer1:8080"), any()))
                .thenReturn(PeerResponse.acknowledged("http://peer1:8080"));
        when(nodeClient.sendPostPrepare(eq("http://peer2:8080"), any()))
                .thenReturn(PeerResponse.failed("http://peer2:8080", "timeout"));

        assertThrows(QuorumNotReachedException.class,
                () -> postService.postAcrossCluster(key, "value"));

        verify(pendingActionsBuffer).discard(any(UUID.class));
        verify(commitPublisher, never()).broadcastCommit(any());
    }

    @Test
    void testPostPartialAcksAboveQuorum() {
        // quorumM=2 → required = 2. With 1 self + 1 peer = 2 >= 2
        clusterConfig.setQuorumM(2);
        InternalPostService postService = new InternalPostService(
                nodeClient, secretPartRepository, secretSharingService,
                pendingActionsBuffer, commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(eq(key), eq("value"), anyInt(), anyInt()))
                .thenReturn(parts(key));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendPostPrepare(eq("http://peer1:8080"), any()))
                .thenReturn(PeerResponse.acknowledged("http://peer1:8080"));
        when(nodeClient.sendPostPrepare(eq("http://peer2:8080"), any()))
                .thenReturn(PeerResponse.failed("http://peer2:8080", "timeout"));

        SecretVersion version = postService.postAcrossCluster(key, "value");

        assertEquals(1L, version.getVersion());
        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
        verify(pendingActionsBuffer, never()).discard(any(UUID.class));
    }

    @Test
    void testPostCommitPublishFailure() {
        clusterConfig.setQuorumM(1);
        InternalPostService postService = new InternalPostService(
                nodeClient, secretPartRepository, secretSharingService,
                pendingActionsBuffer, commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(eq(key), eq("value"), anyInt(), anyInt()))
                .thenReturn(parts(key));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of());
        doThrow(new ServiceUnavailableException("Kafka down"))
                .when(commitPublisher).broadcastCommit(any(CommitMessage.class));

        assertThrows(ServiceUnavailableException.class,
                () -> postService.postAcrossCluster(key, "value"));

        verify(pendingActionsBuffer).discard(any(UUID.class));
    }

    // ══════════════════════════════════════════════════════════════════════
    // PUT — Node Failure Scenarios
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void testPutPeerFailureDuringPrepare() {
        InternalPutService putService = new InternalPutService(
                nodeClient, secretPartRepository, secretSharingService,
                pendingActionsBuffer, commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        SecretPart existing = new SecretPart(key, 1L, 1, new byte[]{1});
        when(secretPartRepository.findLatest(key)).thenReturn(java.util.Optional.of(existing));
        when(secretSharingService.split(eq(key), eq("updated"), anyInt(), anyInt()))
                .thenReturn(parts(key));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendPutPrepare(anyString(), any(PutPrepareRequest.class)))
                .thenAnswer(inv -> PeerResponse.failed(inv.getArgument(0), "connection refused"));

        assertThrows(QuorumNotReachedException.class,
                () -> putService.putAcrossCluster(key, "updated"));

        verify(pendingActionsBuffer).discard(any(UUID.class));
        verify(commitPublisher, never()).broadcastCommit(any());
    }

    @Test
    void testPutSecretNotFoundBeforeNetworkCalls() {
        InternalPutService putService = new InternalPutService(
                nodeClient, secretPartRepository, secretSharingService,
                pendingActionsBuffer, commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "no-such-secret");
        when(secretPartRepository.findLatest(key)).thenReturn(java.util.Optional.empty());

        assertThrows(SecretNotFoundException.class,
                () -> putService.putAcrossCluster(key, "updated"));

        verify(nodeClient, never()).resolvePeerUrls();
        verify(pendingActionsBuffer, never()).bufferAction(any(), any(), any(), any());
    }

    @Test
    void testPutCommitPublishFailure() {
        clusterConfig.setQuorumM(1);
        InternalPutService putService = new InternalPutService(
                nodeClient, secretPartRepository, secretSharingService,
                pendingActionsBuffer, commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        SecretPart existing = new SecretPart(key, 1L, 1, new byte[]{1});
        when(secretPartRepository.findLatest(key)).thenReturn(java.util.Optional.of(existing));
        when(secretSharingService.split(eq(key), eq("updated"), anyInt(), anyInt()))
                .thenReturn(parts(key));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of());
        doThrow(new ServiceUnavailableException("Kafka down"))
                .when(commitPublisher).broadcastCommit(any(CommitMessage.class));

        assertThrows(ServiceUnavailableException.class,
                () -> putService.putAcrossCluster(key, "updated"));

        verify(pendingActionsBuffer).discard(any(UUID.class));
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE — Node Failure Scenarios
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void testDeleteAllPeersFailQuorumNotReached() {
        InternalDeleteService deleteService = new InternalDeleteService(
                nodeClient, secretPartRepository, pendingActionsBuffer,
                commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(anyString(), any(DeletePrepareRequest.class)))
                .thenAnswer(inv -> PeerResponse.failed(inv.getArgument(0), "timeout"));

        assertThrows(QuorumNotReachedException.class,
                () -> deleteService.deleteAcrossCluster(key));

        verify(pendingActionsBuffer).discard(any(UUID.class));
        verify(commitPublisher, never()).broadcastCommit(any());
    }

    @Test
    void testDeleteSufficientAcksDespiteSomeFailures() {
        // m=3, k=2 → required = 2. Self + 1 peer = 2 >= 2
        SecretKey key = new SecretKey("user1", "secret1");
        InternalDeleteService deleteService = new InternalDeleteService(
                nodeClient, secretPartRepository, pendingActionsBuffer,
                commitPublisher, clusterConfig);

        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendDeletePrepare(eq("http://peer1:8080"), any()))
                .thenReturn(PeerResponse.acknowledged("http://peer1:8080"));
        when(nodeClient.sendDeletePrepare(eq("http://peer2:8080"), any()))
                .thenReturn(PeerResponse.failed("http://peer2:8080", "timeout"));

        assertDoesNotThrow(() -> deleteService.deleteAcrossCluster(key));

        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
    }

    @Test
    void testDeleteCommitPublishFailure() {
        clusterConfig.setQuorumM(1);
        clusterConfig.setThresholdK(1);
        InternalDeleteService deleteService = new InternalDeleteService(
                nodeClient, secretPartRepository, pendingActionsBuffer,
                commitPublisher, clusterConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of());
        doThrow(new ServiceUnavailableException("Kafka down"))
                .when(commitPublisher).broadcastCommit(any(CommitMessage.class));

        assertThrows(ServiceUnavailableException.class,
                () -> deleteService.deleteAcrossCluster(key));

        verify(pendingActionsBuffer).discard(any(UUID.class));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Buffer Eviction on Timeout
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void testBufferEvictionCleansUpExpiredEntries() throws InterruptedException {
        ClusterConfig shortConfig = new ClusterConfig();
        shortConfig.setLockTimeoutMillis(50L);
        PendingActionsBuffer realBuffer = new PendingActionsBuffer(shortConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        UUID opId = UUID.randomUUID();
        realBuffer.bufferAction(opId, key, ActionType.POST,
                new SecretPart(key, 1L, 1, new byte[]{1}));

        assertTrue(realBuffer.contains(opId));

        Thread.sleep(100);
        realBuffer.evictExpired();

        assertFalse(realBuffer.contains(opId));
        assertFalse(realBuffer.containsKey(key));
    }

    @Test
    void testBufferEvictionDoesNotRemoveRecentEntries() {
        ClusterConfig longConfig = new ClusterConfig();
        longConfig.setLockTimeoutMillis(60_000L);
        PendingActionsBuffer realBuffer = new PendingActionsBuffer(longConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        UUID opId = UUID.randomUUID();
        realBuffer.bufferAction(opId, key, ActionType.DELETE);

        realBuffer.evictExpired();

        assertTrue(realBuffer.contains(opId));
        assertTrue(realBuffer.containsKey(key));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<SecretPart> parts(SecretKey key) {
        return List.of(
                new SecretPart(key, null, 1, new byte[]{1}),
                new SecretPart(key, null, 2, new byte[]{2}),
                new SecretPart(key, null, 3, new byte[]{3}));
    }
}
