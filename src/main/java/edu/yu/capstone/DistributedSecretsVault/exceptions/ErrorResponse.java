package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Standardized error response body for all HTTP 4xx/5xx responses.
 * Used by {@link GlobalExceptionHandler}.
 */
public record ErrorResponse(String message) {
}