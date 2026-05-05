package edu.yu.capstone.DistributedSecretsVault.encrypt;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ShamirSecretSharingTest {

    private final ShamirSecretSharing sss = new ShamirSecretSharing();

    @Test
    public void testShamirSecretSharingHappyPath() {
        byte[] secret = "MySecretKey".getBytes();
        Map<Integer, byte[]> parts = sss.split(secret, 5, 3);
        assertEquals(5, parts.size());

        // Reconstruct with threshold (3)
        Map<Integer, byte[]> thresholdParts = new HashMap<>();
        int count = 0;
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            thresholdParts.put(entry.getKey(), entry.getValue());
            if (++count == 3) break;
        }

        byte[] reconstructed = sss.reconstruct(thresholdParts);
        assertArrayEquals(secret, reconstructed);
    }

    @Test
    public void testSplitValidation() {
        assertThrows(IllegalArgumentException.class, () -> sss.split(null, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> sss.split("sec".getBytes(), 0, 3));
        assertThrows(IllegalArgumentException.class, () -> sss.split("sec".getBytes(), 5, 0));
        assertThrows(IllegalArgumentException.class, () -> sss.split("sec".getBytes(), 5, 6));
    }

    @Test
    public void testSplitThresholdOne() {
        byte[] secret = "SingleKey".getBytes();
        Map<Integer, byte[]> parts = sss.split(secret, 3, 1);
        assertEquals(3, parts.size());

        // All parts should be exactly the secret
        for (byte[] part : parts.values()) {
            assertArrayEquals(secret, part);
        }
    }

    @Test
    public void testReconstructValidation() {
        assertThrows(IllegalArgumentException.class, () -> sss.reconstruct(null));
        assertThrows(IllegalArgumentException.class, () -> sss.reconstruct(new HashMap<>()));
    }

    @Test
    public void testReconstructSinglePart() {
        byte[] secret = "Key".getBytes();
        Map<Integer, byte[]> singlePartMap = new HashMap<>();
        singlePartMap.put(1, secret);

        byte[] reconstructed = sss.reconstruct(singlePartMap);
        assertArrayEquals(secret, reconstructed);
    }

    @Test
    public void testSecretSplitter() {
        SecretSplitter splitter = new SecretSplitter();
        byte[] secret = "Hello".getBytes();
        Map<Integer, byte[]> parts = splitter.split(secret, 3, 2);
        assertEquals(3, parts.size());
    }

    @Test
    public void testSecretReconstructor() {
        SecretSplitter splitter = new SecretSplitter();
        SecretReconstructor reconstructor = new SecretReconstructor();

        byte[] secret = "HelloServer".getBytes();
        Map<Integer, byte[]> parts = splitter.split(secret, 4, 2);

        Map<Integer, byte[]> thresholdParts = new HashMap<>();
        int count = 0;
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            thresholdParts.put(entry.getKey(), entry.getValue());
            if (++count == 2) break;
        }

        byte[] reconstructed = reconstructor.reconstruct(thresholdParts);
        assertArrayEquals(secret, reconstructed);
    }
}
