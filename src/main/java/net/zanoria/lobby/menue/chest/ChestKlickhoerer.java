package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Bildschirm;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Objects;

/**
 * Der Klick <b>innerhalb</b> eines Kistenmenues.
 *
 * <p>⚠️ Er liegt in {@code menue.chest} und nirgends sonst — {@code InventoryClickEvent} ist ein
 * Kisten-Typ, und {@code :kistenwaechter} macht den Bau rot, wenn er ausserhalb steht. Kommt
 * ZanUI, verschwindet diese Klasse mitsamt {@link ChestMenue}; <b>kein Hoerer ausserhalb wird
 * angefasst</b>.
 */
public final class ChestKlickhoerer implements Listener {

    private final ChestMenue menue;

    public ChestKlickhoerer(ChestMenue menue) {
        this.menue = Objects.requireNonNull(menue, "menue");
    }

    @EventHandler
    public void beimKlicken(InventoryClickEvent ereignis) {
        if (!(ereignis.getWhoClicked() instanceof Player spieler)) {
            return;
        }
        Bildschirm bildschirm = menue.offenFuer(spieler.getUniqueId());
        if (bildschirm == null) {
            // ⚠️ Kein offener Bildschirm heisst: dieses Inventar gehoert uns nicht. Wer hier
            // trotzdem abbraeche, machte JEDES Kisteninventar des Servers unbenutzbar.
            return;
        }

        // ⚠️ IMMER abbrechen, und zwar BEVOR irgendetwas anderes passiert - auch bei einem Klick
        // auf einen leeren Platz. Sonst nimmt der Spieler die Menue-Items mit, und in einer
        // Adventure-Lobby ist ein Item im Inventar der einzige Weg, doch noch etwas
        // kaputtzumachen.
        ereignis.setCancelled(true);

        int platz = ereignis.getRawSlot();
        if (platz < 0 || platz >= bildschirm.eintraege().size()) {
            return;
        }
        bildschirm.eintraege().get(platz).handlung().accept(spieler);
    }

    @EventHandler
    public void beimSchliessen(InventoryCloseEvent ereignis) {
        // ⚠️ Ohne das waechst die Karte in ChestMenue mit jedem Oeffnen weiter, und ein Spieler
        // haette sein Menue nach dem Schliessen weiter "offen" - jeder Klick in sein eigenes
        // Inventar liefe dann in den Zweig darueber.
        menue.schliesse(ereignis.getPlayer().getUniqueId());
    }
}
