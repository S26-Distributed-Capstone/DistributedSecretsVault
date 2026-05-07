package edu.yu.capstone.DistributedSecretsVault.service.communication;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommitPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CommitPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void broadcastCommit(CommitMessage message) {
        kafkaTemplate.send(KafkaConfig.COMMIT_TOPIC, message.getTransactionId(), message);
    }
}
