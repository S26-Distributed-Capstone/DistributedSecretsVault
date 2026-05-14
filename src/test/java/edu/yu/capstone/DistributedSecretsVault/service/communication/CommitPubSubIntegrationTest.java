package edu.yu.capstone.DistributedSecretsVault.service.communication;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a commit broadcast is visible to multiple DSV-style consumers.
 * <p>
 * In production each node uses its own {@code spring.kafka.consumer.group-id} (via
 * {@code NODE_NAME}), so every node consumes the full topic — unlike a shared group where
 * only one consumer would read each partition.
 */
public class CommitPubSubIntegrationTest {

    private static final int NODE_COUNT = 3;
    private static final Duration POLL = Duration.ofMillis(200);
    private static final Duration ASSIGN_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(30);

    private static EmbeddedKafkaBroker broker;

    @BeforeAll
    static void startKafka() {
        broker = new EmbeddedKafkaKraftBroker(1, 1, KafkaConfig.COMMIT_TOPIC);
        broker.afterPropertiesSet();
    }

    @AfterAll
    static void stopKafka() {
        if (broker != null) {
            broker.destroy();
        }
    }

    @Test
    void broadcastCommit_reachesThreeIndependentNodes() throws Exception {
        String bootstrap = broker.getBrokersAsString();

        CommitMessage message = new CommitMessage(
                UUID.randomUUID().toString(),
                "secret-123",
                CommitMessage.Action.POST,
                "encrypted-payload",
                System.currentTimeMillis());

        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory(bootstrap));
        CommitPublisher publisher = new CommitPublisher(kafkaTemplate);

        CyclicBarrier allNodesSubscribed = new CyclicBarrier(NODE_COUNT + 1);

        ExecutorService executor = Executors.newFixedThreadPool(NODE_COUNT);
        List<Future<CommitMessage>> futures = new ArrayList<>();
        for (int i = 0; i < NODE_COUNT; i++) {
            final String groupId = "dsv-coordination-group-node-" + i;
            futures.add(executor.submit(
                    receiveCommitOnNode(bootstrap, groupId, message.getTransactionId(), allNodesSubscribed)));
        }

        // Block until each node consumer has joined the group and been assigned partitions
        allNodesSubscribed.await(ASSIGN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        publisher.broadcastCommit(message);
        kafkaTemplate.flush();

        List<CommitMessage> received = new ArrayList<>();
        for (Future<CommitMessage> future : futures) {
            received.add(future.get(RECEIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        for (CommitMessage got : received) {
            assertNotNull(got);
            assertEquals(message.getTransactionId(), got.getTransactionId());
            assertEquals(message.getSecretId(), got.getSecretId());
            assertEquals(message.getAction(), got.getAction());
            assertEquals(message.getPayload(), got.getPayload());
        }
    }

    private static Callable<CommitMessage> receiveCommitOnNode(
            String bootstrap,
            String groupId,
            String expectedTxId,
            CyclicBarrier allNodesSubscribed) {
        return () -> {
            try (KafkaConsumer<String, CommitMessage> consumer = new KafkaConsumer<>(consumerProps(bootstrap, groupId))) {
                consumer.subscribe(List.of(KafkaConfig.COMMIT_TOPIC));
                long assignDeadline = System.nanoTime() + ASSIGN_TIMEOUT.toNanos();
                while (consumer.assignment().isEmpty()) {
                    assertTrue(System.nanoTime() < assignDeadline,
                            "consumer " + groupId + " did not get partition assignment");
                    consumer.poll(POLL);
                }
                allNodesSubscribed.await(ASSIGN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

                long receiveDeadline = System.nanoTime() + RECEIVE_TIMEOUT.toNanos();
                while (System.nanoTime() < receiveDeadline) {
                    ConsumerRecords<String, CommitMessage> records = consumer.poll(POLL);
                    for (ConsumerRecord<String, CommitMessage> record : records) {
                        CommitMessage value = record.value();
                        if (value != null && expectedTxId.equals(value.getTransactionId())) {
                            return value;
                        }
                    }
                }
                throw new AssertionError("node " + groupId + " did not receive commit for " + expectedTxId);
            }
        };
    }

    private static Map<String, Object> consumerProps(String bootstrap, String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CommitMessage.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    private static DefaultKafkaProducerFactory<String, Object> producerFactory(String bootstrap) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(props);
    }
}
