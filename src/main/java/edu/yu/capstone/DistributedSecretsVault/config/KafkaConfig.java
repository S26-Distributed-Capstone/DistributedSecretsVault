package edu.yu.capstone.DistributedSecretsVault.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String COORDINATION_TOPIC = "secrets-coordination";
    public static final String COMMIT_TOPIC = "secrets-commit";

    @Bean
    public NewTopic coordinationTopic() {
        return TopicBuilder.name(COORDINATION_TOPIC)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "14400000") // 4 hours retention
                .build();
    }

    @Bean
    public NewTopic commitTopic() {
        return TopicBuilder.name(COMMIT_TOPIC)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "14400000") // 4 hours retention
                .build();
    }

    @Bean
    public org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate(org.springframework.kafka.core.ProducerFactory<String, Object> producerFactory) {
        return new org.springframework.kafka.core.KafkaTemplate<>(producerFactory);
    }
}
