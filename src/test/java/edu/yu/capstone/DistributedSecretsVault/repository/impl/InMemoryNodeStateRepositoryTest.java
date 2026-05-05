package edu.yu.capstone.DistributedSecretsVault.repository.impl;

import edu.yu.capstone.DistributedSecretsVault.domain.enums.NodeStatus;
import edu.yu.capstone.DistributedSecretsVault.domain.model.Node;
import edu.yu.capstone.DistributedSecretsVault.domain.model.NodeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryNodeStateRepositoryTest {

    private InMemoryNodeStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNodeStateRepository();
    }

    @Test
    void testSaveThrowsIllegalArgumentWhenStateNull() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    void testSaveThrowsIllegalArgumentWhenNodeNull() {
        NodeState state = new NodeState();
        assertThrows(IllegalArgumentException.class, () -> repository.save(state));
    }

    @Test
    void testSaveThrowsIllegalArgumentWhenNodeIdNull() {
        NodeState state = new NodeState();
        Node node = new Node();
        state.setNode(node);
        assertThrows(IllegalArgumentException.class, () -> repository.save(state));
    }

    @Test
    void testSaveAndFindAndFindAll() {
        NodeState state1 = new NodeState();
        Node node1 = new Node();
        node1.setNodeId("node1");
        state1.setNode(node1);
        state1.setStatus(NodeStatus.ACTIVE);

        NodeState state2 = new NodeState();
        Node node2 = new Node();
        node2.setNodeId("node2");
        state2.setNode(node2);
        state2.setStatus(NodeStatus.SUSPECTED);

        repository.save(state1);
        repository.save(state2);

        Optional<NodeState> found1 = repository.findByNodeId("node1");
        assertTrue(found1.isPresent());
        assertEquals("node1", found1.get().getNode().getNodeId());

        Optional<NodeState> foundUnknown = repository.findByNodeId("unknown");
        assertFalse(foundUnknown.isPresent());

        List<NodeState> all = repository.findAll();
        assertEquals(2, all.size());
        assertTrue(all.contains(state1));
        assertTrue(all.contains(state2));
    }

    @Test
    void testDelete() {
        NodeState state1 = new NodeState();
        Node node1 = new Node();
        node1.setNodeId("node1");
        state1.setNode(node1);

        repository.save(state1);
        assertTrue(repository.findByNodeId("node1").isPresent());

        repository.delete("node1");
        assertFalse(repository.findByNodeId("node1").isPresent());
    }
}
