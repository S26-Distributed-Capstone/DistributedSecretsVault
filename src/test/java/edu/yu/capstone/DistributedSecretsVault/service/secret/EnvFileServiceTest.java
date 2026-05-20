package edu.yu.capstone.DistributedSecretsVault.service.secret;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
public class EnvFileServiceTest {

    @Mock
    private PostSecretService postSecretService;

    @Mock
    private PutSecretService putSecretService;

    @Mock
    private DeleteSecretService deleteSecretService;

    @Mock
    private GetSecretService getSecretService;

    @Mock
    private SecretPartRepository secretPartRepository;

    @InjectMocks
    private EnvFileService envFileService;

    @Test
    void testExecuteProcessesEnvFileOperations() {
        when(secretPartRepository.exists(new SecretKey("user1", "Key1"))).thenReturn(false);
        when(secretPartRepository.exists(new SecretKey("user1", "Key2"))).thenReturn(true);
        when(secretPartRepository.exists(new SecretKey("user1", "Key3"))).thenReturn(true);
        when(getSecretService.getVersion("user1", "Key4", null)).thenReturn(ResponseEntity.ok("fetched"));

        ResponseEntity<String> response = envFileService.execute("user1", """
                Key1=new:val
                Key2=update:next:with:colons
                Key4=get
                Key3=delete
                """);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Key1=val\nKey2=next:with:colons\nKey4=fetched", response.getBody());

        ArgumentCaptor<PostSecretRequest> postCaptor = ArgumentCaptor.forClass(PostSecretRequest.class);
        verify(postSecretService).execute(postCaptor.capture());
        assertEquals("Key1", postCaptor.getValue().getSecretName());
        assertEquals("val", postCaptor.getValue().getSecretValue());
        assertEquals("user1", postCaptor.getValue().getUser());

        ArgumentCaptor<PutSecretRequest> putCaptor = ArgumentCaptor.forClass(PutSecretRequest.class);
        verify(putSecretService).execute(putCaptor.capture());
        assertEquals("Key2", putCaptor.getValue().getSecretCurrentName());
        assertEquals("next:with:colons", putCaptor.getValue().getSecretUpdatedValue());
        assertEquals("user1", putCaptor.getValue().getUser());

        ArgumentCaptor<DeleteSecretRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteSecretRequest.class);
        verify(deleteSecretService).execute(deleteCaptor.capture());
        assertEquals("Key3", deleteCaptor.getValue().getDeleteName());
        assertEquals("user1", deleteCaptor.getValue().getUser());

        verify(getSecretService).getVersion("user1", "Key4", null);
    }

    @Test
    void testExecuteSupportsSpecificVersionGet() {
        when(getSecretService.getVersion("user1", "Key1", 2L)).thenReturn(ResponseEntity.ok("version-two"));

        ResponseEntity<String> response = envFileService.execute("user1", "Key1=get:2");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Key1=version-two", response.getBody());
        verify(getSecretService).getVersion("user1", "Key1", 2L);
    }

    @Test
    void testExecuteReturnsEmptyBodyForDeleteOnlyFile() {
        when(secretPartRepository.exists(new SecretKey("user1", "Key1"))).thenReturn(true);

        ResponseEntity<String> response = envFileService.execute("user1", "Key1=delete");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("", response.getBody());
    }

    @Test
    void testExecuteRejectsDuplicateKeysBeforeWrites() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> envFileService.execute("user1", """
                        Key1=new:val
                        Key1=update:other
                        """));

        assertEquals("Duplicate key in .env file: Key1", exception.getMessage());
        verifyNoInteractions(secretPartRepository, postSecretService, putSecretService, deleteSecretService,
                getSecretService);
    }

    @Test
    void testExecuteRejectsInvalidAction() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> envFileService.execute("user1", "Key1=replace:val"));

        assertEquals("Invalid .env action on line 1: expected new, update, get, or delete", exception.getMessage());
        verifyNoInteractions(secretPartRepository, postSecretService, putSecretService, deleteSecretService,
                getSecretService);
    }

    @Test
    void testExecuteRejectsNewWithoutValueDelimiter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> envFileService.execute("user1", "Key1=new"));

        assertEquals("Invalid .env entry on line 1: expected KEY=new:value, KEY=update:value, KEY=get, KEY=get:version, or KEY=delete",
                exception.getMessage());
        verifyNoInteractions(secretPartRepository, postSecretService, putSecretService, deleteSecretService,
                getSecretService);
    }

    @Test
    void testExecuteRejectsGetAll() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> envFileService.execute("user1", "Key1=get:all"));

        assertEquals("Invalid .env entry on line 1: expected KEY=new:value, KEY=update:value, KEY=get, KEY=get:version, or KEY=delete",
                exception.getMessage());
        verifyNoInteractions(secretPartRepository, postSecretService, putSecretService, deleteSecretService,
                getSecretService);
    }

    @Test
    void testExecuteRejectsExistingSecretForNewOperation() {
        when(secretPartRepository.exists(new SecretKey("user1", "Key1"))).thenReturn(true);

        DuplicateSecretException exception = assertThrows(DuplicateSecretException.class,
                () -> envFileService.execute("user1", "Key1=new:val"));

        assertEquals("Secret already exists: Key1", exception.getMessage());
        verifyNoInteractions(postSecretService, putSecretService, deleteSecretService, getSecretService);
    }

    @Test
    void testExecuteRejectsMissingSecretForUpdateOperation() {
        when(secretPartRepository.exists(new SecretKey("user1", "Key1"))).thenReturn(false);

        SecretNotFoundException exception = assertThrows(SecretNotFoundException.class,
                () -> envFileService.execute("user1", "Key1=update:val"));

        assertEquals("Secret not found: Key1", exception.getMessage());
        verifyNoInteractions(postSecretService, putSecretService, deleteSecretService, getSecretService);
    }

    @Test
    void testExecuteRejectsMissingSecretForGetOperation() {
        when(getSecretService.getVersion("user1", "Key1", null))
                .thenThrow(new SecretNotFoundException("Secret not found: Key1"));

        SecretNotFoundException exception = assertThrows(SecretNotFoundException.class,
                () -> envFileService.execute("user1", "Key1=get"));

        assertEquals("Secret not found: Key1", exception.getMessage());
        verify(getSecretService).getVersion("user1", "Key1", null);
        verifyNoInteractions(postSecretService, putSecretService, deleteSecretService);
    }

    @Test
    void testExecuteRequiresUser() {
        assertThrows(IllegalArgumentException.class, () -> envFileService.execute(" ", "Key1=new:val"));
        verifyNoInteractions(secretPartRepository, postSecretService, putSecretService, deleteSecretService,
                getSecretService);
    }
}
