package edu.yu.capstone.DistributedSecretsVault.exceptions;

public class NodeCommunicationException extends ServiceUnavailableException {
    public NodeCommunicationException() {
        super("Node communication failure");
    }

    public NodeCommunicationException(String message) {
        super(message);
    }
}
