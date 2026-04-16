package edu.yu.capstone.DistributedSecretsVault.exceptions;

public class NodeCommunicationException extends RuntimeException {
    public NodeCommunicationException() {
        super("Node communication failure");
    }

    public NodeCommunicationException(String message) {
        super(message);
    }
}
