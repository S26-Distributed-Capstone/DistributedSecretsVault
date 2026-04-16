package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.Data;

@Data
public class Node {
    private String nodeId;
    private String host;
    private int port;
}
