package edu.yu.capstone.DistributedSecretsVault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DistributedSecretsVaultApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void mainMethodRuns() {
		DistributedSecretsVaultApplication.main(new String[] {
				"--server.port=0",
				"--spring.redis.port=6379" // assuming redis might be needed, but we can just let it try standard properties
		});
	}
}
