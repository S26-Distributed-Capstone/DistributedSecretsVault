package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent by the originator node to all peers during the
 * <b>commit</b> phase of a distributed delete.
 * <p>
 * Upon receiving this, each peer executes its buffered delete and
 * removes the pending entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCommitRequest {
    /** UUID matching the corresponding {@link DeletePrepareRequest#getOperationId()}. */
    private String operationId;

    /** The secret key identifying which secret to delete. */
    private SecretKey secretKey;
}
