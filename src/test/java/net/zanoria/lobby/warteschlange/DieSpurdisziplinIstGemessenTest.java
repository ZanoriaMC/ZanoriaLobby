package net.zanoria.lobby.warteschlange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing Rule 1, an der Naht gemessen: <b>das Redis-Lesen verlaesst den Hauptthread, jeder
 * Bukkit-Aufruf kommt zurueck.</b>
 *
 * <p>⚠️ Der Schwesterfall {@code DerWarteschlangenpfadVerlaesstDenHauptthreadTest} haelt am
 * QUELLTEXT fest, dass die Lobby den Takt asynchron registriert. Dieser hier haelt am VERHALTEN
 * fest, dass die Reihenfolge stimmt. Beide zusammen: der eine belegt die Aufrufstelle, der
 * andere die Rechnung — einer allein liesse die jeweils andere Haelfte offen.
 *
 * <p>⚠️ Die mitschreibende Ausfuehrung fuehrt beide Spuren <b>sofort</b> aus. Der Fall misst
 * damit keine echte Nebenlaeufigkeit — er misst, <em>auf welcher Spur</em> was angemeldet wurde,
 * und genau das ist die Zusicherung. Ein Fall mit echten Threads waere zeitabhaengig und
 * bewiese weniger.
 */
class DieSpurdisziplinIstGemessenTest {

    /** Fuehrt beides sofort aus und schreibt mit, auf welcher Spur. */
    private static final class Mitschreibende implements Warteschlangentakt.Ausfuehrung {
        private final List<String> spuren = new ArrayList<>();

        @Override
        public void abseits(Runnable arbeit) {
            spuren.add("abseits:start");
            arbeit.run();
            spuren.add("abseits:ende");
        }

        @Override
        public void haupt(Runnable arbeit) {
            spuren.add("haupt:start");
            arbeit.run();
            spuren.add("haupt:ende");
        }

        void vermerke(String was) {
            spuren.add(was);
        }

        /** Auf welcher Spur lief der Vermerk? Die innerste offene Spur gewinnt. */
        String spurVon(String vermerk) {
            int i = spuren.indexOf(vermerk);
            assertTrue(i >= 0, "⚠️ '" + vermerk + "' wurde nie vermerkt - dann misst dieser Fall nichts");
            int tiefe = 0;
            for (int j = i - 1; j >= 0; j--) {
                String z = spuren.get(j);
                if (z.endsWith(":ende")) {
                    tiefe++;
                } else if (z.endsWith(":start")) {
                    if (tiefe == 0) {
                        return z.substring(0, z.indexOf(':'));
                    }
                    tiefe--;
                }
            }
            return "keine";
        }
    }

    @Test
    @DisplayName("Der Takt liest Redis auf der Nebenspur und ruft Bukkit nur auf der Hauptspur")
    void derTaktHaeltDieSpurenAuseinander() {
        Mitschreibende ausfuehrung = new Mitschreibende();
        UUID spieler = UUID.randomUUID();

        Warteschlangentakt.fahre(ausfuehrung,
                () -> { ausfuehrung.vermerke("online-lesen(bukkit)"); return List.of(spieler); },
                online -> { ausfuehrung.vermerke("redis-lesen"); return "stand:" + online.size(); },
                stand -> ausfuehrung.vermerke("bossbars-setzen(bukkit)=" + stand));

        assertEquals("haupt", ausfuehrung.spurVon("online-lesen(bukkit)"),
                "⚠️ Bukkit.getOnlinePlayers() gehoert auf den Hauptthread - Bukkits API ist nicht threadsicher");
        assertEquals("abseits", ausfuehrung.spurVon("redis-lesen"),
                "⚠️ Das Redis-Lesen ist genau das, was Standing Rule 1 vom Hauptthread fernhaelt");
        assertEquals("haupt", ausfuehrung.spurVon("bossbars-setzen(bukkit)=stand:1"),
                "⚠️ Jede BossBar-Aenderung gehoert zurueck auf den Hauptthread");
    }

    @Test
    @DisplayName("Eine Spieleraktion arbeitet auf der Nebenspur und meldet auf der Hauptspur zurueck")
    void dieAktionHaeltDieSpurenAuseinander() {
        Mitschreibende ausfuehrung = new Mitschreibende();

        Warteschlangentakt.fahreAktion(ausfuehrung,
                () -> { ausfuehrung.vermerke("queue-join(redis,sperre)"); return true; },
                ergebnis -> ausfuehrung.vermerke("nachricht-an-spieler(bukkit)=" + ergebnis));

        assertEquals("abseits", ausfuehrung.spurVon("queue-join(redis,sperre)"),
                "⚠️ isQueued/leave/join liegen hinter einer netzweiten Sperre mit bis zu 2 s Spin");
        assertEquals("haupt", ausfuehrung.spurVon("nachricht-an-spieler(bukkit)=true"),
                "⚠️ Die Rueckmeldung an den Spieler ist ein Bukkit-Aufruf");
    }

    @Test
    @DisplayName("Positivkontrolle: die Mitschrift kann eine falsche Spur ueberhaupt erkennen")
    void dieMitschriftKannEineFalscheSpurErkennen() {
        // ⚠️ Ohne diesen Fall waere jede Zusicherung oben auch dann gruen, wenn spurVon immer
        // "haupt" lieferte oder gar nichts mitschriebe. Die Bauart, an der heute mehrere
        // Zaehlungen gescheitert sind: das Werkzeug misst nicht, und nichts wird rot.
        Mitschreibende ausfuehrung = new Mitschreibende();
        ausfuehrung.abseits(() -> ausfuehrung.vermerke("liegt-abseits"));
        ausfuehrung.haupt(() -> ausfuehrung.vermerke("liegt-haupt"));

        assertEquals("abseits", ausfuehrung.spurVon("liegt-abseits"));
        assertEquals("haupt", ausfuehrung.spurVon("liegt-haupt"),
                "die Mitschrift muss die beiden Spuren unterscheiden koennen, sonst misst sie nichts");
    }
}
