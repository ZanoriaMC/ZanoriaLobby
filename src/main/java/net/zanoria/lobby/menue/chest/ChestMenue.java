package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Bildschirm;
import net.zanoria.lobby.menue.Eintrag;
import net.zanoria.lobby.menue.Menue;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>EXPERIMENTELL.</b> Die Naht {@link Menue} ueber ein Kisteninventar.
 *
 * <h2>⚠️ Diese Klasse ist zum Wegwerfen gebaut</h2>
 *
 * <p>ZanUI ist noch nicht so weit. Sobald es das ist, wird diese Klasse durch eine
 * ZanUI-Umsetzung ersetzt — <b>ohne dass ein Hoerer angefasst wird</b>. Das ist die tragende
 * Auflage des Betreibers, und sie haelt nur, solange kein Kisten-Typ ausserhalb dieses Pakets
 * steht: {@code :kistenwaechter} macht den Bau rot, wenn doch.
 *
 * <p>⚠️ <b>Kein Layout.</b> Die Eintraege liegen der Reihe nach ({@link Kistenplan}). Wer hier
 * Rahmen oder Luecken ergaenzt, baut Arbeit, die der Tausch wegwirft.
 *
 * <h2>⚠️ Sie wirft nicht</h2>
 *
 * <p>Dieselbe Zusage wie {@code ZanUiService.open}: scheitert das Oeffnen, wird protokolliert und
 * {@code false} zurueckgegeben. Der Grund ist bei ZanUI im Spiel entstanden — eine durchgereichte
 * Ausnahme kam beim Spieler als „An unexpected error occurred" an, und niemand erfuhr, was.
 *
 * <p>⚠️ Der Fangzweig nimmt <b>{@code LinkageError} mit</b>. Das ist keine Vorsicht, sondern
 * gemessen: die Registry-Grenze wirft {@code ExceptionInInitializerError}, und das ist ein
 * {@code Error}, keine {@code RuntimeException}. Ein {@code catch (RuntimeException)} allein
 * liesse genau den Fall durch, der hier am wahrscheinlichsten ist.
 */
public final class ChestMenue implements Menue {

    /** Der Notnagel, wenn ein Vanilla-Rueckfall unbekannt ist. */
    static final String NOTNAGEL = "STONE";

    private final Logger log;

    /**
     * Welcher Bildschirm bei welchem Spieler offen ist.
     *
     * <p>⚠️ {@code ConcurrentHashMap}, weil {@code InventoryCloseEvent} und der naechste
     * {@code oeffne} nicht garantiert im selben Tick liegen.
     */
    private final Map<UUID, Bildschirm> offen = new ConcurrentHashMap<>();

    public ChestMenue(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean oeffne(Player spieler, Bildschirm bildschirm) {
        try {
            int groesse = Kistenplan.groesseFuer(bildschirm.eintraege().size());
            // ⚠️ Der Titel ist hier noch der SCHLUESSEL. Die Aufloesung ueber ZanLang kommt
            // spaeter; bis dahin steht der Schluessel sichtbar da - das ist Absicht. Ein
            // sichtbarer Schluessel ist ein gemeldeter Mangel, ein erfundener Satz nicht.
            Inventory kiste = Bukkit.createInventory(null, groesse, bildschirm.titelSchluessel());

            for (int i = 0; i < bildschirm.eintraege().size(); i++) {
                kiste.setItem(Kistenplan.platzFuer(i), stapelFuer(bildschirm.eintraege().get(i)));
            }

            spieler.openInventory(kiste);
            offen.put(spieler.getUniqueId(), bildschirm);
            return true;
        } catch (RuntimeException | LinkageError fehler) {
            log.error("Bildschirm '{}' liess sich fuer {} nicht oeffnen.",
                    bildschirm.kennung(), spieler.getName(), fehler);
            return false;
        }
    }

    /** Der Bildschirm, den dieser Spieler offen hat — oder {@code null}. */
    public Bildschirm offenFuer(UUID spieler) {
        return offen.get(spieler);
    }

    /** Vergisst den offenen Bildschirm dieses Spielers. */
    public void schliesse(UUID spieler) {
        offen.remove(spieler);
    }

    private ItemStack stapelFuer(Eintrag eintrag) {
        String name = Materialwahl.rueckfallOder(eintrag.sinnbild(), NOTNAGEL);
        if (!name.equals(eintrag.sinnbild().vanillaRueckfall())) {
            // ⚠️ LAUT. Ein Eintrag, der stillschweigend wie Stein aussieht, ist ein Mangel, den
            // niemand findet - der Spieler haelt ihn fuer Absicht.
            log.warn("Sinnbild '{}' nennt den Rueckfall '{}', der kein bekanntes Material ist -"
                            + " genommen wird {}.",
                    eintrag.sinnbild().nexoKennung(), eintrag.sinnbild().vanillaRueckfall(), name);
        }
        return new ItemStack(Material.valueOf(name));
    }
}
