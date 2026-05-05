package edu.yu.capstone.DistributedSecretsVault.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

/**
 * Integration tests for complete secret storage component of the Distributed Secrets Vault.
 * Includes tests for user and data integration.
 */
@SpringBootTest
public class SecretStorageIntegrationTest {

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @Test
    public void testSecretStorageHappyPath() {

    }

    /**
     * Tests data integration component of secret storage with one Redis instance localized on each node
     */
    @Test
    public void testDataIntegrationHappyPath() {

    }

    /**
     * Test user integration component of secret storage with one PostgreSQL instance distributed across
     * all nodes on the cluster, with replication capabilities to ensure all nodes are aware of users in the system
     */
    @Test
    public void testUserIntegrationHappyPath() {

    }
}
