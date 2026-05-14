package edu.yu.capstone.DistributedSecretsVault.service.communication;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class CommitListener {
    private final CommitDispatcher commitDispatcher;

    public CommitListener(CommitDispatcher commitDispatcher) {
        this.commitDispatcher = commitDispatcher;
    }

    @KafkaListener(topics = KafkaConfig.COMMIT_TOPIC)
    public void onCommitMessage(CommitMessage message) {
        commitDispatcher.dispatch(message);
    }
}
