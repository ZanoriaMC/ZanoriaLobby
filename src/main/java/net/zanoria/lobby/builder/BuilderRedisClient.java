package net.zanoria.lobby.builder;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Set;

/**
 * Thin Jedis wrapper for the builder subsystem.
 */
public final class BuilderRedisClient {

    public static final String KEY_STATUS  = "builder:status";
    public static final String KEY_MEMBERS = "builder:members";
    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_RUNNING  = "running";

    private final String host;
    private final int    port;
    private final String username;
    private final String password;
    private JedisPool pool;

    public BuilderRedisClient(String host, int port, String username, String password) {
        this.host     = host;
        this.port     = port;
        this.username = username;
        this.password = password;
    }

    public void connect() {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8);
        cfg.setMaxIdle(2);
        cfg.setMinIdle(1);
        cfg.setTestOnBorrow(true);
        cfg.setMinEvictableIdleDuration(Duration.ofSeconds(60));
        DefaultJedisClientConfig.Builder client = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(2000)
                .socketTimeoutMillis(2000);
        if (username != null && !username.isBlank()) client.user(username);
        if (password != null && !password.isBlank()) client.password(password);
        pool = new JedisPool(cfg, new HostAndPort(host, port), client.build());
        try (Jedis j = pool.getResource()) { j.ping(); }
    }

    public void close() {
        if (pool != null) pool.close();
    }

    public boolean isAvailable() {
        return pool != null && !pool.isClosed();
    }

    public boolean isMember(String name) {
        if (!isAvailable()) return false;
        try (Jedis j = pool.getResource()) {
            return j.sismember(KEY_MEMBERS, name.toLowerCase());
        }
    }

    public void addMember(String name) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) { j.sadd(KEY_MEMBERS, name.toLowerCase()); }
    }

    public void removeMember(String name) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) { j.srem(KEY_MEMBERS, name.toLowerCase()); }
    }

    public Set<String> getMembers() {
        if (!isAvailable()) return Set.of();
        try (Jedis j = pool.getResource()) { return j.smembers(KEY_MEMBERS); }
    }

    public String getStatus() {
        if (!isAvailable()) return null;
        try (Jedis j = pool.getResource()) { return j.get(KEY_STATUS); }
    }

    public void setStatus(String value) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) {
            if (value == null) j.del(KEY_STATUS);
            else j.set(KEY_STATUS, value);
        }
    }

    /**
     * Sets builder:status to the given value only if the key does not already exist.
     * Returns true if the key was set (this caller won the race), false otherwise.
     */
    public boolean setStatusIfAbsent(String value) {
        if (!isAvailable()) return false;
        try (Jedis j = pool.getResource()) {
            // SET key value NX — returns "OK" if set, null if key already existed
            return "OK".equals(j.set(KEY_STATUS, value, redis.clients.jedis.params.SetParams.setParams().nx()));
        }
    }
}
