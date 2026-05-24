package edu.yu.capstone.DistributedSecretsVault.config;

import io.scalecube.services.Microservices;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.net.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring configuration that bootstraps a ScaleCube Microservices node for
 * cluster membership and service discovery.
 * <p>
 * Each application instance joins a ScaleCube cluster by resolving seed
 * members via DNS (headless Kubernetes service) and advertising its pod IP.
 * The node exposes a simple {@link PingService} to verify cluster connectivity.
 * <p>
 * Active only when the {@code test} profile is <b>not</b> active or the
 * {@code scalecube-single-node} profile <b>is</b> active.
 *
 * @see edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient
 * @see edu.yu.capstone.DistributedSecretsVault.health.ScaleCubeHealthIndicator
 */
@Configuration
@Profile("!test | scalecube-single-node")
public class ScaleCubeConfig {
    private static final Logger log = LoggerFactory.getLogger(ScaleCubeConfig.class);
    private static final int DEFAULT_CLUSTER_PORT = 4801;
    private static final int DEFAULT_DNS_RESOLVE_MAX_ATTEMPTS = 5;
    private static final long DEFAULT_DNS_RESOLVE_RETRY_DELAY_MS = 1000L;

    /**
     * Functional interface abstracting DNS resolution for testability.
     */
    @FunctionalInterface
    interface DnsResolver {
        /**
         * Resolve all IP addresses for the given hostname.
         *
         * @param host the hostname to resolve
         * @return array of resolved {@link InetAddress}es
         * @throws UnknownHostException if the host cannot be resolved
         */
        InetAddress[] resolveAllByName(String host) throws UnknownHostException;
    }

    // ScaleCube requires an interface to generate network proxies!
    // We keep it nested here so we don't clutter the project with extra files.
    @Service
    public interface PingService {
        @ServiceMethod
        Mono<String> ping(String request);
    }

    private Microservices microservices;

    /**
     * Creates and starts a ScaleCube {@link Microservices} node.
     * <p>
     * The method reads environment variables ({@code POD_IP}, {@code CLUSTER_PORT},
     * {@code SEED_DNS_HOST}, {@code SEED_DNS_PORT}, {@code NODE_NAME}) to configure
     * the node's address, cluster port, and seed members. Falls back to safe
     * defaults for local (non-Kubernetes) development.
     *
     * @return a fully started {@link Microservices} instance
     * @throws IllegalStateException if {@code SEED_DNS_HOST} is not set or DNS
     *         resolution fails after all retry attempts
     */
    @Bean
    public Microservices scalecubeMicroservices() {
        // 1. Read environment variables mapped from the k8s manifest
        String podIp = readEnvOrSystemProperty("POD_IP");
        String clusterPortStr = readEnvOrSystemProperty("CLUSTER_PORT");
        String seedDnsHost = readEnvOrSystemProperty("SEED_DNS_HOST");
        String seedDnsPortStr = readEnvOrSystemProperty("SEED_DNS_PORT");
        String nodeName = readEnvOrSystemProperty("NODE_NAME");

        // 2. Set safe defaults for local development (outside k8s)
        if (podIp == null || podIp.isEmpty()) {
            podIp = "localhost";
        }

        int clusterPort = DEFAULT_CLUSTER_PORT;
        if (clusterPortStr != null && !clusterPortStr.isEmpty()) {
            clusterPort = Integer.parseInt(clusterPortStr);
        }
        final int resolvedClusterPort = clusterPort;

        int seedDnsPort = clusterPort;
        if (seedDnsPortStr != null && !seedDnsPortStr.isEmpty()) {
            seedDnsPort = Integer.parseInt(seedDnsPortStr);
        }

        if (nodeName == null || nodeName.isBlank()) {
            nodeName = "local-node";
        }
        final String resolvedNodeName = nodeName;

        if (seedDnsHost == null || seedDnsHost.isBlank()) {
            throw new IllegalStateException("SEED_DNS_HOST must be set for DNS-based ScaleCube bootstrap");
        }
        seedDnsHost = seedDnsHost.trim();

        Address[] seedMembers = resolveSeedMembersWithRetry(seedDnsHost, seedDnsPort,
                DEFAULT_DNS_RESOLVE_MAX_ATTEMPTS, DEFAULT_DNS_RESOLVE_RETRY_DELAY_MS, InetAddress::getAllByName);

        log.info("Starting ScaleCube node on {}:{}", podIp, resolvedClusterPort);
        log.info("ScaleCube DNS seed host: {}:{}", seedDnsHost, seedDnsPort);
        log.info("Resolved ScaleCube seed endpoints: {}", seedMembers.length);

        // 3. Build and start the ScaleCube microservices node
        this.microservices = Microservices.builder()
                .externalHost(podIp)
                .externalPort(resolvedClusterPort)
                // Implement our nested interface directly inline as an anonymous class!
                .services((PingService) request -> Mono.just("Pong from " + resolvedNodeName))
                .discovery("scalecube-demo", endpoint -> new ScalecubeServiceDiscovery(endpoint)
                        .options(opts -> opts
                                .memberAlias(resolvedNodeName))
                        .membership(cfg -> cfg.seedMembers(seedMembers))
                        .transport(cfg -> cfg.port(resolvedClusterPort))
                )
                .transport(RSocketServiceTransport::new)
                .startAwait();

        log.info("ScaleCube node started successfully. Node ID: {}", microservices.id());
        
        return microservices;
    }

    /**
     * Gracefully shuts down the ScaleCube node on application context closure.
     */
    @PreDestroy
    public void stopScaleCube() {
        if (microservices != null) {
            log.info("Shutting down ScaleCube node");
            microservices.shutdown().block();
        }
    }

    /**
     * Attempts DNS resolution with retry logic.
     *
     * @param seedDnsHost   the hostname to resolve (e.g. headless k8s service)
     * @param seedDnsPort   port each seed member listens on
     * @param maxAttempts   maximum number of resolution attempts
     * @param retryDelayMs  delay (ms) between retries
     * @param dnsResolver   abstraction for {@link InetAddress#getAllByName}
     * @return array of resolved ScaleCube {@link Address}es
     * @throws IllegalStateException if all attempts fail
     */
    static Address[] resolveSeedMembersWithRetry(String seedDnsHost, int seedDnsPort, int maxAttempts,
            long retryDelayMs, DnsResolver dnsResolver) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return resolveSeedMembers(seedDnsHost, seedDnsPort, dnsResolver);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt == maxAttempts) {
                    break;
                }
                log.warn("Failed to resolve ScaleCube DNS seeds on attempt {}/{} for host {}, retrying",
                        attempt, maxAttempts, seedDnsHost);
                sleepQuietly(retryDelayMs);
            }
        }
        throw new IllegalStateException(
                "Unable to resolve ScaleCube seed members from DNS host " + seedDnsHost + " after " + maxAttempts
                        + " attempts",
                lastFailure);
    }

    /**
     * Resolves all IP addresses for the given DNS host and converts them to
     * ScaleCube {@link Address}es.
     *
     * @param seedDnsHost the hostname to resolve
     * @param seedDnsPort port to pair with each resolved IP
     * @param dnsResolver abstraction for DNS lookups
     * @return array of ScaleCube addresses
     * @throws IllegalStateException if DNS returns no addresses or lookup fails
     */
    static Address[] resolveSeedMembers(String seedDnsHost, int seedDnsPort, DnsResolver dnsResolver) {
        try {
            InetAddress[] addresses = dnsResolver.resolveAllByName(seedDnsHost);
            if (addresses == null || addresses.length == 0) {
                throw new IllegalStateException("DNS host " + seedDnsHost + " resolved with no addresses");
            }
            List<Address> seedMembers = new ArrayList<>();
            for (InetAddress inetAddress : addresses) {
                seedMembers.add(Address.from(inetAddress.getHostAddress() + ":" + seedDnsPort));
            }
            return seedMembers.toArray(Address[]::new);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException("DNS lookup failed for host " + seedDnsHost, ex);
        }
    }

    /**
     * Sleeps for the specified duration, restoring the interrupt flag if interrupted.
     */
    private static void sleepQuietly(long retryDelayMs) {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry DNS seed resolution", interruptedException);
        }
    }

    /**
     * Reads a value from environment variables, falling back to system properties.
     *
     * @param key the environment variable / system property name
     * @return the value, or {@code null} if not set
     */
    private static String readEnvOrSystemProperty(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value;
    }
}
