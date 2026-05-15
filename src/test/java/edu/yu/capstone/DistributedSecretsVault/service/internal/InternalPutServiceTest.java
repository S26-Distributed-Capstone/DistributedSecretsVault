package edu.yu.capstone.DistributedSecretsVault.service.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class InternalPutServiceTest {
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
    private InternalPutService service;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        clusterConfig.setQuorumM(2);
        service = new InternalPutService(nodeClient, secretPartRepository, secretSharingService, pendingActionsBuffer,
                commitPublisher, clusterConfig);
        key = new SecretKey("user1", "secret1");
    }

    @Test
    void testPutSucceedsWithSufficientAcks() {
        when(secretPartRepository.findLatest(key))
                .thenReturn(Optional.of(new SecretPart(key, 1L, 1, new byte[] {0})));
        when(secretSharingService.split(key, "value2", 2, 3)).thenReturn(parts());
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendPutPrepare(anyString(), any(PutPrepareRequest.class)))
                .thenAnswer(invocation -> PeerResponse.acknowledged(invocation.getArgument(0)));

        SecretVersion version = service.putAcrossCluster(key, "value2");

        assertEquals(2L, version.getVersion());
        verify(pendingActionsBuffer).bufferAction(any(), eq(key), eq(ActionType.PUT), any(SecretPart.class));
        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
        verify(secretPartRepository, never()).updatePart(any(SecretPart.class));
    }

    @Test
    void testPutRejectsMissingLocalKeyBeforePrepare() {
        when(secretPartRepository.findLatest(key)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class, () -> service.putAcrossCluster(key, "value2"));

        verify(nodeClient, never()).resolvePeerUrls();
    }

    @Test
    void testPutFailsWhenQuorumNotReached() {
        when(secretPartRepository.findLatest(key))
                .thenReturn(Optional.of(new SecretPart(key, 1L, 1, new byte[] {0})));
        when(secretSharingService.split(key, "value2", 2, 3)).thenReturn(parts());
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendPutPrepare(anyString(), any(PutPrepareRequest.class)))
                .thenAnswer(invocation -> PeerResponse.failed(invocation.getArgument(0), "missing key"));

        assertThrows(QuorumNotReachedException.class, () -> service.putAcrossCluster(key, "value2"));

        verify(pendingActionsBuffer).discard(any());
        verify(commitPublisher, never()).broadcastCommit(any());
    }

    private List<SecretPart> parts() {
        return List.of(
                new SecretPart(key, null, 1, new byte[] {1}),
                new SecretPart(key, null, 2, new byte[] {2}),
                new SecretPart(key, null, 3, new byte[] {3}));
    }
}
