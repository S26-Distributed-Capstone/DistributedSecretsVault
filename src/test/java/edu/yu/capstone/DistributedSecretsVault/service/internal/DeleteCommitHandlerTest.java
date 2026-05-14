package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
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
public class DeleteCommitHandlerTest {

    @Mock
    private PendingDeleteBuffer pendingDeleteBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private DeleteCommitHandler handler;

    @Test
    void testHandleDeletesShardWhenBufferedAndExists() {
        SecretKey key = createKey("user1", "secret1");
        DeletePrepareRequest buffered = new DeletePrepareRequest("originator", "op-1", key);

        when(pendingDeleteBuffer.getAndRemove("op-1")).thenReturn(buffered);
        when(secretPartRepository.exists(key)).thenReturn(true);

        handler.handle(new DeleteCommitRequest("op-1", key));

        verify(pendingDeleteBuffer).getAndRemove("op-1");
        verify(secretPartRepository).deleteParts(key);
    }

    @Test
    void testHandleSkipsDeleteWhenNoLocalShard() {
        SecretKey key = createKey("user1", "secret1");
        DeletePrepareRequest buffered = new DeletePrepareRequest("originator", "op-2", key);

        when(pendingDeleteBuffer.getAndRemove("op-2")).thenReturn(buffered);
        when(secretPartRepository.exists(key)).thenReturn(false);

        handler.handle(new DeleteCommitRequest("op-2", key));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleStillDeletesWhenBufferEntryMissing() {
        // Entry may have been evicted, but we should still attempt the delete
        SecretKey key = createKey("user1", "secret1");

        when(pendingDeleteBuffer.getAndRemove("op-3")).thenReturn(null);
        when(secretPartRepository.exists(key)).thenReturn(true);

        handler.handle(new DeleteCommitRequest("op-3", key));

        verify(secretPartRepository).deleteParts(key);
    }

    @Test
    void testHandleNoOpWhenBufferMissingAndNoShard() {
        SecretKey key = createKey("user1", "secret1");

        when(pendingDeleteBuffer.getAndRemove("op-4")).thenReturn(null);
        when(secretPartRepository.exists(key)).thenReturn(false);

        handler.handle(new DeleteCommitRequest("op-4", key));

        verify(secretPartRepository, never()).deleteParts(any());
    }

    @Test
    void testHandleThrowsWhenRequestNull() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(null));
    }

    @Test
    void testHandleThrowsWhenOperationIdNull() {
        SecretKey key = createKey("user1", "secret1");
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new DeleteCommitRequest(null, key)));
    }

    @Test
    void testHandleThrowsWhenOperationIdBlank() {
        SecretKey key = createKey("user1", "secret1");
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new DeleteCommitRequest("   ", key)));
    }

    @Test
    void testHandleThrowsWhenSecretKeyNull() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new DeleteCommitRequest("op-5", null)));
    }

    private SecretKey createKey(String ownerId, String name) {
        return new SecretKey(ownerId, name);
    }
}
