package edu.yu.capstone.DistributedSecretsVault.service.secret;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
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
public class PutSecretServiceTest {

    @Mock
    private SecretService secretService;

    @InjectMocks
    private PutSecretService putSecretService;

    @Test
    void testExecuteThrowsWhenInputNull() {
        assertThrows(IllegalArgumentException.class, () -> putSecretService.execute(null));
    }

    @Test
    void testExecuteThrowsWhenUserNull() {
        PutSecretRequest request = new PutSecretRequest();
        assertThrows(IllegalArgumentException.class, () -> putSecretService.execute(request));
    }

    @Test
    void testExecuteThrowsWhenUserBlank() {
        PutSecretRequest request = new PutSecretRequest();
        request.setUser("   ");
        assertThrows(IllegalArgumentException.class, () -> putSecretService.execute(request));
    }

    @Test
    void testExecuteSuccess() {
        PutSecretRequest request = new PutSecretRequest();
        request.setUser("user1");
        request.setSecretCurrentName("secret1");
        request.setSecretUpdatedValue("newVal");

        SecretVersion version = new SecretVersion();
        version.setVersion(2L);

        when(secretService.updateSecret(any(SecretKey.class), eq("newVal"))).thenReturn(version);

        ResponseEntity<String> response = putSecretService.execute(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Secret updated (version: 2)", response.getBody());
        verify(secretService).updateSecret(any(SecretKey.class), eq("newVal"));
    }
}
