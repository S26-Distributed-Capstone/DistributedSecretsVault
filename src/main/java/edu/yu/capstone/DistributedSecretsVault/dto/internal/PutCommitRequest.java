package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body dispatched to each node during the <b>commit</b> phase
 * of a distributed update (PUT).
 * <p>
 * Upon receiving this, each node applies the updated shard it buffered during
 * the prepare phase and removes the pending entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PutCommitRequest {
    /** UUID matching the corresponding {@link PutPrepareRequest#getOperationId()}. */
    private UUID operationId;

    /** The secret key identifying which secret to update. */
    private SecretKey secretKey;
}
