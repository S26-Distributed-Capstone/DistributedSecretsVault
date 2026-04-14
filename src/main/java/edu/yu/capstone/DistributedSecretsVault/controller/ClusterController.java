package edu.yu.capstone.DistributedSecretsVault.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.yu.capstone.DistributedSecretsVault.dto.response.ClusterStatusResponse;

@RestController
@RequestMapping("/api/v1/cluster")
public class ClusterController {

    @GetMapping("/nodes")
    public ResponseEntity<List<String>> listNodes() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/status")
    public ResponseEntity<ClusterStatusResponse> status() {
        return ResponseEntity.ok(new ClusterStatusResponse());
    }
}
