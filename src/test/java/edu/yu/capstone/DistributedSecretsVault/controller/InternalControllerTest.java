package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GetShardService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GiveShardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class InternalControllerTest {

    @Mock
    private GetShardService getShardService;

    @Mock
    private GiveShardService giveShardService;

    @InjectMocks
    private InternalController internalController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetShard_WithVersion() {
        SecretPart mockPart = new SecretPart();
        when(getShardService.getVersion("user1", "secret1", 1L))
                .thenReturn(ResponseEntity.ok(mockPart));

        ResponseEntity<SecretPart> response = internalController.getShard("secret1", "user1", 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockPart, response.getBody());
    }

    @Test
    void testGetShard_WithoutVersion() {
        SecretPart mockPart = new SecretPart();
        when(getShardService.getVersion("user1", "secret1", null))
                .thenReturn(ResponseEntity.ok(mockPart));

        ResponseEntity<SecretPart> response = internalController.getShard("secret1", "user1", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockPart, response.getBody());
    }

    @Test
    void testGetAllVersions() {
        Map<Long, SecretPart> mockVersions = new HashMap<>();
        mockVersions.put(1L, new SecretPart());
        mockVersions.put(2L, new SecretPart());

        when(getShardService.getAllVersions("user1", "secret1"))
                .thenReturn(ResponseEntity.ok(mockVersions));

        ResponseEntity<Map<Long, SecretPart>> response = internalController.getAllVersions("secret1", "user1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockVersions, response.getBody());
    }
}
