package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent by the originator node to all peers during the
 * <b>prepare</b> phase of a distributed delete.
 * <p>
 * Each peer buffers this request and returns an ACK. The originator
 * collects ACKs and, upon reaching the threshold, proceeds to the
 * commit phase.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeletePrepareRequest {
    /** Node ID of the originator that initiated the delete. */
    private String originatorNodeId;

    /** UUID correlating prepare → commit for this delete operation. */
    private UUID operationId;

    /** The secret key identifying which secret to delete. */
    private SecretKey secretKey;
}
