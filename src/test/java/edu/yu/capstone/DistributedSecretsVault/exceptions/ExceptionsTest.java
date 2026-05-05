package edu.yu.capstone.DistributedSecretsVault.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExceptionsTest {

    @Test
    void testAccessDeniedException() {
        assertEquals("Access denied", new AccessDeniedException().getMessage());
        assertEquals("Custom", new AccessDeniedException("Custom").getMessage());
    }

    @Test
    void testAuthenticationFailedException() {
        assertEquals("Authentication failed", new AuthenticationFailedException().getMessage());
        assertEquals("Custom", new AuthenticationFailedException("Custom").getMessage());
    }

    @Test
    void testDuplicateSecretException() {
        assertEquals("Secret already exists", new DuplicateSecretException().getMessage());
        assertEquals("Custom", new DuplicateSecretException("Custom").getMessage());
    }

    @Test
    void testInsufficientShardsException() {
        assertEquals("Insufficient secret parts to reconstruct", new InsufficientShardsException().getMessage());
        assertEquals("Custom", new InsufficientShardsException("Custom").getMessage());
        assertEquals("Insufficient shards: 1 available, 3 required", new InsufficientShardsException(1, 3).getMessage());
    }

    @Test
    void testInvalidRequestMetadataException() {
        assertEquals("Invalid request metadata", new InvalidRequestMetadataException().getMessage());
        assertEquals("Custom", new InvalidRequestMetadataException("Custom").getMessage());
    }

    @Test
    void testNodeCommunicationException() {
        assertEquals("Node communication failure", new NodeCommunicationException().getMessage());
        assertEquals("Custom", new NodeCommunicationException("Custom").getMessage());
    }

    @Test
    void testQuorumNotReachedException() {
        assertEquals("Not enough confirmations from nodes", new QuorumNotReachedException().getMessage());
        assertEquals("Custom", new QuorumNotReachedException("Custom").getMessage());
    }

    @Test
    void testSecretNotFoundException() {
        assertEquals("Secret not found", new SecretNotFoundException().getMessage());
        assertEquals("Custom", new SecretNotFoundException("Custom").getMessage());
    }

    @Test
    void testServiceUnavailableException() {
        assertEquals("Custom", new ServiceUnavailableException("Custom").getMessage());
    }

    @Test
    void testShardReconstructionException() {
        assertEquals("Shard reconstruction failed - integrity check failure", new ShardReconstructionException().getMessage());
        assertEquals("Custom", new ShardReconstructionException("Custom").getMessage());
    }

    @Test
    void testVersionConflictException() {
        assertEquals("Version conflict detected", new VersionConflictException().getMessage());
        assertEquals("Custom", new VersionConflictException("Custom").getMessage());
    }

    @Test
    void testVersionEnumerationException() {
        assertEquals("Failed to enumerate secret versions", new VersionEnumerationException().getMessage());
        assertEquals("Custom", new VersionEnumerationException("Custom").getMessage());
    }

    @Test
    void testVersionNotFoundException() {
        assertEquals("Secret version not found", new VersionNotFoundException().getMessage());
        assertEquals("Version 2 not found for secret 'key1'", new VersionNotFoundException("key1", 2).getMessage());
    }

    @Test
    void testWriteLockConflictException() {
        assertEquals("Write lock conflict - another write is in progress for this key", new WriteLockConflictException().getMessage());
        assertEquals("Custom", new WriteLockConflictException("Custom").getMessage());
    }

    @Test
    void testErrorResponse() {
        ErrorResponse response = new ErrorResponse("An error");
        assertEquals("An error", response.message());
    }

    @Test
    void testGlobalExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertEquals("arg error", handler.handleIllegalArgument(new IllegalArgumentException("arg error")).message());
        assertEquals("auth error", handler.handleAuthenticationFailedException(new AuthenticationFailedException("auth error")).message());
        assertEquals("access error", handler.handleAccessDeniedException(new AccessDeniedException("access error")).message());
        assertEquals("not found", handler.handleSecretNotFoundException(new SecretNotFoundException("not found")).message());
        assertEquals("Version 2 not found for secret 'key1'", handler.handleVersionNotFoundException(new VersionNotFoundException("key1", 2)).message());
        assertEquals("dup error", handler.handleDuplicateSecretException(new DuplicateSecretException("dup error")).message());
        assertEquals("lock error", handler.handleWriteLockConflictException(new WriteLockConflictException("lock error")).message());
        assertEquals("shard error", handler.handleShardReconstructionException(new ShardReconstructionException("shard error")).message());
        assertEquals("svc error", handler.handleServiceUnavailableException(new ServiceUnavailableException("svc error")).message());
        assertEquals("ver conflict", handler.handleVersionConflictException(new VersionConflictException("ver conflict")).message());
    }
}
