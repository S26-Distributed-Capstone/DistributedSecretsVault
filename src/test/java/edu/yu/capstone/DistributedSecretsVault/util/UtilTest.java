package edu.yu.capstone.DistributedSecretsVault.util;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UtilTest {

    @Test
    public void testClockUtil() {
        long before = System.currentTimeMillis();
        long now = ClockUtil.nowEpochMillis();
        long after = System.currentTimeMillis();
        assertTrue(now >= before && now <= after);
    }

    @Test
    public void testNetworkUtil() {
        assertEquals("localhost:8080", NetworkUtil.toNodeId("localhost", 8080));
        assertEquals("127.0.0.1:9090", NetworkUtil.toNodeId("127.0.0.1", 9090));
    }

    @Test
    public void testSecretKeyGeneratorHappyPath() {
        SecretKey key = SecretKeyGenerator.of("user1", "my-secret");
        assertNotNull(key);
        assertEquals("user1", key.getOwnerId());
        assertEquals("my-secret", key.getName());
    }

    @Test
    public void testSecretKeyGeneratorValidation() {
        assertThrows(IllegalArgumentException.class, () -> SecretKeyGenerator.of(null, "secret"));
        assertThrows(IllegalArgumentException.class, () -> SecretKeyGenerator.of("", "secret"));
        assertThrows(IllegalArgumentException.class, () -> SecretKeyGenerator.of("   ", "secret"));

        assertThrows(IllegalArgumentException.class, () -> SecretKeyGenerator.of("user1", null));
        assertThrows(IllegalArgumentException.class, () -> SecretKeyGenerator.of("user1", ""));
        assertThrows(IllegalArgumentException.class, () -> SecretKeyGenerator.of("user1", "   "));
    }
}
