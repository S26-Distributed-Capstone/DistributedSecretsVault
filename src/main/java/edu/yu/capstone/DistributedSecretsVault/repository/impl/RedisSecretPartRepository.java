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

// TODO attempt to switch from using a raw list for listVersionsScript

/**
 * Redis-backed implementation of {@link SecretPartRepository}.
 * <p>
 * All persistence operations are executed as atomic Lua scripts to guarantee
 * consistency within a single Redis instance. Secret shards are serialized as
 * JSON via {@link Gson} and stored in a Redis sorted set keyed by
 * {@code ownerId:name}, with the version number as the score.
 * <p>
 * Each Lua script is loaded from the classpath ({@code redis/*.lua}) at
 * construction time and cached as a {@link RedisScript}.
 */
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

    /**
     * Constructs the repository and pre-loads all Lua scripts from the classpath.
     *
     * @param redisTemplate Spring Redis template for executing scripts
     */
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

    /** {@inheritDoc} */
    @Override
    public Optional<SecretPart> findPart(SecretKey key, long version) {
        String value = redisTemplate.execute(getByVersionScript, List.of(secretKey(key)), String.valueOf(version));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(value));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SecretPart> findLatest(SecretKey key) {
        String value = redisTemplate.execute(getLatestScript, List.of(secretKey(key)));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(value));
    }

    /** {@inheritDoc} */
    @Override
    public List<Long> listVersions(SecretKey key) {
        List<?> rawVersions = redisTemplate.execute(listVersionsScript, List.of(secretKey(key)));
        if (rawVersions == null || rawVersions.isEmpty()) {
            return List.of();
        }
        // Lua returns version numbers as strings; convert each to Long
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

    /** {@inheritDoc} */
    @Override
    public boolean exists(SecretKey key) {
        Long size = redisTemplate.execute(existsScript, List.of(secretKey(key)));
        return size != null && size > 0;
    }

    /** {@inheritDoc} */
    @Override
    public void savePart(SecretPart part) {
        if (part == null || part.getKey() == null) {
            throw new IllegalArgumentException("SecretPart and key are required");
        }
        redisTemplate.execute(savePartScript, List.of(secretKey(part.getKey())),
                String.valueOf(part.getVersion()), serialize(part));
    }

    /** {@inheritDoc} */
    @Override
    public boolean updatePart(SecretPart part) {
        if (part == null || part.getKey() == null) {
            throw new IllegalArgumentException("SecretPart and key are required");
        }
        Long updated = redisTemplate.execute(updatePartScript, List.of(secretKey(part.getKey())),
                String.valueOf(part.getVersion()), serialize(part));
        return updated != null && updated > 0;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteParts(SecretKey key) {
        redisTemplate.execute(deleteScript, List.of(secretKey(key)));
    }

    /**
     * Loads a Lua script from the classpath and caches it for repeated execution.
     *
     * @param path       classpath-relative path to the {@code .lua} file
     * @param resultType expected return type of the script
     * @param <T>        result type parameter
     * @return a cached {@link RedisScript}
     */
    private <T> RedisScript<T> loadScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }

    /**
     * Converts a {@link SecretKey} into the Redis key string {@code ownerId:name}.
     */
    private String secretKey(SecretKey key) {
        if (key == null || key.getOwnerId() == null || key.getName() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
        return key.getOwnerId() + ":" + key.getName();
    }

    /** Serializes a {@link SecretPart} to JSON. */
    private String serialize(SecretPart part) {
        return gson.toJson(part);
    }

    /** Deserializes JSON back to a {@link SecretPart}. */
    private SecretPart deserialize(String value) {
        try {
            return gson.fromJson(value, SecretPart.class);
        } catch (JsonSyntaxException ex) {
            throw new IllegalStateException("Unable to deserialize SecretPart", ex);
        }
    }
}
