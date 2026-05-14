package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import io.scalecube.services.Microservices;

/**
 * Outbound HTTP client for inter-node communication within the cluster.
 * <p>
 * Peer discovery is handled via ScaleCube's
 * {@link Microservices#serviceEndpoints()},
 * which provides the addresses of all known cluster members. The ScaleCube
 * endpoint host (set via {@code externalHost} in ScaleCubeConfig) is combined
 * with the Spring Boot HTTP port to form peer base URLs.
 * <p>
 * When ScaleCube is not available (single-node mode, test profile),
 * {@link #resolvePeerUrls()} returns an empty list.
 */
@Component
public class NodeClient {
    private static final Logger log = LoggerFactory.getLogger(NodeClient.class);
    private static final int DEFAULT_HTTP_PORT = 8080;

    private final RestClient restClient;
    private final Optional<Microservices> microservices;
    private final String selfHost;
    private final int httpPort;

    public NodeClient(RestClient restClient, Optional<Microservices> microservices) {
        this.restClient = restClient;
        this.microservices = microservices;

        String envPodIp = readEnv("POD_IP");
        this.selfHost = (envPodIp != null && !envPodIp.isBlank()) ? envPodIp : "localhost";

        String portStr = readEnv("SERVER_PORT");
        if (portStr == null || portStr.isBlank()) {
            portStr = System.getProperty("server.port");
        }
        this.httpPort = (portStr != null && !portStr.isBlank())
                ? Integer.parseInt(portStr)
                : DEFAULT_HTTP_PORT;
    }

    /**
     * Send a delete prepare request to a peer node.
     *
     * @param peerUrl base URL of the peer (e.g. {@code http://10.0.0.2:8080})
     * @param request the prepare request
     * @return {@code true} if the peer acknowledged successfully
     */
    public boolean sendDeletePrepare(String peerUrl, DeletePrepareRequest request) {
        try {
            restClient.delete()
                    .uri(peerUrl
                            + "/internal/delete/prepare?originatorNodeId={orig}&operationId={opId}&secretKeyOwnerId={owner}&secretKeyName={name}",
                            request.getOriginatorNodeId(),
                            request.getOperationId(),
                            request.getSecretKey().getOwnerId(),
                            request.getSecretKey().getName())
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Prepare ACK received from {}", peerUrl);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send delete prepare to {}: {}", peerUrl, ex.getMessage());
            return false;
        }
    }

    /**
     * Send a delete commit request to a peer node.
     *
     * @param peerUrl base URL of the peer
     * @param request the commit request
     * @return {@code true} if the peer acknowledged successfully
     */
    public boolean sendDeleteCommit(String peerUrl, DeleteCommitRequest request) {
        try {
            restClient.delete()
                    .uri(peerUrl
                            + "/internal/delete/commit?operationId={opId}&secretKeyOwnerId={owner}&secretKeyName={name}",
                            request.getOperationId(),
                            request.getSecretKey().getOwnerId(),
                            request.getSecretKey().getName())
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Commit ACK received from {}", peerUrl);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send delete commit to {}: {}", peerUrl, ex.getMessage());
            return false;
        }
    }

    /**
     * Resolve peer node base URLs using ScaleCube's service discovery.
     * <p>
     * Extracts the host from each ScaleCube {@code ServiceEndpoint} address
     * (set via {@code externalHost} in ScaleCubeConfig, which is the pod IP
     * in Kubernetes), combines it with the Spring Boot HTTP port, and filters
     * out the current node.
     *
     * @return list of peer base URLs (e.g.
     *         {@code ["http://10.0.0.2:8080", "http://10.0.0.3:8080"]})
     */
    public List<String> resolvePeerUrls() {
        if (microservices.isEmpty()) {
            log.debug("ScaleCube not available — no peers to resolve (single-node mode)");
            return List.of();
        }

        Microservices ms = microservices.get();
        List<String> peerUrls = new ArrayList<>();

        ms.serviceEndpoints().forEach(endpoint -> {
            // endpoint.address() returns "host:port" (ScaleCube RSocket port)
            // We extract just the host and combine with the HTTP port
            String address = endpoint.address().toString();
            String host = extractHost(address);

            if (!host.equals(selfHost)) {
                peerUrls.add("http://" + host + ":" + httpPort);
            }
        });

        log.debug("Resolved {} peer(s) from ScaleCube service endpoints", peerUrls.size());
        return peerUrls;
    }

    /**
     * Extract the host portion from a ScaleCube address string.
     * ScaleCube addresses are formatted as "host:port".
     */
    private static String extractHost(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        int colonIndex = address.lastIndexOf(':');
        if (colonIndex > 0) {
            return address.substring(0, colonIndex);
        }
        return address;
    }

    private static String readEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value;
    }
}
