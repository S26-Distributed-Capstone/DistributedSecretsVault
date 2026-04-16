package edu.yu.capstone.DistributedSecretsVault.repository;

import java.util.List;
import java.util.Optional;

import edu.yu.capstone.DistributedSecretsVault.domain.model.NodeState;

public interface NodeStateRepository {
    Optional<NodeState> findByNodeId(String nodeId);

    List<NodeState> findAll();

    void save(NodeState state);

    void delete(String nodeId);
}
