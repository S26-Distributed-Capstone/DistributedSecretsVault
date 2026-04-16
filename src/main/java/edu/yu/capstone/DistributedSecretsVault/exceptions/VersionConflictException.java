package edu.yu.capstone.DistributedSecretsVault.exceptions;

public class VersionConflictException extends RuntimeException {
    public VersionConflictException() {
        super("Version conflict detected");
    }

    public VersionConflictException(String message) {
        super(message);
    }
}
