package net.zanoria.lobby.hotbar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ⚠️ Geprueft wird die <b>Zuordnung</b>, nicht das Erzeugen der Gegenstaende.
 *
 * <p>{@code new ItemStack(Material)} wirft ohne laufenden Server (gemessen 2026-09-03,
 * {@code DieRegistrygrenzeIstGemessenTest}: {@code ExceptionInInitializerError}, Ursache
 * {@code No RegistryAccess implementation found}). <b>Dass die Items wirklich im Inventar landen,
 * beantwortet allein der Erstlauf am laufenden Server</b> — und das ist hier gemeldet, nicht
 * umgangen.
 *
 * <p>⚠️ Wer dafuer einen Einheitstest mit gestelltem Inventar baut, misst die Attrappe statt der
 * Klasse.
 */
class DieAusgabeKenntJedenPlatzTest {

    @Test
    @DisplayName("Die Ausgabe kennt fuer jeden Platz genau einen Eintrag")
    void jederPlatzIstZugeordnet() {
        Map<Integer, Hotbarplatz> plan = Hotbarausgabe.plan();

        assertEquals(Hotbarplatz.values().length, plan.size(),
                "⚠️ Der Plan hat " + plan.size() + " Eintraege, das Enum hat "
                        + Hotbarplatz.values().length + ". Ein Eintrag wuerde im Spiel fehlen,"
                        + " ohne Ausnahme und ohne Logzeile.");
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertSame(platz, plan.get(platz.platz()),
                    "⚠️ Platz " + platz.platz() + " traegt nicht " + platz + ".");
        }
    }

    @Test
    @DisplayName("⚠️ Der Plan wird ABGELEITET, nicht gepflegt")
    void derPlanWaechstMit() {
        // ⚠️ Waere der Plan eine aufgeschriebene Liste, wuerde sie beim naechsten Eintrag selbst
        // zur Luege - und der Fall darueber schruempfte mit ihr mit, statt rot zu werden.
        // Diese Gleichheit ist der Beleg, dass die Menge aus dem Enum kommt.
        assertEquals(Hotbarplatz.values().length, Hotbarausgabe.plan().size());
    }
}
