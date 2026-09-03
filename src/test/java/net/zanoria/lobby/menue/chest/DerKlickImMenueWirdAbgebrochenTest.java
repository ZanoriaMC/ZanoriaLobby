package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Bildschirm;
import net.zanoria.lobby.menue.Eintrag;
import net.zanoria.lobby.menue.Sinnbild;
import net.zanoria.lobby.pruefung.Attrappe;
import net.zanoria.lobby.pruefung.Mitschrift;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Dieser Fall fährt den ECHTEN Rumpf von {@link ChestKlickhoerer}.
 *
 * <h2>Warum es ihn ueberhaupt gibt — eine widerlegte Annahme</h2>
 *
 * <p>Der Plan fuehrte diesen Rumpf zuerst als <b>ungedeckt</b>: {@code InventoryClickEvent}
 * verlange eine {@code InventoryView}, und deren Baubarkeit ohne Server sei unbekannt. Die
 * Messung vom 2026-09-03 ({@code DieRegistrygrenzeIstGemessenTest}) sagt etwas anderes:
 *
 * <pre>
 * REGISTRYGRENZE: BAUBAR  new InventoryClickEvent(sicht, CONTAINER, 0, LEFT, PICKUP_ALL)
 *                         | mitschrift=[sicht#convertSlot]
 * </pre>
 *
 * <p>Die Mitschrift belegt, dass die {@code InventoryView}-Attrappe wirklich gefragt wurde.
 * <b>Eine angenommene Grenze ist teurer als eine gemessene, weil niemand sie nachprueft</b> —
 * angenommen haette diese Luecke drei Monate ueberlebt, und der Klick im Menue haette Items
 * mitnehmen lassen. In einer Adventure-Lobby ist ein Item im Inventar der einzige Weg, doch noch
 * etwas kaputtzumachen.
 */
class DerKlickImMenueWirdAbgebrochenTest {

    private static Bildschirm bildschirmMit(int eintraege, List<String> geklickt) {
        List<Eintrag> liste = new ArrayList<>();
        for (int i = 0; i < eintraege; i++) {
            String name = "eintrag" + i;
            liste.add(Eintrag.of("lobby.test." + name,
                    Sinnbild.of("zanoria:test_" + name, "STONE"),
                    spieler -> geklickt.add(name)));
        }
        return Bildschirm.of("lobby.test", "lobby.test.titel", liste);
    }

    private static InventoryClickEvent klickAuf(int platz, Player spieler, Mitschrift m) {
        InventoryView sicht = Attrappe.von("Sicht", m)
                .antwortet("getPlayer", spieler)
                .antwortet("convertSlot", platz)
                .als(InventoryView.class);
        return new InventoryClickEvent(sicht, InventoryType.SlotType.CONTAINER, platz,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static Player spieler(UUID id, Mitschrift m) {
        return Attrappe.von("Spieler", m)
                .antwortet("getUniqueId", id)
                .antwortet("getName", "Tester")
                .als(Player.class);
    }

    @Test
    @DisplayName("⚠️ Ein Klick im offenen Menue wird IMMER abgebrochen")
    void derKlickWirdAbgebrochen() {
        Mitschrift m = new Mitschrift();
        UUID id = UUID.randomUUID();
        Player p = spieler(id, m);
        List<String> geklickt = new ArrayList<>();

        ChestMenue menue = new ChestMenue(LoggerFactory.getLogger("probe"));
        menue.merke(id, bildschirmMit(3, geklickt));

        InventoryClickEvent ereignis = klickAuf(1, p, m);
        new ChestKlickhoerer(menue).beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled(),
                "⚠️ Der Klick wurde NICHT abgebrochen. Dann nimmt der Spieler die Menue-Items"
                        + " mit - und in einer Adventure-Lobby ist ein Item im Inventar der"
                        + " einzige Weg, doch noch etwas kaputtzumachen. Mitschrift: " + m);
    }

    @Test
    @DisplayName("Der Klick fuehrt die Handlung des getroffenen Eintrags aus")
    void dieHandlungLaeuft() {
        Mitschrift m = new Mitschrift();
        UUID id = UUID.randomUUID();
        Player p = spieler(id, m);
        List<String> geklickt = new ArrayList<>();

        ChestMenue menue = new ChestMenue(LoggerFactory.getLogger("probe"));
        menue.merke(id, bildschirmMit(3, geklickt));

        new ChestKlickhoerer(menue).beimKlicken(klickAuf(2, p, m));

        assertEquals(List.of("eintrag2"), geklickt,
                "⚠️ Es lief die falsche Handlung oder gar keine. Gelaufen: " + geklickt);
    }

    @Test
    @DisplayName("⚠️ Ein Klick NEBEN die Eintraege loest keine Handlung aus - bricht aber ab")
    void klickNebenDieEintraege() {
        Mitschrift m = new Mitschrift();
        UUID id = UUID.randomUUID();
        Player p = spieler(id, m);
        List<String> geklickt = new ArrayList<>();

        ChestMenue menue = new ChestMenue(LoggerFactory.getLogger("probe"));
        menue.merke(id, bildschirmMit(3, geklickt));

        // Platz 7 liegt in der Kiste (Groesse 9), traegt aber keinen Eintrag.
        InventoryClickEvent ereignis = klickAuf(7, p, m);
        new ChestKlickhoerer(menue).beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled(),
                "⚠️ Auch ein Klick auf einen leeren Platz muss abgebrochen werden - sonst nimmt"
                        + " der Spieler dort etwas heraus oder legt etwas hinein.");
        assertTrue(geklickt.isEmpty(),
                "⚠️ Ein leerer Platz hat eine Handlung ausgeloest: " + geklickt);
    }

    @Test
    @DisplayName("⚠️ Ohne offenen Bildschirm fasst der Hoerer NICHTS an")
    void ohneOffenenBildschirmPassiertNichts() {
        // ⚠️ Ohne diesen Fall waere "bricht immer ab" auch dann gruen, wenn der Hoerer JEDES
        // Inventar dieses Servers abbraeche - auch das eigene des Spielers.
        Mitschrift m = new Mitschrift();
        Player p = spieler(UUID.randomUUID(), m);

        ChestMenue menue = new ChestMenue(LoggerFactory.getLogger("probe"));
        InventoryClickEvent ereignis = klickAuf(0, p, m);
        new ChestKlickhoerer(menue).beimKlicken(ereignis);

        assertFalse(ereignis.isCancelled(),
                "⚠️ Der Hoerer hat ein Inventar abgebrochen, das ihm gar nicht gehoert. Damit"
                        + " waere jedes Kisteninventar des Servers unbenutzbar.");
    }
}
