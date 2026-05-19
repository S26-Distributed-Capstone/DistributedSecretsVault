package edu.yu.capstone.DistributedSecretsVault.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.service.internal.ActionType;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

/**
 * Thread-safety and concurrency tests.
 * <p>
 * These tests exercise the {@link PendingActionsBuffer} under concurrent load
 * to verify that buffering, committing, and discarding are thread-safe.
 * <p>
 * For the higher-level services ({@code InternalPostService}, etc.), true
 * concurrency is tested via the integration shell scripts against a real
 * multi-node cluster.
 */
@Tag("unit")
public class ConcurrentRequestTest {

    private PendingActionsBuffer buffer;

    @BeforeEach
    void setUp() {
        ClusterConfig config = new ClusterConfig();
        config.setLockTimeoutMillis(30_000L);
        buffer = new PendingActionsBuffer(config);
    }

    // ── Concurrent buffer + commit on different keys ────────────────────

    @Test
    void testConcurrentBufferAndCommitDifferentKeys() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    SecretKey key = new SecretKey("user" + idx, "secret" + idx);
                    UUID opId = UUID.randomUUID();

                    buffer.bufferAction(opId, key, ActionType.POST);
                    assertTrue(buffer.contains(opId));
                    assertTrue(buffer.containsKey(key));

                    PendingAction committed = buffer.commitAndRemove(opId);
                    assertNotNull(committed);
                    assertEquals(opId, committed.operationId());

                    assertFalse(buffer.contains(opId));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Thread " + idx + " threw: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(threadCount, successCount.get());
    }

    // ── Concurrent buffer + commit on SAME key ──────────────────────────

    @Test
    void testConcurrentBufferSameKeyCascadeOnCommit() throws InterruptedException {
        int threadCount = 10;
        SecretKey sharedKey = new SecretKey("user1", "shared-secret");
        List<UUID> operationIds = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allBuffered = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Phase 1: all threads buffer an action for the same key
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UUID opId = UUID.randomUUID();
                    buffer.bufferAction(opId, sharedKey, ActionType.PUT);
                    operationIds.add(opId);
                } catch (Exception e) {
                    fail("Buffering threw: " + e.getMessage());
                } finally {
                    allBuffered.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(allBuffered.await(5, TimeUnit.SECONDS));

        // All should be buffered
        for (UUID opId : operationIds) {
            assertTrue(buffer.contains(opId), "Expected opId to be buffered: " + opId);
        }
        assertTrue(buffer.containsKey(sharedKey));

        // Phase 2: committing ONE should cascade-remove ALL others for the same key
        UUID winnerId = operationIds.get(0);
        PendingAction committed = buffer.commitAndRemove(winnerId);
        assertNotNull(committed);
        assertEquals(winnerId, committed.operationId());

        // All operations for sharedKey should be gone
        for (UUID opId : operationIds) {
            assertFalse(buffer.contains(opId), "Expected opId to be evicted: " + opId);
        }
        assertFalse(buffer.containsKey(sharedKey));

        executor.shutdownNow();
    }

    // ── Concurrent discard ──────────────────────────────────────────────

    @Test
    void testConcurrentDiscard() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger discardedCount = new AtomicInteger(0);

        List<UUID> opIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID opId = UUID.randomUUID();
            opIds.add(opId);
            buffer.bufferAction(opId, new SecretKey("user" + i, "secret" + i), ActionType.DELETE);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    PendingAction discarded = buffer.discard(opIds.get(idx));
                    if (discarded != null) {
                        discardedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("Thread " + idx + " threw: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(threadCount, discardedCount.get());

        // All should be gone
        for (UUID opId : opIds) {
            assertFalse(buffer.contains(opId));
        }
    }

    // ── Race: buffer + immediate commit from different threads ──────────

    @Test
    void testRaceBufferAndCommitFromDifferentThreads() throws InterruptedException {
        int iterations = 50;
        CountDownLatch doneLatch = new CountDownLatch(iterations);
        CopyOnWriteArrayList<Boolean> results = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < iterations; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    SecretKey key = new SecretKey("user", "secret-" + idx);
                    UUID opId = UUID.randomUUID();
                    buffer.bufferAction(opId, key, ActionType.POST,
                            new SecretPart(key, 1L, 1, new byte[]{1}));

                    // Immediately commit from the same thread (simulates fast commit)
                    PendingAction committed = buffer.commitAndRemove(opId);
                    results.add(committed != null);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();

        // Every buffer+commit pair should succeed
        assertEquals(iterations, results.size());
        assertTrue(results.stream().allMatch(Boolean::booleanValue));
    }

    // ── Concurrent eviction + buffer ────────────────────────────────────

    @Test
    void testConcurrentEvictionAndBuffering() throws InterruptedException {
        ClusterConfig shortConfig = new ClusterConfig();
        shortConfig.setLockTimeoutMillis(50L);
        PendingActionsBuffer shortBuffer = new PendingActionsBuffer(shortConfig);

        // Buffer some entries that will expire
        for (int i = 0; i < 10; i++) {
            shortBuffer.bufferAction(UUID.randomUUID(),
                    new SecretKey("old-user", "old-" + i), ActionType.DELETE);
        }

        Thread.sleep(100); // Let them expire

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: evict expired
        Thread evictor = new Thread(() -> {
            try {
                startLatch.await();
                shortBuffer.evictExpired();
            } catch (Exception e) {
                fail("Evictor threw: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: buffer new entries concurrently
        List<UUID> newOps = new ArrayList<>();
        Thread bufferer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    UUID opId = UUID.randomUUID();
                    newOps.add(opId);
                    shortBuffer.bufferAction(opId,
                            new SecretKey("new-user", "new-" + i), ActionType.POST);
                }
            } catch (Exception e) {
                fail("Bufferer threw: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        evictor.start();
        bufferer.start();
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        // New entries should still be present
        for (UUID opId : newOps) {
            assertTrue(shortBuffer.contains(opId),
                    "New entry should survive concurrent eviction: " + opId);
        }
    }
}
