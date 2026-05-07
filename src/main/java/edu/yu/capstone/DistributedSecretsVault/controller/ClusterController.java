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

@RestController
@RequestMapping("/api/v1/cluster")
@ConditionalOnBean(Microservices.class)
public class ClusterController {

    @Autowired
    private Microservices microservices;

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        // Creates a proxy that routes the ping request over ScaleCube to any available node
        PingService pingService = microservices.call().api(PingService.class);
        String response = pingService.ping("Hello").block();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nodes")
    public ResponseEntity<List<String>> listNodes() {
        List<String> nodes = microservices.serviceEndpoints().stream()
                .map(endpoint -> endpoint.id() + " @ " + endpoint.address())
                .collect(Collectors.toList());
        return ResponseEntity.ok(nodes);
    }

    @GetMapping("/status")
    public ResponseEntity<ClusterStatusResponse> status() {
        ClusterStatusResponse response = new ClusterStatusResponse();
        int total = microservices.serviceEndpoints().size();
        response.setTotalNodes(total);
        response.setHealthyNodes(total); // Simplification for now
        return ResponseEntity.ok(response);
    }
}
