package edu.yu.capstone.DistributedSecretsVault.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bad Request ──────────────────────────────────────────────────
    // Spring Validation (@Valid) automatically returns 400 for bean-validation
    // failures. This handler covers any remaining IllegalArgumentException
    // thrown by application code (e.g. delete.md §4 – invalid request).

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ErrorResponse handleIllegalArgument(IllegalArgumentException e) {
        return new ErrorResponse(e.getMessage());
    }

    // ── 401 Unauthorized ─────────────────────────────────────────────────
    // retrieve.md §7, delete.md §3 – authentication failures

    @ExceptionHandler(AuthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    public ErrorResponse handleAuthenticationFailedException(AuthenticationFailedException e) {
        return new ErrorResponse(e.getMessage());
    }

    // ── 403 Forbidden ────────────────────────────────────────────────────
    // retrieve.md §7 – valid credentials but insufficient access

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    public ErrorResponse handleAccessDeniedException(AccessDeniedException e) {
        return new ErrorResponse(e.getMessage());
    }

    // ── 404 Not Found ────────────────────────────────────────────────────
    // retrieve.md §4 – secret not found, delete.md §2 – secret not found

    @ExceptionHandler(SecretNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public ErrorResponse handleSecretNotFoundException(SecretNotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }

    // retrieve.md §5 – version not found (key exists, version does not)

    @ExceptionHandler(VersionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public ErrorResponse handleVersionNotFoundException(VersionNotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }

    // ── 409 Conflict ─────────────────────────────────────────────────────
    // create.md §4-5 – duplicate key on create

    @ExceptionHandler(DuplicateSecretException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public ErrorResponse handleDuplicateSecretException(DuplicateSecretException e) {
        return new ErrorResponse(e.getMessage());
    }

    // create.md §2, update.md §2 – write-lock contention on concurrent writes

    @ExceptionHandler(WriteLockConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public ErrorResponse handleWriteLockConflictException(WriteLockConflictException e) {
        return new ErrorResponse(e.getMessage());
    }

    // ── 500 Internal Server Error ────────────────────────────────────────
    // retrieve.md §12 – shard integrity / reconstruction failure

    @ExceptionHandler(ShardReconstructionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ErrorResponse handleShardReconstructionException(ShardReconstructionException e) {
        return new ErrorResponse(e.getMessage());
    }

    // ── 503 Service Unavailable ──────────────────────────────────────────
    // Catches ServiceUnavailableException and all its subtypes:
    //   - InvalidRequestMetadataException  (create.md §6, update.md §4)
    //   - QuorumNotReachedException        (create.md §7-8, update.md §5-6)
    //   - InsufficientShardsException      (retrieve.md §6, §10)
    //   - VersionEnumerationException      (retrieve.md §11)
    //   - NodeCommunicationException       (retrieve.md §9)
    //   - base ServiceUnavailableException (create.md §3, retrieve.md §8-9, update.md §3)

    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ResponseBody
    public ErrorResponse handleServiceUnavailableException(ServiceUnavailableException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(VersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public ErrorResponse handleVersionConflictException(VersionConflictException e) {
        return new ErrorResponse(e.getMessage());
    }
}
