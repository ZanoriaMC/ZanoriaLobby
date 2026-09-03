package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.chest.Materialwahl;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Vergibt die fuenf Hotbar-Items beim Beitritt.
 *
 * <h2>⚠️ Was hier geprueft ist und was nicht</h2>
 *
 * <p>Die <b>Zuordnung</b> ({@link #plan()}) ist ohne Server pruefbar; das <b>Erzeugen</b> der
 * Gegenstaende ist es nicht — {@code new ItemStack(Material)} faellt in die Registry-Grenze
 * (gemessen 2026-09-03). Die Trennung ist Absicht: so misst ein Einheitstest wenigstens das, was
 * er messen kann, statt gar nichts oder das Falsche.
 *
 * <p><b>Dass die Items wirklich im Inventar landen, beantwortet allein der Erstlauf.</b>
 */
public final class Hotbarausgabe implements Listener {

    /** ⚠️ Derselbe Notnagel wie in {@code ChestMenue} — ein unbekannter Rueckfall wirft nicht. */
    private static final String NOTNAGEL = "STONE";

    private final Logger log;

    public Hotbarausgabe(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Welcher Eintrag auf welchem Platz liegt.
     *
     * <p>⚠️ <b>ABGELEITET, nicht aufgeschrieben.</b> Eine gepflegte Liste wuerde beim naechsten
     * Eintrag selbst zur Luege — dieselbe Lehre wie {@code erwartete_hoerer()} im Erstlauf: eine
     * Erwartung, die mit dem Ausfall mitschrumpft, hat sich selbst abgemeldet.
     */
    public static Map<Integer, Hotbarplatz> plan() {
        Map<Integer, Hotbarplatz> plan = new LinkedHashMap<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            plan.put(platz.platz(), platz);
        }
        return Map.copyOf(plan);
    }

    @EventHandler
    public void beiBeitritt(PlayerJoinEvent ereignis) {
        gib(ereignis.getPlayer());
    }

    /** Der rohe Teil — ohne {@code PlayerJoinEvent}. */
    public void gib(Player spieler) {
        plan().forEach((platz, eintrag) -> {
            try {
                spieler.getInventory().setItem(platz, stapel(eintrag));
            } catch (RuntimeException | LinkageError fehler) {
                // ⚠️ JE ITEM fangen, nicht um die ganze Schleife: sonst nimmt der erste
                // Fehlschlag alle folgenden mit, und im Log steht nichts darueber. Dieselbe
                // Lehre wie spawnQueueNpcs in ZanoriaLobby, wo eine Schleife ohne catch beim
                // ersten Wurf alle uebrigen NPCs mitriss.
                // ⚠️ LinkageError mit, es ist ein Error - siehe ChestMenue.
                log.error("Hotbar-Item {} (Platz {}) konnte nicht vergeben werden.",
                        eintrag, platz, fehler);
            }
        });
    }

    private ItemStack stapel(Hotbarplatz eintrag) {
        String name = Materialwahl.rueckfallOder(eintrag.sinnbild(), NOTNAGEL);
        // ⚠️ Der Nexo-Teil fehlt hier BEWUSST: es gibt heute keine Textur, und ein Aufruf gegen
        // Nexos API waere ein Zweig ohne Gegenstueck. Der PLATZHALTERWAECHTER meldet bei jedem
        // Bau, dass es so ist - wer die erste Textur liefert, baut ihn hier ein.
        return new ItemStack(Material.valueOf(name));
    }
}
