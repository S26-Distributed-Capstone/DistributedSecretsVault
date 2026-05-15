package edu.yu.capstone.DistributedSecretsVault.service.secret;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalPostService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class PostSecretServiceTest {

    @Mock
    private InternalPostService internalPostService;

    @InjectMocks
    private PostSecretService postSecretService;

    @Test
    void testExecuteThrowsWhenInputNull() {
        assertThrows(IllegalArgumentException.class, () -> postSecretService.execute(null));
    }

    @Test
    void testExecuteThrowsWhenUserNull() {
        PostSecretRequest request = new PostSecretRequest();
        request.setSecretName("name");
        assertThrows(IllegalArgumentException.class, () -> postSecretService.execute(request));
    }

    @Test
    void testExecuteThrowsWhenUserBlank() {
        PostSecretRequest request = new PostSecretRequest();
        request.setUser("   ");
        request.setSecretName("name");
        assertThrows(IllegalArgumentException.class, () -> postSecretService.execute(request));
    }

    @Test
    void testExecuteSuccess() {
        PostSecretRequest request = new PostSecretRequest();
        request.setUser("user1");
        request.setSecretName("secret1");
        request.setSecretValue("value1");

        SecretKey key = new SecretKey(request.getUser(), request.getSecretName());
        SecretVersion version = new SecretVersion(key, 1L, System.currentTimeMillis());

        when(internalPostService.postAcrossCluster(any(SecretKey.class), eq("value1"))).thenReturn(version);

        ResponseEntity<String> response = postSecretService.execute(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Secret created (version: 1)", response.getBody());
        verify(internalPostService).postAcrossCluster(any(SecretKey.class), eq("value1"));
    }
}
