package edu.yu.capstone.DistributedSecretsVault.repository.impl;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisSecretPartRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisSecretPartRepository repository;

    private SecretKey validKey;

    @BeforeEach
    void setUp() {
        repository = new RedisSecretPartRepository(redisTemplate);
        validKey = new SecretKey();
        validKey.setOwnerId("user1");
        validKey.setName("name1");
    }

    @Test
    void testFindPart() {
        SecretPart part = new SecretPart();
        part.setKey(validKey);
        part.setVersion(1L);
        part.setPartIndex(1);
        String json = "{\"key\":{\"ownerId\":\"user1\",\"name\":\"name1\"},\"version\":1,\"partIndex\":1}";
        when(redisTemplate.execute(any(), anyList(), eq("1"))).thenReturn(json);

        // match part index
        Optional<SecretPart> result = repository.findPart(validKey, 1L, 1);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getPartIndex());

        // mismatch part index
        Optional<SecretPart> mismatch = repository.findPart(validKey, 1L, 2);
        assertFalse(mismatch.isPresent());

        // null json
        when(redisTemplate.execute(any(), anyList(), eq("1"))).thenReturn(null);
        Optional<SecretPart> empty = repository.findPart(validKey, 1L, 1);
        assertFalse(empty.isPresent());
    }

    @Test
    void testFindParts() {
        String json = "{\"key\":{\"ownerId\":\"user1\",\"name\":\"name1\"},\"version\":1,\"partIndex\":1}";
        when(redisTemplate.execute(any(), anyList(), eq("1"))).thenReturn(json);
        List<SecretPart> parts = repository.findParts(validKey, 1L);
        assertEquals(1, parts.size());

        when(redisTemplate.execute(any(), anyList(), eq("1"))).thenReturn(null);
        List<SecretPart> empty = repository.findParts(validKey, 1L);
        assertTrue(empty.isEmpty());
    }

    @Test
    void testFindLatest() {
        String json = "{\"key\":{\"ownerId\":\"user1\",\"name\":\"name1\"},\"version\":2,\"partIndex\":1}";
        // The script that takes no ARGS
        when(redisTemplate.execute(any(), anyList())).thenReturn(json);
        Optional<SecretPart> latest = repository.findLatest(validKey);
        assertTrue(latest.isPresent());
        assertEquals(2L, latest.get().getVersion());

        when(redisTemplate.execute(any(), anyList())).thenReturn(null);
        Optional<SecretPart> nullLatest = repository.findLatest(validKey);
        assertFalse(nullLatest.isPresent());
    }

    @Test
    void testListVersions() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(Arrays.asList("1", "2"));
        List<Long> versions = repository.listVersions(validKey);
        assertEquals(Arrays.asList(1L, 2L), versions);

        when(redisTemplate.execute(any(), anyList())).thenReturn(Collections.emptyList());
        assertTrue(repository.listVersions(validKey).isEmpty());

        when(redisTemplate.execute(any(), anyList())).thenReturn(null);
        assertTrue(repository.listVersions(validKey).isEmpty());

        // number format exception test
        when(redisTemplate.execute(any(), anyList())).thenReturn(List.of("invalid"));
        assertThrows(IllegalStateException.class, () -> repository.listVersions(validKey));
    }

    @Test
    void testExists() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(1L);
        assertTrue(repository.exists(validKey));

        when(redisTemplate.execute(any(), anyList())).thenReturn(0L);
        assertFalse(repository.exists(validKey));

        when(redisTemplate.execute(any(), anyList())).thenReturn(null);
        assertFalse(repository.exists(validKey));
    }

    @Test
    void testSavePart() {
        SecretPart part = new SecretPart();
        part.setKey(validKey);
        part.setVersion(1L);
        part.setPartIndex(1);

        repository.savePart(part);
        verify(redisTemplate).execute(any(), eq(List.of("user1:name1")), eq("1"), anyString());

        assertThrows(IllegalArgumentException.class, () -> repository.savePart(null));
        SecretPart noKey = new SecretPart();
        assertThrows(IllegalArgumentException.class, () -> repository.savePart(noKey));
    }

    @Test
    void testUpdatePart() {
        SecretPart part = new SecretPart();
        part.setKey(validKey);
        part.setVersion(1L);
        part.setPartIndex(1);

        when(redisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(1L);
        assertTrue(repository.updatePart(part));

        when(redisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(0L);
        assertFalse(repository.updatePart(part));

        when(redisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(null);
        assertFalse(repository.updatePart(part));

        assertThrows(IllegalArgumentException.class, () -> repository.updatePart(null));
        SecretPart noKey = new SecretPart();
        assertThrows(IllegalArgumentException.class, () -> repository.updatePart(noKey));
    }

    @Test
    void testDeleteParts() {
        repository.deleteParts(validKey);
        verify(redisTemplate).execute(any(), eq(List.of("user1:name1")));
    }

    @Test
    void testSecretKeyValidation() {
        assertThrows(IllegalArgumentException.class, () -> repository.exists(null));

        SecretKey nullOwner = new SecretKey();
        nullOwner.setOwnerId(null);
        nullOwner.setName("name");
        assertThrows(IllegalArgumentException.class, () -> repository.exists(nullOwner));

        SecretKey nullName = new SecretKey();
        nullName.setOwnerId("owner");
        nullName.setName(null);
        assertThrows(IllegalArgumentException.class, () -> repository.exists(nullName));
    }

    @Test
    void testDeserializeException() {
        when(redisTemplate.execute(any(), anyList(), eq("1"))).thenReturn("{ invalid json ");
        assertThrows(IllegalStateException.class, () -> repository.findPart(validKey, 1L, 1));
    }
}
