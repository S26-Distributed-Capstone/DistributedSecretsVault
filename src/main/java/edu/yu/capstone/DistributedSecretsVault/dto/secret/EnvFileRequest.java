package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API request body for importing secrets from a {@code .env} file
 * ({@code POST /api/v1/secrets/env} with {@code application/json}).
 * <p>
 * The {@link #envFileContent} field accepts multiple JSON aliases
 * ({@code content}, {@code env}, {@code envFile}) for client convenience.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvFileRequest {
    /** Owner (user) importing the env file. */
    private String user;

    /** Raw text content of the {@code .env} file (e.g. {@code KEY=VALUE} per line). */
    @JsonAlias({ "content", "env", "envFile" })
    private String envFileContent;
}
