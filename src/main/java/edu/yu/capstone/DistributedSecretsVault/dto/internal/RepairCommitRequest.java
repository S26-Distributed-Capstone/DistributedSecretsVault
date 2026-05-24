package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body dispatched to each node during the <b>commit</b> phase
 * of a read-repair operation.
 * <p>
 * Upon receiving this, each node persists the repair shard it buffered during
 * the prepare phase and removes the pending entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepairCommitRequest {
    /** UUID matching the corresponding {@link RepairPrepareRequest#getOperationId()}. */
    private UUID operationId;

    /** The secret key identifying which secret is being repaired. */
    private SecretKey secretKey;
}
