package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Bildschirm;
import net.zanoria.lobby.menue.Menue;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Der Auftrag lautet woertlich: <i>„Alle fuenf sollen etwas oeffnen."</i>
 *
 * <p>Ohne diese Faelle koennte ein Eintrag stumm bleiben, und im Spiel passierte beim Klick
 * nichts - ohne Ausnahme und ohne Logzeile.
 */
class DieFuenfBildschirmeStehenTest {

    @Test
    @DisplayName("⚠️ JEDES der fuenf Items oeffnet wirklich etwas")
    void jedesItemOeffnetEtwas() {
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            Bildschirm b = Lobbybildschirme.fuer(platz);
            assertNotNull(b, "⚠️ " + platz + " oeffnet NICHTS. Im Spiel passiert beim Klick"
                    + " nichts, und niemand erfaehrt warum.");
            assertEquals(platz.bildschirmKennung(), b.kennung());
            assertFalse(b.eintraege().isEmpty(),
                    "⚠️ Der Bildschirm von " + platz + " hat keine Eintraege. Ein leeres Menue"
                            + " ist schlimmer als kein Menue - es sieht aus wie ein Fehler.");
        }
    }

    @Test
    @DisplayName("Die Kennungen der fuenf Bildschirme sind eindeutig")
    void kennungenSindEindeutig() {
        Set<String> gesehen = new HashSet<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertTrue(gesehen.add(Lobbybildschirme.fuer(platz).kennung()),
                    "⚠️ Zwei Items oeffnen denselben Bildschirm.");
        }
    }

    @Test
    @DisplayName("Jeder Eintrag jedes Bildschirms hat Sinnbild und Handlung")
    void jederEintragIstVollstaendig() {
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            for (var e : Lobbybildschirme.fuer(platz).eintraege()) {
                assertNotNull(e.sinnbild());
                assertNotNull(e.handlung());
                assertFalse(e.beschriftungSchluessel().isBlank());
            }
        }
    }

    /** Eine mitschreibende Umsetzung der Naht - moeglich, WEIL {@link Menue} eine Schnittstelle ist. */
    private static final class Mitschreibendes implements Menue {
        final List<String> geoeffnet = new ArrayList<>();

        @Override
        public boolean oeffne(Player spieler, Bildschirm bildschirm) {
            geoeffnet.add(bildschirm.kennung());
            return true;
        }
    }

    @Test
    @DisplayName("⚠️ Jeder der fuenf Plaetze oeffnet SEINEN Bildschirm")
    void jederPlatzOeffnetSeinen() {
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            Mitschreibendes menue = new Mitschreibendes();
            new Hotbarhoerer(menue).beiPlatz(null, platz.platz());

            assertEquals(List.of(platz.bildschirmKennung()), menue.geoeffnet,
                    "⚠️ Platz " + platz.platz() + " haette " + platz.bildschirmKennung()
                            + " oeffnen muessen. Geoeffnet wurde: " + menue.geoeffnet);
        }
    }

    @Test
    @DisplayName("Ein Platz ohne Item oeffnet nichts")
    void leererPlatzOeffnetNichts() {
        // ⚠️ Ohne diesen Fall waere der Fall darueber auch gruen, wenn die Zuordnung gar nicht
        // gelesen wuerde und einfach immer irgendetwas geoeffnet wird.
        Mitschreibendes menue = new Mitschreibendes();
        assertFalse(new Hotbarhoerer(menue).beiPlatz(null, 4));

        assertTrue(menue.geoeffnet.isEmpty(),
                "⚠️ Platz 4 traegt kein Item, es wurde aber etwas geoeffnet: " + menue.geoeffnet);
    }
}
