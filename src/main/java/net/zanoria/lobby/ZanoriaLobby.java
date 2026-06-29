package net.zanoria.lobby;

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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ZanoriaLobby extends JavaPlugin implements Listener {

    private static final String NPC_TAG = "zanoria_lobby_queue_npc";
    private static final String QUEUE_TAG_PREFIX = "zanoria_queue:";
    private static final String QUEUE_CHANNEL = "zanoria:queue";

    private final Map<String, QueueNpcDefinition> npcs = new HashMap<>();
    private final Map<UUID, BossBar> queueBars = new HashMap<>();
    private final Map<QueueType, Long> fullSinceByType = new HashMap<>();

    private BukkitTask bossBarTask;
    private QueueService queueService;
    private BuilderRedisClient   builderRedis;
    private BuilderServerService builderService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, QUEUE_CHANNEL);
        queueService = resolveQueueService();
        if (queueService == null) {
            getSLF4JLogger().error("Nexus QueueService unavailable - disabling ZanoriaLobby.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadNpcDefinitions();
        removeSpawnedQueueNpcs();
        spawnQueueNpcs();
        getServer().getPluginManager().registerEvents(this, this);
        bossBarTask = getServer().getScheduler().runTaskTimer(this, this::updateBossBars, 20L, 20L);

        LobbyNpcCommand npcCommand = new LobbyNpcCommand();
        var cmd = getCommand("lobbynpc");
        if (cmd != null) {
            cmd.setExecutor(npcCommand);
            cmd.setTabCompleter(npcCommand);
        }

        getSLF4JLogger().info("ZanoriaLobby enabled with {} queue NPC(s).", npcs.size());

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
            var bc = new BuilderCommand(builderService, builderRedis);
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
        QueueNpcDefinition npc = findNpc(entity);
        if (npc == null) {
            return;
        }

        joinQueue(event.getPlayer(), npc);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (queueService.isQueued(event.getPlayer().getUniqueId())) {
            showQueueBar(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hideQueueBar(event.getPlayer());
    }

    private QueueService resolveQueueService() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Nexus");
        if (!(plugin instanceof NexusPlugin nexus)) {
            return null;
        }
        return nexus.getQueueService();
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

    private void spawnQueueNpcs() {
        EntityType type = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENTITY_TYPE)
                .get(net.kyori.adventure.key.Key.key("minecraft", "mannequin"));
        if (type == null) type = EntityType.ARMOR_STAND;

        for (QueueNpcDefinition npc : npcs.values()) {
            Entity entity = npc.location().getWorld().spawnEntity(npc.location(), type);
            entity.addScoreboardTag(NPC_TAG);
            entity.addScoreboardTag(QUEUE_TAG_PREFIX + npc.id());
            entity.customName(Component.text(npc.name(), NamedTextColor.AQUA));
            entity.setCustomNameVisible(true);
            entity.setPersistent(false);

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
        }
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

    private void joinQueue(Player player, QueueNpcDefinition npc) {
        UUID playerId = player.getUniqueId();

        // Bereits in einer Queue → verlassen
        if (queueService.isQueued(playerId)) {
            if (sendQueueRequest(player, npc, false)) {
                hideQueueBar(player);
                t(player, "lobby.queue.left").send();
                updateBossBars();
                return;
            }
            queueService.leave(playerId);
            hideQueueBar(player);
            t(player, "lobby.queue.left").send();
            updateBossBars();
            return;
        }

        if (sendQueueRequest(player, npc, true)) {
            showQueueBar(player);
            updateBossBars();
            return;
        }

        if (!queueService.join(playerId, npc.queueType(), npc.mapId())) {
            t(player, "lobby.queue.failed").send();
            return;
        }

        var joinMsg = t(player, "lobby.queue.joined").variable("type", npc.queueType().getDisplayName());
        if (npc.hasMap()) {
            joinMsg.variable("map", npc.mapId());
        }
        joinMsg.send();
        showQueueBar(player);
        updateBossBars();
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

    private void updateBossBars() {
        updateFullQueueTimers();

        for (Player player : Bukkit.getOnlinePlayers()) {
            QueueEntry entry = queueService.getEntry(player.getUniqueId());
            if (entry == null) {
                hideQueueBar(player);
                continue;
            }

            BossBar bossBar = queueBars.get(player.getUniqueId());
            if (bossBar == null) {
                showQueueBar(player);
                bossBar = queueBars.get(player.getUniqueId());
            }

            QueueType type = entry.type();
            int players = queueService.getQueueSize(type);
            int maxPlayers = type.getMaxPlayers();
            long seconds = displaySeconds(player.getUniqueId(), type);
            float progress = Math.min(1.0f, Math.max(0.0f, players / (float) maxPlayers));

            bossBar.name(queueTitle(type, players, maxPlayers, seconds));
            bossBar.progress(progress);
            bossBar.color(players >= maxPlayers ? BossBar.Color.GREEN : BossBar.Color.BLUE);
        }
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

    private long displaySeconds(UUID playerId, QueueType type) {
        Long fullSince = fullSinceByType.get(type);
        if (fullSince != null) {
            long elapsedSeconds = (System.currentTimeMillis() - fullSince) / 1000L;
            return Math.max(1L, 5L - elapsedSeconds);
        }

        long queueTime = queueService.getQueueTime(playerId);
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
            spawnQueueNpcs();
            if (sender instanceof Player player) {
                t(player, "lobby.npc.reload").variable("count", String.valueOf(npcs.size())).send();
            } else {
                sender.sendMessage(Component.text("NPCs reloaded (" + npcs.size() + ")."));
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
