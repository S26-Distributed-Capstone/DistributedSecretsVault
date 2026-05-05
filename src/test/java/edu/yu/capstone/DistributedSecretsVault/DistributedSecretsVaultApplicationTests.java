package edu.yu.capstone.DistributedSecretsVault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

@SpringBootTest
class DistributedSecretsVaultApplicationTests {

	@MockitoBean
	private DataSource dataSource;

	@MockitoBean
	private RedisConnectionFactory redisConnectionFactory;

	@MockitoBean
	private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

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
