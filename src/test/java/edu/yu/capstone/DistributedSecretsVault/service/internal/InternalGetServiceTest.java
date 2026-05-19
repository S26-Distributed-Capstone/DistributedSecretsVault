package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartResponse;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartsResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretReconstructionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InternalGetServiceTest {

    @Mock
    private SecretPartRepository secretPartRepository;

    @Mock
    private SecretReconstructionService secretReconstructionService;

    @Mock
    private NodeClient nodeClient;

    private ClusterConfig clusterConfig;
    private InternalGetService internalGetService;

    private SecretKey validKey;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clusterConfig = new ClusterConfig();
        clusterConfig.setTotalNodes(3);
        clusterConfig.setThresholdK(2);
        internalGetService = new InternalGetService(secretPartRepository, secretReconstructionService,
                nodeClient, clusterConfig);
        lenient().when(nodeClient.resolvePeerUrls()).thenReturn(List.of());
        validKey = new SecretKey("user1", "secret1");
    }

    @Test
    void testGetAcrossClusterLatestVersion() {
        SecretPart part = new SecretPart(validKey, 2L, 1, new byte[] { 1, 2 });
        SecretPart peerPart = new SecretPart(validKey, 2L, 2, new byte[] { 3, 4 });
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.of(part));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.fetchSecretPart("http://peer1:8080", validKey, null))
                .thenReturn(SecretPartResponse.found("http://peer1:8080", peerPart));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("reconstructed");

        String result = internalGetService.getAcrossCluster(validKey, null);

        assertEquals("reconstructed", result);
        verify(secretReconstructionService).reconstruct(argThat(parts -> parts.size() == 2));
    }

    @Test
    void testGetAcrossClusterSpecificVersion() {
        SecretPart part = new SecretPart(validKey, 1L, 1, new byte[] { 1 });
        SecretPart peerPart = new SecretPart(validKey, 1L, 2, new byte[] { 2 });
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.fetchSecretPart("http://peer1:8080", validKey, 1L))
                .thenReturn(SecretPartResponse.found("http://peer1:8080", peerPart));
        when(secretReconstructionService.reconstruct(anyList())).thenReturn("v1-secret");

        String result = internalGetService.getAcrossCluster(validKey, 1L);

        assertEquals("v1-secret", result);
    }

    @Test
    void testGetAcrossClusterRejectsNonExistentKey() {
        assertThrows(SecretNotFoundException.class,
                () -> internalGetService.getAcrossCluster(validKey, null));
    }

    @Test
    void testGetAcrossClusterRejectsNonExistentVersion() {
        when(secretPartRepository.findPart(validKey, 99L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class,
                () -> internalGetService.getAcrossCluster(validKey, 99L));
    }

    @Test
    void testGetAcrossClusterThrowsWhenInsufficientShardsFound() {
        when(secretPartRepository.findLatest(validKey))
                .thenReturn(Optional.of(new SecretPart(validKey, 1L, 1, new byte[] { 1 })));

        assertThrows(InsufficientShardsException.class,
                () -> internalGetService.getAcrossCluster(validKey, null));
    }

    @Test
    void testGetAllVersionsAcrossClusterReturnsSortedMap() {
        SecretPart part1 = new SecretPart(validKey, 1L, 1, new byte[] { 1 });
        SecretPart part2 = new SecretPart(validKey, 2L, 1, new byte[] { 2 });
        SecretPart peerPart1 = new SecretPart(validKey, 1L, 2, new byte[] { 3 });
        SecretPart peerPart2 = new SecretPart(validKey, 2L, 2, new byte[] { 4 });
        when(secretPartRepository.listVersions(validKey)).thenReturn(List.of(2L, 1L));
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part1));
        when(secretPartRepository.findPart(validKey, 2L)).thenReturn(Optional.of(part2));
        when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
        when(nodeClient.fetchAllSecretParts("http://peer1:8080", validKey))
                .thenReturn(SecretPartsResponse.found("http://peer1:8080", Map.of(
                        1L, peerPart1,
                        2L, peerPart2)));
        when(secretReconstructionService.reconstruct(anyList()))
                .thenReturn("v1-secret", "v2-secret");

        Map<Long, String> results = internalGetService.getAllVersionsAcrossCluster(validKey);

        assertEquals(2, results.size());
        assertEquals("v1-secret", results.get(1L));
        assertEquals("v2-secret", results.get(2L));
    }

    @Test
    void testGetAllVersionsAcrossClusterThrowsWhenEmpty() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(List.of());

        assertThrows(SecretNotFoundException.class,
                () -> internalGetService.getAllVersionsAcrossCluster(validKey));
    }

    @Test
    void testGetVersion_WithVersion_Exists() {
        SecretPart part = new SecretPart();
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part));

        ResponseEntity<SecretPart> response = internalGetService.getVersion("user1", "secret1", 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(part, response.getBody());
    }

    @Test
    void testGetVersion_WithoutVersion_Exists() {
        SecretPart part = new SecretPart();
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.of(part));

        ResponseEntity<SecretPart> response = internalGetService.getVersion("user1", "secret1", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(part, response.getBody());
    }

    @Test
    void testGetVersion_KeyDoesNotExist() {
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        assertThrows(SecretNotFoundException.class, () -> internalGetService.getVersion("user1", "secret1", 1L));
    }

    @Test
    void testGetVersion_PartDoesNotExist() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class, () -> internalGetService.getVersion("user1", "secret1", 1L));
    }

    @Test
    void testGetVersion_InvalidUser() {
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getVersion(null, "secret1", 1L));
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getVersion("   ", "secret1", 1L));
    }

    @Test
    void testGetVersion_InvalidSecretName() {
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getVersion("user1", null, 1L));
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getVersion("user1", "   ", 1L));
    }

    @Test
    void testGetAllVersions_Valid() {
        SecretPart part1 = new SecretPart();
        SecretPart part2 = new SecretPart();
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L, 2L));
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part1));
        when(secretPartRepository.findPart(validKey, 2L)).thenReturn(Optional.of(part2));

        ResponseEntity<Map<Long, SecretPart>> response = internalGetService.getAllVersions("user1", "secret1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<Long, SecretPart> results = response.getBody();
        assertEquals(2, results.size());
        assertEquals(part1, results.get(1L));
        assertEquals(part2, results.get(2L));
    }

    @Test
    void testGetAllVersions_NoVersionsFound() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(List.of());

        assertThrows(SecretNotFoundException.class, () -> internalGetService.getAllVersions("user1", "secret1"));
    }

    @Test
    void testGetAllVersions_PartMissingForVersion() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L));
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class, () -> internalGetService.getAllVersions("user1", "secret1"));
    }

    @Test
    void testGetAllVersions_InvalidUser() {
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getAllVersions(null, "secret1"));
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getAllVersions("   ", "secret1"));
    }

    @Test
    void testGetAllVersions_InvalidSecretName() {
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getAllVersions("user1", null));
        assertThrows(IllegalArgumentException.class, () -> internalGetService.getAllVersions("user1", "   "));
    }
}
