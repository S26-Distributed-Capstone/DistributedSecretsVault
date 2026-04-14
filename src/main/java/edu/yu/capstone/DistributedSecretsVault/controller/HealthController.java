package edu.yu.capstone.DistributedSecretsVault.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.yu.capstone.DistributedSecretsVault.dto.response.ClusterStatusResponse;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/cluster")
    public ResponseEntity<ClusterStatusResponse> clusterHealth() {
        return ResponseEntity.ok(new ClusterStatusResponse());
    }
}
