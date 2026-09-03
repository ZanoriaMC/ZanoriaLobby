package net.zanoria.lobby;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Dieser Fall prueft NICHT das Spiel, sondern dass ueberhaupt ein Fall laeuft.
 *
 * <p>Bis zum 2026-09-03 meldete dieses Repo {@code :test NO-SOURCE} - es gab kein einziges
 * {@code src/test}. Ein Geruest, das man fuer vorhanden haelt und das gar nicht faehrt, ist
 * teurer als gar keins: jede spaetere Aussage "die Tests sind gruen" waere dann wahr und
 * bedeutungslos.
 */
class DasTestgeruestLaeuftTest {

    @Test
    @DisplayName("Ein Fall dieses Repos wird wirklich gefahren")
    void einFallLaeuft() {
        assertTrue(true, "Wenn dieser Fall nicht in der Ergebnis-XML steht, laeuft das Geruest nicht.");
    }

    @Test
    @DisplayName("paper-api liegt auf dem Testpfad")
    void paperApiIstDa() {
        assertNotNull(
                org.bukkit.GameMode.ADVENTURE,
                "GameMode.ADVENTURE ist null - dann fehlt paper-api auf testCompileClasspath.");
    }
}
