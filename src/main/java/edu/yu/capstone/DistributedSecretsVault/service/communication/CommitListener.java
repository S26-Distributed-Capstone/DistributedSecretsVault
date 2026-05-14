package edu.yu.capstone.DistributedSecretsVault.service.communication;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class CommitListener {

    private static final Logger logger = LoggerFactory.getLogger(CommitListener.class);

    @KafkaListener(topics = KafkaConfig.COMMIT_TOPIC)
    public void onCommitMessage(CommitMessage message) {
        logger.info("Received commit message for transaction {}: {}", message.getTransactionId(), message);
        // TODO: Update local node state or delegate to a commit handler
    }
}
