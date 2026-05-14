package edu.yu.capstone.DistributedSecretsVault.service.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class PostCommitHandlerTest {
    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    private PostCommitHandler handler;
    private SecretKey key;
    private SecretPart part;

    @BeforeEach
    void setUp() {
        handler = new PostCommitHandler(pendingActionsBuffer, secretPartRepository);
        key = new SecretKey("user1", "secret1");
        part = new SecretPart(key, 1L, 1, new byte[] {1, 2, 3});
    }

    @Test
    void testHandleSavesBufferedShard() {
        UUID operationId = UUID.randomUUID();
        when(pendingActionsBuffer.commitAndRemove(operationId))
                .thenReturn(new PendingAction(operationId, key, ActionType.POST, part, Instant.now()));
        when(secretPartRepository.exists(key)).thenReturn(false);

        handler.handle(new PostCommitRequest(operationId, key));

        verify(secretPartRepository).savePart(part);
    }

    @Test
    void testHandleRejectsUnknownOperation() {
        UUID operationId = UUID.randomUUID();
        when(pendingActionsBuffer.commitAndRemove(operationId)).thenReturn(null);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new PostCommitRequest(operationId, key)));
    }
}
