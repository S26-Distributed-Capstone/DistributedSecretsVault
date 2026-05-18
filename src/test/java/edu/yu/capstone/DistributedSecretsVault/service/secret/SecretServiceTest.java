package edu.yu.capstone.DistributedSecretsVault.service.secret;

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
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartResponse;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartsResponse;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class SecretServiceTest {

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

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        service = new SecretService(secretPartRepository, secretSharingService,
                secretReconstructionService, nodeClient, clusterConfig);
        lenient().when(nodeClient.resolvePeerUrls()).thenReturn(List.of());
    }

    // ── Store (Create) ──────────────────────────────────────────────────

    @Test
    void testStoreSecretCreatesVersionOne() {
        SecretKey key = new SecretKey("user1", "db-password");
        when(secretPartRepository.exists(key)).thenReturn(false);
        when(secretSharingService.split(eq(key), eq("mysecret"), eq(2), eq(3)))
                .thenReturn(parts(key));

        SecretVersion version = service.storeSecret(key, "mysecret");

        assertEquals(1L, version.getVersion());
        assertEquals(key, version.getKey());
        verify(secretPartRepository, times(3)).savePart(any(SecretPart.class));
    }

    @Test
    void testStoreSecretRejectsDuplicate() {
        SecretKey key = new SecretKey("user1", "db-password");
        when(secretPartRepository.exists(key)).thenReturn(true);

        assertThrows(DuplicateSecretException.class,
                () -> service.storeSecret(key, "mysecret"));

        verify(secretPartRepository, never()).savePart(any());
    }

    @Test
    void testStoreSecretRejectsNullKey() {
        assertThrows(IllegalArgumentException.class,
                () -> service.storeSecret(null, "mysecret"));
    }

    @Test
    void testStoreSecretRejectsBlankName() {
        SecretKey key = new SecretKey("user1", "   ");
        assertThrows(IllegalArgumentException.class,
                () -> service.storeSecret(key, "mysecret"));
    }

    @Test
    void testStoreSecretRejectsNullValue() {
        SecretKey key = new SecretKey("user1", "db-password");

        assertThrows(IllegalArgumentException.class,
                () -> service.storeSecret(key, null));
    }

    // ── Update ──────────────────────────────────────────────────────────

    @Test
    void testUpdateSecretIncrementsVersion() {
        SecretKey key = new SecretKey("user1", "db-password");
        SecretPart existingPart = new SecretPart(key, 1L, 1, new byte[]{1});
        when(secretPartRepository.exists(key)).thenReturn(true);
        when(secretPartRepository.findLatest(key)).thenReturn(Optional.of(existingPart));
        when(secretSharingService.split(eq(key), eq("newvalue"), eq(2), eq(3)))
                .thenReturn(parts(key));
        when(secretPartRepository.updatePart(any(SecretPart.class))).thenReturn(true);

        SecretVersion version = service.updateSecret(key, "newvalue");

        assertEquals(2L, version.getVersion());
        verify(secretPartRepository, times(3)).updatePart(any(SecretPart.class));
    }

    @Test
    void testUpdateSecretRejectsNonExistent() {
        SecretKey key = new SecretKey("user1", "no-such-secret");
        when(secretPartRepository.exists(key)).thenReturn(false);

        assertThrows(SecretNotFoundException.class,
                () -> service.updateSecret(key, "newvalue"));

        verify(secretPartRepository, never()).updatePart(any());
    }

    @Test
    void testUpdateSecretRejectsNullValue() {
        SecretKey key = new SecretKey("user1", "db-password");

        assertThrows(IllegalArgumentException.class,
                () -> service.updateSecret(key, null));
    }

    // ── Get Secret ──────────────────────────────────────────────────────

    @Test
    void testGetSecretLatestVersion() {
        SecretKey key = new SecretKey("user1", "db-password");
        SecretPart part = new SecretPart(key, 2L, 1, new byte[] { 1, 2 });
        SecretPart peerPart = new SecretPart(key, 2L, 2, new byte[] { 3, 4 });
        when(secretPartRepository.findLatest(key)).thenReturn(Optional.of(part));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.fetchSecretPart("http://peer1:8080", key, null))
                .thenReturn(SecretPartResponse.found("http://peer1:8080", peerPart));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("reconstructed");

        String result = service.getSecret(key, null);

        assertEquals("reconstructed", result);
        verify(secretReconstructionService).reconstruct(argThat(parts -> parts.size() == 2));
    }

    @Test
    void testGetSecretSpecificVersion() {
        SecretKey key = new SecretKey("user1", "db-password");
        SecretPart part = new SecretPart(key, 1L, 1, new byte[] { 1 });
        SecretPart peerPart = new SecretPart(key, 1L, 2, new byte[] { 2 });
        when(secretPartRepository.findPart(key, 1L)).thenReturn(Optional.of(part));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.fetchSecretPart("http://peer1:8080", key, 1L))
                .thenReturn(SecretPartResponse.found("http://peer1:8080", peerPart));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("v1-secret");

        String result = service.getSecret(key, 1L);

        assertEquals("v1-secret", result);
    }

    @Test
    void testGetSecretRejectsNonExistentKey() {
        SecretKey key = new SecretKey("user1", "no-such-secret");

        assertThrows(SecretNotFoundException.class,
                () -> service.getSecret(key, null));
    }

    @Test
    void testGetSecretRejectsNonExistentVersion() {
        SecretKey key = new SecretKey("user1", "db-password");
        when(secretPartRepository.findPart(key, 99L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class,
                () -> service.getSecret(key, 99L));
    }

    @Test
    void testGetSecretThrowsWhenInsufficientShardsFound() {
        SecretKey key = new SecretKey("user1", "db-password");
        when(secretPartRepository.findLatest(key))
                .thenReturn(Optional.of(new SecretPart(key, 1L, 1, new byte[] { 1 })));

        assertThrows(InsufficientShardsException.class,
                () -> service.getSecret(key, null));
    }

    // ── Get All Versions ────────────────────────────────────────────────

    @Test
    void testGetAllVersionsReturnsSortedMap() {
        SecretKey key = new SecretKey("user1", "db-password");
        SecretPart part1 = new SecretPart(key, 1L, 1, new byte[] { 1 });
        SecretPart part2 = new SecretPart(key, 2L, 1, new byte[] { 2 });
        SecretPart peerPart1 = new SecretPart(key, 1L, 2, new byte[] { 3 });
        SecretPart peerPart2 = new SecretPart(key, 2L, 2, new byte[] { 4 });
        when(secretPartRepository.listVersions(key)).thenReturn(List.of(2L, 1L));
        when(secretPartRepository.findPart(key, 1L)).thenReturn(Optional.of(part1));
        when(secretPartRepository.findPart(key, 2L)).thenReturn(Optional.of(part2));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.fetchAllSecretParts("http://peer1:8080", key))
                .thenReturn(SecretPartsResponse.found("http://peer1:8080", Map.of(
                        1L, peerPart1,
                        2L, peerPart2)));
        when(secretReconstructionService.reconstruct(anyList()))
                .thenReturn("v1-secret", "v2-secret");

        Map<Long, String> results = service.getAllVersions(key);

        assertEquals(2, results.size());
        assertEquals("v1-secret", results.get(1L));
        assertEquals("v2-secret", results.get(2L));
    }

    @Test
    void testGetAllVersionsThrowsWhenEmpty() {
        SecretKey key = new SecretKey("user1", "db-password");
        when(secretPartRepository.listVersions(key)).thenReturn(List.of());

        assertThrows(SecretNotFoundException.class,
                () -> service.getAllVersions(key));
    }

    // ── Delete ──────────────────────────────────────────────────────────

    @Test
    void testDeleteSecretRemovesParts() {
        SecretKey key = new SecretKey("user1", "db-password");
        when(secretPartRepository.exists(key)).thenReturn(true);

        service.deleteSecret(key);

        verify(secretPartRepository).deleteParts(key);
    }

    @Test
    void testDeleteSecretRejectsNonExistent() {
        SecretKey key = new SecretKey("user1", "no-such-secret");
        when(secretPartRepository.exists(key)).thenReturn(false);

        assertThrows(SecretNotFoundException.class,
                () -> service.deleteSecret(key));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    // ── Cluster config edge cases ───────────────────────────────────────

    @Test
    void testStoreSecretWithNullClusterConfig() {
        SecretService nullConfigService = new SecretService(
                secretPartRepository, secretSharingService,
                secretReconstructionService, nodeClient, null);
        SecretKey key = new SecretKey("user1", "db-password");
        when(secretPartRepository.exists(key)).thenReturn(false);
        // totalParts=1, threshold=1
        when(secretSharingService.split(eq(key), eq("val"), eq(1), eq(1)))
                .thenReturn(List.of(new SecretPart(key, null, 1, new byte[]{1})));

        SecretVersion version = nullConfigService.storeSecret(key, "val");

        assertEquals(1L, version.getVersion());
    }

    private List<SecretPart> parts(SecretKey key) {
        return List.of(
                new SecretPart(key, null, 1, new byte[]{1}),
                new SecretPart(key, null, 2, new byte[]{2}),
                new SecretPart(key, null, 3, new byte[]{3}));
    }
}
