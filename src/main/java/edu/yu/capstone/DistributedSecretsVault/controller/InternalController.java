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

/**
 * Internal REST controller for node-to-node communication.
 * <p>
 * Endpoints under {@code /internal} are called by peer nodes during the
 * prepare phase of distributed operations and for fetching individual shards.
 * These endpoints are <b>not</b> intended for external client use.
 * <p>
 * Commit messages are delivered via Kafka rather than HTTP, so there are no
 * commit endpoints here.
 *
 * @see edu.yu.capstone.DistributedSecretsVault.service.internal
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final InternalGetService internalGetService;
    private final PostPrepareHandler postPrepareHandler;
    private final PutPrepareHandler putPrepareHandler;
    private final DeletePrepareHandler deletePrepareHandler;
    private final RepairPrepareHandler repairPrepareHandler;

    /**
     * Constructs the controller with all prepare handlers and the internal get service.
     */
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

    /**
     * Retrieves a single shard from this node's local Redis, optionally at a specific version.
     *
     * @param id      the secret name
     * @param user    the secret owner
     * @param version optional version; if omitted, the latest version is returned
     * @return the {@link SecretPart} stored on this node
     */
    @GetMapping("/{id}")
    public ResponseEntity<SecretPart> getSecretPart(@PathVariable String id,
            @RequestParam(value = "user") String user,
            @RequestParam(value = "version", required = false) Long version) {
        return internalGetService.getVersion(user, id, version);
    }

    /**
     * Retrieves all version shards from this node's local Redis.
     *
     * @param id   the secret name
     * @param user the secret owner
     * @return map of version numbers to {@link SecretPart}s
     */
    @GetMapping("/{id}/all")
    public ResponseEntity<Map<Long, SecretPart>> getAllVersions(@PathVariable String id,
            @RequestParam(value = "user") String user) {
        return internalGetService.getAllVersions(user, id);
    }

    /**
     * Handles a prepare request for a distributed create (POST) operation.
     * Buffers the shard locally and returns 204 No Content as an ACK.
     *
     * @param request the prepare request containing the shard and operation ID
     * @return HTTP 204 No Content on success
     */
    @PostMapping("/prepare")
    public ResponseEntity<Void> preparePost(@RequestBody PostPrepareRequest request) {
        postPrepareHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles a prepare request for a distributed update (PUT) operation.
     * Buffers the updated shard locally and returns 204 No Content as an ACK.
     *
     * @param request the prepare request containing the updated shard and operation ID
     * @return HTTP 204 No Content on success
     */
    @PutMapping("/prepare")
    public ResponseEntity<Void> preparePut(@RequestBody PutPrepareRequest request) {
        putPrepareHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles a prepare request for a read-repair operation.
     * Buffers the repair shard locally and returns 204 No Content as an ACK.
     *
     * @param request the repair prepare request containing the shard
     * @return HTTP 204 No Content on success
     */
    @PostMapping("/repair/prepare")
    public ResponseEntity<Void> prepareRepair(@RequestBody RepairPrepareRequest request) {
        repairPrepareHandler.handle(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles a prepare request for a distributed delete operation.
     * Buffers the delete intent locally and returns 204 No Content as an ACK.
     * <p>
     * Unlike other prepare endpoints, delete uses query parameters instead of
     * a JSON body because HTTP DELETE with a body is not universally supported.
     *
     * @param originatorNodeId the node that initiated the delete
     * @param operationId      UUID correlating this prepare to the eventual commit
     * @param secretKeyOwnerId owner of the secret to delete
     * @param secretKeyName    name of the secret to delete
     * @return HTTP 204 No Content on success
     */
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
