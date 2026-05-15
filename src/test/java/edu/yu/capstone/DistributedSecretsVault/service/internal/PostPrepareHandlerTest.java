package edu.yu.capstone.DistributedSecretsVault.service.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class PostPrepareHandlerTest {
    @Mock
    private PendingActionsBuffer pendingActionsBuffer;

    @Mock
    private SecretPartRepository secretPartRepository;

    private PostPrepareHandler handler;
    private SecretKey key;
    private SecretPartMessage message;

    @BeforeEach
    void setUp() {
        handler = new PostPrepareHandler(pendingActionsBuffer, secretPartRepository);
        key = new SecretKey("user1", "secret1");
        message = new SecretPartMessage(key, 1L, new byte[] {1, 2, 3}, 1L, 1);
    }

    @Test
    void testHandleBuffersShard() {
        UUID operationId = UUID.randomUUID();
        when(secretPartRepository.exists(key)).thenReturn(false);

        handler.handle(new PostPrepareRequest("node-1", operationId, message));

        verify(pendingActionsBuffer).bufferAction(
                org.mockito.ArgumentMatchers.eq(operationId),
                org.mockito.ArgumentMatchers.eq(key),
                org.mockito.ArgumentMatchers.eq(ActionType.POST),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testHandleRejectsDuplicate() {
        when(secretPartRepository.exists(key)).thenReturn(true);

        assertThrows(DuplicateSecretException.class,
                () -> handler.handle(new PostPrepareRequest("node-1", UUID.randomUUID(), message)));
    }
}
