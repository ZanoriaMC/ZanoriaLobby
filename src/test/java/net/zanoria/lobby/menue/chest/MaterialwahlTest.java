package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Sinnbild;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Geprueft wird die <b>Auswahl</b> des Materialnamens — nicht das Erzeugen des
 * {@code ItemStack}s.
 *
 * <p>Gemessen am 2026-09-03 ({@code DieRegistrygrenzeIstGemessenTest}):
 * {@code new ItemStack(Material)} wirft ohne laufenden Server. {@code Material.values()} dagegen
 * laedt vollstaendig (2121 Eintraege) — die Auswahl ist also eine Zeichenkettenfrage und laeuft
 * ueberall, das Erzeugen beantwortet allein der Erstlauf.
 *
 * <p>⚠️ Wer fuer das Erzeugen einen Einheitstest baut, der den {@code ItemStack} selbst stellt,
 * misst die Attrappe statt der Klasse.
 */
class MaterialwahlTest {

    @Test
    @DisplayName("Ist der Rueckfall ein bekanntes Material, wird es genommen")
    void bekanntesMaterial() {
        assertEquals("COMPASS",
                Materialwahl.rueckfallOder(Sinnbild.of("zanoria:x", "COMPASS"), "STONE"));
    }

    @Test
    @DisplayName("⚠️ Ist der Rueckfall ein TIPPFEHLER, wird der Notnagel genommen - nicht geworfen")
    void tippfehlerFaelltAufNotnagel() {
        // ⚠️ Material.valueOf("KOMPASS") wuerde werfen, und der Wurf kaeme im Hoererrumpf an -
        // also mitten im Klick eines Spielers. Ein Eintrag, der wie Stein aussieht, ist besser
        // als ein Menue, das sich nicht oeffnet.
        assertEquals("STONE",
                Materialwahl.rueckfallOder(Sinnbild.of("zanoria:x", "KOMPASS"), "STONE"));
    }

    @Test
    @DisplayName("Ein leerer Rueckfall nimmt ebenfalls den Notnagel")
    void leererRueckfall() {
        assertEquals("STONE",
                Materialwahl.rueckfallOder(Sinnbild.of("zanoria:x", "   "), "STONE"));
    }

    @Test
    @DisplayName("Kleinschreibung und Leerraum werden angenommen")
    void grossKleinUndLeerraum() {
        assertEquals("COMPASS",
                Materialwahl.rueckfallOder(Sinnbild.of("zanoria:x", " compass "), "STONE"));
    }

    @Test
    @DisplayName("⚠️ Positivkontrolle: die Materialliste ist wirklich gefuellt")
    void dieListeIstNichtLeer() {
        // ⚠️ Ohne diesen Fall waere jede Aussage darueber gratis gruen: eine LEERE Liste
        // beantwortet JEDEN Rueckfall mit dem Notnagel, und "Tippfehler faellt auf Notnagel"
        // waere dann wahr, ohne etwas geprueft zu haben. Dieselbe Bauart wie eine Pruefung
        // ueber eine leere Liste.
        assertTrue(Materialwahl.bekannteAnzahl() > 1000,
                "⚠️ Die Materialliste hat nur " + Materialwahl.bekannteAnzahl() + " Eintraege."
                        + " Dann prueft dieser Fallsatz nichts: eine leere Liste liefert immer"
                        + " den Notnagel, und der Tippfehler-Fall waere gratis gruen.");
    }
}
