package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.service.internal.ActionType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Kafka message payload broadcast during the commit phase of a distributed operation.
 * <p>
 * After the originator node collects enough prepare ACKs, it publishes a
 * {@code CommitMessage} to the {@link edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig#COMMIT_TOPIC}
 * topic. Every node (including the originator) consumes the message and
 * finalizes the buffered action via {@link edu.yu.capstone.DistributedSecretsVault.service.communication.CommitDispatcher}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessage {
    /** Unique identifier correlating this commit to its prepare phase. */
    private UUID operationId;

    /** The secret key targeted by this operation. */
    private SecretKey secretKey;

    /** The type of distributed operation (POST, PUT, DELETE, REPAIR). */
    private ActionType actionType;
}
