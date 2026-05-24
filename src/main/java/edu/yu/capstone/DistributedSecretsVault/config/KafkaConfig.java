package edu.yu.capstone.DistributedSecretsVault.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for the distributed commit protocol.
 * <p>
 * Defines the two Kafka topics used by the system:
 * <ul>
 *   <li>{@link #COORDINATION_TOPIC} — reserved for future coordination messages</li>
 *   <li>{@link #COMMIT_TOPIC} — carries {@link edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage}
 *       payloads that instruct all nodes to finalize a buffered prepare operation</li>
 * </ul>
 * Both topics are configured with a 4-hour retention period.
 */
@Configuration
public class KafkaConfig {

    /** Topic name for general coordination messages between nodes. */
    public static final String COORDINATION_TOPIC = "secrets-coordination";

    /** Topic name for commit messages broadcast after quorum is reached. */
    public static final String COMMIT_TOPIC = "secrets-commit";

    /**
     * Creates the coordination Kafka topic if it does not already exist.
     *
     * @return a {@link NewTopic} with 1 partition, 1 replica, and 4-hour retention
     */
    @Bean
    public NewTopic coordinationTopic() {
        return TopicBuilder.name(COORDINATION_TOPIC)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "14400000") // 4 hours retention
                .build();
    }

    /**
     * Creates the commit Kafka topic if it does not already exist.
     *
     * @return a {@link NewTopic} with 1 partition, 1 replica, and 4-hour retention
     */
    @Bean
    public NewTopic commitTopic() {
        return TopicBuilder.name(COMMIT_TOPIC)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "14400000") // 4 hours retention
                .build();
    }
}
