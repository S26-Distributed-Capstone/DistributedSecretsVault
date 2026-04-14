package edu.yu.capstone.dsv.client;

import edu.yu.capstone.dsv.client.dto.CreateSecretRequest;
import edu.yu.capstone.dsv.client.dto.DeleteSecretRequest;
import edu.yu.capstone.dsv.client.dto.UpdateSecretRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

public class Client {

    private static final String SECRETS_PATH = "/api/v1/secrets";

    private final ClientProperties properties;
    private final HttpClient httpClient;

    public Client(ClientProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
    }

    public String createSecret(CreateSecretRequest request) {
        String payload = "{\"secretName\":\"" + escape(request.secretName()) + "\",\"secretValue\":\""
                + escape(request.secretValue()) + "\"}";
        return send("POST", SECRETS_PATH, payload);
    }

    public String getSecret(String id) {
        String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
        return send("GET", SECRETS_PATH + "/" + encodedId, null);
    }

    public String updateSecret(UpdateSecretRequest request) {
        String payload = "{\"secretCurrentName\":\"" + escape(request.secretCurrentName())
                + "\",\"secretCurrentValue\":\"" + escape(request.secretCurrentValue())
                + "\",\"secretUpdatedName\":\"" + escape(request.secretUpdatedName())
                + "\",\"secretUpdatedValue\":\"" + escape(request.secretUpdatedValue()) + "\"}";
        return send("PUT", SECRETS_PATH, payload);
    }

    public void deleteSecret(DeleteSecretRequest request) {
        String payload = "{\"deleteName\":\"" + escape(request.deleteName()) + "\"}";
        send("DELETE", SECRETS_PATH, payload);
    }

    private String send(String method, String path, String body) {
        Supplier<HttpRequest> requestFactory = () -> buildRequest(method, path, body);
        HttpResponse<String> response = executeWithRetry(requestFactory);

        if (response.statusCode() >= 400) {
            throw new ClientException(
                    "Gateway returned error for " + method + " " + path,
                    response.statusCode(),
                    response.body());
        }

        return response.body() == null ? "" : response.body();
    }

    private HttpResponse<String> executeWithRetry(Supplier<HttpRequest> requestFactory) {
        int maxAttempts = properties.maxRetries() + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(requestFactory.get(), HttpResponse.BodyHandlers.ofString());
                if (isRetryableStatus(response.statusCode()) && attempt < maxAttempts) {
                    sleepBeforeRetry();
                    continue;
                }
                return response;
            } catch (IOException e) {
                if (attempt >= maxAttempts) {
                    throw new ClientException("Gateway request failed after retries", e);
                }
                sleepBeforeRetry();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClientException("Request interrupted", e);
            }
        }

        throw new ClientException("Gateway request failed unexpectedly", -1, null);
    }

    private HttpRequest buildRequest(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(properties.baseUrl()) + path))
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(properties.readTimeoutMs()));

        if (properties.bearerToken() != null && !properties.bearerToken().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.bearerToken());
        }

        if (body == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }

        return builder
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static boolean isRetryableStatus(int status) {
        return status == 503 || status == 429;
    }

    private void sleepBeforeRetry() {
        if (properties.retryDelayMs() <= 0) {
            return;
        }

        try {
            Thread.sleep(properties.retryDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException("Retry sleep interrupted", e);
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }

        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}


