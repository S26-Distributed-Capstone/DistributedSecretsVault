package edu.yu.capstone.DistributedSecretsVault.service.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class InternalPostServiceTest {
    @Mock
    private NodeClient nodeClient;

    @Mock
    private SecretPartRepository secretPartRepository;

    @Mock
    private SecretSharingService secretSharingService;

    private ClusterConfig clusterConfig;
    private InternalPostService service;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        clusterConfig.setQuorumM(2);
        service = new InternalPostService(nodeClient, secretPartRepository, secretSharingService, clusterConfig);
        key = new SecretKey("user1", "secret1");
    }

    @Test
    void testPostSucceedsWithSufficientAcks() {
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(key, "value1", 2, 3)).thenReturn(parts());
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendPostPrepare(anyString(), any(PostPrepareRequest.class)))
                .thenAnswer(invocation -> PeerResponse.acknowledged(invocation.getArgument(0)));
        when(nodeClient.sendPostCommit(anyString(), any(PostCommitRequest.class)))
                .thenAnswer(invocation -> PeerResponse.acknowledged(invocation.getArgument(0)));

        SecretVersion version = service.postAcrossCluster(key, "value1");

        assertEquals(1L, version.getVersion());
        verify(secretPartRepository).savePart(any(SecretPart.class));
        verify(nodeClient, times(2)).sendPostCommit(anyString(), any(PostCommitRequest.class));
    }

    @Test
    void testPostRejectsDuplicateBeforePrepare() {
        when(secretPartRepository.exists(key)).thenReturn(true);

        assertThrows(DuplicateSecretException.class, () -> service.postAcrossCluster(key, "value1"));

        verify(nodeClient, never()).resolvePeerUrls();
    }

    @Test
    void testPostFailsWhenQuorumNotReached() {
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(key, "value1", 2, 3)).thenReturn(parts());
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendPostPrepare(anyString(), any(PostPrepareRequest.class)))
                .thenAnswer(invocation -> PeerResponse.failed(invocation.getArgument(0), "timeout"));

        assertThrows(QuorumNotReachedException.class, () -> service.postAcrossCluster(key, "value1"));

        verify(secretPartRepository, never()).savePart(any());
        verify(nodeClient, never()).sendPostCommit(anyString(), any());
    }

    private List<SecretPart> parts() {
        return List.of(
                new SecretPart(key, null, 1, new byte[] {1}),
                new SecretPart(key, null, 2, new byte[] {2}),
                new SecretPart(key, null, 3, new byte[] {3}));
    }
}
