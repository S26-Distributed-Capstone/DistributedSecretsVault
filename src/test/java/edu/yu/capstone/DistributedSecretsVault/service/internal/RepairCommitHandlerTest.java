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
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class RepairCommitHandlerTest {
    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    private RepairCommitHandler handler;
    private SecretKey key;
    private SecretPart part;

    @BeforeEach
    void setUp() {
        handler = new RepairCommitHandler(pendingActionsBuffer, secretPartRepository);
        key = new SecretKey("user1", "secret1");
        part = new SecretPart(key, 2L, 1, new byte[] { 1, 2, 3 });
    }

    @Test
    void testHandleSavesBufferedShardWithoutNewVersion() {
        UUID operationId = UUID.randomUUID();
        when(pendingActionsBuffer.commitAndRemove(operationId))
                .thenReturn(new PendingAction(operationId, key, ActionType.REPAIR, part, Instant.now()));

        handler.handle(new RepairCommitRequest(operationId, key));

        verify(secretPartRepository).savePart(part);
    }

    @Test
    void testHandleRejectsUnknownOperation() {
        UUID operationId = UUID.randomUUID();
        when(pendingActionsBuffer.commitAndRemove(operationId)).thenReturn(null);

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new RepairCommitRequest(operationId, key)));
    }

    @Test
    void testHandleRejectsWrongActionType() {
        UUID operationId = UUID.randomUUID();
        when(pendingActionsBuffer.commitAndRemove(operationId))
                .thenReturn(new PendingAction(operationId, key, ActionType.PUT, part, Instant.now()));

        assertThrows(InternalOperationConflictException.class,
                () -> handler.handle(new RepairCommitRequest(operationId, key)));
    }
}
