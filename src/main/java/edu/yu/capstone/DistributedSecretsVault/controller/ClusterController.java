package edu.yu.capstone.DistributedSecretsVault.controller;

import java.util.List;
import java.util.stream.Collectors;

import io.scalecube.services.Microservices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.yu.capstone.DistributedSecretsVault.config.ScaleCubeConfig.PingService;
import edu.yu.capstone.DistributedSecretsVault.dto.response.ClusterStatusResponse;

/**
 * REST controller for cluster health and diagnostics.
 * <p>
 * Provides endpoints to verify cluster connectivity, list discovered nodes,
 * and retrieve a summary of cluster health. Only available when ScaleCube
 * is active (i.e., when a {@link Microservices} bean exists).
 */
@RestController
@RequestMapping("/api/v1/cluster")
@ConditionalOnBean(Microservices.class)
public class ClusterController {

    @Autowired
    private Microservices microservices;

    /**
     * Sends a ping request through ScaleCube to any available node in the cluster.
     * <p>
     * Useful for verifying that the ScaleCube service mesh is operational and
     * that at least one peer can process requests.
     *
     * @return a "Pong from {nodeName}" response from the receiving node
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        // Creates a proxy that routes the ping request over ScaleCube to any available node
        PingService pingService = microservices.call().api(PingService.class);
        String response = pingService.ping("Hello").block();
        return ResponseEntity.ok(response);
    }

    /**
     * Lists all nodes discovered by ScaleCube's service discovery.
     *
     * @return list of strings in the format {@code "nodeId @ host:port"}
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<String>> listNodes() {
        List<String> nodes = microservices.serviceEndpoints().stream()
                .map(endpoint -> endpoint.id() + " @ " + endpoint.address())
                .collect(Collectors.toList());
        return ResponseEntity.ok(nodes);
    }

    /**
     * Returns a summary of the cluster's health.
     * <p>
     * Currently uses a simplified model where all discovered nodes are
     * assumed healthy.
     *
     * @return a {@link ClusterStatusResponse} with node counts
     */
    @GetMapping("/status")
    public ResponseEntity<ClusterStatusResponse> status() {
        ClusterStatusResponse response = new ClusterStatusResponse();
        int total = microservices.serviceEndpoints().size();
        response.setTotalNodes(total);
        response.setHealthyNodes(total); // Simplification for now
        return ResponseEntity.ok(response);
    }
}
