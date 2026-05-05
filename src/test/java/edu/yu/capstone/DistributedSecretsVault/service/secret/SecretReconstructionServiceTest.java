package edu.yu.capstone.DistributedSecretsVault.service.secret;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SecretReconstructionServiceTest {

    private SecretReconstructionService secretReconstructionService;

    @BeforeEach
    void setUp() {
        secretReconstructionService = new SecretReconstructionService();
    }

    @Test
    void testReconstructThrowsWhenPartsNull() {
        assertThrows(InsufficientShardsException.class, () -> secretReconstructionService.reconstruct(null));
    }

    @Test
    void testReconstructThrowsWhenPartsEmpty() {
        assertThrows(InsufficientShardsException.class, () -> secretReconstructionService.reconstruct(Collections.emptyList()));
    }

    @Test
    void testReconstructThrowsWhenAllShardsNull() {
        List<SecretPart> parts = new ArrayList<>();
        SecretPart part = new SecretPart();
        parts.add(part);

        assertThrows(InsufficientShardsException.class, () -> secretReconstructionService.reconstruct(parts));
    }

    @Test
    void testReconstructSkipsNullParts() {
        List<SecretPart> parts = new ArrayList<>();
        parts.add(null);

        assertThrows(InsufficientShardsException.class, () -> secretReconstructionService.reconstruct(parts));
    }

    @Test
    void testReconstructSuccess() {
        SecretSharingService sharingService = new SecretSharingService();
        edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey key = new edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey();
        key.setName("test");
        List<SecretPart> parts = sharingService.split(key, "my-secret", 2, 3);

        // Use 2 parts to reconstruct
        List<SecretPart> reconstructParts = parts.subList(0, 2);
        String secret = secretReconstructionService.reconstruct(reconstructParts);
        assertEquals("my-secret", secret);
    }
}
