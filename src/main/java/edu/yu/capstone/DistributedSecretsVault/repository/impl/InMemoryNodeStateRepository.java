package edu.yu.capstone.DistributedSecretsVault.repository.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import edu.yu.capstone.DistributedSecretsVault.domain.model.NodeState;
import edu.yu.capstone.DistributedSecretsVault.repository.NodeStateRepository;

public class InMemoryNodeStateRepository implements NodeStateRepository {
    private final Map<String, NodeState> states = new ConcurrentHashMap<>();

    @Override
    public Optional<NodeState> findByNodeId(String nodeId) {
        return Optional.ofNullable(states.get(nodeId));
    }

    @Override
    public List<NodeState> findAll() {
        return List.copyOf(states.values());
    }

    @Override
    public void save(NodeState state) {
        if (state == null || state.getNode() == null || state.getNode().getNodeId() == null) {
            throw new IllegalArgumentException("NodeState must include a nodeId");
        }
        states.put(state.getNode().getNodeId(), state);
    }

    @Override
    public void delete(String nodeId) {
        states.remove(nodeId);
    }
}
