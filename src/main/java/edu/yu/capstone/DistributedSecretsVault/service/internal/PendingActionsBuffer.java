package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;

/**
 * Thread-safe in-memory buffer for pending cluster operations on peer nodes.
 * <p>
 * During the prepare phase of any distributed operation (delete, update, etc.),
 * the action is buffered here until a matching commit arrives. Entries that
 * never receive a commit are evicted after
 * {@link ClusterConfig#getLockTimeoutMillis()}.
 * <p>
 * When a commit arrives for a given {@code operationId}, the buffer also
 * removes <b>all other pending actions for the same {@link SecretKey}</b>,
 * ensuring that stale or conflicting operations on the same secret are
 * automatically cleaned up.
 * <p>
 * In the future, this buffer could be backed by Redis for durability across
 * node restarts.
 */
@Component
public class PendingActionsBuffer {
    private static final Logger log = LoggerFactory.getLogger(PendingActionsBuffer.class);

    /** Primary index: operationId → PendingAction. */
    private final Map<String, PendingAction> byOperationId = new ConcurrentHashMap<>();

    /** Secondary index: SecretKey → set of operationIds affecting that key. */
    private final Map<SecretKey, Set<String>> bySecretKey = new ConcurrentHashMap<>();

    private final Map<SecretKey, KeyLock> locksBySecretKey = new ConcurrentHashMap<>();
    private final long evictionTimeoutMillis;

    public PendingActionsBuffer(ClusterConfig clusterConfig) {
        long timeout = clusterConfig.getLockTimeoutMillis();
        this.evictionTimeoutMillis = timeout > 0 ? timeout : 30_000L;
    }

    /**
     * Buffer an action received during the prepare phase.
     *
     * @param operationId unique ID correlating prepare → commit
     * @param secretKey   the key being affected by this action
     * @param actionType  the type of action being buffered
     */
    public void bufferAction(String operationId, SecretKey secretKey, ActionType actionType) {
        PendingAction entry = new PendingAction(operationId, secretKey, actionType, Instant.now());
        KeyLock keyLock = acquireLock(secretKey);
        try {
            byOperationId.put(operationId, entry);
            bySecretKey.computeIfAbsent(secretKey, k -> ConcurrentHashMap.newKeySet())
                    .add(operationId);
        } finally {
            releaseLock(secretKey, keyLock);
        }
        log.debug("Buffered pending action: operationId={}, secretKey={}, type={}",
                operationId, secretKey, actionType);
    }

    /**
     * Retrieve and remove a buffered action when the commit arrives.
     * <p>
     * In addition to removing the committed operation, this method also removes
     * <b>all other pending actions for the same {@link SecretKey}</b>. This
     * ensures that if multiple operations target the same secret, committing
     * one invalidates the rest.
     *
     * @param operationId the operation ID from the commit request
     * @return the pending action, or {@code null} if not found / already evicted
     */
    public PendingAction commitAndRemove(String operationId) {
        PendingAction candidate = byOperationId.get(operationId);
        if (candidate == null) {
            log.debug("No pending action found for operationId={}", operationId);
            return null;
        }

        KeyLock keyLock = acquireLock(candidate.secretKey());
        try {
            PendingAction committed = byOperationId.remove(operationId);
            if (committed == null) {
                log.debug("No pending action found for operationId={}", operationId);
                return null;
            }

            // Remove all other pending actions for the same secret key
            SecretKey key = committed.secretKey();
            Set<String> relatedOps = bySecretKey.remove(key);
            if (relatedOps != null) {
                for (String relatedOpId : relatedOps) {
                    if (!relatedOpId.equals(operationId)) {
                        PendingAction evicted = byOperationId.remove(relatedOpId);
                        if (evicted != null) {
                            log.debug("Evicted conflicting action: operationId={}, type={}, "
                                    + "superseded by committed operationId={}",
                                    relatedOpId, evicted.actionType(), operationId);
                        }
                    }
                }
            }

            log.debug("Committed and removed action: operationId={}, type={}",
                    operationId, committed.actionType());
            return committed;
        } finally {
            releaseLock(candidate.secretKey(), keyLock);
        }
    }

    /**
     * Check whether an action is currently buffered for the given operation ID.
     */
    public boolean contains(String operationId) {
        PendingAction action = byOperationId.get(operationId);
        if (action == null) {
            return false;
        }
        KeyLock keyLock = acquireLock(action.secretKey());
        try {
            return byOperationId.containsKey(operationId);
        } finally {
            releaseLock(action.secretKey(), keyLock);
        }
    }

    /**
     * Check whether any action is currently buffered for the given secret key.
     */
    public boolean containsKey(SecretKey secretKey) {
        KeyLock keyLock = acquireLock(secretKey);
        try {
            Set<String> ops = bySecretKey.get(secretKey);
            return ops != null && !ops.isEmpty();
        } finally {
            releaseLock(secretKey, keyLock);
        }
    }

    /**
     * Periodically evict pending actions that have exceeded the lock timeout.
     * Runs every 10 seconds.
     */
    @Scheduled(fixedRate = 10_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusMillis(evictionTimeoutMillis);
        List<PendingAction> candidates = new ArrayList<>();

        Iterator<Map.Entry<String, PendingAction>> it = byOperationId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingAction> entry = it.next();
            if (entry.getValue().bufferedAt().isBefore(cutoff)) {
                candidates.add(entry.getValue());
            }
        }

        for (PendingAction candidate : candidates) {
            KeyLock keyLock = acquireLock(candidate.secretKey());
            try {
                PendingAction current = byOperationId.get(candidate.operationId());
                if (current == null || !current.bufferedAt().isBefore(cutoff)) {
                    continue;
                }

                byOperationId.remove(current.operationId());
                removeFromSecretKeyIndex(current.secretKey(), current.operationId());

                log.debug("Evicted expired pending action: operationId={}, type={}",
                        current.operationId(), current.actionType());
            } finally {
                releaseLock(candidate.secretKey(), keyLock);
            }
        }
    }

    private void removeFromSecretKeyIndex(SecretKey key, String operationId) {
        Set<String> ops = bySecretKey.get(key);
        if (ops != null) {
            ops.remove(operationId);
            if (ops.isEmpty()) {
                bySecretKey.remove(key, ops);
            }
        }
    }

    private KeyLock acquireLock(SecretKey key) {
        KeyLock keyLock = locksBySecretKey.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return new KeyLock();
            }
            existing.retain();
            return existing;
        });
        keyLock.lock();
        return keyLock;
    }

    private void releaseLock(SecretKey key, KeyLock keyLock) {
        try {
            keyLock.unlock();
        } finally {
            locksBySecretKey.computeIfPresent(key, (ignored, existing) -> {
                if (existing != keyLock) {
                    return existing;
                }
                return existing.release() == 0 ? null : existing;
            });
        }
    }

    private static final class KeyLock {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger references = new AtomicInteger(1);

        void retain() {
            references.incrementAndGet();
        }

        int release() {
            return references.decrementAndGet();
        }

        void lock() {
            lock.lock();
        }

        void unlock() {
            lock.unlock();
        }
    }

    /**
     * A buffered action awaiting commit.
     *
     * @param operationId unique operation identifier
     * @param secretKey   the key being affected
     * @param actionType  the type of action
     * @param bufferedAt  when this action was buffered
     */
    public record PendingAction(
            String operationId,
            SecretKey secretKey,
            ActionType actionType,
            Instant bufferedAt) {
    }
}
