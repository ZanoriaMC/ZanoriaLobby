package net.zanoria.lobby.schutz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Der Kern dieser Klasse ist NICHT "schuetzt die Lobbywelt", sondern was passiert, wenn die
 * konfigurierte Welt gar nicht existiert.
 *
 * <p>Ein Namensdreher in der Konfiguration wuerde den Schutz sonst lautlos abschalten: der Hoerer
 * liefe, faende nie eine Uebereinstimmung, brueche nie etwas ab - und meldete dabei keinen
 * Fehler. Das ist die Bauart "die Wache laeuft leer und sagt, das sei kein Fehler".
 */
class LobbyschutzTest {

    @Test
    @DisplayName("Die konfigurierte Welt wird geschuetzt")
    void dieLobbyweltIstGeschuetzt() {
        Lobbyschutz schutz = Lobbyschutz.aus("lobby", List.of("lobby", "andere"));
        assertTrue(schutz.schuetzt("lobby"));
    }

    @Test
    @DisplayName("Eine andere Welt wird nicht geschuetzt")
    void andereWeltenBleibenFrei() {
        Lobbyschutz schutz = Lobbyschutz.aus("lobby", List.of("lobby", "andere"));
        assertFalse(schutz.schuetzt("andere"));
    }

    @Test
    @DisplayName("⚠️ Fehlt die konfigurierte Welt, wird ALLES geschuetzt - nicht nichts")
    void einNamensdreherSchuetztAlles() {
        Lobbyschutz schutz = Lobbyschutz.aus("lobbi", List.of("lobby", "andere"));

        assertTrue(schutz.schuetzt("lobby"),
                "⚠️ Die konfigurierte Welt 'lobbi' existiert nicht. Wuerde jetzt nichts geschuetzt,"
                        + " koennte jeder Spieler die Lobby abbauen - ohne Ausnahme und ohne"
                        + " Logzeile. Der Irrtum muss in Richtung 'zu viel geschuetzt' fallen.");
        assertTrue(schutz.schuetzt("andere"));
        assertTrue(schutz.faelltZu(),
                "⚠️ Der Zustand muss ABLESBAR sein, sonst kann der Hoerer ihn nicht protokollieren"
                        + " - und ein stiller Rueckfall auf 'alles' ist nur die zweitbeste Luege.");
    }

    @Test
    @DisplayName("⚠️ Bei null oder leerer Weltliste wird ebenfalls alles geschuetzt")
    void ohneWeltenWirdAllesGeschuetzt() {
        // ⚠️ Beim Start kann die Weltliste noch leer sein. Auch das ist "ich weiss es nicht",
        // und die Antwort auf "ich weiss es nicht" ist zu, nicht auf.
        assertTrue(Lobbyschutz.aus("lobby", List.of()).schuetzt("irgendwas"));
        assertTrue(Lobbyschutz.aus(null, List.of("lobby")).schuetzt("lobby"));
        assertTrue(Lobbyschutz.aus("  ", List.of("lobby")).schuetzt("lobby"));
    }
}
