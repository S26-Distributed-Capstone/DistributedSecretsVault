package edu.yu.capstone.dsv.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.yu.capstone.dsv.client.dto.CreateSecretRequest;
import edu.yu.capstone.dsv.client.dto.DeleteSecretRequest;
import edu.yu.capstone.dsv.client.dto.UpdateSecretRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientTest {

    private HttpServer server;
    private Client client;

    @BeforeEach
    void setUp() throws IOException {
        //creates mock server
        server = HttpServer.create(new InetSocketAddress(0), 0);

        server.createContext("/api/v1/secrets", exchange -> {
            switch (exchange.getRequestMethod()) {
                case "POST" -> respond(exchange, 201, "created");
                case "PUT" -> respond(exchange, 200, "updated");
                case "DELETE" -> respond(exchange, 204, "");
                default -> respond(exchange, 405, "method not allowed");
            }
        });

        server.createContext("/api/v1/secrets/my-secret", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "retrieved");
                return;
            }
            respond(exchange, 405, "method not allowed");
        });

        AtomicInteger flakyCounter = new AtomicInteger(0);
        server.createContext("/api/v1/secrets/flaky", exchange -> {
            if (flakyCounter.incrementAndGet() < 3) {
                respond(exchange, 503, "retry");
                return;
            }
            respond(exchange, 200, "stable");
        });

        server.start();

        ClientProperties properties = new ClientProperties(
                "http://localhost:" + server.getAddress().getPort(),
                3000,
                5000,
                2,
                1,
                ""
        );

        client = new Client(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void supportsCrudRequests() {
        String created = client.createSecret(new CreateSecretRequest("db-password", "hunter2"));
        String retrieved = client.getSecret("my-secret");
        String updated = client.updateSecret(new UpdateSecretRequest("name", "old", "name", "new"));
        client.deleteSecret(new DeleteSecretRequest("name"));

        assertEquals("created", created);
        assertEquals("retrieved", retrieved);
        assertEquals("updated", updated);
    }

    @Test
    void retriesOn503UntilSuccess() {
        String result = client.getSecret("flaky");
        assertEquals("stable", result);
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
        exchange.close();
    }
}
