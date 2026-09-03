package net.zanoria.lobby.hotbar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Die Platzhalter werden gezaehlt, benannt und gepinnt</b> — damit keiner davon stehenbleibt.
 *
 * <h2>⚠️ Wogegen dieser Fall steht</h2>
 *
 * <p>Ein Platzhalter, den niemand meldet, ist ein Platzhalter fuer immer. Er faellt nicht auf,
 * weil das Spiel laeuft: ein Kompass kann auch ein Kompass sein, es gibt keine Ausnahme und keine
 * Logzeile.
 *
 * <h2>⚠️ Warum eine gepinnte MENGE und nicht nur eine Zahl</h2>
 *
 * <p>Eine Zahl („5 Platzhalter") faengt den Fall nicht, in dem jemand einen loest und gleichzeitig
 * einen neuen Eintrag anlegt. Gepinnt sind deshalb die <b>Namen</b>.
 *
 * <p>⚠️ <b>Dieser Fall wird ROT, wenn die Deckung BESSER wird</b> — also wenn jemand eine Textur
 * liefert und {@code istPlatzhalter()} auf {@code false} setzt, ohne den Eintrag hier auszutragen.
 * <b>Das ist Absicht:</b> eine geloeste Luecke, deren Beschreibung stehenbleibt, ist dieselbe
 * Luege wie ein Platzhalter, den niemand meldet.
 *
 * <p>Der laute Teil steht daneben: {@code :platzhalterwaechter} schreibt die Liste bei jedem Bau
 * ins Protokoll und macht den Bau <b>nicht</b> rot. Laut melden und scharf pinnen sind zwei
 * Aufgaben, und sie liegen absichtlich an zwei Orten.
 */
class DieHotbarNenntIhrePlatzhalterTest {

    /**
     * ⚠️ <b>Stand 2026-09-03: ALLE fuenf Eintraege sind Platzhalter.</b> Es gibt fuer die Lobby
     * heute keine einzige eigene Textur.
     *
     * <p>Wer eine liefert, nimmt den Eintrag <b>hier</b> heraus und setzt {@code istPlatzhalter()}
     * im Enum auf {@code false}. Beides zusammen — sonst wird dieser Fall rot, und das ist der
     * eingebaute Weg, es nicht zu vergessen.
     */
    private static final Set<String> ERWARTETE_PLATZHALTER = Set.of(
            "KOMPASS", "RUCKSACK", "BATTLEPASS", "SOZIAL", "EINSTELLUNGEN");

    @Test
    @DisplayName("⚠️ Die Menge der Platzhalter ist gepinnt - keiner kommt und keiner geht lautlos")
    void diePlatzhalterMengeIstGepinnt() {
        Set<String> tatsaechlich = Hotbarplatz.platzhalter().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(ERWARTETE_PLATZHALTER), tatsaechlich,
                "⚠️ Die Menge der Platzhalter hat sich geaendert.\n"
                        + "  KAM DAZU  -> jemand hat einen Eintrag angelegt, ohne eine Textur zu"
                        + " liefern. Eintragen und weitermachen ist in Ordnung; lautlos ist es"
                        + " nicht.\n"
                        + "  FIEL WEG  -> jemand hat eine Textur geliefert. Dann gehoert der"
                        + " Eintrag HIER heraus, im selben Zug. Eine geloeste Luecke, deren"
                        + " Beschreibung stehenbleibt, ist dieselbe Luege wie ein Platzhalter,"
                        + " den niemand meldet.");
    }

    @Test
    @DisplayName("⚠️ Jeder Platz kommt genau einmal vor")
    void diePlaetzeSindEindeutig() {
        // ⚠️ Zwei Eintraege auf demselben Platz hiessen: einer ist im Spiel unsichtbar - ohne
        // Ausnahme und ohne Logzeile. Genau die Sorte Fehler, die man erst im Spiel sieht.
        Set<Integer> gesehen = new HashSet<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertTrue(gesehen.add(platz.platz()),
                    "⚠️ Der Platz " + platz.platz() + " ist doppelt belegt (" + platz + ")."
                            + " Einer der beiden Eintraege waere im Spiel unsichtbar.");
        }
    }

    @Test
    @DisplayName("Die Plaetze sind die vom Auftrag geforderten: 0, 1, 2, 7, 8")
    void diePlaetzeStimmen() {
        assertEquals(0, Hotbarplatz.KOMPASS.platz());
        assertEquals(1, Hotbarplatz.RUCKSACK.platz());
        assertEquals(2, Hotbarplatz.BATTLEPASS.platz());
        assertEquals(7, Hotbarplatz.SOZIAL.platz());
        assertEquals(8, Hotbarplatz.EINSTELLUNGEN.platz());
    }

    @Test
    @DisplayName("Jeder Platz liegt in der Hotbar (0 bis 8)")
    void alleInDerHotbar() {
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertTrue(platz.platz() >= 0 && platz.platz() <= 8,
                    "⚠️ " + platz + " liegt auf " + platz.platz() + " - das ist keine Hotbar."
                            + " Der Eintrag laege im Inventar und waere ohne Oeffnen unerreichbar.");
        }
    }

    @Test
    @DisplayName("Jeder Eintrag hat eine Nexo-Kennung UND einen Vanilla-Rueckfall")
    void jederEintragHatBeideSeiten() {
        Set<String> kennungen = new HashSet<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertFalse(platz.sinnbild().nexoKennung().isBlank(),
                    "⚠️ " + platz + " hat keine Nexo-Kennung. Dann gibt es nichts, worauf eine"
                            + " Textur je zeigen koennte.");
            assertTrue(platz.sinnbild().nexoKennung().startsWith("zanoria:"),
                    "⚠️ " + platz + " traegt die Kennung '" + platz.sinnbild().nexoKennung()
                            + "'. Ohne den Namensraum kollidiert sie mit den Eintraegen der"
                            + " Spielmodi - Nexo fuehrt EINEN Bestand fuer das ganze Netz.");
            assertFalse(platz.sinnbild().vanillaRueckfall().isBlank(),
                    "⚠️ " + platz + " hat keinen Vanilla-Rueckfall. Fehlt Nexo, erscheint dieses"
                            + " Item dann GAR NICHT - ohne Ausnahme und ohne Logzeile.");
            assertTrue(kennungen.add(platz.sinnbild().nexoKennung()),
                    "⚠️ Die Kennung '" + platz.sinnbild().nexoKennung() + "' kommt zweimal vor -"
                            + " zwei Items saehen im Spiel identisch aus.");
        }
    }

    /**
     * ⚠️ Die fuenf Schluessel sind <b>gemessen, nicht erfunden</b>.
     *
     * <p>Am 2026-09-03 stehen sie samt Uebersetzung in
     * {@code ZanLang/src/main/resources/translations/de_de.json}: „Wegweiser", „Rucksack",
     * „Battle Pass", „Freunde", „Einstellungen". Wer hier eigene anlegt (etwa
     * {@code lobby.hotbar.kompass}), erzeugt eine zweite Wahrheit und laesst die vorhandenen
     * verwaisen - im Spiel stuende dann der rohe Schluessel als Item-Name.
     */
    @Test
    @DisplayName("⚠️ Die Beschriftungsschluessel sind die VORHANDENEN aus ZanLang")
    void dieSchluesselSindDieVorhandenen() {
        assertEquals("lobby.hotbar.compass", Hotbarplatz.KOMPASS.beschriftungSchluessel());
        assertEquals("lobby.hotbar.backpack", Hotbarplatz.RUCKSACK.beschriftungSchluessel());
        assertEquals("lobby.hotbar.battlepass", Hotbarplatz.BATTLEPASS.beschriftungSchluessel());
        // ⚠️ Der Eintrag heisst SOZIAL, sein Schluessel aber ...friends: der Auftrag nennt das
        // Item "Social", die bestehende und uebersetzte Entscheidung heisst "Freunde". Die
        // Uebersetzung gewinnt, weil sie schon vor dem Spieler steht.
        assertEquals("lobby.hotbar.friends", Hotbarplatz.SOZIAL.beschriftungSchluessel());
        assertEquals("lobby.hotbar.settings", Hotbarplatz.EINSTELLUNGEN.beschriftungSchluessel());
    }

    @Test
    @DisplayName("⚠️ Kein gezeichneter Text traegt ein Warnzeichen")
    void keinWarnzeichenImGezeichnetenText() {
        // ⚠️ Das Warnzeichen ist ZWEI Codepunkte (U+26A0 + U+FE0F). Minecraft zeichnet U+FE0F
        // als Leerkasten. In Kommentaren ist es richtig, in gezeichneten Zeichenketten nie.
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            String s = platz.beschriftungSchluessel() + platz.bildschirmKennung()
                    + platz.sinnbild().nexoKennung() + platz.sinnbild().vanillaRueckfall();
            assertFalse(s.indexOf('⚠') >= 0 || s.indexOf('️') >= 0,
                    "⚠️ " + platz + " traegt ein Warnzeichen in einer gezeichneten Zeichenkette."
                            + " Minecraft zeichnet U+FE0F als Leerkasten.");
        }
    }
}
