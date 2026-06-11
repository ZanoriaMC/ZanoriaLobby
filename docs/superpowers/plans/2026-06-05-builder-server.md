# Builder Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Players on the builder whitelist can type `/builder` in the lobby to join a on-demand builder server; admins manage the whitelist via `/builder add/remove/list`; the builder server shuts down after 30min idle.

**Architecture:** ZanoriaLobby gets a `/builder` command that checks a Redis-backed whitelist (`builder:members`), starts the Pelican-managed builder server if needed via HTTP, then transfers the player via BungeeCord plugin message once the server reports `builder:status = running`. The Builders plugin writes its Redis status on enable/disable and monitors idle time for auto-shutdown.

**Tech Stack:** Java 21, Paper API 1.21, Jedis 5.x, java.net.http.HttpClient (JDK), Gson

---

## File Map

### ZanoriaLobby (new/modified)
| File | Action | Purpose |
|------|--------|---------|
| `build.gradle` | Modify | Add Jedis 5.x dependency |
| `src/main/resources/config.yml` | Modify | Add `builder:` section |
| `src/main/resources/plugin.yml` | Modify | Add `builder` command + permission |
| `src/main/java/net/zanoria/lobby/builder/BuilderRedisClient.java` | Create | Thin Jedis wrapper (connect, sadd, sismember, get, set, del) |
| `src/main/java/net/zanoria/lobby/builder/BuilderPelicanStarter.java` | Create | Calls Pelican `POST /power` to start the builder server |
| `src/main/java/net/zanoria/lobby/builder/BuilderServerService.java` | Create | Orchestrates: whitelist check → start if needed → poll → transfer |
| `src/main/java/net/zanoria/lobby/builder/BuilderCommand.java` | Create | `/builder [add\|remove\|list] [name]` command |
| `src/main/java/net/zanoria/lobby/ZanoriaLobby.java` | Modify | Init BuilderServerService, register command |

### Builders plugin (new/modified)
| File | Action | Purpose |
|------|--------|---------|
| `build.gradle` | Modify | Add Jedis 5.x dependency |
| `src/main/resources/config.yml` | Create | Redis config + idle-shutdown-minutes |
| `src/main/java/net/zanoria/builders/redis/BuilderRedisClient.java` | Create | Same thin Jedis wrapper |
| `src/main/java/net/zanoria/builders/redis/BuilderRedisReporter.java` | Create | Writes/deletes `builder:status` + `builder:address` on enable/disable |
| `src/main/java/net/zanoria/builders/idle/BuilderIdleShutdown.java` | Create | BukkitRunnable: shuts down after N minutes idle |
| `src/main/java/net/zanoria/builders/Builders.java` | Modify | Init redis reporter + idle shutdown |

---

## Task 1: Add Jedis to both build.gradle files

**Files:**
- Modify: `IdeaProjects/ZanoriaLobby/build.gradle`
- Modify: `IdeaProjects/Builders/build.gradle`

- [ ] **Step 1: Add Jedis to ZanoriaLobby build.gradle**

In `IdeaProjects/ZanoriaLobby/build.gradle`, add inside `dependencies { ... }`:
```groovy
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("C:/Users/krinc/IdeaProjects/Nexus/build/libs/Nexus-1.0-SNAPSHOT.jar"))
    compileOnly(files("C:/Users/krinc/IdeaProjects/RelicWars/run/plugins/ZanLang-1.0.jar"))
    implementation 'redis.clients:jedis:5.1.3'
}
```

Also add shadow plugin so Jedis gets bundled (ZanoriaLobby has no shadow plugin yet):
```groovy
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '8.3.5'
}
```

And at the bottom, add:
```groovy
shadowJar {
    relocate 'redis.clients', 'net.zanoria.lobby.libs.jedis'
    relocate 'org.apache.commons.pool2', 'net.zanoria.lobby.libs.pool2'
    archiveClassifier.set('')
}
```

- [ ] **Step 2: Add Jedis to Builders build.gradle**

In `IdeaProjects/Builders/build.gradle`, same changes:
```groovy
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '8.3.5'
    id("xyz.jpenilla.run-paper") version "2.3.1"
}
```

In `dependencies { ... }`:
```groovy
implementation 'redis.clients:jedis:5.1.3'
```

At the bottom add:
```groovy
shadowJar {
    relocate 'redis.clients', 'net.zanoria.builders.libs.jedis'
    relocate 'org.apache.commons.pool2', 'net.zanoria.builders.libs.pool2'
    archiveClassifier.set('')
}
```

- [ ] **Step 3: Verify both projects sync in Gradle without errors**

Run in ZanoriaLobby:
```
./gradlew dependencies --configuration runtimeClasspath 2>&1 | grep jedis
```
Expected: line containing `jedis-5.1.3`

Run in Builders:
```
./gradlew dependencies --configuration runtimeClasspath 2>&1 | grep jedis
```
Expected: line containing `jedis-5.1.3`

- [ ] **Step 4: Commit**
```bash
# In ZanoriaLobby
cd ~/IdeaProjects/ZanoriaLobby
git add build.gradle
git commit -m "build: add Jedis 5.x dependency with shadow relocation"

# In Builders
cd ~/IdeaProjects/Builders
git add build.gradle
git commit -m "build: add Jedis 5.x dependency with shadow relocation"
```

---

## Task 2: BuilderRedisClient (ZanoriaLobby)

**Files:**
- Create: `src/main/java/net/zanoria/lobby/builder/BuilderRedisClient.java`

- [ ] **Step 1: Create BuilderRedisClient.java**

```java
package net.zanoria.lobby.builder;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * Thin Jedis wrapper for the builder subsystem.
 * Handles connect/close and provides typed operations on builder Redis keys.
 */
public final class BuilderRedisClient {

    // ── Redis keys ────────────────────────────────────────────────────────
    public static final String KEY_STATUS  = "builder:status";
    public static final String KEY_MEMBERS = "builder:members";

    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_RUNNING  = "running";

    private final String host;
    private final int    port;
    private final String password;

    private JedisPool pool;

    public BuilderRedisClient(String host, int port, String password) {
        this.host     = host;
        this.port     = port;
        this.password = password;
    }

    public void connect() {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8);
        cfg.setMaxIdle(2);
        cfg.setMinIdle(1);
        cfg.setTestOnBorrow(true);
        cfg.setMinEvictableIdleDuration(Duration.ofSeconds(60));

        pool = (password != null && !password.isBlank())
                ? new JedisPool(cfg, host, port, 2000, password)
                : new JedisPool(cfg, host, port, 2000);

        try (Jedis j = pool.getResource()) { j.ping(); }
    }

    public void close() {
        if (pool != null) pool.close();
    }

    public boolean isAvailable() {
        return pool != null && !pool.isClosed();
    }

    /** Returns true if the player name (case-insensitive) is in the whitelist. */
    public boolean isMember(String name) {
        if (!isAvailable()) return false;
        try (Jedis j = pool.getResource()) {
            return j.sismember(KEY_MEMBERS, name.toLowerCase());
        }
    }

    public void addMember(String name) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) {
            j.sadd(KEY_MEMBERS, name.toLowerCase());
        }
    }

    public void removeMember(String name) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) {
            j.srem(KEY_MEMBERS, name.toLowerCase());
        }
    }

    /** Returns all member names. */
    public java.util.Set<String> getMembers() {
        if (!isAvailable()) return java.util.Set.of();
        try (Jedis j = pool.getResource()) {
            return j.smembers(KEY_MEMBERS);
        }
    }

    /** Returns the current builder:status value, or null if absent. */
    public String getStatus() {
        if (!isAvailable()) return null;
        try (Jedis j = pool.getResource()) {
            return j.get(KEY_STATUS);
        }
    }

    public void setStatus(String value) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) {
            j.set(KEY_STATUS, value);
        }
    }
}
```

- [ ] **Step 2: Commit**
```bash
cd ~/IdeaProjects/ZanoriaLobby
git add src/main/java/net/zanoria/lobby/builder/BuilderRedisClient.java
git commit -m "feat(builder): add BuilderRedisClient with whitelist and status ops"
```

---

## Task 3: BuilderPelicanStarter (ZanoriaLobby)

**Files:**
- Create: `src/main/java/net/zanoria/lobby/builder/BuilderPelicanStarter.java`

- [ ] **Step 1: Create BuilderPelicanStarter.java**

```java
package net.zanoria.lobby.builder;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends a "start" power action to the Pelican panel for the builder server.
 * Uses the Pelican CLIENT API (not application API) — no server creation needed.
 */
public final class BuilderPelicanStarter {

    private final String panelUrl;   // e.g. "http://49.13.1.183:8080"
    private final String apiKey;     // client API key
    private final String serverUuid; // short UUID of the builder server

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public BuilderPelicanStarter(String panelUrl, String apiKey, String serverUuid) {
        this.panelUrl   = panelUrl.replaceAll("/$", "");
        this.apiKey     = apiKey;
        this.serverUuid = serverUuid;
    }

    /**
     * Sends "start" to the builder server.
     * Returns true if the panel accepted the request (202), false otherwise.
     */
    public boolean startServer() {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("signal", "start");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(panelUrl + "/api/client/servers/" + serverUuid + "/power"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 204 || response.statusCode() == 202;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public boolean isConfigured() {
        return panelUrl != null && !panelUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && serverUuid != null && !serverUuid.isBlank();
    }
}
```

Note: Gson is available transitively via Paper API. If not, add `implementation 'com.google.code.gson:gson:2.10.1'` to build.gradle.

- [ ] **Step 2: Commit**
```bash
cd ~/IdeaProjects/ZanoriaLobby
git add src/main/java/net/zanoria/lobby/builder/BuilderPelicanStarter.java
git commit -m "feat(builder): add BuilderPelicanStarter for on-demand server start"
```

---

## Task 4: BuilderServerService (ZanoriaLobby)

**Files:**
- Create: `src/main/java/net/zanoria/lobby/builder/BuilderServerService.java`

- [ ] **Step 1: Create BuilderServerService.java**

```java
package net.zanoria.lobby.builder;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the builder-join flow:
 *  1. Whitelist check
 *  2. Start server if not running
 *  3. Poll until running, then transfer player
 */
public final class BuilderServerService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final String PREFIX   = "<gradient:#FF702B:#FCC650>Builder</gradient> <dark_gray>» <reset>";
    private static final int    POLL_INTERVAL_TICKS = 40;  // 2s
    private static final int    MAX_POLLS           = 30;  // 60s total

    private final JavaPlugin           plugin;
    private final BuilderRedisClient   redis;
    private final BuilderPelicanStarter pelican;
    private final String               velocityServerName; // name in velocity.toml, e.g. "builder-1"

    /** Players currently waiting for the server to start. */
    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    public BuilderServerService(JavaPlugin plugin, BuilderRedisClient redis,
                                BuilderPelicanStarter pelican, String velocityServerName) {
        this.plugin             = plugin;
        this.redis              = redis;
        this.pelican            = pelican;
        this.velocityServerName = velocityServerName;
    }

    /**
     * Attempts to connect the player to the builder server.
     * Handles all messaging to the player.
     */
    public void joinBuilderServer(Player player) {
        // 1. Whitelist check
        if (!redis.isMember(player.getName())) {
            player.sendMessage(MM.deserialize(PREFIX + "<red>Du bist kein Builder."));
            return;
        }

        // 2. Already pending?
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(MM.deserialize(PREFIX + "<yellow>Server startet bereits, bitte warte..."));
            return;
        }

        String status = redis.getStatus();

        if (BuilderRedisClient.STATUS_RUNNING.equals(status)) {
            // Transfer immediately
            transfer(player);
        } else if (BuilderRedisClient.STATUS_STARTING.equals(status)) {
            // Already starting — just poll
            player.sendMessage(MM.deserialize(PREFIX + "<yellow>Server startet, du wirst automatisch verbunden..."));
            startPolling(player);
        } else {
            // Start the server
            if (!pelican.isConfigured()) {
                player.sendMessage(MM.deserialize(PREFIX + "<red>Builder-Server nicht konfiguriert. Bitte kontaktiere einen Admin."));
                return;
            }
            player.sendMessage(MM.deserialize(PREFIX + "<yellow>Builder-Server wird gestartet..."));
            redis.setStatus(BuilderRedisClient.STATUS_STARTING);

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = pelican.startServer();
                if (!ok) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(MM.deserialize(PREFIX + "<red>Server konnte nicht gestartet werden. Versuche es erneut.")));
                    redis.setStatus(null);
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> startPolling(player));
            });
        }
    }

    private void startPolling(Player player) {
        int[] polls = {0};

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            polls[0]++;

            if (!player.isOnline()) {
                cancelPending(player.getUniqueId());
                return;
            }

            String status = redis.getStatus();
            if (BuilderRedisClient.STATUS_RUNNING.equals(status)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> transfer(player));
                cancelPending(player.getUniqueId());
                return;
            }

            if (polls[0] >= MAX_POLLS) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(MM.deserialize(PREFIX + "<red>Timeout: Server antwortet nicht. Versuche es erneut.")));
                cancelPending(player.getUniqueId());
            }
        }, POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);

        pending.put(player.getUniqueId(), task);
    }

    private void transfer(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(velocityServerName);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    private void cancelPending(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
    }

    public void shutdown() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
    }
}
```

- [ ] **Step 2: Commit**
```bash
cd ~/IdeaProjects/ZanoriaLobby
git add src/main/java/net/zanoria/lobby/builder/BuilderServerService.java
git commit -m "feat(builder): add BuilderServerService with start/poll/transfer flow"
```

---

## Task 5: BuilderCommand (ZanoriaLobby)

**Files:**
- Create: `src/main/java/net/zanoria/lobby/builder/BuilderCommand.java`

- [ ] **Step 1: Create BuilderCommand.java**

```java
package net.zanoria.lobby.builder;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class BuilderCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM     = MiniMessage.miniMessage();
    private static final String      PREFIX = "<gradient:#FF702B:#FCC650>Builder</gradient> <dark_gray>» <reset>";

    private final BuilderServerService service;
    private final BuilderRedisClient   redis;

    public BuilderCommand(BuilderServerService service, BuilderRedisClient redis) {
        this.service = service;
        this.redis   = redis;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            // Join flow
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /builder.");
                return true;
            }
            service.joinBuilderServer(player);
            return true;
        }

        // Admin sub-commands
        if (!sender.hasPermission("zanoria.admin")) {
            sender.sendMessage(MM.deserialize(PREFIX + "<red>Keine Berechtigung."));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 2) { sender.sendMessage(MM.deserialize(PREFIX + "<red>Verwendung: /builder add <name>")); return true; }
                redis.addMember(args[1]);
                sender.sendMessage(MM.deserialize(PREFIX + "<green>" + args[1] + " wurde als Builder hinzugefügt."));
            }
            case "remove" -> {
                if (args.length < 2) { sender.sendMessage(MM.deserialize(PREFIX + "<red>Verwendung: /builder remove <name>")); return true; }
                redis.removeMember(args[1]);
                sender.sendMessage(MM.deserialize(PREFIX + "<yellow>" + args[1] + " wurde entfernt."));
            }
            case "list" -> {
                var members = redis.getMembers();
                if (members.isEmpty()) {
                    sender.sendMessage(MM.deserialize(PREFIX + "<gray>Keine Builder eingetragen."));
                } else {
                    sender.sendMessage(MM.deserialize(PREFIX + "<white>Builder: <aqua>" + String.join(", ", members)));
                }
            }
            default -> sender.sendMessage(MM.deserialize(PREFIX + "<red>Unbekannter Sub-Befehl. Verwende: add, remove, list"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("zanoria.admin")) {
            return List.of("add", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
```

- [ ] **Step 2: Commit**
```bash
cd ~/IdeaProjects/ZanoriaLobby
git add src/main/java/net/zanoria/lobby/builder/BuilderCommand.java
git commit -m "feat(builder): add /builder command with join and admin sub-commands"
```

---

## Task 6: Config + plugin.yml (ZanoriaLobby)

**Files:**
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/resources/plugin.yml`

- [ ] **Step 1: Update config.yml**

Replace full content of `src/main/resources/config.yml`:
```yaml
spawn:
  world: world
  x: 0.5
  y: 64.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0

relicwarsnpc:
  queue: relicwars1v1
  skin: corwis
  name: "RelicWars"
  world: world
  x: 0.5
  y: 64.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0

builder:
  velocity-server-name: "builder-1"
  pelican-url: "http://49.13.1.183:8080"
  api-key: ""
  server-uuid: ""
  redis:
    host: "172.18.0.1"
    port: 6379
    password: ""
```

- [ ] **Step 2: Update plugin.yml**

Replace full content of `src/main/resources/plugin.yml`:
```yaml
name: ZanoriaLobby
version: '1.0-SNAPSHOT'
main: net.zanoria.lobby.ZanoriaLobby
api-version: '1.21'
softdepend:
  - Nexus

commands:
  lobbynpc:
    description: Verwaltet Lobby Queue NPCs
    usage: /lobbynpc <set|list|reload>
    permission: zanoria.lobby.npc
    permission-message: Du hast keine Berechtigung.
  builder:
    description: Betritt den Builder-Server oder verwalte die Builder-Liste
    usage: /builder [add|remove|list] [name]

permissions:
  zanoria.lobby.npc:
    description: Erlaubt das Verwalten von Lobby NPCs
    default: op
  zanoria.admin:
    description: Erlaubt Builder-Verwaltung und andere Admin-Befehle
    default: op
```

- [ ] **Step 3: Commit**
```bash
cd ~/IdeaProjects/ZanoriaLobby
git add src/main/resources/config.yml src/main/resources/plugin.yml
git commit -m "feat(builder): add builder config section and /builder command to plugin.yml"
```

---

## Task 7: Wire up in ZanoriaLobby.java

**Files:**
- Modify: `src/main/java/net/zanoria/lobby/ZanoriaLobby.java`

- [ ] **Step 1: Add fields and imports**

Add these fields after `private QueueService queueService;`:
```java
private BuilderRedisClient   builderRedis;
private BuilderServerService builderService;
```

Add these imports at the top:
```java
import net.zanoria.lobby.builder.BuilderCommand;
import net.zanoria.lobby.builder.BuilderPelicanStarter;
import net.zanoria.lobby.builder.BuilderRedisClient;
import net.zanoria.lobby.builder.BuilderServerService;
```

- [ ] **Step 2: Init in onEnable()**

Add before the closing of `onEnable()`, after the existing NPC/bossbar setup:
```java
// ── Builder subsystem ───────────────────────────────────────────────
getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

String redisHost  = getConfig().getString("builder.redis.host", "172.18.0.1");
int    redisPort  = getConfig().getInt("builder.redis.port", 6379);
String redisPw    = getConfig().getString("builder.redis.password", "");
String pelicanUrl = getConfig().getString("builder.pelican-url", "");
String apiKey     = getConfig().getString("builder.api-key", "");
String uuid       = getConfig().getString("builder.server-uuid", "");
String velServer  = getConfig().getString("builder.velocity-server-name", "builder-1");

builderRedis = new BuilderRedisClient(redisHost, redisPort, redisPw);
try {
    builderRedis.connect();
    getSLF4JLogger().info("Builder Redis connected.");
} catch (Exception e) {
    getSLF4JLogger().warn("Builder Redis unavailable: {}", e.getMessage());
}

BuilderPelicanStarter pelican = new BuilderPelicanStarter(pelicanUrl, apiKey, uuid);
builderService = new BuilderServerService(this, builderRedis, pelican, velServer);

var builderCmd = getCommand("builder");
if (builderCmd != null) {
    var bc = new BuilderCommand(builderService, builderRedis);
    builderCmd.setExecutor(bc);
    builderCmd.setTabCompleter(bc);
}
```

- [ ] **Step 3: Cleanup in onDisable()**

Add at the beginning of `onDisable()`:
```java
if (builderService != null) builderService.shutdown();
if (builderRedis != null)   builderRedis.close();
```

- [ ] **Step 4: Build and check for compile errors**
```
cd ~/IdeaProjects/ZanoriaLobby
./gradlew shadowJar 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```bash
cd ~/IdeaProjects/ZanoriaLobby
git add src/main/java/net/zanoria/lobby/ZanoriaLobby.java
git commit -m "feat(builder): wire up builder subsystem in ZanoriaLobby"
```

---

## Task 8: BuilderRedisClient (Builders plugin)

**Files:**
- Create: `src/main/java/net/zanoria/builders/redis/BuilderRedisClient.java`

- [ ] **Step 1: Create BuilderRedisClient.java**

```java
package net.zanoria.builders.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * Thin Jedis wrapper for the builder server's Redis operations.
 */
public final class BuilderRedisClient {

    public static final String KEY_STATUS = "builder:status";
    public static final String STATUS_RUNNING = "running";

    private final String host;
    private final int    port;
    private final String password;

    private JedisPool pool;

    public BuilderRedisClient(String host, int port, String password) {
        this.host     = host;
        this.port     = port;
        this.password = password;
    }

    public void connect() {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(4);
        cfg.setMaxIdle(2);
        cfg.setTestOnBorrow(true);
        cfg.setMinEvictableIdleDuration(Duration.ofSeconds(60));

        pool = (password != null && !password.isBlank())
                ? new JedisPool(cfg, host, port, 2000, password)
                : new JedisPool(cfg, host, port, 2000);

        try (Jedis j = pool.getResource()) { j.ping(); }
    }

    public void close() {
        if (pool != null) pool.close();
    }

    public boolean isAvailable() {
        return pool != null && !pool.isClosed();
    }

    public void set(String key, String value) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) { j.set(key, value); }
    }

    public void del(String key) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) { j.del(key); }
    }
}
```

- [ ] **Step 2: Commit**
```bash
cd ~/IdeaProjects/Builders
git add src/main/java/net/zanoria/builders/redis/BuilderRedisClient.java
git commit -m "feat(builder): add BuilderRedisClient for server-side Redis ops"
```

---

## Task 9: BuilderRedisReporter (Builders plugin)

**Files:**
- Create: `src/main/java/net/zanoria/builders/redis/BuilderRedisReporter.java`

- [ ] **Step 1: Create BuilderRedisReporter.java**

```java
package net.zanoria.builders.redis;

/**
 * Writes builder:status = "running" on enable and clears it on disable.
 * This lets the lobby know the server is available.
 */
public final class BuilderRedisReporter {

    private final BuilderRedisClient redis;

    public BuilderRedisReporter(BuilderRedisClient redis) {
        this.redis = redis;
    }

    /** Call from Builders.onEnable() after Redis connects. */
    public void reportOnline() {
        redis.set(BuilderRedisClient.KEY_STATUS, BuilderRedisClient.STATUS_RUNNING);
    }

    /** Call from Builders.onDisable(). */
    public void reportOffline() {
        redis.del(BuilderRedisClient.KEY_STATUS);
    }
}
```

- [ ] **Step 2: Commit**
```bash
cd ~/IdeaProjects/Builders
git add src/main/java/net/zanoria/builders/redis/BuilderRedisReporter.java
git commit -m "feat(builder): add BuilderRedisReporter for online/offline signalling"
```

---

## Task 10: BuilderIdleShutdown (Builders plugin)

**Files:**
- Create: `src/main/java/net/zanoria/builders/idle/BuilderIdleShutdown.java`

- [ ] **Step 1: Create BuilderIdleShutdown.java**

```java
package net.zanoria.builders.idle;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shuts the server down after N minutes with zero players online.
 * Resets the timer whenever a player joins.
 */
public final class BuilderIdleShutdown implements Listener {

    private final JavaPlugin plugin;
    private final int        idleMinutes;

    private BukkitTask shutdownTask;
    private long       idleSince = -1; // epoch ms, -1 = not idle

    public BuilderIdleShutdown(JavaPlugin plugin, int idleMinutes) {
        this.plugin      = plugin;
        this.idleMinutes = idleMinutes;
    }

    /** Register with Bukkit and start monitoring. Call from onEnable(). */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // If server starts with no players, begin idle countdown immediately
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            scheduleShutdown();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cancelShutdown();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Use runTaskLater to check after the player has actually left
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                scheduleShutdown();
            }
        }, 20L);
    }

    private void scheduleShutdown() {
        if (shutdownTask != null) return; // already scheduled

        idleSince = System.currentTimeMillis();
        long ticks = idleMinutes * 60L * 20L;

        shutdownTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getSLF4JLogger().info("[BuilderIdleShutdown] {} minutes idle — shutting down.", idleMinutes);
            Bukkit.shutdown();
        }, ticks);

        plugin.getSLF4JLogger().info("[BuilderIdleShutdown] Server idle — shutting down in {} minutes.", idleMinutes);
    }

    private void cancelShutdown() {
        if (shutdownTask != null) {
            shutdownTask.cancel();
            shutdownTask = null;
            idleSince    = -1;
            plugin.getSLF4JLogger().info("[BuilderIdleShutdown] Player joined — idle shutdown cancelled.");
        }
    }
}
```

- [ ] **Step 2: Commit**
```bash
cd ~/IdeaProjects/Builders
git add src/main/java/net/zanoria/builders/idle/BuilderIdleShutdown.java
git commit -m "feat(builder): add BuilderIdleShutdown — shuts down after N min idle"
```

---

## Task 11: Config + plugin.yml (Builders plugin)

**Files:**
- Create: `src/main/resources/config.yml`
- Modify: `src/main/resources/plugin.yml`

- [ ] **Step 1: Create config.yml**

Create `src/main/resources/config.yml`:
```yaml
redis:
  host: "172.18.0.1"
  port: 6379
  password: ""

idle-shutdown-minutes: 30
```

- [ ] **Step 2: Remove `builder` command from plugin.yml**

The `builder` command in the Builders plugin.yml was a different command (BuilderActivityCommand). Check what it does:

```bash
grep -n "builder\|Builder" ~/IdeaProjects/Builders/src/main/java/net/zanoria/builders/activity/BuilderActivityCommand.java | head -5
```

If it registers on the `builder` command alias, rename the command in plugin.yml to `builderactivity` to avoid conflict with ZanoriaLobby's `/builder`. Replace the `builder:` entry in plugin.yml:

```yaml
commands:
  map:
    description: Main builder command
  builderactivity:
    description: Builder activity command
  maps:
    description: Opens map menu
  backup:
    description: Backups for maps (admin command)
  builderadmin:
    description: Opens builder admin menu (admin command)
    aliases:
      - ba
  flyspeed:
    description: FlyMode
  mirror:
    description: MirrorMode
```

And update `BuilderActivityCommand.java` to register on `builderactivity`:
```java
PluginCommand cmd = plugin.getCommand("builderactivity");
```
(Check current registration in Builders.java and update accordingly.)

- [ ] **Step 3: Commit**
```bash
cd ~/IdeaProjects/Builders
git add src/main/resources/config.yml src/main/resources/plugin.yml
git commit -m "feat(builder): add config.yml and rename builder cmd to builderactivity"
```

---

## Task 12: Wire up in Builders.java

**Files:**
- Modify: `src/main/java/net/zanoria/builders/Builders.java`

- [ ] **Step 1: Add imports and fields**

Add after `private BuilderActivityManager activityManager;`:
```java
private net.zanoria.builders.redis.BuilderRedisClient   redisClient;
private net.zanoria.builders.redis.BuilderRedisReporter redisReporter;
```

- [ ] **Step 2: Init in onEnable() after existing setup**

Add at the END of `onEnable()`, before the closing `}`:
```java
// ── Redis reporter + idle shutdown ──────────────────────────────────
saveDefaultConfig();
String redisHost = getConfig().getString("redis.host", "172.18.0.1");
int    redisPort = getConfig().getInt("redis.port", 6379);
String redisPw   = getConfig().getString("redis.password", "");
int    idleMin   = getConfig().getInt("idle-shutdown-minutes", 30);

redisClient = new net.zanoria.builders.redis.BuilderRedisClient(redisHost, redisPort, redisPw);
try {
    redisClient.connect();
    redisReporter = new net.zanoria.builders.redis.BuilderRedisReporter(redisClient);
    redisReporter.reportOnline();
    getLogger().info("Redis connected and builder:status = running");
} catch (Exception e) {
    getLogger().warning("Redis unavailable — idle shutdown and status reporting disabled: " + e.getMessage());
}

new net.zanoria.builders.idle.BuilderIdleShutdown(this, idleMin).start();
```

- [ ] **Step 3: Cleanup in onDisable()**

Add at the START of `onDisable()`:
```java
if (redisReporter != null) redisReporter.reportOffline();
if (redisClient != null)   redisClient.close();
```

- [ ] **Step 4: Build**
```
cd ~/IdeaProjects/Builders
./gradlew shadowJar 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```bash
cd ~/IdeaProjects/Builders
git add src/main/java/net/zanoria/builders/Builders.java
git commit -m "feat(builder): wire up Redis reporter and idle shutdown in Builders"
```

---

## Task 13: Deploy to Hetzner Node

**Goal:** Get builder server running on the Hetzner node with the updated plugins.

- [ ] **Step 1: Build both JARs**
```bash
cd ~/IdeaProjects/ZanoriaLobby && ./gradlew shadowJar
cd ~/IdeaProjects/Builders && ./gradlew shadowJar
```

- [ ] **Step 2: Copy ZanoriaLobby JAR to lobby server on Hetzner**
```bash
wsl -d Ubuntu-22.04 bash -c "scp /mnt/c/Users/krinc/IdeaProjects/ZanoriaLobby/build/libs/ZanoriaLobby-1.0-SNAPSHOT.jar corwis@49.13.1.183:/home/corwis/pelican-daemon/d63415fb-0b17-43e8-92d3-ba408f0623c0/plugins/ZanoriaLobby.jar"
```

- [ ] **Step 3: Create builder server in Pelican Panel**

On `http://49.13.1.183:8080`:
- Create a new server: name `Builder`, egg `Zinth`, memory 2GB, port 25570
- Note the server UUID and short UUID

- [ ] **Step 4: Copy Builders JAR + create pelican-daemon dir**

After noting the builder server UUID from the Panel (call it `BUILDER_UUID`):
```bash
# Copy JAR to the builder server data dir
ssh corwis@49.13.1.183 "mkdir -p /home/corwis/pelican-daemon/\$BUILDER_UUID/plugins"
scp ~/IdeaProjects/Builders/build/libs/Builders-1.0-SNAPSHOT.jar corwis@49.13.1.183:/home/corwis/pelican-daemon/\$BUILDER_UUID/plugins/Builders.jar
# Copy WorldEdit too (required dependency)
scp [path/to/worldedit.jar] corwis@49.13.1.183:/home/corwis/pelican-daemon/\$BUILDER_UUID/plugins/
```

- [ ] **Step 5: Update velocity.toml on proxy**

Add to `/home/corwis/pelican-daemon/c81e8f1c-134b-40c0-86a0-7cf5773c4ff5/velocity.toml` under `[servers]`:
```toml
builder-1 = "172.18.0.x:25570"   # replace 172.18.0.x with actual Wings container IP
```

- [ ] **Step 6: Update ZanoriaLobby config on Hetzner**

Edit `/home/corwis/pelican-daemon/d63415fb-0b17-43e8-92d3-ba408f0623c0/plugins/ZanoriaLobby/config.yml`:
```yaml
builder:
  velocity-server-name: "builder-1"
  pelican-url: "http://172.18.0.1:8080"   # panel accessible from lobby container
  api-key: "<client API key from panel>"
  server-uuid: "<builder server short UUID>"
  redis:
    host: "172.18.0.1"
    port: 6379
    password: ""
```

- [ ] **Step 7: Restart proxy and lobby via Wings**
```bash
TOKEN=$(grep -m1 '^token:' /etc/pelican/config.yml | awk '{print $2}')
curl -s -X POST http://localhost:8081/api/servers/c81e8f1c-.../power -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"action":"restart"}'
curl -s -X POST http://localhost:8081/api/servers/d63415fb-.../power -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"action":"restart"}'
```

- [ ] **Step 8: Add yourself as builder and test**

In game on the lobby: `/builder add Corwis`

Then type `/builder` — expected: "Builder-Server wird gestartet..." then transfer.

---

## Task 14: Push to GitHub

- [ ] **Step 1: Push ZanoriaLobby**
```bash
cd ~/IdeaProjects/ZanoriaLobby
git remote -v  # verify remote exists, if not: git remote add origin <url>
git push origin main
```

- [ ] **Step 2: Push Builders**
```bash
cd ~/IdeaProjects/Builders
git remote -v
git push origin main
```

---

*Self-Review: All spec requirements covered — whitelist check ✅, /builder join ✅, /builder add/remove/list ✅, on-demand start ✅, 30min idle shutdown ✅, Redis status keys ✅, no Nexus changes ✅.*
