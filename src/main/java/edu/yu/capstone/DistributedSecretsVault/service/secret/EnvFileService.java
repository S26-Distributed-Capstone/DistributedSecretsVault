package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

/**
 * Applies a batch of secret operations encoded in a {@code .env}-style file.
 * <p>
 * Supported entries are {@code KEY=new:value}, {@code KEY=update:value},
 * {@code KEY=get}, {@code KEY=get:version}, and {@code KEY=delete}. Blank
 * lines and comment lines are ignored. Create, update, and get operations are
 * returned as regular {@code KEY=value} lines so callers can materialize a
 * resolved environment file.
 */
@Service
public class EnvFileService {
    private static final Logger log = LoggerFactory.getLogger(EnvFileService.class);

    private final PostSecretService postSecretService;
    private final PutSecretService putSecretService;
    private final DeleteSecretService deleteSecretService;
    private final GetSecretService getSecretService;
    private final SecretPartRepository secretPartRepository;

    /**
     * Creates a batch processor that delegates each parsed operation to the
     * existing single-secret services.
     *
     * @param postSecretService     service used for {@code new} operations
     * @param putSecretService      service used for {@code update} operations
     * @param deleteSecretService   service used for {@code delete} operations
     * @param getSecretService      service used for {@code get} operations
     * @param secretPartRepository  repository used for existence checks
     */
    public EnvFileService(PostSecretService postSecretService,
            PutSecretService putSecretService,
            DeleteSecretService deleteSecretService,
            GetSecretService getSecretService,
            SecretPartRepository secretPartRepository) {
        this.postSecretService = postSecretService;
        this.putSecretService = putSecretService;
        this.deleteSecretService = deleteSecretService;
        this.getSecretService = getSecretService;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Parses, validates, and executes all operations in the submitted file.
     * <p>
     * Preconditions are checked for every operation before any write/delete is
     * performed, which keeps malformed batches from being partially applied.
     *
     * @param user           owner whose secrets should be modified or read
     * @param envFileContent raw {@code .env}-style content
     * @return HTTP 200 response containing output {@code KEY=value} lines
     * @throws IllegalArgumentException if the user or file content is invalid
     * @throws DuplicateSecretException if a {@code new} operation targets an existing secret
     * @throws SecretNotFoundException  if an {@code update}, {@code get}, or {@code delete}
     *                                  operation targets a missing secret
     */
    public ResponseEntity<String> execute(String user, String envFileContent) {
        validateUser(user);
        List<EnvSecretOperation> operations = parseOperations(envFileContent);
        Map<String, String> getResults = validateOperationPreconditions(user, operations);

        int created = 0;
        int updated = 0;
        int retrieved = 0;
        int deleted = 0;
        List<String> resultLines = new ArrayList<>();
        for (EnvSecretOperation operation : operations) {
            switch (operation.action()) {
                case NEW -> {
                    postSecretService.execute(new PostSecretRequest(operation.key(), operation.value(), user));
                    resultLines.add(formatResultLine(operation.key(), operation.value()));
                    created++;
                }
                case UPDATE -> {
                    putSecretService.execute(new PutSecretRequest(operation.key(), operation.value(), user));
                    resultLines.add(formatResultLine(operation.key(), operation.value()));
                    updated++;
                }
                case GET -> {
                    resultLines.add(formatResultLine(operation.key(), getResults.get(operation.key())));
                    retrieved++;
                }
                case DELETE -> {
                    deleteSecretService.execute(new DeleteSecretRequest(operation.key(), user));
                    deleted++;
                }
            }
        }

        log.info("Processed .env file for user={}: created={}, updated={}, retrieved={}, deleted={}",
                user, created, updated, retrieved, deleted);
        return ResponseEntity.ok(String.join("\n", resultLines));
    }

    /** Ensures every batch is scoped to a concrete secret owner. */
    private void validateUser(String user) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
    }

    /**
     * Converts file lines into typed operations while rejecting duplicate keys.
     */
    private List<EnvSecretOperation> parseOperations(String envFileContent) {
        if (envFileContent == null || envFileContent.isBlank()) {
            throw new IllegalArgumentException(".env file content is required");
        }

        List<EnvSecretOperation> operations = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        String[] lines = envFileContent.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String rawLine = lines[index];
            String trimmedLine = rawLine.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            // Enforce one operation per key so a batch has deterministic behavior.
            EnvSecretOperation operation = parseOperationLine(trimmedLine, index + 1);
            if (!seenKeys.add(operation.key())) {
                throw new IllegalArgumentException("Duplicate key in .env file: " + operation.key());
            }
            operations.add(operation);
        }

        if (operations.isEmpty()) {
            throw new IllegalArgumentException(".env file must include at least one operation");
        }
        return operations;
    }

    /**
     * Parses a single non-comment entry into its secret key, action, value, and
     * optional version.
     */
    private EnvSecretOperation parseOperationLine(String line, int lineNumber) {
        int equalsIndex = line.indexOf('=');
        if (equalsIndex <= 0) {
            throw invalidLine(lineNumber);
        }

        String key = line.substring(0, equalsIndex).trim();
        if (key.isBlank()) {
            throw invalidLine(lineNumber);
        }

        String actionAndValue = line.substring(equalsIndex + 1);
        int colonIndex = actionAndValue.indexOf(':');
        if (colonIndex < 0 && actionAndValue.isBlank()) {
            throw invalidLine(lineNumber);
        }

        String actionText = colonIndex < 0
                ? actionAndValue.trim().toLowerCase(Locale.ROOT)
                : actionAndValue.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
        EnvAction action = switch (actionText) {
            case "new" -> EnvAction.NEW;
            case "update" -> EnvAction.UPDATE;
            case "get" -> EnvAction.GET;
            case "delete" -> EnvAction.DELETE;
            default -> throw new IllegalArgumentException("Invalid .env action on line " + lineNumber
                    + ": expected new, update, get, or delete");
        };

        // Values are required only for write actions; get/delete may be bare actions.
        if (colonIndex < 0 && action != EnvAction.GET && action != EnvAction.DELETE) {
            throw invalidLine(lineNumber);
        }

        String value = colonIndex < 0 ? "" : actionAndValue.substring(colonIndex + 1);
        Long version = action == EnvAction.GET && colonIndex >= 0
                ? parseGetVersion(value, lineNumber)
                : null;
        return new EnvSecretOperation(key, action, value, version);
    }

    /** Parses the optional version suffix used by {@code KEY=get:version}. */
    private Long parseGetVersion(String value, int lineNumber) {
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw invalidLine(lineNumber);
        }
        try {
            long version = Long.parseLong(trimmedValue);
            if (version <= 0) {
                throw invalidLine(lineNumber);
            }
            return version;
        } catch (NumberFormatException e) {
            throw invalidLine(lineNumber);
        }
    }

    /** Creates a consistent validation error for malformed .env entries. */
    private IllegalArgumentException invalidLine(int lineNumber) {
        return new IllegalArgumentException("Invalid .env entry on line " + lineNumber
                + ": expected KEY=new:value, KEY=update:value, KEY=get, KEY=get:version, or KEY=delete");
    }

    /**
     * Verifies all operation preconditions before mutating cluster state.
     *
     * @return cached values for {@code get} operations so execution does not
     *         perform a second distributed read
     */
    private Map<String, String> validateOperationPreconditions(String user, List<EnvSecretOperation> operations) {
        Map<String, String> getResults = new HashMap<>();
        for (EnvSecretOperation operation : operations) {
            SecretKey key = new SecretKey(user, operation.key());
            switch (operation.action()) {
                case NEW -> {
                    boolean exists = secretPartRepository.exists(key);
                    if (exists) {
                        throw new DuplicateSecretException("Secret already exists: " + operation.key());
                    }
                }
                case UPDATE, DELETE -> {
                    boolean exists = secretPartRepository.exists(key);
                    if (!exists) {
                        throw new SecretNotFoundException("Secret not found: " + operation.key());
                    }
                }
                case GET -> getResults.put(operation.key(),
                        getSecretService.getVersion(user, operation.key(), operation.version()).getBody());
            }
        }
        return getResults;
    }

    /** Formats output lines in standard .env syntax. */
    private String formatResultLine(String key, String value) {
        return key + "=" + (value == null ? "" : value);
    }

    /** Batch operation verbs supported by the .env endpoint. */
    private enum EnvAction {
        NEW,
        UPDATE,
        GET,
        DELETE
    }

    /** Parsed representation of one actionable .env line. */
    private record EnvSecretOperation(String key, EnvAction action, String value, Long version) {
    }
}
