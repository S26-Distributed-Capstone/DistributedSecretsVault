package edu.yu.capstone.DistributedSecretsVault.service.secret;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class GetSecretServiceTest {

    @Mock
    private SecretService secretService;

    @InjectMocks
    private GetSecretService getSecretService;

    @Test
    void testValidateThrowsWhenUserNull() {
        assertThrows(IllegalArgumentException.class, () -> getSecretService.execute(null, "secret1"));
    }

    @Test
    void testValidateThrowsWhenUserBlank() {
        assertThrows(IllegalArgumentException.class, () -> getSecretService.execute("   ", "secret1"));
    }

    @Test
    void testValidateThrowsWhenSecretNameNull() {
        assertThrows(IllegalArgumentException.class, () -> getSecretService.execute("user1", null));
    }

    @Test
    void testValidateThrowsWhenSecretNameBlank() {
        assertThrows(IllegalArgumentException.class, () -> getSecretService.execute("user1", "   "));
    }

    @Test
    void testExecuteNoVersionSuccess() {
        when(secretService.getSecret(any(SecretKey.class), eq(null))).thenReturn("value1");
        ResponseEntity<String> response = getSecretService.execute("user1", "secret1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("value1", response.getBody());
    }

    @Test
    void testExecuteWithVersionSuccess() {
        when(secretService.getSecret(any(SecretKey.class), eq(2L))).thenReturn("value2");
        ResponseEntity<String> response = getSecretService.execute("user1", "secret1", 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("value2", response.getBody());
    }

    @Test
    void testExecuteAllSuccess() {
        Map<Long, String> expected = new HashMap<>();
        expected.put(1L, "v1");
        expected.put(2L, "v2");
        when(secretService.getAllVersions(any(SecretKey.class))).thenReturn(expected);

        ResponseEntity<Map<Long, String>> response = getSecretService.executeAll("user1", "secret1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
