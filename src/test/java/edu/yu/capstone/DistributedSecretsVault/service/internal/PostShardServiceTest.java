package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PostShardServiceTest {

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private PostShardService postShardService;

    private SecretKey validKey;
    private SecretPartMessage validMessage;
    private String validUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validUser = "user1";
        validKey = new SecretKey(validUser, "secret1");
        validMessage = new SecretPartMessage(validKey, 1L, new byte[] { 1, 2, 3 }, System.currentTimeMillis(), 0);
    }

    @Test
    void testPostShard_Valid() {
        when(secretPartRepository.exists(validKey)).thenReturn(false);

        ResponseEntity<String> response = postShardService.postShard(validMessage, validUser);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Secret created (version: 1)", response.getBody());

        ArgumentCaptor<SecretPart> partCaptor = ArgumentCaptor.forClass(SecretPart.class);
        verify(secretPartRepository).savePart(partCaptor.capture());

        SecretPart capturedPart = partCaptor.getValue();
        assertEquals(validKey, capturedPart.getKey());
        assertEquals(1L, capturedPart.getVersion());
        assertEquals(0, capturedPart.getPartIndex());
        assertEquals(validMessage.getShard(), capturedPart.getShard());
    }

    @Test
    void testPostShard_NullInput() {
        assertThrows(IllegalArgumentException.class, () -> postShardService.postShard(null, validUser));
    }

    @Test
    void testPostShard_NullUser() {
        assertThrows(IllegalArgumentException.class, () -> postShardService.postShard(validMessage, null));
    }

    @Test
    void testPostShard_BlankUser() {
        assertThrows(IllegalArgumentException.class, () -> postShardService.postShard(validMessage, "   "));
    }

    @Test
    void testPostShard_NullShard() {
        validMessage.setShard(null);
        assertThrows(IllegalArgumentException.class, () -> postShardService.postShard(validMessage, validUser));
    }

    @Test
    void testPostShard_DuplicateSecret() {
        when(secretPartRepository.exists(validKey)).thenReturn(true);

        assertThrows(DuplicateSecretException.class, () -> postShardService.postShard(validMessage, validUser));
    }
}
