package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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

    @Test
    void testBufferAndCommit() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();
        buffer.bufferAction(operationId, key, ActionType.DELETE);

        assertTrue(buffer.contains(operationId));
        assertTrue(buffer.containsKey(key));

        PendingAction committed = buffer.commitAndRemove(operationId);
        assertNotNull(committed);
        assertEquals(operationId, committed.operationId());
        assertEquals(key, committed.secretKey());
        assertEquals(ActionType.DELETE, committed.actionType());

        assertFalse(buffer.contains(operationId));
        assertFalse(buffer.containsKey(key));
    }

    @Test
    void testCommitAndRemoveReturnsNullForUnknownOperationId() {
        assertNull(buffer.commitAndRemove(UUID.randomUUID()));
    }

    @Test
    void testContainsReturnsFalseForUnknownOperationId() {
        assertFalse(buffer.contains(UUID.randomUUID()));
    }

    @Test
    void testCommitOnlyRemovesOnce() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();
        buffer.bufferAction(operationId, key, ActionType.DELETE);

        assertNotNull(buffer.commitAndRemove(operationId));
        assertNull(buffer.commitAndRemove(operationId));
    }

    @Test
    void testMultipleActionsForDifferentKeys() {
        SecretKey key1 = new SecretKey("user1", "secret1");
        SecretKey key2 = new SecretKey("user2", "secret2");
        SecretKey key3 = new SecretKey("user3", "secret3");
        UUID opA = UUID.randomUUID();
        UUID opB = UUID.randomUUID();
        UUID opC = UUID.randomUUID();

        buffer.bufferAction(opA, key1, ActionType.DELETE);
        buffer.bufferAction(opB, key2, ActionType.UPDATE);
        buffer.bufferAction(opC, key3, ActionType.DELETE);

        assertTrue(buffer.contains(opA));
        assertTrue(buffer.contains(opB));
        assertTrue(buffer.contains(opC));

        assertNotNull(buffer.commitAndRemove(opB));
        assertFalse(buffer.contains(opB));
        assertFalse(buffer.containsKey(key2));
        assertTrue(buffer.contains(opA));
        assertTrue(buffer.contains(opC));
    }

    @Test
    void testCommitCascadeRemovesOtherActionsForSameKey() {
        SecretKey sameKey = new SecretKey("user1", "secret1");
        UUID op1 = UUID.randomUUID();
        UUID op2 = UUID.randomUUID();
        UUID op3 = UUID.randomUUID();

        buffer.bufferAction(op1, sameKey, ActionType.DELETE);
        buffer.bufferAction(op2, sameKey, ActionType.UPDATE);
        buffer.bufferAction(op3, sameKey, ActionType.DELETE);

        assertTrue(buffer.contains(op1));
        assertTrue(buffer.contains(op2));
        assertTrue(buffer.contains(op3));

        PendingAction committed = buffer.commitAndRemove(op2);
        assertNotNull(committed);
        assertEquals(op2, committed.operationId());

        assertFalse(buffer.contains(op1));
        assertFalse(buffer.contains(op2));
        assertFalse(buffer.contains(op3));
        assertFalse(buffer.containsKey(sameKey));
    }

    @Test
    void testCascadeDoesNotAffectDifferentKeys() {
        SecretKey key1 = new SecretKey("user1", "secret1");
        SecretKey key2 = new SecretKey("user2", "secret2");
        UUID opA = UUID.randomUUID();
        UUID opB = UUID.randomUUID();
        UUID opC = UUID.randomUUID();

        buffer.bufferAction(opA, key1, ActionType.DELETE);
        buffer.bufferAction(opB, key1, ActionType.DELETE);
        buffer.bufferAction(opC, key2, ActionType.DELETE);

        buffer.commitAndRemove(opA);

        assertFalse(buffer.contains(opA));
        assertFalse(buffer.contains(opB));
        assertTrue(buffer.contains(opC));
        assertTrue(buffer.containsKey(key2));
    }

    @Test
    void testBufferDifferentActionTypes() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID opDel = UUID.randomUUID();
        UUID opUpd = UUID.randomUUID();

        buffer.bufferAction(opDel, key, ActionType.DELETE);
        buffer.bufferAction(opUpd, key, ActionType.UPDATE);

        PendingAction del = buffer.commitAndRemove(opDel);
        assertNotNull(del);
        assertEquals(ActionType.DELETE, del.actionType());
        assertFalse(buffer.contains(opUpd));
    }

    @Test
    void testEvictExpiredRemovesOldEntries() throws InterruptedException {
        ClusterConfig shortTimeoutConfig = new ClusterConfig();
        shortTimeoutConfig.setLockTimeoutMillis(50L);
        PendingActionsBuffer shortBuffer = new PendingActionsBuffer(shortTimeoutConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();
        shortBuffer.bufferAction(operationId, key, ActionType.DELETE);
        assertTrue(shortBuffer.contains(operationId));

        Thread.sleep(100);
        shortBuffer.evictExpired();

        assertFalse(shortBuffer.contains(operationId));
        assertFalse(shortBuffer.containsKey(key));
    }

    @Test
    void testEvictExpiredKeepsRecentEntries() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();
        buffer.bufferAction(operationId, key, ActionType.DELETE);

        buffer.evictExpired();

        assertTrue(buffer.contains(operationId));
    }

    @Test
    void testDefaultTimeoutWhenConfigIsZero() {
        ClusterConfig zeroConfig = new ClusterConfig();
        zeroConfig.setLockTimeoutMillis(0);
        PendingActionsBuffer zeroBuffer = new PendingActionsBuffer(zeroConfig);

        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();
        zeroBuffer.bufferAction(operationId, key, ActionType.DELETE);
        assertTrue(zeroBuffer.contains(operationId));
    }

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
        shortBuffer.bufferAction(UUID.randomUUID(), key, ActionType.DELETE);
        shortBuffer.bufferAction(UUID.randomUUID(), key, ActionType.UPDATE);
        assertTrue(shortBuffer.containsKey(key));

        Thread.sleep(100);
        shortBuffer.evictExpired();

        assertFalse(shortBuffer.containsKey(key));
    }
}
