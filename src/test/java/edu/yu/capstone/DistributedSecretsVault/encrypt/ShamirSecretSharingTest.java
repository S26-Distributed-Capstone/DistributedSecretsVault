package edu.yu.capstone.DistributedSecretsVault.encrypt;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Shamir Secret Sharing implementation.
 * Verifies split/reconstruct round-trips, threshold behavior, and edge cases.
 */
@Tag("unit")
public class ShamirSecretSharingTest {

    private ShamirSecretSharing shamir;

    @BeforeEach
    void setUp() {
        shamir = new ShamirSecretSharing();
    }

    // ── Round-trip: split and reconstruct ────────────────────────────────

    @Test
    void testSplitAndReconstructRoundTrip_3of5() {
        byte[] secret = "my-database-password".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret, 5, 3);

        assertEquals(5, parts.size());

        // Use exactly 3 shards (the threshold) for reconstruction
        Map<Integer, byte[]> subset = new HashMap<>();
        int count = 0;
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            subset.put(entry.getKey(), entry.getValue());
            if (++count == 3) break;
        }

        byte[] reconstructed = shamir.reconstruct(subset);
        assertArrayEquals(secret, reconstructed);
    }

    @Test
    void testSplitAndReconstructRoundTrip_2of3() {
        byte[] secret = "api-key-12345".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret, 3, 2);

        assertEquals(3, parts.size());

        // Use exactly 2 shards
        Map<Integer, byte[]> subset = new HashMap<>();
        int count = 0;
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            subset.put(entry.getKey(), entry.getValue());
            if (++count == 2) break;
        }

        byte[] reconstructed = shamir.reconstruct(subset);
        assertArrayEquals(secret, reconstructed);
    }

    @Test
    void testSplitAndReconstructWithAllShards() {
        byte[] secret = "full-shard-test".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret, 3, 2);

        // Use all shards (more than threshold)
        byte[] reconstructed = shamir.reconstruct(parts);
        assertArrayEquals(secret, reconstructed);
    }

    // ── Threshold = 1 (raw copies) ──────────────────────────────────────

    @Test
    void testThresholdOneReturnsRawCopies() {
        byte[] secret = "simple-secret".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret, 3, 1);

        assertEquals(3, parts.size());
        for (byte[] shard : parts.values()) {
            assertArrayEquals(secret, shard, "Threshold=1 should return raw copies");
        }
    }

    @Test
    void testThresholdOneReconstructsFromSingleShard() {
        byte[] secret = "single-shard-test".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret, 3, 1);

        // Any single shard should reconstruct the secret
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            Map<Integer, byte[]> singleShard = Map.of(entry.getKey(), entry.getValue());
            byte[] reconstructed = shamir.reconstruct(singleShard);
            assertArrayEquals(secret, reconstructed);
        }
    }

    // ── Single shard reconstruction ─────────────────────────────────────

    @Test
    void testReconstructFromSingleShardMap() {
        byte[] shard = "raw-shard-data".getBytes(StandardCharsets.UTF_8);
        Map<Integer, byte[]> parts = Map.of(1, shard);

        byte[] result = shamir.reconstruct(parts);

        assertArrayEquals(shard, result,
                "Single-shard reconstruct should return the shard as-is");
    }

    // ── Large secret data ───────────────────────────────────────────────

    @Test
    void testLargeSecretRoundTrip() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("abcdefghijklmnop"); // 16KB total
        }
        byte[] secret = sb.toString().getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret, 5, 3);
        assertEquals(5, parts.size());

        // Reconstruct with 3 shards
        Map<Integer, byte[]> subset = new HashMap<>();
        int count = 0;
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            subset.put(entry.getKey(), entry.getValue());
            if (++count == 3) break;
        }

        byte[] reconstructed = shamir.reconstruct(subset);
        assertArrayEquals(secret, reconstructed);
    }

    // ── Invalid inputs ──────────────────────────────────────────────────

    @Test
    void testSplitRejectsNullSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> shamir.split(null, 3, 2));
    }

    @Test
    void testSplitRejectsZeroParts() {
        assertThrows(IllegalArgumentException.class,
                () -> shamir.split(new byte[]{1}, 0, 1));
    }

    @Test
    void testSplitRejectsZeroThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> shamir.split(new byte[]{1}, 3, 0));
    }

    @Test
    void testSplitRejectsThresholdGreaterThanParts() {
        assertThrows(IllegalArgumentException.class,
                () -> shamir.split(new byte[]{1}, 2, 5));
    }

    @Test
    void testSplitRejectsNegativeParts() {
        assertThrows(IllegalArgumentException.class,
                () -> shamir.split(new byte[]{1}, -1, 1));
    }

    @Test
    void testReconstructRejectsNullParts() {
        assertThrows(IllegalArgumentException.class,
                () -> shamir.reconstruct(null));
    }

    @Test
    void testReconstructRejectsEmptyParts() {
        assertThrows(IllegalArgumentException.class,
                () -> shamir.reconstruct(Map.of()));
    }

    // ── Edge case: n = k (all shards required) ─────────────────────────

    @Test
    void testSplitAndReconstructWhenThresholdEqualsTotalParts() {
        byte[] secret = "all-shards-needed".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret, 3, 3);

        assertEquals(3, parts.size());

        byte[] reconstructed = shamir.reconstruct(parts);
        assertArrayEquals(secret, reconstructed);
    }
}
