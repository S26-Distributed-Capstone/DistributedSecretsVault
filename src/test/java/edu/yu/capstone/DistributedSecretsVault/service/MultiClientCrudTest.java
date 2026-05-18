package edu.yu.capstone.DistributedSecretsVault.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartResponse;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartsResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretReconstructionService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretService;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

/**
 * Tests simulating CRUD operations from multiple independent clients/users.
 * Verifies caller-scoped isolation: each user can only see and modify their own
 * secrets.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class MultiClientCrudTest {

        @Mock
        private SecretPartRepository secretPartRepository;

        @Mock
        private SecretSharingService secretSharingService;

        @Mock
        private SecretReconstructionService secretReconstructionService;

        @Mock
        private NodeClient nodeClient;

        private ClusterConfig clusterConfig;
        private SecretService service;

        @BeforeEach
        void setUp() {
                clusterConfig = new ClusterConfig();
                clusterConfig.setTotalNodes(3);
                clusterConfig.setThresholdK(2);
                service = new SecretService(secretPartRepository, secretSharingService,
                                secretReconstructionService, nodeClient, clusterConfig);
                lenient().when(nodeClient.resolvePeerUrls()).thenReturn(List.of());
        }

        // ── Two users, different keys ───────────────────────────────────────

        @Test
        void testTwoUsersCreateDifferentKeys() {
                SecretKey aliceKey = new SecretKey("alice", "db-password");
                SecretKey bobKey = new SecretKey("bob", "api-key");

                when(secretPartRepository.exists(aliceKey)).thenReturn(false);
                when(secretPartRepository.exists(bobKey)).thenReturn(false);
                when(secretSharingService.split(eq(aliceKey), anyString(), anyInt(), anyInt()))
                                .thenReturn(parts(aliceKey));
                when(secretSharingService.split(eq(bobKey), anyString(), anyInt(), anyInt()))
                                .thenReturn(parts(bobKey));

                SecretVersion aliceVersion = service.storeSecret(aliceKey, "alice-secret");
                SecretVersion bobVersion = service.storeSecret(bobKey, "bob-secret");

                assertEquals(1L, aliceVersion.getVersion());
                assertEquals(1L, bobVersion.getVersion());
                assertEquals("alice", aliceVersion.getKey().getOwnerId());
                assertEquals("bob", bobVersion.getKey().getOwnerId());

                // Verify strict isolation during save
                verify(secretPartRepository, times(3)).savePart(argThat(p -> p.getKey().equals(aliceKey)));
                verify(secretPartRepository, times(3)).savePart(argThat(p -> p.getKey().equals(bobKey)));
        }

        // ── Two users, same key name (isolation by ownerId) ─────────────────

        @Test
        void testTwoUsersCreateSameKeyNameBothSucceed() {
                SecretKey aliceKey = new SecretKey("alice", "db-password");
                SecretKey bobKey = new SecretKey("bob", "db-password");

                // Different SecretKey objects (different ownerId) → both can exist
                when(secretPartRepository.exists(aliceKey)).thenReturn(false);
                when(secretPartRepository.exists(bobKey)).thenReturn(false);
                when(secretSharingService.split(eq(aliceKey), anyString(), anyInt(), anyInt()))
                                .thenReturn(parts(aliceKey));
                when(secretSharingService.split(eq(bobKey), anyString(), anyInt(), anyInt()))
                                .thenReturn(parts(bobKey));

                SecretVersion aliceVersion = service.storeSecret(aliceKey, "alice-secret");
                SecretVersion bobVersion = service.storeSecret(bobKey, "bob-secret");

                assertEquals(1L, aliceVersion.getVersion());
                assertEquals(1L, bobVersion.getVersion());
                assertNotEquals(aliceVersion.getKey().getOwnerId(), bobVersion.getKey().getOwnerId());

                // Verify strict isolation during save even with same secret name
                verify(secretPartRepository, times(3)).savePart(argThat(p -> p.getKey().equals(aliceKey)));
                verify(secretPartRepository, times(3)).savePart(argThat(p -> p.getKey().equals(bobKey)));
        }

        // ── User A creates, User B tries to read A's secret ─────────────────

        @Test
        void testUserCannotReadOtherUsersSecret() {
                SecretKey aliceKey = new SecretKey("alice", "db-password");
                SecretKey bobQueryForAlice = new SecretKey("bob", "db-password");

                // Simulate that Alice has successfully stored this secret
                // Bob's query uses a different SecretKey (due to ownerId), so it should not resolve to Alice's
                when(secretPartRepository.findLatest(bobQueryForAlice)).thenReturn(Optional.empty());

                assertThrows(SecretNotFoundException.class,
                                () -> service.getSecret(bobQueryForAlice, null));
        }

        // ── Full lifecycle per user ─────────────────────────────────────────

        @Test
        void testFullLifecycleForTwoUsersIndependently() {
                SecretKey aliceKey = new SecretKey("alice", "api-key");
                SecretKey bobKey = new SecretKey("bob", "api-key");

                // -- Alice: create
                when(secretPartRepository.exists(aliceKey)).thenReturn(false);
                when(secretSharingService.split(eq(aliceKey), eq("alice-v1"), anyInt(), anyInt()))
                                .thenReturn(parts(aliceKey));
                SecretVersion aliceV1 = service.storeSecret(aliceKey, "alice-v1");
                assertEquals(1L, aliceV1.getVersion());

                // -- Bob: create (same key name, different owner)
                when(secretPartRepository.exists(bobKey)).thenReturn(false);
                when(secretSharingService.split(eq(bobKey), eq("bob-v1"), anyInt(), anyInt()))
                                .thenReturn(parts(bobKey));
                SecretVersion bobV1 = service.storeSecret(bobKey, "bob-v1");
                assertEquals(1L, bobV1.getVersion());

                // -- Alice: read back
                when(secretPartRepository.exists(aliceKey)).thenReturn(true);
                SecretPart alicePart = new SecretPart(aliceKey, 1L, 1, new byte[] { 1 });
                SecretPart alicePeerPart = new SecretPart(aliceKey, 1L, 2, new byte[] { 2 });
                when(secretPartRepository.findLatest(aliceKey)).thenReturn(Optional.of(alicePart));
                when(nodeClient.resolvePeerUrls()).thenReturn(List.of("http://peer1:8080"));
                when(nodeClient.fetchSecretPart("http://peer1:8080", aliceKey, null))
                                .thenReturn(SecretPartResponse.found("http://peer1:8080", alicePeerPart));
                when(secretReconstructionService.reconstruct(anyList())).thenReturn("alice-v1");
                assertEquals("alice-v1", service.getSecret(aliceKey, null));

                // -- Alice: update
                when(secretSharingService.split(eq(aliceKey), eq("alice-v2"), anyInt(), anyInt()))
                                .thenReturn(parts(aliceKey));
                when(secretPartRepository.updatePart(any(SecretPart.class))).thenReturn(true);
                SecretVersion aliceV2 = service.updateSecret(aliceKey, "alice-v2");
                assertEquals(2L, aliceV2.getVersion());

                // -- Alice: get all versions
                when(secretPartRepository.listVersions(aliceKey)).thenReturn(List.of(1L, 2L));
                SecretPart alicePart2 = new SecretPart(aliceKey, 2L, 1, new byte[] { 2 });
                SecretPart alicePeerPart2 = new SecretPart(aliceKey, 2L, 2, new byte[] { 3 });
                when(secretPartRepository.findPart(aliceKey, 1L)).thenReturn(Optional.of(alicePart));
                when(secretPartRepository.findPart(aliceKey, 2L)).thenReturn(Optional.of(alicePart2));
                when(nodeClient.fetchAllSecretParts("http://peer1:8080", aliceKey))
                                .thenReturn(SecretPartsResponse.found("http://peer1:8080", Map.of(
                                                1L, alicePeerPart,
                                                2L, alicePeerPart2)));
                when(secretReconstructionService.reconstruct(anyList()))
                                .thenReturn("alice-v1", "alice-v2");
                Map<Long, String> allVersions = service.getAllVersions(aliceKey);
                assertEquals(2, allVersions.size());

                // -- Alice: delete
                service.deleteSecret(aliceKey);
                verify(secretPartRepository).deleteParts(aliceKey);
                verify(secretPartRepository, never()).deleteParts(bobKey); // Verify Bob was untouched

                // -- Alice: confirm gone
                when(secretPartRepository.findLatest(aliceKey)).thenReturn(Optional.empty());
                when(nodeClient.fetchSecretPart("http://peer1:8080", aliceKey, null))
                                .thenReturn(SecretPartResponse.rejected("http://peer1:8080", 404, "not found"));
                assertThrows(SecretNotFoundException.class,
                                () -> service.getSecret(aliceKey, null));

                // -- Bob's secret should be unaffected
                SecretPart bobPart = new SecretPart(bobKey, 1L, 1, new byte[] { 1 });
                SecretPart bobPeerPart = new SecretPart(bobKey, 1L, 2, new byte[] { 2 });
                when(secretPartRepository.findLatest(bobKey)).thenReturn(Optional.of(bobPart));
                when(nodeClient.fetchSecretPart("http://peer1:8080", bobKey, null))
                                .thenReturn(SecretPartResponse.found("http://peer1:8080", bobPeerPart));
                when(secretReconstructionService.reconstruct(anyList())).thenReturn("bob-v1");
                assertEquals("bob-v1", service.getSecret(bobKey, null));
        }

        // ── Independent updates don't cross-contaminate ─────────────────────

        @Test
        void testIndependentUpdatesNoCrossContamination() {
                SecretKey aliceKey = new SecretKey("alice", "shared-name");
                SecretKey bobKey = new SecretKey("bob", "shared-name");

                // Both exist
                when(secretPartRepository.exists(aliceKey)).thenReturn(true);
                when(secretPartRepository.exists(bobKey)).thenReturn(true);

                // Alice updates
                SecretPart alicePart = new SecretPart(aliceKey, 1L, 1, new byte[] { 1 });
                when(secretPartRepository.findLatest(aliceKey)).thenReturn(Optional.of(alicePart));
                when(secretSharingService.split(eq(aliceKey), eq("alice-updated"), anyInt(), anyInt()))
                                .thenReturn(parts(aliceKey));
                when(secretPartRepository.updatePart(any(SecretPart.class))).thenReturn(true);
                SecretVersion aliceUpdated = service.updateSecret(aliceKey, "alice-updated");
                assertEquals(2L, aliceUpdated.getVersion());

                // Verify Alice's update only affected Alice's parts
                verify(secretPartRepository, times(3)).updatePart(argThat(p -> p.getKey().equals(aliceKey)));
                verify(secretPartRepository, never()).updatePart(argThat(p -> p.getKey().equals(bobKey)));

                // Bob deletes — should not affect Alice
                service.deleteSecret(bobKey);
                verify(secretPartRepository).deleteParts(bobKey);
                verify(secretPartRepository, never()).deleteParts(aliceKey);
        }

        private List<SecretPart> parts(SecretKey key) {
                return List.of(
                                new SecretPart(key, null, 1, new byte[] { 1 }),
                                new SecretPart(key, null, 2, new byte[] { 2 }),
                                new SecretPart(key, null, 3, new byte[] { 3 }));
        }
}
