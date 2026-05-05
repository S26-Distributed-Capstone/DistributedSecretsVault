package edu.yu.capstone.DistributedSecretsVault.service.cluster;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

@SpringBootTest
public class HeartbeatServiceTest {

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @Test
    public void testHeartbeatServiceHappyPath() {

    }
}
