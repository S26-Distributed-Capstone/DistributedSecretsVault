package edu.yu.capstone.DistributedSecretsVault.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.secret.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/secrets")
public class SecretController {

    private final GetSecretService getSecretService;
    private final PostSecretService postSecretService;

    public SecretController(GetSecretService getSecretService, PostSecretService postSecretService) {
        this.getSecretService = getSecretService;
        this.postSecretService = postSecretService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getSecret(@PathVariable String id) {
        return this.getSecretService.execute(id);
    }

    @PostMapping
    public ResponseEntity<String> postSecret(@RequestBody PostSecretRequest request) {
        return this.postSecretService.execute(request);
    }

}
