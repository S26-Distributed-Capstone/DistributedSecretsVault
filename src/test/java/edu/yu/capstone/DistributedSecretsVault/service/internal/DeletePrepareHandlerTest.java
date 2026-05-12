package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class DeletePrepareHandlerTest {

    @Mock
    private PendingDeleteBuffer pendingDeleteBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private DeletePrepareHandler handler;

    @Test
    void testHandleBuffersDeleteWhenShardExists() {
        when(secretPartRepository.exists(any(SecretKey.class))).thenReturn(true);

        DeletePrepareRequest request = createRequest("op-1", "user1", "secret1");
        handler.handle(request);

        verify(pendingDeleteBuffer).bufferDelete(request);
    }

    @Test
    void testHandleBuffersDeleteWhenShardDoesNotExist() {
        when(secretPartRepository.exists(any(SecretKey.class))).thenReturn(false);

        DeletePrepareRequest request = createRequest("op-2", "user1", "secret1");
        handler.handle(request);

        // Buffer should still be called even if no local shard exists
        verify(pendingDeleteBuffer).bufferDelete(request);
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
    void testHandleThrowsWhenOperationIdBlank() {
        DeletePrepareRequest request = createRequest("   ", "user1", "secret1");
        assertThrows(IllegalArgumentException.class, () -> handler.handle(request));
    }

    @Test
    void testHandleThrowsWhenSecretKeyNull() {
        DeletePrepareRequest request = new DeletePrepareRequest("originator", "op-3", null);
        assertThrows(IllegalArgumentException.class, () -> handler.handle(request));
    }

    private DeletePrepareRequest createRequest(String operationId, String ownerId, String secretName) {
        SecretKey key = new SecretKey();
        key.setOwnerId(ownerId);
        key.setName(secretName);
        return new DeletePrepareRequest("originator-node", operationId, key);
    }
}
