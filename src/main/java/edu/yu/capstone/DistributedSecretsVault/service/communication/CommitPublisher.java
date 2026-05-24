package edu.yu.capstone.DistributedSecretsVault.service.communication;

import java.util.concurrent.TimeUnit;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.ServiceUnavailableException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes {@link CommitMessage}s to the Kafka commit topic.
 * <p>
 * After the originator node collects enough prepare ACKs from peers,
 * it calls {@link #broadcastCommit(CommitMessage)} to notify all nodes
 * (including itself) to finalize the buffered action. The send is
 * synchronous with a configurable timeout to ensure delivery is confirmed
 * before the caller returns.
 *
 * @see CommitListener
 * @see CommitDispatcher
 */
@Service
public class CommitPublisher {
    /** Maximum time to wait for Kafka send confirmation. */
    private static final long PUBLISH_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Constructs the publisher with a Spring Kafka template.
     *
     * @param kafkaTemplate template for sending Kafka messages
     */
    public CommitPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Synchronously publishes a commit message to the Kafka commit topic.
     * <p>
     * The message is keyed by the operation ID to ensure ordering for the
     * same operation. Blocks up to {@link #PUBLISH_TIMEOUT_SECONDS} seconds
     * for broker acknowledgment.
     *
     * @param message the commit message to broadcast
     * @throws IllegalArgumentException    if the message or operation ID is null
     * @throws ServiceUnavailableException if publishing fails or is interrupted
     */
    public void broadcastCommit(CommitMessage message) {
        if (message == null || message.getOperationId() == null) {
            throw new IllegalArgumentException("Commit message and operation ID are required");
        }
        try {
            kafkaTemplate.send(KafkaConfig.COMMIT_TOPIC, message.getOperationId().toString(), message)
                    .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(
                    "Kafka commit publish interrupted for operationId=" + message.getOperationId());
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Kafka commit publish failed for operationId=" + message.getOperationId());
        }
    }
}
