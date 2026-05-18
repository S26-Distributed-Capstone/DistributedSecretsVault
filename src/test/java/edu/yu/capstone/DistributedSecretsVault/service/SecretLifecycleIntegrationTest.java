package edu.yu.capstone.DistributedSecretsVault.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
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
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartResponse;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartsResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretReconstructionService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

/**
 * End-to-end lifecycle test using mocked infrastructure.
 * Verifies the complete flow: create → read → update → read versions → delete → confirm gone.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class SecretLifecycleIntegrationTest {

    @Mock
    private SecretPartRepository secretPartRepository;

    @Mock
    private SecretSharingService secretSharingService;

    @Mock
    private SecretReconstructionService secretReconstructionService;

    @Mock
    private NodeClient nodeClient;

    private ClusterConfig clusterConfig;
    private SecretService service;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        service = new SecretService(secretPartRepository, secretSharingService,
                secretReconstructionService, nodeClient, clusterConfig);
        lenient().when(nodeClient.resolvePeerUrls()).thenReturn(List.of());
        key = new SecretKey("alice", "db-password");
    }

    @Test
    void testFullSecretLifecycle() {
        // ── Step 1: Create ──────────────────────────────────────────────
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(eq(key), eq("original-password"), eq(2), eq(3)))
                .thenReturn(parts(key));

        SecretVersion v1 = service.storeSecret(key, "original-password");

        assertEquals(1L, v1.getVersion());
        assertEquals(key, v1.getKey());
        verify(secretPartRepository, times(3)).savePart(any(SecretPart.class));

        // ── Step 2: Read back (latest) ──────────────────────────────────
        when(secretPartRepository.exists(key)).thenReturn(true);
        SecretPart part1 = new SecretPart(key, 1L, 1, new byte[]{10, 20});
        SecretPart peerPart1 = new SecretPart(key, 1L, 2, new byte[]{21, 22});
        when(secretPartRepository.findLatest(key)).thenReturn(Optional.of(part1));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.fetchSecretPart("http://peer1:8080", key, null))
                .thenReturn(SecretPartResponse.found("http://peer1:8080", peerPart1));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("original-password");

        String readBack = service.getSecret(key, null);

        assertEquals("original-password", readBack);

        // ── Step 3: Update ──────────────────────────────────────────────
        when(secretSharingService.split(eq(key), eq("new-password"), eq(2), eq(3)))
                .thenReturn(parts(key));
        when(secretPartRepository.updatePart(any(SecretPart.class))).thenReturn(true);

        SecretVersion v2 = service.updateSecret(key, "new-password");

        assertEquals(2L, v2.getVersion());

        // ── Step 4: Read specific version 1 ─────────────────────────────
        when(secretPartRepository.listVersions(key)).thenReturn(List.of(1L, 2L));
        when(secretPartRepository.findPart(key, 1L)).thenReturn(Optional.of(part1));
        when(nodeClient.fetchSecretPart("http://peer1:8080", key, 1L))
                .thenReturn(SecretPartResponse.found("http://peer1:8080", peerPart1));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("original-password");

        String v1Read = service.getSecret(key, 1L);

        assertEquals("original-password", v1Read);

        // ── Step 5: Read latest (should be version 2) ───────────────────
        SecretPart part2 = new SecretPart(key, 2L, 1, new byte[]{30, 40});
        SecretPart peerPart2 = new SecretPart(key, 2L, 2, new byte[]{41, 42});
        when(secretPartRepository.findLatest(key)).thenReturn(Optional.of(part2));
        when(nodeClient.fetchSecretPart("http://peer1:8080", key, null))
                .thenReturn(SecretPartResponse.found("http://peer1:8080", peerPart2));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("new-password");

        String latestRead = service.getSecret(key, null);

        assertEquals("new-password", latestRead);

        // ── Step 6: Read all versions ───────────────────────────────────
        when(secretPartRepository.findPart(key, 1L)).thenReturn(Optional.of(part1));
        when(secretPartRepository.findPart(key, 2L)).thenReturn(Optional.of(part2));
        when(nodeClient.fetchAllSecretParts("http://peer1:8080", key))
                .thenReturn(SecretPartsResponse.found("http://peer1:8080", Map.of(
                        1L, peerPart1,
                        2L, peerPart2)));
        when(secretReconstructionService.reconstruct(anyList()))
                .thenReturn("original-password", "new-password");

        Map<Long, String> allVersions = service.getAllVersions(key);

        assertEquals(2, allVersions.size());
        assertEquals("original-password", allVersions.get(1L));
        assertEquals("new-password", allVersions.get(2L));

        // ── Step 7: Delete ──────────────────────────────────────────────
        service.deleteSecret(key);

        verify(secretPartRepository).deleteParts(key);

        // ── Step 8: Read after delete → not found ───────────────────────
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretPartRepository.findLatest(key)).thenReturn(Optional.empty());
        when(nodeClient.fetchSecretPart("http://peer1:8080", key, null))
                .thenReturn(SecretPartResponse.rejected("http://peer1:8080", 404, "not found"));

        assertThrows(SecretNotFoundException.class,
                () -> service.getSecret(key, null));

        // ── Step 9: Create again after delete → fresh version 1 ─────────
        when(secretSharingService.split(eq(key), eq("recreated-password"), eq(2), eq(3)))
                .thenReturn(parts(key));

        SecretVersion recreated = service.storeSecret(key, "recreated-password");

        assertEquals(1L, recreated.getVersion());
    }

    @Test
    void testUpdateAfterDeleteFails() {
        // Create
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(eq(key), anyString(), anyInt(), anyInt()))
                .thenReturn(parts(key));
        service.storeSecret(key, "value");

        // Delete
        when(secretPartRepository.exists(key)).thenReturn(true);
        service.deleteSecret(key);

        // Update after delete → not found
        when(secretPartRepository.exists(key)).thenReturn(false);
        assertThrows(SecretNotFoundException.class,
                () -> service.updateSecret(key, "new-value"));
    }

    @Test
    void testGetAllVersionsAfterDeleteFails() {
        when(secretPartRepository.listVersions(key)).thenReturn(List.of());

        assertThrows(SecretNotFoundException.class,
                () -> service.getAllVersions(key));
    }

    private List<SecretPart> parts(SecretKey key) {
        return List.of(
                new SecretPart(key, null, 1, new byte[]{1}),
                new SecretPart(key, null, 2, new byte[]{2}),
                new SecretPart(key, null, 3, new byte[]{3}));
    }
}
