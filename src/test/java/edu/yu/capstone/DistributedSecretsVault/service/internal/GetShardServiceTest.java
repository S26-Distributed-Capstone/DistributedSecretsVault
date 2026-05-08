package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class GetShardServiceTest {

    @Mock
    private ShardService shardService;

    @InjectMocks
    private GetShardService getShardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetVersion_Valid() {
        SecretPart mockPart = new SecretPart();
        when(shardService.getShard(any(SecretKey.class), eq(1L))).thenReturn(mockPart);

        ResponseEntity<SecretPart> response = getShardService.getVersion("user1", "secret1", 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockPart, response.getBody());
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
        Map<Long, SecretPart> mockMap = new HashMap<>();
        when(shardService.getAllVersions(any(SecretKey.class))).thenReturn(mockMap);

        ResponseEntity<Map<Long, SecretPart>> response = getShardService.getAllVersions("user1", "secret1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockMap, response.getBody());
    }
}
