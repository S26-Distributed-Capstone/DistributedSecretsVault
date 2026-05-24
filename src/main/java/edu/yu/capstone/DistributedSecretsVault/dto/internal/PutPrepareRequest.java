package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent by the originator node to all peers during the
 * <b>prepare</b> phase of a distributed update (PUT).
 * <p>
 * Each peer verifies the secret exists, buffers the updated shard in
 * {@link edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer},
 * and responds with an ACK.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PutPrepareRequest {
    /** Node ID of the originator that initiated the update. */
    private String originatorNodeId;

    /** UUID correlating prepare → commit for this update operation. */
    private UUID operationId;

    /** The updated shard payload to be buffered on the receiving peer. */
    private SecretPartMessage secretPartMessage;
}
