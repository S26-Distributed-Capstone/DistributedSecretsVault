package edu.yu.capstone.DistributedSecretsVault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DistributedSecretsVaultApplication {

	public static void main(String[] args) {
		SpringApplication.run(DistributedSecretsVaultApplication.class, args);
	}

}
