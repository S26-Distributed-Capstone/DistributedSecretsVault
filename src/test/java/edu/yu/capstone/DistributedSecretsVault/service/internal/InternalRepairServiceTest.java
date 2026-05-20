package edu.yu.capstone.DistributedSecretsVault.service.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class InternalRepairServiceTest {
    @Mock
    private NodeClient nodeClient;

    @Mock
    private SecretSharingService secretSharingService;

    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private CommitPublisher commitPublisher;

    private ClusterConfig clusterConfig;
    private InternalRepairService service;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        clusterConfig.setQuorumM(2);
        clusterConfig.setRepairEnabled(true);
        clusterConfig.setRepairTriggerBuffer(1);
        service = new InternalRepairService(nodeClient, secretSharingService, pendingActionsBuffer,
                commitPublisher, clusterConfig);
        key = new SecretKey("user1", "secret1");
    }

    @Test
    void testShouldRepairAtThresholdOrThresholdPlusBuffer() {
        assertTrue(service.shouldRepairLatestRead(2));
        assertTrue(service.shouldRepairLatestRead(3));
    }

    @Test
    void testShouldNotRepairWhenDisabledOrBelowThreshold() {
        assertFalse(service.shouldRepairLatestRead(1));
        clusterConfig.setRepairEnabled(false);
        assertFalse(service.shouldRepairLatestRead(2));
    }

    @Test
    void testRepairStagesAndPublishesCommitWhenQuorumReached() {
        when(secretSharingService.split(key, "secret", 2, 3)).thenReturn(parts());
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendRepairPrepare(anyString(), any(RepairPrepareRequest.class)))
                .thenAnswer(invocation -> PeerResponse.acknowledged(invocation.getArgument(0)));

        service.repairLatestVersion(key, 4L, "secret");

        verify(pendingActionsBuffer).bufferAction(any(), eq(key), eq(ActionType.REPAIR), any(SecretPart.class));
        verify(commitPublisher).broadcastCommit(any(CommitMessage.class));
    }

    @Test
    void testRepairDiscardsWhenQuorumNotReached() {
        when(secretSharingService.split(key, "secret", 2, 3)).thenReturn(parts());
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080", "http://peer2:8080"));
        when(nodeClient.sendRepairPrepare(anyString(), any(RepairPrepareRequest.class)))
                .thenAnswer(invocation -> PeerResponse.failed(invocation.getArgument(0), "timeout"));

        service.repairLatestVersion(key, 4L, "secret");

        verify(pendingActionsBuffer).discard(any());
        verify(commitPublisher, never()).broadcastCommit(any());
    }

    private List<SecretPart> parts() {
        return List.of(
                new SecretPart(key, null, 1, new byte[] { 1 }),
                new SecretPart(key, null, 2, new byte[] { 2 }),
                new SecretPart(key, null, 3, new byte[] { 3 }));
    }
}
