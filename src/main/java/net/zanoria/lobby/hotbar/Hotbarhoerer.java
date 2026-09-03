package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Menue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/**
 * Der Klick <b>auf</b> ein Hotbar-Item oeffnet seinen Bildschirm.
 *
 * <h2>⚠️ Erkannt wird am PLATZ, nicht am Gegenstand</h2>
 *
 * <p>Die fuenf Plaetze sind fest und in {@link Hotbarplatz} gepinnt. Ueber
 * {@code getHeldItemSlot()} — ein {@code int} — ist die Erkennung ohne Vergleich von
 * {@code ItemStack}s moeglich. Das hat zwei Gruende, und beide zaehlen:
 *
 * <ul>
 *   <li>Ein Vergleich ueber {@code ItemStack} waere ohne laufenden Server nicht pruefbar —
 *       {@code new ItemStack(Material)} wirft (gemessen 2026-09-03, Registry-Grenze).</li>
 *   <li>Er waere ausserdem <b>falsch</b>, sobald ein Item umbenannt oder mit einer Nexo-Textur
 *       versehen wird: der Platz bleibt, der Gegenstand nicht.</li>
 * </ul>
 *
 * <p>⚠️ Dieser Hoerer kennt <b>keinen</b> Kisten-Typ. Er ruft {@link Menue#oeffne} — das ist die
 * Naht, und {@code :kistenwaechter} haelt maschinell fest, dass es so bleibt. Kommt ZanUI, wird
 * die Umsetzung getauscht und <b>diese Klasse nicht angefasst</b>.
 */
public final class Hotbarhoerer implements Listener {

    private final Menue menue;

    public Hotbarhoerer(Menue menue) {
        this.menue = Objects.requireNonNull(menue, "menue");
    }

    @EventHandler
    public void beimKlicken(PlayerInteractEvent ereignis) {
        // ⚠️ Nur Main Hand: sonst feuert das Ereignis doppelt (Main + Offhand) und das Menue
        // wird zweimal geoeffnet. Dieselbe Falle wie in ZanoriaLobby.onNpcInteract.
        if (ereignis.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        if (beiPlatz(spieler, spieler.getInventory().getHeldItemSlot())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Der rohe Teil — ohne {@code PlayerInteractEvent}.
     *
     * <p>Herausgezogen nach derselben Bauart wie {@code LobbyWeltschutz.beimBeitreten}: das
     * Bukkit-Ende liest, das rohe Ende entscheidet.
     *
     * @return ob wirklich ein Bildschirm geoeffnet wurde
     */
    public boolean beiPlatz(Player spieler, int platz) {
        for (Hotbarplatz eintrag : Hotbarplatz.values()) {
            if (eintrag.platz() == platz) {
                return menue.oeffne(spieler, Lobbybildschirme.fuer(eintrag));
            }
        }
        return false;
    }
}
