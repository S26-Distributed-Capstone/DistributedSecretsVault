package edu.yu.capstone.DistributedSecretsVault.service.internal;

/**
 * Types of distributed operations that can be buffered in
 * {@link PendingActionsBuffer} during the prepare phase.
 */
public enum ActionType {
    DELETE,
    UPDATE,
    PUT
}
