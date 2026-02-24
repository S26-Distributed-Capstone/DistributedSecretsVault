package edu.yu.capstone.DistributedSecretsVault.exceptions;

public class SecretNotFoundException extends RuntimeException {
    public SecretNotFoundException() {
        super("Secret not found");
    }
}
