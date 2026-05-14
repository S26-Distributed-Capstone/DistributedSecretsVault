package edu.yu.capstone.DistributedSecretsVault.service.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class PutPrepareHandlerTest {
    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    private PutPrepareHandler handler;
    private SecretKey key;
    private SecretPartMessage message;

    @BeforeEach
    void setUp() {
        handler = new PutPrepareHandler(pendingActionsBuffer, secretPartRepository);
        key = new SecretKey("user1", "secret1");
        message = new SecretPartMessage(key, 2L, new byte[] {1, 2, 3}, 1L, 1);
    }

    @Test
    void testHandleBuffersShardWhenKeyExists() {
        UUID operationId = UUID.randomUUID();
        when(secretPartRepository.exists(key)).thenReturn(true);

        handler.handle(new PutPrepareRequest("node-1", operationId, message));

        verify(pendingActionsBuffer).bufferAction(
                eq(operationId),
                eq(key),
                eq(ActionType.PUT),
                any());
    }

    @Test
    void testHandleRejectsMissingKey() {
        when(secretPartRepository.exists(key)).thenReturn(false);

        assertThrows(SecretNotFoundException.class,
                () -> handler.handle(new PutPrepareRequest("node-1", UUID.randomUUID(), message)));
    }
}
