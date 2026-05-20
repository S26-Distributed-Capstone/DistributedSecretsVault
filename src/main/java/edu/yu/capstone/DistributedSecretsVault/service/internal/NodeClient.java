package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairPrepareRequest;
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
     * @return peer response details, including status/error data when not acknowledged
     */
    public PeerResponse sendDeletePrepare(String peerUrl, DeletePrepareRequest request) {
        try {
            restClient.delete()
                    .uri(peerUrl
                            + "/internal/prepare?originatorNodeId={orig}&operationId={opId}&secretKeyOwnerId={owner}&secretKeyName={name}",
                            request.getOriginatorNodeId(),
                            request.getOperationId(),
                            request.getSecretKey().getOwnerId(),
                            request.getSecretKey().getName())
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Prepare ACK received from {}", peerUrl);
            return PeerResponse.acknowledged(peerUrl);
        } catch (RestClientResponseException ex) {
            log.warn("Delete prepare rejected by {} with HTTP {}", peerUrl, ex.getStatusCode().value());
            return PeerResponse.rejected(peerUrl, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Failed to send delete prepare to {}: {}", peerUrl, ex.getMessage());
            return PeerResponse.failed(peerUrl, ex.getMessage());
        }
    }

    public PeerResponse sendPostPrepare(String peerUrl, PostPrepareRequest request) {
        try {
            restClient.post()
                    .uri(peerUrl + "/internal/prepare")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Post prepare ACK received from {}", peerUrl);
            return PeerResponse.acknowledged(peerUrl);
        } catch (RestClientResponseException ex) {
            log.warn("Post prepare rejected by {} with HTTP {}", peerUrl, ex.getStatusCode().value());
            return PeerResponse.rejected(peerUrl, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Failed to send post prepare to {}: {}", peerUrl, ex.getMessage());
            return PeerResponse.failed(peerUrl, ex.getMessage());
        }
    }

    public PeerResponse sendPutPrepare(String peerUrl, PutPrepareRequest request) {
        try {
            restClient.put()
                    .uri(peerUrl + "/internal/prepare")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Put prepare ACK received from {}", peerUrl);
            return PeerResponse.acknowledged(peerUrl);
        } catch (RestClientResponseException ex) {
            log.warn("Put prepare rejected by {} with HTTP {}", peerUrl, ex.getStatusCode().value());
            return PeerResponse.rejected(peerUrl, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Failed to send put prepare to {}: {}", peerUrl, ex.getMessage());
            return PeerResponse.failed(peerUrl, ex.getMessage());
        }
    }

    public PeerResponse sendRepairPrepare(String peerUrl, RepairPrepareRequest request) {
        try {
            restClient.post()
                    .uri(peerUrl + "/internal/repair/prepare")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Repair prepare ACK received from {}", peerUrl);
            return PeerResponse.acknowledged(peerUrl);
        } catch (RestClientResponseException ex) {
            log.warn("Repair prepare rejected by {} with HTTP {}", peerUrl, ex.getStatusCode().value());
            return PeerResponse.rejected(peerUrl, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Failed to send repair prepare to {}: {}", peerUrl, ex.getMessage());
            return PeerResponse.failed(peerUrl, ex.getMessage());
        }
    }

    public SecretPartResponse fetchSecretPart(String peerUrl, SecretKey key, Long version) {
        try {
            SecretPart part;
            if (version == null) {
                part = restClient.get()
                        .uri(peerUrl + "/internal/{id}?user={user}", key.getName(), key.getOwnerId())
                        .retrieve()
                        .body(SecretPart.class);
            } else {
                part = restClient.get()
                        .uri(peerUrl + "/internal/{id}?user={user}&version={version}",
                                key.getName(), key.getOwnerId(), version)
                        .retrieve()
                        .body(SecretPart.class);
            }
            log.debug("Secret part received from {}", peerUrl);
            return SecretPartResponse.found(peerUrl, part);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.debug("Secret part not available from {} with HTTP {}", peerUrl, ex.getStatusCode().value());
            } else {
                log.warn("Secret part fetch failed from {} with HTTP {}", peerUrl, ex.getStatusCode().value());
            }
            return SecretPartResponse.rejected(peerUrl, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Failed to fetch secret part from {}: {}", peerUrl, ex.getMessage());
            return SecretPartResponse.failed(peerUrl, ex.getMessage());
        }
    }

    public SecretPartsResponse fetchAllSecretParts(String peerUrl, SecretKey key) {
        try {
            Map<Long, SecretPart> parts = restClient.get()
                    .uri(peerUrl + "/internal/{id}/all?user={user}", key.getName(), key.getOwnerId())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<Long, SecretPart>>() {
                    });
            log.debug("Secret version parts received from {}", peerUrl);
            return SecretPartsResponse.found(peerUrl, parts == null ? Map.of() : parts);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.debug("Secret version parts not available from {} with HTTP {}",
                        peerUrl, ex.getStatusCode().value());
            } else {
                log.warn("Secret version parts fetch failed from {} with HTTP {}",
                        peerUrl, ex.getStatusCode().value());
            }
            return SecretPartsResponse.rejected(peerUrl, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Failed to fetch secret version parts from {}: {}", peerUrl, ex.getMessage());
            return SecretPartsResponse.failed(peerUrl, ex.getMessage());
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
            log.debug("ScaleCube not available - no peers to resolve (single-node mode)");
            return List.of();
        }

        Microservices ms = microservices.get();
        Set<String> peerUrls = new LinkedHashSet<>();

        ms.serviceEndpoints().forEach(endpoint -> {
            // endpoint.address() returns "host:port" (ScaleCube RSocket port)
            // We extract just the host and combine with the HTTP port
            String address = endpoint.address().toString();
            String host = extractHost(address);

            if (!host.isBlank() && !host.equals(selfHost)) {
                peerUrls.add("http://" + host + ":" + httpPort);
            }
        });

        log.debug("Resolved {} peer(s) from ScaleCube service endpoints", peerUrls.size());
        return new ArrayList<>(peerUrls);
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

    public record PeerResponse(String peerUrl, boolean acknowledged, Integer statusCode, String errorMessage) {
        public static PeerResponse acknowledged(String peerUrl) {
            return new PeerResponse(peerUrl, true, null, null);
        }

        public static PeerResponse rejected(String peerUrl, int statusCode, String errorMessage) {
            return new PeerResponse(peerUrl, false, statusCode, errorMessage);
        }

        public static PeerResponse failed(String peerUrl, String errorMessage) {
            return new PeerResponse(peerUrl, false, null, errorMessage);
        }
    }

    public record SecretPartResponse(String peerUrl, SecretPart part, Integer statusCode, String errorMessage) {
        public boolean found() {
            return part != null;
        }

        public static SecretPartResponse found(String peerUrl, SecretPart part) {
            return new SecretPartResponse(peerUrl, part, null, null);
        }

        public static SecretPartResponse rejected(String peerUrl, int statusCode, String errorMessage) {
            return new SecretPartResponse(peerUrl, null, statusCode, errorMessage);
        }

        public static SecretPartResponse failed(String peerUrl, String errorMessage) {
            return new SecretPartResponse(peerUrl, null, null, errorMessage);
        }
    }

    public record SecretPartsResponse(String peerUrl, Map<Long, SecretPart> parts, Integer statusCode,
            String errorMessage) {
        public boolean found() {
            return parts != null && !parts.isEmpty();
        }

        public static SecretPartsResponse found(String peerUrl, Map<Long, SecretPart> parts) {
            return new SecretPartsResponse(peerUrl, parts, null, null);
        }

        public static SecretPartsResponse rejected(String peerUrl, int statusCode, String errorMessage) {
            return new SecretPartsResponse(peerUrl, null, statusCode, errorMessage);
        }

        public static SecretPartsResponse failed(String peerUrl, String errorMessage) {
            return new SecretPartsResponse(peerUrl, null, null, errorMessage);
        }
    }
}
