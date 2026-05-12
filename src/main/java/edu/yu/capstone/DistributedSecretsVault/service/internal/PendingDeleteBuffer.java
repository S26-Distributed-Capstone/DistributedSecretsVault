package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;

/**
 * Thread-safe in-memory buffer for pending delete operations on peer nodes.
 * <p>
 * When a peer receives a {@link DeletePrepareRequest}, the delete is buffered
 * here until a matching commit arrives. Entries that never receive a commit
 * are evicted after {@link ClusterConfig#getLockTimeoutMillis()}.
 * <p>
 * In the future, this buffer could be backed by Redis for durability across
 * node restarts.
 */
@Component
public class PendingDeleteBuffer {
    private static final Logger log = LoggerFactory.getLogger(PendingDeleteBuffer.class);

    private final Map<String, PendingDelete> pending = new ConcurrentHashMap<>();
    private final long evictionTimeoutMillis;

    public PendingDeleteBuffer(ClusterConfig clusterConfig) {
        long timeout = clusterConfig.getLockTimeoutMillis();
        this.evictionTimeoutMillis = timeout > 0 ? timeout : 30_000L;
    }

    /**
     * Buffer a delete operation received during the prepare phase.
     *
     * @param request the prepare request to buffer
     */
    public void bufferDelete(DeletePrepareRequest request) {
        PendingDelete entry = new PendingDelete(request, Instant.now());
        pending.put(request.getOperationId(), entry);
        log.debug("Buffered pending delete: operationId={}, secretKey={}",
                request.getOperationId(), request.getSecretKey());
    }

    /**
     * Retrieve and remove a buffered delete when the commit arrives.
     *
     * @param operationId the operation ID from the commit request
     * @return the original prepare request, or {@code null} if not found / already evicted
     */
    public DeletePrepareRequest getAndRemove(String operationId) {
        PendingDelete entry = pending.remove(operationId);
        if (entry == null) {
            log.warn("No pending delete found for operationId={}", operationId);
            return null;
        }
        log.debug("Removed pending delete: operationId={}", operationId);
        return entry.request();
    }

    /**
     * Check whether a delete is currently buffered for the given operation ID.
     */
    public boolean contains(String operationId) {
        return pending.containsKey(operationId);
    }

    /**
     * Periodically evict pending deletes that have exceeded the lock timeout.
     * Runs every 10 seconds.
     */
    @Scheduled(fixedRate = 10_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusMillis(evictionTimeoutMillis);
        Iterator<Map.Entry<String, PendingDelete>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingDelete> entry = it.next();
            if (entry.getValue().bufferedAt().isBefore(cutoff)) {
                it.remove();
                log.info("Evicted expired pending delete: operationId={}", entry.getKey());
            }
        }
    }

    /**
     * Internal record holding a buffered delete and its buffer timestamp.
     */
    record PendingDelete(DeletePrepareRequest request, Instant bufferedAt) {
    }
}
