package net.zanoria.lobby.warteschlange;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Der Ablauf des BossBar-Takts — <b>ohne jeden Bukkit-Bezug</b>, damit die Spurdisziplin
 * pruefbar ist.
 *
 * <p>⚠️ <b>Warum es diese Klasse gibt.</b> Standing Rule 1 verlangt, dass Join/Quit/Command/Tick
 * kein synchrones I/O auf dem Hauptthread machen. Bis zum 2026-09-06 tat der Takt genau das:
 * {@code runTaskTimer} registriert, und von dort jede Sekunde {@code getQueueSize} je Modus plus
 * {@code getEntry} je Online-Spieler gegen Redis. Der naheliegende Fix — Takt auf
 * {@code runTaskTimerAsynchronously} — reicht allein NICHT: Bukkits API ist nicht threadsicher,
 * also muessen die Spielerliste und jede BossBar-Aenderung zurueck auf den Hauptthread.
 *
 * <p>⚠️ <b>Und warum als eigene Klasse statt direkt im Plugin.</b> {@code updateBossBars} und
 * {@code joinQueue} sind private Methoden auf einem {@code JavaPlugin}; ZanoriaLobby hat weder
 * Mockito noch MockBukkit. Ohne Naht kaeme kein Fall an sie heran, und Standing Rule 8
 * („mindestens ein Test, der ohne den Fix rot ist") waere nicht erfuellbar. Der Fund entstand
 * gerade dadurch, dass ihn niemand bemerkte — ein Umbau ohne Waechter stellt diesen Zustand
 * wieder her.
 */
public final class Warteschlangentakt {

    /**
     * Die zwei Spuren. In der Lobby sind das {@code runTaskAsynchronously} und {@code runTask};
     * im Waechter eine mitschreibende Fassung, die beide sofort ausfuehrt und festhaelt, auf
     * welcher Spur was lief.
     */
    public interface Ausfuehrung {

        /** Abseits des Hauptthreads — hierhin gehoert alles, was Redis anfasst. */
        void abseits(Runnable arbeit);

        /** Zurueck auf den Hauptthread — hierhin gehoert jeder Bukkit-Aufruf. */
        void haupt(Runnable arbeit);
    }

    private Warteschlangentakt() {
    }

    /**
     * Faehrt einen Takt: Spielerliste auf der Hauptspur holen, Warteschlangenstand auf der
     * Nebenspur lesen, Ergebnis auf der Hauptspur anwenden.
     *
     * <p>⚠️ Die Reihenfolge ist die Zusicherung, nicht ein Umsetzungsdetail. Wer {@code lesen}
     * auf die Hauptspur zieht, hat Regel 1 wieder verletzt; wer {@code anwenden} oder
     * {@code onlineLesen} auf die Nebenspur zieht, ruft Bukkit von einem fremden Thread.
     *
     * @param ausfuehrung  die beiden Spuren
     * @param onlineLesen  Bukkit: wer ist online (Hauptspur)
     * @param lesen        Redis: der Stand zu diesen Spielern (Nebenspur)
     * @param anwenden     Bukkit: BossBars setzen (Hauptspur)
     */
    public static <T> void fahre(Ausfuehrung ausfuehrung,
                                 Supplier<List<UUID>> onlineLesen,
                                 Function<List<UUID>, T> lesen,
                                 Consumer<T> anwenden) {
        ausfuehrung.haupt(() -> {
            List<UUID> online = onlineLesen.get();
            ausfuehrung.abseits(() -> {
                T stand = lesen.apply(online);
                ausfuehrung.haupt(() -> anwenden.accept(stand));
            });
        });
    }

    /**
     * Faehrt eine Spieleraktion: die blockierende Arbeit auf der Nebenspur, die Rueckmeldung an
     * den Spieler auf der Hauptspur.
     *
     * <p>⚠️ Anlass ist {@code joinQueue}: {@code isQueued/leave/join} liegen hinter
     * {@code RedisQueueStore.withLock} mit bis zu 2 s Spin, waehrend der Matchmaker dieselbe
     * Sperre alle 250 ms anfasst — und das ist die haeufigste Spieleraktion der Lobby.
     */
    public static <T> void fahreAktion(Ausfuehrung ausfuehrung, Supplier<T> arbeit, Consumer<T> rueckmeldung) {
        ausfuehrung.abseits(() -> {
            T ergebnis = arbeit.get();
            ausfuehrung.haupt(() -> rueckmeldung.accept(ergebnis));
        });
    }
}
