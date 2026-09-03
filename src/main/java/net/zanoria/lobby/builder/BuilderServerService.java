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
 * 1. Whitelist check
 * 2. Start server if not running
 * 3. Poll until running, then transfer player
 */
public final class BuilderServerService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX  = "<gradient:#FF702B:#FCC650>Builder</gradient> <dark_gray>» <reset>";

    private static final int POLL_INTERVAL_TICKS = 40;  // 2s
    private static final int MAX_POLLS           = 30;  // 60s total

    private final JavaPlugin            plugin;
    private final BuilderRedisClient    redis;
    private final BuilderPelicanStarter pelican;
    private final String                velocityServerName;

    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    public BuilderServerService(JavaPlugin plugin, BuilderRedisClient redis,
                                BuilderPelicanStarter pelican, String velocityServerName) {
        this.plugin             = plugin;
        this.redis              = redis;
        this.pelican            = pelican;
        this.velocityServerName = velocityServerName;
    }

    public void joinBuilderServer(Player player) {
        // ⚠️ Die BERECHTIGUNG hat der Befehl schon geprueft (plugin.yml: permission:
        // zanoria.builder, vergeben von Nexus an OWNER/CO_OWNER/ADMIN). Hier steht die ZWEITE
        // Bedingung, und sie ist eine andere Frage: hat der Zielserver ueberhaupt das Werkzeug,
        // fuer das der Spieler hingeschickt wird?
        //
        // ⚠️ Hier stand bis zum 2026-09-03 eine Pruefung gegen redis.isMember(getName()) - eine
        // Namensliste. Sie ist stillgelegt, nicht geloescht; der Grund steht ueber
        // BuilderRedisClient.KEY_MEMBERS. Wer sie zurueckverdrahtet, macht das Tor wieder
        // namensabhaengig, und DasBuildertorIstEineBerechtigungTest wird rot.
        String worldedit = redis.getWorldedit();
        if (worldedit == null || worldedit.isBlank() || "FEHLT".equals(worldedit)) {
            // ⚠️ FAELLT ZU. Ein fehlender Schluessel ist KEIN Grund durchzulassen: der Spieler
            // landete sonst auf einem Server ohne FAWE - also ohne den Grund seiner Reise -, und
            // wuerde dort feststellen, dass kein einziger Befehl geht.
            player.sendMessage(MM.deserialize(PREFIX + "<red>Der Builder-Server meldet kein"
                    + " WorldEdit/FAWE (" + (worldedit == null ? "keine Meldung" : worldedit)
                    + "). Der Zutritt bleibt zu, bis das behoben ist."));
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(MM.deserialize(PREFIX + "<yellow>Server startet bereits, bitte warte..."));
            return;
        }

        String status = redis.getStatus();

        if (BuilderRedisClient.STATUS_RUNNING.equals(status)) {
            transfer(player);
        } else if (BuilderRedisClient.STATUS_STARTING.equals(status)) {
            player.sendMessage(MM.deserialize(PREFIX + "<yellow>Server startet, du wirst automatisch verbunden..."));
            startPolling(player);
        } else {
            if (!pelican.isConfigured()) {
                player.sendMessage(MM.deserialize(PREFIX + "<red>Builder-Server nicht konfiguriert. Bitte kontaktiere einen Admin."));
                return;
            }
            // Atomic SET NX — only first caller proceeds, others see "starting"
            if (!redis.setStatusIfAbsent(BuilderRedisClient.STATUS_STARTING)) {
                // Another player already triggered the start — just poll
                player.sendMessage(MM.deserialize(PREFIX + "<yellow>Server startet bereits, du wirst automatisch verbunden..."));
                startPolling(player);
                return;
            }
            player.sendMessage(MM.deserialize(PREFIX + "<yellow>Builder-Server wird gestartet..."));
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
            if (!player.isOnline()) { cancelPending(player.getUniqueId()); return; }
            if (BuilderRedisClient.STATUS_RUNNING.equals(redis.getStatus())) {
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
