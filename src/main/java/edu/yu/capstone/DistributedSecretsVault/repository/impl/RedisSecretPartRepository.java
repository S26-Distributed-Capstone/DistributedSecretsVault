package edu.yu.capstone.DistributedSecretsVault.repository.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@Repository
public class RedisSecretPartRepository implements SecretPartRepository {
    private static final String SAVE_PART_PATH = "redis/save_part.lua";
    private static final String GET_BY_VERSION_PATH = "redis/get_by_version.lua";
    private static final String GET_LATEST_PATH = "redis/get_latest.lua";
    private static final String LIST_VERSIONS_PATH = "redis/list_versions.lua";
    private static final String EXISTS_PATH = "redis/exists.lua";
    private static final String UPDATE_PART_PATH = "redis/update_part.lua";
    private static final String DELETE_PATH = "redis/delete.lua";

    private final StringRedisTemplate redisTemplate;
    private final Gson gson;
    private final RedisScript<Long> savePartScript;
    private final RedisScript<String> getByVersionScript;
    private final RedisScript<String> getLatestScript;
    private final RedisScript<List> listVersionsScript;
    private final RedisScript<Long> existsScript;
    private final RedisScript<Long> updatePartScript;
    private final RedisScript<Long> deleteScript;

    public RedisSecretPartRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.gson = new Gson();
        this.savePartScript = loadScript(SAVE_PART_PATH, Long.class);
        this.getByVersionScript = loadScript(GET_BY_VERSION_PATH, String.class);
        this.getLatestScript = loadScript(GET_LATEST_PATH, String.class);
        this.listVersionsScript = loadScript(LIST_VERSIONS_PATH, List.class);
        this.existsScript = loadScript(EXISTS_PATH, Long.class);
        this.updatePartScript = loadScript(UPDATE_PART_PATH, Long.class);
        this.deleteScript = loadScript(DELETE_PATH, Long.class);
    }

    @Override
    public Optional<SecretPart> findPart(SecretKey key, long version, int partIndex) {
        Optional<SecretPart> part = getPartByVersion(key, version);
        if (part.isEmpty() || part.get().getPartIndex() != partIndex) {
            return Optional.empty();
        }
        return part;
    }

    @Override
    public List<SecretPart> findParts(SecretKey key, long version) {
        return getPartByVersion(key, version).map(List::of).orElseGet(List::of);
    }

    @Override
    public Optional<SecretPart> findLatest(SecretKey key) {
        String value = redisTemplate.execute(getLatestScript, List.of(secretKey(key)));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(value));
    }

    @Override
    public List<Long> listVersions(SecretKey key) {
        List<?> rawVersions = redisTemplate.execute(listVersionsScript, List.of(secretKey(key)));
        if (rawVersions == null || rawVersions.isEmpty()) {
            return List.of();
        }
        List<Long> results = new ArrayList<>(rawVersions.size());
        for (Object version : rawVersions) {
            try {
                results.add(Long.valueOf(String.valueOf(version)));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid version value in Redis: " + version, ex);
            }
        }
        return results;
    }

    @Override
    public boolean exists(SecretKey key) {
        Long size = redisTemplate.execute(existsScript, List.of(secretKey(key)));
        return size != null && size > 0;
    }

    @Override
    public void savePart(SecretPart part) {
        if (part == null || part.getKey() == null) {
            throw new IllegalArgumentException("SecretPart and key are required");
        }
        redisTemplate.execute(savePartScript, List.of(secretKey(part.getKey())),
                String.valueOf(part.getVersion()), serialize(part));
    }

    @Override
    public boolean updatePart(SecretPart part) {
        if (part == null || part.getKey() == null) {
            throw new IllegalArgumentException("SecretPart and key are required");
        }
        Long updated = redisTemplate.execute(updatePartScript, List.of(secretKey(part.getKey())),
                String.valueOf(part.getVersion()), serialize(part));
        return updated != null && updated > 0;
    }

    @Override
    public void deleteParts(SecretKey key) {
        redisTemplate.execute(deleteScript, List.of(secretKey(key)));
    }

    private Optional<SecretPart> getPartByVersion(SecretKey key, long version) {
        String value = redisTemplate.execute(getByVersionScript, List.of(secretKey(key)), String.valueOf(version));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(value));
    }

    private <T> RedisScript<T> loadScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }

    private String secretKey(SecretKey key) {
        if (key == null || key.getOwnerId() == null || key.getName() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
        return key.getOwnerId() + ":" + key.getName();
    }

    private String serialize(SecretPart part) {
        return gson.toJson(part);
    }

    private SecretPart deserialize(String value) {
        try {
            return gson.fromJson(value, SecretPart.class);
        } catch (JsonSyntaxException ex) {
            throw new IllegalStateException("Unable to deserialize SecretPart", ex);
        }
    }
}
