package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class PendingDeleteBufferTest {

    private PendingDeleteBuffer buffer;

    @BeforeEach
    void setUp() {
        ClusterConfig config = new ClusterConfig();
        config.setLockTimeoutMillis(5000L);
        buffer = new PendingDeleteBuffer(config);
    }

    @Test
    void testBufferAndRetrieve() {
        DeletePrepareRequest request = createRequest("op-1", "user1", "secret1");

        buffer.bufferDelete(request);

        assertTrue(buffer.contains("op-1"));
        DeletePrepareRequest retrieved = buffer.getAndRemove("op-1");
        assertNotNull(retrieved);
        assertEquals("op-1", retrieved.getOperationId());
        assertFalse(buffer.contains("op-1"));
    }

    @Test
    void testGetAndRemoveReturnsNullForUnknownOperationId() {
        assertNull(buffer.getAndRemove("nonexistent"));
    }

    @Test
    void testContainsReturnsFalseForUnknownOperationId() {
        assertFalse(buffer.contains("nonexistent"));
    }

    @Test
    void testGetAndRemoveOnlyRemovesOnce() {
        DeletePrepareRequest request = createRequest("op-2", "user1", "secret1");
        buffer.bufferDelete(request);

        assertNotNull(buffer.getAndRemove("op-2"));
        assertNull(buffer.getAndRemove("op-2"));
    }

    @Test
    void testMultipleBufferedDeletes() {
        buffer.bufferDelete(createRequest("op-a", "user1", "secret1"));
        buffer.bufferDelete(createRequest("op-b", "user2", "secret2"));
        buffer.bufferDelete(createRequest("op-c", "user3", "secret3"));

        assertTrue(buffer.contains("op-a"));
        assertTrue(buffer.contains("op-b"));
        assertTrue(buffer.contains("op-c"));

        assertNotNull(buffer.getAndRemove("op-b"));
        assertFalse(buffer.contains("op-b"));
        assertTrue(buffer.contains("op-a"));
        assertTrue(buffer.contains("op-c"));
    }

    @Test
    void testEvictExpiredRemovesOldEntries() throws InterruptedException {
        // Use a very short timeout for testing
        ClusterConfig shortTimeoutConfig = new ClusterConfig();
        shortTimeoutConfig.setLockTimeoutMillis(50L);
        PendingDeleteBuffer shortBuffer = new PendingDeleteBuffer(shortTimeoutConfig);

        shortBuffer.bufferDelete(createRequest("op-expire", "user1", "secret1"));
        assertTrue(shortBuffer.contains("op-expire"));

        // Wait for the entry to expire
        Thread.sleep(100);
        shortBuffer.evictExpired();

        assertFalse(shortBuffer.contains("op-expire"));
    }

    @Test
    void testEvictExpiredKeepsRecentEntries() {
        buffer.bufferDelete(createRequest("op-recent", "user1", "secret1"));

        // Evict immediately — entry was just added, should survive
        buffer.evictExpired();

        assertTrue(buffer.contains("op-recent"));
    }

    @Test
    void testDefaultTimeoutWhenConfigIsZero() {
        ClusterConfig zeroConfig = new ClusterConfig();
        zeroConfig.setLockTimeoutMillis(0);
        PendingDeleteBuffer zeroBuffer = new PendingDeleteBuffer(zeroConfig);

        // Should not throw; falls back to 30s default
        zeroBuffer.bufferDelete(createRequest("op-zero", "user1", "secret1"));
        assertTrue(zeroBuffer.contains("op-zero"));
    }

    private DeletePrepareRequest createRequest(String operationId, String ownerId, String secretName) {
        SecretKey key = new SecretKey();
        key.setOwnerId(ownerId);
        key.setName(secretName);
        return new DeletePrepareRequest("originator-node", operationId, key);
    }
}
