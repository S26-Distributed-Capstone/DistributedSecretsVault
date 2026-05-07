package edu.yu.capstone.DistributedSecretsVault.domain.model;

import edu.yu.capstone.DistributedSecretsVault.domain.enums.NodeStatus;
import lombok.Data;

@Data
public class NodeState {
    private Node node;
    private NodeStatus status;
}
