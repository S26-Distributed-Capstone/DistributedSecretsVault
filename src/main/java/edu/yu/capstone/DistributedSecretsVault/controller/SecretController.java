package edu.yu.capstone.DistributedSecretsVault.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.secret.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/secrets")
public class SecretController {

    private final GetSecretService getSecretService;
    private final PostSecretService postSecretService;
    private final PutSecretService putSecretService;
    private final DeleteSecretService deleteSecretService;

    public SecretController(GetSecretService getSecretService, PostSecretService postSecretService,
            PutSecretService putSecretService, DeleteSecretService deleteSecretService) {
        this.getSecretService = getSecretService;
        this.postSecretService = postSecretService;
        this.putSecretService = putSecretService;
        this.deleteSecretService = deleteSecretService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getSecret(@PathVariable String id, @RequestParam("user") String user) {
        return this.getSecretService.execute(user, id);
    }

    @PostMapping
    public ResponseEntity<String> postSecret(@RequestBody PostSecretRequest request) {
        return this.postSecretService.execute(request);
    }

    @PutMapping
    public ResponseEntity<String> updateSecret(@RequestBody PutSecretRequest request) {
        return this.putSecretService.execute(request);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteSecret(@RequestBody DeleteSecretRequest request) {
        return this.deleteSecretService.execute(request);
    }

}
