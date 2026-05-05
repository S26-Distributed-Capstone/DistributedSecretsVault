package edu.yu.capstone.DistributedSecretsVault.service.secret;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DeleteSecretServiceTest {

    @Mock
    private SecretService secretService;

    @InjectMocks
    private DeleteSecretService deleteSecretService;

    @Test
    void testExecuteThrowsWhenInputNull() {
        assertThrows(IllegalArgumentException.class, () -> deleteSecretService.execute(null));
    }

    @Test
    void testExecuteThrowsWhenUserNull() {
        DeleteSecretRequest request = new DeleteSecretRequest();
        assertThrows(IllegalArgumentException.class, () -> deleteSecretService.execute(request));
    }

    @Test
    void testExecuteThrowsWhenUserBlank() {
        DeleteSecretRequest request = new DeleteSecretRequest();
        request.setUser("   ");
        assertThrows(IllegalArgumentException.class, () -> deleteSecretService.execute(request));
    }

    @Test
    void testExecuteSuccess() {
        DeleteSecretRequest request = new DeleteSecretRequest();
        request.setUser("user1");
        request.setDeleteName("secret1");

        ResponseEntity<Void> response = deleteSecretService.execute(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(secretService).deleteSecret(any(SecretKey.class));
    }
}
