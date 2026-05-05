package edu.yu.capstone.DistributedSecretsVault.service.secret;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
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
public class PostSecretServiceTest {

    @Mock
    private SecretService secretService;

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

        SecretVersion version = new SecretVersion();
        version.setVersion(1L);

        when(secretService.storeSecret(any(SecretKey.class), eq("value1"))).thenReturn(version);

        ResponseEntity<String> response = postSecretService.execute(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Secret created (version: 1)", response.getBody());
        verify(secretService).storeSecret(any(SecretKey.class), eq("value1"));
    }
}

