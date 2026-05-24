package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent by the originator node to all peers during the
 * <b>prepare</b> phase of a distributed create (POST).
 * <p>
 * Each peer buffers the shard in {@link edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer}
 * and responds with an ACK. The originator collects ACKs and, upon reaching
 * quorum, broadcasts a commit via Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostPrepareRequest {
    /** Node ID of the originator that initiated the create. */
    private String originatorNodeId;

    /** UUID correlating prepare → commit for this create operation. */
    private UUID operationId;

    /** The shard payload to be buffered on the receiving peer. */
    private SecretPartMessage secretPartMessage;
}
