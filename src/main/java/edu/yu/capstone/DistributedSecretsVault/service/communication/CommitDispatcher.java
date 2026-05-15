package edu.yu.capstone.DistributedSecretsVault.service.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.service.internal.ActionType;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeleteCommitHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostCommitHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PutCommitHandler;

@Service
public class CommitDispatcher {
    private static final Logger log = LoggerFactory.getLogger(CommitDispatcher.class);

    private final DeleteCommitHandler deleteCommitHandler;
    private final PostCommitHandler postCommitHandler;
    private final PutCommitHandler putCommitHandler;

    public CommitDispatcher(DeleteCommitHandler deleteCommitHandler,
            PostCommitHandler postCommitHandler,
            PutCommitHandler putCommitHandler) {
        this.deleteCommitHandler = deleteCommitHandler;
        this.postCommitHandler = postCommitHandler;
        this.putCommitHandler = putCommitHandler;
    }

    public void dispatch(CommitMessage message) {
        validate(message);
        try {
            if (message.getActionType() == ActionType.DELETE) {
                deleteCommitHandler.handle(new DeleteCommitRequest(message.getOperationId(), message.getSecretKey()));
            } else if (message.getActionType() == ActionType.POST) {
                postCommitHandler.handle(new PostCommitRequest(message.getOperationId(), message.getSecretKey()));
            } else if (message.getActionType() == ActionType.PUT) {
                putCommitHandler.handle(new PutCommitRequest(message.getOperationId(), message.getSecretKey()));
            } else {
                log.warn("Ignoring unsupported commit action type: operationId={}, actionType={}",
                        message.getOperationId(), message.getActionType());
            }
        } catch (InternalOperationConflictException e) {
            log.warn("Ignoring stale or conflicting Kafka commit: operationId={}, key={}, actionType={}, reason={}",
                    message.getOperationId(), message.getSecretKey(), message.getActionType(), e.getMessage());
        }
    }

    private void validate(CommitMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Commit message is required");
        }
        if (message.getOperationId() == null) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        if (message.getSecretKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
        if (message.getActionType() == null) {
            throw new IllegalArgumentException("Action type is required");
        }
    }
}
