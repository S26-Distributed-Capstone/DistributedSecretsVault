package edu.yu.capstone.DistributedSecretsVault.exceptions;

public class InsufficientPartsException extends RuntimeException {
    public InsufficientPartsException() {
        super("Insufficient secret parts to reconstruct");
    }

    public InsufficientPartsException(String message) {
        super(message);
    }
}
