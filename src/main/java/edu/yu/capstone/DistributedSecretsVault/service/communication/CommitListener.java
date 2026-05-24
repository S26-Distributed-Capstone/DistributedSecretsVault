package edu.yu.capstone.DistributedSecretsVault.service.communication;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer that listens for commit messages on the
 * {@link KafkaConfig#COMMIT_TOPIC} topic and delegates them to the
 * {@link CommitDispatcher} for processing.
 * <p>
 * Every node in the cluster runs this listener, so a single Kafka commit
 * message is consumed by all nodes simultaneously, ensuring every node
 * commits the same buffered prepare action.
 */
@Service
public class CommitListener {
    private final CommitDispatcher commitDispatcher;

    /**
     * Constructs the listener with the dispatcher that routes commit messages.
     */
    public CommitListener(CommitDispatcher commitDispatcher) {
        this.commitDispatcher = commitDispatcher;
    }

    /**
     * Invoked by Spring Kafka when a new {@link CommitMessage} arrives on
     * the commit topic. Delegates to the dispatcher for action-specific handling.
     *
     * @param message the deserialized commit message
     */
    @KafkaListener(topics = KafkaConfig.COMMIT_TOPIC)
    public void onCommitMessage(CommitMessage message) {
        commitDispatcher.dispatch(message);
    }
}
