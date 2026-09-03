package net.zanoria.lobby.schutz;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/**
 * Adventure-Modus und Blockschutz in der Lobbywelt.
 *
 * <p>⚠️ <b>Beides, nicht nur der Modus.</b> Adventure allein laesst den Abbau mit passendem
 * Werkzeug zu - ein Spieler mit einer Spitzhacke im Inventar braeche sonst Bloecke ab.
 *
 * <p>⚠️ Dieser Hoerer entscheidet NICHTS. Er liest die Welt und fragt {@link Lobbyschutz}. Der
 * Grund steht dort: die Regel ist ohne Server pruefbar, ein Hoererrumpf nur mit Muehe.
 *
 * <p>⚠️ <b>Kein Bypass-Recht, und das ist eine Entscheidung.</b> Wer bauen will, geht auf den
 * Builder-Server. Ein Bypass hier waere ein zweiter Bauweg neben genau dem, den Stueck 4 absichert.
 */
public final class LobbyWeltschutz implements Listener {

    private final Lobbyschutz schutz;

    public LobbyWeltschutz(Lobbyschutz schutz) {
        this.schutz = Objects.requireNonNull(schutz, "schutz");
    }

    @EventHandler
    public void beimAbbauen(BlockBreakEvent ereignis) {
        if (schutz.schuetzt(ereignis.getBlock().getWorld().getName())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler
    public void beimSetzen(BlockPlaceEvent ereignis) {
        if (schutz.schuetzt(ereignis.getBlock().getWorld().getName())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler
    public void beiBeitritt(PlayerJoinEvent ereignis) {
        beimBeitreten(ereignis.getPlayer());
    }

    /**
     * Der rohe Teil des Beitritts - ohne {@code PlayerJoinEvent}.
     *
     * <p>⚠️ Herausgezogen nach derselben Bauart wie {@code NexusStrikeMatch.beiTod(UUID, long)}:
     * das Bukkit-Ende liest, das rohe Ende entscheidet.
     */
    public void beimBeitreten(Player spieler) {
        if (schutz.schuetzt(spieler.getWorld().getName())) {
            spieler.setGameMode(GameMode.ADVENTURE);
        }
    }
}
