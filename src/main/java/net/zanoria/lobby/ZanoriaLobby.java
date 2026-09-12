package net.zanoria.lobby;

import net.zanoria.lobby.warteschlange.Warteschlangentakt;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.zanoria.nexus.NexusPlugin;
import net.zanoria.nexus.queue.QueueEntry;
import net.zanoria.nexus.queue.QueueService;
import net.zanoria.nexus.queue.QueueType;
import net.zanoria.zanlang.service.TranslationService;
import net.zanoria.zanlang.util.TranslationResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.zanoria.lobby.builder.BuilderCommand;
import net.zanoria.lobby.builder.BuilderPelicanStarter;
import net.zanoria.lobby.builder.BuilderRedisClient;
import net.zanoria.lobby.builder.BuilderServerService;
import net.zanoria.lobby.schutz.LobbyWeltschutz;
import net.zanoria.lobby.hotbar.Hotbarausgabe;
import net.zanoria.lobby.hotbar.Hotbarhoerer;
import net.zanoria.lobby.menue.chest.ChestKlickhoerer;
import net.zanoria.lobby.menue.chest.ChestMenue;
import net.zanoria.lobby.schutz.Lobbyschutz;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ZanoriaLobby extends JavaPlugin implements Listener {

    private static final String NPC_TAG = "zanoria_lobby_queue_npc";
    private static final String QUEUE_TAG_PREFIX = "zanoria_queue:";
    private static final String QUEUE_CHANNEL = "zanoria:queue";

    private final Map<String, QueueNpcDefinition> npcs = new HashMap<>();
    private final Map<UUID, BossBar> queueBars = new HashMap<>();
    /**
     * ⚠️ <b>ConcurrentHashMap, nicht HashMap</b> — und das haengt an 8b6feaa.
     *
     * <p>Bis dahin lief alles auf dem Hauptthread, da war eine {@code HashMap} richtig. Seit der
     * Takt die Spuren trennt, wird diese Karte auf der <b>Nebenspur geschrieben</b>
     * ({@code updateFullQueueTimers}: {@code putIfAbsent}/{@code remove}) und auf der
     * <b>Hauptspur gelesen</b> ({@code displaySeconds}: {@code get}).
     *
     * <p>Dass sich das ueberlappt, ist nicht hypothetisch: {@code updateBossBars} schedult
     * weiter und kehrt sofort zurueck, die Kette laeuft ueber Scheduler-Spruenge. Der
     * Redis-Teil darf hinter {@code RedisQueueStore.withLock} bis zu <b>2 s</b> spinnen, der
     * Takt feuert aber jede <b>Sekunde</b> — Tick N liest also noch, waehrend Tick N+1 schon
     * schreibt. Genau die Sperrenlast, gegen die 8b6feaa gebaut wurde, ist die Bedingung dafuer.
     *
     * <p>⚠️ {@code null} darf hier nie hinein — {@code ConcurrentHashMap} verbietet es. Gepruefte
     * Zugriffe: {@code putIfAbsent(type, now)} mit einem {@code long}, {@code remove(type)},
     * {@code get(type)}. Ein {@code get} auf einen fehlenden Schluessel liefert weiterhin
     * {@code null}, und {@code displaySeconds} rechnet damit.
     */
    private final Map<QueueType, Long> fullSinceByType = new ConcurrentHashMap<>();

    private BukkitTask bossBarTask;
    private QueueService queueService;
    private BuilderRedisClient   builderRedis;
    private BuilderServerService builderService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, QUEUE_CHANNEL);
        // ⚠️ HIER STAND BIS ZUM 2026-09-04 EIN disablePlugin(this). Der Erstlauf hat gezeigt,
        // was das kostet, und der Befund ist groesser als eine Verdrahtungsfrage:
        //
        //   Nexus braucht MySQL (127.0.0.1:3308). Faellt die Datenbank aus, schaltet Nexus sich
        //   ab; ZanoriaLobby fand dann keinen QueueService und schaltete sich EBENFALLS ab -
        //   mitsamt LOBBYSCHUTZ, Hotbar und Menues. In einer Lobby im Ueberlebensmodus heisst
        //   das: kein Blockschutz, weil eine DATENBANK weg ist.
        //
        // Gemessen am 2026-09-04, woertlich aus dem Erstlaufprotokoll:
        //   [Nexus] Database connection failed: Access denied for user 'root'@'172.19.0.1'
        //   [Nexus] Nexus failed to start - disabling.
        //   [ZanoriaLobby] NoClassDefFoundError: net/zanoria/nexus/NexusPlugin
        //                  at resolveQueueService(ZanoriaLobby.java:205)
        //
        // ⚠️ Der Schutz haengt jetzt an NICHTS ausser der Konfiguration. Ohne Nexus faellt
        // allein die WARTESCHLANGE aus - laut gemeldet, nicht stillschweigend -, und der Rest
        // der Lobby bleibt bedienbar. Ein Ausfall soll so klein sein wie seine Ursache.
        queueService = resolveQueueService();
        boolean warteschlangeLaeuft = queueService != null;
        if (!warteschlangeLaeuft) {
            getSLF4JLogger().error(
                    "ZanoriaLobby: Nexus QueueService nicht erreichbar. Die WARTESCHLANGE faellt"
                            + " aus - keine Queue-NPCs, keine BossBar, kein /lobbynpc."
                            + " Lobbyschutz, Hotbar und Menues laufen weiter."
                            + " Haeufigste Ursache: Nexus kommt nicht hoch, weil seine Datenbank"
                            + " fehlt - dann steht der Grund in den Nexus-Zeilen darueber.");
        }

        int npcsGesetzt = 0;
        if (warteschlangeLaeuft) {
            loadNpcDefinitions();
            removeSpawnedQueueNpcs();
            npcsGesetzt = spawnQueueNpcs();
        }
        getServer().getPluginManager().registerEvents(this, this);

        // ── Lobbyschutz ─────────────────────────────────────────────────────
        Lobbyschutz lobbyschutz = Lobbyschutz.aus(
                getConfig().getString("lobby.welt"),
                getServer().getWorlds().stream().map(World::getName).toList());
        if (lobbyschutz.faelltZu()) {
            // ⚠️ LAUT. Ein stiller Rueckfall auf "alles geschuetzt" waere zwar sicher, aber
            // niemand faende je den Tippfehler in der Konfiguration.
            getSLF4JLogger().error(
                    "ZanoriaLobby: die konfigurierte Lobbywelt '{}' existiert nicht."
                            + " Vorhanden sind: {}. Es werden vorsorglich ALLE Welten geschuetzt.",
                    getConfig().getString("lobby.welt"),
                    getServer().getWorlds().stream().map(World::getName).toList());
        }
        getServer().getPluginManager().registerEvents(new LobbyWeltschutz(lobbyschutz), this);

        // ── Menues und Hotbar ───────────────────────────────────────────────
        // ⚠️ ChestMenue ist EXPERIMENTELL, und diese drei Zeilen sind die EINZIGE Stelle im
        // Repo, die das weiss. Kommt ZanUI, wird hier getauscht - kein Hoerer wird angefasst.
        // Dass das so bleibt, haelt :kistenwaechter maschinell fest.
        ChestMenue chestMenue = new ChestMenue(getSLF4JLogger());
        getServer().getPluginManager().registerEvents(new ChestKlickhoerer(chestMenue), this);
        getServer().getPluginManager().registerEvents(new Hotbarhoerer(chestMenue), this);
        getServer().getPluginManager().registerEvents(new Hotbarausgabe(getSLF4JLogger()), this);

        if (warteschlangeLaeuft) {
            // ⚠️ Standing Rule 1: der Takt liest jede Sekunde Redis - getQueueSize je Modus
            // und getEntry je Online-Spieler. Synchron registriert (bis 2026-09-06
            // runTaskTimer) blockierte das den Hauptthread unbedingt, auch wenn niemand
            // wartet, mit Kosten in Spielerzahl x Modianzahl.
            bossBarTask = getServer().getScheduler()
                    .runTaskTimerAsynchronously(this, this::updateBossBars, 20L, 20L);

            LobbyNpcCommand npcCommand = new LobbyNpcCommand();
            var cmd = getCommand("lobbynpc");
            if (cmd != null) {
                cmd.setExecutor(npcCommand);
                cmd.setTabCompleter(npcCommand);
            }
        }

        // ⚠️ GESETZT, nicht npcs.size(). Die alte Fassung meldete die Konfigurationseintraege
        // und stand am 2026-08-13 auf "7", waehrend im Spiel kein einziger NPC existierte.
        if (warteschlangeLaeuft && npcsGesetzt < npcs.size()) {
            getSLF4JLogger().error(
                    "ZanoriaLobby: nur {} von {} Queue-NPCs gesetzt - die uebrigen fehlen im Spiel."
                            + " Ursache steht in den Zeilen darueber.",
                    npcsGesetzt, npcs.size());
        } else {
            getSLF4JLogger().info("ZanoriaLobby enabled with {} queue NPC(s) gesetzt.", npcsGesetzt);
        }

        // ── Builder subsystem ───────────────────────────────────────────────
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        String redisHost = environmentOrConfig("ZANORIA_REDIS_HOST", "builder.redis.host", "172.18.0.1");
        int redisPort = Integer.parseInt(environmentOrConfig("ZANORIA_REDIS_PORT", "builder.redis.port", "6379"));
        String redisUser = environmentOrConfig("ZANORIA_REDIS_USERNAME", "builder.redis.username", "default");
        String redisPw = environmentOrConfig("ZANORIA_REDIS_PASSWORD", "builder.redis.password", "");
        String pelicanUrl = getConfig().getString("builder.pelican-url", "");
        String apiKey     = getConfig().getString("builder.api-key", "");
        String uuid       = getConfig().getString("builder.server-uuid", "");
        String velServer  = getConfig().getString("builder.velocity-server-name", "builder-1");

        builderRedis = new BuilderRedisClient(redisHost, redisPort, redisUser, redisPw);
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
            var bc = new BuilderCommand(builderService);
            builderCmd.setExecutor(bc);
            builderCmd.setTabCompleter(bc);
        }
    }

    @Override
    public void onDisable() {
        if (builderService != null) builderService.shutdown();
        if (builderRedis != null)   builderRedis.close();
        removeSpawnedQueueNpcs();
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        // Nur Main Hand verarbeiten, sonst feuert das Event doppelt (Main + Offhand)
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        Entity entity = event.getRightClicked();
        if (!entity.getScoreboardTags().contains(NPC_TAG)) {
            return;
        }

        event.setCancelled(true);
        if (queueService == null) {
            return;
        }
        QueueNpcDefinition npc = findNpc(entity);
        if (npc == null) {
            return;
        }

        joinQueue(event.getPlayer(), npc);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // ⚠️ Ohne Nexus gibt es keine Warteschlange - und dann auch keine BossBar. Ein
        // ungeschuetzter Zugriff waere hier eine NullPointerException bei JEDEM Beitritt.
        if (queueService == null) {
            return;
        }
        // ⚠️ Standing Rule 1: isQueued liegt hinter Redis. Auf dem Hauptthread haette jeder
        // Beitritt daran gehangen - und beim Redis-Ausfall an der Zeitgrenze des Pools.
        Player beigetreten = event.getPlayer();
        Warteschlangentakt.fahreAktion(spurwechsel(),
                () -> queueService.isQueued(beigetreten.getUniqueId()),
                imWartestand -> {
                    if (Boolean.TRUE.equals(imWartestand)) {
                        showQueueBar(beigetreten);
                    }
                });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hideQueueBar(event.getPlayer());
    }

    /**
     * Der QueueService von Nexus, oder {@code null}.
     *
     * <p>⚠️ <b>Faengt {@code LinkageError} mit, und das ist gemessen, keine Vorsicht.</b> Ist
     * Nexus abgeschaltet, weil seine Datenbank fehlt, laedt schon das {@code instanceof
     * NexusPlugin} die Klasse - und das wirft {@code NoClassDefFoundError}, einen
     * {@code Error}. Ein {@code catch (Exception)} liesse ihn durch.
     *
     * <p>Gemessen am 2026-09-04 im Erstlauf, woertlich:
     * {@code NoClassDefFoundError: net/zanoria/nexus/NexusPlugin at resolveQueueService}. Der
     * Wurf riss das ganze onEnable mit - und damit den Lobbyschutz.
     */
    private QueueService resolveQueueService() {
        try {
            Plugin plugin = getServer().getPluginManager().getPlugin("Nexus");
            if (!(plugin instanceof NexusPlugin nexus)) {
                return null;
            }
            return nexus.getQueueService();
        } catch (RuntimeException | LinkageError fehler) {
            getSLF4JLogger().error(
                    "ZanoriaLobby: Nexus liess sich nicht abfragen ({}). Die Warteschlange faellt"
                            + " aus; der Rest der Lobby laeuft weiter.",
                    fehler.toString());
            return null;
        }
    }

    private String environmentOrConfig(String environment, String path, String fallback) {
        String value = System.getenv(environment);
        return value == null || value.isBlank() ? getConfig().getString(path, fallback) : value;
    }

    private void loadNpcDefinitions() {
        npcs.clear();
        ConfigurationSection root = getConfig().getConfigurationSection("npcs");
        if (root == null) {
            root = getConfig();
        }

        int index = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.isString("queue")) {
                continue;
            }

            QueueType queueType = parseQueueType(section.getString("queue"));
            if (queueType == null) {
                getSLF4JLogger().warn("Skipping queue NPC {} because queue '{}' is unknown.", id, section.getString("queue"));
                continue;
            }

            String mapId = section.getString("map", null);

            npcs.put(id.toLowerCase(Locale.ROOT), new QueueNpcDefinition(
                    id.toLowerCase(Locale.ROOT),
                    queueType,
                    section.getString("skin", ""),
                    section.getString("name", queueType.getDisplayName()),
                    resolveLocation(section, index++),
                    mapId
            ));
        }
    }

    private Location resolveLocation(ConfigurationSection section, int index) {
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            throw new IllegalStateException("No world loaded for lobby NPC spawn.");
        }

        Location spawn = world.getSpawnLocation().toCenterLocation();
        if (!section.contains("x") || !section.contains("y") || !section.contains("z")) {
            return spawn.add(index * 2.0, 0.0, 0.0);
        }

        Location configured = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
        return configured;
    }

    /**
     * Setzt die Warteschlangen-NPCs und liefert zurück, wie viele davon
     * <b>wirklich entstanden sind</b>.
     *
     * <p>⚠️ <b>Der Rückgabewert ist der eigentliche Fix, nicht das try/catch.</b> Vorher meldete
     * der Aufrufer {@code npcs.size()} — also die Zahl der <em>Konfigurationseinträge</em>. Am
     * 2026-08-13 stand deshalb <i>"enabled with 7 queue NPC(s)"</i> im Log, während im Spiel
     * <b>kein einziges Objekt existierte</b> ({@code execute if entity @e[tag=…]} fand nichts).
     * Eine Zählung, die zählt, was sie hineingesteckt hat statt was herauskam, meldet Erfolg
     * unabhängig vom Ergebnis.</p>
     *
     * <p>⚠️ Dieselbe Form wie in {@code PlayerManager.onPreLogin}: dort verschluckte ein
     * {@code catch (Exception)} ohne Logausgabe die Ursache eines Login-Abbruchs. Hier war es
     * eine Schleife ganz ohne {@code catch} — wirft der erste Aufruf, entstehen auch alle
     * folgenden nicht, und niemand erfährt warum.</p>
     */
    private int spawnQueueNpcs() {
        EntityType type = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENTITY_TYPE)
                .get(net.kyori.adventure.key.Key.key("minecraft", "mannequin"));
        if (type == null) type = EntityType.ARMOR_STAND;

        // ⚠️ ZUERST AUFRAEUMEN, DANN SETZEN. Bis 2026-08-14 trugen die NPCs
        // setPersistent(false) - damit werden sie beim Speichern der Welt verworfen, und im
        // Spiel stand keiner, waehrend das Log "6 gesetzt" meldete. Der Grund fuer das Flag war
        // richtig (sonst haeuft jeder Neustart Duplikate an), die Folge war es nicht.
        // Jetzt bleiben sie bestehen, und die Duplikate verhindert dieser Durchgang.
        int entfernt = 0;
        for (QueueNpcDefinition npc : npcs.values()) {
            World welt = npc.location().getWorld();
            if (welt == null) {
                continue;
            }
            for (Entity vorhanden : welt.getEntities()) {
                if (vorhanden.getScoreboardTags().contains(NPC_TAG)) {
                    vorhanden.remove();
                    entfernt++;
                }
            }
            break; // alle NPCs liegen in derselben Welt; ein Durchgang genuegt
        }
        if (entfernt > 0) {
            getSLF4JLogger().info("{} alte Queue-NPC(s) entfernt, bevor neu gesetzt wird.", entfernt);
        }

        int gesetzt = 0;
        for (QueueNpcDefinition npc : npcs.values()) {
            try {
            Entity entity = npc.location().getWorld().spawnEntity(npc.location(), type);
            entity.addScoreboardTag(NPC_TAG);
            entity.addScoreboardTag(QUEUE_TAG_PREFIX + npc.id());
            entity.customName(Component.text(npc.name(), NamedTextColor.AQUA));
            entity.setCustomNameVisible(true);
            // ⚠️ KEIN setPersistent(false). Siehe den Aufraeum-Durchgang oben: das Flag hat die
            // NPCs beim ersten Speichern der Welt verworfen.

            if (entity instanceof LivingEntity living) {
                living.setInvulnerable(true);
                living.setRemoveWhenFarAway(false);
                living.setGravity(false);
                applyArmorStandSkin(living, npc.skin());
            }

            // ArmorStand-Fallback: body verstecken
            if (entity instanceof ArmorStand armorStand) {
                armorStand.setArms(false);
                armorStand.setBasePlate(false);
                armorStand.setVisible(false);
            }

            // ⚠️ NACHSEHEN, NICHT DEM RUECKGABEWERT GLAUBEN. "spawnEntity kam zurueck" heisst
            // noch nicht "das Wesen existiert": am 2026-08-14 meldete diese Schleife sechs
            // gesetzte NPCs, waehrend die Welt nachweislich weder ein mannequin noch einen
            // armor_stand enthielt. Gezaehlt wird deshalb erst, wenn die Welt das Wesen
            // zurueckgibt - das ist der Unterschied zwischen "gesetzt" und "vorhanden".
            World welt = npc.location().getWorld();
            boolean vorhanden = entity.isValid()
                    && welt != null
                    && welt.getEntity(entity.getUniqueId()) != null;
            if (vorhanden) {
                gesetzt++;
            } else {
                getSLF4JLogger().error(
                        "Queue-NPC '{}' wurde gesetzt, ist danach aber nicht in der Welt"
                                + " (valid={}, von der Welt gefunden={}). Er wird im Spiel fehlen.",
                        npc.id(), entity.isValid(),
                        welt != null && welt.getEntity(entity.getUniqueId()) != null);
            }
            } catch (Exception fehler) {
                // ⚠️ Je NPC fangen, nicht um die ganze Schleife: sonst nimmt der erste
                // Fehlschlag alle folgenden mit, und im Log steht nichts darueber.
                Location ort = npc.location();
                getSLF4JLogger().error(
                        "Queue-NPC '{}' konnte nicht gesetzt werden (Welt={} x={} y={} z={}, Typ={})",
                        npc.id(),
                        ort.getWorld() != null ? ort.getWorld().getName() : "?",
                        ort.getX(), ort.getY(), ort.getZ(), type,
                        fehler);
            }
        }
        return gesetzt;
    }

    private void applyArmorStandSkin(LivingEntity entity, String skin) {
        if (skin == null || skin.isBlank()) {
            return;
        }

        // Versuche zuerst den Online-Spieler (gecachte Textur)
        Player online = Bukkit.getPlayerExact(skin);
        if (online != null) {
            applySkinProfile(entity, online.getPlayerProfile());
            return;
        }

        // Async Mojang-Lookup für Offline-Spieler
        PlayerProfile profile = Bukkit.createProfile(skin);
        profile.update().thenAccept(completed -> getServer().getScheduler().runTask(this, () -> {
            if (entity.isValid()) {
                applySkinProfile(entity, completed);
            }
        }));
    }

    private void applySkinProfile(LivingEntity entity, PlayerProfile profile) {
        // Mannequin: Skin direkt über ResolvableProfile setzen
        if (entity instanceof org.bukkit.entity.Mannequin mannequin) {
            io.papermc.paper.datacomponent.item.ResolvableProfile resolvable =
                    io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(profile);
            mannequin.setProfile(resolvable);
            return;
        }
        // Fallback ArmorStand: Player Head als Helm
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
            entity.getEquipment().setHelmet(head);
        }
    }

    private EntityType findEntityType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private QueueNpcDefinition findNpc(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (!tag.startsWith(QUEUE_TAG_PREFIX)) {
                continue;
            }
            return npcs.get(tag.substring(QUEUE_TAG_PREFIX.length()));
        }
        return null;
    }

    /**
     * ⚠️ Standing Rule 1, die haeufigste Spieleraktion der Lobby. {@code isQueued}, {@code leave}
     * und {@code join} liegen hinter {@code RedisQueueStore.withLock} — einer netzweiten Sperre
     * mit bis zu 2 s Spin, waehrend der Matchmaker dieselbe Sperre alle 250 ms anfasst. Bis zum
     * 2026-09-06 lief das blockierend auf dem Hauptthread.
     *
     * <p>{@code sendQueueRequest} bleibt bewusst auf der Hauptspur: es ist
     * {@code player.sendPluginMessage} an Velocity und blockiert nicht.
     */
    private void joinQueue(Player player, QueueNpcDefinition npc) {
        UUID playerId = player.getUniqueId();
        Warteschlangentakt.fahreAktion(spurwechsel(),
                () -> queueService.isQueued(playerId),
                imWartestand -> beitrittFortsetzen(player, npc, Boolean.TRUE.equals(imWartestand)));
    }

    /** Hauptspur: entscheiden und melden; was noch einmal Redis braucht, geht wieder abseits. */
    private void beitrittFortsetzen(Player player, QueueNpcDefinition npc, boolean imWartestand) {
        UUID playerId = player.getUniqueId();

        // Bereits in einer Queue → verlassen
        if (imWartestand) {
            if (sendQueueRequest(player, npc, false)) {
                hideQueueBar(player);
                t(player, "lobby.queue.left").send();
                updateBossBars();
                return;
            }
            Warteschlangentakt.fahreAktion(spurwechsel(),
                    () -> queueService.leave(playerId),
                    ignoriert -> {
                        hideQueueBar(player);
                        t(player, "lobby.queue.left").send();
                        updateBossBars();
                    });
            return;
        }

        if (sendQueueRequest(player, npc, true)) {
            showQueueBar(player);
            updateBossBars();
            return;
        }

        Warteschlangentakt.fahreAktion(spurwechsel(),
                () -> queueService.join(playerId, npc.queueType(), npc.mapId()),
                beigetreten -> {
                    if (!Boolean.TRUE.equals(beigetreten)) {
                        t(player, "lobby.queue.failed").send();
                        return;
                    }
                    var joinMsg = t(player, "lobby.queue.joined")
                            .variable("type", npc.queueType().getDisplayName());
                    if (npc.hasMap()) {
                        joinMsg.variable("map", npc.mapId());
                    }
                    joinMsg.send();
                    showQueueBar(player);
                    updateBossBars();
                });
    }

    private boolean sendQueueRequest(Player player, QueueNpcDefinition npc, boolean joining) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(npc.queueType().name());
                output.writeUTF(npc.hasMap() ? npc.mapId() : "");
            }
            player.sendPluginMessage(this, QUEUE_CHANNEL, bytes.toByteArray());
            if (joining) {
                var message = t(player, "lobby.queue.joined")
                        .variable("type", npc.queueType().getDisplayName());
                if (npc.hasMap()) {
                    message.variable("map", npc.mapId());
                }
                message.send();
            } else {
                t(player, "lobby.queue.left").send();
            }
            return true;
        } catch (Exception exception) {
            getSLF4JLogger().warn("Could not send queue request to Velocity: {}", exception.getMessage());
            return false;
        }
    }

    private void showQueueBar(Player player) {
        queueBars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar bossBar = BossBar.bossBar(
                    Component.text("Queue", NamedTextColor.AQUA),
                    0.0f,
                    BossBar.Color.BLUE,
                    BossBar.Overlay.PROGRESS
            );
            player.showBossBar(bossBar);
            return bossBar;
        });
    }

    private void hideQueueBar(Player player) {
        BossBar bossBar = queueBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    /**
     * Ein Takt des Warteschlangen-Anzeigers.
     *
     * <p>⚠️ Standing Rule 1. Bis zum 2026-09-06 lief diese Methode synchron auf dem Hauptthread
     * und las dabei jede Sekunde Redis: {@code getQueueSize} je Modus plus {@code getEntry} je
     * Online-Spieler, unbedingt, auch wenn niemand wartet. Jetzt liegt das Lesen auf der
     * Nebenspur — und weil Bukkits API nicht threadsicher ist, liegen die Spielerliste und
     * jede BossBar-Aenderung weiterhin auf dem Hauptthread. Die Reihenfolge haelt
     * {@link net.zanoria.lobby.warteschlange.Warteschlangentakt} fest, gemessen von
     * {@code DieSpurdisziplinIstGemessenTest}.
     */
    private void updateBossBars() {
        if (queueService == null) {
            return;
        }
        Warteschlangentakt.fahre(spurwechsel(),
                this::onlineSpielerIds,
                this::warteschlangenstandLesen,
                this::bossBarsAnwenden);
    }

    /** Die beiden Spuren, wie der Bukkit-Scheduler sie anbietet. */
    private Warteschlangentakt.Ausfuehrung spurwechsel() {
        return new Warteschlangentakt.Ausfuehrung() {
            @Override
            public void abseits(Runnable arbeit) {
                getServer().getScheduler().runTaskAsynchronously(ZanoriaLobby.this, arbeit);
            }

            @Override
            public void haupt(Runnable arbeit) {
                getServer().getScheduler().runTask(ZanoriaLobby.this, arbeit);
            }
        };
    }

    /** Hauptspur: Bukkit fragen, wer online ist. */
    private List<UUID> onlineSpielerIds() {
        List<UUID> ids = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ids.add(player.getUniqueId());
        }
        return ids;
    }

    /** Nebenspur: alles, was Redis anfasst — und sonst nichts. */
    private Warteschlangenstand warteschlangenstandLesen(List<UUID> online) {
        updateFullQueueTimers();
        Map<QueueType, Integer> groessen = new HashMap<>();
        for (QueueType type : QueueType.values()) {
            groessen.put(type, queueService.getQueueSize(type));
        }
        Map<UUID, QueueEntry> eintraege = new HashMap<>();
        Map<UUID, Long> wartezeiten = new HashMap<>();
        for (UUID id : online) {
            QueueEntry entry = queueService.getEntry(id);
            if (entry != null) {
                eintraege.put(id, entry);
                // ⚠️ Auch getQueueTime ist Redis. Bis zum 2026-09-06 las displaySeconds es auf
                // dem Hauptthread nach - der Takt war umgestellt, diese eine Zeile nicht.
                wartezeiten.put(id, queueService.getQueueTime(id));
            }
        }
        return new Warteschlangenstand(groessen, eintraege, wartezeiten);
    }

    /** Hauptspur: alles, was Bukkit anfasst — und sonst nichts. */
    private void bossBarsAnwenden(Warteschlangenstand stand) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            QueueEntry entry = stand.eintraege().get(player.getUniqueId());
            if (entry == null) {
                hideQueueBar(player);
                continue;
            }

            BossBar bossBar = queueBars.get(player.getUniqueId());
            if (bossBar == null) {
                showQueueBar(player);
                bossBar = queueBars.get(player.getUniqueId());
            }
            if (bossBar == null) {
                continue;
            }

            QueueType type = entry.type();
            int players = stand.groessen().getOrDefault(type, 0);
            int maxPlayers = type.getMaxPlayers();
            long seconds = displaySeconds(type, stand.wartezeiten().getOrDefault(player.getUniqueId(), 0L));
            float progress = Math.min(1.0f, Math.max(0.0f, players / (float) maxPlayers));

            bossBar.name(queueTitle(type, players, maxPlayers, seconds));
            bossBar.progress(progress);
            bossBar.color(players >= maxPlayers ? BossBar.Color.GREEN : BossBar.Color.BLUE);
        }
    }

    /** Was ein Takt auf der Nebenspur gelesen hat. */
    private record Warteschlangenstand(Map<QueueType, Integer> groessen,
                                       Map<UUID, QueueEntry> eintraege,
                                       Map<UUID, Long> wartezeiten) {
    }

    private void updateFullQueueTimers() {
        long now = System.currentTimeMillis();
        for (QueueType type : QueueType.values()) {
            if (queueService.getQueueSize(type) >= type.getMaxPlayers()) {
                fullSinceByType.putIfAbsent(type, now);
            } else {
                fullSinceByType.remove(type);
            }
        }
    }

    /**
     * ⚠️ Nimmt die Wartezeit als Wert entgegen, statt sie selbst zu lesen: der Aufrufer
     * {@code bossBarsAnwenden} laeuft auf dem Hauptthread, und {@code getQueueTime} ist Redis.
     */
    private long displaySeconds(QueueType type, long queueTime) {
        Long fullSince = fullSinceByType.get(type);
        if (fullSince != null) {
            long elapsedSeconds = (System.currentTimeMillis() - fullSince) / 1000L;
            return Math.max(1L, 5L - elapsedSeconds);
        }

        return Math.max(0L, queueTime / 1000L);
    }

    private Component queueTitle(QueueType type, int players, int maxPlayers, long seconds) {
        return Component.text(type.getDisplayName(), NamedTextColor.AQUA)
                .append(Component.text(" ● ", NamedTextColor.DARK_GRAY))
                .append(Component.text(players, NamedTextColor.GREEN))
                .append(Component.text("/", NamedTextColor.DARK_GRAY))
                .append(Component.text(maxPlayers, NamedTextColor.GREEN))
                .append(Component.text(" ● ", NamedTextColor.DARK_GRAY))
                .append(Component.text(seconds + "s", NamedTextColor.AQUA));
    }

    private void removeSpawnedQueueNpcs() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(NPC_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private QueueType parseQueueType(String input) {
        if (input == null) {
            return null;
        }
        try {
            return QueueType.valueOf(input);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // ZanLang helper
    // -----------------------------------------------------------------------

    private TranslationResult t(Player player, String key) {
        TranslationService service = getServer().getServicesManager().load(TranslationService.class);
        if (service == null) throw new IllegalStateException("ZanLang TranslationService not available.");
        return service.translate(player, key);
    }

    private record QueueNpcDefinition(
            String id,
            QueueType queueType,
            String skin,
            String name,
            Location location,
            String mapId        // nullable — null means random
    ) {
        boolean hasMap() { return mapId != null && !mapId.isBlank(); }
    }

    // -----------------------------------------------------------------------
    // /lobbynpc command
    // -----------------------------------------------------------------------

    private final class LobbyNpcCommand implements CommandExecutor, TabCompleter {

        private static final String PERM = "zanoria.lobby.npc";

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission(PERM)) {
                if (sender instanceof Player p) t(p, "lobby.npc.noperm").send();
                else sender.sendMessage(Component.text("No permission."));
                return true;
            }

            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set"    -> cmdSet(sender, args);
                case "list"   -> cmdList(sender);
                case "reload" -> cmdReload(sender);
                default       -> { sendHelp(sender); yield true; }
            };
        }

        private boolean cmdSet(CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                if (sender instanceof Player p) t(p, "lobby.npc.set.playeronly").send();
                else sender.sendMessage(Component.text("Players only."));
                return true;
            }
            if (args.length < 2) {
                t(player, "lobby.npc.set.usage").send();
                return true;
            }

            String id = args[1].toLowerCase(Locale.ROOT);
            ConfigurationSection root = getConfig().getConfigurationSection("npcs") != null
                    ? getConfig().getConfigurationSection("npcs") : getConfig();

            if (!root.contains(id)) {
                t(player, "lobby.npc.set.invalid").variable("id", id).send();
                return true;
            }

            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                t(player, "lobby.npc.set.invalid").variable("id", id).send();
                return true;
            }

            Location loc = player.getLocation();
            section.set("world", loc.getWorld().getName());
            section.set("x", Math.round(loc.getX() * 100.0) / 100.0);
            section.set("y", Math.round(loc.getY() * 100.0) / 100.0);
            section.set("z", Math.round(loc.getZ() * 100.0) / 100.0);
            section.set("yaw",   Math.round(loc.getYaw()   * 10.0) / 10.0);
            section.set("pitch", Math.round(loc.getPitch() * 10.0) / 10.0);
            saveConfig();

            removeSpawnedQueueNpcs();
            loadNpcDefinitions();
            spawnQueueNpcs();

            String pos = String.format("%.1f/%.1f/%.1f", loc.getX(), loc.getY(), loc.getZ());
            t(player, "lobby.npc.set").variable("id", id).variable("pos", pos).send();
            return true;
        }

        private boolean cmdList(CommandSender sender) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Players only."));
                return true;
            }
            if (npcs.isEmpty()) {
                t(player, "lobby.npc.list.empty").send();
                return true;
            }
            t(player, "lobby.npc.list.header").send();
            for (QueueNpcDefinition npc : npcs.values()) {
                Location l = npc.location();
                t(player, "lobby.npc.list.entry")
                        .variable("id",    npc.id())
                        .variable("type",  npc.queueType().name())
                        .variable("world", l.getWorld() != null ? l.getWorld().getName() : "?")
                        .variable("x",     String.format("%.1f", l.getX()))
                        .variable("y",     String.format("%.1f", l.getY()))
                        .variable("z",     String.format("%.1f", l.getZ()))
                        .variable("yaw",   String.format("%.1f", l.getYaw()))
                        .send();
            }
            return true;
        }

        private boolean cmdReload(CommandSender sender) {
            reloadConfig();
            removeSpawnedQueueNpcs();
            loadNpcDefinitions();
            // ⚠️ Dieselbe Falle wie beim Start: gemeldet wird, was GESETZT wurde, nicht was in
            // der Konfiguration steht. Sonst bestaetigt /lobbynpc reload einen Erfolg, den der
            // Ausfuehrende danach im Spiel vergeblich sucht.
            int gesetzt = spawnQueueNpcs();
            if (sender instanceof Player player) {
                t(player, "lobby.npc.reload").variable("count", String.valueOf(gesetzt)).send();
                if (gesetzt < npcs.size()) {
                    player.sendMessage(Component.text(
                            "⚠ Nur " + gesetzt + " von " + npcs.size()
                                    + " NPCs gesetzt - Ursache steht in der Serverkonsole.",
                            NamedTextColor.RED));
                }
            } else {
                sender.sendMessage(Component.text(
                        "NPCs reloaded (" + gesetzt + " von " + npcs.size() + " gesetzt)."));
            }
            return true;
        }

        private void sendHelp(CommandSender sender) {
            if (!(sender instanceof Player player)) { return; }
            t(player, "lobby.npc.list.header").send();
            t(player, "lobby.npc.help.set").send();
            t(player, "lobby.npc.help.list").send();
            t(player, "lobby.npc.help.reload").send();
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission(PERM)) return List.of();
            if (args.length == 1) {
                return List.of("set", "list", "reload").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                ConfigurationSection root = getConfig().getConfigurationSection("npcs") != null
                        ? getConfig().getConfigurationSection("npcs")
                        : getConfig();
                List<String> ids = new ArrayList<>(root.getKeys(false));
                return ids.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            return List.of();
        }
    }
}
