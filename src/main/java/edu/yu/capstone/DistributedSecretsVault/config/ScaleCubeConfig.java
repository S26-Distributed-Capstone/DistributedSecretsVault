package edu.yu.capstone.DistributedSecretsVault.config;

import io.scalecube.services.Microservices;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.net.Address;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;

@Configuration
public class ScaleCubeConfig {

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
        String seedsStr = System.getenv("SEEDS");

        // 2. Set safe defaults for local development (outside k8s)
        if (podIp == null || podIp.isEmpty()) {
            podIp = "localhost";
        }
        
        int parsedPort = 4801;
        if (clusterPortStr != null && !clusterPortStr.isEmpty()) {
            parsedPort = Integer.parseInt(clusterPortStr);
        }
        final int clusterPort = parsedPort;
        
        List<String> parsedSeeds = List.of();
        if (seedsStr != null && !seedsStr.isEmpty()) {
            parsedSeeds = Arrays.asList(seedsStr.split(","));
        }
        final List<String> seeds = parsedSeeds;

        System.out.println("Starting ScaleCube node on " + podIp + ":" + clusterPort);
        System.out.println("Using seeds: " + seeds);

        // 3. Build and start the ScaleCube microservices node
        this.microservices = Microservices.builder()
                .externalHost(podIp)
                .externalPort(clusterPort)
                // Implement our nested interface directly inline as an anonymous class!
                .services((PingService) request -> Mono.just("Pong from " + System.getenv("NODE_NAME")))
                .discovery("scalecube-demo", endpoint -> new ScalecubeServiceDiscovery(endpoint)
                        .options(opts -> opts
                                .memberAlias(System.getenv("NODE_NAME")))
                        .membership(cfg -> cfg.seedMembers(seeds.stream().map(Address::from).toArray(Address[]::new)))
                        .transport(cfg -> cfg.port(clusterPort))
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
}
