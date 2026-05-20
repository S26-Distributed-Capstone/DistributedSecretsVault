package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeletePrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalGetService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostPrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PutPrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.RepairPrepareHandler;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private final InternalGetService internalGetService;
    private final PostPrepareHandler postPrepareHandler;
    private final PutPrepareHandler putPrepareHandler;
    private final DeletePrepareHandler deletePrepareHandler;
    private final RepairPrepareHandler repairPrepareHandler;

    public InternalController(InternalGetService internalGetService,
            PostPrepareHandler postPrepareHandler,
            PutPrepareHandler putPrepareHandler,
            DeletePrepareHandler deletePrepareHandler,
            RepairPrepareHandler repairPrepareHandler) {
        this.internalGetService = internalGetService;
        this.postPrepareHandler = postPrepareHandler;
        this.putPrepareHandler = putPrepareHandler;
        this.deletePrepareHandler = deletePrepareHandler;
        this.repairPrepareHandler = repairPrepareHandler;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SecretPart> getSecretPart(@PathVariable String id,
            @RequestParam(value = "user") String user,
            @RequestParam(value = "version", required = false) Long version) {
        return internalGetService.getVersion(user, id, version);
    }

    @GetMapping("/{id}/all")
    public ResponseEntity<Map<Long, SecretPart>> getAllVersions(@PathVariable String id,
            @RequestParam(value = "user") String user) {
        return internalGetService.getAllVersions(user, id);
    }

    @PostMapping("/prepare")
    public ResponseEntity<Void> preparePost(@RequestBody PostPrepareRequest request) {
        postPrepareHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/prepare")
    public ResponseEntity<Void> preparePut(@RequestBody PutPrepareRequest request) {
        putPrepareHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/repair/prepare")
    public ResponseEntity<Void> prepareRepair(@RequestBody RepairPrepareRequest request) {
        repairPrepareHandler.handle(request);
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

}
