package net.zanoria.lobby.menue;

import net.zanoria.lobby.hotbar.Hotbarplatz;
import net.zanoria.lobby.hotbar.Lobbybildschirme;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Jeder Schluessel, den ein Bildschirm traegt, muss in ZanLang einen Satz haben.
 *
 * <h2>Warum das ein eigener Waechter ist und nicht nebenbei passiert</h2>
 *
 * <p>Gemessen am 2026-09-03: <b>ZanLang hat keine Anmeldemethode fuer Schluessel</b> — es gibt in
 * {@code ZanLang/src/main/java} keine. Wer einen Bildschirm baut, muss seine Schluessel
 * <b>von Hand</b> in {@code translations/de_de.json} und {@code en_us.json} tragen. Fehlt einer,
 * steht im Spiel der <b>rohe Schluessel</b> als Ueberschrift: {@code lobby.kompass.titel}.
 *
 * <p>Das ist nicht der Ausnahme-, sondern der Normalfall bei jedem Ausrollen — ZanUI hat aus genau
 * diesem Grund eine eigene {@code missingKeys}-Pruefung. Dieser Fall findet das Vergessene
 * <b>beim Bau</b> statt vor einem Spieler.
 *
 * <p>⚠️ Er liest ZanLangs Quelldatei ueber einen relativen Pfad ins Nachbarrepo. <b>Fehlt das
 * Nachbarrepo, wird er UEBERSPRUNGEN und nicht gruen</b> — ein gruener Fall ueber eine Datei, die
 * es nicht gibt, waere genau die Luege, gegen die er steht.
 *
 * <p>⚠️ <b>Uebersprungen ist auch nicht bestanden.</b> Wer diesen Fall in der Ergebnis-XML als
 * {@code skipped} sieht, hat keine Aussage ueber die Schluessel — er hat nur kein Nachbarrepo.
 */
class JederSchluesselHatEinenSatzTest {

    /** ⚠️ BEIDE Dateien. Ein Schluessel nur in de_de laesst englische Spieler den Rohtext sehen. */
    private static final List<Path> ZANLANG = List.of(
            Path.of("../ZanLang/src/main/resources/translations/de_de.json"),
            Path.of("../ZanLang/src/main/resources/translations/en_us.json"));

    /** Alle Schluessel, die im Spiel gezeichnet werden. Abgeleitet, nicht gepflegt. */
    private static Set<String> gesuchteSchluessel() {
        // ⚠️ ABGELEITET aus Enum und Bildschirmen, nicht aufgeschrieben. Eine gepflegte Liste
        // wuerde beim naechsten Eintrag selbst zur Luege - dieselbe Lehre wie erwartete_hoerer()
        // im Erstlauf.
        Set<String> gesucht = new LinkedHashSet<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            gesucht.add(platz.beschriftungSchluessel());
            Bildschirm b = Lobbybildschirme.fuer(platz);
            gesucht.add(b.titelSchluessel());
            b.eintraege().forEach(e -> gesucht.add(e.beschriftungSchluessel()));
        }
        return gesucht;
    }

    @Test
    @DisplayName("⚠️ Jeder Titel- und Beschriftungsschluessel steht in BEIDEN ZanLang-Dateien")
    void jederSchluesselHatEinenSatz() throws IOException {
        List<String> fehlt = new ArrayList<>();

        for (Path datei : ZANLANG) {
            Assumptions.assumeTrue(Files.exists(datei),
                    "ZanLang-Nachbarrepo nicht da (" + datei + ") - dieser Fall wird"
                            + " UEBERSPRUNGEN, nicht bestanden.");
            String json = Files.readString(datei, StandardCharsets.UTF_8);

            // ⚠️ POSITIVKONTROLLE ZUERST: findet die Suche ueberhaupt einen bekannten Schluessel?
            // Ohne sie waere ein falscher Pfad, eine andere Kodierung oder ein geaendertes
            // Dateiformat als "alles vorhanden" durchgegangen - die Suche faende dann NICHTS,
            // und "nichts fehlt" waere gratis gruen.
            assertTrue(json.contains("\"lobby.hotbar.compass\""),
                    "⚠️ Der bekannte Schluessel lobby.hotbar.compass steht nicht in " + datei
                            + ". Dann misst dieser Fall etwas anderes als er glaubt, und jede"
                            + " Aussage unten waere wertlos.");

            for (String s : gesuchteSchluessel()) {
                if (!json.contains("\"" + s + "\"")) {
                    fehlt.add(datei.getFileName() + ": " + s);
                }
            }
        }

        assertTrue(fehlt.isEmpty(),
                "⚠️ Diese Schluessel haben in ZanLang keinen Satz und wuerden im Spiel ROH"
                        + " angezeigt:\n  " + String.join("\n  ", fehlt)
                        + "\n\nZanLang kennt KEINE Anmeldemethode - sie muessen von Hand in"
                        + " translations/de_de.json UND en_us.json.");
    }
}
