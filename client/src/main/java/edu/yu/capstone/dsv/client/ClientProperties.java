package edu.yu.capstone.dsv.client;

public record ClientProperties(
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs,
        int maxRetries,
        long retryDelayMs,
        String bearerToken
) {
    public static ClientProperties fromEnvironment() {
        return new ClientProperties(
                getenvOrDefault("DSV_API_BASE_URL", "http://localhost:8080"),
                parseLongEnv("DSV_CLIENT_CONNECT_TIMEOUT_MS", 3000),
                parseLongEnv("DSV_CLIENT_READ_TIMEOUT_MS", 5000),
                parseIntEnv("DSV_CLIENT_MAX_RETRIES", 2),
                parseLongEnv("DSV_CLIENT_RETRY_DELAY_MS", 200),
                getenvOrDefault("DSV_CLIENT_BEARER_TOKEN", "")
        );
    }

    private static String getenvOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int parseIntEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLongEnv(String key, long fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

}
