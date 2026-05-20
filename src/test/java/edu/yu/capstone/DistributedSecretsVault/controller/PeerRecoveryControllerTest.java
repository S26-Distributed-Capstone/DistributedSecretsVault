package edu.yu.capstone.DistributedSecretsVault.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.NodeStateResponse;
import edu.yu.capstone.DistributedSecretsVault.dto.recovery.StateSummary;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PeerRecoveryControllerTest {

    @Mock
    private SecretPartRepository secretPartRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void exportNodeStateReturnsAllKnownVersions() {
        when(redisTemplate.keys("*:*" )).thenReturn(Set.of("alice:db-password"));
        when(secretPartRepository.listVersions(new SecretKey("alice", "db-password"))).thenReturn(List.of(1L, 2L));

        PeerRecoveryController controller = new PeerRecoveryController(secretPartRepository, redisTemplate);
        ResponseEntity<NodeStateResponse> response = controller.exportNodeState();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getNodeState().size());
        assertTrue(response.getBody().getNodeState().contains(new StateSummary("alice", "db-password", 1L, "local")));
        assertTrue(response.getBody().getNodeState().contains(new StateSummary("alice", "db-password", 2L, "local")));
        verify(secretPartRepository).listVersions(new SecretKey("alice", "db-password"));
    }

    @Test
    void getShardReturnsStoredShard() {
        SecretPart part = new SecretPart(new SecretKey("alice", "db-password"), 1L, 1, new byte[] { 9 });
        when(secretPartRepository.findPart(new SecretKey("alice", "db-password"), 1L)).thenReturn(java.util.Optional.of(part));

        PeerRecoveryController controller = new PeerRecoveryController(secretPartRepository, redisTemplate);
        ResponseEntity<SecretPart> response = controller.getShard("alice", "db-password", 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(part, response.getBody());
    }
}
