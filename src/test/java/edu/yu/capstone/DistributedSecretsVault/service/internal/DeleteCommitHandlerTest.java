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
        PendingAction buffered = new PendingAction("op-1", key, ActionType.DELETE, Instant.now());

        when(pendingActionsBuffer.commitAndRemove("op-1")).thenReturn(buffered);
        when(secretPartRepository.exists(key)).thenReturn(true);

        handler.handle(new DeleteCommitRequest("op-1", key));

        verify(pendingActionsBuffer).commitAndRemove("op-1");
        verify(secretPartRepository).deleteParts(key);
    }

    @Test
    void testHandleSkipsDeleteWhenNoLocalShard() {
        SecretKey key = new SecretKey("user1", "secret1");
        PendingAction buffered = new PendingAction("op-2", key, ActionType.DELETE, Instant.now());

        when(pendingActionsBuffer.commitAndRemove("op-2")).thenReturn(buffered);
        when(secretPartRepository.exists(key)).thenReturn(false);

        handler.handle(new DeleteCommitRequest("op-2", key));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenBufferEntryMissing() {
        SecretKey key = new SecretKey("user1", "secret1");

        when(pendingActionsBuffer.commitAndRemove("op-3")).thenReturn(null);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new DeleteCommitRequest("op-3", key)));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenBufferMissingAndNoShard() {
        SecretKey key = new SecretKey("user1", "secret1");

        when(pendingActionsBuffer.commitAndRemove("op-4")).thenReturn(null);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new DeleteCommitRequest("op-4", key)));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenCommitKeyDiffersFromBufferedKey() {
        SecretKey bufferedKey = new SecretKey("user1", "secret1");
        SecretKey commitKey = new SecretKey("user1", "secret2");
        PendingAction buffered = new PendingAction("op-6", bufferedKey, ActionType.DELETE, Instant.now());

        when(pendingActionsBuffer.commitAndRemove("op-6")).thenReturn(buffered);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new DeleteCommitRequest("op-6", commitKey)));

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
    void testHandleThrowsWhenOperationIdBlank() {
        SecretKey key = new SecretKey("user1", "secret1");
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new DeleteCommitRequest("   ", key)));
    }

    @Test
    void testHandleThrowsWhenSecretKeyNull() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new DeleteCommitRequest("op-5", null)));
    }
}
