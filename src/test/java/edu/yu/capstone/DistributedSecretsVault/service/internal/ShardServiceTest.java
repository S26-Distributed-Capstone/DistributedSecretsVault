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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class ShardServiceTest {

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private ShardService shardService;

    private SecretKey validKey;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validKey = new SecretKey("user1", "secret1");
    }

    @Test
    void testGetShard_WithVersion_Exists() {
        SecretPart part = new SecretPart();
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part));

        SecretPart result = shardService.getShard(validKey, 1L);

        assertEquals(part, result);
    }

    @Test
    void testGetShard_WithoutVersion_Exists() {
        SecretPart part = new SecretPart();
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findLatest(validKey)).thenReturn(Optional.of(part));

        SecretPart result = shardService.getShard(validKey, null);

        assertEquals(part, result);
    }

    @Test
    void testGetShard_KeyDoesNotExist() {
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        assertThrows(SecretNotFoundException.class, () -> shardService.getShard(validKey, 1L));
    }

    @Test
    void testGetShard_PartDoesNotExist() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class, () -> shardService.getShard(validKey, 1L));
    }

    @Test
    void testGetAllVersions_Valid() {
        SecretPart part1 = new SecretPart();
        SecretPart part2 = new SecretPart();
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L, 2L));
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.of(part1));
        when(secretPartRepository.findPart(validKey, 2L)).thenReturn(Optional.of(part2));

        Map<Long, SecretPart> results = shardService.getAllVersions(validKey);

        assertEquals(2, results.size());
        assertEquals(part1, results.get(1L));
        assertEquals(part2, results.get(2L));
    }

    @Test
    void testGetAllVersions_NoVersionsFound() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(List.of());

        assertThrows(SecretNotFoundException.class, () -> shardService.getAllVersions(validKey));
    }

    @Test
    void testGetAllVersions_PartMissingForVersion() {
        when(secretPartRepository.listVersions(validKey)).thenReturn(Arrays.asList(1L));
        when(secretPartRepository.findPart(validKey, 1L)).thenReturn(Optional.empty());

        assertThrows(SecretNotFoundException.class, () -> shardService.getAllVersions(validKey));
    }
}
