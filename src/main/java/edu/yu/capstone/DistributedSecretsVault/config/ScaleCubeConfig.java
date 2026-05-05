package edu.yu.capstone.DistributedSecretsVault.config;

import io.scalecube.services.Microservices;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.net.Address;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ScaleCubeConfig {
    @Value("${scalecube.cluster.default-port}")
    private int defaultClusterPort;

    @Value("${scalecube.dns.resolve.max-attempts}")
    private int dnsResolveMaxAttempts;

    @Value("${scalecube.dns.resolve.retry-delay-ms}")
    private long dnsResolveRetryDelayMs;

    @FunctionalInterface
    interface DnsResolver {
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

    @Bean
    public Microservices scalecubeMicroservices() {
        // 1. Read environment variables mapped from the k8s manifest
        String podIp = System.getenv("POD_IP");
        String clusterPortStr = System.getenv("CLUSTER_PORT");
        String seedDnsHost = System.getenv("SEED_DNS_HOST");
        String seedDnsPortStr = System.getenv("SEED_DNS_PORT");
        String nodeName = System.getenv("NODE_NAME");

        // 2. Set safe defaults for local development (outside k8s)
        if (podIp == null || podIp.isEmpty()) {
            podIp = "localhost";
        }

        int clusterPort = defaultClusterPort;
        if (clusterPortStr != null && !clusterPortStr.isEmpty()) {
            clusterPort = Integer.parseInt(clusterPortStr);
        }
        final int resolvedClusterPort = clusterPort;

        int seedDnsPort = clusterPort;
        if (seedDnsPortStr != null && !seedDnsPortStr.isEmpty()) {
            seedDnsPort = Integer.parseInt(seedDnsPortStr);
        }

        if (seedDnsHost == null || seedDnsHost.isBlank()) {
            throw new IllegalStateException("SEED_DNS_HOST must be set for DNS-based ScaleCube bootstrap");
        }
        seedDnsHost = seedDnsHost.trim();

        Address[] seedMembers = resolveSeedMembersWithRetry(seedDnsHost, seedDnsPort, dnsResolveMaxAttempts,
                dnsResolveRetryDelayMs, InetAddress::getAllByName);

        System.out.println("Starting ScaleCube node on " + podIp + ":" + resolvedClusterPort);
        System.out.println("ScaleCube DNS seed host: " + seedDnsHost + ":" + seedDnsPort);
        System.out.println("Resolved ScaleCube seed endpoints: " + seedMembers.length);

        // 3. Build and start the ScaleCube microservices node
        this.microservices = Microservices.builder()
                .externalHost(podIp)
                .externalPort(resolvedClusterPort)
                // Implement our nested interface directly inline as an anonymous class!
                .services((PingService) request -> Mono.just("Pong from " + nodeName))
                .discovery("scalecube-demo", endpoint -> new ScalecubeServiceDiscovery(endpoint)
                        .options(opts -> opts
                                .memberAlias(nodeName))
                        .membership(cfg -> cfg.seedMembers(seedMembers))
                        .transport(cfg -> cfg.port(resolvedClusterPort))
                )
                .transport(RSocketServiceTransport::new)
                .startAwait();

        System.out.println("ScaleCube node started successfully. Node ID: " + microservices.id());
        
        return microservices;
    }

    @PreDestroy
    public void stopScaleCube() {
        if (microservices != null) {
            System.out.println("Shutting down ScaleCube node...");
            microservices.shutdown().block();
        }
    }

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
                System.out.println("Failed to resolve ScaleCube DNS seeds on attempt " + attempt + "/" + maxAttempts
                        + " for host " + seedDnsHost + ", retrying...");
                sleepQuietly(retryDelayMs);
            }
        }
        throw new IllegalStateException(
                "Unable to resolve ScaleCube seed members from DNS host " + seedDnsHost + " after " + maxAttempts
                        + " attempts",
                lastFailure);
    }

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

    private static void sleepQuietly(long retryDelayMs) {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry DNS seed resolution", interruptedException);
        }
    }
}
