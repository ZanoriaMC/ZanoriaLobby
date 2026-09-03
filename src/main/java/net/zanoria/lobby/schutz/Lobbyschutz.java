package net.zanoria.lobby.schutz;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Die Regel: welche Welt in der Lobby geschuetzt ist.
 *
 * <p>⚠️ <b>Bukkitfrei, und das ist der Grund, warum es diese Klasse gibt.</b> Der Hoerer daneben
 * laesst sich ohne Server nur schwer fahren; diese Rechnung laeuft in jedem Einheitstest.
 * Dieselbe Bauart wie {@code NexusStrikeMatch.beiTod(UUID, long)}: das Bukkit-Ende liest die Welt,
 * das rohe Ende entscheidet.
 *
 * <h2>⚠️ Warum sie ZU faellt und nicht auf</h2>
 *
 * <p>Steht in der Konfiguration eine Welt, die es nicht gibt - ein Tippfehler genuegt -, dann
 * schuetzt diese Klasse <b>alle</b> Welten und sagt es ueber {@link #faelltZu()}. Der Hoerer
 * schreibt daraufhin eine Fehlerzeile.
 *
 * <p>Die Gegenrichtung waere die teure: nichts geschuetzt, kein Fehler, kein Protokolleintrag -
 * und im Spiel baut jeder die Lobby ab. Ein Schutz, der bei Unsicherheit aufmacht, ist keiner.
 */
public final class Lobbyschutz {

    private final String weltname;
    private final boolean faelltZu;

    private Lobbyschutz(String weltname, boolean faelltZu) {
        this.weltname = weltname;
        this.faelltZu = faelltZu;
    }

    /**
     * @param konfigurierteWelt der Name aus {@code config.yml}, darf {@code null}/leer sein
     * @param vorhandeneWelten  die Namen der wirklich geladenen Welten
     */
    public static Lobbyschutz aus(String konfigurierteWelt, List<String> vorhandeneWelten) {
        Objects.requireNonNull(vorhandeneWelten, "vorhandeneWelten");
        if (konfigurierteWelt == null || konfigurierteWelt.isBlank()) {
            return new Lobbyschutz(null, true);
        }
        boolean gefunden = vorhandeneWelten.stream()
                .anyMatch(w -> w.equalsIgnoreCase(konfigurierteWelt));
        return gefunden
                ? new Lobbyschutz(konfigurierteWelt.toLowerCase(Locale.ROOT), false)
                : new Lobbyschutz(null, true);
    }

    /** Ob in dieser Welt weder abgebaut noch gesetzt werden darf. */
    public boolean schuetzt(String weltname) {
        if (faelltZu) {
            return true;
        }
        return weltname != null && weltname.toLowerCase(Locale.ROOT).equals(this.weltname);
    }

    /**
     * Ob der Rueckfall greift - also ob die konfigurierte Welt fehlt.
     *
     * <p>⚠️ Muss ablesbar sein, damit der Hoerer es protokollieren kann. Ein stiller Rueckfall auf
     * "alles geschuetzt" waere zwar sicher, aber niemand faende je den Tippfehler.
     */
    public boolean faelltZu() {
        return faelltZu;
    }
}
