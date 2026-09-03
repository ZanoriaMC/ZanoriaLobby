package net.zanoria.lobby.menue.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ⚠️ Diese Faelle pruefen die Rechnung, die ohne laufenden Server pruefbar ist - und sie ist
 * genau deshalb aus {@code ChestMenue} herausgezogen.
 *
 * <p>Gemessen am 2026-09-03 ({@code DieRegistrygrenzeIstGemessenTest}): sowohl
 * {@code Bukkit.createInventory} als auch {@code new ItemStack(Material)} scheitern ohne Server.
 * Laege die Groessenrechnung drueben, waere sie in <b>keinem</b> Einheitstest pruefbar - und eine
 * Groessenrechnung, die danebenliegt, wirft im Spiel beim Oeffnen, vor dem Spieler.
 */
class KistenplanTest {

    @Test
    @DisplayName("Die Groesse ist immer ein Vielfaches von neun")
    void groesseIstVielfachesVonNeun() {
        assertEquals(9, Kistenplan.groesseFuer(1),
                "⚠️ Ein einzelner Eintrag braucht eine ganze Reihe. Rundet die Rechnung ab,"
                        + " kommt hier 0 heraus - und Bukkit wirft beim Oeffnen.");
        assertEquals(9, Kistenplan.groesseFuer(9));
        assertEquals(18, Kistenplan.groesseFuer(10));
        assertEquals(54, Kistenplan.groesseFuer(54));
    }

    @Test
    @DisplayName("Die Eintraege liegen der Reihe nach")
    void derReiheNach() {
        assertEquals(0, Kistenplan.platzFuer(0));
        assertEquals(1, Kistenplan.platzFuer(1));
        assertEquals(11, Kistenplan.platzFuer(11));
    }

    @Test
    @DisplayName("⚠️ Mehr als 54 Eintraege werden LAUT abgewiesen, nicht abgeschnitten")
    void zuVieleWerdenAbgewiesen() {
        // ⚠️ Bukkit kann keine Kiste ueber 54 Plaetze. Stillschweigend abschneiden hiesse: der
        // 55. Eintrag existiert im Code, ist im Spiel unsichtbar, und niemand erfaehrt es.
        // Ein Fehler beim ZUSAMMENBAUEN ist billiger als ein fehlender Knopf vor dem Spieler -
        // und Abschneiden sieht dabei wie eine Freundlichkeit aus.
        assertThrows(IllegalArgumentException.class, () -> Kistenplan.groesseFuer(55));
    }

    @Test
    @DisplayName("Null Eintraege sind kein Bildschirm")
    void nullEintraegeGehenNicht() {
        assertThrows(IllegalArgumentException.class, () -> Kistenplan.groesseFuer(0));
        assertThrows(IllegalArgumentException.class, () -> Kistenplan.groesseFuer(-1));
    }

    @Test
    @DisplayName("Ein Platz ausserhalb der Kiste wird abgewiesen")
    void platzAusserhalb() {
        assertThrows(IllegalArgumentException.class, () -> Kistenplan.platzFuer(-1));
        assertThrows(IllegalArgumentException.class,
                () -> Kistenplan.platzFuer(Kistenplan.MAX_PLAETZE));
    }
}
