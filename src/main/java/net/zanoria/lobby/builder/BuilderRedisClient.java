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

    /**
     * Was der Builder-Server ueber sein WorldEdit/FAWE meldet.
     *
     * <p>⚠️ Der Name steht WORTGLEICH in {@code Builders/BuilderRedisClient.KEY_WORLDEDIT}.
     * Weichen sie voneinander ab, schreibt der Builder-Server unter einen Schluessel, den hier
     * niemand liest - und die Lobby weist ALLE ab, obwohl FAWE laeuft. Beide Seiten saehen fuer
     * sich richtig aus.
     */
    public static final String KEY_WORLDEDIT = "builder:worldedit";

    /**
     * ⚠️ <b>STILLGELEGT am 2026-09-03. Diese Liste entscheidet NICHTS mehr.</b>
     *
     * <p>Bis dahin war sie das Tor zum Builder-Server: {@code BuilderServerService} fragte
     * {@code isMember(spieler.getName())}. Ersetzt durch die Berechtigung
     * {@code zanoria.builder}, vergeben von Nexus an OWNER, CO_OWNER und ADMIN.
     *
     * <p><b>Zwei Gruende, und beide gelten weiter:</b>
     * <ol>
     *   <li>Der Schluessel war der <b>Name</b>. Eine Namensaenderung verschob oder verlor den
     *       Zugang - lautlos, ohne Ausnahme und ohne Logzeile.</li>
     *   <li>Sie war ein <b>zweiter Ort</b> neben dem Rangsystem. Wer freischalten wollte, musste
     *       zuerst wissen, dass es sie ueberhaupt gibt.</li>
     * </ol>
     *
     * <p>⚠️ <b>NICHT geloescht</b>, aus zwei Gruenden: ein bestehender Redis-Bestand wuerde sonst
     * verwaisen, und dieser Kommentar ginge mit. Wer sie zurueckverdrahtet, macht das Tor wieder
     * namensabhaengig; {@code DasBuildertorIstEineBerechtigungTest} wird dabei rot, <b>und das
     * ist Absicht</b>.
     */
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

    /** ⚠️ STILLGELEGT - entscheidet nichts mehr. Siehe {@link #KEY_MEMBERS}. */
    @Deprecated
    public boolean isMember(String name) {
        if (!isAvailable()) return false;
        try (Jedis j = pool.getResource()) {
            return j.sismember(KEY_MEMBERS, name.toLowerCase());
        }
    }

    /** ⚠️ STILLGELEGT - siehe {@link #KEY_MEMBERS}. */
    @Deprecated
    public void addMember(String name) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) { j.sadd(KEY_MEMBERS, name.toLowerCase()); }
    }

    /** ⚠️ STILLGELEGT - siehe {@link #KEY_MEMBERS}. */
    @Deprecated
    public void removeMember(String name) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) { j.srem(KEY_MEMBERS, name.toLowerCase()); }
    }

    /** ⚠️ STILLGELEGT - siehe {@link #KEY_MEMBERS}. */
    @Deprecated
    public Set<String> getMembers() {
        if (!isAvailable()) return Set.of();
        try (Jedis j = pool.getResource()) { return j.smembers(KEY_MEMBERS); }
    }

    /** Was der Builder-Server ueber sein WorldEdit/FAWE meldet, oder {@code null}. */
    public String getWorldedit() {
        if (!isAvailable()) return null;
        try (Jedis j = pool.getResource()) { return j.get(KEY_WORLDEDIT); }
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
