package edu.yu.capstone.DistributedSecretsVault.exceptions;

public class DuplicateSecretException extends RuntimeException {
    public DuplicateSecretException() {
        super("Secret already exists");
    }
}
