package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

@Service
public class EnvFileService {
    private static final Logger log = LoggerFactory.getLogger(EnvFileService.class);

    private final PostSecretService postSecretService;
    private final PutSecretService putSecretService;
    private final DeleteSecretService deleteSecretService;
    private final SecretPartRepository secretPartRepository;

    public EnvFileService(PostSecretService postSecretService,
            PutSecretService putSecretService,
            DeleteSecretService deleteSecretService,
            SecretPartRepository secretPartRepository) {
        this.postSecretService = postSecretService;
        this.putSecretService = putSecretService;
        this.deleteSecretService = deleteSecretService;
        this.secretPartRepository = secretPartRepository;
    }

    public ResponseEntity<String> execute(String user, String envFileContent) {
        validateUser(user);
        List<EnvSecretOperation> operations = parseOperations(envFileContent);
        validateOperationPreconditions(user, operations);

        int created = 0;
        int updated = 0;
        int deleted = 0;
        for (EnvSecretOperation operation : operations) {
            switch (operation.action()) {
                case NEW -> {
                    postSecretService.execute(new PostSecretRequest(operation.key(), operation.value(), user));
                    created++;
                }
                case UPDATE -> {
                    putSecretService.execute(new PutSecretRequest(operation.key(), operation.value(), user));
                    updated++;
                }
                case DELETE -> {
                    deleteSecretService.execute(new DeleteSecretRequest(operation.key(), user));
                    deleted++;
                }
            }
        }

        log.info("Processed .env file for user={}: created={}, updated={}, deleted={}",
                user, created, updated, deleted);
        return ResponseEntity.ok("Processed .env file: " + created + " created, "
                + updated + " updated, " + deleted + " deleted");
    }

    private void validateUser(String user) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
    }

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
            case "delete" -> EnvAction.DELETE;
            default -> throw new IllegalArgumentException("Invalid .env action on line " + lineNumber
                    + ": expected new, update, or delete");
        };

        if (colonIndex < 0 && action != EnvAction.DELETE) {
            throw invalidLine(lineNumber);
        }

        String value = colonIndex < 0 ? "" : actionAndValue.substring(colonIndex + 1);
        return new EnvSecretOperation(key, action, value);
    }

    private IllegalArgumentException invalidLine(int lineNumber) {
        return new IllegalArgumentException("Invalid .env entry on line " + lineNumber
                + ": expected KEY=new:value, KEY=update:value, or KEY=delete");
    }

    private void validateOperationPreconditions(String user, List<EnvSecretOperation> operations) {
        for (EnvSecretOperation operation : operations) {
            SecretKey key = new SecretKey(user, operation.key());
            boolean exists = secretPartRepository.exists(key);
            switch (operation.action()) {
                case NEW -> {
                    if (exists) {
                        throw new DuplicateSecretException("Secret already exists: " + operation.key());
                    }
                }
                case UPDATE, DELETE -> {
                    if (!exists) {
                        throw new SecretNotFoundException("Secret not found: " + operation.key());
                    }
                }
            }
        }
    }

    private enum EnvAction {
        NEW,
        UPDATE,
        DELETE
    }

    private record EnvSecretOperation(String key, EnvAction action, String value) {
    }
}
