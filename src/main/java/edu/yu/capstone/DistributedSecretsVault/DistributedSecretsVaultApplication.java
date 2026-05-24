package edu.yu.capstone.DistributedSecretsVault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Distributed Secrets Vault application.
 * <p>
 * This Spring Boot application provides a distributed, fault-tolerant secrets
 * management system that uses <b>Shamir's Secret Sharing</b> to split secrets
 * into shards distributed across multiple nodes. It relies on a two-phase
 * prepare/commit protocol coordinated through Kafka, with ScaleCube for
 * service discovery and Redis for shard storage.
 * <p>
 * Key annotations:
 * <ul>
 *   <li>{@code @ConfigurationPropertiesScan} — auto-discovers configuration
 *       property classes such as {@link edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig},
 *       {@link edu.yu.capstone.DistributedSecretsVault.config.NetworkConfig}, etc.</li>
 *   <li>{@code @EnableScheduling} — enables scheduled tasks such as
 *       {@link edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer#evictExpired()}.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DistributedSecretsVaultApplication {

	/**
	 * Launches the Spring Boot application.
	 *
	 * @param args command-line arguments forwarded to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(DistributedSecretsVaultApplication.class, args);
	}

}
