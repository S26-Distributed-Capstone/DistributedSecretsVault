package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class DeleteCommitHandlerTest {

    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private DeleteCommitHandler handler;

    @Test
    void testHandleDeletesShardWhenBufferedAndExists() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();
        PendingAction buffered = new PendingAction(operationId, key, ActionType.DELETE, Instant.now());

        when(pendingActionsBuffer.commitAndRemove(operationId)).thenReturn(buffered);
        when(secretPartRepository.exists(key)).thenReturn(true);

        handler.handle(new DeleteCommitRequest(operationId, key));

        verify(pendingActionsBuffer).commitAndRemove(operationId);
        verify(secretPartRepository).deleteParts(key);
    }

    @Test
    void testHandleSkipsDeleteWhenNoLocalShard() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();
        PendingAction buffered = new PendingAction(operationId, key, ActionType.DELETE, Instant.now());

        when(pendingActionsBuffer.commitAndRemove(operationId)).thenReturn(buffered);
        when(secretPartRepository.exists(key)).thenReturn(false);

        handler.handle(new DeleteCommitRequest(operationId, key));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenBufferEntryMissing() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();

        when(pendingActionsBuffer.commitAndRemove(operationId)).thenReturn(null);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new DeleteCommitRequest(operationId, key)));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenBufferMissingAndNoShard() {
        SecretKey key = new SecretKey("user1", "secret1");
        UUID operationId = UUID.randomUUID();

        when(pendingActionsBuffer.commitAndRemove(operationId)).thenReturn(null);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new DeleteCommitRequest(operationId, key)));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenCommitKeyDiffersFromBufferedKey() {
        SecretKey bufferedKey = new SecretKey("user1", "secret1");
        SecretKey commitKey = new SecretKey("user1", "secret2");
        UUID operationId = UUID.randomUUID();
        PendingAction buffered = new PendingAction(operationId, bufferedKey, ActionType.DELETE, Instant.now());

        when(pendingActionsBuffer.commitAndRemove(operationId)).thenReturn(buffered);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new DeleteCommitRequest(operationId, commitKey)));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenRequestNull() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(null));
    }

    @Test
    void testHandleThrowsWhenOperationIdNull() {
        SecretKey key = new SecretKey("user1", "secret1");
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new DeleteCommitRequest(null, key)));
    }

    @Test
    void testHandleThrowsWhenSecretKeyNull() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new DeleteCommitRequest(UUID.randomUUID(), null)));
    }
}
