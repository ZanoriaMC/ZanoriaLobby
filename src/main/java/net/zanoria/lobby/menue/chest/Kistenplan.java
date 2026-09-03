package net.zanoria.lobby.menue.chest;

/**
 * Die Rechnung hinter {@code ChestMenue} — <b>bukkitfrei</b>.
 *
 * <p>⚠️ Sie liegt getrennt, weil beide Bukkit-Griffe der Umsetzung ohne laufenden Server
 * scheitern. Am 2026-09-03 gemessen ({@code DieRegistrygrenzeIstGemessenTest}):
 *
 * <ul>
 *   <li>{@code Bukkit.createInventory(null, 9, "titel")} →
 *       {@code NullPointerException}, {@code Bukkit.server} ist null</li>
 *   <li>{@code new ItemStack(Material.COMPASS)} → {@code ExceptionInInitializerError},
 *       Ursache {@code IllegalStateException: No RegistryAccess implementation found}</li>
 * </ul>
 *
 * <p>⚠️ <b>Das sind zwei verschiedene Fehler, und der Unterschied gehoert mitgelesen:</b> das
 * Server-Singleton liesse sich grundsaetzlich stellen (per Feld setzen), die Registry nicht. Weil
 * {@code ItemStack} hart blockiert, bleibt das Oeffnen trotzdem Sache des Erstlaufs - aber wer
 * beide zusammenwirft, sucht spaeter am falschen Ende.
 *
 * <p>Laege die Groessenrechnung drueben, waere sie in <b>keinem</b> Einheitstest pruefbar - und
 * eine Groessenrechnung, die danebenliegt, wirft im Spiel beim Oeffnen, vor dem Spieler.
 *
 * <p>⚠️ <b>Kein Layout.</b> Entschieden vom Betreiber am 2026-09-03: <i>„so einfach wie moeglich -
 * die Menues werden mit ZanUI ohnehin neu gebaut. Sie muessen funktionieren und austauschbar sein,
 * sonst nichts."</i> Wer hier Rahmen, Luecken oder Platzwuensche ergaenzt, baut Arbeit, die der
 * Tausch wegwirft.
 */
public final class Kistenplan {

    /** Bukkits Obergrenze fuer eine Kiste. */
    public static final int MAX_PLAETZE = 54;

    private static final int REIHE = 9;

    private Kistenplan() {
    }

    /** Die Inventargroesse fuer so viele Eintraege — aufgerundet auf volle Neunerreihen. */
    public static int groesseFuer(int eintraege) {
        if (eintraege <= 0) {
            throw new IllegalArgumentException(
                    "Ein Bildschirm ohne Eintraege ist kein Bildschirm (bekommen: "
                            + eintraege + ")");
        }
        if (eintraege > MAX_PLAETZE) {
            // ⚠️ LAUT abweisen, nicht abschneiden. Ein stillschweigend abgeschnittener Eintrag
            // existiert im Code und ist im Spiel unsichtbar - ohne Ausnahme und ohne Logzeile.
            // Abschneiden sieht dabei wie eine Freundlichkeit aus, und genau das macht es teuer.
            throw new IllegalArgumentException(
                    "Eine Kiste fasst hoechstens " + MAX_PLAETZE + " Eintraege, gefordert waren "
                            + eintraege + ". Stillschweigend abschneiden hiesse: der"
                            + " ueberzaehlige Eintrag existiert im Code und ist im Spiel"
                            + " unsichtbar.");
        }
        return ((eintraege + REIHE - 1) / REIHE) * REIHE;
    }

    /** Der Platz des n-ten Eintrags. Der Reihe nach, kein Layout. */
    public static int platzFuer(int index) {
        if (index < 0 || index >= MAX_PLAETZE) {
            throw new IllegalArgumentException("Platz " + index + " liegt ausserhalb der Kiste");
        }
        return index;
    }
}
