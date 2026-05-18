package edu.yu.capstone.DistributedSecretsVault.service.communication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import edu.yu.capstone.DistributedSecretsVault.config.KafkaConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.ServiceUnavailableException;
import edu.yu.capstone.DistributedSecretsVault.service.internal.ActionType;

/**
 * Unit tests for {@link CommitPublisher}.
 * Verifies Kafka publishing behavior including success, failure, and validation.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class CommitPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private CommitPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CommitPublisher(kafkaTemplate);
    }

    @Test
    void testBroadcastCommitSendsToCorrectTopicWithOperationIdAsKey() throws Exception {
        UUID operationId = UUID.randomUUID();
        SecretKey secretKey = new SecretKey("user1", "secret1");
        CommitMessage message = new CommitMessage(operationId, secretKey, ActionType.POST);

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
        doReturn(null).when(future).get(anyLong(), any());

        assertDoesNotThrow(() -> publisher.broadcastCommit(message));

        verify(kafkaTemplate).send(
                eq(KafkaConfig.COMMIT_TOPIC),
                eq(operationId.toString()),
                eq(message));
    }

    @Test
    void testBroadcastCommitThrowsServiceUnavailableOnKafkaFailure() throws Exception {
        UUID operationId = UUID.randomUUID();
        CommitMessage message = new CommitMessage(
                operationId, new SecretKey("u", "s"), ActionType.DELETE);

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
        doThrow(new RuntimeException("Kafka broker unavailable")).when(future).get(anyLong(), any());

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> publisher.broadcastCommit(message));

        assertTrue(ex.getMessage().contains(operationId.toString()));
    }

    @Test
    void testBroadcastCommitRejectsNullMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> publisher.broadcastCommit(null));
    }

    @Test
    void testBroadcastCommitRejectsNullOperationId() {
        CommitMessage message = new CommitMessage(null, new SecretKey("u", "s"), ActionType.POST);

        assertThrows(IllegalArgumentException.class,
                () -> publisher.broadcastCommit(message));
    }
}
