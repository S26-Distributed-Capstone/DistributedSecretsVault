package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import edu.yu.capstone.DistributedSecretsVault.service.cluster.ClusterManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecretServiceTest {

    @Mock
    private SecretPartRepository secretPartRepository;

    @Mock
    private ClusterManager clusterManager;

    @Mock
    private SecretSharingService secretSharingService;

    @Mock
    private SecretReconstructionService secretReconstructionService;

    @Mock
    private ClusterConfig clusterConfig;

    @InjectMocks
    private SecretService secretService;

    private SecretKey validKey;

    @BeforeEach
    void setUp() {
        validKey = new SecretKey();
        validKey.setOwnerId("user1");
        validKey.setName("my_secret");
    }

    private SecretPart createPart(int index, long version) {
        SecretPart p = new SecretPart();
        p.setPartIndex(index);
        p.setVersion(version);
        return p;
    }

    // --- Validation tests ---

    @Test
    void testValidateKey() {
        assertThrows(IllegalArgumentException.class, () -> secretService.storeSecret(null, "val"));

        SecretKey badNameKey = new SecretKey();
        assertThrows(IllegalArgumentException.class, () -> secretService.storeSecret(badNameKey, "val"));

        badNameKey.setName("");
        assertThrows(IllegalArgumentException.class, () -> secretService.storeSecret(badNameKey, "val"));

        badNameKey.setName(" ");
        assertThrows(IllegalArgumentException.class, () -> secretService.storeSecret(badNameKey, "val"));
    }

    // --- storeSecret ---

    @Test
    void storeSecret_NullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> secretService.storeSecret(validKey, null));
    }

    @Test
    void storeSecret_DuplicateThrows() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        assertThrows(DuplicateSecretException.class, () -> secretService.storeSecret(validKey, "val"));
    }

    @Test
    void storeSecret_Success() {
        when(clusterConfig.getTotalNodes()).thenReturn(3);
        when(clusterConfig.getThresholdK()).thenReturn(2);
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        List<SecretPart> splits = Arrays.asList(createPart(1, 0), createPart(2, 0), createPart(3, 0));
        when(secretSharingService.split(validKey, "val", 2, 3)).thenReturn(splits);

        SecretVersion sv = secretService.storeSecret(validKey, "val");

        assertNotNull(sv);
        assertEquals(validKey, sv.getKey());
        assertEquals(1L, sv.getVersion());
        assertEquals(1L, sv.getEpoch());

        verify(secretPartRepository, times(3)).savePart(any(SecretPart.class));
    }

    // --- updateSecret ---

    @Test
    void updateSecret_NullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> secretService.updateSecret(validKey, null));
    }

    @Test
    void updateSecret_NotFoundThrows() {
        when(secretPartRepository.exists(validKey)).thenReturn(false);
        assertThrows(SecretNotFoundException.class, () -> secretService.updateSecret(validKey, "val"));
    }

    @Test
    void updateSecret_UpdatePartFails_Throws() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        SecretPart p = createPart(1, 1L);
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.of(p));

        when(clusterConfig.getTotalNodes()).thenReturn(3);
        when(clusterConfig.getThresholdK()).thenReturn(2);

        List<SecretPart> splits = Collections.singletonList(createPart(1, 2L));
        when(secretSharingService.split(validKey, "val", 2, 3)).thenReturn(splits);

        // Simulation update failure
        when(secretPartRepository.updatePart(any())).thenReturn(false);

        assertThrows(SecretNotFoundException.class, () -> secretService.updateSecret(validKey, "val"));
    }

    @Test
    void updateSecret_Success() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.empty()); // Next version will be 1

        when(clusterConfig.getTotalNodes()).thenReturn(1);
        when(clusterConfig.getThresholdK()).thenReturn(1);
        List<SecretPart> splits = Collections.singletonList(createPart(1, 0L));
        when(secretSharingService.split(validKey, "val", 1, 1)).thenReturn(splits);
        when(secretPartRepository.updatePart(any())).thenReturn(true);

        SecretVersion sv = secretService.updateSecret(validKey, "val");
        assertNotNull(sv);
        assertEquals(1L, sv.getVersion());
        verify(secretPartRepository, times(1)).updatePart(any(SecretPart.class));
    }

    // --- getSecret ---
    @Test
    void getSecret_NotFoundThrows() {
        when(secretPartRepository.exists(validKey)).thenReturn(false);
        assertThrows(SecretNotFoundException.class, () -> secretService.getSecret(validKey, null));
    }

    @Test
    void getSecret_WithExplicitVersionNotFound() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L, 2L));
        assertThrows(SecretNotFoundException.class, () -> secretService.getSecret(validKey, 3L));
    }

    @Test
    void getSecret_LatestVersionNotFound() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.empty());
        assertThrows(SecretNotFoundException.class, () -> secretService.getSecret(validKey, null));
    }

    @Test
    void getSecret_InsufficientShardsThrows() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.listVersions(validKey)).thenReturn(List.of(1L));

        when(clusterConfig.getTotalNodes()).thenReturn(3);
        when(clusterConfig.getThresholdK()).thenReturn(2);

        when(secretPartRepository.findParts(validKey, 1L)).thenReturn(Collections.singletonList(createPart(1, 1L)));

        assertThrows(InsufficientShardsException.class, () -> secretService.getSecret(validKey, 1L));
    }

    @Test
    void getSecret_Success() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        SecretPart p = createPart(1, 2L);
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.of(p));

        when(clusterConfig.getTotalNodes()).thenReturn(3);
        when(clusterConfig.getThresholdK()).thenReturn(2);

        List<SecretPart> availableParts = Arrays.asList(createPart(1, 2L), createPart(2, 2L));
        when(secretPartRepository.findParts(validKey, 2L)).thenReturn(availableParts);
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("secretVal");

        String val = secretService.getSecret(validKey, null);
        assertEquals("secretVal", val);
    }

    // --- getAllVersions ---

    @Test
    void getAllVersions_NoVersionsThrows() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(Collections.emptyList());
        assertThrows(SecretNotFoundException.class, () -> secretService.getAllVersions(validKey));
    }

    @Test
    void getAllVersions_InsufficientShardsThrows() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(List.of(1L));
        when(clusterConfig.getTotalNodes()).thenReturn(3);
        when(clusterConfig.getThresholdK()).thenReturn(2);
        when(secretPartRepository.findParts(validKey, 1L)).thenReturn(Collections.singletonList(createPart(1, 1L)));

        assertThrows(InsufficientShardsException.class, () -> secretService.getAllVersions(validKey));
    }

    @Test
    void getAllVersions_Success() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L, 2L));
        when(clusterConfig.getTotalNodes()).thenReturn(1);
        when(clusterConfig.getThresholdK()).thenReturn(1);

        when(secretPartRepository.findParts(validKey, 1L)).thenReturn(Collections.singletonList(createPart(1, 1L)));
        when(secretPartRepository.findParts(validKey, 2L)).thenReturn(Collections.singletonList(createPart(1, 2L)));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("val1", "val2");

        Map<Long, String> res = secretService.getAllVersions(validKey);
        assertEquals(2, res.size());
        assertEquals("val1", res.get(1L));
        assertEquals("val2", res.get(2L));
    }

    // --- deleteSecret ---

    @Test
    void deleteSecret_NotFoundThrows() {
        when(secretPartRepository.exists(validKey)).thenReturn(false);
        assertThrows(SecretNotFoundException.class, () -> secretService.deleteSecret(validKey));
    }

    @Test
    void deleteSecret_Success() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        secretService.deleteSecret(validKey);
        verify(secretPartRepository, times(1)).deleteParts(validKey);
    }

    // --- threshold edge cases ---

    @Test
    void configNullReturnsOne() {
        // We recreate service with null config to test edge cases in resolveThreshold / resolveTotalParts
        SecretService sService = new SecretService(secretPartRepository, clusterManager, secretSharingService, secretReconstructionService, null);
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        List<SecretPart> splits = Collections.singletonList(createPart(1, 0));
        when(secretSharingService.split(validKey, "val", 1, 1)).thenReturn(splits);

        SecretVersion sv = sService.storeSecret(validKey, "val");
        assertNotNull(sv);
    }

    @Test
    void configInvalidReturnsAdjusted() {
        when(clusterConfig.getTotalNodes()).thenReturn(-1);
        when(clusterConfig.getThresholdK()).thenReturn(-5);
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        List<SecretPart> splits = Collections.singletonList(createPart(1, 0));
        // Expect total 1, threshold 1 if config is invalid
        when(secretSharingService.split(validKey, "val", 1, 1)).thenReturn(splits);

        SecretVersion sv = secretService.storeSecret(validKey, "val");
        assertNotNull(sv);
    }

    @Test
    void thresholdGreaterThanTotalAdjusts() {
        when(clusterConfig.getTotalNodes()).thenReturn(2);
        when(clusterConfig.getThresholdK()).thenReturn(5); // greater than 2
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        List<SecretPart> splits = Arrays.asList(createPart(1, 0), createPart(2, 0));
        // Should use threshold 2
        when(secretSharingService.split(validKey, "val", 2, 2)).thenReturn(splits);

        SecretVersion sv = secretService.storeSecret(validKey, "val");
        assertNotNull(sv);
    }
}
