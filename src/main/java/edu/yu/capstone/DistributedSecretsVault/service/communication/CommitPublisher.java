package edu.yu.capstone.DistributedSecretsVault.service.communication;

import java.util.concurrent.TimeUnit;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.ServiceUnavailableException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommitPublisher {
    private static final long PUBLISH_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CommitPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

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
