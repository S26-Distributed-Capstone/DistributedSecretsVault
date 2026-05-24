package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent by the originator node to peers during the
 * <b>prepare</b> phase of a read-repair operation.
 * <p>
 * Read-repair re-distributes shards to nodes that are missing them,
 * bringing the cluster back to full redundancy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepairPrepareRequest {
    /** Node ID of the originator that initiated the repair. */
    private String originatorNodeId;

    /** UUID correlating prepare → commit for this repair operation. */
    private UUID operationId;

    /** The shard payload to be buffered on the receiving peer. */
    private SecretPartMessage secretPartMessage;
}
