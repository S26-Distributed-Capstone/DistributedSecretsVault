package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class SecretSharingServiceTest {

    private SecretSharingService secretSharingService;
    private SecretKey validKey;

    @BeforeEach
    void setUp() {
        secretSharingService = new SecretSharingService();
        validKey = new SecretKey();
        validKey.setOwnerId("user1");
        validKey.setName("my_secret");
    }

    @Test
    void testSplit_NullKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> secretSharingService.split(null, "val", 1, 1));

        SecretKey badKey = new SecretKey();
        assertThrows(IllegalArgumentException.class, () -> secretSharingService.split(badKey, "val", 1, 1));

        badKey.setName("   ");
        assertThrows(IllegalArgumentException.class, () -> secretSharingService.split(badKey, "val", 1, 1));
    }

    @Test
    void testSplit_NullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> secretSharingService.split(validKey, null, 1, 1));
    }

    @Test
    void testSplit_Success() {
        List<SecretPart> parts = secretSharingService.split(validKey, "MySuperSecretValue", 3, 5);
        assertEquals(5, parts.size());

        for (SecretPart part : parts) {
            assertEquals(validKey, part.getKey());
            assertNotNull(part.getShard());
            assertTrue(part.getPartIndex() >= 1 && part.getPartIndex() <= 5);
        }
    }
}
