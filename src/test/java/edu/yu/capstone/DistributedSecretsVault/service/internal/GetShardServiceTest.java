package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.when;

public class GetShardServiceTest {

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private GetShardService getShardService;

    private SecretKey validKey;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validKey = new SecretKey("user1", "secret1");
    }

    @Test
    void testGetVersion_WithVersion_Exists() {
        SecretPart part = new SecretPart();
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part));

        ResponseEntity<SecretPart> response = getShardService.getVersion("user1", "secret1", 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(part, response.getBody());
    }

    @Test
    void testGetVersion_WithoutVersion_Exists() {
        SecretPart part = new SecretPart();
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.of(part));

        ResponseEntity<SecretPart> response = getShardService.getVersion("user1", "secret1", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(part, response.getBody());
    }

    @Test
    void testGetVersion_KeyDoesNotExist() {
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        assertThrows(SecretNotFoundException.class, () -> getShardService.getVersion("user1", "secret1", 1L));
    }

    @Test
    void testGetVersion_PartDoesNotExist() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class, () -> getShardService.getVersion("user1", "secret1", 1L));
    }

    @Test
    void testGetVersion_InvalidUser() {
        assertThrows(IllegalArgumentException.class, () -> getShardService.getVersion(null, "secret1", 1L));
        assertThrows(IllegalArgumentException.class, () -> getShardService.getVersion("   ", "secret1", 1L));
    }

    @Test
    void testGetVersion_InvalidSecretName() {
        assertThrows(IllegalArgumentException.class, () -> getShardService.getVersion("user1", null, 1L));
        assertThrows(IllegalArgumentException.class, () -> getShardService.getVersion("user1", "   ", 1L));
    }

    @Test
    void testGetAllVersions_Valid() {
        SecretPart part1 = new SecretPart();
        SecretPart part2 = new SecretPart();
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L, 2L));
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part1));
        when(secretPartRepository.findPart(validKey, 2L)).thenReturn(Optional.of(part2));

        ResponseEntity<Map<Long, SecretPart>> response = getShardService.getAllVersions("user1", "secret1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<Long, SecretPart> results = response.getBody();
        assertEquals(2, results.size());
        assertEquals(part1, results.get(1L));
        assertEquals(part2, results.get(2L));
    }

    @Test
    void testGetAllVersions_NoVersionsFound() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(List.of());

        assertThrows(SecretNotFoundException.class, () -> getShardService.getAllVersions("user1", "secret1"));
    }

    @Test
    void testGetAllVersions_PartMissingForVersion() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L));
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class, () -> getShardService.getAllVersions("user1", "secret1"));
    }

    @Test
    void testGetAllVersions_InvalidUser() {
        assertThrows(IllegalArgumentException.class, () -> getShardService.getAllVersions(null, "secret1"));
        assertThrows(IllegalArgumentException.class, () -> getShardService.getAllVersions("   ", "secret1"));
    }

    @Test
    void testGetAllVersions_InvalidSecretName() {
        assertThrows(IllegalArgumentException.class, () -> getShardService.getAllVersions("user1", null));
        assertThrows(IllegalArgumentException.class, () -> getShardService.getAllVersions("user1", "   "));
    }
}
