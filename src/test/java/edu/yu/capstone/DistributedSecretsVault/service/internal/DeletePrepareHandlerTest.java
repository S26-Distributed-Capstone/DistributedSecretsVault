package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class DeletePrepareHandlerTest {

    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private DeletePrepareHandler handler;

    @Test
    void testHandleBuffersDeleteWhenShardExists() {
        when(secretPartRepository.exists(any(SecretKey.class))).thenReturn(true);

        UUID operationId = UUID.randomUUID();
        DeletePrepareRequest request = createRequest(operationId, "user1", "secret1");
        handler.handle(request);

        verify(pendingActionsBuffer).bufferAction(
                eq(operationId), any(SecretKey.class), eq(ActionType.DELETE));
    }

    @Test
    void testHandleThrowsWhenShardDoesNotExist() {
        when(secretPartRepository.exists(any(SecretKey.class))).thenReturn(false);

        DeletePrepareRequest request = createRequest(UUID.randomUUID(), "user1", "secret1");
        assertThrows(SecretNotFoundException.class, () -> handler.handle(request));

        verify(pendingActionsBuffer, never()).bufferAction(any(), any(), any());
    }

    @Test
    void testHandleThrowsWhenRequestNull() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(null));
    }

    @Test
    void testHandleThrowsWhenOperationIdNull() {
        DeletePrepareRequest request = createRequest(null, "user1", "secret1");
        assertThrows(IllegalArgumentException.class, () -> handler.handle(request));
    }

    @Test
    void testHandleThrowsWhenSecretKeyNull() {
        DeletePrepareRequest request = new DeletePrepareRequest("originator", UUID.randomUUID(), null);
        assertThrows(IllegalArgumentException.class, () -> handler.handle(request));
    }

    private DeletePrepareRequest createRequest(UUID operationId, String ownerId, String secretName) {
        SecretKey key = new SecretKey(ownerId, secretName);
        return new DeletePrepareRequest("originator-node", operationId, key);
    }
}
