package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.NodeCommunicationException;

/**
 * Outbound HTTP client for inter-node communication within the cluster.
 * <p>
 * Uses the same DNS-based peer discovery as ScaleCube: resolves
 * {@code SEED_DNS_HOST} to obtain all pod IPs, combines each with the
 * Spring Boot HTTP port, and filters out the current node's own IP
 * ({@code POD_IP}).
 * <p>
 * Throws {@link NodeCommunicationException} when a peer call fails.
 */
@Component
public class NodeClient {
    private static final Logger log = LoggerFactory.getLogger(NodeClient.class);
    private static final int DEFAULT_HTTP_PORT = 8080;

    private final RestClient restClient;
    private final String podIp;
    private final String seedDnsHost;
    private final int httpPort;

    public NodeClient(RestClient restClient) {
        this.restClient = restClient;

        String envPodIp = readEnv("POD_IP");
        this.podIp = (envPodIp != null && !envPodIp.isBlank()) ? envPodIp : "localhost";

        this.seedDnsHost = readEnv("SEED_DNS_HOST");

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
            restClient.post()
                    .uri(peerUrl + "/internal/delete/prepare")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
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
            restClient.post()
                    .uri(peerUrl + "/internal/delete/commit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
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
     * Resolve peer node base URLs using the same DNS-based discovery as
     * ScaleCube. Resolves {@code SEED_DNS_HOST}, combines with the HTTP
     * port, and filters out the current node.
     *
     * @return list of peer base URLs (e.g. {@code ["http://10.0.0.2:8080", "http://10.0.0.3:8080"]})
     */
    public List<String> resolvePeerUrls() {
        if (seedDnsHost == null || seedDnsHost.isBlank()) {
            log.debug("SEED_DNS_HOST not set — no peers to resolve (single-node mode)");
            return List.of();
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(seedDnsHost);
            List<String> peerUrls = new ArrayList<>();
            for (InetAddress addr : addresses) {
                String ip = addr.getHostAddress();
                if (!ip.equals(podIp)) {
                    peerUrls.add("http://" + ip + ":" + httpPort);
                }
            }
            log.debug("Resolved {} peer(s) from DNS host {}", peerUrls.size(), seedDnsHost);
            return peerUrls;
        } catch (UnknownHostException ex) {
            throw new NodeCommunicationException(
                    "Failed to resolve peer nodes from DNS host: " + seedDnsHost);
        }
    }

    private static String readEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value;
    }
}
