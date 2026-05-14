package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class PendingActionsBufferTest {

    private PendingActionsBuffer buffer;

    @BeforeEach
    void setUp() {
        ClusterConfig config = new ClusterConfig();
        config.setLockTimeoutMillis(5000L);
        buffer = new PendingActionsBuffer(config);
    }

    // ── Basic buffer / retrieve ────────────────────────────────────────

    @Test
    void testBufferAndCommit() {
        SecretKey key = new SecretKey("user1", "secret1");
        buffer.bufferAction("op-1", key, ActionType.DELETE);

        assertTrue(buffer.contains("op-1"));
        assertTrue(buffer.containsKey(key));

        PendingAction committed = buffer.commitAndRemove("op-1");
        assertNotNull(committed);
        assertEquals("op-1", committed.operationId());
        assertEquals(key, committed.secretKey());
        assertEquals(ActionType.DELETE, committed.actionType());

        assertFalse(buffer.contains("op-1"));
        assertFalse(buffer.containsKey(key));
    }

    @Test
    void testCommitAndRemoveReturnsNullForUnknownOperationId() {
        assertNull(buffer.commitAndRemove("nonexistent"));
    }

    @Test
    void testContainsReturnsFalseForUnknownOperationId() {
        assertFalse(buffer.contains("nonexistent"));
    }

    @Test
    void testCommitOnlyRemovesOnce() {
        SecretKey key = new SecretKey("user1", "secret1");
        buffer.bufferAction("op-2", key, ActionType.DELETE);

        assertNotNull(buffer.commitAndRemove("op-2"));
        assertNull(buffer.commitAndRemove("op-2"));
    }

    // ── Multiple actions for different keys ─────────────────────────────

    @Test
    void testMultipleActionsForDifferentKeys() {
        SecretKey key1 = new SecretKey("user1", "secret1");
        SecretKey key2 = new SecretKey("user2", "secret2");
        SecretKey key3 = new SecretKey("user3", "secret3");

        buffer.bufferAction("op-a", key1, ActionType.DELETE);
        buffer.bufferAction("op-b", key2, ActionType.UPDATE);
        buffer.bufferAction("op-c", key3, ActionType.DELETE);

        assertTrue(buffer.contains("op-a"));
        assertTrue(buffer.contains("op-b"));
        assertTrue(buffer.contains("op-c"));

        assertNotNull(buffer.commitAndRemove("op-b"));
        assertFalse(buffer.contains("op-b"));
        assertFalse(buffer.containsKey(key2));

        // Other keys unaffected
        assertTrue(buffer.contains("op-a"));
        assertTrue(buffer.contains("op-c"));
    }

    // ── Cascade: commit one action removes all others for same key ──────

    @Test
    void testCommitCascadeRemovesOtherActionsForSameKey() {
        SecretKey sameKey = new SecretKey("user1", "secret1");

        buffer.bufferAction("op-1", sameKey, ActionType.DELETE);
        buffer.bufferAction("op-2", sameKey, ActionType.UPDATE);
        buffer.bufferAction("op-3", sameKey, ActionType.DELETE);

        assertTrue(buffer.contains("op-1"));
        assertTrue(buffer.contains("op-2"));
        assertTrue(buffer.contains("op-3"));

        // Commit op-2 → should cascade remove op-1 and op-3
        PendingAction committed = buffer.commitAndRemove("op-2");
        assertNotNull(committed);
        assertEquals("op-2", committed.operationId());

        assertFalse(buffer.contains("op-1"));
        assertFalse(buffer.contains("op-2"));
        assertFalse(buffer.contains("op-3"));
        assertFalse(buffer.containsKey(sameKey));
    }

    @Test
    void testCascadeDoesNotAffectDifferentKeys() {
        SecretKey key1 = new SecretKey("user1", "secret1");
        SecretKey key2 = new SecretKey("user2", "secret2");

        buffer.bufferAction("op-a", key1, ActionType.DELETE);
        buffer.bufferAction("op-b", key1, ActionType.DELETE);
        buffer.bufferAction("op-c", key2, ActionType.DELETE);

        // Commit op-a → cascades op-b (same key1), but op-c (key2) stays
        buffer.commitAndRemove("op-a");

        assertFalse(buffer.contains("op-a"));
        assertFalse(buffer.contains("op-b"));
        assertTrue(buffer.contains("op-c"));
        assertTrue(buffer.containsKey(key2));
    }

    // ── Different action types ──────────────────────────────────────────

    @Test
    void testBufferDifferentActionTypes() {
        SecretKey key = new SecretKey("user1", "secret1");

        buffer.bufferAction("op-del", key, ActionType.DELETE);
        buffer.bufferAction("op-upd", key, ActionType.UPDATE);

        PendingAction del = buffer.commitAndRemove("op-del");
        assertNotNull(del);
        assertEquals(ActionType.DELETE, del.actionType());

        // op-upd should have been cascade removed
        assertFalse(buffer.contains("op-upd"));
    }

    // ── Eviction ────────────────────────────────────────────────────────

    @Test
    void testEvictExpiredRemovesOldEntries() throws InterruptedException {
        ClusterConfig shortTimeoutConfig = new ClusterConfig();
        shortTimeoutConfig.setLockTimeoutMillis(50L);
        PendingActionsBuffer shortBuffer = new PendingActionsBuffer(shortTimeoutConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        shortBuffer.bufferAction("op-expire", key, ActionType.DELETE);
        assertTrue(shortBuffer.contains("op-expire"));

        Thread.sleep(100);
        shortBuffer.evictExpired();

        assertFalse(shortBuffer.contains("op-expire"));
        assertFalse(shortBuffer.containsKey(key));
    }

    @Test
    void testEvictExpiredKeepsRecentEntries() {
        SecretKey key = new SecretKey("user1", "secret1");
        buffer.bufferAction("op-recent", key, ActionType.DELETE);

        buffer.evictExpired();

        assertTrue(buffer.contains("op-recent"));
    }

    @Test
    void testDefaultTimeoutWhenConfigIsZero() {
        ClusterConfig zeroConfig = new ClusterConfig();
        zeroConfig.setLockTimeoutMillis(0);
        PendingActionsBuffer zeroBuffer = new PendingActionsBuffer(zeroConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        zeroBuffer.bufferAction("op-zero", key, ActionType.DELETE);
        assertTrue(zeroBuffer.contains("op-zero"));
    }

    // ── containsKey ─────────────────────────────────────────────────────

    @Test
    void testContainsKeyReturnsFalseForUnknownKey() {
        assertFalse(buffer.containsKey(new SecretKey("unknown", "unknown")));
    }

    @Test
    void testContainsKeyReturnsFalseAfterAllActionsEvicted() throws InterruptedException {
        ClusterConfig shortTimeoutConfig = new ClusterConfig();
        shortTimeoutConfig.setLockTimeoutMillis(50L);
        PendingActionsBuffer shortBuffer = new PendingActionsBuffer(shortTimeoutConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        shortBuffer.bufferAction("op-1", key, ActionType.DELETE);
        shortBuffer.bufferAction("op-2", key, ActionType.UPDATE);
        assertTrue(shortBuffer.containsKey(key));

        Thread.sleep(100);
        shortBuffer.evictExpired();

        assertFalse(shortBuffer.containsKey(key));
    }
}
