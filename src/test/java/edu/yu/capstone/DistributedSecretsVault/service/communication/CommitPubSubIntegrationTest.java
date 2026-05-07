package edu.yu.capstone.DistributedSecretsVault.service.communication;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class CommitPubSubIntegrationTest {

    @Test
    public void testBroadcastCommit() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        CommitPublisher publisher = new CommitPublisher(kafkaTemplate);

        CommitMessage message = new CommitMessage(
                UUID.randomUUID().toString(),
                "secret-123",
                CommitMessage.Action.POST,
                "encrypted-payload",
                System.currentTimeMillis()
        );

        publisher.broadcastCommit(message);

        verify(kafkaTemplate).send(eq(KafkaConfig.COMMIT_TOPIC), eq(message.getTransactionId()), eq(message));
    }
}
