package edu.yu.capstone.DistributedSecretsVault.service.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class RepairPrepareHandlerTest {
    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    private RepairPrepareHandler handler;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        handler = new RepairPrepareHandler(pendingActionsBuffer);
        key = new SecretKey("user1", "secret1");
    }

    @Test
    void testHandleBuffersRepairPart() {
        UUID operationId = UUID.randomUUID();
        SecretPartMessage message = new SecretPartMessage(key, 2L, new byte[] { 1 }, 1L, 1);

        handler.handle(new RepairPrepareRequest("node-1", operationId, message));

        verify(pendingActionsBuffer).bufferAction(eq(operationId), eq(key), eq(ActionType.REPAIR),
                any(SecretPart.class));
    }

    @Test
    void testHandleRejectsMissingVersion() {
        UUID operationId = UUID.randomUUID();
        SecretPartMessage message = new SecretPartMessage(key, null, new byte[] { 1 }, 1L, 1);

        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new RepairPrepareRequest("node-1", operationId, message)));
    }
}
