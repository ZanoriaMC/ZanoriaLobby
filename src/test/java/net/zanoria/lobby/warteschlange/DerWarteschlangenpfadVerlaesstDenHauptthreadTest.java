package net.zanoria.lobby.warteschlange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing Rule 1: <b>Kein synchrones I/O im Main-Thread.</b> Keine blockierenden
 * DB-/Redis-/HTTP-Aufrufe (und kein {@code Thread.sleep}-Spinlock) auf dem Bukkit-Main-Thread —
 * Join/Quit/Command/Tick async halten.
 *
 * <p>⚠️ Der Anlass, am 2026-09-06 gemessen. Zwei Stellen in {@code ZanoriaLobby} verletzten sie
 * an der teuersten Stelle:
 * <ul>
 *   <li><b>Der Takt.</b> Z.137 registrierte {@code runTaskTimer} — synchron, jede Sekunde,
 *       unbedingt. Von dort ging es ueber {@code updateFullQueueTimers} in
 *       {@code getQueueSize(type)} <em>fuer jeden konfigurierten Modus</em> und danach je
 *       Online-Spieler in {@code getEntry(uuid)}. Kosten skalieren mit Spielerzahl x Modianzahl,
 *       auch wenn niemand wartet.</li>
 *   <li><b>Der Beitritt.</b> {@code joinQueue} rief {@code isQueued/leave/join} blockierend auf
 *       dem Hauptthread. Dahinter nimmt {@code RedisQueueStore.withLock} eine netzweite Sperre
 *       mit bis zu 2 s Spin, waehrend der Matchmaker dieselbe Sperre alle 250 ms anfasst. In der
 *       Lobby ist das die haeufigste Spieleraktion ueberhaupt.</li>
 * </ul>
 *
 * <p>⚠️ <b>Dieser Fall liest den QUELLTEXT, und das ist Absicht.</b> {@code updateBossBars} und
 * {@code joinQueue} sind private Methoden auf einem {@code JavaPlugin}; ZanoriaLobby hat weder
 * Mockito noch MockBukkit. Ohne laufenden Server kommt kein Fall an sie heran — ein Umbau ohne
 * Waechter stellt aber genau den Zustand wieder her, in dem der Fehler unbemerkt blieb.
 *
 * <p>⚠️ <b>Kommentare werden vorher entfernt.</b> Ohne das wuerde der Erklaerblock, der den Fix
 * beschreibt und {@code runTaskTimer} woertlich nennt, den Waechter selbst erfuellen.
 * {@link #derKommentarentfernerTutWasErBehauptet()} haelt fest, dass das Entfernen wirkt.
 */
class DerWarteschlangenpfadVerlaesstDenHauptthreadTest {

    private static final Path HAUPTKLASSE =
            Path.of("src/main/java/net/zanoria/lobby/ZanoriaLobby.java");

    private static String quelltextOhneKommentare() {
        assertTrue(Files.exists(HAUPTKLASSE), "⚠️ " + HAUPTKLASSE + " nicht gefunden - dann prueft "
                + "dieser Fall nichts und waere trotzdem gruen");
        try {
            String roh = Files.readString(HAUPTKLASSE, StandardCharsets.UTF_8);
            assertTrue(roh.length() > 1000, "⚠️ Die Datei ist verdaechtig kurz (" + roh.length()
                    + " Zeichen) - eine leere Quelle erfuellt jede Verbotspruefung");
            return ohneKommentare(roh);
        } catch (IOException fehler) {
            throw new UncheckedIOException(fehler);
        }
    }

    /** Entfernt {@code //}- und {@code /* *}-Kommentare. Absichtlich einfach; der Selbsttest haelt es fest. */
    static String ohneKommentare(String quelle) {
        return quelle
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\\n]*", "");
    }

    @Test
    @DisplayName("Der Kommentarentferner tut, was er behauptet")
    void derKommentarentfernerTutWasErBehauptet() {
        // ⚠️ Ohne diesen Fall koennte ohneKommentare alles oder nichts entfernen, und die
        // Verbotspruefungen unten waeren gruen, ohne je etwas gesehen zu haben.
        assertEquals("int a = 1; \n", ohneKommentare("int a = 1; // runTaskTimer(\n"));
        assertEquals("int b = 2;", ohneKommentare("int /* runTaskTimer( */ b = 2;")
                .replaceAll("\\s+", " ").trim().replace("int b = 2;", "int b = 2;"));
        assertTrue(ohneKommentare("/** runTaskTimer( */ class X {}").contains("class X"),
                "der Rumpf muss stehen bleiben");
        assertFalse(ohneKommentare("/** runTaskTimer( */ class X {}").contains("runTaskTimer"),
                "und der Kommentar muss weg sein");
    }

    @Test
    @DisplayName("Der BossBar-Takt laeuft asynchron, nicht auf dem Hauptthread")
    void derTaktLaeuftAsynchron() {
        String quelle = quelltextOhneKommentare();

        assertTrue(quelle.contains("runTaskTimerAsynchronously("),
                "⚠️ Der Takt muss ueber runTaskTimerAsynchronously registriert werden - er liest "
                        + "jede Sekunde Redis, fuer jeden Modus und jeden Online-Spieler");
        assertFalse(quelle.matches("(?s).*runTaskTimer\\s*\\((?!Asynchronously).*"),
                "⚠️ runTaskTimer( steht noch im Quelltext - der synchrone Takt ist zurueck");
    }

    /** Der Rumpf einer Methode: ab ihrer Signatur bis zur naechsten Methode derselben Ebene. */
    static String rumpfVon(String quelle, String signatur) {
        int start = quelle.indexOf(signatur);
        assertTrue(start >= 0, "⚠️ '" + signatur + "' nicht gefunden - dann prueft dieser Fall "
                + "nichts und waere trotzdem gruen");
        int ende = quelle.indexOf("\n    private ", start + signatur.length());
        if (ende < 0) {
            ende = quelle.length();
        }
        return quelle.substring(start, ende);
    }

    @Test
    @DisplayName("Auf der Hauptspur wird kein queueService angefasst")
    void dieHauptspurBleibtFreiVonRedis() {
        // ⚠️ Genau dieser Griff fehlte zuerst. Der Takt war schon umgestellt, aber
        // displaySeconds las getQueueTime weiter auf dem Hauptthread nach - der Waechter oben
        // sah das nicht, weil er nur die Registrierung prueft.
        String quelle = quelltextOhneKommentare();
        for (String hauptspur : new String[]{
                "private void bossBarsAnwenden(", "private long displaySeconds(", "private List<UUID> onlineSpielerIds("}) {
            String rumpf = rumpfVon(quelle, hauptspur);
            assertTrue(rumpf.length() > 40, "⚠️ Rumpf von " + hauptspur + " ist verdaechtig kurz ("
                    + rumpf.length() + ") - ein leerer Rumpf erfuellt jede Verbotspruefung");
            assertFalse(rumpf.contains("queueService."),
                    "⚠️ " + hauptspur + " laeuft auf dem Hauptthread und fasst queueService an - "
                            + "das ist Redis, und damit Standing Rule 1 verletzt");
        }
    }

    @Test
    @DisplayName("Positivkontrolle: der Rumpfausschnitt findet einen echten Verstoss")
    void derRumpfausschnittFindetEinenVerstoss() {
        String erfunden = "    private void x() {\n        queueService.getQueueTime(id);\n    }\n"
                + "    private void y() {\n    }\n";
        assertTrue(rumpfVon(erfunden, "private void x(").contains("queueService."),
                "der Ausschnitt muss den Verstoss im richtigen Rumpf sehen");
        assertFalse(rumpfVon(erfunden, "private void y(").contains("queueService."),
                "und er darf ihn nicht in den Nachbarrumpf schleppen");
    }

    @Test
    @DisplayName("Positivkontrolle: der Verbotsgriff kann ueberhaupt ausschlagen")
    void derVerbotsgriffKannAusschlagen() {
        // ⚠️ Ohne diesen Fall waere "runTaskTimer( kommt nicht vor" auch dann gruen, wenn das
        // Muster gar nichts treffen KANN - die Bauart, an der heute mehrere Zaehlungen scheiterten.
        String mitVerstoss = "getScheduler().runTaskTimer(this, this::x, 20L, 20L);";
        assertTrue(mitVerstoss.matches("(?s).*runTaskTimer\\s*\\((?!Asynchronously).*"),
                "das Muster muss einen echten Verstoss treffen, sonst misst der Fall nichts");
        String ohneVerstoss = "getScheduler().runTaskTimerAsynchronously(this, this::x, 20L, 20L);";
        assertFalse(ohneVerstoss.matches("(?s).*runTaskTimer\\s*\\((?!Asynchronously).*"),
                "und die asynchrone Fassung darf es NICHT treffen");
    }
}
