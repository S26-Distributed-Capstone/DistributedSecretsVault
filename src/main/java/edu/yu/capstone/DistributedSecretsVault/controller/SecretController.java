package edu.yu.capstone.DistributedSecretsVault.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.EnvFileRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.secret.DeleteSecretService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.EnvFileService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.GetSecretService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.PostSecretService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.PutSecretService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Public-facing REST controller for secret CRUD operations.
 * <p>
 * All endpoints are scoped under {@code /api/v1/secrets}. Secrets are
 * identified by a user-provided name and are owner-scoped (each user
 * sees only their own secrets). The controller delegates to dedicated
 * service classes that orchestrate the distributed two-phase commit
 * protocol across the cluster.
 *
 * @see edu.yu.capstone.DistributedSecretsVault.service.secret
 */
@RestController
@RequestMapping("/api/v1/secrets")
public class SecretController {

    private final GetSecretService getSecretService;
    private final PostSecretService postSecretService;
    private final PutSecretService putSecretService;
    private final DeleteSecretService deleteSecretService;
    private final EnvFileService envFileService;

    /**
     * Constructs the controller with all required secret service dependencies.
     */
    public SecretController(GetSecretService getSecretService, PostSecretService postSecretService,
            PutSecretService putSecretService, DeleteSecretService deleteSecretService,
            EnvFileService envFileService) {
        this.getSecretService = getSecretService;
        this.postSecretService = postSecretService;
        this.putSecretService = putSecretService;
        this.deleteSecretService = deleteSecretService;
        this.envFileService = envFileService;
    }

    /**
     * Retrieves a single secret value, optionally at a specific version.
     *
     * @param id      the secret name
     * @param user    the owner of the secret
     * @param version optional version number; if omitted, the latest version is returned
     * @return the reconstructed plaintext secret value
     */
    @GetMapping("/{id}")
    public ResponseEntity<String> getSecret(@PathVariable String id, @RequestParam("user") String user,
            @RequestParam(value = "version", required = false) Long version) {
        return getSecretService.getVersion(user, id, version);
    }

    /**
     * Retrieves all versions of a secret as a map of version number to plaintext value.
     *
     * @param id   the secret name
     * @param user the owner of the secret
     * @return map of version numbers to reconstructed secret values
     */
    @GetMapping("/{id}/all")
    public ResponseEntity<Map<Long, String>> getAllSecrets(@PathVariable String id, @RequestParam("user") String user) {
        return getSecretService.getAllVersions(user, id);
    }

    /**
     * Creates a new secret. The secret value is split into Shamir shards and
     * distributed across the cluster using the two-phase commit protocol.
     *
     * @param request the create request containing name, value, and owner
     * @return HTTP 201 with a confirmation message including the assigned version
     */
    @PostMapping
    public ResponseEntity<String> postSecret(@RequestBody PostSecretRequest request) {
        return postSecretService.execute(request);
    }

    /**
     * Imports secrets from a {@code .env} file sent as plain text.
     * Each {@code KEY=VALUE} line becomes a separate secret.
     *
     * @param user           the owner for the imported secrets
     * @param envFileContent raw text content of the {@code .env} file
     * @return confirmation message summarizing the import
     */
    @PostMapping(value = "/env", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> postEnvFile(@RequestParam("user") String user, @RequestBody String envFileContent) {
        return envFileService.execute(user, envFileContent);
    }

    /**
     * Imports secrets from a {@code .env} file uploaded as a multipart form.
     *
     * @param user the owner for the imported secrets
     * @param file the uploaded {@code .env} file
     * @return confirmation message summarizing the import
     * @throws IllegalArgumentException if the file cannot be read
     */
    @PostMapping(value = "/env", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> postEnvFile(@RequestParam("user") String user,
            @RequestParam("file") MultipartFile file) {
        try {
            return envFileService.execute(user, new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read .env file", e);
        }
    }

    /**
     * Imports secrets from a {@code .env} file sent as a JSON request body.
     *
     * @param request JSON body containing the owner and env file content
     * @return confirmation message summarizing the import
     */
    @PostMapping(value = "/env", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> postEnvFile(@RequestBody EnvFileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        return envFileService.execute(request.getUser(), request.getEnvFileContent());
    }

    /**
     * Updates an existing secret with a new value, creating a new version.
     *
     * @param request the update request containing the secret name, new value, and owner
     * @return confirmation message including the new version number
     */
    @PutMapping
    public ResponseEntity<String> updateSecret(@RequestBody PutSecretRequest request) {
        return putSecretService.execute(request);
    }

    /**
     * Deletes a secret and all of its versions from every node in the cluster.
     *
     * @param request the delete request containing the secret name and owner
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteSecret(@RequestBody DeleteSecretRequest request) {
        return deleteSecretService.execute(request);
    }

}
