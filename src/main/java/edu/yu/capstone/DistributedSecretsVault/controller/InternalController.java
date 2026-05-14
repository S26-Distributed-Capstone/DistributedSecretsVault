package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeleteCommitHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeletePrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GetShardService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostCommitHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostPrepareHandler;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private final GetShardService getShardService;
    private final PostPrepareHandler postPrepareHandler;
    private final PostCommitHandler postCommitHandler;
    private final DeletePrepareHandler deletePrepareHandler;
    private final DeleteCommitHandler deleteCommitHandler;

    public InternalController(GetShardService getShardService,
            PostPrepareHandler postPrepareHandler,
            PostCommitHandler postCommitHandler,
            DeletePrepareHandler deletePrepareHandler,
            DeleteCommitHandler deleteCommitHandler) {
        this.getShardService = getShardService;
        this.postPrepareHandler = postPrepareHandler;
        this.postCommitHandler = postCommitHandler;
        this.deletePrepareHandler = deletePrepareHandler;
        this.deleteCommitHandler = deleteCommitHandler;
    }

    @GetMapping("/shard/{id}")
    public ResponseEntity<SecretPart> getShard(@PathVariable String id,
            @RequestParam(value = "user") String user,
            @RequestParam(value = "version", required = false) Long version) {
        return getShardService.getVersion(user, id, version);
    }

    @GetMapping("/shard/{id}/all")
    public ResponseEntity<Map<Long, SecretPart>> getAllVersions(@PathVariable String id,
            @RequestParam(value = "user") String user) {
        return getShardService.getAllVersions(user, id);
    }

    @PostMapping("/prepare")
    public ResponseEntity<Void> preparePost(@RequestBody PostPrepareRequest request) {
        postPrepareHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/commit")
    public ResponseEntity<Void> commitPost(@RequestBody PostCommitRequest request) {
        postCommitHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/prepare")
    public ResponseEntity<Void> prepareDelete(
            @RequestParam("originatorNodeId") String originatorNodeId,
            @RequestParam("operationId") UUID operationId,
            @RequestParam("secretKeyOwnerId") String secretKeyOwnerId,
            @RequestParam("secretKeyName") String secretKeyName) {
        SecretKey secretKey = new SecretKey(secretKeyOwnerId, secretKeyName);
        DeletePrepareRequest request = new DeletePrepareRequest(originatorNodeId, operationId, secretKey);
        deletePrepareHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/commit")
    public ResponseEntity<Void> commitDelete(
            @RequestParam("operationId") UUID operationId,
            @RequestParam("secretKeyOwnerId") String secretKeyOwnerId,
            @RequestParam("secretKeyName") String secretKeyName) {
        SecretKey secretKey = new SecretKey(secretKeyOwnerId, secretKeyName);
        DeleteCommitRequest request = new DeleteCommitRequest(operationId, secretKey);
        deleteCommitHandler.handle(request);
        return ResponseEntity.noContent().build();
    }
}
