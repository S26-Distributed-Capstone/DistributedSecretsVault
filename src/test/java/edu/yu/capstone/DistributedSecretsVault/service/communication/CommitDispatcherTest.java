package edu.yu.capstone.DistributedSecretsVault.service.communication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.service.internal.ActionType;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeleteCommitHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostCommitHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PutCommitHandler;

/**
 * Unit tests for {@link CommitDispatcher}.
 * Verifies correct routing to handlers and graceful handling of stale commits.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class CommitDispatcherTest {

    @Mock
    private DeleteCommitHandler deleteCommitHandler;

    @Mock
    private PostCommitHandler postCommitHandler;

    @Mock
    private PutCommitHandler putCommitHandler;

    private CommitDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CommitDispatcher(deleteCommitHandler, postCommitHandler, putCommitHandler);
    }

    // ── Routing ─────────────────────────────────────────────────────────

    @Test
    void testDispatchesDeleteToDeleteHandler() {
        CommitMessage msg = new CommitMessage(
                UUID.randomUUID(), new SecretKey("user1", "secret1"), ActionType.DELETE);

        dispatcher.dispatch(msg);

        verify(deleteCommitHandler).handle(any(DeleteCommitRequest.class));
        verify(postCommitHandler, never()).handle(any());
        verify(putCommitHandler, never()).handle(any());
    }

    @Test
    void testDispatchesPostToPostHandler() {
        CommitMessage msg = new CommitMessage(
                UUID.randomUUID(), new SecretKey("user1", "secret1"), ActionType.POST);

        dispatcher.dispatch(msg);

        verify(postCommitHandler).handle(any(PostCommitRequest.class));
        verify(deleteCommitHandler, never()).handle(any());
        verify(putCommitHandler, never()).handle(any());
    }

    @Test
    void testDispatchesPutToPutHandler() {
        CommitMessage msg = new CommitMessage(
                UUID.randomUUID(), new SecretKey("user1", "secret1"), ActionType.PUT);

        dispatcher.dispatch(msg);

        verify(putCommitHandler).handle(any(PutCommitRequest.class));
        verify(deleteCommitHandler, never()).handle(any());
        verify(postCommitHandler, never()).handle(any());
    }

    // ── Stale / Conflicting Commits ─────────────────────────────────────

    @Test
    void testStaleCommitIsCaughtAndLogged() {
        CommitMessage msg = new CommitMessage(
                UUID.randomUUID(), new SecretKey("user1", "secret1"), ActionType.DELETE);
        doThrow(new InternalOperationConflictException("No staged operation found"))
                .when(deleteCommitHandler).handle(any(DeleteCommitRequest.class));

        // Should NOT throw — CommitDispatcher catches InternalOperationConflictException
        assertDoesNotThrow(() -> dispatcher.dispatch(msg));
    }

    @Test
    void testStalePostCommitIsCaughtAndLogged() {
        CommitMessage msg = new CommitMessage(
                UUID.randomUUID(), new SecretKey("user1", "secret1"), ActionType.POST);
        doThrow(new InternalOperationConflictException("No staged operation found"))
                .when(postCommitHandler).handle(any(PostCommitRequest.class));

        assertDoesNotThrow(() -> dispatcher.dispatch(msg));
    }

    @Test
    void testStalePutCommitIsCaughtAndLogged() {
        CommitMessage msg = new CommitMessage(
                UUID.randomUUID(), new SecretKey("user1", "secret1"), ActionType.PUT);
        doThrow(new InternalOperationConflictException("No staged operation found"))
                .when(putCommitHandler).handle(any(PutCommitRequest.class));

        assertDoesNotThrow(() -> dispatcher.dispatch(msg));
    }

    // ── Validation ──────────────────────────────────────────────────────

    @Test
    void testDispatchRejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(null));
    }

    @Test
    void testDispatchRejectsNullOperationId() {
        CommitMessage msg = new CommitMessage(null, new SecretKey("u", "s"), ActionType.DELETE);
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(msg));
    }

    @Test
    void testDispatchRejectsNullSecretKey() {
        CommitMessage msg = new CommitMessage(UUID.randomUUID(), null, ActionType.DELETE);
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(msg));
    }

    @Test
    void testDispatchRejectsNullActionType() {
        CommitMessage msg = new CommitMessage(UUID.randomUUID(), new SecretKey("u", "s"), null);
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(msg));
    }
}
